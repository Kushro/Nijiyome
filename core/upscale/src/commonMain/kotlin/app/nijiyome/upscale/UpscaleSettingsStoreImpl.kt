// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where the JSON of [ImageUpscaleSettings] is kept. Calls are blocking; [PersistedUpscaleSettingsStore] reads once at creation and
 * writes on its IO dispatcher. Step 4 picks the backing: [FileUpscaleSettingsPersistence], or the app settings DataStore key
 * `image_upscale_json` next to `frame_gen_json` (plan §5).
 */
interface UpscaleSettingsPersistence {
    fun read(): String?
    fun write(json: String)
}

/** [UpscaleSettingsPersistence] over one file (`upscale-settings.json`), replaced atomically like the JSON stores of :core:discover. */
class FileUpscaleSettingsPersistence(private val file: File) : UpscaleSettingsPersistence {
    private val gate = Any()

    override fun read(): String? = synchronized(gate) { file.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() } }

    override fun write(json: String) {
        synchronized(gate) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json)
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

/**
 * The [UpscaleSettingsStore] of the app. [update] is visible in [settings] at once; the write runs on [io] and always stores the
 * latest value, so two quick updates never land out of order. [clearCache] empties [cache]; the queue sees it through
 * [UpscaleResultCache.clears] and forgets its finished paths.
 */
class PersistedUpscaleSettingsStore(
    private val persistence: UpscaleSettingsPersistence,
    private val cache: UpscaleResultCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : UpscaleSettingsStore {
    private val state = MutableStateFlow(UpscaleSettingsCodec.decode(runCatching { persistence.read() }.getOrNull()))
    override val settings: StateFlow<ImageUpscaleSettings> = state.asStateFlow()

    override fun update(transform: (ImageUpscaleSettings) -> ImageUpscaleSettings) {
        state.update { transform(it).sanitized() }
        scope.launch(io) {
            // Runs one at a time on [io]: the value written is the newest at write time, whichever launch got here first.
            runCatching { persistence.write(UpscaleSettingsCodec.encode(state.value)) }
        }
    }

    override suspend fun clearCache() {
        withContext(io) { cache.clear() }
    }
}

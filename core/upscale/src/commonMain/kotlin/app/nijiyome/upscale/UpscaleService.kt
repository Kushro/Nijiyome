// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.concurrent.Executors

/**
 * The upscaling graph of one app (plan §4.1, step 2's assembly order): result cache, settings store, queue. Built by the platform's
 * Koin module, which passes the engine, the pause signals and the folders; [close] (Koin's onClose) stops the queue's coroutines and
 * its worker thread.
 */
class UpscaleService private constructor(
    val queue: UpscaleQueue,
    val store: UpscaleSettingsStore,
    private val scope: CoroutineScope,
    private val worker: ExecutorCoroutineDispatcher,
) {
    fun close() {
        scope.cancel()
        worker.close()
    }

    companion object {
        /**
         * [pauses] gets the settings (for NOT_CHARGING) and the scope the reasons are shared in. [threadSetup] runs once on the worker
         * thread before any job (Android lowers its priority there).
         */
        fun create(
            cacheDir: File,
            persistence: UpscaleSettingsPersistence,
            engine: UpscaleEngine,
            source: UpscaleSourceProvider,
            pauses: (settings: StateFlow<ImageUpscaleSettings>, scope: CoroutineScope) -> StateFlow<Set<UpscalePauseReason>>,
            limits: UpscaleLimits = platformUpscaleLimits(),
            codec: UpscaleImageCodec = platformUpscaleCodec(),
            threadSetup: () -> Unit = {},
        ): UpscaleService {
            // The queue's collectors adjust the cache cap, so they must not wait for the main thread (step 2, §5).
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val worker = lowPriorityWorker(threadSetup)
            val cache = UpscaleResultCache(
                DirectoryUpscaleFileStore(File(cacheDir, "upscaled")),
                UpscaleResultCache.megabytes(limits.defaultCacheMb),
            )
            val store = PersistedUpscaleSettingsStore(persistence, cache, scope)
            val queue = DefaultUpscaleQueue(
                engine = engine,
                source = source,
                codec = codec,
                cache = cache,
                settings = store.settings,
                pauseReasons = pauses(store.settings, scope),
                scope = scope,
                limits = limits,
                worker = worker,
            )
            return UpscaleService(queue, store, scope, worker)
        }

        /** One daemon thread at the lowest Java priority: the queue's worker, where every JNI call runs. */
        private fun lowPriorityWorker(threadSetup: () -> Unit): ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { task ->
                Thread({
                    runCatching(threadSetup)
                    task.run()
                }, "nijiyome-upscale").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }.asCoroutineDispatcher()
    }
}

/** Merges the platform's pause signals (each a flow of the reasons it holds right now) into the queue's input. */
fun upscalePauses(scope: CoroutineScope, signals: List<Flow<Set<UpscalePauseReason>>>): StateFlow<Set<UpscalePauseReason>> {
    val merged: Flow<Set<UpscalePauseReason>> =
        if (signals.isEmpty()) flowOf(emptySet()) else combine(signals) { sets -> sets.flatMapTo(HashSet()) { it }.toSet() }
    return merged.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, emptySet())
}

/** A boolean signal as a pause reason. */
fun Flow<Boolean>.pauseWhen(reason: UpscalePauseReason): Flow<Set<UpscalePauseReason>> =
    map { on -> if (on) setOf(reason) else emptySet() }

/**
 * [UpscaleSourceProvider] over Coil's disk cache (plan §4.2 step 3): the bytes Coil stored for [url] (its default disk cache key is
 * the URL), never a download. Null when the entry is missing, so the job is skipped.
 */
class CoilDiskCacheSource(private val loader: () -> ImageLoader?) : UpscaleSourceProvider {
    override suspend fun read(url: String): ByteArray? = runCatching {
        val cache = loader()?.diskCache ?: return null
        cache.openSnapshot(url)?.use { snapshot -> cache.fileSystem.read(snapshot.data) { readByteArray() } }
    }.getOrNull()
}

/**
 * The cheap Coil hook of plan §4.5: an image fetched from the network again tells the queue, which drops results made from other
 * source bytes. [queue] is looked up lazily (the image loader exists before the dependency graph on Android).
 */
class UpscaleRefreshListener(private val queue: () -> UpscaleQueue?) : EventListener() {
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        if (result.dataSource != DataSource.NETWORK) return
        val url = request.data as? String ?: return
        if (!url.startsWith("http")) return
        runCatching { queue()?.sourceRefreshed(url) }
    }
}

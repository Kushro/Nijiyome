// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import app.nijiyome.model.LinkServerController
import app.nijiyome.model.NowPlaying
import app.nijiyome.player.mpv.framegen.DesktopFrameGenActive
import app.nijiyome.upscale.CoilDiskCacheSource
import app.nijiyome.upscale.FileUpscaleSettingsPersistence
import app.nijiyome.upscale.UpscalePauseReason
import app.nijiyome.upscale.UpscaleQueue
import app.nijiyome.upscale.UpscaleService
import app.nijiyome.upscale.UpscaleSettingsStore
import app.nijiyome.upscale.desktopUpscaleEngine
import app.nijiyome.upscale.pauseWhen
import app.nijiyome.upscale.upscalePauses
import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

/** Whether the main window is hidden (tray) or minimised; Main.kt writes it, the upscale queue pauses on it (plan §4.6). */
object DesktopWindowHidden {
    private val _hidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun set(hidden: Boolean) {
        _hidden.value = hidden
    }
}

/**
 * GPU image upscaling on desktop (docs/plan-upscaling.md §4.1): the queue and the settings store as singletons. Settings live in
 * `<data>/upscale-settings.json` next to `torrent-settings.json`, the results in `<cache>/upscaled/`, the sources in Coil's disk
 * cache (`<cache>/coil3`, Main.kt). Pauses: a player screen open, embedded frame generation, Link sessions streaming from this PC,
 * and the window hidden or minimised.
 */
fun desktopUpscaleModule(dataDir: File, cacheDir: File): Module = module {
    single {
        val link = getOrNull<LinkServerController>()
        UpscaleService.create(
            cacheDir = cacheDir,
            persistence = FileUpscaleSettingsPersistence(File(dataDir, "upscale-settings.json")),
            engine = desktopUpscaleEngine(),
            source = CoilDiskCacheSource { SingletonImageLoader.get(PlatformContext.INSTANCE) },
            pauses = { _, scope ->
                upscalePauses(
                    scope,
                    listOf(
                        NowPlaying.current.map { it != null }.pauseWhen(UpscalePauseReason.PLAYER_OPEN),
                        DesktopFrameGenActive.active.pauseWhen(UpscalePauseReason.FRAMEGEN_ACTIVE),
                        (link?.state?.map { it.activeSessions.isNotEmpty() } ?: flowOf(false)).pauseWhen(UpscalePauseReason.LINK_BUSY),
                        DesktopWindowHidden.hidden.pauseWhen(UpscalePauseReason.APP_HIDDEN),
                    ),
                )
            },
        )
    } onClose { it?.close() }
    single<UpscaleQueue> { get<UpscaleService>().queue }
    single<UpscaleSettingsStore> { get<UpscaleService>().store }
}

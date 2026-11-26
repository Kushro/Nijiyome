// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.model.AppSettings
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SettingsRepository
import app.nijiyome.player.ExternalSessionFactory
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.PlayerVolumeMemory
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.mpv.DesktopMpvAssets
import app.nijiyome.player.mpv.DesktopShaderInstaller
import app.nijiyome.player.mpv.displayRefreshRate
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Hito 4, paso D: Koin-bound factory of the external mpv sessions ([ExternalMpvPlayerSession]). It decides, from Settings › Reproductor
 * and the desktop player settings ([DesktopPlayerPrefs]), whether a player plays in mpv's own window and with which configuration:
 *
 * - engine EXTERNAL: the user's own mpv config, or mpv-anime-build when the enhanced mode is on (and downloaded);
 * - frame generation on and lsfg-vk found (Linux): the external window whatever the engine, with the app's own config and quality
 *   presets when the engine was libmpv (Tatakai Next did the same: lsfg-vk only hooks mpv's own Vulkan swapchain).
 *
 * [cacheDir] holds the bundled fonts and shaders (shared with the embedded engine, `<cache>/mpv/`), the filtered HLS masters and the
 * downloaded mpv-anime-build zip; [dataDir] its extraction; [runtimeDir] the throwaway lsfg-vk config.
 */
class ExternalMpvSessionFactory(
    private val prefs: () -> DesktopPlayerPrefs,
    private val settings: () -> AppSettings,
    private val dataDir: File,
    private val cacheDir: File,
    private val runtimeDir: File = defaultRuntimeDir(),
    private val env: Map<String, String> = System.getenv(),
    /** Extra options of every mpv (tests: `--ao=null`, `--vo=null`). */
    private val extraArgs: List<String> = emptyList(),
    /** Environment changes of every mpv (null = remove; tests: an empty `MPV_HOME`, gamescope's display). */
    private val extraEnv: Map<String, String?> = emptyMap(),
    private val frameGenAvailability: (DesktopPlayerPrefs) -> LsfgVk.Availability = { LsfgVk.detect(it.lsfgDll, env) },
    /** v1.3.2: embedded desktop framegen handles FG inside the app window when available; only fall back to external mpv if embedded is unavailable. */
    private val embeddedFrameGenAvailable: (DesktopPlayerPrefs) -> Boolean = {
        app.nijiyome.player.mpv.framegen.DesktopFrameGenController.probeEmbeddedAvailability(it.lsfgDll).ok
    },
    private val refreshHz: () -> Float? = ::displayRefreshRate,
    /** Finds mpv from the custom path setting ([findMpv]). */
    private val mpvLookup: (String?) -> File? = { findMpv(it, env) },
    /** The app's `sub-fonts-dir` (bundled fonts and the user's imported ones), shared with the embedded engine. */
    private val fontsDir: File = File(File(cacheDir, "mpv"), "fonts"),
    /** The last volume the user set (shared with the embedded engine), so the next window opens at it. */
    private val volumeMemory: PlayerVolumeMemory = PlayerVolumeMemory(),
    /** 1.1: the loopback media proxy of Settings › Red (null = none). */
    private val mediaRoute: app.nijiyome.model.LocalMediaRoute? = null,
) : ExternalSessionFactory {
    private val root = File(cacheDir, "mpv")
    private val shaders by lazy { DesktopShaderInstaller(File(root, "shaders")) }

    /** Frame generation can run now (switch on, layer and shaders found). */
    fun frameGenReady(p: DesktopPlayerPrefs = prefs()): LsfgVk.Availability? = if (p.frameGen) frameGenAvailability(p) else null

    override fun wants(engine: PlayerEngine): Boolean {
        if (engine == PlayerEngine.EXTERNAL) return true
        val p = prefs()
        if (!p.frameGen) return false
        // v1.3.2: embedded libmpv runs LSFG in-app (zero-copy GL/D3D12 <-> Vulkan); only redirect to external mpv if embedded FG is unavailable.
        if (embeddedFrameGenAvailable(p)) return false
        return frameGenReady(p)?.available == true
    }

    override fun unavailableReason(): String? {
        val custom = prefs().mpvPath
        if (mpvLookup(custom) != null) return null
        return if (custom != null) "mpv not found at $custom" else "mpv is not installed (not on PATH)"
    }

    override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession = create(scope, listener, remote, "")

    override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean, title: String): PlayerSession {
        val p = prefs()
        val s = settings()
        val mpv = checkNotNull(mpvLookup(p.mpvPath)) { "mpv is not installed" }
        val userChoseExternal = s.playerEngine == PlayerEngine.EXTERNAL
        val fg = frameGenReady(p)?.takeIf { it.available }?.let { a ->
            val hz = refreshHz()?.toDouble()
            ExternalFrameGen(
                multiplier = LsfgVk.multiplierFor(p.frameGenMultiplier, hz),
                requested = p.frameGenMultiplier,
                refreshHz = hz,
                flowScale = p.frameGenFlowScale,
                performance = p.frameGenPerformance,
                dll = a.dll,
                configDir = runtimeDir,
            )
        }
        val app = !userChoseExternal
        if (app) DesktopMpvAssets.installSubtitleFonts(fontsDir)
        mediaRoute?.warm()
        return ExternalMpvPlayerSession(
            scope = scope,
            listener = listener,
            remote = remote,
            setup = ExternalMpvSetup(
                mpv = mpv,
                userChoseExternal = userChoseExternal,
                enhanced = if (userChoseExternal && p.enhanced) ExternalEnhanced(dataDir, cacheDir, p.enhancedUosc) else null,
                frameGen = fg,
                title = title,
                fontsDir = fontsDir.takeIf { app },
                cacheDir = cacheDir,
                extra = extraArgs,
                extraEnv = extraEnv,
                ipcDir = runtimeDir,
            ),
            initialQuality = QualityRequest.from(s.playerPreset, s.playerQuality),
            shaders = if (app) shaders else null,
            initialVolume = volumeMemory.last(),
            onVolume = volumeMemory::remember,
            refreshHz = refreshHz,
            env = env,
            mediaRoute = mediaRoute,
        )
    }

    companion object {
        /**
         * This user's private folder for the IPC sockets and the throwaway lsfg-vk config ([PrivateRuntimeDir]: mode 0700, never a
         * fixed name in a shared folder).
         */
        fun defaultRuntimeDir(): File = PrivateRuntimeDir.default
    }
}

/**
 * Koin bindings of the external mpv (load it next to [app.nijiyome.player.mpv.desktopMpvPlayerModule]): the desktop player settings
 * store (`<data>/desktop-player.json`) and the [ExternalSessionFactory] the player view model picks up.
 */
fun desktopExternalMpvModule(dataDir: File, cacheDir: File, fontsDir: File = File(File(cacheDir, "mpv"), "fonts")): Module = module {
    single { DesktopPlayerPrefsStore(File(dataDir, "desktop-player.json")) }
    single { ExternalMpvSettingsControl(get(), dataDir = dataDir, cacheDir = cacheDir) }
    single<ExternalSessionFactory> {
        val store = get<DesktopPlayerPrefsStore>()
        val settings = getOrNull<SettingsRepository>()
        ExternalMpvSessionFactory(
            prefs = { store.prefs.value },
            settings = { settings?.settings?.value ?: AppSettings() },
            dataDir = dataDir,
            cacheDir = cacheDir,
            fontsDir = fontsDir,
            volumeMemory = getOrNull<PlayerVolumeMemory>() ?: PlayerVolumeMemory(store),
            mediaRoute = getOrNull(),
        )
    }
}

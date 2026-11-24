// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.player.mpv.MpvEngineDefaults
import java.io.File

// Hito 4, paso D: the command line and the environment of the external mpv (pure). Ported from Tatakai Next
// (tk-player/src/external.rs: `mpv_args`, `FrameGen::mpv_args`, `AnimeBuild::args`, `FgWindow`, `user_env`, `find_mpv`); see
// docs/origen/THIRD_PARTY_NOTICES-escritorio.md for its provenance.

/** Whose configuration the external mpv runs with. */
sealed interface ExternalMpvConfig {
    /** The user's own mpv config, scripts and key bindings (the EXTERNAL engine setting). Never `--no-config`. */
    data object User : ExternalMpvConfig

    /** mpv-anime-build ("modo mejorado"): its extracted release as mpv's config directory, with [scripts] loaded explicitly. */
    data class AnimeBuild(val configDir: File, val uosc: Boolean, val scripts: List<File>) : ExternalMpvConfig

    /**
     * The app owns the config (`--no-config`): frame generation pulled a libmpv user into this window, so the app's quality presets
     * (shaders, deband, image) are applied over IPC exactly as in the embedded engine.
     */
    data object App : ExternalMpvConfig
}

/** lsfg-vk frame generation for this mpv: the dedicated config file it reads ([LsfgVk.configToml]) and the multiplier in it. */
data class LsfgLaunch(val configFile: File, val multiplier: Int)

/** Everything one external mpv process is started with. */
internal data class ExternalMpvLaunch(
    val endpoint: String,
    val url: String,
    val headers: Map<String, String>,
    val startSecs: Double,
    val title: String,
    val config: ExternalMpvConfig,
    val frameGen: LsfgLaunch? = null,
    /** Open fullscreen (frame generation pulled a libmpv user here: less of a pop-up). */
    val fullscreen: Boolean = false,
    /** `demuxer-lavf-o` key=value of a filtered local HLS master (HlsPrefetch.lavfOptions), empty = none. */
    val lavfOptions: String = "",
    /** A loopback torrent stream: pieces may take a while, so the network timeout is the embedded engine's. */
    val loopback: Boolean = false,
    /** Options the session applies at start (`name -> value`: subtitle style, languages, volume, the app's quality chain...). */
    val options: Map<String, String> = emptyMap(),
    /** Extra options of every process (tests: `--ao=null`, `--vo=null`). */
    val extra: List<String> = emptyList(),
    /**
     * Open paused; the session unpauses once it observes mpv over IPC. Otherwise a short file (or a loaded machine) could play, and
     * even end, before the session connects, and the events it missed are not sent again.
     */
    val startPaused: Boolean = true,
)

/** Sent when the source has no User-Agent: the embedded engine's default. */
internal const val EXTERNAL_DEFAULT_USER_AGENT = MpvEngineDefaults.DEFAULT_USER_AGENT

/** The embedded engine's cache and network values (MpvEngineDefaults), used when the app owns the config. */
private val APP_CACHE_OPTIONS = listOf(
    "--cache=yes", "--demuxer-max-bytes=150MiB", "--demuxer-max-back-bytes=50MiB", "--demuxer-readahead-secs=60", "--network-timeout=90",
)

/** Vulkan presentation so the lsfg-vk layer sees a swapchain; no display-resample (lsfg paces the frames). Always last. */
internal val FRAME_GEN_OPTIONS = listOf("--vo=gpu-next", "--gpu-api=vulkan", "--video-sync=audio", "--interpolation=no")

/** The full argument list after the executable (pure). */
internal fun externalMpvArgs(l: ExternalMpvLaunch): List<String> {
    val args = mutableListOf<String>()
    when (val c = l.config) {
        ExternalMpvConfig.User -> Unit
        ExternalMpvConfig.App -> args += "--no-config"
        is ExternalMpvConfig.AnimeBuild -> args += animeBuildArgs(c)
    }
    args += "--force-window=immediate"
    args += "--input-ipc-server=${l.endpoint}"
    if (l.title.isNotBlank()) {
        args += "--title=${escapeTitle(l.title)}"
        // The OSC shows the media title; for HLS it would be the playlist's file name ("master.m3u8").
        args += "--force-media-title=${escapeTitle(l.title)}"
    }
    if (l.startSecs.isFinite() && l.startSecs > 0) args += "--start=${"%.3f".format(java.util.Locale.ROOT, l.startSecs)}"
    val plan = headerPlan(l.headers)
    plan.referrer?.let { args += "--referrer=$it" }
    args += "--user-agent=${plan.userAgent ?: EXTERNAL_DEFAULT_USER_AGENT}"
    // `-append` adds one item without splitting on commas (cookies contain them).
    plan.fields.forEach { args += "--http-header-fields-append=$it" }
    if (l.config != ExternalMpvConfig.User) {
        args += APP_CACHE_OPTIONS
        args += listOf("--vo=gpu-next", "--hwdec=auto-safe", "--autofit-larger=90%x90%")
    } else if (l.loopback) {
        args += "--network-timeout=90"
    }
    if (l.lavfOptions.isNotEmpty()) args += "--demuxer-lavf-o-append=${l.lavfOptions}"
    for ((k, v) in l.options) args += "--$k=$v"
    if (l.startPaused) args += "--pause=yes"
    // Never yt-dlp, never playlist entries a network file points at (a user's mpv.conf cannot turn them on: these come later).
    args += listOf("--ytdl=no", "--load-unsafe-playlists=no")
    if (l.frameGen != null) args += FRAME_GEN_OPTIONS
    if (l.fullscreen) args += "--fs"
    args += l.extra
    args += "--"
    args += l.url
    return args
}

/** mpv-anime-build's options. The forced Vulkan / pacing options come later on the command line, so they win. */
internal fun animeBuildArgs(b: ExternalMpvConfig.AnimeBuild): List<String> = buildList {
    add("--config-dir=${b.configDir.absolutePath}")
    add("--config=yes")
    add("--load-scripts=no")
    // `-append`: one path per item, no list-separator splitting.
    b.scripts.forEach { add("--scripts-append=${it.absolutePath}") }
    // Without uosc mpv's own OSC keeps seek / pause with the mouse (the window has no Nijiyome controls).
    add(if (b.uosc) "--osc=no" else "--osc=yes")
    // The app records progress and passes `--start`; the build's own resume would fight it.
    addAll(listOf("--resume-playback=no", "--save-position-on-quit=no", "--keep-open=no", "--idle=no"))
}

/** The environment changes of the mpv process: [frameGen]'s config, or the layer kept out when the app owns the config. */
internal fun externalMpvEnv(l: ExternalMpvLaunch, inherited: Map<String, String>): Map<String, String?> {
    val env = LinkedHashMap<String, String?>(appImageScrub(inherited))
    if (l.frameGen != null) {
        env[LsfgVk.ENV_CONFIG] = l.frameGen.configFile.absolutePath
        env[LsfgVk.ENV_DISABLE] = null
    } else if (l.config != ExternalMpvConfig.User) {
        env[LsfgVk.ENV_DISABLE] = "1"
    }
    return env
}

/**
 * Variables an AppImage points at bundled GTK / GIO files (Tatakai's `APPIMAGE_VARS`); the machine's mpv must not inherit them.
 * Returns the changes (null = remove). Empty outside an AppImage.
 */
internal fun appImageScrub(env: Map<String, String>): Map<String, String?> {
    val appdir = env["APPDIR"]?.takeIf { env["APPIMAGE"] != null && it.isNotEmpty() } ?: return emptyMap()
    val out = LinkedHashMap<String, String?>()
    APPIMAGE_VARS.forEach { out[it] = null }
    out["LD_LIBRARY_PATH"] = env["LD_LIBRARY_PATH"]?.split(File.pathSeparatorChar)?.filterNot { it.startsWith(appdir) }
        ?.joinToString(File.pathSeparator)?.takeIf { it.isNotEmpty() }
    out["XDG_DATA_DIRS"] = env["XDG_DATA_DIRS"]?.split(File.pathSeparatorChar)?.filterNot { it.startsWith(appdir) }
        ?.joinToString(File.pathSeparator)?.takeIf { it.isNotEmpty() }
    return out
}

private val APPIMAGE_VARS = listOf(
    "GTK_DATA_PREFIX", "GTK_EXE_PREFIX", "GTK_PATH", "GTK_THEME", "GTK_IM_MODULE_FILE", "GDK_PIXBUF_MODULE_FILE", "GSETTINGS_SCHEMA_DIR",
    "GI_TYPELIB_PATH", "GIO_MODULE_DIR", "GIO_EXTRA_MODULES",
)

/** mpv expands `${property}` in `--title`; a literal `$` is written `$$`. */
internal fun escapeTitle(title: String): String = title.map { if (it.code < 0x20 || it.code == 0x7f) ' ' else it }.joinToString("").replace("$", "$$")

/** How a source's headers reach mpv: its own `referrer` / `user-agent` options, the rest as `Name: value` header fields. */
internal data class HeaderPlan(val userAgent: String?, val referrer: String?, val fields: List<String>)

internal fun headerPlan(headers: Map<String, String>): HeaderPlan {
    var ua: String? = null
    var referrer: String? = null
    val fields = mutableListOf<String>()
    for ((k, v) in headers) {
        when {
            k.equals("User-Agent", ignoreCase = true) -> ua = v
            k.equals("Referer", ignoreCase = true) -> referrer = v
            else -> fields += "$k: $v"
        }
    }
    return HeaderPlan(ua, referrer, fields)
}

/** The mpv executable name of this OS. */
internal fun mpvExeName(windows: Boolean = isWindowsHost()): String = if (windows) "mpv.exe" else "mpv"

/** Usual install locations checked after PATH. */
internal fun wellKnownMpv(windows: Boolean, env: Map<String, String>): List<File> = if (windows) {
    listOf(
        "ProgramFiles" to "mpv\\mpv.exe",
        "LOCALAPPDATA" to "Programs\\mpv\\mpv.exe",
        "USERPROFILE" to "scoop\\apps\\mpv\\current\\mpv.exe",
        "ProgramData" to "chocolatey\\bin\\mpv.exe",
    ).mapNotNull { (v, rel) -> env[v]?.let { File(it, rel) } }
} else {
    listOf("/usr/bin/mpv", "/usr/local/bin/mpv", "/snap/bin/mpv", "/var/lib/flatpak/exports/bin/io.mpv.Mpv").map(::File)
}

/**
 * The mpv to run: [custom] if set (and only it: a wrong custom path is reported as not found instead of silently using another mpv),
 * else the first `mpv` on PATH, else a usual location.
 */
fun findMpv(
    custom: String?,
    env: Map<String, String> = System.getenv(),
    windows: Boolean = isWindowsHost(),
    wellKnown: List<File> = wellKnownMpv(windows, env),
): File? {
    custom?.trim()?.takeIf { it.isNotEmpty() }?.let { p -> return File(p).takeIf { it.isFile && it.canExecute() } }
    val exe = mpvExeName(windows)
    val onPath = env["PATH"].orEmpty().split(File.pathSeparatorChar).filter { it.isNotEmpty() }.map { File(it, exe) }
    return (onPath + wellKnown).firstOrNull { it.isFile && it.canExecute() }
}

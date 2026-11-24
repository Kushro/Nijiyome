// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.model.AppSettings
import app.nijiyome.model.Container
import app.nijiyome.model.PlayerEngine
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hito 4, paso D: the external mpv on the real GPU, inside `gamescope --backend headless` (no window on the user's desktop, no sound),
 * with and without the enhanced mode and with and without lsfg-vk. Measured over mpv's IPC (dropped frames, mpv's frame rates) and
 * gamescope's own statistics (`-T`: the frame rate gamescope receives, which includes the frames lsfg-vk adds), plus a
 * `gamescopectl screenshot` per case. Runs only when the gamescope script sets `NIJIYOME_GS_DISPLAY` (gamescope's X display, which
 * must differ from the user's `NIJIYOME_USER_DISPLAY`); skipped everywhere else.
 *
 * Variables: `NIJIYOME_GS_OUT` (results), `NIJIYOME_GS_CACHE` (the clip and the pinned mpv-anime-build download), `NIJIYOME_GS_STATS`
 * (gamescope's statistics file: it must exist before gamescope starts), `NIJIYOME_GS_SECONDS` (measured seconds per case, default 12),
 * `NIJIYOME_GS_CASES` (a comma-separated subset of plain, enhanced, plain-lsfg, enhanced-lsfg).
 */
class ExternalMpvGamescopeRun {
    private val env = System.getenv()

    @Test
    fun measureInGamescope() {
        val display = env["NIJIYOME_GS_DISPLAY"].orEmpty()
        assumeTrue("not inside the gamescope script", display.isNotBlank())
        val user = env["NIJIYOME_USER_DISPLAY"].orEmpty()
        assertTrue(display != user, "refusing: gamescope's DISPLAY $display is the user's")
        val out = File(env["NIJIYOME_GS_OUT"] ?: fail("NIJIYOME_GS_OUT")).apply { mkdirs() }
        val cache = File(env["NIJIYOME_GS_CACHE"] ?: fail("NIJIYOME_GS_CACHE")).apply { mkdirs() }
        val stats = env["NIJIYOME_GS_STATS"]?.let(::File)
        val seconds = env["NIJIYOME_GS_SECONDS"]?.toLongOrNull() ?: 12
        assumeTrue("mpv not installed", findMpv(null) != null)
        val clip = clip(cache)
        val data = File(out, "data")

        // The enhanced mode needs the release: downloaded once into the cache with its pinned SHA-256, extracted under `data`.
        val build = runCatching {
            AnimeBuild.prepare(AnimeBuild.download(cache), AnimeBuild.configDir(data))
        }
        println("mpv-anime-build: ${build.fold({ "ready at ${it.path}" }, { "unavailable: ${it.message}" })}")
        val lsfg = LsfgVk.detect(null)
        println("lsfg-vk: layer=${lsfg.layer} dll=${lsfg.dll} missing=${lsfg.missing}")

        val only = env["NIJIYOME_GS_CASES"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val cases = buildList {
            add(Case("plain", enhanced = false, frameGen = false))
            if (build.isSuccess) add(Case("enhanced", enhanced = true, frameGen = false))
            if (lsfg.available) {
                add(Case("plain-lsfg", enhanced = false, frameGen = true))
                if (build.isSuccess) add(Case("enhanced-lsfg", enhanced = true, frameGen = true))
            }
        }.filter { only == null || it.name in only }
        val report = StringBuilder()
        val mpvHome = File(out, "mpv-home").apply { mkdirs() }
        for (c in cases) {
            val line = runCase(c, clip, data, cache, out, mpvHome, display, stats, seconds)
            println(line)
            report.append(line).append('\n')
        }
        File(out, "results.txt").writeText(report.toString())
    }

    private data class Case(val name: String, val enhanced: Boolean, val frameGen: Boolean)

    private fun runCase(
        c: Case, clip: File, data: File, cache: File, out: File, mpvHome: File, display: String, stats: File?, seconds: Long,
    ): String {
        val prefs = DesktopPlayerPrefs(enhanced = c.enhanced, enhancedUosc = true, frameGen = c.frameGen, frameGenMultiplier = 2)
        val lsfgLog = File(out, "${c.name}-lsfg.log")
        val factory = ExternalMpvSessionFactory(
            prefs = { prefs },
            settings = { AppSettings(playerEngine = PlayerEngine.EXTERNAL) },
            dataDir = data,
            cacheDir = cache,
            runtimeDir = File(out, "run-${c.name}"),
            extraArgs = listOf("--ao=null", "--fs", "--log-file=" + File(out, "${c.name}-mpv.log").absolutePath),
            extraEnv = buildMap {
                put("DISPLAY", display)
                put("WAYLAND_DISPLAY", null) // mpv on gamescope's X server (XWayland), never a Wayland socket of the user's
                put("MPV_HOME", mpvHome.absolutePath) // the user's mpv.conf stays out of the measurement
                // gamescope's own Vulkan WSI layer makes Vulkan clients crash under --backend headless (mpv exits 139 and takes
                // gamescope down); without it mpv presents through gamescope's XWayland like any X11 client. Test environment only.
                put("DISABLE_GAMESCOPE_WSI", "1")
                if (c.frameGen) {
                    put(LsfgVk.ENV_LOG_FILE, lsfgLog.absolutePath)
                    put(LsfgVk.ENV_LOG_LEVEL, env["NIJIYOME_GS_LSFG_LEVEL"] ?: "info")
                }
            },
            refreshHz = { 60f },
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            return runBlocking(Dispatchers.Main) {
                val session = factory.create(scope, object : PlaybackListener {}, remote = false, title = "Nijiyome ${c.name}") as ExternalMpvPlayerSession
                session.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, c.name, localFile = true), startMs = 0)
                withTimeoutOrNull(30_000) { session.state.first { it.started && it.isPlaying } }
                    ?: return@runBlocking "${c.name}: did not start (${session.state.value}) closed=${session.windowClosed.value}".also { session.release() }
                delay(3_000) // warm-up: shaders compile, the window settles fullscreen
                val p = session.processForTest ?: return@runBlocking "${c.name}: mpv is gone"
                fun get(name: String): String = p.request(listOf("get_property_string", name))?.takeIf { it.error == "success" }?.data
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
                val drops0 = get("frame-drop-count").toLongOrNull() ?: 0
                val dec0 = get("decoder-frame-drop-count").toLongOrNull() ?: 0
                val delayed0 = get("vo-delayed-frame-count").toLongOrNull() ?: 0
                val pos0 = session.currentPositionMs()
                // gamescope rewrites its statistics file (`fps=` of the focused window): sample it every second.
                val fpsSamples = mutableListOf<Double>()
                repeat(seconds.toInt()) {
                    delay(1000)
                    stats?.let { gamescopeFps(it) }?.let(fpsSamples::add)
                }
                val drops = (get("frame-drop-count").toLongOrNull() ?: 0) - drops0
                val dec = (get("decoder-frame-drop-count").toLongOrNull() ?: 0) - dec0
                val delayed = (get("vo-delayed-frame-count").toLongOrNull() ?: 0) - delayed0
                val played = (session.currentPositionMs() - pos0) / 1000.0
                val shot = File(out, "${c.name}.png")
                val shotOk = gamescopeScreenshot(shot)
                val gsFps = fpsSamples.takeIf { it.isNotEmpty() }
                    ?.let { "%.1f (n=%d, min %.1f, max %.1f)".format(it.average(), it.size, it.min(), it.max()) }
                val line = buildString {
                    append(c.name).append(": ")
                    append("vo=").append(get("current-vo")).append(" gpu-api=").append(get("gpu-api"))
                    append(" hwdec=").append(get("hwdec-current"))
                    append(" played=").append("%.1f".format(played)).append("s")
                    append(" vo-drops=").append(drops).append(" decoder-drops=").append(dec).append(" vo-delayed=").append(delayed)
                    append(" estimated-vf-fps=").append(get("estimated-vf-fps"))
                    append(" estimated-display-fps=").append(get("estimated-display-fps"))
                    append(" display-fps=").append(get("display-fps"))
                    append(" shaders=").append(get("glsl-shaders").split(':', ';').count { it.isNotBlank() })
                    append(" profile=").append(get("profile").ifBlank { "-" })
                    append(" gamescope-fps=").append(gsFps ?: "?")
                    append(" screenshot=").append(if (shotOk) shot.name else "failed")
                    append(" mode=").append(session.mode.value)
                }
                session.release()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                while (LiveProcesses.count() > 0 && System.nanoTime() < deadline) delay(100)
                line
            }
        } finally {
            scope.cancel()
        }
    }

    /** `gamescopectl screenshot` of gamescope's output (gamescopectl finds gamescope through `GAMESCOPE_WAYLAND_DISPLAY`). */
    private fun gamescopeScreenshot(file: File): Boolean = runCatching {
        file.delete()
        val p = ProcessBuilder("gamescopectl", "screenshot", file.absolutePath).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        p.waitFor(10, TimeUnit.SECONDS)
        // gamescope writes the file asynchronously
        repeat(30) { if (file.length() > 0) return@runCatching true; Thread.sleep(200) }
        false
    }.getOrDefault(false)

    /** The last frame rate gamescope wrote (`fps=` lines of its statistics file) for the focused window; null below 1 fps or none. */
    private fun gamescopeFps(stats: File): Double? = runCatching {
        Regex("fps=([0-9.]+)").findAll(stats.readText()).mapNotNull { it.groupValues[1].toDoubleOrNull() }.lastOrNull()?.takeIf { it >= 1.0 }
    }.getOrNull()

    /** 30 s of 1080p 24 fps testsrc2 without audio, made once into [cache]. */
    private fun clip(cache: File): File {
        val f = File(cache, "clip-1080p24.mkv")
        if (f.length() > 0) return f
        val cmd = listOf(
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=24000/1001:duration=30",
            "-an", "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p", f.absolutePath,
        )
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assumeTrue("ffmpeg could not make the clip: $log", p.waitFor(180, TimeUnit.SECONDS) && p.exitValue() == 0)
        return f
    }
}

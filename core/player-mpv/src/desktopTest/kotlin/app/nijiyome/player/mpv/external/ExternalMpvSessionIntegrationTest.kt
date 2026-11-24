// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.model.AppSettings
import app.nijiyome.model.Container
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SubtitleTrack
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerVolume
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
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hito 4, paso D: the external session against the machine's real mpv, over its JSON IPC, with a silent clip made by ffmpeg (no audio
 * track). No window and no sound: `--vo=null --ao=null`, and desktopTest runs without the user's display variables (build.gradle.kts).
 * mpv's own config is an empty folder (`MPV_HOME`), so the user's mpv.conf and scripts are not involved. Skipped (Assume) when mpv or
 * ffmpeg is missing.
 */
class ExternalMpvSessionIntegrationTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-xmpv-it").toFile()
    private val progress = CopyOnWriteArrayList<Triple<Long, Long, Boolean>>()
    private val listener = object : PlaybackListener {
        override fun onProgress(positionMs: Long, durationMs: Long, force: Boolean) {
            progress += Triple(positionMs, durationMs, force)
        }
    }

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun factory(engine: PlayerEngine) = ExternalMpvSessionFactory(
        prefs = { DesktopPlayerPrefs() },
        settings = { AppSettings(playerEngine = engine) },
        dataDir = File(tmp, "data"),
        cacheDir = File(tmp, "cache"),
        runtimeDir = File(tmp, "run"),
        extraArgs = listOf("--vo=null", "--ao=null"),
        extraEnv = mapOf("MPV_HOME" to File(tmp, "mpv-home").apply { mkdirs() }.absolutePath, "DISPLAY" to null, "WAYLAND_DISPLAY" to null),
        frameGenAvailability = { LsfgVk.Availability(null, null, LsfgVk.Missing.LAYER) },
        refreshHz = { null },
    )

    @Test
    fun loadsPlaysPausesSeeksListsTracksSetsVolumeEndsAndClosesWithItsWindow() {
        assumeTrue("mpv not installed", findMpv(null) != null)
        val clip = makeClip()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val external = File(tmp, "en.srt").apply { writeText("1\n00:00:00,000 --> 00:00:05,000\nHello\n") }
        try {
            runBlocking(Dispatchers.Main) {
                val f = factory(PlayerEngine.EXTERNAL)
                assertTrue(f.wants(PlayerEngine.EXTERNAL))
                val session = f.create(scope, listener, remote = false, title = "Test \$clip · E1") as ExternalMpvPlayerSession
                assertEquals(ExternalMpvMode.Kind.USER_CONFIG, session.mode.value.config)
                session.setPreferredLanguages(subtitle = "es", audio = null)
                session.load(
                    PlayableSource(
                        clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip",
                        subtitles = listOf(SubtitleTrack(url = external.absolutePath, lang = "en", label = "English", format = "srt")),
                        localFile = true,
                    ),
                    startMs = 500,
                )

                // Load: started at the start position, duration, playing.
                var s = session.await("started") { it.started && it.isPlaying && it.durationMs > 0 }
                assertEquals(1, s.loadId)
                assertTrue(s.durationMs in 5_800..6_300, "duration ${s.durationMs}")
                assertTrue(s.positionMs >= 400, "start position ${s.positionMs}")

                // Tracks: the embedded Spanish one (picked by slang) and the source's external English one.
                s = session.await("tracks") { it.subtitles.size == 2 }
                assertEquals(listOf("spa", "en"), s.subtitles.map { it.language })
                assertEquals(listOf(0, null), s.subtitles.map { it.embeddedOrdinal })
                assertTrue(s.subtitles.first { it.language == "spa" }.selected, "slang picked the Spanish track")
                val en = s.subtitles.first { it.language == "en" }.groupIndex
                session.selectSubtitle(en)
                session.await("subtitle switched") { st -> st.subtitles.single { it.selected }.groupIndex == en }
                session.selectSubtitle(null)
                session.await("subtitles off") { !it.subtitlesEnabled }
                session.setSubtitleDelay(250)
                session.await("subtitle delay") { it.subtitleDelayMs == 250L }

                // Advances.
                val first = s.positionMs
                session.await("advancing") { it.positionMs >= first + 400 }

                // Pause holds the position and forces a progress tick.
                session.pause()
                session.await("paused") { !it.isPlaying }
                val pausedAt = session.currentPositionMs()
                delay(600)
                assertTrue(kotlin.math.abs(session.currentPositionMs() - pausedAt) < 80, "paused position moved")
                assertTrue(progress.any { it.third }, "forced progress on pause")

                // Seek while paused.
                session.seekTo(3_000)
                s = session.await("seeked") { kotlin.math.abs(it.positionMs - 3_000) < 150 }
                assertTrue(!s.isPlaying)

                // Speed, volume and mute (mpv's own volume, 0–100).
                session.setSpeed(1.5f)
                session.await("speed") { it.speed == 1.5f }
                session.setSpeed(1f)
                delay(300)
                assertEquals(PlayerVolume(90, muted = false), session.changeVolume(-10))
                delay(300)
                assertEquals(PlayerVolume(90, muted = true), session.toggleMute())
                delay(300)
                assertEquals(PlayerVolume(95, muted = false), session.changeVolume(5))

                // A screenshot of the current frame.
                val shot = File(tmp, "shot.png")
                val shotOk = session.screenshotToFile(shot.absolutePath)
                println("screenshot-to-file with vo=null: $shotOk (${shot.length()} bytes)")

                // Plays to the end: mpv then quits by itself (no keep-open in an empty config), and the session says the window closed.
                session.seekTo(5_000)
                session.play()
                s = session.await("ended") { it.ended }
                assertTrue(!s.isPlaying && s.error == null)
                withTimeoutOrNull(30_000) { session.windowClosed.first { it } } ?: fail("mpv did not exit after the end")
                // Review: the window closes only after the player state has the end (the end screen, not "back").
                assertTrue(session.state.value.ended, "ended before windowClosed")
                assertTrue(progress.last().third, "closing forces a progress tick")

                // Replay: play() after mpv exited starts it again, same load.
                session.seekTo(0)
                session.play()
                s = session.await("replay") { it.isPlaying && !it.ended && it.positionMs < 3_000 }
                assertEquals(1, s.loadId)
                assertTrue(!session.windowClosed.value)

                // The user closes the mpv window (here: `quit` from mpv's side): the session reports it, without an error.
                assertTrue(session.processForTest != null)
                session.processForTest?.send(listOf("quit"))
                withTimeoutOrNull(30_000) { session.windowClosed.first { it } } ?: fail("closing the window was not seen")
                assertTrue(session.state.value.error == null)

                // A file mpv cannot open is an error of that load, not a closed window.
                session.load(PlayableSource(File(tmp, "missing.mkv").absolutePath, emptyMap(), Container.PROGRESSIVE, "missing", localFile = true), startMs = 0)
                s = session.await("error") { it.loadId == 2 && it.error != null }
                println("missing file error: ${s.error}")
                assertTrue(!session.windowClosed.value)

                // HTTP: the source's headers reach the server (User-Agent, Referer and the rest as header fields), from the start position.
                val server = HeaderServer(clip)
                try {
                    session.load(
                        PlayableSource(server.url("/clip.mkv"), mapOf("Referer" to "https://nijiyome.test/ep/1", "X-Probe" to "a,b"), Container.PROGRESSIVE, "http"),
                        startMs = 2_000,
                    )
                    s = session.await("http plays") { it.loadId == 3 && it.started && it.isPlaying }
                    assertTrue(s.positionMs >= 1_900, "start position ${s.positionMs}")
                    val req = server.requests.firstOrNull { it.path == "/clip.mkv" } ?: fail("no request reached the server")
                    assertEquals("https://nijiyome.test/ep/1", req.headers["referer"])
                    assertEquals(EXTERNAL_DEFAULT_USER_AGENT, req.headers["user-agent"])
                    assertEquals("a,b", req.headers["x-probe"])
                } finally {
                    server.stop()
                }
                session.release()
                assertTrue(progress.last().third, "release forces a final progress tick")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                while (LiveProcesses.count() > 0 && System.nanoTime() < deadline) delay(100)
                assertEquals(0, LiveProcesses.count(), "no mpv outlives the session")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingTheSessionWhileMpvStartsLeavesNothingBehind() {
        assumeTrue("mpv not installed", findMpv(null) != null)
        val clip = makeClip()
        val run = File(tmp, "run")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            runBlocking(Dispatchers.Main) {
                // Back / Stop / another source at every moment of the start: before the IO work, while mpv starts, while it connects.
                for (waitMs in listOf(0L, 5L, 20L, 50L, 100L, 200L, 400L)) {
                    val session = factory(PlayerEngine.EXTERNAL).create(scope, listener, remote = false, title = "Cancel") as ExternalMpvPlayerSession
                    session.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
                    delay(waitMs)
                    if (waitMs % 2 == 0L) {
                        session.release()
                    } else {
                        // a new load replaces the starting one, then the session closes
                        session.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
                        delay(waitMs)
                        session.release()
                    }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while ((LiveProcesses.count() > 0 || commandThreads() > 0 || sockets(run) > 0) && System.nanoTime() < deadline) delay(100)
                assertEquals(0, LiveProcesses.count(), "no mpv outlives a closed session")
                assertEquals(0, commandThreads(), "no command thread left")
                assertEquals(0, sockets(run), "no socket left in ${run.path}")
                assertEquals(0, countMpvProcesses(run), "no mpv process of this test left")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aRefusedUrlNeverStartsMpvAndFailsTheLoad() {
        assumeTrue("mpv not installed", findMpv(null) != null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            runBlocking(Dispatchers.Main) {
                val session = factory(PlayerEngine.EXTERNAL).create(scope, listener, remote = false, title = "Refused") as ExternalMpvPlayerSession
                for ((i, url) in listOf("edl://%20%/etc/passwd", "lavfi://sine", File(tmp, "x.mkv").absolutePath).withIndex()) {
                    // an extension's source (not the app's own file): only http / https
                    session.load(PlayableSource(url, mapOf("X-Inject" to "a\r\nb"), Container.PROGRESSIVE, "bad"), startMs = 0)
                    val s = session.await("refused $url") { it.loadId == i + 1 && it.error != null }
                    assertEquals(ExternalMpvPlayerSession.REFUSED_URL, s.error?.name)
                    assertTrue(session.processForTest == null)
                }
                assertEquals(0, LiveProcesses.count())
                session.release()
            }
        } finally {
            scope.cancel()
        }
    }

    private fun commandThreads(): Int = Thread.getAllStackTraces().keys.count { it.isAlive && it.name == "mpv-external-cmd" }

    private fun sockets(dir: File): Int = dir.listFiles { f -> f.name.endsWith(".sock") }?.size ?: 0

    /** mpv processes whose command line points at this test's socket folder. */
    private fun countMpvProcesses(dir: File): Int = ProcessHandle.allProcesses()
        .filter { p -> p.info().commandLine().orElse("").let { it.contains("mpv") && it.contains(dir.path) } }.count().toInt()

    @Test
    fun theAppsOwnConfigPlaysWithTheQualityEngine() {
        assumeTrue("mpv not installed", findMpv(null) != null)
        val clip = makeClip()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            runBlocking(Dispatchers.Main) {
                // Engine libmpv + frame generation pulled it here: the app owns the config (frame generation itself is off in this test).
                val session = factory(PlayerEngine.MPV).create(scope, listener, remote = false, title = "App") as ExternalMpvPlayerSession
                assertEquals(ExternalMpvMode.Kind.APP_CONFIG, session.mode.value.config)
                assertTrue(session.mode.value.forcedByFrameGen)
                assertTrue(session.quality.value.available, "the shared quality engine runs")
                session.setQuality("anime4k-a", app.nijiyome.model.QualityTier.BALANCED)
                session.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
                session.await("started") { it.started && it.isPlaying }
                session.release()
            }
        } finally {
            scope.cancel()
        }
    }

    /** Serves [clip] at /clip.mkv with byte ranges (404 elsewhere) and records each request's headers (lower-case names). */
    private class HeaderServer(private val clip: File) {
        data class Req(val path: String, val headers: Map<String, String>)

        val requests = CopyOnWriteArrayList<Req>()
        private val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                requests += Req(ex.requestURI.path, ex.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") })
                if (ex.requestURI.path == "/clip.mkv") {
                    val bytes = clip.readBytes()
                    val from = ex.requestHeaders.getFirst("Range")?.let { Regex("bytes=(\\d+)-").find(it)?.groupValues?.get(1)?.toInt() }
                    ex.responseHeaders.add("Content-Type", "video/x-matroska")
                    ex.responseHeaders.add("Accept-Ranges", "bytes")
                    val start = (from ?: 0).coerceIn(0, bytes.size)
                    if (from != null) {
                        ex.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                        ex.sendResponseHeaders(206, (bytes.size - start).toLong())
                    } else {
                        ex.sendResponseHeaders(200, bytes.size.toLong())
                    }
                    ex.responseBody.use { runCatching { it.write(bytes, start, bytes.size - start) } }
                } else {
                    ex.sendResponseHeaders(404, -1)
                    ex.close()
                }
            }
            start()
        }

        fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

        fun stop() = server.stop(0)
    }

    private suspend fun ExternalMpvPlayerSession.await(what: String, timeoutMs: Long = 30_000, p: (PlaybackState) -> Boolean): PlaybackState =
        withTimeoutOrNull(timeoutMs) { state.first(p) } ?: fail("timed out waiting for $what: ${state.value}")

    /** 6 s, 320x240 testsrc2 at 24 fps, no audio, one SRT track in Spanish (Matroska). A missing ffmpeg skips the test. */
    private fun makeClip(): File {
        val bin = System.getenv("NIJIYOME_FFMPEG") ?: "ffmpeg"
        val ok = runCatching {
            ProcessBuilder(bin, "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
        }.getOrDefault(false)
        assumeTrue("ffmpeg not available", ok)
        val srt = File(tmp, "es.srt").apply { writeText("1\n00:00:00,000 --> 00:00:05,000\nHola\n") }
        val out = File(tmp, "clip.mkv")
        if (out.length() > 0) return out
        val cmd = listOf(
            bin, "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=24:duration=6",
            "-i", srt.absolutePath,
            "-map", "0:v", "-map", "1:s", "-an",
            "-c:v", "libx264", "-preset", "ultrafast", "-g", "24", "-c:s", "srt", "-metadata:s:s:0", "language=spa",
            out.absolutePath,
        )
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assumeTrue("ffmpeg could not make the clip: $log", p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && out.length() > 0)
        return out
    }
}

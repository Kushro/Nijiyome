// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.model.Container
import app.nijiyome.player.MPV_OPEN_ERROR_CODE
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.PlayerVolume
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hito 4: the desktop engine against real libmpv (MediaMP's runtime) with a clip made by ffmpeg (lavfi: 3 s of testsrc2, two audio
 * tracks, one subtitle track). Audio goes to `ao=null`; no window is opened: without a [MpvVideoSurface] MediaMP opens the file with
 * `vo=null`, so this runs without a display (desktopTest is headless, see build.gradle.kts). Skipped (Assume) when ffmpeg is missing
 * or the native runtime cannot be loaded on this machine.
 */
class DesktopMpvEngineIntegrationTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-mpv-it").toFile()
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

    @Test
    fun playsSeeksPausesListsTracksAndEnds() {
        val clip = makeClip()
        val factory = DesktopMpvSessionFactory(
            cacheDir = File(tmp, "cache"),
            mainDispatcher = Dispatchers.Main,
            extraOptions = mapOf("ao" to "null"),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val server = HeaderServer(clip)
        try {
            runBlocking(Dispatchers.Main) {
                val session = try {
                    factory.create(scope, listener, remote = false)
                } catch (t: Throwable) {
                    assumeNoException("libmpv runtime not loadable here", t)
                    error("unreachable")
                }
                session.setPreferredLanguages(subtitle = "es", audio = "en")
                session.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)

                // Load: duration, tracks (two audio, one subtitle picked by the preferred language), playing.
                var s = session.await("started") { it.started && it.isPlaying && it.durationMs > 0 }
                assertEquals(1, s.loadId)
                assertTrue(s.durationMs in 2_800..3_300, "duration ${s.durationMs}")
                s = session.await("tracks") { it.audioTracks.size == 2 && it.subtitles.size == 1 }
                assertEquals(listOf("jpn", "eng"), s.audioTracks.map { it.language })
                assertEquals("eng", s.audioTracks.single { it.selected }.language, "alang from setPreferredLanguages")
                assertEquals("spa", s.subtitles.single().language)
                assertTrue(s.subtitlesEnabled, "slang picked the Spanish track")
                assertEquals(0, s.subtitles.single().embeddedOrdinal)

                // Position advances.
                session.await("advancing") { it.positionMs >= 600 }

                // Pause holds the position and forces a progress tick.
                session.pause()
                s = session.await("paused") { !it.isPlaying }
                val pausedAt = session.currentPositionMs()
                delay(500)
                assertTrue(kotlin.math.abs(session.currentPositionMs() - pausedAt) < 60, "paused position moved")
                assertTrue(progress.any { it.third }, "forced progress on pause")

                // Seek while paused stays paused at the new position.
                session.seekTo(2_000)
                s = session.await("seeked") { kotlin.math.abs(it.positionMs - 2_000) < 150 }
                assertTrue(!s.isPlaying)

                // Track selection.
                val jpn = s.audioTracks.first { it.language == "jpn" }.id
                session.selectAudio(jpn)
                session.await("audio switched") { st -> st.audioTracks.single { it.selected }.id == jpn }
                session.selectSubtitle(null)
                session.await("subs off") { !it.subtitlesEnabled }

                // A local subtitle file becomes the selected track.
                val extra = File(tmp, "extra.srt").apply { writeText("1\n00:00:00,000 --> 00:00:03,000\nExtra\n") }
                assertTrue(session.addSubtitleFile(extra.absolutePath, "Extra"))
                s = session.await("external sub") { st -> st.subtitles.size == 2 && st.subtitles.any { it.selected && it.label == "Extra" } }
                assertNull(s.subtitles.single { it.label == "Extra" }.embeddedOrdinal)
                session.setSubtitleDelay(250)
                session.await("sub delay") { it.subtitleDelayMs == 250L }

                // The player's own volume: steps within 0–100, mute keeps the level, a step unmutes.
                assertEquals(PlayerVolume(95, muted = false), session.changeVolume(-5))
                assertEquals(PlayerVolume(100, muted = false), session.changeVolume(50))
                assertEquals(PlayerVolume(100, muted = true), session.toggleMute())
                assertEquals(PlayerVolume(90, muted = false), session.changeVolume(-10))

                // Plays to the end.
                session.play()
                s = session.await("ended", 8_000) { it.ended }
                assertTrue(!s.isPlaying && s.error == null)
                val shot = File(tmp, "shot.png")
                println("screenshot-to-file with vo=null: ${session.screenshotToFile(shot.absolutePath)} (${shot.length()} bytes)")

                // play() at the end replays from the start.
                session.play()
                session.await("replay") { it.isPlaying && !it.ended && it.positionMs < 2_000 }

                // HTTP: the source's headers reach the server (User-Agent, Referer and the rest as header fields).
                session.load(
                    PlayableSource(server.url("/clip.mkv"), mapOf("Referer" to "https://nijiyome.test/ep/1", "X-Probe" to "a,b"), Container.PROGRESSIVE, "http"),
                    startMs = 1_000,
                )
                s = session.await("http started") { it.loadId == 2 && it.started }
                assertTrue(s.positionMs >= 900, "start position ${s.positionMs}")
                val req = assertNotNull(server.requests.firstOrNull { it.path == "/clip.mkv" })
                assertEquals("https://nijiyome.test/ep/1", req.headers["referer"])
                assertEquals(MpvEngineDefaults.DEFAULT_USER_AGENT, req.headers["user-agent"])
                assertEquals("a,b", req.headers["x-probe"])

                // A 404 of a watched (network) source before any frame: MPV_OPEN_ERROR_CODE, like Android.
                session.load(PlayableSource(server.url("/missing.mkv"), mapOf("Referer" to "r"), Container.MP4, "404"), startMs = 0)
                s = session.await("404") { it.loadId == 3 && it.error != null }
                val err = s.error!!
                println("404 error: code=${err.code} name=${err.name}")
                assertEquals(MPV_OPEN_ERROR_CODE, err.code)
                assertTrue(!s.isBuffering && !s.started)

                session.release()
                assertTrue(progress.last().third, "release forces a final progress tick")

                // The next session of the same factory (runtime already prepared) has its own mpv and plays.
                val again = factory.create(scope, listener, remote = false)
                assertEquals(0, again.state.value.loadId)
                again.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
                s = again.await("second session plays") { it.loadId == 1 && it.started && it.isPlaying }
                assertTrue(s.subtitleDelayMs == 0L && s.secondarySubtitleId == null)
                again.release()
            }
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    private suspend fun PlayerSession.await(what: String, timeoutMs: Long = 10_000, p: (PlaybackState) -> Boolean): PlaybackState =
        withTimeoutOrNull(timeoutMs) { state.first(p) } ?: fail("timed out waiting for $what: ${state.value}")

    /**
     * 3 s, 320x240 testsrc2 at 24 fps, sine audio in Japanese and English, one SRT track in Spanish (Matroska). `NIJIYOME_FFMPEG`
     * overrides the ffmpeg binary (a missing one skips the test).
     */
    private fun makeClip(): File {
        val bin = System.getenv("NIJIYOME_FFMPEG") ?: "ffmpeg"
        val ffmpeg = runCatching {
            ProcessBuilder(bin, "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
        }.getOrDefault(false)
        assumeTrue("ffmpeg not available", ffmpeg)
        val srt = File(tmp, "es.srt").apply { writeText("1\n00:00:00,000 --> 00:00:02,500\nHola\n") }
        val out = File(tmp, "clip.mkv")
        val cmd = listOf(
            bin, "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=24:duration=3",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
            "-f", "lavfi", "-i", "sine=frequency=880:duration=3",
            "-i", srt.absolutePath,
            "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s",
            "-c:v", "libx264", "-preset", "ultrafast", "-g", "24", "-c:a", "aac", "-c:s", "srt",
            "-metadata:s:a:0", "language=jpn", "-metadata:s:a:1", "language=eng", "-metadata:s:s:0", "language=spa",
            out.absolutePath,
        )
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assumeTrue("ffmpeg could not make the clip: $log", p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && out.length() > 0)
        return out
    }

    /** Serves [clip] at /clip.mkv (404 elsewhere) and records each request's headers (lower-case names). */
    private class HeaderServer(private val clip: File) {
        data class Req(val path: String, val headers: Map<String, String>)

        val requests = CopyOnWriteArrayList<Req>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                requests += Req(ex.requestURI.path, ex.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") })
                if (ex.requestURI.path == "/clip.mkv") {
                    // Byte ranges, so mpv can seek in the stream (the start position needs it).
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
}

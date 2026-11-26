// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-link/tests/stall.rs and tests/subtitles.rs (AGPL-3.0, same author): back-pressure with an encoder that
// does not wait, stalled runs, early / real ends, the RAM window around a paused playhead, and the source's subtitles.
package app.nijiyome.server.link

import app.nijiyome.server.link.media.FfmpegEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StallSubtitlesTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private fun h(engine: FakeEngine, tuning: SessionTuning) = harness(engine, tuning).also { open += it }

    private fun session(h: Harness, p: Paired, segments: Int): String {
        val body = mediaPlaylist(segments)
        val port = Stub { text(200, body) }.also { open += it }.port
        return sessionAt(h, p, port)
    }

    private fun sessionAt(h: Harness, p: Paired, port: Int): String {
        val r = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"url","url":"http://127.0.0.1:$port/list.m3u8","container":"hls"}}""")
        assertEquals(201, r.code)
        return r.obj()["session_id"]!!.jsonPrimitive.content
    }

    private fun status(h: Harness, p: Paired, sid: String): JsonObject = p.client.get(h.url("/v1/sessions/$sid")).obj()

    private fun heartbeat(h: Harness, p: Paired, sid: String, secs: Double, paused: Boolean) =
        assertEquals(204, p.client.call("POST", h.url("/v1/sessions/$sid/position"), """{"position_secs":$secs,"paused":$paused}""").code)

    private fun get(h: Harness, p: Paired, sid: String, file: String) = p.client.get(h.url("/v1/sessions/$sid/$file"))

    private fun tuning() = SessionTuning(segmentWaitMs = 5_000, stallTimeoutMs = 600)

    // ---- stall.rs ----

    @Test
    fun anEncoderThatDoesNotWaitIsParkedDuringALongPauseAndResumedOnPlay() {
        val engine = FakeEngine(300, 1000, 5).apply { noWait = true }
        val h = h(engine, tuning())
        val p = h.pair("Tab")
        val sid = session(h, p, 300)
        heartbeat(h, p, sid, 0.0, true)
        waitUntil("the run to be parked", 400) { status(h, p, sid)["parked"]!!.jsonPrimitive.content == "true" }
        assertEquals("true", status(h, p, sid)["throttled"]!!.jsonPrimitive.content)
        val made = engine.sent.size
        assertTrue(made <= 2 * 15 + 3, "the run went on during the pause: $made segments sent")
        repeat(6) {
            Thread.sleep(250)
            heartbeat(h, p, sid, 0.0, true)
        }
        assertEquals(made, engine.sent.size, "a parked run makes nothing")
        assertEquals(listOf(0), engine.starts.toList())
        for (k in 0 until 80) {
            heartbeat(h, p, sid, k * 2.0, false)
            val started = System.nanoTime()
            val r = get(h, p, sid, "seg_$k.m4s")
            assertEquals(200, r.code, "seg_$k")
            assertEquals((k % 251).toByte(), r.body.bytes()[0], "seg_$k")
            assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "seg_$k")
        }
        val starts = engine.starts.toList()
        assertTrue(starts.size >= 2 && starts[1] > 0, "production resumed where it stopped: $starts")
    }

    @Test
    fun aRunThatStopsProducingWithoutExitingIsRestartedWhereItStopped() {
        val engine = FakeEngine(100, 100, 0).apply { scripts += RunScript.HangAfter(5) }
        val h = h(engine, tuning())
        val p = h.pair("Tab")
        val sid = session(h, p, 100)
        for (k in 0 until 5) assertEquals(200, get(h, p, sid, "seg_$k.m4s").code)
        heartbeat(h, p, sid, 8.0, false)
        val r = get(h, p, sid, "seg_5.m4s")
        assertEquals(200, r.code, "the stalled run was restarted")
        assertEquals(5.toByte(), r.body.bytes()[0])
        assertEquals(listOf(0, 5), engine.starts.toList())
    }

    @Test
    fun aSessionWhoseRunsKeepStallingFailsWithSegmentUnavailable() {
        val engine = FakeEngine(100, 100, 0).apply {
            scripts += RunScript.HangAfter(2)
            repeat(6) { scripts += RunScript.HangAfter(0) }
        }
        val h = h(engine, tuning().copy(stallTimeoutMs = 300))
        val p = h.pair("Tab")
        val sid = session(h, p, 100)
        assertEquals(200, get(h, p, sid, "seg_1.m4s").code)
        waitUntil("the session to fail", 400) { status(h, p, sid)["state"]!!.jsonPrimitive.content == "failed" }
        assertEquals("engine_stalled", status(h, p, sid)["error"]!!.jsonPrimitive.content)
        assertEquals(listOf(0, 2, 2, 2), engine.starts.toList(), "three restarts where it stopped, then it gave up")
        for (file in listOf("seg_2.m4s", "init.mp4")) {
            val r = get(h, p, sid, file)
            assertEquals(410, r.code, file)
            assertEquals("segment_unavailable", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content, file)
        }
    }

    @Test
    fun theRealEndOfTheSourceIsReportedAndNeverRestarted() {
        val engine = FakeEngine(10, 100, 0)
        val h = h(engine, tuning())
        val p = h.pair("Tab")
        val sid = session(h, p, 12)
        waitUntil("the end of the source", 400) { status(h, p, sid)["ended"]?.jsonPrimitive?.content == "true" }
        val st = status(h, p, sid)
        assertEquals(20.0, st["end_secs"]!!.jsonPrimitive.double, st.toString())
        assertEquals("ready", st["state"]!!.jsonPrimitive.content)
        assertEquals(200, get(h, p, sid, "seg_9.m4s").code)
        for (k in listOf(10, 11)) assertEquals(404, get(h, p, sid, "seg_$k.m4s").code, "seg_$k")
        assertEquals(listOf(0), engine.starts.toList(), "nothing restarts at the end")
    }

    @Test
    fun anInputThatBreaksEarlyDropsTheCutSegmentAndGoesOnFromThere() {
        val engine = FakeEngine(100, 100, 0).apply { scripts += RunScript.StopAfter(6) }
        val h = h(engine, tuning())
        val p = h.pair("Tab")
        val sid = session(h, p, 100)
        waitUntil("the early end", 400) {
            engine.putResults.size >= 6 &&
                status(h, p, sid)["produced"] == Json.parseToJsonElement("""{"from_secs":0.0,"to_secs":10.0}""")
        }
        assertFalse("ended" in status(h, p, sid))
        assertEquals(200, get(h, p, sid, "seg_5.m4s").code)
        assertEquals(listOf(0, 5), engine.starts.toList(), "segment 5 was made again by a new run")
    }

    @Test
    fun segmentsAfterAPausedPlayheadAreNeverEvictedOverBudget() {
        val engine = FakeEngine(200, 1000, 0)
        val h = h(engine, tuning().copy(maxRamBytes = 5_000))
        val p = h.pair("Tab")
        val sid = session(h, p, 200)
        heartbeat(h, p, sid, 0.0, true)
        waitUntil("back-pressure", 400) { status(h, p, sid)["throttled"]!!.jsonPrimitive.content == "true" }
        for (k in listOf(0, 1, 10, 15)) assertEquals(200, get(h, p, sid, "seg_$k.m4s").code, "seg_$k")
        assertEquals(listOf(0), engine.starts.toList(), "served from RAM, no restart")
        assertEquals(0.0, status(h, p, sid)["produced"]!!.jsonObject["from_secs"]!!.jsonPrimitive.double)
    }

    @Test
    fun shortSeeksInsideTheWindowDoNotRestart() {
        val engine = FakeEngine(200, 100, 0)
        val h = h(engine, tuning())
        val p = h.pair("Tab")
        val sid = session(h, p, 200)
        for (k in 0 until 30) {
            heartbeat(h, p, sid, k * 2.0, false)
            assertEquals(200, get(h, p, sid, "seg_$k.m4s").code)
        }
        for (k in listOf(24, 4, 34)) {
            heartbeat(h, p, sid, k * 2.0, false)
            val started = System.nanoTime()
            val r = get(h, p, sid, "seg_$k.m4s")
            assertEquals(200, r.code)
            assertEquals((k % 251).toByte(), r.body.bytes()[0])
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000, "seg_$k")
        }
        assertEquals(listOf(0), engine.starts.toList())
    }

    // ---- subtitles.rs ----

    private val tracks = """{"streams":[
        {"codec_name":"ass","disposition":{"default":1,"forced":0},"tags":{"language":"eng","title":"English"}},
        {"codec_name":"ass","disposition":{"default":0,"forced":0},"tags":{"language":"spa","title":"Spanish(Latin America)"}},
        {"codec_name":"subrip","disposition":{"default":0,"forced":1},"tags":{"language":"eng","title":"Signs"}},
        {"codec_name":"hdmv_pgs_subtitle","tags":{"language":"por"}}
    ]}"""

    private fun engineWithTracks() = FakeEngine(3, 100, 1).apply { subtitleProbe = tracks }

    private fun upstream(): Int {
        val body = mediaPlaylist(3)
        return Stub { text(200, body) }.also { open += it }.port
    }

    private fun renew(h: Harness, p: Paired, sid: String): String {
        val r = p.client.call("POST", h.url("/v1/sessions"), """{"source":{"kind":"session","session_id":"$sid"},"start_secs":2.0}""")
        assertEquals(201, r.code)
        return r.obj()["session_id"]!!.jsonPrimitive.content
    }

    private fun file(h: Harness, p: Paired, sid: String, name: String) = p.client.get(h.url("/v1/sessions/$sid/subtitles/$name"))

    private fun listed(h: Harness, p: Paired, sid: String): JsonObject {
        waitUntil("the subtitle listing") { "subtitles" in status(h, p, sid) }
        return status(h, p, sid)
    }

    @Test
    fun aSessionListsTheSourceSubtitlesAndServesEachTextTrackFromOnePass() {
        val engine = engineWithTracks()
        val h = h(engine, quickTuning())
        val p = h.pair("Pixel")
        val other = h.pair("Tablet")
        val sid = sessionAt(h, p, upstream())
        val st = listed(h, p, sid)
        assertEquals("idle", st["subtitles_state"]!!.jsonPrimitive.content, "listed; nothing read until the phone asks")
        assertEquals(
            Json.parseToJsonElement(
                """[
                {"index":0,"language":"eng","title":"English","codec":"ass","default":true,"forced":false,"text":true,"formats":["ass","vtt"]},
                {"index":1,"language":"spa","title":"Spanish(Latin America)","codec":"ass","default":false,"forced":false,"text":true,"formats":["ass","vtt"]},
                {"index":2,"language":"eng","title":"Signs","codec":"subrip","default":false,"forced":true,"text":true,"formats":["vtt"]},
                {"index":3,"language":"por","codec":"hdmv_pgs_subtitle","default":false,"forced":false,"text":false,"formats":[]}
                ]""",
            ),
            st["subtitles"],
        )
        assertTrue(engine.extractions.isEmpty())
        val r = file(h, p, sid, "1.ass")
        assertEquals(200, r.code)
        assertEquals("text/x-ssa; charset=utf-8", r.header("Content-Type"))
        assertEquals("no-store", r.header("Cache-Control"))
        assertTrue("track 1" in r.body.string())
        val v = file(h, p, sid, "2.vtt")
        assertEquals("text/vtt; charset=utf-8", v.header("Content-Type"))
        assertTrue(v.body.string().startsWith("WEBVTT"))
        assertEquals(200, file(h, p, sid, "0.vtt").code)
        assertEquals("ready", status(h, p, sid)["subtitles_state"]!!.jsonPrimitive.content)
        assertEquals(1, engine.extractions.size)
        assertTrue(engine.extractions[0].startsWith("http://127.0.0.1:"))
        for (name in listOf("2.ass", "3.vtt", "9.vtt", "1.srt", "x.vtt", "1.vtt.gz")) assertEquals(404, file(h, p, sid, name).code, name)
        assertEquals(404, file(h, other, sid, "1.ass").code)
        assertEquals(204, p.client.call("DELETE", h.url("/v1/sessions/$sid")).code)
        assertEquals(410, file(h, p, sid, "1.ass").code)
    }

    @Test
    fun aLongExtractionAnswersRetryAfterUntilTheFilesAreReady() {
        val engine = engineWithTracks().apply { extractDelayMs = 1500 }
        val h = h(engine, quickTuning().copy(subtitleWaitMs = 300))
        val p = h.pair("Pixel")
        val sid = sessionAt(h, p, upstream())
        listed(h, p, sid)
        val r = file(h, p, sid, "0.ass")
        assertEquals(503, r.code)
        assertEquals("1", r.header("Retry-After"))
        assertEquals("segment_wait_timeout", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("extracting", status(h, p, sid)["subtitles_state"]!!.jsonPrimitive.content)
        assertEquals(503, file(h, p, sid, "1.vtt").code)
        waitUntil("the extraction") { file(h, p, sid, "0.ass").use { it.code == 200 } }
        assertEquals(1, engine.extractions.size)
    }

    @Test
    fun aProfileChangeKeepsTheExtractedSubtitlesAndRestartsAnUnfinishedPass() {
        val engine = engineWithTracks()
        val h = h(engine, quickTuning().copy(subtitleWaitMs = 500))
        val p = h.pair("Pixel")
        val port = upstream()
        val sid = sessionAt(h, p, port)
        listed(h, p, sid)
        assertEquals(200, file(h, p, sid, "1.ass").code)
        val renewed = renew(h, p, sid)
        val st = status(h, p, renewed)
        assertEquals(4, st["subtitles"]!!.jsonArray.size)
        assertEquals("ready", st["subtitles_state"]!!.jsonPrimitive.content)
        assertTrue("track 1" in file(h, p, renewed, "1.ass").body.string())
        assertEquals(1, engine.extractions.size)
        assertEquals(204, p.client.call("DELETE", h.url("/v1/sessions/$renewed")).code)
        val same = sessionAt(h, p, port)
        assertEquals("ready", status(h, p, same)["subtitles_state"]!!.jsonPrimitive.content)
        assertEquals(200, file(h, p, same, "0.vtt").code)
        assertEquals(1, engine.extractions.size)

        engine.extractDelayMs = 30_000
        val fresh = sessionAt(h, p, upstream())
        listed(h, p, fresh)
        assertEquals(503, file(h, p, fresh, "0.vtt").code)
        engine.extractDelayMs = 0
        val next = renew(h, p, fresh)
        assertEquals("idle", status(h, p, next)["subtitles_state"]!!.jsonPrimitive.content)
        assertEquals(200, file(h, p, next, "0.vtt").code)
        val passes = engine.extractions.toList()
        assertEquals(3, passes.size)
        assertTrue(passes[1] != passes[2], "the restarted pass reads the new session's input")
    }

    @Test
    fun withoutAListingThereAreNoSubtitlesAndAFailedPassIsUnavailable() {
        val engine = FakeEngine(3, 100, 1)
        val h = h(engine, quickTuning())
        val p = h.pair("Pixel")
        val sid = sessionAt(h, p, upstream())
        waitUntil("the failed listing") { status(h, p, sid)["subtitles_state"]?.jsonPrimitive?.content == "unavailable" }
        assertFalse("subtitles" in status(h, p, sid))
        assertEquals(404, file(h, p, sid, "0.vtt").code)

        engine.subtitleProbe = tracks
        engine.extractFails = true
        val sid2 = sessionAt(h, p, upstream())
        listed(h, p, sid2)
        val r = file(h, p, sid2, "0.ass")
        assertEquals(503, r.code)
        assertNull(r.header("Retry-After"), "final: the phone stops asking")
        assertEquals("source_unavailable", r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("failed", status(h, p, sid2)["subtitles_state"]!!.jsonPrimitive.content)
    }

    /** The real binaries (skipped where FFmpeg is missing): an MKV with an ASS and an SRT track, listed and extracted in one pass. */
    @Test
    fun realFfmpegListsAndExtractsAssAndWebvttWithTheSourceTimeline() {
        val ffmpeg = File("/usr/bin/ffmpeg")
        if (!ffmpeg.isFile || !File("/usr/bin/ffprobe").isFile) return println("ffmpeg / ffprobe not installed: skipped")
        val dir = Files.createTempDirectory("subs-real").toFile()
        try {
            val ass = File(dir, "in.ass").apply {
                writeText(
                    "[Script Info]\nScriptType: v4.00+\nPlayResX: 640\nPlayResY: 360\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Sign,Arial,40,&H0000FFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,8,10,10,10,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.50,0:00:03.00,Sign,,0,0,0,,{\\pos(320,40)}Hola PC\n",
                )
            }
            val srt = File(dir, "in.srt").apply { writeText("1\n00:00:02,000 --> 00:00:03,500\nSigns line\n") }
            val mkv = File(dir, "ep.mkv")
            val made = ProcessBuilder(
                ffmpeg.path, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=black:s=64x64:r=24:d=4", "-i", ass.path, "-i", srt.path,
                "-map", "0:v", "-map", "1:s", "-map", "2:s", "-c:v", "ffv1", "-c:s:0", "copy", "-c:s:1", "srt",
                "-metadata:s:s:0", "language=spa", "-metadata:s:s:0", "title=Español (Latinoamérica)", "-disposition:s:0", "default",
                "-metadata:s:s:1", "language=eng", "-metadata:s:s:1", "title=Signs", mkv.path,
            ).redirectErrorStream(true).start().waitFor()
            assertEquals(0, made, "could not build the test MKV")
            val bytes = mkv.readBytes()
            val stub = Stub { Canned(200, listOf("content-type" to "video/x-matroska"), bytes) }.also { open += it }
            val input = "http://127.0.0.1:${stub.port}/ep.mkv"
            val engine = FfmpegEngine(ffmpeg)
            val t = runBlocking { engine.subtitleTracks(input) } ?: error("listing")
            assertEquals(2, t.size)
            assertEquals(Triple("spa", "Español (Latinoamérica)", true), Triple(t[0].language, t[0].title, t[0].default))
            assertEquals("ass" to listOf("ass", "vtt"), t[0].codec to t[0].formats)
            assertEquals("subrip" to listOf("vtt"), t[1].codec to t[1].formats)
            val out = File(dir, "out").apply { mkdirs() }
            val outputs = Subtitles.planOutputs(t, out)
            runBlocking { engine.extractSubtitles(input, outputs) }
            val assOut = File(out, "0.ass").readText()
            assertTrue("Style: Sign,Arial,40" in assOut, assOut)
            assertTrue("0:00:01.50,0:00:03.00,Sign" in assOut && "\\pos(320,40)" in assOut, assOut)
            val vtt = File(out, "0.vtt").readText()
            assertTrue(vtt.startsWith("WEBVTT") && "00:01.500 --> 00:03.000" in vtt && "Hola PC" in vtt, vtt)
            val signs = File(out, "1.vtt").readText()
            assertTrue("00:02.000 --> 00:03.500" in signs && "Signs line" in signs, signs)
        } finally {
            dir.deleteRecursively()
        }
    }
}

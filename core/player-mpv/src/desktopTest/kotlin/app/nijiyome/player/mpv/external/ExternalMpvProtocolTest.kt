// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.player.PlaybackState
import app.nijiyome.player.mpv.MpvState
import app.nijiyome.player.mpv.mpvPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** mpv's JSON IPC lines -> the external session's state, and the commands it writes. */
class ExternalMpvProtocolTest {
    private fun pc(name: String, data: String) = """{"event":"property-change","id":1,"name":"$name","data":$data}"""

    private fun run(vararg lines: String, from: ExternalMpvSnapshot = ExternalMpvSnapshot(mpv = MpvState(loadId = 1))): ExternalMpvSnapshot =
        lines.fold(from) { s, l -> parseIpcLine(l)?.let { reduce(s, it) } ?: s }

    @Test
    fun commandsAreJsonArraysWithAnOptionalRequestId() {
        assertEquals("""{"command":["set","pause","yes"]}""", ipcCommand(listOf("set", "pause", "yes")))
        assertEquals("""{"command":["seek",12.5,"absolute"],"request_id":7}""", ipcCommand(listOf("seek", 12.5, "absolute"), 7))
        assertEquals("""{"command":["observe_property",3,"pause"]}""", ipcCommand(listOf("observe_property", 3, "pause")))
        // Strings are escaped (titles and paths with quotes / backslashes).
        assertEquals("""{"command":["sub-add","C:\\a \"b\".srt"]}""", ipcCommand(listOf("sub-add", "C:\\a \"b\".srt")))
        assertEquals("""{"command":["set_property","http-header-fields",["A: 1","B: 2"]]}""", ipcCommand(listOf("set_property", "http-header-fields", listOf("A: 1", "B: 2"))))
        val hello = observeCommands()
        assertEquals(OBSERVED_PROPERTIES.size, hello.size)
        assertEquals("""{"command":["observe_property",1,"time-pos"]}""", hello.first())
    }

    @Test
    fun linesParseIntoEventsRepliesOrNothing() {
        assertEquals(IpcMessage.PropertyChange("time-pos", kotlinx.serialization.json.JsonPrimitive(12.5)), parseIpcLine(pc("time-pos", "12.5")))
        assertEquals(IpcMessage.PropertyChange("time-pos", null), parseIpcLine(pc("time-pos", "null")))
        val reply = parseIpcLine("""{"request_id":4,"error":"success","data":null}""") as IpcMessage.Reply
        assertEquals(4L, reply.requestId)
        assertEquals("success", reply.error)
        assertTrue(parseIpcLine("""{"event":"end-file","reason":"eof","playlist_entry_id":1}""") is IpcMessage.Event)
        assertNull(parseIpcLine("not json"))
        assertNull(parseIpcLine("[1,2]"))
        assertNull(parseIpcLine(""))
    }

    @Test
    fun loadStartPlayPauseAndEndFollowTheEmbeddedEngine() {
        var s = run("""{"event":"start-file","playlist_entry_id":1}""")
        assertTrue(s.mpv.buffering && !s.mpv.started && !s.fileLoaded)
        s = run(pc("pause", "false"), """{"event":"file-loaded"}""", pc("duration", "5.0"), pc("core-idle", "false"), from = s)
        assertTrue(s.fileLoaded && s.mpv.playing)
        assertEquals(5.0, s.mpv.durationSecs)
        s = run("""{"event":"playback-restart"}""", pc("time-pos", "1.25"), pc("demuxer-cache-time", "4.0"), from = s)
        assertTrue(s.mpv.started && !s.mpv.buffering && s.mpv.playing)
        assertEquals(1.25, s.mpv.positionSecs)
        // Pause / buffering.
        s = run(pc("pause", "true"), from = s)
        assertFalse(s.mpv.playing)
        assertTrue(s.paused)
        s = run(pc("core-idle", "true"), pc("pause", "false"), pc("paused-for-cache", "true"), from = s)
        assertTrue(s.mpv.buffering)
        s = run(pc("paused-for-cache", "false"), pc("core-idle", "false"), from = s)
        assertTrue(s.mpv.playing && !s.mpv.buffering)
        // Negative positions are clamped, non-numbers ignored.
        assertEquals(0.0, run(pc("time-pos", "-0.2"), from = s).mpv.positionSecs)
        assertEquals(1.25, run(pc("time-pos", "\"x\""), from = s).mpv.positionSecs)
        // The end: eof-reached or end-file eof.
        val ended = run(pc("eof-reached", "true"), from = s)
        assertTrue(ended.mpv.ended && !ended.mpv.playing && !ended.mpv.buffering)
        assertTrue(run("""{"event":"end-file","reason":"eof"}""", from = s).mpv.ended)
        // A stop / quit / redirect is not an end.
        assertFalse(run("""{"event":"end-file","reason":"quit"}""", from = s).mpv.ended)
        assertTrue(run("""{"event":"shutdown"}""", from = s).closed)
    }

    @Test
    fun eventsMissedBeforeTheSessionConnectedDoNotLeaveItWaiting() {
        // The verificar failure: mpv loaded and started the file before the session connected, so `file-loaded` and
        // `playback-restart` never reached it; only the observed properties came (mpv sends their values on subscription).
        var s = run(pc("time-pos", "0.5"), pc("duration", "6.0"), pc("pause", "true"), pc("core-idle", "true"), pc("paused-for-cache", "false"))
        assertTrue(s.fileLoaded, "a position / duration means the file is loaded")
        assertFalse(s.mpv.started, "nothing played yet (mpv opens paused)")
        assertTrue(s.mpv.buffering && !s.mpv.playing)
        // A position change while paused (the start seek) is not playback.
        s = run(pc("time-pos", "0.6"), from = s)
        assertFalse(s.mpv.started)
        // Unpaused and advancing: started, playing, not buffering.
        s = run(pc("pause", "false"), pc("core-idle", "false"), pc("time-pos", "0.7"), from = s)
        assertTrue(s.mpv.started && s.mpv.playing && !s.mpv.buffering)
        // The end is seen from the property too.
        assertTrue(run(pc("eof-reached", "true"), from = s).mpv.ended)
        // A new file starts over.
        val next = run("""{"event":"start-file"}""", from = s)
        assertFalse(next.fileLoaded || next.mpv.started)
        assertEquals(null, next.firstPositionSecs)
    }

    @Test
    fun aFrameOnTheVideoOutputMeansStartedEvenWhilePaused() {
        // Review: the session connected after `playback-restart` and mpv stays paused (the user paused while it opened).
        var s = run(pc("vo-configured", "true"), pc("pause", "true"), pc("core-idle", "true"))
        assertFalse(s.mpv.started, "no file yet")
        s = run(pc("time-pos", "0.0"), from = s)
        assertTrue(s.mpv.started && !s.mpv.buffering && !s.mpv.playing)
        assertFalse(run("""{"event":"start-file"}""", from = s).voConfigured)
    }

    @Test
    fun goingBackFromTheEndClearsIt() {
        // keep-open: the end, then a seek back inside mpv's window.
        var s = run(pc("time-pos", "1.0"), pc("pause", "false"), pc("core-idle", "false"), pc("time-pos", "5.9"), pc("eof-reached", "true"))
        assertTrue(s.mpv.ended && !s.mpv.playing)
        s = run(pc("eof-reached", "false"), from = s)
        assertFalse(s.mpv.ended)
        assertTrue(s.mpv.playing)
        assertFalse(run(pc("eof-reached", "true"), """{"event":"playback-restart"}""", from = s).mpv.ended, "a restart is playback again")
        // Once the file was unloaded (end-file), a late `eof-reached=false` does not undo the end.
        val unloaded = run(pc("eof-reached", "true"), """{"event":"end-file","reason":"eof"}""", pc("eof-reached", "false"), from = s)
        assertTrue(unloaded.mpv.ended)
    }

    @Test
    fun aLoadErrorCarriesMpvsReason() {
        val s = run("""{"event":"start-file"}""", """{"event":"end-file","reason":"error","file_error":"loading failed"}""")
        assertEquals("mpv could not play the file: loading failed", s.mpv.error)
        assertFalse(s.mpv.buffering || s.mpv.playing)
        // eof-reached before file-loaded is not an end (an empty file list).
        assertFalse(run(pc("eof-reached", "true")).mpv.ended)
    }

    @Test
    fun trackListSubtitleDelayLoopAndSecondaryTrack() {
        val list = """[
            {"id":1,"type":"video","codec":"h264","selected":true},
            {"id":1,"type":"sub","codec":"subrip","lang":"spa","selected":true,"external":false},
            {"id":2,"type":"sub","codec":"ass","title":"Signs","external":true},
            {"id":1,"type":"audio","codec":"aac","lang":"jpn","selected":true},
            {"type":"sub"}
        ]"""
        val s = run(pc("track-list", list), pc("sub-delay", "0.25"), pc("loop-file", "\"inf\""), pc("secondary-sid", "2"))
        assertEquals(listOf(1, 2), s.mpv.subtitles.map { it.id })
        assertTrue(s.mpv.subtitles[0].selected && !s.mpv.subtitles[0].external)
        assertTrue(s.mpv.subtitles[1].external)
        assertEquals("Signs", s.mpv.subtitles[1].title)
        assertEquals("jpn", s.mpv.audio.single().lang)
        assertEquals(250L, s.mpv.subDelayMs)
        assertTrue(s.mpv.loop)
        assertEquals(2, s.mpv.secondarySid)
        assertFalse(run(pc("loop-file", "false"), from = s).mpv.loop)
        assertNull(run(pc("secondary-sid", "false"), from = s).mpv.secondarySid)

        // The shared mapping gives the player the same options as the embedded engine.
        val ps = mpvPlaybackState(PlaybackState(), s.mpv, loaded = true, endedHere = false, watched = false)
        assertEquals(listOf(0, null), ps.subtitles.map { it.embeddedOrdinal })
        assertTrue(ps.subtitlesEnabled)
        assertTrue(ps.subtitles[1].ass)
        assertEquals(250L, ps.subtitleDelayMs)
        assertEquals(2, ps.secondarySubtitleId)
    }

    @Test
    fun telemetryIsKeptAsText() {
        val s = run(
            pc("frame-drop-count", "3"), pc("estimated-display-fps", "59.94"), pc("current-vo", "\"gpu-next\""), pc("mute", "true"),
            pc("volume", "85.0"),
        )
        assertEquals("3", s.props["frame-drop-count"])
        assertEquals("59.94", s.props["estimated-display-fps"])
        assertEquals("gpu-next", s.props["current-vo"])
        assertEquals("yes", s.props["mute"])
        assertEquals("85.0", s.props["volume"])
        // A property that becomes unavailable is dropped.
        assertNull(run(pc("frame-drop-count", "null"), from = s).props["frame-drop-count"])
    }
}

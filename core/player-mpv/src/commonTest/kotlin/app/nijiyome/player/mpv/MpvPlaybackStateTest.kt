// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.MPV_OPEN_ERROR_CODE
import app.nijiyome.player.MPV_START_TIMEOUT_CODE
import app.nijiyome.player.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Hito 4: the MpvState -> PlaybackState mapping shared by the Android and desktop sessions.
class MpvPlaybackStateTest {
    private val tracks = MpvState(
        loadId = 3,
        started = true,
        playing = true,
        buffering = false,
        positionSecs = 12.5,
        durationSecs = 1440.25,
        cacheSecs = 40.0,
        speed = 1.5,
        subtitles = listOf(
            MpvTrack(1, "Signs", "eng", "ass", selected = false),
            MpvTrack(2, null, "spa", "subrip", selected = true),
            MpvTrack(3, null, null, "webvtt", selected = false, external = true),
        ),
        audio = listOf(MpvTrack(1, null, "jpn", "aac", selected = true), MpvTrack(2, "Dub", "eng", "opus", selected = false)),
        subText = "hola",
        secondarySid = 1,
        loop = true,
        subDelayMs = -250,
    )

    @Test
    fun mapsPositionTracksAndSubtitleExtras() {
        val s = mpvPlaybackState(PlaybackState(), tracks, loaded = true, endedHere = false, watched = false)
        assertEquals(3, s.loadId)
        assertTrue(s.started && s.isPlaying && !s.isBuffering && !s.ended)
        assertEquals(12_500, s.positionMs)
        assertEquals(1_440_250, s.durationMs)
        assertEquals(40_000, s.bufferedMs)
        assertEquals(1.5f, s.speed)
        assertEquals(listOf("Signs", "spa", "#3"), s.subtitles.map { it.label })
        assertEquals(listOf(0, 1, null), s.subtitles.map { it.embeddedOrdinal }, "sub-add tracks are not in the file")
        assertEquals(listOf(true, false, false), s.subtitles.map { it.ass })
        assertTrue(s.subtitlesEnabled)
        assertFalse(s.subtitleIsAss, "the selected track is SRT")
        assertEquals(listOf("jpn", "Dub"), s.audioTracks.map { it.label })
        assertTrue(s.fullAss)
        assertEquals("hola", s.subText)
        assertEquals(1, s.secondarySubtitleId)
        assertTrue(s.loop)
        assertEquals(-250, s.subtitleDelayMs)
        assertNull(s.error)
    }

    @Test
    fun errorCodeFollowsTheWatchdogRules() {
        val failed = MpvState(loadId = 1, started = false, error = "ffmpeg: http: HTTP error 404 Not Found")
        val watched = mpvPlaybackState(PlaybackState(), failed, loaded = true, endedHere = false, watched = true).error!!
        assertEquals(MPV_OPEN_ERROR_CODE, watched.code)
        assertEquals("ffmpeg: http: HTTP error 404 Not Found", watched.name)
        assertTrue(watched.exhausted)
        assertEquals(-1, mpvPlaybackState(PlaybackState(), failed, loaded = true, endedHere = false, watched = false).error!!.code)
        val timeout = failed.copy(error = START_TIMEOUT)
        assertEquals(MPV_START_TIMEOUT_CODE, mpvPlaybackState(PlaybackState(), timeout, true, false, watched = true).error!!.code)
        val afterStart = failed.copy(started = true)
        assertEquals(-1, mpvPlaybackState(PlaybackState(), afterStart, true, false, watched = true).error!!.code)
    }

    @Test
    fun bufferingOnlyForALoadAndNotWhileFailedOrEndedHere() {
        val loading = MpvState(buffering = true)
        assertFalse(mpvPlaybackState(PlaybackState(), loading, loaded = false, endedHere = false, watched = false).isBuffering)
        assertTrue(mpvPlaybackState(PlaybackState(), loading, loaded = true, endedHere = false, watched = false).isBuffering)
        assertFalse(mpvPlaybackState(PlaybackState(), loading.copy(error = "x"), true, false, false).isBuffering)
        val ended = mpvPlaybackState(PlaybackState(), loading.copy(error = "x"), loaded = true, endedHere = true, watched = false)
        assertTrue(ended.ended && !ended.isBuffering)
        assertNull(ended.error, "finishAsEnded hides the stream's error")
    }

    @Test
    fun bufferedPositionIsNeverBehindThePlayhead() {
        assertEquals(30_000, mpvBufferedMs(30.0, 10.0))
        assertEquals(45_000, mpvBufferedMs(30.0, 45.0))
    }
}

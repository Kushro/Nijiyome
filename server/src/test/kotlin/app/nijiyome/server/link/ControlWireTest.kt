// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of the unit tests of Tatakai Next tk-link/src/control.rs (AGPL-3.0, same author). Encoding is the server's (Control.kt); where
// the Rust test decodes, the phone's real DTOs of :core:link decode here (they skip unknown event types on purpose, see below).
package app.nijiyome.server.link

import app.nijiyome.link.EventsPageDto
import app.nijiyome.link.PhoneEventDto
import app.nijiyome.link.PlayerStateDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlWireTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun parse(s: String) = ControlCommand.parse(obj(s))
    private val phoneJson = Json { ignoreUnknownKeys = true }
    private fun open(id: String, ep: Long) = ControlCommand.Open(id, ep, 0)

    @Test
    fun commandsAreValidated() {
        assertTrue(ControlCommand.valid(ControlCommand.Toggle))
        assertTrue(ControlCommand.valid(open("21", 1)))
        assertFalse(ControlCommand.valid(open(" ", 1)))
        assertFalse(ControlCommand.valid(open("21", 0)))
        assertFalse(ControlCommand.valid(ControlCommand.Seek(999_999)))
    }

    @Test
    fun eventsWireIsTypeTaggedAndCamelCase() {
        val page = eventsPage(listOf(PhoneEvent.Handoff("s1", "21", 3, 75, null)))
        assertEquals("""{"events":[{"type":"handoff","sessionId":"s1","mediaId":"21","episode":3,"positionSecs":75}]}""", page.toString())
        val back = phoneJson.decodeFromString(EventsPageDto.serializer(), """{"events":[{"type":"handoff","sessionId":"s2"}]}""")
        assertEquals(PhoneEventDto(type = "handoff", sessionId = "s2"), back.events[0])
        assertEquals(Triple(EVENTS_MAX_WAIT_SECS, 1, EVENTS_MAX_WAIT_SECS), Triple(clampWait(null), clampWait(0), clampWait(99)))
    }

    @Test
    fun wireFormIsCamelCaseAndTagged() {
        assertEquals(ControlCommand.Open("21", 3, 120), parse("""{"command":"open","mediaId":"21","episode":3,"positionSecs":120}"""))
        assertEquals(ControlCommand.Seek(9), parse("""{"command":"seek","seconds":9}"""))
        assertEquals(ControlCommand.Toggle, parse("""{"command":"toggle"}"""))
        val st = PlayerStateDto(playing = true, title = "x", episode = 3, positionSecs = 90, durationSecs = null, mediaId = "21")
        assertEquals("""{"playing":true,"title":"x","episode":3,"positionSecs":90,"mediaId":"21"}""", Wire.json.encodeToString(PlayerStateDto.serializer(), st))
    }

    @Test
    fun openBoundsAreExact() {
        assertTrue(ControlCommand.valid(open("a".repeat(128), 1)))
        assertFalse(ControlCommand.valid(open("a".repeat(129), 1)))
        assertFalse(ControlCommand.valid(open("21\n", 1)))
        assertFalse(ControlCommand.valid(open("", 1)))
        assertTrue(ControlCommand.valid(open("ext:toko:abc", UInt.MAX_VALUE.toLong())))
        assertTrue(ControlCommand.valid(ControlCommand.Seek(24 * 3600)))
        assertFalse(ControlCommand.valid(ControlCommand.Seek(24 * 3600 + 1)))
        for (c in listOf(ControlCommand.Play, ControlCommand.Pause, ControlCommand.Next, ControlCommand.Prev)) assertTrue(ControlCommand.valid(c))
    }

    @Test
    fun everySimpleCommandRoundTrips() {
        for ((c, name) in listOf(ControlCommand.Play to "play", ControlCommand.Pause to "pause", ControlCommand.Toggle to "toggle", ControlCommand.Next to "next", ControlCommand.Prev to "prev")) {
            assertEquals(c, parse("""{"command":"$name"}"""), name)
        }
    }

    @Test
    fun malformedCommandsAreRejected() {
        for (bad in listOf(
            """{"command":"rewind"}""", """{"command":"seek"}""", """{"command":"open","episode":1}""", """{"command":"seek","seconds":-1}""",
            """{"seconds":5}""", """{"command":"Play"}""",
        )) {
            assertNull(parse(bad), bad)
        }
        assertEquals(ControlCommand.Open("21", 2, 0), parse("""{"command":"open","mediaId":"21","episode":2}"""), "positionSecs is optional")
    }

    @Test
    fun playerStateDefaultsAndMinimalWire() {
        val st = phoneJson.decodeFromString(PlayerStateDto.serializer(), """{"playing":false}""")
        assertEquals(PlayerStateDto(playing = false), st)
        assertEquals("""{"playing":false,"positionSecs":0}""", Wire.json.encodeToString(PlayerStateDto.serializer(), st))
        assertTrue(runCatching { phoneJson.decodeFromString(PlayerStateDto.serializer(), "{}") }.isFailure, "playing is required")
    }

    @Test
    fun historyResyncEventsAreSnakeCase() {
        val page = eventsPage(listOf(PhoneEvent.HistoryResync(ResyncMode.PUSH_ALL), PhoneEvent.HistoryResync(ResyncMode.PULL_REPLACE)))
        assertEquals("""{"events":[{"type":"history_resync","mode":"push_all"},{"type":"history_resync","mode":"pull_replace"}]}""", page.toString())
        val back = phoneJson.decodeFromString(EventsPageDto.serializer(), page.toString())
        assertEquals(listOf("push_all", "pull_replace"), back.events.map { it.mode })
    }

    @Test
    fun eventsPageDefaultsAndUnknownEventTypes() {
        assertEquals("""{"events":[]}""", eventsPage(emptyList()).toString())
        // Rust refuses an unknown `type`; the phone of Nijiyome keeps it as an event it then skips (core/link: "a newer PC never breaks
        // this phone"), so it is decoded, typed, and nothing acts on it
        assertEquals("other", phoneJson.decodeFromString(EventsPageDto.serializer(), """{"events":[{"type":"other"}]}""").events.single().type)
        assertEquals("""{"type":"handoff","sessionId":"s","positionSecs":0,"title":"T"}""", PhoneEvent.Handoff("s", title = "T").toJson().toString())
        assertEquals(1, clampWait(1))
        assertEquals(EVENTS_MAX_WAIT_SECS, clampWait(EVENTS_MAX_WAIT_SECS.toLong()))
        assertEquals("no_player", noPlayerBody()["error"]!!.jsonObject["code"].toString().trim('"'))
    }
}

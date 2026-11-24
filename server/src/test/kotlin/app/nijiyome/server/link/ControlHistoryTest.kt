// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-link/tests/control.rs and tests/history.rs (AGPL-3.0, same author): remote control, the events long-poll,
// last-seen / platform, and /v1/history over real mTLS with the phone's TLS client.
package app.nijiyome.server.link

import app.nijiyome.link.PlayerStateDto
import app.nijiyome.model.HistoryEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.IOException
import java.time.Instant
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A player host (control.rs `PlayerHost`). */
class PlayerHost : TestHost() {
    @Volatile var playing: PlayerStateDto? = null
    val got: MutableList<ControlCommand> = Collections.synchronizedList(mutableListOf())

    override fun control(cmd: ControlCommand): Boolean {
        got += cmd
        return true
    }

    override fun playerState(): PlayerStateDto? = playing
}

/** In-memory history with the PC's merge rule (history.rs `MemHistory`). */
class MemHistory : TestHost() {
    val rows: MutableList<HistoryEntry> = Collections.synchronizedList(mutableListOf())
    val pushes: MutableList<Triple<String, Boolean, Int>> = Collections.synchronizedList(mutableListOf())

    override fun historySince(sinceUnix: Long, limit: Int): List<HistoryEntry> =
        synchronized(rows) { rows.filter { it.watchedAt.epochSecond > sinceUnix }.sortedBy { it.watchedAt }.take(limit) }

    override fun historyMerge(rows: List<HistoryEntry>): Int = synchronized(this.rows) {
        var merged = 0
        for (r in rows) {
            val i = this.rows.indexOfFirst { it.episodeId == r.episodeId }
            when {
                i < 0 -> {
                    this.rows += r
                    merged++
                }
                History.winsOver(r, this.rows[i]) -> {
                    this.rows[i] = r
                    merged++
                }
            }
        }
        merged
    }

    override fun historyClear(): Boolean {
        rows.clear()
        return true
    }

    override fun historyMergeFrom(deviceId: String, rows: List<HistoryEntry>, authoritative: Boolean): Int {
        pushes += Triple(deviceId, authoritative, rows.size)
        if (!authoritative) return historyMerge(rows)
        synchronized(this.rows) {
            for (r in rows) {
                this.rows.removeAll { it.episodeId == r.episodeId }
                this.rows += r
            }
        }
        return rows.size
    }
}

fun entry(ep: Int, progress: Int, at: Long) = HistoryEntry(
    animeId = "21", title = "One Piece", poster = null, episodeId = "21?ep=$ep", episode = ep, progressSec = progress, durationSec = 1440,
    completed = HistoryEntry.isCompleted(progress, 1440), watchedAt = Instant.ofEpochSecond(at), anilistId = 21, malId = null,
)

class ControlHistoryTest {
    private val open = mutableListOf<AutoCloseable>()

    @AfterTest
    fun tearDown() = open.reversed().forEach { runCatching { it.close() } }

    private fun server(host: TestHost): Harness = harness(FakeEngine(1, 10, 1)) { it.host = host }.also { open += it }

    private fun err(r: okhttp3.Response) = r.obj()["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    // ---- control.rs ----

    @Test
    fun controlNeedsAPlayerExceptOpen() {
        val host = PlayerHost()
        val s = server(host)
        val phone = s.pair("Pixel", userAgent = "okhttp/4.12.0")
        val r = phone.client.get(s.url("/v1/player"))
        assertEquals(404, r.code)
        assertEquals("no_player", err(r))
        assertEquals(409, phone.client.call("POST", s.url("/v1/control"), """{"command":"toggle"}""").code)
        assertEquals(204, phone.client.call("POST", s.url("/v1/control"), """{"command":"open","mediaId":"21","episode":3,"positionSecs":120}""").code)

        host.playing = PlayerStateDto(playing = true, title = "One Piece", episode = 3, positionSecs = 125, durationSecs = 1440, mediaId = "21")
        val st = phone.client.get(s.url("/v1/player")).obj()
        assertEquals(125, st["positionSecs"]!!.jsonPrimitive.int)
        assertEquals("21", st["mediaId"]!!.jsonPrimitive.content)
        for (c in listOf("prev", "toggle", "next")) assertEquals(204, phone.client.call("POST", s.url("/v1/control"), """{"command":"$c"}""").code, c)
        assertEquals(400, phone.client.call("POST", s.url("/v1/control"), """{"command":"explode"}""").code)
        assertEquals(listOf(ControlCommand.Open("21", 3, 120), ControlCommand.Prev, ControlCommand.Toggle, ControlCommand.Next), host.got.toList())
    }

    @Test
    fun eventsLongPollDeliversHandoffsOnce() = runBlocking {
        val s = server(PlayerHost())
        val phone = s.pair("Pixel", userAgent = "okhttp/4.12.0")
        assertEquals(204, phone.client.get(s.url("/v1/events?wait=1")).code)
        val poll = async(Dispatchers.IO) { phone.client.get(s.url("/v1/events?wait=10")) }
        delay(300)
        assertTrue(s.link.pushEvent(phone.deviceId, PhoneEvent.Handoff("s1", "21", 3, 75, "One Piece")))
        val r = withTimeout(5_000) { poll.await() }
        assertEquals(200, r.code)
        val ev = r.obj()["events"]!!.jsonArray[0].jsonObject
        assertEquals("handoff", ev["type"]!!.jsonPrimitive.content)
        assertEquals("s1", ev["sessionId"]!!.jsonPrimitive.content)
        assertEquals(75, ev["positionSecs"]!!.jsonPrimitive.int)
        assertEquals(204, phone.client.get(s.url("/v1/events?wait=1")).code, "drained on delivery")
        assertFalse(s.link.pushEvent("unknown-device", PhoneEvent.Handoff("x")))
        val d = s.link.devices().first { it.id == phone.deviceId }
        assertTrue(d.lastSeenAt != null)
        assertEquals("android", d.platform)
    }

    // ---- history.rs ----

    @Test
    fun pullPagesFollowTheCursorOldestFirst() {
        val host = MemHistory()
        host.historyMerge(listOf(entry(1, 100, 1_000), entry(2, 383, 2_000), entry(3, 900, 3_000)))
        val s = server(host)
        val phone = s.pair("Pixel")
        val r = phone.client.get(s.url("/v1/history?limit=2"))
        assertEquals(200, r.code)
        assertEquals("v1", r.header("Nijiyome-Link"))
        val page = r.obj()
        assertEquals(listOf(1, 2), page["items"]!!.jsonArray.map { it.jsonObject["episode"]!!.jsonPrimitive.int })
        assertEquals("true", page["truncated"]!!.jsonPrimitive.content)
        assertEquals(2_000, page["next_since_unix"]!!.jsonPrimitive.long)
        val item = page["items"]!!.jsonArray[1].jsonObject
        assertEquals(383, item["progressSec"]!!.jsonPrimitive.int)
        assertEquals("21", item["animeId"]!!.jsonPrimitive.content)
        assertEquals("21?ep=2", item["episodeId"]!!.jsonPrimitive.content)
        assertEquals(21, item["anilistId"]!!.jsonPrimitive.int)
        assertFalse("malId" in item, "unknown values are omitted, never null")

        val next = phone.client.get(s.url("/v1/history?since=2000&limit=2")).obj()
        assertEquals(1, next["items"]!!.jsonArray.size)
        assertEquals(3, next["items"]!!.jsonArray[0].jsonObject["episode"]!!.jsonPrimitive.int)
        assertEquals("false", next["truncated"]!!.jsonPrimitive.content)
        assertEquals(3_000, next["next_since_unix"]!!.jsonPrimitive.long)

        assertEquals(Json.parseToJsonElement("""{"items":[],"next_since_unix":3000,"truncated":false,"mode":"full"}"""), phone.client.get(s.url("/v1/history?since=3000")).json())
        for (q in listOf("since=-1", "since=abc", "limit=x")) {
            val bad = phone.client.get(s.url("/v1/history?$q"))
            assertEquals(400, bad.code, q)
            assertEquals("request_invalid", err(bad), q)
        }
    }

    @Test
    fun pushMergesByNewestAndMovesTheExactPosition() {
        val host = MemHistory()
        host.historyMerge(listOf(entry(1, 100, 1_000), entry(2, 700, 5_000)))
        val s = server(host)
        val phone = s.pair("Pixel")
        val push = """{"items":[
            {"animeId":"21","title":"One Piece","episodeId":"21?ep=1","episode":1,"progressSec":383,"durationSec":1440,"completed":false,"watchedAt":"2026-10-27T21:04:11.000Z","anilistId":21},
            {"animeId":"21","title":"One Piece","episodeId":"21?ep=2","episode":2,"progressSec":10,"completed":false,"watchedAt":"1970-01-01T00:16:40Z","anilistId":21},
            {"animeId":"21","title":"One Piece","episodeId":"21?ep=3","episode":3,"progressSec":1400,"durationSec":1440,"completed":true,"watchedAt":"2026-10-27T21:05:00Z","anilistId":21},
            {"animeId":"x","title":"No ids","episodeId":"x?ep=1","episode":1,"watchedAt":"2026-10-27T21:05:00Z"},
            {"animeId":"21","title":"Bad date","episodeId":"21?ep=9","episode":9,"watchedAt":"yesterday","anilistId":21}
        ]}"""
        val r = phone.client.call("POST", s.url("/v1/history"), push)
        assertEquals(200, r.code)
        assertEquals(Json.parseToJsonElement("""{"merged":2,"mode":"full"}"""), r.json())
        val rows = host.rows.toList()
        fun ep(n: Int) = rows.first { it.episode == n }
        assertEquals(383, ep(1).progressSec, "the phone's newer position wins")
        assertEquals(1_793_135_051, ep(1).watchedAt.epochSecond)
        assertEquals(700, ep(2).progressSec, "an older phone row never overwrites the PC")
        assertTrue(ep(3).completed)
        assertTrue(rows.none { it.episode == 9 || it.animeId == "x" })

        val tablet = s.pair("Tablet")
        assertEquals(3, tablet.client.get(s.url("/v1/history")).obj()["items"]!!.jsonArray.size)

        for (bad in listOf("""{"items":[]}""", """{"items":[],"user_id":"x"}""", """{"nope":1}""")) {
            val x = phone.client.call("POST", s.url("/v1/history"), bad)
            assertEquals(400, x.code, bad)
            assertEquals("request_invalid", err(x))
        }
        val tooMany = (0..500).joinToString(",", """{"items":[""", "]}") {
            """{"animeId":"21","title":"t","episodeId":"21?ep=$it","episode":$it,"watchedAt":"2026-10-27T21:05:00Z","anilistId":21}"""
        }
        assertEquals(400, phone.client.call("POST", s.url("/v1/history"), tooMany).code)
        val huge = """{"items":[{"animeId":"21","title":"${"x".repeat(300 * 1024)}","episodeId":"21?ep=1","episode":1,"watchedAt":"2026-10-27T21:05:00Z","anilistId":21}]}"""
        val big = phone.client.call("POST", s.url("/v1/history"), huge)
        assertEquals(413, big.code)
        assertEquals("body_too_large", err(big))
    }

    @Test
    fun deleteClearsThePcHistory() {
        val host = MemHistory()
        host.historyMerge(listOf(entry(1, 100, 1_000), entry(2, 383, 2_000)))
        val s = server(host)
        val phone = s.pair("Pixel")
        assertEquals(204, phone.client.call("DELETE", s.url("/v1/history")).code)
        assertTrue(host.rows.isEmpty())
        assertEquals(JsonArray(emptyList()), phone.client.get(s.url("/v1/history")).obj()["items"])
    }

    @Test
    fun historyNeedsAClientCertificateAndAnActiveDevice() {
        val host = MemHistory()
        host.historyMerge(listOf(entry(1, 100, 1_000)))
        val s = server(host)
        val bootstrap = s.bootstrap()
        for (m in listOf("GET", "POST", "DELETE")) {
            val r = bootstrap.call(m, s.url("/v1/history"))
            assertEquals(401, r.code, m)
            assertEquals("client_certificate_required", err(r))
        }
        assertEquals(1, host.rows.size, "nothing was cleared without a certificate")
        val phone = s.pair("Pixel")
        assertEquals(200, phone.client.get(s.url("/v1/history")).code)
        s.link.revoke(phone.deviceId)
        repeat(2) {
            try {
                val r = phone.client.get(s.url("/v1/history"))
                assertEquals(403, r.code)
                assertEquals("device_not_authorized", err(r))
            } catch (_: IOException) {
            }
        }
        assertEquals(1, host.rows.size, "a revoked device reads and clears nothing")
    }

    private fun rowJson(ep: Int, progress: Int, at: String) =
        """{"animeId":"21","title":"One Piece","episodeId":"21?ep=$ep","episode":$ep,"progressSec":$progress,"watchedAt":"$at","anilistId":21}"""

    @Test
    fun receiveOnlyAndPausedDevicesAreIgnoredOnPushAndPausedPullsNothing() {
        val host = MemHistory()
        host.historyMerge(listOf(entry(1, 100, 1_000)))
        val s = server(host)
        val moto = s.pair("moto g51")
        val tab = s.pair("Tab")
        assertEquals(Json.parseToJsonElement("""{"mode":"full","resync":false}"""), moto.client.get(s.url("/v1/history/mode")).json())

        assertTrue(s.link.setHistoryMode(moto.deviceId, HistoryMode.RECEIVE_ONLY))
        val push = """{"items":[${rowJson(1, 5, "2026-11-01T00:00:00Z")}]}"""
        val r = moto.client.call("POST", s.url("/v1/history"), push)
        assertEquals(200, r.code)
        assertEquals(Json.parseToJsonElement("""{"merged":0,"ignored":true,"mode":"receive_only"}"""), r.json())
        assertEquals(100, host.rows[0].progressSec, "nothing stored")
        val page = moto.client.get(s.url("/v1/history")).obj()
        assertEquals(1, page["items"]!!.jsonArray.size)
        assertEquals("receive_only", page["mode"]!!.jsonPrimitive.content)

        assertTrue(s.link.setHistoryMode(moto.deviceId, HistoryMode.PAUSED))
        val ignored = moto.client.call("POST", s.url("/v1/history"), push).obj()
        assertEquals(0, ignored["merged"]!!.jsonPrimitive.int)
        assertEquals("true", ignored["ignored"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""{"items":[],"next_since_unix":7,"truncated":false,"mode":"paused"}"""), moto.client.get(s.url("/v1/history?since=7")).json())

        assertEquals(Json.parseToJsonElement("""{"merged":1,"mode":"full"}"""), tab.client.call("POST", s.url("/v1/history"), push).json())
        assertEquals(listOf(Triple(tab.deviceId, false, 1)), host.pushes.toList())
    }

    @Test
    fun aSourceDeviceWinsInItsWindowAndDoneFansOutToUnpausedOthers() {
        val host = MemHistory()
        val s = server(host)
        val tab = s.pair("Tab")
        val moto = s.pair("moto g51")
        val quiet = s.pair("Old phone")
        s.link.setHistoryMode(quiet.deviceId, HistoryMode.PAUSED)
        host.historyMerge(listOf(entry(1, 30, 5_000)))

        assertFalse(s.link.startResyncFrom(quiet.deviceId), "a paused device cannot be the source")
        assertTrue(s.link.startResyncFrom(tab.deviceId))
        assertTrue(s.link.resyncRunning(tab.deviceId))
        assertEquals(Json.parseToJsonElement("""{"events":[{"type":"history_resync","mode":"push_all"}]}"""), tab.client.get(s.url("/v1/events?wait=1")).json())
        assertEquals("true", tab.client.get(s.url("/v1/history/mode")).obj()["resync"]!!.jsonPrimitive.content)

        val r = tab.client.call("POST", s.url("/v1/history"), """{"items":[${rowJson(1, 1200, "1970-01-01T00:16:40Z")}]}""").obj()
        assertEquals(1, r["merged"]!!.jsonPrimitive.int)
        assertEquals(1200, host.rows[0].progressSec)
        assertEquals(Triple(tab.deviceId, true, 1), host.pushes.last())

        assertEquals(Json.parseToJsonElement("""{"notified":0}"""), moto.client.call("POST", s.url("/v1/history/resync-done")).json())
        assertEquals(Json.parseToJsonElement("""{"notified":1}"""), tab.client.call("POST", s.url("/v1/history/resync-done")).json())
        assertFalse(s.link.resyncRunning(tab.deviceId))
        assertEquals(Json.parseToJsonElement("""{"events":[{"type":"history_resync","mode":"pull_replace"}]}"""), moto.client.get(s.url("/v1/events?wait=1")).json())
        assertEquals(204, quiet.client.get(s.url("/v1/events?wait=1")).code, "the paused device is left alone")
        assertEquals(204, tab.client.get(s.url("/v1/events?wait=1")).code, "and so is the source")

        val older = tab.client.call("POST", s.url("/v1/history"), """{"items":[${rowJson(1, 10, "1970-01-01T00:00:01Z")}]}""").obj()
        assertEquals(0, older["merged"]!!.jsonPrimitive.int)
    }
}

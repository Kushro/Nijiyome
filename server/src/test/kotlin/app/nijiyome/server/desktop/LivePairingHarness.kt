// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.link.AesGcmKeyCipher
import app.nijiyome.link.LibraryDto
import app.nijiyome.link.LinkHistoryRemoteImpl
import app.nijiyome.link.LinkHistoryRow
import app.nijiyome.link.LinkKeyVault
import app.nijiyome.link.LinkRepositoryImpl
import app.nijiyome.model.LinkSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.time.Instant
import javax.crypto.KeyGenerator
import kotlin.system.exitProcess

/**
 * Not a unit test: the phone side of the manual check against the running desktop app (Xvfb). Given the pairing text the app
 * shows (`nijiyome-link:v1?...`) and a scratch folder, it pairs with the phone's real `:core:link` code, reads capabilities and the
 * library, plays the first library item (master → media → init → two segments), sends a heartbeat and syncs one history row both
 * ways, printing one line per step. Exit code 0 = every step worked.
 *
 * `java -cp <server test runtime classpath> app.nijiyome.server.desktop.LivePairingHarnessKt <payload> <scratch dir>`
 */
fun main(args: Array<String>): Unit = runBlocking {
    val payload = args.getOrNull(0)?.trim().orEmpty()
    val scratch = File(args.getOrNull(1) ?: error("usage: <payload> <scratch dir>")).apply { mkdirs() }
    fun step(name: String, detail: Any?) = println("STEP $name: $detail")
    try {
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val repo = LinkRepositoryImpl(LinkKeyVault(scratch, AesGcmKeyCipher { aes }))
        val pc = repo.pair(payload, "Harness phone").getOrThrow()
        step("pair", "${pc.serverName} ${pc.host}:${pc.port} device=${pc.deviceId}")
        val caps = repo.capabilities().getOrThrow()
        step("capabilities", caps.profiles.joinToString { it.id })
        val http = repo.playbackClient() ?: error("no playback client")
        fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute()
        val lib = get("https://${pc.host}:${pc.port}/v1/library").use { Json { ignoreUnknownKeys = true }.decodeFromString(LibraryDto.serializer(), it.body.string()) }
        step("library", lib.items.joinToString { "${it.title} (${it.durationSecs} s)" })
        val item = lib.items.firstOrNull() ?: error("the library is empty")
        val session = repo.startSession(LinkSource.Library(item.id), "p-none").getOrThrow()
        step("session", "${session.sessionId} ${session.playbackUrl}")
        fun <T : Any> wait(what: String, f: () -> T?): T {
            repeat(300) {
                runCatching(f).getOrNull()?.let { return it }
                Thread.sleep(100)
            }
            error("timed out waiting for $what")
        }
        val master = wait("master") { get(session.playbackUrl).use { if (it.code == 200) it.body.string() else null } }
        step("master.m3u8", master.lines().filter { it.isNotBlank() }.joinToString(" | "))
        val mediaUrl = session.playbackUrl.toHttpUrl().resolve("media.m3u8")!!.toString()
        val media = wait("media") { get(mediaUrl).use { if (it.code == 200) it.body.string() else null } }
        val segs = media.lines().filter { it.startsWith("seg_") }
        step("media.m3u8", "${segs.size} segments")
        val init = wait("init") { get(mediaUrl.replace("media.m3u8", "init.mp4")).use { if (it.code == 200) it.body.bytes() else null } }
        step("init.mp4", "${init.size} bytes, box ${String(init, 4, 4, Charsets.ISO_8859_1)}")
        for (s in segs.take(2)) {
            val b = wait(s) { get(mediaUrl.replace("media.m3u8", s)).use { if (it.code == 200) it.body.bytes() else null } }
            step(s, "${b.size} bytes")
        }
        val state = get(session.playbackUrl.removeSuffix("/master.m3u8")).use { it.body.string() }
        step("session state", state)
        repo.heartbeat(session.sessionId, 2.0, false).getOrThrow()
        step("heartbeat", "ok")
        val remote = LinkHistoryRemoteImpl(repo)
        val row = LinkHistoryRow("21", "One Piece", null, "21?ep=1", 1, 600, 1440, false, Instant.now().minusSeconds(60).toString(), 21, null)
        step("history push", remote.push(listOf(row)))
        step("history pull", remote.since(0).items.joinToString { "${it.title} ep ${it.episode} @ ${it.progressSec}s" })
        println("HARNESS OK")
        exitProcess(0)
    } catch (e: Throwable) {
        println("HARNESS FAILED: $e")
        e.printStackTrace()
        exitProcess(1)
    }
}

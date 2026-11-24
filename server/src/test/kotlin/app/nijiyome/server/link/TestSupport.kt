// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of Tatakai Next tk-link/tests/common/mod.rs (AGPL-3.0, same author): loopback stubs, a fake FFmpeg, a test host and a
// paired mTLS client. The client side is the phone's own code (:core:link PairingClient / LinkCrypto).
package app.nijiyome.server.link

import app.nijiyome.link.LibraryItemDto
import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.PairingClient
import app.nijiyome.link.PairingPayload as PhonePayload
import app.nijiyome.link.LinkResult
import app.nijiyome.link.PayloadResult
import app.nijiyome.server.link.media.CommandRunner
import app.nijiyome.server.link.media.Engine
import app.nijiyome.server.link.media.EngineExit
import app.nijiyome.server.link.media.EngineJob
import app.nijiyome.server.link.media.EngineProcess
import app.nijiyome.server.link.media.SourceInfo
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.Resolver
import app.nijiyome.server.net.SafeHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

typealias Seen = MutableList<String>

/** One HTTP answer of a [Stub]. */
data class Canned(val status: Int, val headers: List<Pair<String, String>> = emptyList(), val body: ByteArray = ByteArray(0))

fun text(status: Int, body: String) = Canned(status, listOf("content-type" to "application/vnd.apple.mpegurl"), body.toByteArray())

fun redirect(to: String) = Canned(302, listOf("location" to to))

fun requestPath(req: String): String = req.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1).orEmpty()

/** Loopback HTTP stub: one request per connection; `responder(raw request)`; every request is recorded. */
class Stub(private val responder: (String) -> Canned) : AutoCloseable {
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    val port: Int = server.localPort
    val seen: Seen = Collections.synchronizedList(mutableListOf())

    init {
        thread(isDaemon = true, name = "stub-$port") {
            while (!server.isClosed) {
                val s = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { runCatching { serve(s) } }
            }
        }
    }

    private fun serve(s: Socket) = s.use {
        val input = s.getInputStream()
        val buf = ByteArray(16 * 1024)
        val n = input.read(buf).coerceAtLeast(0)
        val req = String(buf, 0, n)
        seen += req
        val a = responder(req)
        val head = StringBuilder("HTTP/1.1 ${a.status} X\r\ncontent-length: ${a.body.size}\r\nconnection: close\r\n")
        a.headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
        head.append("\r\n")
        val out = s.getOutputStream()
        out.write(head.toString().toByteArray())
        out.write(a.body)
        out.flush()
    }

    override fun close() = server.close()
}

/** Maps host names to fixed addresses (no real DNS in tests). */
class MapResolver(private val map: Map<String, List<InetAddress>>) : Resolver {
    override suspend fun resolve(host: String, port: Int): List<InetAddress> = map[host] ?: throw java.net.UnknownHostException(host)
}

/** Production policy except that loopback is reachable on any port. */
fun lenientHttp(resolver: Resolver = MapResolver(emptyMap())) =
    SafeHttp(NetPolicy.linkSource(true).copy(allowLoopback = true, allowedPorts = null), resolver)

/** HLS media playlist with [n] 2-second segments. */
fun mediaPlaylist(n: Int): String = buildString {
    append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n")
    repeat(n) { append("#EXTINF:2.0,\nseg$it.ts\n") }
    append("#EXT-X-ENDLIST\n")
}

// ---- fake FFmpeg -----------------------------------------------------------------------------------------------------------

/** 0.4.1: misbehaviour of one fake run. */
sealed interface RunScript {
    data class HangAfter(val n: Int) : RunScript
    data class StopAfter(val n: Int) : RunScript
}

class FakeEngine(val totalSegments: Int, val segBytes: Int, val delayMs: Long) : Engine {
    val starts: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    val args: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
    val putResults: MutableList<Pair<Int, Int>> = Collections.synchronizedList(mutableListOf())
    @Volatile var probedDuration: Double? = null
    @Volatile var probeUnreadable = false
    @Volatile var subtitleProbe: String? = null
    @Volatile var extractDelayMs = 0L
    @Volatile var extractFails = false
    val extractions: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** PUT like FFmpeg n9's HLS muxer, which does not wait for the answer. */
    @Volatile var noWait = false
    val sent: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    val scripts = ArrayDeque<RunScript>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun spawn(job: EngineJob): EngineProcess {
        starts += job.startSegment
        args += job.args
        val nw = noWait
        val script = synchronized(scripts) { scripts.removeFirstOrNull() }
        val j: Job = scope.launch {
            suspend fun put(name: String, body: ByteArray): Int = withContext(Dispatchers.IO) { ffmpegPut("${job.sinkBase}/$name", body, wait = !nw) }
            if (put("init.mp4", "INIT".toByteArray()) != 200) return@launch
            for ((n, k) in (job.startSegment until totalSegments).withIndex()) {
                when (script) {
                    is RunScript.HangAfter -> if (n >= script.n) awaitCancellation()
                    is RunScript.StopAfter -> if (n >= script.n) return@launch
                    null -> Unit
                }
                sent += k
                val st = put("seg_$k.m4s", ByteArray(segBytes) { (k % 251).toByte() })
                putResults += k to st
                if (st != 200) return@launch // stale generation or session closed
                if (delayMs > 0) delay(delayMs)
            }
        }
        return object : EngineProcess {
            override suspend fun await(): EngineExit {
                j.join()
                return if (j.isCancelled) EngineExit.FAILED else EngineExit.SUCCESS
            }

            override fun kill() = j.cancel()
        }
    }

    override suspend fun sourceInfo(inputUrl: String) = SourceInfo(null, probedDuration, probeUnreadable)

    override suspend fun subtitleTracks(inputUrl: String): List<SubtitleTrack>? = subtitleProbe?.let(Subtitles::parseFfprobe)

    override suspend fun extractSubtitles(inputUrl: String, outputs: List<SubtitleOutput>) {
        extractions += inputUrl
        delay(extractDelayMs)
        if (extractFails) throw java.io.IOException("fake extraction failed")
        for (o in outputs) {
            o.path.writeText(
                when (o.format) {
                    SubFormat.ASS -> "[Script Info]\nScriptType: v4.00+\n\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,track ${o.track}\n"
                    SubFormat.VTT -> "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\ntrack ${o.track}\n"
                },
            )
        }
    }

    companion object {
        /**
         * PUT [body] the way FFmpeg's HLS muxer does (`-method PUT`): chunked body, then a half-close before reading the status
         * line; with [wait] false (FFmpeg n9) the connection is closed without reading it. Answers the status (200 when not waiting).
         */
        fun ffmpegPut(url: String, body: ByteArray, wait: Boolean): Int {
            val rest = url.removePrefix("http://")
            val host = rest.substringBefore('/')
            val path = "/" + rest.substringAfter('/')
            return try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host.substringBefore(':'), host.substringAfter(':').toInt()), 5_000)
                    s.soTimeout = 60_000
                    val out = s.getOutputStream()
                    val req = ByteArrayOutputStream()
                    req.write("PUT $path HTTP/1.1\r\nTransfer-Encoding: chunked\r\nUser-Agent: Lavf\r\nConnection: close\r\nHost: $host\r\n\r\n".toByteArray())
                    var i = 0
                    while (i < body.size) {
                        val n = minOf(32 * 1024, body.size - i)
                        req.write("${n.toString(16)}\r\n".toByteArray())
                        req.write(body, i, n)
                        req.write("\r\n".toByteArray())
                        i += n
                    }
                    req.write("0\r\n\r\n".toByteArray())
                    out.write(req.toByteArray())
                    out.flush()
                    s.shutdownOutput()
                    if (!wait) return 200
                    val resp = s.getInputStream().readAllBytes().decodeToString()
                    resp.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                }
            } catch (_: Exception) {
                0
            }
        }
    }
}

// ---- host + canned ffmpeg detection ---------------------------------------------------------------------------------------

open class TestHost : LinkHost {
    val files: MutableMap<String, File> = Collections.synchronizedMap(mutableMapOf())
    val extensionPerms: MutableMap<String, List<String>> = Collections.synchronizedMap(mutableMapOf())
    @Volatile var torrent: OpenedTorrent? = null
    val torrentsEnded: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile var torrentReusable = true
    @Volatile var torrentOpenHangs = false
    val torrentCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun library(): List<LibraryItemDto> = files.map { (id, p) -> LibraryItemDto(id, p.nameWithoutExtension, 6.0, 1280, 720, 10) }
    override fun libraryPath(id: String): File? = files[id]
    override fun ffmpegPath(): File? = File("ffmpeg-for-tests")
    override fun extensionPermissions(id: String): List<String>? = extensionPerms[id]

    override suspend fun openTorrent(req: TorrentRequest): OpenedTorrent {
        val answer = torrent
        if (torrentOpenHangs) awaitCancellation()
        val opened = answer ?: api(ErrorCode.SOURCE_UNAVAILABLE)
        torrentCalls += "open:${opened.torrentId}"
        return opened
    }

    override suspend fun reuseTorrent(torrentId: String): Boolean {
        val found = torrentReusable
        if (found) torrentCalls += "reuse:$torrentId"
        return found
    }

    override fun torrentSessionEnded(torrentId: String) {
        torrentsEnded += torrentId
        torrentCalls += "ended:$torrentId"
    }
}

object CannedRunner : CommandRunner {
    override suspend fun run(program: File, args: List<String>): String =
        if ("-encoders" in args) " V....D h264_nvenc           NVIDIA NVENC H.264 encoder\n V....D libopenh264          OpenH264\n"
        else " ... libplacebo        VV->V      libplacebo\n"
}

// ---- harness -----------------------------------------------------------------------------------------------------------------

fun quickTuning() = SessionTuning(segmentWaitMs = 5_000)

class Paired(val client: OkHttpClient, val deviceId: String)

class Harness(
    val link: LinkServer,
    val dir: File,
    val host: TestHost,
    val engine: Engine,
) : AutoCloseable {
    val base: String get() = "https://127.0.0.1:${link.port}"
    fun url(path: String) = "$base$path"

    /** A client that trusts the pinned CA and presents no certificate (what the phone uses for `POST /v1/pairing`). */
    fun bootstrap(): OkHttpClient = LinkCrypto.httpClient("127.0.0.1", link.caFingerprint)

    /** Pairs a new device with the phone's own pairing code and returns its mTLS client (the phone's [LinkCrypto]). */
    fun pair(name: String, userAgent: String? = null): Paired {
        val payload = (PhonePayload.parse(link.openPairingWindow().uri()) as PayloadResult.Valid).payload
        val key = LinkCrypto.generateKeyPair()
        val grant = runBlocking { PairingClient().pair(payload, name, key) }
        val g = (grant as? LinkResult.Ok)?.value ?: error("pairing failed: $grant")
        var client = LinkCrypto.httpClient("127.0.0.1", link.caFingerprint, LinkCrypto.ClientKeyManager(key.private, arrayOf(g.certificate)))
        if (userAgent != null) {
            client = client.newBuilder().addInterceptor { c -> c.proceed(c.request().newBuilder().header("User-Agent", userAgent).build()) }.build()
        }
        return Paired(client, g.deviceId)
    }

    override fun close() {
        link.stop()
        dir.deleteRecursively()
    }
}

fun harness(
    engine: Engine,
    tuning: SessionTuning = quickTuning(),
    http: SafeHttp = lenientHttp(),
    host: TestHost = TestHost(),
    tweak: (LinkConfigBuilder) -> Unit = {},
): Harness {
    val dir = Files.createTempDirectory("nijiyome-link-test").toFile()
    val b = LinkConfigBuilder(File(dir, "link"), engine, tuning, http)
    tweak(b)
    val link = LinkServer.start(b.build(), b.host ?: host)
    return Harness(link, dir, b.host ?: host, engine)
}

class LinkConfigBuilder(var dataDir: File, var engine: Engine?, var tuning: SessionTuning, var http: SafeHttp?) {
    var allowHttpSources = true
    var lsfg: LsfgSetup = LsfgSetup.Off
    var host: TestHost? = null
    var bindIps = listOf("127.0.0.1")
    var port = 0

    fun build() = LinkConfig(
        dataDir = dataDir, serverName = "Test PC", port = port, bindIps = bindIps, allowHttpSources = allowHttpSources, lsfg = lsfg,
        tuning = tuning, engine = engine, runner = CannedRunner, fetchHttp = http, discoverRenderNode = false, nvencDevice = true,
        platform = app.nijiyome.server.link.media.Platform.LINUX,
    )
}

// ---- HTTP helpers --------------------------------------------------------------------------------------------------------

private val JSON_TYPE = "application/json".toMediaType()

fun OkHttpClient.call(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): Response {
    val b = Request.Builder().url(url)
    headers.forEach { (k, v) -> b.header(k, v) }
    val rb = body?.toRequestBody(JSON_TYPE) ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(null) else null
    b.method(method, rb)
    // The body is read whole and the network response closed here: tests often look only at `.code`, and OkHttp 5 may hand an
    // idle connection with an unread (leaked) body to the next call, which then reads the old body as its status line.
    return newCall(b.build()).execute().use { r ->
        val bytes = if (method == "HEAD") ByteArray(0) else r.body.bytes()
        r.newBuilder().body(bytes.toResponseBody(r.body.contentType())).build()
    }
}

fun OkHttpClient.get(url: String) = call("GET", url)

fun Response.json(): JsonElement = use { Json.parseToJsonElement(it.body.string()) }

fun Response.obj(): JsonObject = json() as JsonObject

/** Polls [f] every 25 ms, up to [attempts] times. */
fun waitUntil(what: String, attempts: Int = 200, f: () -> Boolean) {
    repeat(attempts) {
        if (runCatching(f).getOrDefault(false)) return
        Thread.sleep(25)
    }
    error("timed out waiting for $what")
}

fun InputStream.readAllText(): String = readAllBytes().decodeToString()

/** A client with short timeouts that never follows redirects (loopback helpers). */
val plainClient: OkHttpClient = OkHttpClient.Builder().followRedirects(false).readTimeout(30, TimeUnit.SECONDS).build()

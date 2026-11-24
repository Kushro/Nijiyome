// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.mpv.external.findMpv
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1.1: the network options Android's MpvEngine sets ([mpvHttpOptions], [mpvTlsOptions], plus `curl-enabled=no` and an empty
 * `demuxer-lavf-o`) run by this machine's mpv (0.42, the version of the app's libmpv 1.1.0; its FFmpeg and TLS library are the
 * system's, not the AAR's FFmpeg 9.0.2 with mbedTLS), against loopback servers:
 * - a server that answers 403 unless User-Agent, Referer and a custom header are there, for an AES-128 HLS stream (master behind
 *   a redirect, media playlist, key, segments, one segment behind a redirect) and a progressive MP4 behind a redirect;
 * - an HTTPS server with a certificate of a test CA: with the check on, mpv plays it with that CA's bundle and refuses it with
 *   another CA's; with the check off (the default) it plays either way.
 * Skipped (Assume) without mpv, ffmpeg or openssl on PATH.
 */
class MpvNetworkIntegrationTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-mpv-net").toFile()
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun cleanUp() {
        servers.forEach { it.stop(0) }
        tmp.deleteRecursively() // temp dir created above
    }

    private data class Seen(val path: String, val ua: String?, val referer: String?, val token: String?)

    private val headers = mapOf("User-Agent" to "NijiyomeTest/1.1", "Referer" to "https://site.test/watch/1", "X-Token" to "t,1")

    @Test
    fun theHeadersReachEveryRequestOfAnHlsStreamAndAProgressiveFile() {
        val mpv = requireTools()
        val seen = CopyOnWriteArrayList<Seen>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { servers += it }
        val base = "http://127.0.0.1:${server.address.port}"
        val www = File(tmp, "www").apply { mkdirs() }
        makeHls(File(www, "v"), keyUrl = "$base/k/key.bin")
        makeMp4(File(www, "clip.mp4"))
        val redirects = mapOf("/master.m3u8" to "/r/master.m3u8", "/v/seg1.ts" to "/cdn/seg1.ts", "/clip.mp4" to "/cdn/clip.mp4")
        val files = mapOf("/cdn/seg1.ts" to "v/seg1.ts", "/k/key.bin" to "v/key.bin", "/cdn/clip.mp4" to "clip.mp4")
        server.createContext("/") { ex ->
            val path = ex.requestURI.path
            val s = Seen(path, ex.requestHeaders.getFirst("User-Agent"), ex.requestHeaders.getFirst("Referer"), ex.requestHeaders.getFirst("X-Token"))
            seen += s
            when {
                s.ua != headers["User-Agent"] || s.referer != headers["Referer"] || s.token != headers["X-Token"] -> ex.reply(403, ByteArray(0))
                path in redirects -> {
                    ex.responseHeaders.add("Location", redirects.getValue(path))
                    ex.reply(302, ByteArray(0))
                }
                path == "/r/master.m3u8" ->
                    ex.reply(200, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=320x240\n/v/index.m3u8\n".toByteArray())
                else -> File(www, files[path] ?: path.removePrefix("/")).let { f -> if (f.isFile) ex.replyRanged(f.readBytes()) else ex.reply(404, ByteArray(0)) }
            }
        }
        server.start()

        val (hlsExit, hlsLog) = play(mpv, "$base/master.m3u8", tls = mpvTlsOptions(false, null))
        val hlsPaths = listOf("/master.m3u8", "/r/master.m3u8", "/v/index.m3u8", "/k/key.bin", "/v/seg0.ts", "/v/seg1.ts", "/cdn/seg1.ts")
        println("[mpv-net] hls exit=$hlsExit requests=${seen.map { it.path }}")
        assertEquals(0, hlsExit, hlsLog)
        hlsPaths.forEach { p -> assertTrue(seen.any { it.path == p }, "requested $p: ${seen.map { it.path }}") }

        seen.clear()
        val (mp4Exit, mp4Log) = play(mpv, "$base/clip.mp4", tls = mpvTlsOptions(false, null))
        println("[mpv-net] mp4 exit=$mp4Exit requests=${seen.map { it.path }}")
        assertEquals(0, mp4Exit, mp4Log)
        assertTrue(seen.any { it.path == "/cdn/clip.mp4" }, "followed the redirect: ${seen.map { it.path }}")
        // every request of both, redirects and nested opens included, carried the three headers (none was refused)
        assertEquals(emptyList(), seen.filter { it.ua != headers["User-Agent"] || it.referer != headers["Referer"] || it.token != headers["X-Token"] })
    }

    @Test
    fun withTheCheckOnOnlyTheRightCaIsAccepted() {
        val mpv = requireTools()
        val certs = File(tmp, "certs").apply { mkdirs() }
        openssl(certs, "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes", "-keyout", "ca.key", "-out", "ca.crt", "-days", "2", "-subj", "/CN=Nijiyome Test CA")
        openssl(certs, "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes", "-keyout", "other.key", "-out", "other.crt", "-days", "2", "-subj", "/CN=Some Other CA")
        openssl(certs, "req", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256", "-nodes", "-keyout", "leaf.key", "-out", "leaf.csr", "-subj", "/CN=127.0.0.1")
        File(certs, "ext.cnf").writeText("subjectAltName=IP:127.0.0.1\nbasicConstraints=CA:FALSE\nextendedKeyUsage=serverAuth\n")
        openssl(certs, "x509", "-req", "-in", "leaf.csr", "-CA", "ca.crt", "-CAkey", "ca.key", "-CAcreateserial", "-out", "leaf.crt", "-days", "2", "-extfile", "ext.cnf")
        openssl(certs, "pkcs12", "-export", "-inkey", "leaf.key", "-in", "leaf.crt", "-certfile", "ca.crt", "-out", "leaf.p12", "-passout", "pass:test")

        // The bundles mpv gets are written the way the app writes the system's CAs (pemBundle).
        fun bundle(name: String): File {
            val cert = File(certs, "$name.crt").inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
            return File(certs, "$name-bundle.pem").apply { writeText(pemBundle(listOf(cert))) }
        }
        val rightCa = bundle("ca")
        val otherCa = bundle("other")

        val keyStore = KeyStore.getInstance("PKCS12").apply { File(certs, "leaf.p12").inputStream().use { load(it, "test".toCharArray()) } }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, "test".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).also { servers += it }
        server.httpsConfigurator = HttpsConfigurator(ssl)
        val clip = File(tmp, "clip.mp4").also { makeMp4(it) }
        val gets = CopyOnWriteArrayList<String>()
        server.createContext("/") { ex ->
            gets += ex.requestURI.path
            ex.replyRanged(clip.readBytes())
        }
        server.start()
        val url = "https://127.0.0.1:${server.address.port}/clip.mp4"

        val (okExit, okLog) = play(mpv, url, tls = mpvTlsOptions(true, rightCa))
        println("[mpv-net] tls right CA exit=$okExit gets=$gets")
        assertEquals(0, okExit, okLog)
        assertTrue(gets.isNotEmpty(), "the stream was read")

        gets.clear()
        val (badExit, badLog) = play(mpv, url, tls = mpvTlsOptions(true, otherCa))
        println("[mpv-net] tls other CA exit=$badExit gets=$gets log=${badLog.lines().filter { it.isNotBlank() }.take(4)}")
        assertTrue(badExit != 0, "refused with another CA's bundle:\n$badLog")
        assertTrue(gets.isEmpty(), "no request got through the handshake: $gets")

        gets.clear()
        // review #4: on without a bundle (it could not be written) mpv is left with tls-verify=yes and no CA: nothing gets through
        val (closedExit, closedLog) = play(mpv, url, tls = mpvTlsOptions(true, File(certs, "gone.pem")))
        println("[mpv-net] tls on without bundle exit=$closedExit gets=$gets")
        assertTrue(closedExit != 0, "fails closed without a bundle:\n$closedLog")
        assertTrue(gets.isEmpty(), "no request got through the handshake: $gets")

        gets.clear()
        val (offExit, offLog) = play(mpv, url, tls = mpvTlsOptions(false, otherCa))
        println("[mpv-net] tls off exit=$offExit gets=$gets")
        assertEquals(0, offExit, offLog)
        assertTrue(gets.isNotEmpty(), "the default (check off) plays it")
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun requireTools(): File {
        val mpv = findMpv(null)
        assumeTrue("mpv not installed", mpv != null)
        assumeTrue("ffmpeg not available", runs(listOf("ffmpeg", "-version")))
        assumeTrue("openssl not available", runs(listOf("openssl", "version")))
        return mpv!!
    }

    private fun runs(cmd: List<String>): Boolean = runCatching {
        ProcessBuilder(cmd).redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor(20, TimeUnit.SECONDS) && it.exitValue() == 0 }
    }.getOrDefault(false)

    /** Android's network options for [headers] (and [tls]), the rest of the run silent and without a window. */
    private fun play(mpv: File, url: String, tls: List<Pair<String, String>>): Pair<Int, String> {
        val options = mpvHttpOptions(headers) + tls + listOf("curl-enabled" to "no", "demuxer-lavf-o" to "", "network-timeout" to "10")
        val cmd = listOf(mpv.absolutePath, "--no-config", "--vo=null", "--ao=null", "--end=1.5", "--msg-level=all=warn") +
            options.map { (k, v) -> "--$k=$v" } + url
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["MPV_HOME"] = File(tmp, "mpv-home").apply { mkdirs() }.absolutePath
        pb.environment().remove("DISPLAY")
        pb.environment().remove("WAYLAND_DISPLAY")
        val p = pb.start()
        val out = StringBuilder()
        val reader = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }.apply { start() }
        if (!p.waitFor(40, TimeUnit.SECONDS)) p.destroyForcibly()
        reader.join(5_000)
        return (if (p.isAlive) -1 else p.exitValue()) to out.toString()
    }

    private fun ffmpeg(vararg args: String) {
        val p = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-loglevel", "error", "-y") + args).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assumeTrue("ffmpeg failed: $log", p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0)
    }

    private fun makeHls(dir: File, keyUrl: String) {
        dir.mkdirs()
        val key = File(dir, "key.bin").apply { writeBytes(ByteArray(16) { (it * 7 + 3).toByte() }) }
        val keyInfo = File(tmp, "keyinfo").apply { writeText("$keyUrl\n${key.absolutePath}\n") }
        ffmpeg(
            "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25", "-t", "3", "-c:v", "mpeg2video", "-f", "hls", "-hls_time", "1",
            "-hls_list_size", "0", "-hls_key_info_file", keyInfo.absolutePath, "-hls_segment_filename", File(dir, "seg%d.ts").absolutePath,
            File(dir, "index.m3u8").absolutePath,
        )
    }

    private fun makeMp4(out: File) {
        if (out.length() > 0) return
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25", "-t", "3", "-c:v", "mpeg4", "-movflags", "+faststart", out.absolutePath)
    }

    private fun openssl(dir: File, vararg args: String) {
        val p = ProcessBuilder(listOf("openssl") + args).directory(dir).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        assumeTrue("openssl ${args.firstOrNull()} failed: $log", p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0)
    }

    private fun HttpExchange.reply(code: Int, body: ByteArray) {
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun HttpExchange.replyRanged(bytes: ByteArray) {
        responseHeaders.add("Accept-Ranges", "bytes")
        val m = requestHeaders.getFirst("Range")?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it.trim()) }
        if (m == null) return reply(200, bytes)
        val from = m.groupValues[1].toInt().coerceAtMost(bytes.size)
        val to = m.groupValues[2].toIntOrNull()?.coerceAtMost(bytes.size - 1) ?: (bytes.size - 1)
        responseHeaders.add("Content-Range", "bytes $from-$to/${bytes.size}")
        reply(206, bytes.copyOfRange(from, to + 1))
    }
}

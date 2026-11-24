// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.ext

import java.io.BufferedInputStream
import java.io.File
import java.net.Socket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keep-alive framing of the extension server: every answer carries exactly the bytes its headers announce. */
class FramingProbeTest {
    private fun readResponse(input: BufferedInputStream): Triple<String, Map<String, String>, String> {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            check(b >= 0) { "EOF after ${head.length} bytes: $head" }
            head.append(b.toChar())
        }
        val lines = head.toString().trim().split("\r\n")
        val headers = lines.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val len = headers["content-length"]?.toInt() ?: 0
        val body = ByteArray(len)
        var off = 0
        while (off < len) off += input.read(body, off, len - off).also { check(it > 0) }
        return Triple(lines[0], headers, body.decodeToString())
    }

    @Test
    fun keepAliveAnswersNeverCarryExtraBytes() {
        val dir = Files.createTempDirectory("framing").toFile()
        val s = ExtServer.start(ExtServerConfig(File(dir, "server"), 0, loopbackOnly = true), FakeCatalog())
        try {
            Socket("127.0.0.1", s.port).use { sock ->
                sock.soTimeout = 5_000
                val out = sock.getOutputStream()
                val input = BufferedInputStream(sock.getInputStream())
                repeat(300) { i ->
                    val req = when (i % 4) {
                        0 -> "GET /api/server/v1/sources HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        1 -> "{\"code\":\"000\"}".let { b -> "POST /api/server/v1/auth/pair HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: ${b.length}\r\n\r\n$b" }
                        2 -> "GET /api/server/v1/health HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        else -> "GET /api/server/v1/extensions HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Bearer nope\r\n\r\n"
                    }
                    out.write(req.toByteArray())
                    out.flush()
                    val (status, _, body) = runCatching { readResponse(input) }.getOrElse { throw AssertionError("request $i (${req.lineSequence().first()})", it) }
                    assertTrue(status.startsWith("HTTP/1.1 "), "request $i: $status")
                    assertTrue(body.isEmpty() || body.startsWith("{"), "request $i: $body")
                }
                assertEquals(0, input.available(), "nothing left over")
            }
        } finally {
            s.stop()
            dir.deleteRecursively()
        }
    }
}

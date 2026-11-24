// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import java.io.File
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals

/** v1.1: the HTTP and TLS options Android's MpvEngine sets on libmpv (the real mpv runs them in MpvNetworkIntegrationTest). */
class MpvNetworkOptionsTest {
    @Test
    fun headersBecomeUserAgentReferrerAndTheRestAsFields() {
        val options = mpvHttpOptions(mapOf("referer" to "https://site.test/ep", "USER-AGENT" to "UA/2", "Origin" to "https://site.test", "X-List" to "a,b"))
        assertEquals(
            listOf("user-agent" to "UA/2", "referrer" to "https://site.test/ep", "http-header-fields" to "Origin: https://site.test,X-List: a\\,b"),
            options,
        )
    }

    @Test
    fun withoutHeadersTheDefaultUserAgentAndNoReferer() {
        assertEquals(
            listOf("user-agent" to MpvEngineDefaults.DEFAULT_USER_AGENT, "referrer" to "", "http-header-fields" to ""),
            mpvHttpOptions(emptyMap()),
        )
    }

    @Test
    fun tlsIsCheckedOnlyWhenAskedAndThereIsABundle() {
        val dir = Files.createTempDirectory("nijiyome-mpv-tls").toFile()
        try {
            val bundle = File(dir, "ca.pem").apply { writeText(pemBundle(listOf(testCa()))) }
            assertEquals(listOf("tls-verify" to "no"), mpvTlsOptions(verify = false, caFile = bundle), "off: as before 1.1")
            assertEquals(listOf("tls-verify" to "yes", "tls-ca-file" to bundle.absolutePath), mpvTlsOptions(verify = true, caFile = bundle))
            // review #4: on without a usable bundle it fails closed, never back to tls-verify=no
            assertEquals(listOf("tls-verify" to "yes"), mpvTlsOptions(verify = true, caFile = null), "no bundle: closed")
            assertEquals(listOf("tls-verify" to "yes"), mpvTlsOptions(verify = true, caFile = File(dir, "missing.pem")))
            assertEquals(listOf("tls-verify" to "yes"), mpvTlsOptions(verify = true, caFile = File(dir, "empty.pem").apply { writeText("") }))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun anHttpsLoadStopsWhenTheCheckIsOnWithoutABundle() {
        val dir = Files.createTempDirectory("nijiyome-mpv-tls").toFile()
        try {
            val bundle = File(dir, "ca.pem").apply { writeText(pemBundle(listOf(testCa()))) }
            assertEquals(true, mpvTlsBlocksLoad(verify = true, caFile = null, url = "https://cdn.test/a.m3u8"))
            assertEquals(true, mpvTlsBlocksLoad(verify = true, caFile = File(dir, "gone.pem"), url = "HTTPS://cdn.test/a.mp4"))
            assertEquals(false, mpvTlsBlocksLoad(verify = true, caFile = bundle, url = "https://cdn.test/a.m3u8"), "with the bundle it plays")
            assertEquals(false, mpvTlsBlocksLoad(verify = false, caFile = null, url = "https://cdn.test/a.m3u8"), "check off: as before")
            assertEquals(false, mpvTlsBlocksLoad(verify = true, caFile = null, url = "http://127.0.0.1:8080/torrent/1"), "no TLS involved")
            // the error the player gets: a watched stream moves to Media3 (which verifies certificates), anything else fails visibly
            assertEquals(app.nijiyome.player.MPV_OPEN_ERROR_CODE, mpvErrorCode(TLS_CA_UNAVAILABLE, started = false, watched = true))
            assertEquals(-1, mpvErrorCode(TLS_CA_UNAVAILABLE, started = false, watched = false))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theBundleIsWrittenAgainWhenItIsGone() {
        val dir = Files.createTempDirectory("nijiyome-mpv-tls").toFile()
        try {
            val target = File(dir, "system-ca.pem")
            var writes = 0
            var canWrite = true
            val bundle = RegeneratedFile {
                if (!canWrite) null else target.apply { writes++; writeText(pemBundle(listOf(testCa()))) }
            }
            assertEquals(target, bundle.get())
            assertEquals(target, bundle.get())
            assertEquals(1, writes, "reused while it is there")
            target.delete() // "Clear cache"
            assertEquals(target, bundle.get())
            assertEquals(2, writes, "written again")
            assertEquals(true, target.isFile)
            target.writeText("") // a failed or interrupted write
            assertEquals(target, bundle.get())
            assertEquals(3, writes)
            target.delete()
            canWrite = false
            assertEquals(null, bundle.get(), "cannot be written: null, so the engine fails closed")
            assertEquals(listOf("tls-verify" to "yes"), mpvTlsOptions(true, bundle.get()))
            canWrite = true
            assertEquals(target, bundle.get(), "recovers once it can be written")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun thePemBundleReadsBackAsTheSameCertificates() {
        val ca = testCa()
        val pem = pemBundle(listOf(ca, ca))
        assertEquals(2, Regex("-----BEGIN CERTIFICATE-----").findAll(pem).count())
        val back = CertificateFactory.getInstance("X.509").generateCertificates(pem.byteInputStream()).toList()
        assertEquals(listOf(ca, ca), back)
        assertEquals(true, pem.lines().filter { it.isNotEmpty() && !it.startsWith("-----") }.all { it.length <= 64 })
    }

    /** A throwaway self-signed CA (openssl req -x509, EC P-256), only parsed here. */
    private fun testCa(): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(TEST_CA_PEM.byteInputStream()) as X509Certificate

    private companion object {
        const val TEST_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBjjCCATWgAwIBAgIUTK6MAuKry9do+dV9+LUeUFQ9Aa8wCgYIKoZIzj0EAwIw
HTEbMBkGA1UEAwwSTmlqaXlvbWUgVGVzdCBDQSAxMB4XDTI2MTEyMzIyMjAzMloX
DTM2MTEyMDIyMjAzMlowHTEbMBkGA1UEAwwSTmlqaXlvbWUgVGVzdCBDQSAxMFkw
EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEVo5rsHbUJ9sFurYKfnLcXi8tYuwGcKQG
bzlnCv936yor3jWFgS/XYmjh9t+0o7mNG9Y+GcEszI8lw/LGkZ0x66NTMFEwHQYD
VR0OBBYEFIsmHZpUoFFmFTNjahZ3Kn0FXaWWMB8GA1UdIwQYMBaAFIsmHZpUoFFm
FTNjahZ3Kn0FXaWWMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIg
WDh00IA1miXyw3i2Y8DLlQpYqS+EP6gFChMialDc1MECIEtn0Vrdt65pdr5qeX4K
0KBNtv81SYwiO2vGDTJ1PW//
-----END CERTIFICATE-----"""
    }
}

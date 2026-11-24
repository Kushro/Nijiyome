// SPDX-License-Identifier: AGPL-3.0-or-later
// Port of the unit tests of Tatakai Next tk-link/src/{pairing,devices,identity,discovery,firewall,playlist}.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.link.LinkCrypto
import app.nijiyome.link.LinkCsr
import app.nijiyome.link.PairingPayload as PhonePayload
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.File
import java.io.StringWriter
import java.net.InetAddress
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkUnitTest {
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun tearDown() = dirs.forEach { it.deleteRecursively() }

    private fun tmp(): File = Files.createTempDirectory("link-unit").toFile().also { dirs += it }

    private fun key(curve: String = "secp256r1"): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    private fun csr(k: KeyPair = key(), alg: String = "SHA256withECDSA"): String {
        val req = JcaPKCS10CertificationRequestBuilder(X500Name("CN=phone"), k.public).build(JcaContentSignerBuilder(alg).build(k.private))
        return StringWriter().also { w -> JcaPEMWriter(w).use { it.writeObject(req) } }.toString()
    }

    private fun t(secs: Long): Instant = Instant.ofEpochSecond(1_800_000_000 + secs)

    private fun code(e: () -> Unit): ErrorCode = assertFailsWith<PairingException> { e() }.error

    // ---- pairing.rs ----

    private class Setup(val window: PairingWindow, val devices: DeviceRegistry)

    private fun setup(): Setup {
        val identity = Identity.loadOrInit(tmp(), emptyList())
        val devices = DeviceRegistry.inMemory()
        return Setup(PairingWindow(identity, devices, "192.168.1.20", 47321, "My PC & Co"), devices)
    }

    private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @Test
    fun payloadHasTheExactFormat() {
        val w = setup().window
        val p = w.open(PairingWindow.DEFAULT_TTL, 5, t(0))
        assertEquals(10, p.code.length)
        assertTrue(p.code.all { it in crockford })
        val uri = p.uri()
        assertTrue(uri.startsWith("nijiyome-link:v1?h=192.168.1.20&p=47321&ca=${p.caFingerprint}&c=${p.code}&x=1800000300&n="), uri)
        assertTrue(uri.endsWith("&n=My%20PC%20%26%20Co"), uri)
        assertFalse(p.code in p.toString(), "toString never prints the code")
        // the phone's own parser reads it back (shared core/link code)
        assertTrue(PhonePayload.parse(uri).toString().isNotEmpty())
    }

    @Test
    fun codeIsReadAsCrockford() {
        assertEquals("0123456789", PhonePayload.normalizeCode("0123456789"))
        assertNull(PhonePayload.normalizeCode("o1i3-4567 89"), "inner spaces are not accepted")
        assertEquals("0113456789", PhonePayload.normalizeCode("OIL3456789"))
        assertEquals("0123456789", PhonePayload.normalizeCode("01234-56789"))
        assertNull(PhonePayload.normalizeCode("012345678"))
        assertNull(PhonePayload.normalizeCode("01234567890"))
        assertNull(PhonePayload.normalizeCode("0123456U89"), "U is not in the alphabet")
    }

    @Test
    fun redeemsOnceAndRegistersTheDevice() {
        val s = setup()
        val p = s.window.open(PairingWindow.DEFAULT_TTL, 5, t(0))
        val issued = s.window.redeem(p.code, csr(), "  My\u202e Phone  ", t(10))
        assertTrue("BEGIN CERTIFICATE" in issued.clientCertPem)
        assertTrue("BEGIN CERTIFICATE" in issued.caCertPem)
        assertEquals("My Phone", s.devices.get(issued.deviceId)!!.name)
        assertTrue(s.devices.isActive(issued.deviceId, t(11).epochSecond))
        assertEquals(ErrorCode.PAIRING_CLOSED, code { s.window.redeem(p.code, csr(), "x", t(11)) }, "single use")
        assertEquals(WindowStatus.Closed, s.window.status(t(11)))
    }

    @Test
    fun fiveWrongCodesCloseTheWindowEvenForTheRightOne() {
        val s = setup()
        val p = s.window.open(PairingWindow.DEFAULT_TTL, 5, t(0))
        for (left in 4 downTo 1) {
            assertEquals(ErrorCode.PAIRING_CODE_INVALID, code { s.window.redeem("0000000000", csr(), "x", t(1)) })
            assertEquals(WindowStatus.Open(t(300), left), s.window.status(t(1)))
        }
        assertEquals(ErrorCode.PAIRING_ATTEMPTS_EXCEEDED, code { s.window.redeem("0000000000", csr(), "x", t(1)) })
        assertEquals(ErrorCode.PAIRING_ATTEMPTS_EXCEEDED, code { s.window.redeem(p.code, csr(), "x", t(2)) })
        assertEquals(WindowStatus.Exhausted, s.window.status(t(2)))
        assertTrue(s.devices.list().isEmpty())
    }

    @Test
    fun expiryAndValidationOrder() {
        val s = setup()
        val w = s.window
        assertEquals(ErrorCode.PAIRING_CLOSED, code { w.redeem("x", "garbage", "x", t(0)) })
        val p = w.open(PairingWindow.DEFAULT_TTL, 5, t(0))
        assertEquals(ErrorCode.CSR_INVALID, code { w.redeem("wrong", "garbage", "x", t(1)) })
        assertEquals(WindowStatus.Open(t(300), 5), w.status(t(1)), "a bad CSR costs no attempt")
        assertEquals(ErrorCode.CSR_INVALID, code { w.redeem(p.code, "garbage", "x", t(1)) })
        assertEquals(ErrorCode.PAIRING_CLOSED, code { w.redeem(p.code, csr(), "x", t(300)) }, "exactly at expiry")
        assertEquals(WindowStatus.Closed, w.status(t(299)))
        assertTrue(s.devices.list().isEmpty())
        val p2 = w.open(PairingWindow.DEFAULT_TTL, 5, t(400))
        assertNotEquals(p.code, p2.code)
        w.redeem(p2.code, csr(), "x", t(401))
    }

    @Test
    fun rejectsForeignCurveCsrs() {
        val s = setup()
        val p = s.window.open(PairingWindow.DEFAULT_TTL, 5, t(0))
        assertEquals(ErrorCode.CSR_INVALID, code { s.window.redeem(p.code, csr(key("secp384r1"), "SHA384withECDSA"), "x", t(1)) })
    }

    @Test
    fun deviceNamesAreCleaned() {
        assertEquals("Pixel 8", LinkCsr.sanitizeDeviceName("  Pixel \u200b8\n"))
        assertEquals(LinkCsr.FALLBACK_DEVICE_NAME, LinkCsr.sanitizeDeviceName("\u202e\u0007"))
        assertEquals(64, LinkCsr.sanitizeDeviceName("a".repeat(100)).length)
    }

    // ---- devices.rs ----

    private fun dev(id: String) = Device(id = id, name = "Phone", fingerprint = "fp-$id", createdAt = 0, notAfter = 1000)

    @Test
    fun historyModeDefaultsToFullAndRoundTrips() {
        val f = File(tmp(), "devices.json")
        f.writeText("""[{"id":"a","name":"Tab","fingerprint":"f","created_at":0,"not_after":10,"platform":"android","last_seen_at":5}]""")
        assertEquals(HistoryMode.FULL, DeviceRegistry.open(f).get("a")!!.historyMode)
        for ((m, wire) in listOf(HistoryMode.FULL to "full", HistoryMode.RECEIVE_ONLY to "receive_only", HistoryMode.PAUSED to "paused")) {
            assertEquals(wire, m.wire)
            val file = File(tmp(), "d.json")
            val d = dev("x").copy(historyMode = m)
            DeviceRegistry.open(file).add(d)
            assertTrue(Regex("\"history_mode\":\\s*\"$wire\"").containsMatchIn(file.readText()), file.readText())
            assertEquals(d, DeviceRegistry.open(file).get("x"))
        }
        assertTrue(HistoryMode.FULL.acceptsPushes && HistoryMode.FULL.servesPulls)
        assertTrue(!HistoryMode.RECEIVE_ONLY.acceptsPushes && HistoryMode.RECEIVE_ONLY.servesPulls)
        assertTrue(!HistoryMode.PAUSED.acceptsPushes && !HistoryMode.PAUSED.servesPulls)
    }

    @Test
    fun setHistoryModePersistsAndRefusesRevoked() {
        val path = File(tmp(), "devices.json")
        val reg = DeviceRegistry.open(path)
        reg.add(dev("moto"))
        reg.add(dev("old"))
        assertTrue(reg.setHistoryMode("moto", HistoryMode.RECEIVE_ONLY))
        assertFalse(reg.setHistoryMode("zzz", HistoryMode.PAUSED))
        reg.revoke("old", 1)
        assertFalse(reg.setHistoryMode("old", HistoryMode.PAUSED))
        val again = DeviceRegistry.open(path)
        assertEquals(HistoryMode.RECEIVE_ONLY, again.historyMode("moto"))
        assertEquals(HistoryMode.FULL, again.historyMode("old"))
    }

    @Test
    fun aResyncAsksTheSourceToPushThenFansOutToUnpausedOthers() {
        val reg = DeviceRegistry.inMemory()
        for (id in listOf("tab", "moto", "quiet", "gone")) reg.add(dev(id))
        reg.setHistoryMode("quiet", HistoryMode.PAUSED)
        reg.setHistoryMode("moto", HistoryMode.RECEIVE_ONLY)
        reg.revoke("gone", 1)
        assertFalse(reg.startResync("moto", 10), "a receive-only device cannot be the source")
        assertFalse(reg.startResync("gone", 10), "nor a revoked one")
        assertTrue(reg.startResync("tab", 10))
        assertTrue(reg.resyncOpen("tab", 11))
        assertEquals(listOf<PhoneEvent>(PhoneEvent.HistoryResync(ResyncMode.PUSH_ALL)), reg.takeEvents("tab", 11))
        assertEquals(0, reg.finishResync("moto", 12), "no window for that device")
        assertTrue(reg.takeEvents("tab", 12).isEmpty())
        assertEquals(1, reg.finishResync("tab", 12))
        assertFalse(reg.resyncOpen("tab", 13), "closed")
        assertEquals(listOf<PhoneEvent>(PhoneEvent.HistoryResync(ResyncMode.PULL_REPLACE)), reg.takeEvents("moto", 13))
        assertTrue(reg.takeEvents("quiet", 13).isEmpty(), "paused devices are left alone")
        assertTrue(reg.takeEvents("tab", 13).isEmpty(), "not the source itself")
        assertEquals(0, reg.finishResync("tab", 14), "a second done fans out nothing")
    }

    @Test
    fun theResyncWindowOpensExpiresAndClosesOnce() {
        val reg = DeviceRegistry.inMemory()
        assertFalse(reg.resyncOpen("tab", 0))
        reg.openResync("tab", 100)
        assertTrue(reg.resyncOpen("tab", 100 + RESYNC_WINDOW_SECS - 1))
        assertFalse(reg.resyncOpen("tab", 100 + RESYNC_WINDOW_SECS), "expired")
        assertFalse(reg.resyncOpen("moto", 101), "per device")
        assertTrue(reg.closeResync("tab", 200))
        assertFalse(reg.closeResync("tab", 201), "closed once")
        reg.openResync("tab", 0)
        assertFalse(reg.closeResync("tab", RESYNC_WINDOW_SECS + 1), "an expired window does not count")
    }

    @Test
    fun touchTracksLastSeenAndPlatform() {
        val path = File(tmp(), "devices.json")
        val reg = DeviceRegistry.open(path)
        reg.add(dev("a"))
        reg.touch("a", 100, "okhttp/4.12.0")
        assertEquals(100L, reg.get("a")!!.lastSeenAt)
        assertEquals("android", reg.get("a")!!.platform)
        reg.touch("a", 110, null)
        assertEquals(110L, reg.get("a")!!.lastSeenAt, "always current in memory")
        assertEquals(100L, DeviceRegistry.open(path).get("a")!!.lastSeenAt, "persisted at most once a minute")
        assertEquals("ios", platformFromUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"))
        assertNull(platformFromUserAgent("curl/8"))
    }

    @Test
    fun eventMailboxIsBoundedAndDrained() {
        val reg = DeviceRegistry.inMemory()
        reg.add(dev("a"))
        fun ev(n: Int) = PhoneEvent.Handoff(sessionId = "s$n", episode = n)
        assertFalse(reg.pushEvent("zzz", ev(0), 0), "unknown device")
        for (n in 0 until EVENTS_QUEUE_MAX + 3) assertTrue(reg.pushEvent("a", ev(n), 10))
        val got = reg.takeEvents("a", 11)
        assertEquals(EVENTS_QUEUE_MAX, got.size)
        assertEquals(ev(3), got[0], "oldest dropped first")
        assertTrue(reg.takeEvents("a", 11).isEmpty(), "drained on delivery")
        reg.pushEvent("a", ev(1), 0)
        assertTrue(reg.takeEvents("a", EVENT_TTL_SECS + 1).isEmpty(), "expired")
    }

    @Test
    fun persistsRevocationAndNotifies() {
        val path = File(tmp(), "link/devices.json")
        val reg = DeviceRegistry.open(path)
        val revoked = mutableListOf<String>()
        reg.onRevoked { revoked += it }
        reg.add(dev("a"))
        assertTrue(reg.isActive("a", 10))
        assertFalse(reg.isActive("a", 1000), "expired certificate")
        assertTrue(reg.revoke("a", 20))
        assertFalse(reg.isActive("a", 21))
        assertFalse(reg.revoke("a", 22), "second revoke is a no-op")
        assertEquals(listOf("a"), revoked)
        val again = DeviceRegistry.open(path)
        assertFalse(again.isActive("a", 21))
        assertEquals(20L, again.byFingerprint("fp-a")!!.revokedAt)
    }

    @Test
    fun renamePersistsAndRefusesBadInput() {
        val path = File(tmp(), "devices.json")
        val reg = DeviceRegistry.open(path)
        reg.add(dev("a"))
        reg.add(dev("b"))
        assertTrue(reg.rename("a", "  Pixel de Ana "))
        assertFalse(reg.rename("a", "   "), "empty name")
        assertFalse(reg.rename("zzz", "x"), "unknown device")
        assertTrue(reg.revoke("b", 5))
        assertFalse(reg.rename("b", "x"), "revoked device")
        val again = DeviceRegistry.open(path)
        assertEquals("Pixel de Ana", again.get("a")!!.name)
        assertEquals("Phone", again.get("b")!!.name)
    }

    // ---- identity.rs ----

    @Test
    fun createsPersistsAndReloadsTheSameCaAndRenewsTheServerCertOnIpChange() {
        val dir = tmp()
        val ips = listOf(InetAddress.getByName("192.168.1.20"))
        val a = Identity.loadOrInit(dir, ips)
        val pin = a.caFingerprint
        assertEquals(64, pin.length)
        assertTrue(InetAddress.getByName("192.168.1.20") in a.serverIps())
        assertTrue(InetAddress.getByName("127.0.0.1") in a.serverIps())
        val b = Identity.loadOrInit(dir, ips)
        assertEquals(pin, b.caFingerprint)
        assertEquals(a.serverChain()[0], b.serverChain()[0], "unchanged IPs keep the server cert")
        val c = Identity.loadOrInit(dir, listOf(InetAddress.getByName("192.168.1.99")))
        assertEquals(pin, c.caFingerprint, "the CA (and so the phone pin) never changes")
        assertTrue(InetAddress.getByName("192.168.1.99") in c.serverIps())
        assertFalse(InetAddress.getByName("192.168.1.20") in c.serverIps())
    }

    @Test
    fun issuesAClientCertificateForTheCsrKeyOnly() {
        val id = Identity.loadOrInit(tmp(), emptyList())
        val k = key()
        val issued = id.issueClientFromCsr(csr(k), Instant.now())
        assertContentEquals(k.public.encoded, issued.certificate.publicKey.encoded)
        assertTrue(issued.deviceId in issued.certificate.subjectX500Principal.name)
        assertEquals(LinkCrypto.fingerprint(issued.certificate), issued.fingerprint)
        issued.certificate.verify(id.ca.publicKey)
        assertFailsWith<IdentityException> { id.issueClientFromCsr("not a csr", Instant.now()) }
        val lines = csr(k).lines().filter { it.isNotEmpty() }.toMutableList()
        val i = lines.size - 2
        lines[i] = (if (lines[i][0] == 'A') 'B' else 'A') + lines[i].substring(1)
        assertFailsWith<IdentityException> { id.issueClientFromCsr(lines.joinToString("\n"), Instant.now()) }
    }

    // ---- discovery.rs ----

    @Test
    fun txtRecordsUseTheCanonicalKeys() {
        assertEquals(listOf("v" to "1", "name" to "My PC", "id" to "abc-123", "fp" to "abcdef"), Discovery.txtRecords(" My PC ", "abc-123", "ABCDEF"))
        assertEquals("nijiyome-3f2a91c077aa.local.", Discovery.hostLabel("3f2a-91c0-77aa-bb11-0000"))
        assertEquals("nijiyome-pc.local.", Discovery.hostLabel("--"))
    }

    @Test
    fun instanceNamesAreBoundedAndDotless() {
        assertEquals("MyPC", Discovery.instanceName("My.PC"))
        assertEquals("Nijiyome", Discovery.instanceName("   "))
        assertTrue(Discovery.instanceName("x".repeat(100)).length <= 63)
    }

    // ---- firewall.rs (the command is built here; running it is replaced) ----

    /** The script the elevated PowerShell runs, decoded back from the outer script's `-EncodedCommand`. */
    private fun elevatedOf(outer: String): String {
        val b64 = Regex("""'-EncodedCommand','([A-Za-z0-9+/=]+)'""").find(outer)?.groupValues?.get(1) ?: error(outer)
        return String(Base64.getDecoder().decode(b64), Charsets.UTF_16LE)
    }

    @Test
    fun theRuleIsPrivateProfileInboundTcpOnThisAppsPortsOnly() {
        val a = Firewall.addRuleArgs(File("C:\\Apps\\Nijiyome\\Nijiyome.exe"), listOf(8099, 47321, 8099))
        for (x in listOf("profile=private", "dir=in", "protocol=TCP", "localport=8099,47321", "enable=yes")) assertTrue(x in a, x)
        assertFalse(a.any { "public" in it || "any" in it || "profile=all" in it })
        assertTrue(a.any { it.startsWith("program=") && it.endsWith("Nijiyome.exe") })
        // One UAC prompt: only the outer PowerShell elevates; netsh runs inside it, hidden and waited for.
        val ps = Firewall.elevateScript(listOf(a))
        assertEquals(1, Regex("-Verb RunAs").findAll(ps).count(), ps)
        assertTrue("Start-Process -FilePath 'powershell.exe'" in ps && "-Wait -PassThru" in ps, ps)
        val inner = elevatedOf(ps)
        assertTrue("Start-Process -FilePath 'netsh.exe'" in inner && "-Wait -PassThru" in inner && "RunAs" !in inner, inner)
        assertTrue("'localport=8099,47321'" in inner && "'profile=private'" in inner, inner)
        assertFailsWith<IllegalArgumentException> { Firewall.addRuleArgs(File("x.exe"), emptyList()) }
        assertFailsWith<IllegalArgumentException> { Firewall.elevateScript(emptyList()) }
    }

    @Test
    fun allowingAgainReplacesTheRuleSoTheOldPortIsNotLeftOpen() {
        val exe = File("C:\\Apps\\Nijiyome.exe")
        val steps = Firewall.ruleSteps(exe, listOf(47400, 8099))
        assertEquals(listOf(Firewall.deleteRuleArgs(), Firewall.addRuleArgs(exe, listOf(47400, 8099))), steps)
        assertEquals(listOf("advfirewall", "firewall", "delete", "rule", "name=${Firewall.RULE_NAME}"), steps[0])
        var seen: List<String>? = null
        Firewall.allowPrivateNetwork(exe, listOf(47400, 8099)) { seen = it; 0 }
        val inner = elevatedOf(seen!!.last())
        val delete = inner.indexOf("'delete','rule','name=\"${Firewall.RULE_NAME}\"'")
        val add = inner.indexOf("'add','rule','name=\"${Firewall.RULE_NAME}\"'")
        assertTrue(delete >= 0 && add > delete, inner)
        // Only the add decides the outcome (there may be no rule to delete); its failure is never the UAC code.
        assertTrue(inner.trimEnd().endsWith("if (\$p.ExitCode -eq 0) { exit 0 } else { exit 1 }"), inner)
        assertFalse(Firewall.CANCELLED.toString() in inner, inner)
    }

    @Test
    fun onlyARefusedPromptIsCancelledAnyOtherErrorFails() {
        val ps = Firewall.elevateScript(Firewall.ruleSteps(File("C:\\Apps\\Nijiyome.exe"), listOf(47321)))
        // No blanket "catch { exit 1223 }": the code is returned only for a Win32Exception with ERROR_CANCELLED in the chain.
        assertFalse(Regex("""catch \{ exit \d+ \}""").containsMatchIn(ps), ps)
        assertTrue("[System.ComponentModel.Win32Exception] -and \$e.NativeErrorCode -eq 1223) { exit 1223 }" in ps, ps)
        assertTrue("\$e = \$e.InnerException }; exit 1 }" in ps, ps)
        // A non-zero exit of the elevated script maps to 1, never passes through as 1223.
        assertTrue("if (\$p.ExitCode -eq 0) { exit 0 } else { exit 1 } } catch" in ps, ps)
    }

    @Test
    fun aProgramPathWithSpacesIsQuotedForNetshAndForPowershell() {
        assertEquals("program=\"C:\\Program Files\\Nijiyome\\Nijiyome.exe\"", Firewall.cmdlineArg("program=C:\\Program Files\\Nijiyome\\Nijiyome.exe"))
        assertEquals("localport=47321", Firewall.cmdlineArg("localport=47321"))
        assertEquals("'it''s'", Firewall.psQuote("it's"))
        val ps = Firewall.elevateScript(listOf(Firewall.addRuleArgs(File("C:\\O'Brien Apps\\Nijiyome.exe"), listOf(47321))))
        assertTrue("'program=\"C:\\O''Brien Apps\\Nijiyome.exe\"'" in elevatedOf(ps), ps)
        // The outer script carries the inner one only as Base64: no path text to quote a second time.
        assertFalse("Brien" in ps, ps)
    }

    @Test
    fun allowPrivateNetworkMapsTheExitCodeAndRefusesUnquotablePaths() {
        var seen: List<String>? = null
        val exe = File("C:\\Apps\\Nijiyome.exe")
        assertEquals(Firewall.Outcome.ADDED, Firewall.allowPrivateNetwork(exe, listOf(47321)) { seen = it; 0 })
        assertEquals(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command"), seen!!.take(6))
        assertEquals(Firewall.Outcome.CANCELLED, Firewall.allowPrivateNetwork(exe, listOf(47321)) { Firewall.CANCELLED })
        assertEquals(Firewall.Outcome.FAILED, Firewall.allowPrivateNetwork(exe, listOf(47321)) { 1 })
        assertEquals(Firewall.Outcome.FAILED, Firewall.allowPrivateNetwork(exe, listOf(47321)) { error("no powershell") })
        var ran = false
        assertEquals(Firewall.Outcome.FAILED, Firewall.allowPrivateNetwork(File("C:\\a\"b.exe"), listOf(47321)) { ran = true; 0 })
        assertFalse(ran)
    }

    // ---- playlist.rs ----

    private val base = "https://cdn.example.com/hls/master.m3u8".toHttpUrl()
    private val kindName = mapOf(
        Playlist.UriKind.PLAYLIST to "Playlist", Playlist.UriKind.SEGMENT to "Segment", Playlist.UriKind.KEY to "Key", Playlist.UriKind.MAP to "Map",
    )
    private val tag: (okhttp3.HttpUrl, Playlist.UriKind) -> String = { u, k -> "/${kindName[k]}/$u" }
    private val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720\nv720/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=500\nhttps://other.example.com/low.m3u8\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"audio/en.m3u8\"\n"
    private val media = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-KEY:METHOD=AES-128,URI=\"keys/k1.bin\",IV=0x01\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4.5,\nseg0.ts\n#EXTINF:5.5,\n/abs/seg1.ts\n#EXT-X-ENDLIST\n"

    private fun reason(body: ByteArray, depth: Int, map: (okhttp3.HttpUrl, Playlist.UriKind) -> String = tag) =
        assertFailsWith<Playlist.RewriteException> { Playlist.rewrite(body, base, depth, map) }.reason

    @Test
    fun rewritesMasterVariantsAndAlternativeRenditions() {
        val r = Playlist.rewrite(master.toByteArray(), base, 0, tag)
        assertTrue(r.isMaster)
        assertTrue("\n/Playlist/https://cdn.example.com/hls/v720/index.m3u8\n" in r.text)
        assertTrue("\n/Playlist/https://other.example.com/low.m3u8\n" in r.text)
        assertTrue("URI=\"/Playlist/https://cdn.example.com/hls/audio/en.m3u8\"" in r.text)
        assertTrue("#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720\n" in r.text, "tags are preserved")
    }

    @Test
    fun rewritesMediaSegmentsKeysAndMapsAndSumsTheDuration() {
        val r = Playlist.rewrite(media.toByteArray(), base, 1, tag)
        assertFalse(r.isMaster)
        assertEquals(10.0, r.durationSecs)
        assertTrue("#EXT-X-KEY:METHOD=AES-128,URI=\"/Key/https://cdn.example.com/hls/keys/k1.bin\",IV=0x01" in r.text)
        assertTrue("#EXT-X-MAP:URI=\"/Map/https://cdn.example.com/hls/init.mp4\"" in r.text)
        assertTrue("\n/Segment/https://cdn.example.com/hls/seg0.ts\n" in r.text)
        assertTrue("\n/Segment/https://cdn.example.com/abs/seg1.ts\n" in r.text)
        assertTrue("#EXT-X-ENDLIST" in r.text)
        val none = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:1,\na.ts\n"
        assertTrue("#EXT-X-KEY:METHOD=NONE\n" in Playlist.rewrite(none.toByteArray(), base, 1, tag).text)
    }

    @Test
    fun rejectsDataFileAndOtherSchemesEverywhere() {
        val forbidden = Playlist.RewriteException.Reason.FORBIDDEN_SCHEME
        for (uri in listOf("data:text/plain;base64,AAAA", "file:///etc/passwd", "ftp://h/x.ts", "javascript:alert(1)", "skd://key", "gopher://h/")) {
            assertEquals(forbidden, reason("#EXTM3U\n#EXTINF:1,\n$uri\n".toByteArray(), 1), "segment $uri")
            assertEquals(forbidden, reason("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"$uri\"\n#EXTINF:1,\na.ts\n".toByteArray(), 1), "key $uri")
            assertEquals(forbidden, reason("#EXTM3U\n#EXT-X-MAP:URI=\"$uri\"\n#EXTINF:1,\na.ts\n".toByteArray(), 1), "map $uri")
            assertEquals(forbidden, reason("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n$uri\n".toByteArray(), 0), "variant $uri")
        }
        // a UNC-looking path is either refused or resolved as a relative https path: never a file share
        runCatching { Playlist.rewrite("#EXTM3U\n#EXTINF:1,\n\\\\server\\share\\x.ts\n".toByteArray(), base, 1, tag) }
            .onSuccess { r -> assertFalse("file:" in r.text) }
    }

    @Test
    fun rejectsOversizedNestedAndNonPlaylists() {
        val big = StringBuilder("#EXTM3U\n")
        while (big.length <= Playlist.MAX_PLAYLIST_BYTES) big.append("#EXTINF:1,\nsegment-with-a-long-enough-name.ts\n")
        assertEquals(Playlist.RewriteException.Reason.TOO_LARGE, reason(big.toString().toByteArray(), 1))
        assertEquals(Playlist.RewriteException.Reason.TOO_DEEP, reason(master.toByteArray(), 1), "a master reached at depth 1 is nesting")
        assertEquals(Playlist.RewriteException.Reason.NOT_PLAYLIST, reason("<html>not hls</html>".toByteArray(), 0))
        assertEquals(Playlist.RewriteException.Reason.NOT_UTF8, reason(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0), 0))
        assertEquals(Playlist.RewriteException.Reason.BAD_URI, reason("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"oops\n#EXTINF:1,\na.ts\n".toByteArray(), 1))
    }

    @Test
    fun aKeyOnADisallowedHostIsRejectedByTheMapper() {
        val deny: (okhttp3.HttpUrl, Playlist.UriKind) -> String = { u, k ->
            if (k == Playlist.UriKind.KEY && u.host == "evil.internal") throw Playlist.RewriteException(Playlist.RewriteException.Reason.DENIED) else u.toString()
        }
        assertEquals(Playlist.RewriteException.Reason.DENIED, reason("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://evil.internal/key\"\n#EXTINF:1,\na.ts\n".toByteArray(), 1, deny))
        Playlist.rewrite("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.example.com/key\"\n#EXTINF:1,\na.ts\n".toByteArray(), base, 1, deny)
    }

    @Test
    fun crlfPlaylistsAreNormalisedAndAttributeNamesMustMatchExactly() {
        assertFalse('\r' in Playlist.rewrite(media.replace("\n", "\r\n").toByteArray(), base, 1, tag).text)
        val r = Playlist.rewrite("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,XURI=\"keep\",URI=\"k\"\n#EXTINF:1,\na.ts\n".toByteArray(), base, 1, tag)
        assertTrue("XURI=\"keep\"" in r.text)
        assertTrue(",URI=\"/Key/https://cdn.example.com/hls/k\"" in r.text)
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.extensions.BrowserError
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parts of the desktop hidden browser that need no browser: finding one, its switches, the evaluate envelope, cookies, prefs. */
class HiddenBrowserLogicTest {
    /** A fake file system: path -> (executable, first bytes, canonical path). */
    private class Fs(val files: Map<String, Triple<Boolean, String, String?>>) {
        fun locator(os: String = "Linux", path: String = "/usr/bin:/bin", env: Map<String, String> = emptyMap(), home: String = "/home/u") =
            ChromiumLocator(
                os = os,
                env = { k -> if (k == "PATH") path else env[k] },
                home = home,
                exists = { it.path in files },
                executable = { files[it.path]?.first == true },
                canonical = { files[it.path]?.third ?: it.path },
                head = { files[it.path]?.second.orEmpty() },
            )
    }

    private val elf = "\u007fELF"

    /** The fake Linux file system is keyed by `/` paths; on a Windows host `File.path` turns them into `\` ones. */
    private fun assumeLinuxHost() = assumeTrue("a simulated Linux file system", File.separatorChar == '/')

    @Test
    fun findsTheFirstUsualBrowserAndPrefersAValidManualPath() {
        assumeLinuxHost()
        val fs = Fs(
            mapOf(
                "/usr/bin/microsoft-edge-stable" to Triple(true, elf, "/opt/microsoft/msedge/msedge"),
                "/opt/microsoft/msedge/msedge" to Triple(true, elf, null),
                "/home/u/apps/thorium" to Triple(true, elf, null),
            ),
        )
        val l = fs.locator()
        // the real binary before the PATH launcher
        assertEquals(ChromiumExecutable("/opt/microsoft/msedge/msedge", manual = false), l.find(null))
        assertEquals(ChromiumExecutable("/home/u/apps/thorium", manual = true), l.find("  /home/u/apps/thorium "))
        // a wrong manual path falls back to the automatic one, and says why
        assertEquals("/opt/microsoft/msedge/msedge", l.find("/nope")?.path)
        // only the PATH launcher: it is used
        assertEquals("/usr/bin/microsoft-edge-stable", Fs(mapOf("/usr/bin/microsoft-edge-stable" to Triple(true, "#!/usr/bin/env sh\nexec /opt/microsoft/msedge/microsoft-edge", null))).locator().find(null)?.path)
        assertEquals(ChromiumLocator.Problem.MISSING, l.check("/nope"))
        assertNull(Fs(emptyMap()).locator().find(null))
    }

    @Test
    fun aManualPathMustBeAnExecutableAndNotASandboxedWrapper() {
        assumeLinuxHost()
        val fs = Fs(
            mapOf(
                "/home/u/notes.txt" to Triple(false, "hello", null),
                "/snap/bin/chromium" to Triple(true, elf, "/usr/bin/snap"),
                "/usr/bin/chromium-browser" to Triple(true, "#!/bin/sh\nexec /snap/bin/chromium \"\$@\"\n", null),
                "/var/lib/flatpak/exports/bin/com.google.Chrome" to Triple(true, "#!/bin/sh\nexec /usr/bin/flatpak run --branch=stable com.google.Chrome \"\$@\"", null),
                "/usr/local/bin/chrome-wrapper" to Triple(true, "#!/bin/sh\nexec /opt/google/chrome/chrome \"\$@\"", null),
            ),
        )
        val l = fs.locator()
        assertEquals(ChromiumLocator.Problem.NOT_EXECUTABLE, l.check("/home/u/notes.txt"))
        assertEquals(ChromiumLocator.Problem.SANDBOXED, l.check("/snap/bin/chromium"))
        assertEquals(ChromiumLocator.Problem.SANDBOXED, l.check("/usr/bin/chromium-browser"))
        assertEquals(ChromiumLocator.Problem.SANDBOXED, l.check("/var/lib/flatpak/exports/bin/com.google.Chrome"))
        assertNull(l.check("/usr/local/bin/chrome-wrapper"), "a plain wrapper script of a native install is fine")
        // the Ubuntu chromium-browser that is really the Snap is not picked automatically either
        assertNull(l.find(null))
    }

    @Test
    fun flatpakAndSnapInstallsAreReportedNotUsed() {
        assumeLinuxHost()
        assertEquals("Flatpak (com.google.Chrome)", Fs(mapOf("/var/lib/flatpak/exports/bin/com.google.Chrome" to Triple(true, "#!/bin/sh", null))).locator().sandboxedInstall())
        assertEquals("Flatpak (org.chromium.Chromium)", Fs(mapOf("/home/u/.local/share/flatpak/exports/bin/org.chromium.Chromium" to Triple(true, "#!/bin/sh", null))).locator().sandboxedInstall())
        assertEquals("Snap (chromium)", Fs(mapOf("/snap/bin/chromium" to Triple(true, elf, "/usr/bin/snap"))).locator().sandboxedInstall())
        assertNull(Fs(emptyMap()).locator().sandboxedInstall())
        assertNull(Fs(emptyMap()).locator(os = "Windows 11").sandboxedInstall())
    }

    @Test
    fun windowsLooksForEdgeAndChromeInProgramFiles() {
        val fs = Fs(mapOf(File("C:\\Program Files (x86)", "Microsoft\\Edge\\Application\\msedge.exe").path to Triple(true, "MZ", null)))
        val found = fs.locator(os = "Windows 11", path = "", env = mapOf("ProgramFiles(x86)" to "C:\\Program Files (x86)")).find(null)
        assertEquals(File("C:\\Program Files (x86)", "Microsoft\\Edge\\Application\\msedge.exe").path, found?.path)
    }

    @Test
    fun allTrafficGoesThroughTheGatewayWithNoCredentialsOnTheCommandLine() {
        BrowserGateway(upstream = ProxyEndpoint(ProxyKind.HTTP, "proxy.lan", 3128, "u", "p")).use { g ->
            val a = g.chromiumArgs()
            assertEquals("--proxy-server=socks5://127.0.0.1:${g.port}", a[0])
            assertTrue("--proxy-bypass-list=<-loopback>" in a, "not even loopback skips the gateway")
            assertTrue("--force-webrtc-ip-handling-policy=disable_non_proxied_udp" in a)
            assertTrue(a.none { "proxy.lan" in it || "u:p" in it || "@" in it }, "the outbound proxy and its password stay in the JVM")
        }
    }

    @Test
    fun launchSwitchesKeepTheBrowserHeadlessQuietAndUndetected() {
        val a = ChromiumHost.BASE_ARGS
        assertTrue("--headless" in a)
        assertTrue("--disable-blink-features=AutomationControlled" in a)
        assertTrue("--password-store=basic" in a && "--use-mock-keychain" in a)
        assertTrue("--mute-audio" in a)
        assertTrue(a.none { it.startsWith("--remote-debugging-address") || it.startsWith("--remote-allow-origins") }, "never exposed beyond loopback")
    }

    @Test
    fun evaluateEnvelopeAndCookieShape() {
        assertEquals("x", CdpBrowserRuntime.parseEnvelope(JsonPrimitive("""{"ok":true,"v":{"a":"x"}}""")).jsonObject["a"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, CdpBrowserRuntime.parseEnvelope(JsonPrimitive("""{"ok":true,"v":null}""")))
        assertEquals("boom", assertFailsWith<BrowserError.Failed> { CdpBrowserRuntime.parseEnvelope(JsonPrimitive("""{"ok":false,"e":"boom"}""")) }.message)
        assertFailsWith<BrowserError.Failed> { CdpBrowserRuntime.parseEnvelope(JsonNull) }
        assertFailsWith<BrowserError.Failed> { CdpBrowserRuntime.parseEnvelope(JsonPrimitive(3)) }
        assertFailsWith<BrowserError.TooLarge> { CdpBrowserRuntime.parseEnvelope(JsonPrimitive("x".repeat(2_000_001))) }

        val c = CdpBrowserRuntime.cookieJson(
            buildJsonObject {
                put("name", "cf_clearance"); put("value", "v"); put("domain", ".example.com"); put("path", "/")
                put("expires", 1.9e9); put("size", 13); put("httpOnly", true); put("secure", true); put("session", false); put("sameSite", "None")
            },
        )
        assertEquals(".example.com", c["domain"]!!.jsonPrimitive.content)
        assertEquals(true, c["httpOnly"]!!.jsonPrimitive.boolean)
        assertEquals(false, c["session"]!!.jsonPrimitive.boolean)
        assertEquals(1.9e9, c["expires"]!!.jsonPrimitive.double)
        assertEquals("None", c["sameSite"]!!.jsonPrimitive.content)
        assertEquals(setOf("name", "value", "domain", "path", "expires", "size", "httpOnly", "secure", "session", "sameSite"), c.keys)
    }

    @Test
    fun theManualPathIsKeptInItsOwnFile() {
        val dir = Files.createTempDirectory("nij-browser-prefs").toFile()
        try {
            val file = File(dir, DesktopHiddenBrowser.PREFS_FILE)
            val exe = File(dir, "my-chromium").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
            // only files of this folder exist for it (the machine's own browsers do not count here)
            val noBrowser = ChromiumLocator(env = { null }, exists = { it.path.startsWith(dir.path) && it.isFile })
            val b = DesktopHiddenBrowser(file, noBrowser)
            assertFalse(b.state.value.available)
            b.setManualPath(" ${exe.path} ")
            assertEquals(exe.path, b.state.value.browser?.path)
            assertTrue(b.state.value.available)
            assertTrue(file.readText().contains("browser_path"))
            assertEquals(exe.path, DesktopHiddenBrowser(file, noBrowser).state.value.manualPath, "read back at the next start")
            b.setManualPath(File(dir, "missing").path)
            assertEquals(ChromiumLocator.Problem.MISSING, b.state.value.manualProblem)
            assertFalse(b.state.value.available)
            b.setManualPath("")
            assertEquals("{}", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}

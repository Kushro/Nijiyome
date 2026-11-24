// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/discovery.rs and firewall.rs (AGPL-3.0, same author). mdns-sd replaced by JmDNS (Apache-2.0).
package app.nijiyome.server.link

import app.nijiyome.link.DiscoveryWire
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.util.Base64
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * mDNS announcement of Nijiyome Link: service type `_nijiyome-link._tcp` (the phone's `DiscoveryWire.SERVICE_TYPE`), instance =
 * the server name, port = the Link port, TXT `v=1`, `name`, `id` (= `instance_id` of `/v1/capabilities`) and `fp` (= SHA-256 of
 * the Link CA, the pin the phone confirms before pairing). Announced on the addresses the server listens on, never elsewhere.
 */
object Discovery {
    /** The TXT records in the canonical key order (`v`, `name`, `id`, `fp`). */
    fun txtRecords(serverName: String, instanceId: String, fingerprintHex: String): List<Pair<String, String>> {
        val name = serverName.trim().filterNot { it.isISOControl() }.take(64)
        return listOf(
            DiscoveryWire.TXT_VERSION to "1",
            DiscoveryWire.TXT_NAME to name.ifEmpty { "Nijiyome" },
            DiscoveryWire.TXT_INSTANCE_ID to instanceId,
            DiscoveryWire.TXT_FINGERPRINT to fingerprintHex.lowercase(),
        )
    }

    /** mDNS host name of this PC: `nijiyome-<first 12 alphanumerics of the instance id>.local.` (no personal data). */
    fun hostLabel(instanceId: String): String {
        val id = instanceId.filter { it.isLetterOrDigit() && it.code < 128 }.take(12).lowercase()
        return "nijiyome-${id.ifEmpty { "pc" }}.local."
    }

    /** Instance name as mDNS allows it: at most 63 bytes, no dots. */
    fun instanceName(serverName: String): String {
        val cleaned = serverName.trim().filter { it != '.' }.ifEmpty { "Nijiyome" }
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < cleaned.length) {
            val cp = cleaned.codePointAt(i)
            val n = String(Character.toChars(cp)).toByteArray().size
            if (bytes + n > 63) break
            out.appendCodePoint(cp)
            bytes += n
            i += Character.charCount(cp)
        }
        return out.toString()
    }
}

/** A running announcement; [close] unregisters it and stops the responder. */
fun interface Announcement : Closeable

/** Where the announcement goes (JmDNS on the LAN; an in-memory responder in tests). */
fun interface Announcer {
    fun announce(addresses: List<InetAddress>, serverName: String, instanceId: String, port: Int, fingerprintHex: String): Announcement
}

/** One JmDNS responder per listening IPv4 address (loopback and wildcards are skipped: phones cannot use them). */
object JmdnsAnnouncer : Announcer {
    override fun announce(addresses: List<InetAddress>, serverName: String, instanceId: String, port: Int, fingerprintHex: String): Announcement {
        val usable = addresses.filter { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
        val txt = Discovery.txtRecords(serverName, instanceId, fingerprintHex).toMap()
        val responders = usable.mapNotNull { ip ->
            runCatching {
                val dns = JmDNS.create(ip, Discovery.hostLabel(instanceId).removeSuffix(".local."))
                dns.registerService(ServiceInfo.create("${DiscoveryWire.SERVICE_TYPE}.local.", Discovery.instanceName(serverName), port, 0, 0, txt))
                dns
            }.getOrNull()
        }
        return Announcement {
            responders.forEach { d ->
                runCatching {
                    d.unregisterAllServices()
                    d.close()
                }
            }
        }
    }
}

/**
 * Windows Firewall rule of this app's ports (Private profile only), after Tatakai Next tk-app/src/firewall.rs. Creating it needs
 * elevation, so it is only ever run from an explicit user action with a UAC prompt ("Permitir en red privada" in Settings ›
 * Nijiyome Link), never automatically and never for the Public profile (review E6).
 */
object Firewall {
    const val RULE_NAME = "Nijiyome Link"

    /** Exit code of the script when the administrator prompt was cancelled (ERROR_CANCELLED). */
    const val CANCELLED = 1223

    /** `netsh` arguments of the inbound rule: TCP [ports] (one or more), this [exe], Private profile. */
    fun addRuleArgs(exe: File, ports: List<Int>): List<String> {
        require(ports.isNotEmpty() && ports.all { it in 1..65535 }) { "ports" }
        return listOf(
            "advfirewall", "firewall", "add", "rule", "name=$RULE_NAME", "dir=in", "action=allow", "protocol=TCP",
            "localport=${ports.distinct().sorted().joinToString(",")}", "profile=private", "program=${exe.path}", "enable=yes",
        )
    }

    fun deleteRuleArgs(): List<String> = listOf("advfirewall", "firewall", "delete", "rule", "name=$RULE_NAME")

    /** A PowerShell single-quoted literal. */
    fun psQuote(s: String): String = "'" + s.replace("'", "''") + "'"

    /**
     * One netsh argument as it must appear on its command line: a value with spaces goes in double quotes
     * (`program="C:\Program Files\Nijiyome\Nijiyome.exe"`), because `Start-Process -ArgumentList` does not quote.
     */
    fun cmdlineArg(a: String): String {
        val eq = a.indexOf('=')
        return if (eq > 0 && a.contains(' ')) "${a.substring(0, eq)}=\"${a.substring(eq + 1)}\"" else a
    }

    /**
     * The script that runs elevated: each netsh command of [steps] in order (hidden, waiting for it). Exit 0 only if the last one
     * succeeded, else 1; the earlier ones may fail (deleting a rule that is not there). Never [CANCELLED]: that code means only the
     * refused prompt.
     */
    fun elevatedScript(steps: List<List<String>>): String {
        require(steps.isNotEmpty()) { "steps" }
        val runs = steps.joinToString("; ") { args ->
            val list = args.joinToString(",") { psQuote(cmdlineArg(it)) }
            "\$p = Start-Process -FilePath 'netsh.exe' -ArgumentList @($list) -WindowStyle Hidden -Wait -PassThru"
        }
        return "\$ErrorActionPreference='Stop'; $runs; if (\$p.ExitCode -eq 0) { exit 0 } else { exit 1 }"
    }

    /** [script] as `powershell -EncodedCommand` takes it (Base64 of UTF-16LE): no quoting left between the two processes. */
    fun encodedCommand(script: String): String = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

    /**
     * The script that starts [elevatedScript] of [steps] in an elevated PowerShell (one UAC prompt for all of them) and waits:
     * exit 0 only if it succeeded, [CANCELLED] only when the prompt was refused (a Win32Exception with ERROR_CANCELLED anywhere in
     * the error's chain), 1 for every other error.
     */
    fun elevateScript(steps: List<List<String>>): String {
        val inner = listOf("-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encodedCommand(elevatedScript(steps)))
        val list = inner.joinToString(",") { psQuote(it) }
        return "\$ErrorActionPreference='Stop'; try { \$p = Start-Process -FilePath 'powershell.exe' -ArgumentList @($list) -Verb RunAs " +
            "-WindowStyle Hidden -Wait -PassThru; if (\$p.ExitCode -eq 0) { exit 0 } else { exit 1 } } catch { \$e = \$_.Exception; " +
            "while (\$e) { if (\$e -is [System.ComponentModel.Win32Exception] -and \$e.NativeErrorCode -eq $CANCELLED) { exit $CANCELLED }; " +
            "\$e = \$e.InnerException }; exit 1 }"
    }

    /** The elevated steps of [allowPrivateNetwork]: drop every earlier rule of this app (other ports), then add the current one. */
    fun ruleSteps(exe: File, ports: List<Int>): List<List<String>> = listOf(deleteRuleArgs(), addRuleArgs(exe, ports))

    /** The whole process command line; run only by [allowPrivateNetwork]. */
    fun powershellInvocation(steps: List<List<String>>): List<String> =
        listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", elevateScript(steps))

    /** What [allowPrivateNetwork] ended with. */
    enum class Outcome { ADDED, CANCELLED, FAILED }

    /**
     * Runs the elevation flow for [exe] and [ports] (blocking: call it off the UI thread). [run] starts the command line and returns
     * its exit code (tests replace it). The rule is replaced, not added again: changing a port and allowing again leaves no rule
     * open on the old port. A path with a double quote or a control character is refused (it could not be quoted).
     */
    fun allowPrivateNetwork(exe: File, ports: List<Int>, run: (List<String>) -> Int = ::runHidden): Outcome {
        if (exe.path.any { it == '"' || it < ' ' }) return Outcome.FAILED
        val code = runCatching { run(powershellInvocation(ruleSteps(exe, ports))) }.getOrElse { return Outcome.FAILED }
        return when (code) {
            0 -> Outcome.ADDED
            CANCELLED -> Outcome.CANCELLED
            else -> Outcome.FAILED
        }
    }

    private fun runHidden(cmd: List<String>): Int {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.close()
        return p.waitFor()
    }
}

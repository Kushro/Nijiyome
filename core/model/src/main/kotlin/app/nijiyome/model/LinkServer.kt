// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.coroutines.flow.StateFlow

/**
 * The Nijiyome Link server of this PC and its integrated extension server (desktop only: Settings › Nijiyome Link shows them when
 * this is registered; Android never registers it and keeps its own client section). Every action is asynchronous: it changes
 * [state] when it lands.
 */
interface LinkServerController {
    val state: StateFlow<LinkServerState>

    /** Turns the Link server on (it then starts with the app) or off (it stops and stays stopped). */
    fun setEnabled(enabled: Boolean)

    /** The name phones see (mDNS, pairing payload, capabilities); blank = the default one. Restarts a running server. */
    fun setServerName(name: String)

    /** Announces `_nijiyome-link._tcp` over mDNS while running. */
    fun setAnnounce(enabled: Boolean)

    /** Opens (or replaces) the 5-minute single-use pairing window: new code and QR. Starts the server if it is off. */
    fun openPairing()

    fun closePairing()

    fun revoke(deviceId: String)

    fun rename(deviceId: String, name: String)

    fun setHistoryMode(deviceId: String, mode: LinkDeviceHistoryMode)

    /** "Use this device as the source" of the history (it pushes everything, then the others replace theirs). */
    fun useAsHistorySource(deviceId: String)

    /** The folder whose videos `GET /v1/library` lists (one folder, not recursive); blank = none. */
    fun setLibraryFolder(path: String)

    /**
     * The TCP port of the Link server ([LinkServerPorts.isValid]; another value is ignored). Saved and, when the server runs,
     * applied by restarting it (phones paired before find it again by mDNS or by a new pairing).
     */
    fun setPort(port: Int)

    /**
     * Windows: adds the inbound firewall rule of this app's ports for the Private profile through a UAC prompt (only from this
     * explicit action). The result lands in [LinkServerState.firewall]. Does nothing where [LinkServerState.firewallButton] is false.
     */
    fun allowPrivateNetwork()

    // ---- integrated extension server ----

    fun setExtensionServerEnabled(enabled: Boolean)

    /** A new 6-digit pairing code (5 minutes, single use) with its `nijiyome://extensionserver/connect` link. */
    fun openExtensionPairing()

    /** Closes the pairing window: its code stops working at once. */
    fun closeExtensionPairing()

    fun revokeExtensionDevice(deviceId: String)

    /** The extension server's TCP port ([LinkServerPorts.isValid], not the Link port); restarts it when it runs. */
    fun setExtensionPort(port: Int)

    /**
     * The viewer password phones may sign in with instead of a pairing code ([LinkServerPorts.PASSWORD_MIN]..[LinkServerPorts.PASSWORD_MAX]
     * characters); null removes it. Only while the extension server runs. Stored as a hash by the server, never kept here.
     * The outcome lands in [LinkServerState.extPasswordResult].
     */
    fun setExtensionPassword(password: String?)
}

/** Limits shared by the server and Settings (ports above the privileged range; the viewer password's length). */
object LinkServerPorts {
    /** The servers' default ports (`LinkServer.DEFAULT_PORT`, `DEFAULT_EXT_PORT`; a test checks they agree). */
    const val LINK_DEFAULT = 47321
    const val EXT_DEFAULT = 8099
    const val MIN = 1024
    const val MAX = 65535
    const val PASSWORD_MIN = 10
    const val PASSWORD_MAX = 1024

    fun isValid(port: Int): Boolean = port in MIN..MAX

    /** "Restart on another port" (Tatakai Next `alt_port`): 10 above [port], or the default when that leaves the range. */
    fun alternative(port: Int, default: Int): Int = (port + 10).takeIf { port >= MIN && isValid(it) } ?: default

    /** Counted in characters (code points), as the server counts them. */
    fun passwordOk(password: String): Boolean = password.codePointCount(0, password.length) in PASSWORD_MIN..PASSWORD_MAX
}

/** Outcome of [LinkServerController.allowPrivateNetwork]. */
enum class FirewallResult { RUNNING, ADDED, CANCELLED, FAILED }

/** Outcome of [LinkServerController.setExtensionPassword]. */
enum class ExtPasswordResult { SAVED, CLEARED, INVALID, FAILED }

enum class LinkDeviceHistoryMode { FULL, RECEIVE_ONLY, PAUSED }

/** A square QR code: [size] x [size] modules, row by row, true = dark. */
class QrMatrix(val size: Int, val cells: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * size + x]
}

data class LinkServerState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val starting: Boolean = false,
    /** Why the last start failed: `port_in_use` | `no_address` | `identity` | `other`; null = no error. */
    val error: String? = null,
    val serverName: String = "",
    val defaultServerName: String = "",
    val port: Int = 0,
    /** `https://<lan ip>:<port>` addresses phones reach. */
    val addresses: List<String> = emptyList(),
    /** Display form of the CA fingerprint (`7A3F 91C2 0E5D B8A4`), the pin the phone confirms. */
    val fingerprint: String? = null,
    val announce: Boolean = true,
    val pairing: LinkServerPairing? = null,
    val devices: List<LinkServerDevice> = emptyList(),
    /** What processing this PC offers: GPU, encoder and how many enhancement profiles. */
    val gpu: String? = null,
    val encoder: String? = null,
    val profiles: Int = 0,
    /** FFmpeg was found (without it every session fails). */
    val ffmpeg: Boolean = false,
    val libraryFolder: String = "",
    // ---- integrated extension server ----
    val extEnabled: Boolean = false,
    val extRunning: Boolean = false,
    val extError: String? = null,
    val extUrls: List<String> = emptyList(),
    val extPairing: ExtensionServerPairing? = null,
    val extDevices: List<ExtensionServerDevice> = emptyList(),
    /** The extension server's configured port (the one it listens on while running). */
    val extPort: Int = 0,
    /** A viewer password is set (phones may sign in with it instead of a code). */
    val extPasswordSet: Boolean = false,
    val extPasswordResult: ExtPasswordResult? = null,
    /** The extension server's latest requests and events, newest first (at most [EXT_LOG_LIMIT]). */
    val extLogs: List<ExtensionServerLogEntry> = emptyList(),
    // ---- firewall ----
    /** True on Windows: Settings offers "Permitir en red privada"; elsewhere it shows a hint with the ports. */
    val firewallButton: Boolean = false,
    val firewall: FirewallResult? = null,
) {
    companion object {
        const val EXT_LOG_LIMIT = 100
    }
}

/**
 * One line of the extension server's log (no tokens, cookies or media URLs): [event] is `request`, `pairing`, `source_failed`...,
 * [route] the API path, [name] a device or source name, [error] an error code.
 */
data class ExtensionServerLogEntry(
    val id: Long,
    val atMs: Long,
    val event: String,
    val route: String? = null,
    val name: String? = null,
    val count: Int? = null,
    val durationMs: Long? = null,
    val error: String? = null,
)

/** The open pairing window: the code to type, the `nijiyome-link:v1` text to paste and its QR. */
data class LinkServerPairing(val code: String, val uri: String, val expiresAtMs: Long, val qr: QrMatrix?)

data class LinkServerDevice(
    val id: String,
    val name: String,
    val platform: String,
    val lastSeenMs: Long?,
    val online: Boolean,
    val historyMode: LinkDeviceHistoryMode,
    val resyncing: Boolean,
    /** History rows this device contributed to the PC. */
    val contributed: Int,
)

data class ExtensionServerPairing(val code: String, val link: String, val expiresAtMs: Long, val qr: QrMatrix?)

data class ExtensionServerDevice(val id: String, val label: String, val lastSeenMs: Long?)

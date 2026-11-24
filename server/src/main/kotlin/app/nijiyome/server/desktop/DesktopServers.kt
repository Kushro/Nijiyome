// SPDX-License-Identifier: AGPL-3.0-or-later
// The desktop app's servers, after Tatakai Next tk-app/src/live/link.rs (LinkService) and live/ext_server.rs (AGPL-3.0, same author).
package app.nijiyome.server.desktop

import app.nijiyome.model.AppLog
import app.nijiyome.model.ExtPasswordResult
import app.nijiyome.model.ExtensionServerDevice
import app.nijiyome.model.ExtensionServerLogEntry
import app.nijiyome.model.FirewallResult
import app.nijiyome.model.ExtensionServerPairing
import app.nijiyome.model.LinkDeviceHistoryMode
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerDevice
import app.nijiyome.model.LinkServerPairing
import app.nijiyome.model.LinkServerPorts
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.LinkSource
import app.nijiyome.model.PhoneTarget
import app.nijiyome.model.QrMatrix
import app.nijiyome.model.SendToPhone
import app.nijiyome.model.SendToPhoneRequest
import app.nijiyome.server.link.KeySealer
import app.nijiyome.server.link.ApiException
import app.nijiyome.server.link.CreateRequest
import app.nijiyome.server.link.ErrorCode
import app.nijiyome.server.link.PhoneEvent
import app.nijiyome.server.link.SourceRequest
import app.nijiyome.server.ext.ExtServer
import app.nijiyome.server.ext.ExtServerConfig
import app.nijiyome.server.ext.ExtensionCatalog
import app.nijiyome.server.link.Announcement
import app.nijiyome.server.link.Announcer
import app.nijiyome.server.link.Firewall
import app.nijiyome.server.link.HistoryMode
import app.nijiyome.server.link.JmdnsAnnouncer
import app.nijiyome.server.link.LinkConfig
import app.nijiyome.server.link.LinkHost
import app.nijiyome.server.link.LinkServer
import app.nijiyome.server.link.LinkStartException
import app.nijiyome.server.link.LsfgSetup
import app.nijiyome.server.link.WindowStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress

/** What Settings › Nijiyome Link stores for the servers (`<data>/link/server-settings.json`; Android has none of it). */
@Serializable
data class ServerSettings(
    /** Off until the user turns it on: no LAN port is opened on a fresh install. */
    val linkEnabled: Boolean = false,
    val serverName: String = "",
    val announce: Boolean = true,
    val port: Int = LinkConfig.DEFAULT_PORT,
    val libraryFolder: String = "",
    val allowHttpSources: Boolean = false,
    val extEnabled: Boolean = false,
    val extPort: Int = app.nijiyome.server.ext.DEFAULT_EXT_PORT,
    val extRequirePairing: Boolean = true,
    val extProxy: Boolean = true,
    /** Addresses both servers listen on; empty = the private LAN IPv4 (+ 127.0.0.1 for the extension server). Not in the UI. */
    val bindIps: List<String> = emptyList(),
) {
    companion object {
        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun load(file: File): ServerSettings = runCatching { JSON.decodeFromString(serializer(), file.readText()) }.getOrDefault(ServerSettings())

        fun save(file: File, s: ServerSettings) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(JSON.encodeToString(serializer(), s))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}

/**
 * The Link server and the integrated extension server of the desktop app, started and stopped with it according to
 * [ServerSettings], and the state Settings › Nijiyome Link shows. Every server operation runs off the caller's thread.
 */
class DesktopServers(
    private val dataDir: File,
    private val linkHost: (DesktopServers) -> LinkHost,
    private val catalog: ExtensionCatalog?,
    /** Folder with the 14 Anime4K `.glsl` passes (extracted by the app); null = no Anime4K profiles. */
    private val anime4kDir: () -> File? = { null },
    private val announcer: Announcer = JmdnsAnnouncer,
    private val bindIps: List<String> = emptyList(),
    private val historyOrigins: () -> Map<String, Int> = { emptyMap() },
    private val keySealer: KeySealer = KeySealer.Plain,
    /**
     * The `Lossless.dll` of the LSFG profiles: the one chosen in Settings › Reproductor (Tatakai Next `link_lsfg_dll`), read each
     * time the Link server starts; null or blank = search Steam.
     */
    private val lsfgDll: () -> String? = { null },
    /** The app version phones see in the capabilities (`server_version`); the packaged version, not a fixed string. */
    private val appVersion: () -> String? = { null },
    /** Whether "Permitir en red privada" exists here (Windows). */
    private val firewallButton: Boolean = isWindows(),
    /** Adds the Windows Firewall rule of these ports (UAC prompt); tests replace it. */
    private val firewall: (List<Int>) -> Firewall.Outcome = { ports ->
        ownExecutable()?.let { Firewall.allowPrivateNetwork(it, ports) } ?: Firewall.Outcome.FAILED
    },
) : LinkServerController, SendToPhone, AutoCloseable {
    private val linkDir = File(dataDir, "link")
    private val settingsFile = File(linkDir, "server-settings.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    @Volatile private var settings = ServerSettings.load(settingsFile)
    @Volatile private var link: LinkServer? = null

    /** The running Link server (tests). */
    internal val linkServer: LinkServer? get() = link
    @Volatile private var ext: ExtServer? = null
    @Volatile private var announcement: Announcement? = null
    @Volatile private var pairing: LinkServerPairing? = null
    @Volatile private var extPairing: ExtensionServerPairing? = null
    @Volatile private var ffmpegFound = false
    @Volatile private var startedLsfgDll: String? = null
    @Volatile private var extPasswordResult: ExtPasswordResult? = null
    @Volatile private var firewallResult: FirewallResult? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(
        LinkServerState(
            enabled = settings.linkEnabled, extEnabled = settings.extEnabled, defaultServerName = defaultName(),
            port = settings.port, extPort = settings.extPort, firewallButton = firewallButton,
        ),
    )
    override val state: StateFlow<LinkServerState> = _state.asStateFlow()

    private val _phones = MutableStateFlow<List<PhoneTarget>>(emptyList())
    override val phones: StateFlow<List<PhoneTarget>> = _phones.asStateFlow()

    /** Starts what the settings turn on (in the background) and the periodic refresh of the state. */
    fun start() {
        scope.launch {
            lock.withLock {
                if (settings.extEnabled) startExtLocked()
                if (settings.linkEnabled) startLinkLocked()
            }
            publish()
        }
        ticker = scope.launch {
            while (true) {
                delay(2_000)
                val currentDll = runCatching { lsfgDll() }.getOrNull()?.trim()?.ifBlank { null }
                val running = link
                if (running != null && settings.linkEnabled && currentDll != startedLsfgDll && running.activeSessions() == 0) {
                    lock.withLock {
                        if (link != null && settings.linkEnabled && link?.activeSessions() == 0) {
                            stopLinkLocked()
                            startLinkLocked()
                        }
                    }
                }
                publish()
            }
        }
    }

    /** The extension server's media proxy endpoints a Link `url` source may read (BUG-023). */
    fun ownMediaOrigins(): List<InetSocketAddress> = ext?.takeIf { settings.extProxy }?.addresses.orEmpty()

    fun libraryFolder(): File? = settings.libraryFolder.takeIf { it.isNotBlank() }?.let(::File)

    private fun defaultName(): String = "Nijiyome (${ExtServer.hostName()})"

    private fun update(f: (ServerSettings) -> ServerSettings) {
        settings = f(settings)
        runCatching { ServerSettings.save(settingsFile, settings) }
    }

    private fun launchLocked(block: suspend () -> Unit) {
        scope.launch {
            lock.withLock { block() }
            publish()
        }
    }

    // ---- Link -----------------------------------------------------------------------------------------------------------

    private fun startLinkLocked() {
        if (link != null) return
        _state.update { it.copy(starting = true, error = null) }
        val host = linkHost(this)
        ffmpegFound = host.ffmpegPath() != null
        val cfg = linkConfig()
        val dllAtStart = runCatching { lsfgDll() }.getOrNull()?.trim()?.ifBlank { null }
        try {
            val s = LinkServer.start(cfg, host)
            link = s
            startedLsfgDll = dllAtStart
            if (settings.announce) announce(s)
            _state.update { it.copy(error = null) }
        } catch (e: LinkStartException) {
            val error = when (e) {
                is LinkStartException.AddrInUse -> "port_in_use"
                is LinkStartException.NoAddress -> "no_address"
                is LinkStartException.Identity -> "identity"
                is LinkStartException.PublicBind -> "no_address"
            }
            AppLog.w(TAG, "Link did not start ($error): ${startFailure(e)}")
            _state.update { it.copy(error = error) }
        } catch (e: Exception) {
            AppLog.w(TAG, "Link did not start (other): ${startFailure(e)}")
            _state.update { it.copy(error = "other") }
        } finally {
            _state.update { it.copy(starting = false) }
        }
    }

    /** What the Link server starts with now: the saved settings and the player's LSFG library. */
    internal fun linkConfig(): LinkConfig = LinkConfig(
        dataDir = linkDir,
        keySealer = keySealer,
        serverName = settings.serverName.trim().ifEmpty { defaultName() },
        port = settings.port,
        bindIps = settings.bindIps.ifEmpty { bindIps },
        anime4kDir = runCatching { anime4kDir() }.getOrNull(),
        allowHttpSources = settings.allowHttpSources,
        lsfg = LsfgSetup.Detect(runCatching { lsfgDll() }.getOrNull()?.trim()?.ifBlank { null }),
        serverVersion = runCatching { appVersion() }.getOrNull()?.trim()?.removePrefix("v")?.ifBlank { null } ?: LinkConfig.UNKNOWN_VERSION,
    )

    override fun regenerateIdentity() = launchLocked {
        stopLinkLocked()
        val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val moved = moveIdentityAside(linkDir, stamp)
        AppLog.w(TAG, "Link identity regenerated; ${moved.size} files moved to link/$IDENTITY_BACKUP_PREFIX$stamp, phones must pair again")
        _state.update { it.copy(error = null) }
        if (settings.linkEnabled) startLinkLocked()
    }

    private fun stopLinkLocked() {
        announcement?.let { runCatching { it.close() } }
        announcement = null
        link?.stop()
        link = null
        pairing = null
    }

    private fun announce(s: LinkServer) {
        announcement?.let { runCatching { it.close() } }
        announcement = runCatching {
            announcer.announce(s.localAddresses.map { it.address }, s.serverName, s.instanceId, s.port, s.caFingerprint)
        }.getOrNull()
    }

    override fun setEnabled(enabled: Boolean) {
        update { it.copy(linkEnabled = enabled) }
        _state.update { it.copy(enabled = enabled) }
        launchLocked { if (enabled) startLinkLocked() else stopLinkLocked() }
    }

    override fun setServerName(name: String) {
        update { it.copy(serverName = name.trim().take(64)) }
        launchLocked {
            if (link != null) {
                stopLinkLocked()
                startLinkLocked()
            }
        }
    }

    override fun setAnnounce(enabled: Boolean) {
        update { it.copy(announce = enabled) }
        launchLocked {
            val s = link ?: return@launchLocked
            if (enabled) announce(s) else {
                announcement?.let { runCatching { it.close() } }
                announcement = null
            }
        }
    }

    override fun openPairing() {
        launchLocked {
            if (link == null) {
                update { it.copy(linkEnabled = true) }
                _state.update { it.copy(enabled = true) }
                startLinkLocked()
            }
            val s = link ?: return@launchLocked
            val p = s.openPairingWindow()
            pairing = LinkServerPairing(p.code, p.uri(), p.expiresAt.toEpochMilli(), qrOf(p.uri()))
        }
    }

    override fun closePairing() {
        launchLocked {
            link?.closePairingWindow()
            pairing = null
        }
    }

    override fun revoke(deviceId: String) = launchLocked { link?.revoke(deviceId) }

    override fun rename(deviceId: String, name: String) = launchLocked { link?.renameDevice(deviceId, name) }

    override fun setHistoryMode(deviceId: String, mode: LinkDeviceHistoryMode) = launchLocked {
        link?.setHistoryMode(deviceId, HistoryMode.valueOf(mode.name))
    }

    override fun useAsHistorySource(deviceId: String) = launchLocked { link?.startResyncFrom(deviceId) }

    override fun setLibraryFolder(path: String) {
        update { it.copy(libraryFolder = path.trim()) }
        scope.launch { publish() }
    }

    override fun setPort(port: Int) {
        if (!LinkServerPorts.isValid(port) || port == settings.extPort) return
        update { it.copy(port = port) }
        // not running until it is back on the new port (no stale "listening on" line meanwhile)
        _state.update { it.copy(port = port, error = null, running = false, addresses = emptyList(), fingerprint = null) }
        launchLocked {
            stopLinkLocked()
            if (settings.linkEnabled) startLinkLocked()
        }
    }

    override fun allowPrivateNetwork() {
        if (!firewallButton || firewallResult == FirewallResult.RUNNING) return
        firewallResult = FirewallResult.RUNNING
        _state.update { it.copy(firewall = FirewallResult.RUNNING) }
        scope.launch {
            // the ports this app listens on (both servers, even when one is off: turning it on later needs no new prompt)
            val ports = listOf(settings.port, settings.extPort).filter(LinkServerPorts::isValid)
            val outcome = if (ports.isEmpty()) Firewall.Outcome.FAILED else runCatching { firewall(ports) }.getOrDefault(Firewall.Outcome.FAILED)
            firewallResult = when (outcome) {
                Firewall.Outcome.ADDED -> FirewallResult.ADDED
                Firewall.Outcome.CANCELLED -> FirewallResult.CANCELLED
                Firewall.Outcome.FAILED -> FirewallResult.FAILED
            }
            publish()
        }
    }

    // ---- extension server -----------------------------------------------------------------------------------------------

    private fun startExtLocked() {
        if (ext != null || catalog == null) return
        try {
            ext = ExtServer.start(
                ExtServerConfig(
                    dir = File(dataDir, "server"),
                    port = settings.extPort,
                    name = settings.serverName.trim().ifEmpty { defaultName() },
                    requirePairing = settings.extRequirePairing,
                    proxyEnabled = settings.extProxy,
                    bindIps = settings.bindIps.ifEmpty { bindIps },
                ),
                catalog,
            )
            _state.update { it.copy(extError = null) }
        } catch (e: Exception) {
            val code = when {
                e is app.nijiyome.server.ext.ExtStartException && e.publicBind -> "no_address"
                e is app.nijiyome.server.ext.ExtStartException -> "port_in_use"
                else -> "other"
            }
            AppLog.w("ext-server", "extension server did not start ($code): ${e::class.simpleName}")
            _state.update { it.copy(extError = code) }
        }
    }

    private fun stopExtLocked() {
        ext?.stop()
        ext = null
        extPairing = null
        extPasswordResult = null
    }

    override fun setExtensionServerEnabled(enabled: Boolean) {
        update { it.copy(extEnabled = enabled) }
        _state.update { it.copy(extEnabled = enabled) }
        launchLocked { if (enabled) startExtLocked() else stopExtLocked() }
    }

    override fun setExtensionPort(port: Int) {
        if (!LinkServerPorts.isValid(port) || port == settings.port) return
        update { it.copy(extPort = port) }
        _state.update { it.copy(extPort = port, extError = null, extRunning = false, extUrls = emptyList(), extPairing = null) }
        launchLocked {
            stopExtLocked()
            if (settings.extEnabled) startExtLocked()
        }
    }

    override fun openExtensionPairing() = launchLocked {
        val s = ext ?: return@launchLocked
        // one window at a time: the code it replaces stops working
        extPairing?.let { s.cancelPairing(it.code) }
        val origin = s.lanUrls().firstOrNull() ?: "http://127.0.0.1:${s.port}"
        val p = runCatching { s.createPairing(origin) }.getOrNull() ?: return@launchLocked
        extPairing = ExtensionServerPairing(p.code, p.deepLink, System.currentTimeMillis() + p.expiresIn * 1000L, qrOf(p.deepLink))
    }

    override fun closeExtensionPairing() = launchLocked {
        extPairing?.let { p -> ext?.cancelPairing(p.code) }
        extPairing = null
    }

    override fun setExtensionPassword(password: String?) {
        if (password != null && !LinkServerPorts.passwordOk(password)) {
            extPasswordResult = ExtPasswordResult.INVALID
            _state.update { it.copy(extPasswordResult = ExtPasswordResult.INVALID) }
            return
        }
        launchLocked {
            val s = ext
            extPasswordResult = when {
                s == null -> ExtPasswordResult.FAILED
                runCatching { s.setViewerPassword(password) }.isFailure -> ExtPasswordResult.FAILED
                else -> {
                    // the server drops every live code when the password changes: so goes the window that showed one
                    extPairing = null
                    if (password == null) ExtPasswordResult.CLEARED else ExtPasswordResult.SAVED
                }
            }
        }
    }

    override fun revokeExtensionDevice(deviceId: String) = launchLocked { runCatching { ext?.revoke(deviceId) } }

    // ---- "Enviar a dispositivo" (tk-app live/link.rs `start_and_hand_off`) ----

    override suspend fun send(deviceId: String, request: SendToPhoneRequest): Result<Unit> = withContext(Dispatchers.IO) {
        val s = link ?: return@withContext Result.failure(IllegalStateException("link_off"))
        val source = when (val src = request.source) {
            is LinkSource.Url -> SourceRequest.Url(src.url, java.util.TreeMap(src.headers), null, null)
            is LinkSource.Torrent -> SourceRequest.Torrent(src.uri, src.mediaId, src.episode.toLong())
            is LinkSource.Library -> SourceRequest.Library(src.itemId)
        }
        try {
            val created = s.createSessionFor(deviceId, CreateRequest(source, profile = null, audioTrack = 0, startSecs = request.positionSecs.toDouble()))
            val event = PhoneEvent.Handoff(
                sessionId = created.sessionId,
                mediaId = request.mediaId,
                episode = request.episode,
                positionSecs = request.positionSecs,
                title = request.title.takeIf { it.isNotBlank() },
            )
            if (!s.pushEvent(deviceId, event)) {
                AppLog.w(TAG, "send to device failed: ${ErrorCode.DEVICE_NOT_AUTHORIZED.code}")
                return@withContext Result.failure(IllegalStateException(ErrorCode.DEVICE_NOT_AUTHORIZED.code))
            }
            Result.success(Unit)
        } catch (e: ApiException) {
            AppLog.w(TAG, "send to device failed: ${e.error.code}")
            Result.failure(IllegalStateException(e.error.code))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "send to device failed: ${e::class.simpleName}")
            Result.failure(IllegalStateException(ErrorCode.INTERNAL.code))
        }
    }

    // ---- state ----------------------------------------------------------------------------------------------------------

    private fun publish() {
        val s = link
        val now = System.currentTimeMillis()
        val p = pairing?.takeIf { it.expiresAtMs > now && s?.pairingStatus() is WindowStatus.Open }
        if (p == null) pairing = null
        val caps = runCatching { s?.capabilities() }.getOrNull()
        val online = s?.onlineDeviceIds().orEmpty()
        val counts = runCatching { historyOrigins() }.getOrDefault(emptyMap())
        val nowSecs = now / 1000
        val e = ext
        val ep = extPairing?.takeIf { it.expiresAtMs > now }
        if (ep == null) extPairing = null
        _phones.value = s?.devices()?.filter { it.isActive(nowSecs) }?.map { PhoneTarget(it.id, it.name, it.id in online) }.orEmpty()
        _state.update { cur ->
            cur.copy(
                enabled = settings.linkEnabled,
                running = s != null,
                serverName = settings.serverName,
                defaultServerName = defaultName(),
                port = s?.port ?: settings.port,
                // the LAN addresses phones use; loopback only when it listens nowhere else (a loopback-only test setup)
                addresses = s?.localAddresses?.let { all -> all.filter { !it.address.isLoopbackAddress }.ifEmpty { all } }
                    ?.map { "https://${it.address.hostAddress}:${it.port}" }.orEmpty(),
                fingerprint = s?.caFingerprint?.let(::displayFingerprint),
                announce = settings.announce,
                pairing = p,
                devices = s?.devices()?.filter { it.isActive(nowSecs) }?.map { d ->
                    LinkServerDevice(
                        id = d.id, name = d.name, platform = d.platform, lastSeenMs = d.lastSeenAt?.let { it * 1000 }, online = d.id in online,
                        historyMode = LinkDeviceHistoryMode.valueOf(d.historyMode.name), resyncing = s.resyncRunning(d.id), contributed = counts[d.id] ?: 0,
                    )
                }.orEmpty(),
                gpu = caps?.get("gpu")?.jsonObject?.let { g ->
                    g["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: g["vendor"]?.jsonPrimitive?.content?.takeIf { it != "unknown" }?.uppercase()
                },
                encoder = (caps?.get("encoders") as? JsonArray)?.firstOrNull()?.let { (it as JsonPrimitive).content },
                profiles = (caps?.get("profiles") as? JsonArray)?.size ?: 0,
                ffmpeg = ffmpegFound,
                libraryFolder = settings.libraryFolder,
                extEnabled = settings.extEnabled,
                extRunning = e != null,
                extUrls = e?.lanUrls().orEmpty(),
                extPairing = ep,
                extDevices = e?.devices()?.filter { it.role == "viewer" }?.map { ExtensionServerDevice(it.id, it.label, it.lastSeenAt?.let { t -> t * 1000 }) }.orEmpty(),
                extPort = e?.port ?: settings.extPort,
                extPasswordSet = runCatching { e?.viewerPasswordSet() }.getOrNull() == true,
                extPasswordResult = extPasswordResult,
                extLogs = e?.let(::logsOf).orEmpty(),
                firewallButton = firewallButton,
                firewall = firewallResult,
            )
        }
    }

    /** Stops both servers (the app is closing). */
    override fun close() {
        ticker?.cancel()
        runCatching { announcement?.close() }
        runCatching { link?.stop() }
        runCatching { ext?.stop() }
        link = null
        ext = null
        scope.cancel()
    }

    companion object {
        /** The newest [LinkServerState.EXT_LOG_LIMIT] entries of the extension server's log ring, newest first. */
        internal fun logsOf(s: ExtServer): List<ExtensionServerLogEntry> =
            s.logs(0).first.takeLast(LinkServerState.EXT_LOG_LIMIT).asReversed().map { e ->
                ExtensionServerLogEntry(e.id, e.atUnix * 1000, e.event, e.route, e.name, e.count, e.durationMs, e.error)
            }

        private const val TAG = "link"
        const val IDENTITY_BACKUP_PREFIX = "identity-backup-"

        /**
         * Moves the identity files and `devices.json` of [linkDir] into `identity-backup-<stamp>/` (never deleted); the next start
         * creates a new identity. Returns the names moved.
         */
        internal fun moveIdentityAside(linkDir: File, stamp: String): List<String> {
            val names = app.nijiyome.server.link.Identity.FILES + "devices.json"
            val present = names.filter { File(linkDir, it).exists() }
            if (present.isEmpty()) return emptyList()
            var backup = File(linkDir, IDENTITY_BACKUP_PREFIX + stamp)
            var n = 2
            while (backup.exists()) backup = File(linkDir, "$IDENTITY_BACKUP_PREFIX$stamp-${n++}")
            backup.mkdirs()
            return present.filter { name ->
                val from = File(linkDir, name)
                runCatching { java.nio.file.Files.move(from.toPath(), File(backup, name).toPath()) }.isSuccess.also { ok ->
                    if (!ok) AppLog.w(TAG, "could not move $name to the identity backup")
                }
            }.also { names.forEach { File(linkDir, "$it.tmp").delete() } }
        }

        /** What a start failure says in the log: our own messages, the class of anything else (no secrets, no paths). */
        internal fun startFailure(e: Throwable): String {
            val chain = generateSequence(e) { it.cause }.take(4).toList()
            return chain.joinToString(" <- ") { t ->
                when (t) {
                    is LinkStartException.AddrInUse -> "port ${t.port} in use"
                    is LinkStartException.NoAddress -> "no LAN address"
                    is LinkStartException.PublicBind -> "refused a public address"
                    is LinkStartException.Identity -> "identity"
                    is app.nijiyome.server.link.IdentityException -> "identity: ${t.message}"
                    else -> t.javaClass.simpleName
                }
            }
        }

        fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

        /**
         * The program the firewall rule allows: the jpackage launcher (it hosts the JVM, so it is the process that listens), else
         * this process's executable. Null when neither is known.
         */
        fun ownExecutable(): File? =
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
                ?: ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.isFile }

        /** `7A3F 91C2 0E5D B8A4`: the first 16 hex digits of the CA fingerprint, upper case, in groups of 4. */
        fun displayFingerprint(hex: String): String = hex.filter { it.isLetterOrDigit() }.take(16).uppercase().chunked(4).joinToString(" ")

        fun qrOf(text: String): QrMatrix? = runCatching {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
            val size = m.width
            QrMatrix(size, BooleanArray(size * size) { m.get(it % size, it / size) })
        }.getOrNull()
    }
}

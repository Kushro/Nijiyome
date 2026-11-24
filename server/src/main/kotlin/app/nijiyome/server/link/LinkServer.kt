// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/lib.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.link.PlayerStateDto
import app.nijiyome.link.SessionCreatedDto
import app.nijiyome.model.AppLog
import app.nijiyome.server.http.KtorServer
import app.nijiyome.server.link.media.Anime4k
import app.nijiyome.server.link.media.Anime4kMode
import app.nijiyome.server.link.media.Anime4kVariant
import app.nijiyome.server.link.media.BenchStore
import app.nijiyome.server.link.media.CapabilitiesJson
import app.nijiyome.server.link.media.CommandRunner
import app.nijiyome.server.link.media.Detected
import app.nijiyome.server.link.media.Detection
import app.nijiyome.server.link.media.Engine
import app.nijiyome.server.link.media.Environment
import app.nijiyome.server.link.media.FfmpegEngine
import app.nijiyome.server.link.media.GpuInfo
import app.nijiyome.server.link.media.Lsfg
import app.nijiyome.server.link.media.LsfgStatus
import app.nijiyome.server.link.media.NoEngine
import app.nijiyome.server.link.media.Platform
import app.nijiyome.server.link.media.Vendor
import app.nijiyome.server.link.media.SystemRunner
import app.nijiyome.server.link.media.Vaapi
import app.nijiyome.server.net.NetPolicy
import app.nijiyome.server.net.SafeHttp
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.BindException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID

/** Why the Link server did not start. */
sealed class LinkStartException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PublicBind(val ip: String) : LinkStartException("refusing to bind $ip: only private LAN or loopback addresses are allowed")
    class NoAddress : LinkStartException("no LAN address found to listen on")
    class AddrInUse(val port: Int, cause: Throwable) : LinkStartException("port $port is in use", cause)
    class Identity(cause: Throwable) : LinkStartException("identity: ${cause.message}", cause)
}

/** Whether and how Link looks for LSFG (`temple-lsfg` + `Lossless.dll`). */
sealed interface LsfgSetup {
    /** No LSFG profiles (reported as `lsfg.reason = "disabled"`). */
    data object Off : LsfgSetup
    /** Find the helper and the library, then run `temple-lsfg --probe` once at start. */
    data class Detect(val dll: String?) : LsfgSetup
    /** A precomputed status (tests). */
    data class Status(val status: LsfgStatus) : LsfgSetup
}

class LinkConfig(
    /** `<data>/link` (tests pass a temporary folder). */
    val dataDir: File,
    val serverName: String,
    /** How the identity's private keys are stored at rest (the app's secret store on desktop; plain 0600 in tests). */
    val keySealer: KeySealer = KeySealer.Plain,
    /** 47321 by default; 0 picks an ephemeral port (tests). */
    val port: Int = DEFAULT_PORT,
    /** Addresses to listen on. Empty = the detected LAN IPv4. Never 0.0.0.0, never public addresses. */
    val bindIps: List<String> = emptyList(),
    /** Directory with the Anime4K `.glsl` files; null = no Anime4K profiles. */
    val anime4kDir: File? = null,
    /** `anime4k.lock.json`: when given, the shaders must match it or the Anime4K profiles are withheld. */
    val anime4kLock: File? = null,
    /** "Allow HTTP" for `url` sources (https only by default). */
    val allowHttpSources: Boolean = false,
    /** `client_fg` of the capabilities ("Mixto": the phone generates frames on a `fg: none` stream). */
    val clientFgAllowed: Boolean = true,
    /** Real GPU info from the app; null = guessed from the encoders / render node / LSFG probe. */
    val gpu: GpuInfo? = null,
    val lsfg: LsfgSetup = LsfgSetup.Off,
    val tuning: SessionTuning = SessionTuning(),
    val serverVersion: String = UNKNOWN_VERSION,
    // test hooks (production leaves them null)
    val engine: Engine? = null,
    val runner: CommandRunner? = null,
    val fetchHttp: SafeHttp? = null,
    val platform: Platform = Platform.current(),
    val discoverRenderNode: Boolean = true,
    /** Whether an NVIDIA driver is present; null = detect (`/dev/nvidiactl` on Linux). */
    val nvencDevice: Boolean? = null,
) {
    companion object {
        const val DEFAULT_PORT = 47321

        /** What `server_version` says when the app did not pass its version (tests, a bare server). */
        const val UNKNOWN_VERSION = "0.0.0"
    }
}

/**
 * Nijiyome Link v1, the PC side (contracts/link/openapi.v1.json): own CA + pairing (5-minute single-use window), HTTPS on the
 * private LAN with optional client certificates and a PER REQUEST device check, hot revocation, sessions of FFmpeg (VAAPI or
 * software, Anime4K through libplacebo, LSFG through `temple-lsfg`) served as fMP4 HLS, the source's subtitles, the watch history
 * of the user's devices and the remote control of this PC's player. Ktor + Netty (plan §8, decision 2).
 */
class LinkServer private constructor(
    val identity: Identity,
    val devices: DeviceRegistry,
    val manager: Manager,
    val env: Environment,
    val bench: BenchStore,
    val host: LinkHost,
    val instanceId: String,
    val serverName: String,
    private val clientFgAllowed: Boolean,
    private val serverVersion: String,
    val connections: LinkConnections,
) {
    lateinit var pairing: PairingWindow
        private set
    private lateinit var http: KtorServer
    private lateinit var fetcher: Fetcher
    private lateinit var sink: Sink
    private var ticker: Job? = null
    private val eventListeners = java.util.concurrent.CopyOnWriteArrayList<(LinkEvent) -> Unit>()

    /** Addresses the server listens on. */
    val localAddresses: List<InetSocketAddress> get() = http.addresses
    val port: Int get() = http.port

    /** The loopback helpers (source fetcher, FFmpeg sink): never reachable from the network. */
    internal val helperAddresses: List<InetSocketAddress> get() = fetcher.boundAddresses + sink.boundAddresses
    val caFingerprint: String get() = identity.caFingerprint

    internal fun emit(e: LinkEvent) = eventListeners.forEach { runCatching { it(e) } }

    fun onEvent(listener: (LinkEvent) -> Unit) {
        eventListeners += listener
    }

    fun capabilities(): JsonObject = CapabilitiesJson.build(env, bench, instanceId, serverName, clientFgAllowed, serverVersion)

    /** Opens (or replaces) the pairing window: new code, 5 minutes, single use, 5 attempts. */
    fun openPairingWindow(): PairingPayload = pairing.open()

    fun closePairingWindow() = pairing.close()

    fun pairingStatus(): WindowStatus = pairing.status()

    fun devices(): List<Device> = devices.list()

    /** Revokes a device: refused on its next request, its live connections closed and its sessions torn down. */
    fun revoke(id: String): Boolean {
        if (!devices.revoke(id, now())) return false
        manager.closeForDevice(id)
        emit(LinkEvent.DeviceRevoked(id))
        return true
    }

    fun setHistoryMode(id: String, mode: HistoryMode): Boolean = devices.setHistoryMode(id, mode)

    /** "Use this device as the source" (see [DeviceRegistry.startResync]). */
    fun startResyncFrom(id: String): Boolean = devices.startResync(id, now())

    fun resyncRunning(id: String): Boolean = devices.resyncOpen(id, now())

    fun renameDevice(id: String, name: String): Boolean = devices.rename(id, app.nijiyome.link.LinkCsr.sanitizeDeviceName(name))

    /** Queues [event] for [deviceId] (`false` = unknown or revoked). */
    fun pushEvent(deviceId: String, event: PhoneEvent): Boolean = devices.pushEvent(deviceId, event, now())

    /** "Enviar a dispositivo": a session owned by [deviceId], the same path as the phone's own `POST /v1/sessions`. */
    suspend fun createSessionFor(deviceId: String, req: CreateRequest): SessionCreatedDto {
        if (!devices.isActive(deviceId, now())) api(ErrorCode.DEVICE_NOT_AUTHORIZED)
        return manager.create(deviceId, req)
    }

    fun activeSessions(): Int = manager.activeCount()

    /** Devices connected now: with a live session, or seen within [ONLINE_WINDOW_SECS] (the events long-poll keeps it fresh). */
    fun onlineDeviceIds(): List<String> = onlineIds(manager.deviceIds(), devices.list(), now())

    /** Stops listening, closes every session (FFmpeg dies with them) and the loopback helpers. */
    fun stop() {
        ticker?.cancel()
        runCatching { http.stop() }
        manager.shutdown()
        runCatching { sink.stop() }
        runCatching { fetcher.stop() }
    }

    internal val router by lazy { LinkRouter(this) }

    companion object {
        /** BUG-039: a device seen this recently counts as connected (two long-poll rounds + slack). */
        const val ONLINE_WINDOW_SECS = 2L * EVENTS_MAX_WAIT_SECS + 15

        fun now(): Long = Instant.now().epochSecond

        fun onlineIds(withSession: List<String>, devices: List<Device>, now: Long): List<String> {
            val out = withSession.toMutableList()
            for (d in devices) {
                val t = d.lastSeenAt
                val recent = t != null && now - t <= ONLINE_WINDOW_SECS && t <= now + 5
                if (recent && d.isActive(now) && d.id !in out) out += d.id
            }
            return out
        }

        fun isPrivateOrLoopback(ip: InetAddress): Boolean = ip is Inet4Address && (ip.isSiteLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress)

        /** Primary private LAN IPv4 (UDP "connect" trick: no packet is sent). */
        fun detectLanIps(): List<String> = runCatching {
            DatagramSocket().use { s ->
                s.connect(InetSocketAddress("192.0.2.1", 9))
                val a = s.localAddress
                if (a is Inet4Address && (a.isSiteLocalAddress || a.isLinkLocalAddress) && !a.isLoopbackAddress) listOf(a.hostAddress) else emptyList()
            }
        }.getOrDefault(emptyList())

        /** Starts the server. Nothing keeps running when it throws. */
        fun start(cfg: LinkConfig, host: LinkHost): LinkServer {
            val bindIps = cfg.bindIps.ifEmpty { detectLanIps() }
            if (bindIps.isEmpty()) throw LinkStartException.NoAddress()
            val addrs = bindIps.map { InetAddress.getByName(it) }
            addrs.firstOrNull { !isPrivateOrLoopback(it) }?.let { throw LinkStartException.PublicBind(it.hostAddress) }

            cfg.dataDir.mkdirs()
            val identity = try {
                Identity.loadOrInit(cfg.dataDir, addrs, sealer = cfg.keySealer)
            } catch (e: Exception) {
                throw LinkStartException.Identity(e)
            }
            val devices = DeviceRegistry.open(File(cfg.dataDir, "devices.json"))
            val ssl = linkSslContext(identity)

            // environment: encoders / filters / GPU / shaders / render node / LSFG
            val ffmpeg = host.ffmpegPath()
            val runner = cfg.runner ?: SystemRunner
            val detected = if (ffmpeg != null) runBlocking { Detection.detect(cfg.platform, ffmpeg, runner) } else Detected()
            val shadersOk = cfg.anime4kDir?.let { dir ->
                Anime4k.shadersPresent(dir) && (cfg.anime4kLock?.let { lock -> runCatching { Anime4k.verifyAgainstLock(dir, lock.readText()) == null }.getOrDefault(false) } ?: true)
            } ?: false
            val renderNode = if (cfg.discoverRenderNode) Vaapi.discoverSystem() else null
            val lsfg = when (val l = cfg.lsfg) {
                LsfgSetup.Off -> LsfgStatus.unavailable(Lsfg.REASON_DISABLED)
                is LsfgSetup.Status -> l.status
                is LsfgSetup.Detect -> if (ffmpeg == null) LsfgStatus.unavailable(Lsfg.REASON_ENGINE) else Lsfg.detectSystem(l.dll)
            }
            var gpu = cfg.gpu ?: GpuInfo.guessWith(detected, renderNode)
            if (cfg.gpu == null) {
                val vulkanOk = if (detected.libplacebo && cfg.runner == null && ffmpeg != null) {
                    probeLibplaceboVulkan(ffmpeg).also { ok ->
                        if (!ok) AppLog.w("link", "libplacebo/Vulkan probe failed; Anime4K profiles disabled")
                    }
                } else {
                    detected.libplacebo
                }
                gpu = gpu.copy(vulkan = vulkanOk)
                if (gpu.name.isEmpty()) lsfg.device?.let { gpu = gpu.copy(name = it) }
            }
            val largeOk = shadersOk && cfg.anime4kDir?.let { Anime4k.chainPresent(it, Anime4kMode.A, Anime4kVariant.L) } == true
            val nvenc = cfg.nvencDevice ?: (cfg.platform != Platform.LINUX || File("/dev/nvidiactl").exists() || renderNode?.vendor == Vendor.NVIDIA)
            val env = Environment(detected, gpu, shadersOk, cfg.platform, renderNode, lsfg, largeOk, nvenc)
            if (cfg.runner == null) {
                val availProfiles = app.nijiyome.server.link.media.Profiles.CATALOG.count(env::profileAvailable)
                AppLog.i(
                    "link",
                    "capabilities: encoders=${env.usableEncoders().joinToString(",") { it.wire }.ifEmpty { "none" }} " +
                        "libplacebo=${detected.libplacebo} vulkan=${gpu.vulkan} shaders=$shadersOk large=$largeOk " +
                        "lsfg=${lsfg.isAvailable}${lsfg.reason?.let { "($it)" } ?: ""} profiles=$availProfiles",
                )
            }
            val engine = cfg.engine ?: ffmpeg?.let { FfmpegEngine(it) } ?: NoEngine

            val fetcher = Fetcher.start(cfg.fetchHttp ?: SafeHttp(NetPolicy.linkSource(cfg.allowHttpSources)))
            val workRoot = File(cfg.dataDir, "sessions")
            // leftovers of a previous run: a fixed subdirectory of the link dir
            workRoot.deleteRecursively()
            workRoot.mkdirs()
            val bench = BenchStore(File(cfg.dataDir, "bench.json"))
            var server: LinkServer? = null
            val manager = Manager(
                cfg.tuning, engine, fetcher, env, if (shadersOk) cfg.anime4kDir else null, workRoot, cfg.allowHttpSources, host,
                events = { e -> server?.emit(e) },
                deviceActive = { id -> devices.isActive(id, java.time.Instant.now().epochSecond) },
            )
            manager.bench = bench
            val sink = try {
                Sink.start(manager)
            } catch (e: Exception) {
                fetcher.stop()
                throw e
            }
            val pairHost = bindIps.firstOrNull { !InetAddress.getByName(it).isLoopbackAddress } ?: "127.0.0.1"
            val connections = LinkConnections(devices)
            val s = LinkServer(
                identity, devices, manager, env, bench, host, UUID.randomUUID().toString(), cfg.serverName,
                cfg.clientFgAllowed, cfg.serverVersion, connections,
            )
            server = s
            s.fetcher = fetcher
            s.sink = sink
            try {
                s.http = KtorServer.start(bindIps, cfg.port, writeTimeoutSecs = 60, threads = 8, pipeline = { connections.install(this, ssl) }) { s.router.handle(it) }
            } catch (e: BindException) {
                sink.stop()
                fetcher.stop()
                manager.shutdown()
                throw LinkStartException.AddrInUse(cfg.port, e)
            }
            // the pairing payload carries the bound port (an ephemeral one in tests)
            s.pairing = PairingWindow(identity, devices, pairHost, s.http.port, cfg.serverName)
            devices.onRevoked { id -> manager.closeForDevice(id) }
            s.ticker = manager.startTicker()
            return s
        }

        /** Lightweight 1-frame check that FFmpeg's `libplacebo` can actually initialize Vulkan on this machine. */
        private fun probeLibplaceboVulkan(ffmpeg: File): Boolean = runCatching {
            val pb = ProcessBuilder(
                ffmpeg.path, "-hide_banner", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=c=black:s=64x64:d=0.1",
                "-frames:v", "1", "-vf", "libplacebo=w=64:h=64:format=nv12",
                "-f", "null", "-",
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.environment().putAll(Lsfg.VULKAN_EXTRA_ENV)
            val p = pb.start()
            runCatching { p.outputStream.close() }
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.descendants().forEach { it.destroyForcibly() }
                p.destroyForcibly()
                false
            } else {
                p.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}

/** The PC's player state for `GET /v1/player` (re-export of the phone's DTO). */
typealias PlayerState = PlayerStateDto

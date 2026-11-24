// SPDX-License-Identifier: AGPL-3.0-or-later
// Settings › Red of the desktop app, after Tatakai Next's server › network settings (tk-ui settings/server.rs; AGPL-3.0, same author).
package app.nijiyome.server.desktop

import app.nijiyome.model.NetProxyKind
import app.nijiyome.model.NetTestResult
import app.nijiyome.model.NetworkSettingsController
import app.nijiyome.model.NetworkSettingsState
import app.nijiyome.model.SecretStore
import app.nijiyome.network.FlareSolverr
import app.nijiyome.network.FlareSolverrInterceptor
import app.nijiyome.network.Outbound
import app.nijiyome.network.ProbeResult
import app.nijiyome.network.ProxyEndpoint
import app.nijiyome.network.ProxyKind
import app.nijiyome.network.ProxyProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** `<data>/network.json`: what Settings › Red keeps besides the proxy credentials (those live in the secret store). */
@Serializable
data class NetworkFile(
    val proxyEnabled: Boolean = false,
    val proxyKind: String = "SOCKS5",
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    /** Whether the secret store holds credentials (so the proxy is known to need them before the store answers). */
    val proxyAuth: Boolean = false,
    val flareSolverrUrl: String = "",
    val localMediaProxy: Boolean = true,
) {
    val kind: ProxyKind get() = runCatching { ProxyKind.valueOf(proxyKind) }.getOrDefault(ProxyKind.SOCKS5)

    /** The proxy without credentials (null = off or incomplete). */
    fun endpoint(username: String? = null, password: String? = null): ProxyEndpoint? =
        if (proxyEnabled && ProxyEndpoint.valid(proxyHost, proxyPort)) ProxyEndpoint(kind, proxyHost.trim(), proxyPort, username, password) else null

    companion object {
        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fileIn(dataDir: File) = File(dataDir, "network.json")

        fun load(file: File): NetworkFile = runCatching { JSON.decodeFromString(serializer(), file.readText()) }.getOrDefault(NetworkFile())

        fun save(file: File, s: NetworkFile) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(JSON.encodeToString(serializer(), s))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }

        /**
         * At start, before any client exists: installs the outbound route and applies `network.json` (the proxy without its
         * credentials until the secret store answers: a proxy that needs them refuses meanwhile, nothing goes out directly).
         */
        fun applyAtStart(dataDir: File) {
            Outbound.install()
            val f = load(fileIn(dataDir))
            Outbound.proxy = f.endpoint()
            Outbound.flareSolverr = FlareSolverr.normalize(f.flareSolverrUrl)
        }
    }
}

/** Settings › Red: the outbound proxy (HTTP / SOCKS5), FlareSolverr and the loopback media proxy of the player. */
class DesktopNetwork(
    dataDir: File,
    private val secrets: SecretStore?,
    /** The proxy test's target (tests use a local server). */
    private val probeTarget: String = ProxyProbe.DEFAULT_TARGET,
) : NetworkSettingsController, AutoCloseable {
    private val file = NetworkFile.fileIn(dataDir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var current: NetworkFile = NetworkFile.load(file)
        private set

    @Volatile
    private var username: String? = null

    @Volatile
    private var password: String? = null

    private val _state = MutableStateFlow(stateOf(current))
    override val state: StateFlow<NetworkSettingsState> = _state.asStateFlow()

    /** Called after [apply]; the torrent engine re-reads the proxy. */
    @Volatile
    var onChange: () -> Unit = {}

    /** Bumped by every save: credentials read at start never overwrite ones saved meanwhile. */
    @Volatile
    private var edits = 0

    init {
        apply()
        scope.launch {
            val seen = edits
            val u = runCatching { secrets?.get(KEY_USER) }.getOrNull()
            val p = runCatching { secrets?.get(KEY_PASS) }.getOrNull()
            if (edits == seen) {
                username = u
                password = p
                apply()
            }
        }
    }

    /** The proxy as the app uses it now (with credentials). */
    fun endpoint(): ProxyEndpoint? = current.endpoint(username?.takeIf { it.isNotEmpty() }, password)

    private fun apply() {
        val f = current
        Outbound.proxy = endpoint()
        val fs = FlareSolverr.normalize(f.flareSolverrUrl)
        if (fs != Outbound.flareSolverr) FlareSolverrInterceptor.shared.clear()
        Outbound.flareSolverr = fs
        _state.value = stateOf(f)
        runCatching { onChange() }
    }

    private fun stateOf(f: NetworkFile) = NetworkSettingsState(
        proxyEnabled = f.proxyEnabled,
        proxyKind = if (f.kind == ProxyKind.HTTP) NetProxyKind.HTTP else NetProxyKind.SOCKS5,
        proxyHost = f.proxyHost,
        proxyPort = f.proxyPort,
        proxyUsername = username.orEmpty(),
        hasPassword = !password.isNullOrEmpty(),
        flareSolverrUrl = f.flareSolverrUrl,
        localMediaProxy = f.localMediaProxy,
    )

    private fun save(f: NetworkFile) {
        edits++
        current = f
        runCatching { NetworkFile.save(file, f) }
    }

    override suspend fun saveProxy(enabled: Boolean, kind: NetProxyKind, host: String, port: Int, username: String, password: String?): Boolean {
        val h = host.trim()
        if (enabled && !ProxyEndpoint.valid(h, port)) return false
        edits++
        val user = username.trim()
        val pass = if (user.isEmpty()) "" else password ?: this.password.orEmpty()
        withContext(Dispatchers.IO) {
            runCatching {
                secrets?.put(KEY_USER, user.ifEmpty { null })
                secrets?.put(KEY_PASS, pass.ifEmpty { null })
            }
        }
        this.username = user
        this.password = pass
        save(current.copy(proxyEnabled = enabled, proxyKind = toKind(kind).name, proxyHost = h, proxyPort = port, proxyAuth = user.isNotEmpty()))
        apply()
        return true
    }

    override suspend fun testProxy(kind: NetProxyKind, host: String, port: Int, username: String, password: String?): NetTestResult {
        val h = host.trim()
        if (!ProxyEndpoint.valid(h, port)) return NetTestResult.Failed("invalid host or port")
        val user = username.trim().takeIf { it.isNotEmpty() }
        val e = ProxyEndpoint(toKind(kind), h, port, user, if (user == null) null else password ?: this.password)
        return withContext(Dispatchers.IO) { ProxyProbe.test(e, probeTarget) }.toUi()
    }

    override suspend fun saveFlareSolverr(url: String): Boolean {
        val t = url.trim().trimEnd('/')
        if (t.isNotEmpty() && FlareSolverr.normalize(t) == null) return false
        save(current.copy(flareSolverrUrl = t))
        apply()
        return true
    }

    override suspend fun testFlareSolverr(url: String): NetTestResult = withContext(Dispatchers.IO) { FlareSolverr.probe(url) }.toUi()

    override fun setLocalMediaProxy(enabled: Boolean) {
        save(current.copy(localMediaProxy = enabled))
        apply()
    }

    override fun close() {
        scope.cancel()
    }

    private fun ProbeResult.toUi(): NetTestResult = when (this) {
        is ProbeResult.Ok -> NetTestResult.Ok(latencyMs, detail)
        is ProbeResult.Failed -> NetTestResult.Failed(reason)
    }

    companion object {
        const val KEY_USER = "net.proxy.username"
        const val KEY_PASS = "net.proxy.password"

        private fun toKind(k: NetProxyKind) = if (k == NetProxyKind.HTTP) ProxyKind.HTTP else ProxyKind.SOCKS5
    }
}

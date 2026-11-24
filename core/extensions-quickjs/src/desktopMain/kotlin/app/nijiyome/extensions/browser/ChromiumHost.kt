// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import app.nijiyome.network.AddressRules
import app.nijiyome.network.Outbound
import app.nijiyome.network.ProxyEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The desktop's hidden browser process (1.1, F4): the user's Chrome / Edge / Chromium started headless and driven over the Chrome
 * DevTools Protocol. One process serves every hidden page; each [CdpPage] lives in its own browser context (separate cookies, storage
 * and cache, thrown away on close).
 *
 * * Started on first use, stopped [idleMs] after the last page closes, and killed with the app (shutdown hook).
 * * Its profile is a fresh `0700` temporary folder, deleted when the process ends: the user's own profile is never touched (and an
 *   open window of the same browser is another instance).
 * * The debugging connection is a pipe, never a port: Linux, two `0600` FIFOs in that folder; Windows, two anonymous pipes
 *   ([WindowsPipes]). Only if the Windows pipe cannot be set up it falls back to `--remote-debugging-port=0` on 127.0.0.1 (logged: any
 *   local process could then reach that port while the browser runs, at most [idleMs] after its last page).
 * * All its traffic goes through a [BrowserGateway] (SOCKS5 on 127.0.0.1): local / private destinations are refused there, at the
 *   socket, with SafeHttp's address rules, and the outbound proxy of Settings › Red is used from there (any kind, with its password).
 *   A change of proxy or browser restarts the process at the next page.
 *
 * [destinationAllowed] / [refuseLocalNames] are the gateway's rules; tests loosen them to reach a local test page.
 */
class ChromiumHost(
    private val executable: () -> ChromiumExecutable?,
    private val proxy: () -> ProxyEndpoint? = { Outbound.proxy },
    private val idleMs: Long = DEFAULT_IDLE_MS,
    private val transport: Transport = Transport.PIPE,
    private val log: (String) -> Unit = { System.err.println("W/HiddenBrowser: $it") },
    private val destinationAllowed: (InetAddress, Int) -> Boolean = { ip, _ -> !AddressRules.isBlocked(ip) },
    private val refuseLocalNames: Boolean = true,
) {
    /** PIPE: FIFOs (Linux) / anonymous pipes (Windows, port as fallback). PORT: 127.0.0.1 port (tests of that fallback). */
    enum class Transport { PIPE, PORT }

    /** The network rules of one launch. */
    internal class Rules(val upstream: ProxyEndpoint?, val allowed: (InetAddress, Int) -> Boolean, val refuseLocalNames: Boolean)

    internal class Running(
        val process: Process,
        val connection: CdpConnection,
        val gateway: BrowserGateway,
        val profile: File,
        val executable: String,
        val proxy: ProxyEndpoint?,
        val product: String,
        val userAgent: String,
        val hook: Thread,
        val transport: Transport,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val leases = PageLeases(scope, mutex, idleMs) { running?.let { stopLocked(it) } }

    @Volatile private var running: Running? = null

    /** Tests: the running process, its profile folder, its gateway and transport, and the pages counted open. */
    internal val currentProcess: Process? get() = running?.process
    internal val currentProfile: File? get() = running?.profile
    internal val currentGateway: BrowserGateway? get() = running?.gateway
    internal val currentTransport: Transport? get() = running?.transport
    internal val openPages: Int get() = leases.count

    /** Whether a page can be opened now (a browser was found). Cheap: nothing is started. */
    val available: Boolean get() = executable() != null

    /** Opens a hidden page in a new browser context, starting the browser when needed. Throws [IOException] when it cannot. */
    internal suspend fun openPage(userAgent: String? = null): CdpPage {
        val (r, lease) = mutex.withLock {
            val exe = executable() ?: throw IOException("no Chrome / Edge / Chromium found")
            val p = proxy()
            val current = running?.takeIf { !it.connection.closed && it.process.isAlive }
            if (current != null && (current.executable != exe.path || current.proxy != p)) {
                log("restarting the hidden browser (browser or proxy changed)")
                stopLocked(current)
            }
            val r = running?.takeIf { !it.connection.closed && it.process.isAlive } ?: launch(exe.path, p).also { running = it }
            r to leases.acquireLocked()
        }
        // the lease is given back exactly once: by the page's close, or here when the page could not be opened
        return try {
            CdpPage.open(r.connection, userAgent ?: r.userAgent) { lease.release() }
        } catch (e: Throwable) {
            lease.release()
            throw e
        }
    }

    /** Stops the browser now (open pages fail with "closed"). */
    suspend fun shutdown() = mutex.withLock { running?.let { stopLocked(it) } }

    private suspend fun stopLocked(r: Running) {
        if (running === r) running = null
        withContext(Dispatchers.IO) { stop(r) }
    }

    private suspend fun launch(exe: String, p: ProxyEndpoint?): Running = withContext(Dispatchers.IO) {
        start(exe, Rules(p, destinationAllowed, refuseLocalNames), transport, log)
    }

    /**
     * Pages counted open and the idle stop (1.1 review #6). The count and the idle job are only touched under [mutex]; each page holds a
     * [Lease] whose [Lease.release] counts once however many paths call it.
     */
    internal class PageLeases(
        private val scope: CoroutineScope,
        private val mutex: Mutex,
        private val idleMs: Long,
        private val onIdle: suspend () -> Unit,
    ) {
        @Volatile var count: Int = 0
            private set
        private var idleJob: Job? = null

        /** A page starts. Caller holds [mutex]. */
        fun acquireLocked(): Lease {
            idleJob?.cancel()
            idleJob = null
            count++
            return Lease()
        }

        inner class Lease {
            private val released = AtomicBoolean(false)

            /** The page is gone; idempotent. Returns the job that applies it (tests join it). */
            fun release(): Job? {
                if (!released.compareAndSet(false, true)) return null
                return scope.launch {
                    mutex.withLock {
                        count--
                        if (count == 0) {
                            idleJob?.cancel()
                            idleJob = scope.launch {
                                delay(idleMs)
                                mutex.withLock { if (count == 0) onIdle() }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_IDLE_MS = 60_000L
        private const val START_TIMEOUT_MS = 20_000L

        /** Starts of the browser are one at a time (the Windows pipe ends must reach this process only). */
        private val LAUNCH_LOCK = Any()

        /** The switches of every launch (besides the transport, the profile and the gateway). */
        val BASE_ARGS: List<String> = listOf(
            "--headless",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-sync",
            "--disable-background-networking",
            "--disable-component-update",
            "--disable-default-apps",
            "--disable-features=Translate,MediaRouter,OptimizationHints",
            "--disable-blink-features=AutomationControlled",
            // no keyring prompt (KWallet / GNOME Keyring / macOS keychain) for a throwaway profile
            "--password-store=basic",
            "--use-mock-keychain",
            "--mute-audio",
            "--no-pings",
            "--window-size=1280,720",
        )

        /** Starts [exe] headless behind its gateway, connects, and reads its version. Throws [IOException]; nothing is left behind. */
        internal fun start(exe: String, rules: Rules, transport: Transport, log: (String) -> Unit): Running {
            if (transport == Transport.PIPE && isWindows()) {
                try {
                    return startWith(exe, rules, Transport.PIPE, log)
                } catch (e: IOException) {
                    log("the Windows debugging pipe failed (${e.message}); using a 127.0.0.1 port instead, reachable by local processes while the browser runs")
                }
                return startWith(exe, rules, Transport.PORT, log)
            }
            return startWith(exe, rules, transport, log)
        }

        private fun startWith(exe: String, rules: Rules, transport: Transport, log: (String) -> Unit): Running {
            val profile = privateTempDir()
            val gateway = BrowserGateway(rules.upstream, rules.allowed, refuseLocalNames = rules.refuseLocalNames, log = { log("gateway: $it") })
            val args = ArrayList<String>()
            args += exe
            args += BASE_ARGS
            args += "--user-data-dir=${profile.path}"
            args += gateway.chromiumArgs()
            var process: Process? = null
            var connection: CdpConnection? = null
            var winPipes: WindowsPipes? = null
            try {
                val stderr = File(profile, "browser.log")
                val pb: ProcessBuilder
                var fifoIn: File? = null
                var fifoOut: File? = null
                when {
                    transport == Transport.PIPE && isWindows() -> {
                        winPipes = WindowsPipes.create()
                        args += "--remote-debugging-pipe"
                        args += winPipes.switch
                        args += "about:blank"
                        pb = ProcessBuilder(args)
                    }
                    transport == Transport.PIPE -> {
                        fifoIn = File(profile, "cdp-in")
                        fifoOut = File(profile, "cdp-out")
                        val mk = ProcessBuilder("mkfifo", "-m", "600", fifoIn.path, fifoOut.path).redirectErrorStream(true).start()
                        if (!mk.waitFor(5, TimeUnit.SECONDS) || mk.exitValue() != 0) throw IOException("mkfifo failed")
                        args += "--remote-debugging-pipe"
                        args += "about:blank"
                        // fd 3 = what we write, fd 4 = what we read (Chromium's pipe convention)
                        pb = ProcessBuilder(listOf("/bin/sh", "-c", "exec \"\$0\" \"\$@\" 3<\"\$NIJ_CDP_IN\" 4>\"\$NIJ_CDP_OUT\"") + args)
                        pb.environment()["NIJ_CDP_IN"] = fifoIn.path
                        pb.environment()["NIJ_CDP_OUT"] = fifoOut.path
                    }
                    else -> {
                        args += "--remote-debugging-port=0"
                        args += "about:blank"
                        pb = ProcessBuilder(args)
                    }
                }
                pb.redirectErrorStream(true).redirectOutput(stderr).redirectInput(ProcessBuilder.Redirect.from(File(if (isWindows()) "NUL" else "/dev/null")))
                process = synchronized(LAUNCH_LOCK) {
                    try {
                        pb.start()
                    } finally {
                        winPipes?.closeBrowserEnds()
                    }
                }
                val t: CdpTransport = when {
                    winPipes != null -> winPipes.transport().also { winPipes = null }
                    transport == Transport.PIPE -> openPipe(process, fifoIn!!, fifoOut!!)
                    else -> openPort(process, profile)
                }
                connection = CdpConnection(t).also { it.start() }
                val version = kotlinx.coroutines.runBlocking { connection.call("Browser.getVersion") }
                val product = version.str("product").orEmpty()
                val ua = version.str("userAgent").orEmpty().replace("HeadlessChrome", "Chrome")
                val proc = process
                val dir = profile
                val hook = Thread({ killAndClean(proc, dir) }, "hidden-browser-exit")
                Runtime.getRuntime().addShutdownHook(hook)
                log("hidden browser started: $product (${if (transport == Transport.PIPE) "pipe" else "127.0.0.1 port"}, gateway 127.0.0.1:${gateway.port}${rules.upstream?.let { ", proxy ${it.uri()}" }.orEmpty()})")
                return Running(process, connection, gateway, profile, exe, rules.upstream, product, ua, hook, transport)
            } catch (e: Throwable) {
                connection?.close()
                winPipes?.closeAll()
                gateway.close()
                val tail = runCatching { File(profile, "browser.log").readLines().takeLast(3).joinToString(" | ") }.getOrDefault("")
                process?.let { killAndClean(it, profile) } ?: profile.deleteRecursively()
                throw IOException("the hidden browser could not start: ${e.message}${if (tail.isNotBlank()) " ($tail)" else ""}", e)
            }
        }

        internal fun stop(r: Running) {
            runCatching { r.connection.fire("Browser.close") }
            if (!r.process.waitFor(3, TimeUnit.SECONDS)) killTree(r.process)
            r.connection.close()
            r.gateway.close()
            runCatching { Runtime.getRuntime().removeShutdownHook(r.hook) }
            deleteProfile(r.profile)
        }

        /**
         * Settings' "Probar": starts [exe] on its own (not the shared process), opens a page, evaluates `1+1` and closes. The product
         * name (`Edg/158.0…`) or the reason it failed.
         */
        suspend fun probe(exe: String, p: ProxyEndpoint? = Outbound.proxy, transport: Transport = Transport.PIPE): Result<String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val rules = Rules(p, { ip, _ -> !AddressRules.isBlocked(ip) }, refuseLocalNames = true)
                    val r = withTimeoutOrNull(START_TIMEOUT_MS + 10_000) { runInterruptible { start(exe, rules, transport) {} } }
                        ?: throw IOException("timed out")
                    try {
                        val page = CdpPage.open(r.connection, r.userAgent) {}
                        try {
                            val two = page.evaluateRaw("1+1")
                            if ((two as? JsonPrimitive)?.content != "2") throw IOException("unexpected answer")
                        } finally {
                            page.close()
                        }
                        r.product
                    } finally {
                        stop(r)
                    }
                }
            }

        private fun openPipe(process: Process, fifoIn: File, fifoOut: File): CdpTransport {
            // Opening a FIFO blocks until the other side opens it: done on a helper thread, unblocked by hand if the browser never does.
            var out: FileOutputStream? = null
            var input: FileInputStream? = null
            val opener = Thread({
                runCatching {
                    out = FileOutputStream(fifoIn)
                    input = FileInputStream(fifoOut)
                }
            }, "cdp-pipe-open")
            opener.isDaemon = true
            opener.start()
            opener.join(START_TIMEOUT_MS)
            if (out == null || input == null || !process.isAlive) {
                // unblock the opener: open the far ends ourselves, then give up
                runCatching { FileInputStream(fifoIn).close() }
                runCatching { FileOutputStream(fifoOut).close() }
                opener.join(1_000)
                runCatching { out?.close() }
                runCatching { input?.close() }
                throw IOException("the browser did not open the debugging pipe")
            }
            return PipeTransport(out, input)
        }

        private fun openPort(process: Process, profile: File): CdpTransport {
            val active = File(profile, "DevToolsActivePort")
            val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive) throw IOException("the browser exited (${process.exitValue()})")
                val lines = runCatching { active.readLines() }.getOrDefault(emptyList())
                if (lines.size >= 2) {
                    val port = lines[0].trim().toIntOrNull() ?: throw IOException("bad DevToolsActivePort")
                    val path = lines[1].trim()
                    if (port !in 1..65535 || !path.startsWith("/devtools/browser/")) throw IOException("bad DevToolsActivePort")
                    return WebSocketTransport("ws://127.0.0.1:$port$path")
                }
                Thread.sleep(25)
            }
            throw IOException("the browser did not open its debugging port")
        }

        private fun privateTempDir(): File {
            val dir = if (isWindows()) {
                Files.createTempDirectory("nijiyome-browser-")
            } else {
                Files.createTempDirectory("nijiyome-browser-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            }
            return dir.toFile()
        }

        private fun killAndClean(process: Process, profile: File) {
            killTree(process)
            deleteProfile(profile)
        }

        private fun killTree(process: Process) {
            runCatching { process.descendants().forEach { it.destroyForcibly() } }
            process.destroyForcibly()
            process.waitFor(3, TimeUnit.SECONDS)
        }

        /** Chromium's helper processes may hold files for a moment after the main one exits (Windows): a few tries. */
        private fun deleteProfile(profile: File) {
            repeat(10) {
                if (profile.deleteRecursively() || !profile.exists()) return
                Thread.sleep(100)
            }
        }

        private fun isWindows() = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

        internal fun params(vararg kv: Pair<String, Any?>): JsonObject = buildJsonObject {
            for ((k, v) in kv) when (v) {
                null -> Unit
                is String -> put(k, v)
                is Boolean -> put(k, v)
                is Number -> put(k, v)
                is kotlinx.serialization.json.JsonElement -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}

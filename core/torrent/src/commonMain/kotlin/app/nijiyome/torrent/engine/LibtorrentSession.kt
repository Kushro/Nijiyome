// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.torrent.engine

import app.nijiyome.model.torrent.TorrentSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.ErrorCode
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.swig.info_hash_t
import org.libtorrent4j.swig.torrent_handle
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.remove_flags_t
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t

/**
 * Thin wrapper over libtorrent4j 2.1.0-39 `SessionManager` (docs/redesign/12 §4 f.1). Every libtorrent call of :core:torrent goes
 * through this class or [TorrentHandle] (APIs checked in .deps-sources/libtorrent4j-2.1.0-39). Blocking: call from Dispatchers.IO.
 *
 * Persistence (filesDir/torrents): `session.dat` (session state: DHT nodes), `<infoHash>.resume` (fastresume incl. the info dict).
 */
internal class LibtorrentSession(
    private val stateDir: File,
    private val onAlert: (Alert<*>) -> Unit,
    /** 1.1: the SOCKS5 proxy of Settings › Red (desktop); Android: always null, and its packs never carry proxy settings. */
    private val proxy: () -> app.nijiyome.torrent.system.TorrentProxy? = { null },
) {
    @Volatile private var proxied = false
    private val manager = SessionManager(false)
    private val pendingResume = AtomicInteger()
    @Volatile private var lastListenPort: Int? = null

    private val listener = object : AlertListener {
        override fun types(): IntArray = LISTENED.map { it.swig() }.toIntArray()

        override fun alert(alert: Alert<*>) {
            if (alert.type() == AlertType.SAVE_RESUME_DATA) {
                writeResume(alert as SaveResumeDataAlert)
                pendingResume.decrementAndGet()
            } else if (alert.type() == AlertType.SAVE_RESUME_DATA_FAILED) {
                pendingResume.decrementAndGet()
            }
            try {
                onAlert(alert)
            } catch (_: Exception) {
                // never let a listener exception kill the alert loop
            }
        }
    }

    val isRunning: Boolean get() = manager.isRunning

    fun start(settings: TorrentSettings, uploadLimitBps: Int, lsdAllowed: Boolean) {
        if (manager.isRunning) return
        stateDir.mkdirs()
        val pack = settingsPack(settings, uploadLimitBps, lsdAllowed, includeListen = true)
        val stateFile = File(stateDir, SESSION_STATE)
        val params = try {
            if (stateFile.isFile) SessionParams(stateFile.readBytes()).also { it.setSettings(pack) } else SessionParams(pack)
        } catch (_: Exception) {
            SessionParams(pack)
        }
        manager.addListener(listener)
        manager.start(params)
    }

    /** Saves resume data of every torrent (waits up to [timeoutMs]), the session state, then stops. */
    fun stop(handles: List<TorrentHandle>, timeoutMs: Long = 4_000) {
        if (!manager.isRunning) return
        handles.forEach { requestResume(it) }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pendingResume.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        try {
            manager.saveState()?.let { bytes -> atomicWrite(File(stateDir, SESSION_STATE), bytes) }
        } catch (_: Exception) {
            // state is a cache (DHT nodes)
        }
        manager.removeListener(listener)
        manager.stop()
        pendingResume.set(0)
        lastListenPort = null
    }

    fun applySettings(settings: TorrentSettings, uploadLimitBps: Int, lsdAllowed: Boolean) {
        if (!manager.isRunning) return
        val includeListen = lastListenPort != settings.listenPort
        manager.applySettings(settingsPack(settings, uploadLimitBps, lsdAllowed, includeListen))
    }

    fun find(id: String): TorrentHandle? = try {
        manager.find(Sha1Hash.parseHex(id))?.takeIf { it.isValid }
    } catch (_: Exception) {
        null
    }

    /** Synchronous add (returns the handle, or the existing one for a duplicate). Throws [IllegalStateException] on error. */
    fun add(params: AddTorrentParams): TorrentHandle {
        val ec = ErrorCode(error_code())
        val handle = SessionHandle(manager.swig()).addTorrent(params, ec)
        if (ec.isError || !handle.isValid) throw IllegalStateException(ec.message ?: "add failed")
        return handle
    }

    fun remove(handle: TorrentHandle, deleteFiles: Boolean) {
        if (!handle.isValid) return
        if (deleteFiles) manager.remove(handle, SessionHandle.DELETE_FILES) else manager.remove(handle, remove_flags_t())
    }

    fun requestResume(handle: TorrentHandle) {
        if (!handle.isValid) return
        try {
            pendingResume.incrementAndGet()
            handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
        } catch (_: Exception) {
            pendingResume.decrementAndGet()
        }
    }

    /**
     * The `.torrent` of a magnet (its info dict only: trackers and web seeds stay on the magnet), fetched from the swarm without
     * downloading any data (added in upload mode, stop-when-ready, saved under [tempDir], and removed again; one already in the
     * session answers from it). Blocking; null = no metadata in [timeoutSecs]. Throws [IllegalArgumentException] for a magnet
     * libtorrent cannot parse.
     *
     * Replaces libtorrent4j's `SessionManager.fetchMagnet`, which never answered for a hybrid (v1 + v2) torrent added by its v1 hash:
     * it recognised its torrent by `torrent_handle::info_hash()`, the truncated v2 hash once the metadata is in, and it wrote the
     * `.torrent` from resume data, which libtorrent refuses for a v2 torrent whose piece layers have not arrived ("a piece layer is
     * invalid"). Here the handle matches on either hash ([sameTorrent]) and the info section is taken as it came from the swarm.
     */
    fun fetchMagnet(magnet: String, timeoutSecs: Int, tempDir: File, onTick: ((elapsedSecs: Int, peers: Int) -> Unit)? = null): ByteArray? {
        if (!manager.isRunning) return null
        tempDir.mkdirs()
        val parseError = error_code()
        val params = libtorrent.parse_magnet_uri(magnet, parseError)
        if (parseError.value() != 0) throw IllegalArgumentException(parseError.message())
        val wanted = params.info_hashes
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        val waiter = object : AlertListener {
            override fun types(): IntArray = intArrayOf(AlertType.METADATA_RECEIVED.swig())

            override fun alert(alert: Alert<*>) {
                val handle = (alert as? TorrentAlert<*>)?.handle()?.swig() ?: return
                if (!handle.is_valid() || !sameTorrent(handle.info_hashes(), wanted)) return
                runCatching { infoOnlyTorrent(handle) }.getOrNull()?.let(result::set)
                done.countDown()
            }
        }
        manager.addListener(waiter)
        var added: torrent_handle? = null
        try {
            val existing = manager.swig().find_torrent(wanted.get_best())?.takeIf { it.is_valid() }
            if (existing != null) {
                // Already in the session: answers now if it has its metadata, else when it arrives (the waiter).
                infoOnlyTorrent(existing)?.let { return it }
            } else {
                params.setName("fetch_magnet___" + wanted.get_best().to_hex())
                params.setSave_path(tempDir.absolutePath)
                params.setFlags(params.flags.and_(TorrentFlags.AUTO_MANAGED.inv()).or_(TorrentFlags.UPLOAD_MODE).or_(TorrentFlags.STOP_WHEN_READY))
                val addError = error_code()
                added = manager.swig().add_torrent(params, addError)
                if (addError.value() != 0) throw IllegalStateException(addError.message())
                added.resume()
            }
            val waiting = added ?: existing
            val startedNs = System.nanoTime()
            while (true) {
                val elapsed = ((System.nanoTime() - startedNs) / 1_000_000_000L).toInt()
                if (elapsed >= timeoutSecs) break
                onTick?.let { tick -> tick(elapsed, runCatching { waiting?.status()?.num_peers ?: 0 }.getOrDefault(0)) }
                if (done.await(1, TimeUnit.SECONDS)) break
            }
        } finally {
            manager.removeListener(waiter)
            added?.takeIf { it.is_valid() }?.let { runCatching { manager.swig().remove_torrent(it) } }
        }
        return result.get()
    }

    /** `d4:info<info dict>e` of a handle with metadata (null without): what `TorrentInfo(bytes)` needs for the files. */
    private fun infoOnlyTorrent(handle: torrent_handle): ByteArray? {
        val info = handle.torrent_file_ptr()?.takeIf { it.is_valid() } ?: return null
        val span = info.get_info_section()
        val size = span.size().toInt()
        if (size <= 0) return null
        val out = java.io.ByteArrayOutputStream(size + 9)
        out.write("d4:info".toByteArray(Charsets.US_ASCII))
        for (i in 0 until size) out.write(span.at(i.toLong()).toInt())
        out.write('e'.code)
        return out.toByteArray()
    }

    fun resumeFile(id: String): File = File(stateDir, "$id.resume")

    /** Resume data written by a previous run (null when missing or unreadable). */
    fun readResume(id: String): AddTorrentParams? {
        val file = resumeFile(id)
        if (!file.isFile) return null
        return try {
            val ec = error_code()
            val params = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(file.readBytes()), ec)
            if (ec.value() != 0) null else AddTorrentParams(params)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeResume(alert: SaveResumeDataAlert) {
        try {
            val params = alert.params()
            val id = alert.handle().infoHash().toHex()
            atomicWrite(resumeFile(id), AddTorrentParams.writeResumeDataBuf(params))
        } catch (_: Exception) {
            // keep the previous resume file
        }
    }

    private fun settingsPack(settings: TorrentSettings, uploadLimitBps: Int, lsdAllowed: Boolean, includeListen: Boolean): SettingsPack {
        val s = settings.sanitized()
        val pack = SettingsPack()
        pack.downloadRateLimit(s.downloadLimitKib * 1024)
        pack.uploadRateLimit(uploadLimitBps)
        pack.connectionsLimit((s.maxPeers * 4).coerceIn(MIN_CONNECTIONS, MAX_CONNECTIONS))
        pack.setEnableDht(s.dht)
        pack.setEnableLsd(s.lsd && lsdAllowed)
        pack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), s.upnp)
        pack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), s.upnp)
        if (includeListen) {
            val port = s.listenPort
            pack.listenInterfaces("0.0.0.0:$port,[::]:$port")
            lastListenPort = port
        }
        val p = proxy()
        if (p != null || proxied) applyProxy(pack, p)
        proxied = p != null
        return pack
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        /** The same torrent: equal v1 hashes, or equal v2 hashes (a hybrid is known by either). */
        fun sameTorrent(have: info_hash_t, wanted: info_hash_t): Boolean =
            (have.has_v1() && wanted.has_v1() && have.getV1().eq(wanted.getV1())) ||
                (have.has_v2() && wanted.has_v2() && have.getV2().to_hex() == wanted.getV2().to_hex())

        /**
         * Peers, trackers and the DHT through the SOCKS5 [p] (libtorrent carries UDP through SOCKS5 too), host names resolved by the
         * proxy; local discovery, UPnP and NAT-PMP off while proxied (they only make sense on the local network). null = no proxy.
         */
        internal fun applyProxy(pack: SettingsPack, p: app.nijiyome.torrent.system.TorrentProxy?) {
            val types = settings_pack.int_types.proxy_type.swigValue()
            if (p == null) {
                // off: nothing of the previous proxy (host, credentials) stays in the native session's settings
                pack.setInteger(types, settings_pack.proxy_type_t.none.swigValue())
                pack.setString(settings_pack.string_types.proxy_hostname.swigValue(), "")
                pack.setInteger(settings_pack.int_types.proxy_port.swigValue(), 0)
                pack.setString(settings_pack.string_types.proxy_username.swigValue(), "")
                pack.setString(settings_pack.string_types.proxy_password.swigValue(), "")
                return
            }
            val auth = !p.username.isNullOrEmpty()
            pack.setInteger(types, (if (auth) settings_pack.proxy_type_t.socks5_pw else settings_pack.proxy_type_t.socks5).swigValue())
            pack.setString(settings_pack.string_types.proxy_hostname.swigValue(), p.host)
            pack.setInteger(settings_pack.int_types.proxy_port.swigValue(), p.port)
            pack.setString(settings_pack.string_types.proxy_username.swigValue(), p.username.orEmpty())
            pack.setString(settings_pack.string_types.proxy_password.swigValue(), p.password.orEmpty())
            pack.setBoolean(settings_pack.bool_types.proxy_hostnames.swigValue(), true)
            pack.setBoolean(settings_pack.bool_types.proxy_peer_connections.swigValue(), true)
            pack.setBoolean(settings_pack.bool_types.proxy_tracker_connections.swigValue(), true)
            pack.setEnableLsd(false)
            pack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
            pack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        }

        private const val SESSION_STATE = "session.dat"
        private const val MIN_CONNECTIONS = 40
        private const val MAX_CONNECTIONS = 2_000

        private val LISTENED = listOf(
            AlertType.METADATA_RECEIVED,
            AlertType.SAVE_RESUME_DATA,
            AlertType.SAVE_RESUME_DATA_FAILED,
            AlertType.TORRENT_FINISHED,
            AlertType.FILE_COMPLETED,
            AlertType.TORRENT_ERROR,
            AlertType.FILE_ERROR,
            AlertType.STATE_CHANGED,
            AlertType.PIECE_FINISHED,
            AlertType.TORRENT_REMOVED,
        )

        /**
         * Flags for every torrent we add: never auto-managed (the run policy decides), optionally without PEX. Never in upload mode
         * nor stop-when-ready: those belong to fetchMagnet's metadata-only torrent, and a 1.0.0 desktop resume file could carry them.
         */
        fun addFlags(base: torrent_flags_t, paused: Boolean, pex: Boolean, dontDownloadByDefault: Boolean): torrent_flags_t {
            var flags = base.and_(TorrentFlags.AUTO_MANAGED.inv()).and_(TorrentFlags.UPLOAD_MODE.inv()).and_(TorrentFlags.STOP_WHEN_READY.inv())
            flags = if (paused) flags.or_(TorrentFlags.PAUSED) else flags.and_(TorrentFlags.PAUSED.inv())
            if (!pex) flags = flags.or_(TorrentFlags.DISABLE_PEX)
            if (dontDownloadByDefault) flags = flags.or_(TorrentFlags.DEFAULT_DONT_DOWNLOAD)
            return flags
        }
    }
}

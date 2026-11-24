// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/devices.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Queued events older than this are dropped (10 minutes). */
const val EVENT_TTL_SECS = 10L * 60

/** `last_seen_at` reaches `devices.json` at most this often per device (it is always current in memory). */
const val LAST_SEEN_PERSIST_SECS = 60L

/** "Use this device as the source": how long its pushes win regardless of timestamps. */
const val RESYNC_WINDOW_SECS = 10L * 60

object HistoryModeSerializer : KSerializer<HistoryMode> {
    override val descriptor = PrimitiveSerialDescriptor("HistoryMode", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: HistoryMode) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): HistoryMode = HistoryMode.fromWire(decoder.decodeString()) ?: HistoryMode.FULL
}

/** A paired device, as `devices.json` stores it. */
@Serializable
data class Device(
    val id: String,
    val name: String,
    /** SHA-256 (hex) of the client certificate DER. */
    val fingerprint: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("not_after") val notAfter: Long,
    @SerialName("revoked_at") val revokedAt: Long? = null,
    /** `android` | `ios` | empty (unknown). */
    val platform: String = "",
    /** Unix seconds of the last authenticated request; null = never seen after pairing. */
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
    @Serializable(with = HistoryModeSerializer::class)
    @SerialName("history_mode") val historyMode: HistoryMode = HistoryMode.FULL,
) {
    fun isActive(now: Long): Boolean = revokedAt == null && now < notAfter
}

/** Platform of a phone from its `User-Agent`; null when the agent says nothing useful. */
fun platformFromUserAgent(ua: String): String? {
    val l = ua.lowercase()
    return when {
        "android" in l || "okhttp" in l || "dalvik" in l -> "android"
        "iphone" in l || "ipad" in l || "ios" in l || "cfnetwork" in l -> "ios"
        else -> null
    }
}

/**
 * Paired devices: in-memory list + `devices.json` (atomic writes), hot revocation (listeners close the device's live connections
 * and sessions), `last_seen_at` / platform per authenticated request, the per-device event mailbox of `GET /v1/events` and the
 * "use as source" windows (memory only: a restart closes them).
 */
class DeviceRegistry private constructor(private val file: File?, initial: List<Device>) {
    private val devices = initial.toMutableList()
    private val revocationListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val events = HashMap<String, ArrayDeque<Pair<Long, PhoneEvent>>>()
    private val seenPersisted = HashMap<String, Long>()
    private val resync = HashMap<String, Long>()
    private val _eventsVersion = MutableStateFlow(0L)

    /** Bumped whenever an event is queued for any device (the long-poll re-checks its own queue). */
    val eventsVersion: StateFlow<Long> = _eventsVersion.asStateFlow()

    private fun persist() {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(JSON.encodeToString(ListSerializer(Device.serializer()), devices))
        if (!tmp.renameTo(f)) {
            f.delete()
            if (!tmp.renameTo(f)) throw IOException("could not write devices.json")
        }
    }

    @Synchronized
    fun add(d: Device) {
        devices += d
        persist()
    }

    @Synchronized
    fun list(): List<Device> = devices.toList()

    @Synchronized
    fun get(id: String): Device? = devices.firstOrNull { it.id == id }

    @Synchronized
    fun byFingerprint(fp: String): Device? = devices.firstOrNull { it.fingerprint == fp }

    @Synchronized
    fun isActive(id: String, now: Long): Boolean = devices.any { it.id == id && it.isActive(now) }

    fun onRevoked(listener: (String) -> Unit) {
        revocationListeners += listener
    }

    /** Revokes at once (effective on the next request) and notifies the listeners. False when unknown or already revoked. */
    fun revoke(id: String, now: Long): Boolean {
        val changed = synchronized(this) {
            val i = devices.indexOfFirst { it.id == id && it.revokedAt == null }
            if (i < 0) return@synchronized false
            devices[i] = devices[i].copy(revokedAt = now)
            runCatching { persist() }
            true
        }
        if (changed) revocationListeners.forEach { runCatching { it(id) } }
        return changed
    }

    /** Display name (persisted). Empty names and unknown or revoked devices are refused. */
    @Synchronized
    fun rename(id: String, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val i = devices.indexOfFirst { it.id == id && it.revokedAt == null }
        if (i < 0) return false
        devices[i] = devices[i].copy(name = n)
        runCatching { persist() }
        return true
    }

    /** `full` for an unknown device (the routes never get there without an active one). */
    @Synchronized
    fun historyMode(id: String): HistoryMode = devices.firstOrNull { it.id == id }?.historyMode ?: HistoryMode.FULL

    @Synchronized
    fun setHistoryMode(id: String, mode: HistoryMode): Boolean {
        val i = devices.indexOfFirst { it.id == id && it.revokedAt == null }
        if (i < 0) return false
        devices[i] = devices[i].copy(historyMode = mode)
        runCatching { persist() }
        return true
    }

    @Synchronized
    fun openResync(id: String, now: Long) {
        resync[id] = now + RESYNC_WINDOW_SECS
    }

    @Synchronized
    fun resyncOpen(id: String, now: Long): Boolean = resync[id]?.let { now < it } == true

    /** Closes the window; true when it was open at [now] (an expired one is dropped and answers false). */
    @Synchronized
    fun closeResync(id: String, now: Long): Boolean = resync.remove(id)?.let { now < it } == true

    /** [id] must be active and in `full` mode; opens its window and queues `history_resync` / `push_all` for it. */
    fun startResync(id: String, now: Long): Boolean {
        val ok = synchronized(this) { devices.any { it.id == id && it.isActive(now) && it.historyMode.acceptsPushes } }
        if (!ok) return false
        openResync(id, now)
        return pushEvent(id, PhoneEvent.HistoryResync(ResyncMode.PUSH_ALL), now)
    }

    /** `resync-done` from [source]: closes its window and, when it was open, queues `pull_replace` for the others. */
    fun finishResync(source: String, now: Long): Int {
        if (!closeResync(source, now)) return 0
        return History.resyncTargets(list(), source, now).count { pushEvent(it, PhoneEvent.HistoryResync(ResyncMode.PULL_REPLACE), now) }
    }

    /** An authenticated request of [id]: refreshes `last_seen_at` and (from the `User-Agent`) the platform. */
    @Synchronized
    fun touch(id: String, now: Long, userAgent: String?) {
        val platform = userAgent?.let(::platformFromUserAgent)
        val i = devices.indexOfFirst { it.id == id }
        if (i < 0) return
        val d = devices[i]
        val platformChanged = platform != null && d.platform != platform
        devices[i] = d.copy(lastSeenAt = now, platform = platform ?: d.platform)
        val last = seenPersisted[id] ?: 0
        if (platformChanged || now - last >= LAST_SEEN_PERSIST_SECS) {
            seenPersisted[id] = now
            runCatching { persist() }
        }
    }

    /** Queues [event] for [id] (oldest dropped above [EVENTS_QUEUE_MAX]); false for an unknown or revoked device. */
    fun pushEvent(id: String, event: PhoneEvent, now: Long): Boolean {
        synchronized(this) {
            if (devices.none { it.id == id && it.revokedAt == null }) return false
            val q = events.getOrPut(id) { ArrayDeque() }
            q.removeAll { now - it.first >= EVENT_TTL_SECS }
            q.addLast(now to event)
            while (q.size > EVENTS_QUEUE_MAX) q.removeFirst()
        }
        _eventsVersion.value = _eventsVersion.value + 1
        return true
    }

    /** Drains the live events of [id], oldest first. */
    @Synchronized
    fun takeEvents(id: String, now: Long): List<PhoneEvent> =
        events.remove(id)?.filter { now - it.first < EVENT_TTL_SECS }?.map { it.second } ?: emptyList()

    companion object {
        private val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

        /** File-backed registry: a missing file starts empty, a corrupt one is an error. */
        fun open(file: File): DeviceRegistry {
            val list = if (file.isFile) JSON.decodeFromString(ListSerializer(Device.serializer()), file.readText()) else emptyList()
            return DeviceRegistry(file, list)
        }

        fun inMemory(): DeviceRegistry = DeviceRegistry(null, emptyList())
    }
}

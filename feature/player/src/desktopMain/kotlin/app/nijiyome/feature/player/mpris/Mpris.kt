// SPDX-License-Identifier: AGPL-3.0-or-later
// v1.1 (F2): the desktop player in the system's media controls on Linux (MPRIS 2.2 over D-Bus: the media keys, KDE / GNOME media
// widgets, playerctl). Spec: https://specifications.freedesktop.org/mpris-spec/latest/
@file:Suppress("FunctionName") // D-Bus member names are PascalCase

package app.nijiyome.feature.player.mpris

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.util.Locale

// PENDIENTE-ESCRITORIO: controles multimedia del sistema en Windows (SMTC, Windows.Media.SystemMediaTransportControls). Es WinRT: desde la JVM no hay un camino sin código nativo (JNA no llama interfaces WinRT con eventos). Diseño: la misma MprisPort (estado + comandos) detrás de una interfaz de plataforma; en Windows, una DLL pequeña (C++/WinRT o Rust con el crate `windows`, por JNI) que cree el SMTC con ISystemMediaTransportControlsInterop::GetForWindow sobre el HWND de la ventana, publique título / episodio / carátula / estado y devuelva ButtonPressed (Play, Pause, Next, Previous) a la JVM. Rust por JNI es el último recurso y lo decide el usuario antes.

/** What the player screen shows MPRIS (read on D-Bus threads: must be cheap and thread safe). */
internal data class MprisSnapshot(
    val status: Status,
    val title: String,
    /** The episode line ("Episodio 3"), shown as the artist. */
    val subtitle: String?,
    /** `https://` poster, or null. */
    val artUrl: String?,
    val lengthUs: Long,
    val positionUs: Long,
    val canGoNext: Boolean,
    val canGoPrevious: Boolean,
    /** Changes when the episode changes (the track id). */
    val trackKey: String,
) {
    enum class Status(val dbus: String) { PLAYING("Playing"), PAUSED("Paused"), STOPPED("Stopped") }

    companion object {
        val STOPPED = MprisSnapshot(Status.STOPPED, "", null, null, 0, 0, false, false, "none")
    }
}

/** The commands MPRIS forwards to the player screen (called on D-Bus threads; the screen moves them to its own thread). */
internal interface MprisPort {
    fun snapshot(): MprisSnapshot
    fun play()
    fun pause()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionUs: Long)
}

@DBusInterfaceName("org.mpris.MediaPlayer2")
internal interface MediaPlayer2 : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
internal interface MediaPlayer2Player : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    /** `Seeked(x Position)`: the position jumped (a seek), not regular progress. */
    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

/** The keys and values MPRIS's `Metadata` carries for [s] (empty when stopped). */
internal fun mprisMetadata(s: MprisSnapshot): Map<String, Variant<*>> {
    if (s.status == MprisSnapshot.Status.STOPPED) {
        return linkedMapOf("mpris:trackid" to Variant(DBusPath(NO_TRACK)))
    }
    val out = linkedMapOf<String, Variant<*>>(
        "mpris:trackid" to Variant(DBusPath(trackPath(s.trackKey))),
        "xesam:title" to Variant(s.title),
    )
    s.subtitle?.takeIf { it.isNotBlank() }?.let { out["xesam:artist"] = Variant(listOf(it), "as") }
    if (s.lengthUs > 0) out["mpris:length"] = Variant(s.lengthUs)
    s.artUrl?.let { out["mpris:artUrl"] = Variant(it) }
    return out
}

/** A valid D-Bus object path for a track key (only `[A-Za-z0-9_]` in each element). */
internal fun trackPath(key: String): String {
    val safe = key.map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }.joinToString("").ifEmpty { "none" }
    return "/app/nijiyome/track/$safe"
}

internal const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
internal const val MPRIS_PATH = "/org/mpris/MediaPlayer2"
internal const val PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
internal const val ROOT_IFACE = "org.mpris.MediaPlayer2"

/**
 * The exported MPRIS object: the root and player interfaces plus `org.freedesktop.DBus.Properties` over whatever [port] the player
 * screen attached (none = stopped, every command ignored).
 */
internal class MprisObject(@Volatile var port: MprisPort?) : MediaPlayer2, MediaPlayer2Player, Properties {
    override fun getObjectPath(): String = MPRIS_PATH

    private fun snap(): MprisSnapshot = runCatching { port?.snapshot() }.getOrNull() ?: MprisSnapshot.STOPPED

    override fun Raise() = Unit
    override fun Quit() = Unit
    override fun Next() { port?.next() }
    override fun Previous() { port?.previous() }
    override fun Pause() { port?.pause() }
    override fun PlayPause() { port?.playPause() }
    override fun Stop() { port?.pause() }
    override fun Play() { port?.play() }
    override fun OpenUri(uri: String) = Unit

    override fun Seek(offset: Long) {
        val p = port ?: return
        val s = p.snapshot()
        val target = (s.positionUs + offset).coerceAtLeast(0)
        // The spec: seeking past the end acts like Next; here it stops at the end (the player's own end screen follows).
        p.seekTo(if (s.lengthUs > 0) target.coerceAtMost(s.lengthUs) else target)
    }

    override fun SetPosition(trackId: DBusPath, position: Long) {
        val p = port ?: return
        val s = p.snapshot()
        // Ignored when it names another track or falls outside it (spec).
        if (trackId.path != trackPath(s.trackKey) || position < 0 || (s.lengthUs > 0 && position > s.lengthUs)) return
        p.seekTo(position)
    }

    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
        @Suppress("UNCHECKED_CAST")
        return all(interfaceName)[propertyName] as A
    }

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = Unit // Volume / Rate / Loop: read only here

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = all(interfaceName)

    fun all(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        ROOT_IFACE -> linkedMapOf(
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(false),
            "HasTrackList" to Variant(false),
            "Identity" to Variant(IDENTITY),
            "DesktopEntry" to Variant(DESKTOP_ENTRY),
            "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
            "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
        )
        PLAYER_IFACE -> playerProperties(snap())
        else -> emptyMap()
    }

    companion object {
        const val IDENTITY = "Nijiyome"
        /** packaging/linux/app.nijiyome.desktop, without `.desktop` (spec). */
        const val DESKTOP_ENTRY = "app.nijiyome"
    }
}

/** `org.mpris.MediaPlayer2.Player` properties of [s]. */
internal fun playerProperties(s: MprisSnapshot): Map<String, Variant<*>> {
    val active = s.status != MprisSnapshot.Status.STOPPED
    return linkedMapOf(
        "PlaybackStatus" to Variant(s.status.dbus),
        "Rate" to Variant(1.0),
        "MinimumRate" to Variant(1.0),
        "MaximumRate" to Variant(1.0),
        "Metadata" to Variant(mprisMetadata(s), "a{sv}"),
        "Volume" to Variant(1.0),
        "Position" to Variant(s.positionUs),
        "CanGoNext" to Variant(active && s.canGoNext),
        "CanGoPrevious" to Variant(active && s.canGoPrevious),
        "CanPlay" to Variant(active),
        "CanPause" to Variant(active),
        "CanSeek" to Variant(active && s.lengthUs > 0),
        "CanControl" to Variant(true),
    )
}

/** The properties that changed between two snapshots (Position never: MPRIS clients interpolate it and listen to Seeked). */
internal fun changedProperties(before: MprisSnapshot?, after: MprisSnapshot): Map<String, Variant<*>> {
    val a = playerProperties(after) - "Position"
    if (before == null) return a
    val b = playerProperties(before)
    return a.filter { (k, v) -> b[k] != v }
}

/**
 * The app's MPRIS service on the session bus: one connection and one bus name for the whole app (`org.mpris.MediaPlayer2.nijiyome`,
 * or `.instance<pid>` when another copy has it), connected when a player screen attaches and closed when the last one detaches (the
 * desktop's media widget then forgets the episode) and when the app quits ([shutdown]). Without a session bus it stays off (logged
 * once); nothing else depends on it.
 *
 * v1.1 review #5: the player screen hands its work to [post] (this service's own thread, which lives as long as the app), never to
 * a Compose scope: a scope is cancelled with the screen, so a detach launched there from `onDispose` never ran, the bus kept the old
 * episode and the service kept the closed screen's view model and session.
 */
internal class MprisService(
    private val connect: () -> DBusConnection = { DBusConnectionBuilder.forSessionBus().withShared(false).build() },
    private val log: (String) -> Unit = { System.err.println("I/mpris: $it") },
) {
    private val obj = MprisObject(null)
    private var connection: DBusConnection? = null
    private var failed = false
    private var owner: Any? = null
    private var last: MprisSnapshot? = null

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "nijiyome-mpris").apply { isDaemon = true }
    }

    /** Runs [block] on this service's thread, in order; nothing once the service is shut down. */
    fun post(block: MprisService.() -> Unit) {
        runCatching { executor.execute { runCatching { block() }.onFailure { log("failed: $it") } } }
    }

    /** The screen's attach, on this service's thread (callable from any thread, never blocks). */
    fun attachAsync(owner: Any, port: MprisPort) = post { attach(owner, port) }

    /** The screen's detach from `onDispose`: runs even though the screen's own scope is cancelled by then. */
    fun detachAsync(owner: Any) = post { detach(owner) }

    /** Waits until everything [post]ed so far ran (tests); false on timeout. */
    fun flush(timeoutMs: Long = 5_000): Boolean = runCatching {
        executor.submit {}.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        true
    }.getOrDefault(false)

    /** Whether a player screen is attached (its port is held). */
    @get:Synchronized
    val attached: Boolean get() = obj.port != null

    /** The bus name it got (null until connected). */
    @Volatile
    var busName: String? = null
        private set

    @Synchronized
    fun attach(owner: Any, port: MprisPort) {
        if (!ensureConnected()) return
        this.owner = owner
        obj.port = port
        changed()
    }

    /** [owner]'s screen left: "Stopped", then the connection closes (nothing holds the screen any more). */
    @Synchronized
    fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        obj.port = null
        changed()
        close()
    }

    /** Sends PropertiesChanged for what differs from the last call. */
    @Synchronized
    fun changed() {
        val c = connection ?: return
        val now = runCatching { obj.port?.snapshot() }.getOrNull() ?: MprisSnapshot.STOPPED
        val diff = changedProperties(last, now)
        last = now
        if (diff.isEmpty()) return
        runCatching { c.sendMessage(Properties.PropertiesChanged(MPRIS_PATH, PLAYER_IFACE, diff, emptyList())) }
            .onFailure { log("PropertiesChanged failed: $it") }
    }

    /** `Seeked`: the position jumped to [positionUs]. */
    @Synchronized
    fun seeked(positionUs: Long) {
        val c = connection ?: return
        runCatching { c.sendMessage(MediaPlayer2Player.Seeked(MPRIS_PATH, positionUs)) }.onFailure { log("Seeked failed: $it") }
    }

    /** Leaves the bus (the name is released); the next [attach] connects again. */
    @Synchronized
    fun close() {
        owner = null
        obj.port = null
        last = null
        val c = connection ?: return
        connection = null
        busName?.let { runCatching { c.releaseBusName(it) } }
        busName = null
        runCatching { c.close() }
    }

    /** App exit: closes the connection (after anything already posted) and stops this service's thread. */
    fun shutdown(timeoutMs: Long = 2_000) {
        post { close() }
        executor.shutdown()
        runCatching { executor.awaitTermination(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    private fun ensureConnected(): Boolean {
        if (connection != null) return true
        if (failed) return false
        return runCatching {
            val c = connect()
            c.exportObject(MPRIS_PATH, obj)
            val name = runCatching { c.requestBusName(BUS_NAME); BUS_NAME }.getOrElse {
                val alt = "$BUS_NAME.instance${ProcessHandle.current().pid()}"
                c.requestBusName(alt)
                alt
            }
            busName = name
            connection = c
            log("on the session bus as $name")
            true
        }.getOrElse {
            failed = true
            log("no session bus, system media controls off: ${it.message ?: it}")
            false
        }
    }

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.nijiyome"

        private val lazyInstance = lazy {
            if (System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("linux")) MprisService() else null
        }

        /** The app's service, Linux only (null elsewhere). */
        val instance: MprisService? by lazyInstance

        /** App exit: shuts the service down if it was ever created (nothing is created for it). */
        fun shutdownInstance() {
            if (lazyInstance.isInitialized()) lazyInstance.value?.shutdown()
        }
    }
}

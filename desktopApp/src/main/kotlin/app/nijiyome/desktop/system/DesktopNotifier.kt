// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.model.NoticeDestination
import app.nijiyome.model.SystemNotifier
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * System notifications on desktop (torrent finished or failed, episode downloaded: what Android posts through its notification
 * channels). Linux: `org.freedesktop.Notifications.Notify` on the session bus, through `gdbus` (GLib) or else `notify-send`
 * (libnotify); a desktop without a notification server shows nothing and nothing fails. Windows: a balloon of the AWT tray.
 * Posting never blocks the caller: commands run on one background thread with a timeout.
 *
 * Clicks (parity DESK-09): a notice with a [NoticeDestination] calls [onOpen] when it is clicked, where the mechanism reports it.
 * Linux through gdbus: the notice carries the `default` action and one `gdbus monitor` process watches `ActionInvoked` for the ids
 * it posted (a server that sends that signal only to the posting connection, which has already left, reports nothing). The
 * `notify-send` fallback reports no clicks. Windows: a click on the balloon while its tray icon is still up (10 s).
 */
class DesktopNotifier(
    private val windows: Boolean,
    private val icon: String = "app.nijiyome",
    private val run: (List<String>) -> Int = ::runCommand,
    private val windowsBalloon: (title: String, body: String, onClick: (() -> Unit)?) -> Unit = ::awtBalloon,
    /** Runs a command and returns its output when it succeeded, else null (gdbus prints the new notification's id). */
    private val call: (List<String>) -> String? = ::runForOutput,
    /** Starts the process whose output lines report `ActionInvoked` / `NotificationClosed`; null when it cannot run. */
    private val startMonitor: () -> Process? = ::startSignalMonitor,
) : SystemNotifier {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "notifications").apply { isDaemon = true } }

    /** Called (on a background thread) with the destination of a clicked notification. Set by the app at start. */
    @Volatile
    var onOpen: ((NoticeDestination) -> Unit)? = null

    /** Notification ids posted with a destination, until clicked or closed. */
    private val pending = ConcurrentHashMap<Long, NoticeDestination>()

    @Volatile
    private var monitor: Process? = null

    override fun notify(title: String, body: String) {
        executor.execute { post(title, body) }
    }

    override fun notify(title: String, body: String, destination: NoticeDestination?) {
        executor.execute { post(title, body, destination) }
    }

    /** Posts on this thread (tests). */
    fun post(title: String, body: String, destination: NoticeDestination? = null) {
        if (windows) {
            val onClick: (() -> Unit)? = destination?.let { d -> { onOpen?.invoke(d) } }
            runCatching { windowsBalloon(title, body, onClick) }
            return
        }
        if (destination == null) {
            if (run(gdbusCommand(title, body, icon)) == 0) return
        } else {
            val output = call(gdbusCommand(title, body, icon, clickable = true))
            if (output != null) {
                notificationId(output)?.let { id -> track(id, destination) }
                return
            }
        }
        run(notifySendCommand(title, body, icon))
    }

    private fun track(id: Long, destination: NoticeDestination) {
        // A long session never piles up ids of notices nobody clicked or closed.
        if (pending.size >= MAX_PENDING) pending.keys.minOrNull()?.let { pending.remove(it) }
        pending[id] = destination
        ensureMonitor()
    }

    @Synchronized
    private fun ensureMonitor() {
        if (monitor?.isAlive == true) return
        val process = runCatching { startMonitor() }.getOrNull() ?: return
        monitor = process
        Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach(::onSignalLine) }
            }
        }, "notification-clicks").apply { isDaemon = true }.start()
    }

    /** One line of `gdbus monitor`: a click on the body opens its destination; a closed notice is forgotten. */
    internal fun onSignalLine(line: String) {
        val invoked = ACTION_INVOKED.find(line)
        if (invoked != null) {
            val id = invoked.groupValues[1].toLongOrNull() ?: return
            if (invoked.groupValues[2] != DEFAULT_ACTION) return
            pending.remove(id)?.let { d -> onOpen?.invoke(d) }
            return
        }
        NOTIFICATION_CLOSED.find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { pending.remove(it) }
    }

    /** Ids still waiting for a click (tests). */
    internal fun pendingIds(): Set<Long> = pending.keys.toSet()

    companion object {
        private const val MAX_PENDING = 64
        private const val DEFAULT_ACTION = "default"
        private val ACTION_INVOKED = Regex("""ActionInvoked \(uint32 (\d+), '([^']*)'\)""")
        private val NOTIFICATION_CLOSED = Regex("""NotificationClosed \(uint32 (\d+),""")
        private val NOTIFY_REPLY = Regex("""uint32 (\d+)""")

        /**
         * `gdbus call … Notify` (signature `susssasa{sv}i`); every string as a quoted GVariant text literal. The expiry is a positive
         * 8 s: gdbus would read `-1` as an option. [clickable] adds the `default` action (a click on the notice's body).
         */
        fun gdbusCommand(title: String, body: String, icon: String, clickable: Boolean = false): List<String> = listOf(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.Notify",
            gvariantString("Nijiyome"), "0", gvariantString(icon), gvariantString(title), gvariantString(body),
            if (clickable) "['$DEFAULT_ACTION', '']" else "[]",
            "{'desktop-entry': <'app.nijiyome'>}", "8000",
        )

        /** The id in gdbus's reply to Notify (`(uint32 42,)`). */
        fun notificationId(reply: String): Long? = NOTIFY_REPLY.find(reply)?.groupValues?.get(1)?.toLongOrNull()

        fun notifySendCommand(title: String, body: String, icon: String): List<String> =
            listOf("notify-send", "--app-name=Nijiyome", "--icon=$icon", "--", title, body)

        /** A GVariant text string: single quotes, with `\` and `'` escaped; control characters become spaces. */
        fun gvariantString(s: String): String = buildString {
            append('\'')
            for (c in s) {
                when {
                    c == '\\' -> append("\\\\")
                    c == '\'' -> append("\\'")
                    c < ' ' -> append(' ')
                    else -> append(c)
                }
            }
            append('\'')
        }

        private fun runCommand(command: List<String>): Int = try {
            val p = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (p.waitFor(10, TimeUnit.SECONDS)) p.exitValue() else -1.also { p.destroyForcibly() }
        } catch (_: IOException) {
            -1
        }

        private fun runForOutput(command: List<String>): String? = try {
            val p = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = p.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                null
            } else {
                // The reply is one short line, so it fits the pipe buffer while the process runs.
                val output = p.inputStream.bufferedReader().readText()
                if (p.exitValue() == 0) output else null
            }
        } catch (_: IOException) {
            null
        }

        /** `gdbus monitor` on the notification service; it lives as long as the app (destroyed at shutdown). */
        private fun startSignalMonitor(): Process? = try {
            ProcessBuilder(
                "gdbus", "monitor", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
            ).redirectErrorStream(true).start().also { p ->
                Runtime.getRuntime().addShutdownHook(Thread { p.destroy() })
            }
        } catch (_: IOException) {
            null
        }

        private fun awtBalloon(title: String, body: String, onClick: (() -> Unit)?) {
            if (!java.awt.SystemTray.isSupported()) return
            val tray = java.awt.SystemTray.getSystemTray()
            val image = DesktopNotifier::class.java.getResource("/icons/nijiyome.png")?.let { java.awt.Toolkit.getDefaultToolkit().getImage(it) } ?: return
            val trayIcon = java.awt.TrayIcon(image, "Nijiyome").apply { isImageAutoSize = true }
            // Windows reports a click on the balloon as the tray icon's action.
            if (onClick != null) trayIcon.addActionListener { onClick() }
            tray.add(trayIcon)
            trayIcon.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
            // The balloon stays in the action center; the extra icon goes away after a while.
            Thread.sleep(10_000)
            tray.remove(trayIcon)
        }
    }
}

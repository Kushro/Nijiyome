// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.model.SystemNotifier
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * System notifications on desktop (torrent finished or failed, episode downloaded: what Android posts through its notification
 * channels). Linux: `org.freedesktop.Notifications.Notify` on the session bus, through `gdbus` (GLib) or else `notify-send`
 * (libnotify); a desktop without a notification server shows nothing and nothing fails. Windows: a balloon of the AWT tray.
 * Posting never blocks the caller: commands run on one background thread with a timeout.
 */
class DesktopNotifier(
    private val windows: Boolean,
    private val icon: String = "app.nijiyome",
    private val run: (List<String>) -> Int = ::runCommand,
    private val windowsBalloon: (String, String) -> Unit = ::awtBalloon,
) : SystemNotifier {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "notifications").apply { isDaemon = true } }

    override fun notify(title: String, body: String) {
        executor.execute { post(title, body) }
    }

    /** Posts on this thread (tests). */
    fun post(title: String, body: String) {
        if (windows) {
            runCatching { windowsBalloon(title, body) }
            return
        }
        if (run(gdbusCommand(title, body, icon)) == 0) return
        run(notifySendCommand(title, body, icon))
    }

    companion object {
        /** `gdbus call … Notify` (signature `susssasa{sv}i`); every string as a quoted GVariant text literal. The expiry is a positive 8 s: gdbus would read `-1` as an option. */
        fun gdbusCommand(title: String, body: String, icon: String): List<String> = listOf(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.Notifications",
            "--object-path", "/org/freedesktop/Notifications",
            "--method", "org.freedesktop.Notifications.Notify",
            gvariantString("Nijiyome"), "0", gvariantString(icon), gvariantString(title), gvariantString(body), "[]",
            "{'desktop-entry': <'app.nijiyome'>}", "8000",
        )

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

        private fun awtBalloon(title: String, body: String) {
            if (!java.awt.SystemTray.isSupported()) return
            val tray = java.awt.SystemTray.getSystemTray()
            val image = DesktopNotifier::class.java.getResource("/icons/nijiyome.png")?.let { java.awt.Toolkit.getDefaultToolkit().getImage(it) } ?: return
            val trayIcon = java.awt.TrayIcon(image, "Nijiyome").apply { isImageAutoSize = true }
            tray.add(trayIcon)
            trayIcon.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
            // The balloon stays in the action center; the extra icon goes away after a while.
            Thread.sleep(10_000)
            tray.remove(trayIcon)
        }
    }
}

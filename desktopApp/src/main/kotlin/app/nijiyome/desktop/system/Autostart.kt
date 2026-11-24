// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * "Iniciar con el sistema" (Tatakai Next's `autostart.rs`, per user, no elevation): Linux writes an XDG autostart entry
 * `$XDG_CONFIG_HOME/autostart/app.nijiyome.desktop` (default `~/.config/autostart`), Windows the `HKCU\…\Run` value `Nijiyome`.
 * Both start the app with [LaunchArgs.MINIMIZED], which shows only the tray icon when "Iniciar minimizado" is on.
 * Only a packaged app can register ([launcher] null otherwise): a development run moves on every build.
 */
class Autostart(
    /** The executable to start: the AppImage, the jpackage launcher or the installed `Nijiyome.exe`. */
    private val launcher: String?,
    private val windows: Boolean,
    private val env: (String) -> String? = System::getenv,
    private val home: String = System.getProperty("user.home"),
    /** Runs a command (Windows `reg`), returns its exit code. Injectable so tests only look at the command. */
    private val run: (List<String>) -> Int = ::runCommand,
) {
    val supported: Boolean get() = launcher != null

    /** The XDG autostart file (Linux). */
    val desktopFile: File
        get() {
            val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File) ?: File(home, ".config")
            return File(File(config, "autostart"), DESKTOP_ID)
        }

    fun isEnabled(): Boolean = when {
        launcher == null -> false
        windows -> run(queryCommand()) == 0
        else -> desktopFile.isFile
    }

    /** Creates (true) or removes (false) the entry. Idempotent. */
    fun setEnabled(enabled: Boolean) {
        val exe = launcher ?: return
        if (windows) {
            val code = run(if (enabled) addCommand(exe) else deleteCommand())
            // `reg delete` of a missing value answers 1: nothing to remove.
            if (code != 0 && enabled) throw IOException("reg add answered $code")
            return
        }
        val file = desktopFile
        if (!enabled) {
            file.delete()
            return
        }
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, "$DESKTOP_ID.tmp")
        tmp.writeText(desktopEntry(exe))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.path}")
        }
    }

    companion object {
        const val DESKTOP_ID = "app.nijiyome.desktop"
        const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE_NAME = "Nijiyome"

        /** The autostart entry (Desktop Entry spec 1.5). */
        fun desktopEntry(exe: String): String = buildString {
            append("[Desktop Entry]\n")
            append("Type=Application\n")
            append("Version=1.5\n")
            append("Name=Nijiyome\n")
            append("Comment=Start Nijiyome when you sign in\n")
            append("Comment[es]=Abre Nijiyome al iniciar sesión\n")
            append("Exec=").append(desktopExec(exe)).append(' ').append(LaunchArgs.MINIMIZED).append('\n')
            append("Icon=app.nijiyome\n")
            append("Terminal=false\n")
            append("X-GNOME-Autostart-enabled=true\n")
        }

        /** The executable as an `Exec=` argument: quoted, with `"`, `` ` ``, `$` and `\` escaped and `%` doubled (Desktop Entry spec). */
        fun desktopExec(exe: String): String = buildString {
            append('"')
            for (c in exe) {
                when (c) {
                    '"', '`', '$', '\\' -> append('\\').append(c)
                    '%' -> append("%%")
                    else -> append(c)
                }
            }
            append('"')
        }

        /** `"C:\…\Nijiyome.exe" --minimized`: the value data of the `Run` entry. */
        fun runValue(exe: String): String = "\"$exe\" ${LaunchArgs.MINIMIZED}"

        fun addCommand(exe: String): List<String> = listOf("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", runValue(exe), "/f")

        fun deleteCommand(): List<String> = listOf("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")

        fun queryCommand(): List<String> = listOf("reg", "query", RUN_KEY, "/v", VALUE_NAME)

        private fun runCommand(command: List<String>): Int = try {
            val p = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (p.waitFor(15, TimeUnit.SECONDS)) p.exitValue() else -1.also { p.destroyForcibly() }
        } catch (_: IOException) {
            -1
        }
    }
}

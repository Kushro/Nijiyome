// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v1.1 (F6) "Abrir enlaces magnet y archivos .torrent con Nijiyome" (Tatakai Next's `magnet.rs`, per user, no elevation), asked
 * in the first-run wizard and in Ajustes › Datos. Only a packaged app offers it ([launcher] null otherwise).
 *
 * - Linux: a hidden desktop entry `$XDG_DATA_HOME/applications/app.nijiyome.links.desktop` (`Exec=<launcher> %u`, also for
 *   `nijiyome:` links) and the defaults of `$XDG_CONFIG_HOME/mimeapps.list` (what `xdg-mime default` writes), for
 *   `x-scheme-handler/magnet` and `application/x-bittorrent`. Turning it off removes only those defaults while they still name
 *   this entry, and the entry itself (so the desktop stops offering it for those types); another client's choice is never touched.
 * - Windows: `HKCU\Software\Classes\magnet` (`URL Protocol`, `shell\open\command` = `"<launcher>" "%1"`) and `.torrent` files
 *   through the `Nijiyome.torrent` class (the installer's name), as the default of `HKCU\Software\Classes\.torrent`; through `reg`.
 *   Windows keeps the user's own choice from "Default apps" above both. Turning it off deletes the magnet key and the `.torrent`
 *   default only while they still point at this launcher / class.
 */
class LinkHandlers(
    private val launcher: String?,
    private val windows: Boolean,
    private val env: (String) -> String? = System::getenv,
    private val home: String = System.getProperty("user.home"),
    /** Runs a command, returns its exit code and output (null when it could not run). Injectable so tests only look at commands. */
    private val run: (List<String>) -> Pair<Int, String>? = ::runCommand,
) {
    val supported: Boolean get() = launcher != null

    private fun xdg(name: String, default: String): File =
        env(name)?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File) ?: File(home, default)

    val handlerEntry: File get() = File(File(xdg("XDG_DATA_HOME", ".local/share"), "applications"), HANDLER_ID)

    val mimeApps: File get() = File(xdg("XDG_CONFIG_HOME", ".config"), "mimeapps.list")

    fun isEnabled(): Boolean {
        val exe = launcher ?: return false
        if (windows) {
            val (code, out) = run(listOf("reg", "query", MAGNET_COMMAND_KEY, "/ve")) ?: return false
            return code == 0 && out.contains(exe, ignoreCase = true)
        }
        if (!handlerEntry.isFile) return false
        val text = runCatching { mimeApps.readText() }.getOrNull() ?: return false
        return defaultsOf(text)[MAGNET_MIME]?.split(';')?.firstOrNull()?.trim() == HANDLER_ID
    }

    /** Takes (true) or gives back (false) the links. Idempotent; throws [IOException] when the system refused. */
    fun setEnabled(enabled: Boolean) {
        val exe = launcher ?: return
        if (windows) return setWindows(exe, enabled)
        if (enabled) {
            writeAtomically(handlerEntry, handlerDesktopEntry(exe))
            // Best effort: some desktops read MimeType= from the cache only.
            run(listOf("update-desktop-database", handlerEntry.parentFile.path))
        }
        val before = runCatching { mimeApps.readText() }.getOrDefault("")
        val after = withDefaults(before, MIMES.associateWith { if (enabled) "$HANDLER_ID;" else null }, owner = HANDLER_ID)
        if (after != before) writeAtomically(mimeApps, after)
        if (!enabled && handlerEntry.isFile) {
            // v1.1 review #14: a leftover entry still announces the types, and a desktop without another client would pick it.
            handlerEntry.delete()
            run(listOf("update-desktop-database", handlerEntry.parentFile.path))
        }
    }

    private fun setWindows(exe: String, enabled: Boolean) {
        if (enabled) {
            val commands = listOf(
                listOf("reg", "add", MAGNET_KEY, "/ve", "/d", "URL:Magnet link", "/f"),
                listOf("reg", "add", MAGNET_KEY, "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f"),
                listOf("reg", "add", MAGNET_COMMAND_KEY, "/ve", "/d", windowsCommand(exe), "/f"),
                // .torrent files (v1.1 review #14): the installer's class, then the extension's default.
                listOf("reg", "add", TORRENT_CLASS_KEY, "/ve", "/d", "Torrent file (Nijiyome)", "/f"),
                listOf("reg", "add", TORRENT_COMMAND_KEY, "/ve", "/d", windowsCommand(exe), "/f"),
                listOf("reg", "add", TORRENT_EXT_KEY, "/ve", "/d", TORRENT_CLASS, "/f"),
            )
            for (c in commands) {
                val code = run(c)?.first
                if (code != 0) throw IOException("reg add answered $code")
            }
        } else {
            if (isEnabled()) run(listOf("reg", "delete", MAGNET_KEY, "/f"))
            val torrent = run(listOf("reg", "query", TORRENT_EXT_KEY, "/ve"))
            if (torrent?.first == 0 && torrent.second.contains(TORRENT_CLASS, ignoreCase = true)) {
                run(listOf("reg", "delete", TORRENT_EXT_KEY, "/ve", "/f"))
            }
        }
    }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.path}")
        }
    }

    companion object {
        const val HANDLER_ID = "app.nijiyome.links.desktop"
        const val MAGNET_MIME = "x-scheme-handler/magnet"
        val MIMES = listOf(MAGNET_MIME, "application/x-bittorrent")
        const val MAGNET_KEY = "HKCU\\Software\\Classes\\magnet"
        const val MAGNET_COMMAND_KEY = "HKCU\\Software\\Classes\\magnet\\shell\\open\\command"
        const val TORRENT_CLASS = "Nijiyome.torrent"
        const val TORRENT_CLASS_KEY = "HKCU\\Software\\Classes\\Nijiyome.torrent"
        const val TORRENT_COMMAND_KEY = "HKCU\\Software\\Classes\\Nijiyome.torrent\\shell\\open\\command"
        const val TORRENT_EXT_KEY = "HKCU\\Software\\Classes\\.torrent"

        /** `"C:\…\Nijiyome.exe" "%1"`. */
        fun windowsCommand(exe: String): String = "\"$exe\" \"%1\""

        /** The hidden handler entry (Desktop Entry spec 1.5). */
        fun handlerDesktopEntry(exe: String): String = buildString {
            append("[Desktop Entry]\n")
            append("Type=Application\n")
            append("Version=1.5\n")
            append("Name=Nijiyome\n")
            append("Comment=Open magnet links and .torrent files with Nijiyome\n")
            append("Comment[es]=Abre enlaces magnet y archivos .torrent con Nijiyome\n")
            append("Exec=").append(Autostart.desktopExec(exe)).append(" %u\n")
            append("Icon=app.nijiyome\n")
            append("Terminal=false\n")
            append("NoDisplay=true\n")
            append("MimeType=x-scheme-handler/magnet;application/x-bittorrent;x-scheme-handler/nijiyome;\n")
        }

        /** The `[Default Applications]` keys of a `mimeapps.list`. */
        fun defaultsOf(text: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            var inDefaults = false
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("[")) inDefaults = line == DEFAULTS
                else if (inDefaults && '=' in line && !line.startsWith("#")) out[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
            }
            return out
        }

        /**
         * [text] (a `mimeapps.list`) with the `[Default Applications]` keys of [changes] set, or removed when null; a removal only
         * happens while the key's first application is [owner]. Other lines and groups stay as they were.
         */
        fun withDefaults(text: String, changes: Map<String, String?>, owner: String): String {
            val lines = text.lines().toMutableList().apply { if (lastOrNull() == "") removeAt(lastIndex) }
            var start = lines.indexOfFirst { it.trim() == DEFAULTS }
            if (start < 0) {
                if (changes.values.all { it == null }) return text
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
                lines += DEFAULTS
                start = lines.lastIndex
            }
            var end = (start + 1 until lines.size).firstOrNull { lines[it].trim().startsWith("[") } ?: lines.size
            for ((key, value) in changes) {
                val at = (start + 1 until end).firstOrNull { lines[it].substringBefore('=').trim() == key && '=' in lines[it] }
                when {
                    value != null && at != null -> lines[at] = "$key=$value"
                    value != null -> {
                        // after the group's last key, before the blank line that ends it
                        var insert = end
                        while (insert > start + 1 && lines[insert - 1].isBlank()) insert--
                        lines.add(insert, "$key=$value")
                        end++
                    }
                    at != null && lines[at].substringAfter('=').split(';').firstOrNull()?.trim() == owner -> {
                        lines.removeAt(at)
                        end--
                    }
                }
            }
            return lines.joinToString("\n") + "\n"
        }

        private const val DEFAULTS = "[Default Applications]"

        /**
         * The exit code and output of [command], or null when it could not start or did not end within [timeoutSeconds] (then it
         * is killed). The output is read on its own thread so a program that keeps it open cannot hold the caller (review #12).
         */
        internal fun runCommand(command: List<String>, timeoutSeconds: Long = 15): Pair<Int, String>? {
            val p = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (_: IOException) {
                return null
            }
            val out = java.io.ByteArrayOutputStream()
            val reader = kotlin.concurrent.thread(isDaemon = true, name = "process-output") { runCatching { p.inputStream.copyTo(out) } }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                reader.join(500)
                return null
            }
            reader.join(1_000)
            return p.exitValue() to out.toString(Charsets.UTF_8)
        }
    }
}

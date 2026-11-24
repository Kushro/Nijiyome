// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import java.io.File
import java.util.Locale

/** A Chrome / Edge / Chromium-family executable the hidden browser can drive over CDP. */
data class ChromiumExecutable(val path: String, val manual: Boolean)

/**
 * Where the hidden browser of the desktop comes from (1.1, F4): the user's own Chrome, Edge or Chromium, never a bundled one.
 *
 * Order: the path set in Settings (when valid), then the usual install locations, then the usual command names on `PATH`. Flatpak and
 * Snap installs are not used in 1.1: their sandbox has its own `/tmp` and does not take the debugging pipe, so they are only reported
 * (Settings explains it and suggests the manual path).
 */
class ChromiumLocator(
    private val os: String = System.getProperty("os.name").orEmpty(),
    private val env: (String) -> String? = System::getenv,
    private val home: String = System.getProperty("user.home").orEmpty(),
    private val exists: (File) -> Boolean = { it.isFile },
    private val executable: (File) -> Boolean = { it.isFile && it.canExecute() },
    private val canonical: (File) -> String = { runCatching { it.canonicalPath }.getOrDefault(it.path) },
    private val head: (File) -> String = { f -> runCatching { f.inputStream().use { String(it.readNBytes(4096), Charsets.UTF_8) } }.getOrDefault("") },
) {
    val windows: Boolean get() = os.lowercase(Locale.ROOT).startsWith("windows")

    /** Why a path cannot be used as the hidden browser; null when it can. */
    enum class Problem { MISSING, NOT_EXECUTABLE, SANDBOXED }

    fun check(path: String): Problem? {
        val f = File(path.trim())
        if (!exists(f)) return Problem.MISSING
        if (!executable(f)) return Problem.NOT_EXECUTABLE
        if (!windows && sandboxed(f)) return Problem.SANDBOXED
        return null
    }

    /** The browser to use: [manualPath] when it passes [check], else the first one found; null = none. */
    fun find(manualPath: String?): ChromiumExecutable? {
        manualPath?.trim()?.takeIf { it.isNotEmpty() }?.let { p -> if (check(p) == null) return ChromiumExecutable(File(p).path, manual = true) }
        return candidates().firstOrNull { check(it.path) == null }?.let { ChromiumExecutable(it.path, manual = false) }
    }

    /** A Flatpak / Snap Chrome, Chromium, Edge or Brave that is installed but not used (for the message of Settings); null = none. */
    fun sandboxedInstall(): String? {
        if (windows) return null
        val flatpakDirs = listOf("/var/lib/flatpak/exports/bin", "$home/.local/share/flatpak/exports/bin")
        for (dir in flatpakDirs) for (id in FLATPAK_IDS) if (exists(File(dir, id))) return "Flatpak ($id)"
        for (name in SNAP_NAMES) if (exists(File("/snap/bin", name))) return "Snap ($name)"
        return null
    }

    private fun candidates(): List<File> {
        val out = ArrayList<File>()
        val pathDirs = env("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
        if (windows) {
            val roots = listOfNotNull(env("ProgramFiles(x86)"), env("ProgramFiles"), env("LOCALAPPDATA"))
            for (root in roots) for (rel in WINDOWS_RELATIVE) out += File(root, rel)
            for (dir in pathDirs) for (n in WINDOWS_NAMES) out += File(dir, n)
        } else {
            // the real binaries first: PATH launchers are often scripts that add the user's own flags (AUR's *-flags.conf)
            out += UNIX_FIXED.map(::File)
            for (dir in pathDirs) for (n in UNIX_NAMES) out += File(dir, n)
        }
        return out.distinctBy { it.path }
    }

    /** A Snap wrapper (`/snap/bin/x` -> `/usr/bin/snap`, or a script that execs `/snap/bin/...`) or a Flatpak export script. */
    private fun sandboxed(f: File): Boolean {
        val real = canonical(f)
        if (real.startsWith("/snap/") || real == "/usr/bin/snap" || f.path.startsWith("/snap/")) return true
        if (real.contains("/flatpak/exports/")) return true
        val text = head(f)
        return text.startsWith("#!") && (text.contains("/snap/bin/") || text.contains("flatpak run"))
    }

    companion object {
        val UNIX_NAMES = listOf(
            "google-chrome-stable", "google-chrome", "chromium", "chromium-browser", "microsoft-edge-stable", "microsoft-edge",
            "brave-browser", "brave", "vivaldi-stable", "vivaldi",
        )
        val UNIX_FIXED = listOf(
            "/opt/google/chrome/chrome", "/opt/microsoft/msedge/msedge", "/usr/lib/chromium/chromium",
            "/usr/lib/chromium-browser/chromium-browser", "/opt/brave.com/brave/brave",
        )

        // PENDIENTE-ESCRITORIO: navegador oculto en Windows sin probar (Edge / Chrome por puerto de depuración en 127.0.0.1)
        val WINDOWS_RELATIVE = listOf(
            "Microsoft\\Edge\\Application\\msedge.exe", "Google\\Chrome\\Application\\chrome.exe",
            "BraveSoftware\\Brave-Browser\\Application\\brave.exe", "Chromium\\Application\\chrome.exe",
        )
        val WINDOWS_NAMES = listOf("msedge.exe", "chrome.exe")
        val FLATPAK_IDS = listOf("com.google.Chrome", "org.chromium.Chromium", "com.microsoft.Edge", "com.brave.Browser", "io.github.ungoogled_software.ungoogled_chromium")
        val SNAP_NAMES = listOf("chromium", "brave")
    }
}

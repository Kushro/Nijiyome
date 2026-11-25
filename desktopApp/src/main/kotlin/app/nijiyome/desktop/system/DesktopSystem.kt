// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.data.DesktopSecretStore
import app.nijiyome.feature.settings.DesktopSystemControl
import app.nijiyome.feature.settings.DesktopSystemPrefs
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

/** [DesktopSystemPrefs] in `<data>/desktop-system.properties` (a missing or broken file reads as the defaults). */
class DesktopSystemPrefsStore(private val file: File) {
    fun load(): DesktopSystemPrefs {
        val p = Properties()
        runCatching { file.inputStream().use(p::load) }.onFailure { return DesktopSystemPrefs() }
        val d = DesktopSystemPrefs()
        fun flag(key: String, default: Boolean) = p.getProperty(key)?.toBooleanStrictOrNull() ?: default
        return DesktopSystemPrefs(
            trayIcon = flag("trayIcon", d.trayIcon),
            closeToTray = flag("closeToTray", d.closeToTray),
            startMinimized = flag("startMinimized", d.startMinimized),
            startWithSystem = flag("startWithSystem", d.startWithSystem),
            discordPresence = flag("discordPresence", d.discordPresence),
            nativeTitleBar = flag("nativeTitleBar", d.nativeTitleBar),
        )
    }

    fun save(prefs: DesktopSystemPrefs) {
        val p = Properties()
        p["trayIcon"] = prefs.trayIcon.toString()
        p["closeToTray"] = prefs.closeToTray.toString()
        p["startMinimized"] = prefs.startMinimized.toString()
        p["startWithSystem"] = prefs.startWithSystem.toString()
        p["discordPresence"] = prefs.discordPresence.toString()
        p["nativeTitleBar"] = prefs.nativeTitleBar.toString()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { p.store(it, "Nijiyome desktop system settings") }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/**
 * The desktop's system settings and what applies them: the autostart entry follows [DesktopSystemPrefs.startWithSystem], the
 * window code reads the tray flags, the presence code the Discord flag. Also "Restablecer la app"'s wiper for the autostart entry
 * (the reset clears the preferences file with the rest of the data directory).
 */
class DesktopSystem(
    private val store: DesktopSystemPrefsStore,
    private val autostart: Autostart,
    private val secrets: SecretStore?,
    /** v1.1 (F6): magnet links and `.torrent` files (null: not offered, as before). */
    private val linkHandlers: LinkHandlers? = null,
    override val discordConfigured: Boolean,
    trayAvailable: Boolean,
) : DesktopSystemControl, AppDataWiper {
    private val _prefs = MutableStateFlow(store.load())
    override val prefs: StateFlow<DesktopSystemPrefs> = _prefs.asStateFlow()

    private val _trayAvailable = MutableStateFlow(trayAvailable)
    override val trayAvailable: StateFlow<Boolean> = _trayAvailable.asStateFlow()

    override val autostartSupported: Boolean get() = autostart.supported

    override val linkHandlerSupported: Boolean get() = linkHandlers?.supported == true

    override fun linkHandlerEnabled(): Boolean = runCatching { linkHandlers?.isEnabled() == true }.getOrDefault(false)

    override fun setLinkHandler(enabled: Boolean): Boolean = runCatching { linkHandlers?.setEnabled(enabled) != null }
        .onFailure { System.err.println("link handler: ${it.message}") }
        .getOrDefault(false)

    override val secretsBackend: String? get() = (secrets as? DesktopSecretStore)?.backend

    override fun secretsUnreadable(): String? {
        val store = secrets as? DesktopSecretStore ?: return null
        runCatching { kotlinx.coroutines.runBlocking { store.prepare() } }
        return store.unreadableBackup
    }

    override fun dismissSecretsUnreadable() {
        (secrets as? DesktopSecretStore)?.dismissUnreadable()
    }

    /** The tray could not be shown after all (the native side failed). */
    fun trayFailed() {
        _trayAvailable.value = false
    }

    @Synchronized
    override fun update(transform: (DesktopSystemPrefs) -> DesktopSystemPrefs) {
        val before = _prefs.value
        val after = transform(before)
        if (after == before) return
        if (after.startWithSystem != before.startWithSystem) {
            runCatching { autostart.setEnabled(after.startWithSystem) }.onFailure {
                System.err.println("autostart: ${it.message}")
                return
            }
        }
        _prefs.value = after
        runCatching { store.save(after) }.onFailure { System.err.println("desktop settings: ${it.message}") }
    }

    /**
     * At start and when Ajustes › Datos opens (checklist system.autostart: the switch shows the real state): the setting follows
     * the system's entry. An entry the user removed by hand (the session's startup manager, `regedit`) turns the switch off instead
     * of coming back; an existing entry is written again so it starts this launcher (the AppImage may have moved).
     */
    @Synchronized
    override fun refreshAutostart() {
        if (!autostart.supported) return
        val real = runCatching { autostart.isEnabled() }.getOrElse { return }
        if (real) runCatching { autostart.setEnabled(true) }
        val current = _prefs.value
        if (current.startWithSystem == real) return
        val after = current.copy(startWithSystem = real)
        _prefs.value = after
        runCatching { store.save(after) }.onFailure { System.err.println("desktop settings: ${it.message}") }
    }

    /** At start: see [refreshAutostart]. */
    fun reconcileAutostart() = refreshAutostart()

    override suspend fun wipe() {
        runCatching { autostart.setEnabled(false) }
        runCatching { linkHandlers?.setEnabled(false) }
        _prefs.value = DesktopSystemPrefs()
    }

    companion object {
        /**
         * Whether a tray host answers. Windows always has the notification area. Linux needs a StatusNotifierItem watcher on the
         * session bus (KDE, GNOME with the AppIndicator extension, most panels); asked through `gdbus`, assumed present when it
         * cannot be asked.
         */
        fun trayHostPresent(windows: Boolean, run: (List<String>) -> String? = ::output): Boolean {
            if (windows) return true
            val answer = run(
                listOf(
                    "gdbus", "call", "--session", "--dest", "org.freedesktop.DBus", "--object-path", "/org/freedesktop/DBus",
                    "--method", "org.freedesktop.DBus.NameHasOwner", "org.kde.StatusNotifierWatcher",
                ),
            ) ?: return true
            return answer.contains("true")
        }

        private fun output(command: List<String>): String? = runCatching {
            val p = ProcessBuilder(command).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                null
            } else if (p.exitValue() == 0) {
                text
            } else {
                "false"
            }
        }.getOrNull()
    }
}

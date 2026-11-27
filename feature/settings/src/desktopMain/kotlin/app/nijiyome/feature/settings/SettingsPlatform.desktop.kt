// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_accounts_no_browser
import app.nijiyome.feature.settings.resources.settings_accounts_port_busy
import app.nijiyome.feature.settings.resources.settings_data_hint_desktop
import app.nijiyome.feature.settings.resources.settings_data_open_folder
import app.nijiyome.feature.settings.resources.settings_data_open_folder_action
import app.nijiyome.feature.settings.resources.settings_data_open_folder_hint
import app.nijiyome.model.AppSettings
import app.nijiyome.model.LoadState
import app.nijiyome.ui.platform.rememberOpenUrl
import kotlinx.coroutines.launch

// The desktop side of SettingsPlatform.kt. Each pending marker below is a capability desktop does not have yet.

/** Desktop has no wallpaper colors: the switch is hidden, the palette applies. */
internal actual val systemCategoryOffered: Boolean = true

internal actual val dynamicColorSupported: Boolean = false

/** Android packages of external players: desktop has none (its External engine is mpv, see ExternalPlayerSettings.desktop.kt). */
@Composable
internal actual fun rememberInstalledPackages(packages: Set<String>, key: Any?): Set<String> = emptySet()

/**
 * Koin properties the desktop app sets at start (`desktopApp` Main.kt): the packaged version and the data directory. A development
 * run without a packaged version shows none.
 */
object DesktopAppProperties {
    const val VERSION = "nijiyome.app.version"
    const val DATA_DIR = "nijiyome.app.dataDir"
}

/** The packaged version (`gradle.properties` or the release tag) and the application id. */
@Composable
internal actual fun rememberAppVersionParts(): List<String> {
    val koin = org.koin.compose.getKoin()
    return remember(koin) { listOf(koin.getProperty<String>(DesktopAppProperties.VERSION).orEmpty(), "app.nijiyome") }
}

/** Versions of what the desktop app bundles (technical strings, not translated). */
internal actual val componentVersions: List<Pair<String, String>> = listOf(
    "Compose Multiplatform" to "1.12.2",
    "libmpv" to "MediaMP 0.5.3",
    "libtorrent4j" to "2.1.0-39",
    "QuickJS" to "quickjs-kt 1.0.18",
    "OkHttp" to "5.5.1",
    "Java" to (System.getProperty("java.runtime.version") ?: System.getProperty("java.version").orEmpty()),
    "Nijiyome Link" to "v1 · openapi.v1.json",
)

/** Ajustes › Datos on desktop: the real data directory instead of Android's app folder. */
@Composable
internal actual fun dataLocationHint(): String {
    val koin = org.koin.compose.getKoin()
    val dir = remember(koin) { koin.getProperty<String>(DesktopAppProperties.DATA_DIR).orEmpty() }
    return org.jetbrains.compose.resources.stringResource(Res.string.settings_data_hint_desktop, dir)
}

/**
 * Acerca de › Licencias: `THIRD_PARTY_NOTICES.md` and the OFL texts of the bundled fonts, packaged by `desktopApp` under
 * `licenses/` on the classpath (listed in `licenses/index.txt`, a jar cannot be listed), in the same paragraphs as Android.
 */
@Composable
internal actual fun rememberLicensesLoader(): suspend () -> LoadState<List<String>> =
    remember { { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadBundledLicenses() } } }

/** Reads the bundled notices through [read] (a classpath resource by default); Ok(empty) when none is bundled. */
internal fun loadBundledLicenses(read: (String) -> String? = ::classpathText): LoadState<List<String>> {
    val names = read("licenses/index.txt")?.lines()?.map(String::trim)?.filter(String::isNotEmpty) ?: return LoadState.Ok(emptyList())
    // Android's order: the notices first, then the font licenses by name.
    val ordered = names.filter { it == "THIRD_PARTY_NOTICES.md" } + names.filter { it.startsWith("OFL-") }.sorted()
    val paragraphs = ordered.flatMap { name ->
        val text = read("licenses/$name") ?: return@flatMap emptyList()
        listOf("── $name ──") + text.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotEmpty)
    }
    return LoadState.Ok(paragraphs)
}

private fun classpathText(path: String): String? =
    DesktopAppProperties::class.java.classLoader?.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

/** Bound by the desktop app: closes the window and starts the app again (after "Restablecer la app"). */
fun interface DesktopAppRestart {
    fun restart()
}

/** After "Restablecer la app": the desktop app closes and starts again, landing on the first-run wizard. */
@Composable
internal actual fun rememberRestartAfterReset(): () -> Unit {
    val koin = org.koin.compose.getKoin()
    val restart = remember(koin) { koin.getOrNull<DesktopAppRestart>() }
    return remember(restart) { { restart?.restart() ?: Unit } }
}

/**
 * "Exportar" of Listas y cuentas and Datos › Historial: a "Guardar como" dialog (Swing's chooser, modal over the window) with the
 * suggested name, then the file written. Cancelling says nothing, as on Android.
 */
@Composable
internal actual fun rememberSaveJsonDocument(onDone: suspend (Boolean) -> Unit): (fileName: String, json: String) -> Unit {
    val scope = rememberCoroutineScope()
    val done = rememberUpdatedState(onDone)
    return remember(scope) {
        { fileName, json ->
            chooseSaveFile(fileName) { target ->
                if (target != null) {
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { writeExport(target, json) }
                        done.value(ok)
                    }
                }
            }
        }
    }
}

/** Writes [json] to [target] through a temporary file next to it, so a failed write never leaves half a file. */
internal fun writeExport(target: java.io.File, json: String): Boolean = runCatching {
    val tmp = java.io.File(target.absoluteFile.parentFile, ".${target.name}.tmp")
    tmp.writeText(json, Charsets.UTF_8)
    java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    true
}.getOrDefault(false)

/** A chosen name without an extension gets [extension] (`.json`, or `.csv` for the CSV list export). */
internal fun withJsonExtension(file: java.io.File, extension: String = "json"): java.io.File =
    if (file.name.contains('.')) file else java.io.File(file.parentFile, "${file.name}.$extension")

private fun chooseSaveFile(suggested: String, onResult: (java.io.File?) -> Unit) {
    val show = {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            onResult(null)
        } else {
            val extension = suggested.substringAfterLast('.', "json").lowercase()
            val chooser = javax.swing.JFileChooser().apply {
                dialogType = javax.swing.JFileChooser.SAVE_DIALOG
                selectedFile = java.io.File(currentDirectory, suggested)
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("*.$extension", extension)
            }
            val parent = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            val answer = chooser.showSaveDialog(parent)
            onResult(chooser.selectedFile?.takeIf { answer == javax.swing.JFileChooser.APPROVE_OPTION }?.let { withJsonExtension(it, extension) })
        }
    }
    if (javax.swing.SwingUtilities.isEventDispatchThread()) show() else javax.swing.SwingUtilities.invokeLater(show)
}

@Composable
internal actual fun rememberOpenReleaseUrl(): (url: String) -> Unit = rememberOpenUrl()

/** "Compartir" on desktop: shows the log file in the system file manager, to attach it to a bug report. */
@Composable
internal actual fun rememberShareLog(system: SystemViewModel): () -> Boolean = remember(system) {
    {
        val file = system.logFile()
        file != null && runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file) else desktop.open(file.parentFile)
        }.isSuccess
    }
}

/** Desktop login: the loopback listener of `DesktopOAuthLauncher`, then the system browser. */
@Composable
internal actual fun rememberOpenAuthUrl(): (url: String) -> Boolean {
    val launcher = org.koin.compose.koinInject<app.nijiyome.data.DesktopOAuthLauncher>()
    return remember(launcher) { { url -> launcher.open(url) } }
}

/** v1.1 (F6): MyAnimeList XML, AniList dumps and Tatakai Next's list file besides Nijiyome's JSON; JSON or CSV out. */
internal actual val listFileFormatsOffered: Boolean = true

/**
 * "Importar" of Listas y cuentas: the file picker (JSON or a MyAnimeList XML export), then the file read here (a list export is
 * small; anything over 8 MiB is refused).
 */
@Composable
internal actual fun rememberOpenJsonDocument(onResult: suspend (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val result = rememberUpdatedState(onResult)
    return app.nijiyome.ui.platform.rememberFilePicker(listOf("application/json", "application/xml", "text/plain"), multiple = false) { picked ->
        val path = picked.firstOrNull() ?: return@rememberFilePicker
        scope.launch {
            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val file = if (path.startsWith("file:")) java.io.File(java.net.URI(path)) else java.io.File(path)
                    file.takeIf { it.length() <= MAX_IMPORT_BYTES }?.readText()
                }.getOrNull()
            }
            result.value(json)
        }
    }
}

private const val MAX_IMPORT_BYTES = 8L * 1024 * 1024

/** Desktop has no runtime permission for notifications: the wizard has no permissions step. */
internal actual val notificationsNeedPermission: Boolean = false

@Composable
internal actual fun OnboardingNotifications() = Unit

// FrameGenSettingsSlot (lsfg-vk on the external mpv) and the External engine rows are in ExternalPlayerSettings.desktop.kt.

/** Ajustes › Datos: "Abrir carpeta" on the data folder (database, settings, logs, sealed tokens), as Tatakai Next's `open_data_folder`. */
@Composable
internal actual fun DataFolderRow() {
    val koin = org.koin.compose.getKoin()
    val dir = remember(koin) { koin.getProperty<String>(DesktopAppProperties.DATA_DIR).orEmpty() }
    val open = app.nijiyome.ui.platform.rememberOpenFolder()
    if (dir.isEmpty() || open == null) return
    SettingRow(
        org.jetbrains.compose.resources.stringResource(Res.string.settings_data_open_folder),
        org.jetbrains.compose.resources.stringResource(Res.string.settings_data_open_folder_hint),
    ) {
        androidx.compose.material3.OutlinedButton(onClick = { open(dir) }) {
            androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(Res.string.settings_data_open_folder_action))
        }
    }
}

/** Tatakai Next's danger zone: type the word, and choose whether the torrents go with their files. */
internal actual val resetNeedsTypedWord: Boolean = true

/** Tatakai Next's metadata cache row: size, "Vaciar" and the TTL. */
internal actual val metadataCacheClearOffered: Boolean = true

internal actual val upscaleOnPhone: Boolean = false

/** Why the loopback login could not open: the port is taken (another app holds it) or no browser answered. */
@Composable
internal actual fun rememberAuthOpenFailureText(): suspend () -> String {
    val launcher = org.koin.compose.koinInject<app.nijiyome.data.DesktopOAuthLauncher>()
    return remember(launcher) {
        {
            if (launcher.lastFailure == app.nijiyome.data.DesktopOAuthLauncher.Failure.PORT_BUSY) {
                org.jetbrains.compose.resources.getString(Res.string.settings_accounts_port_busy, launcher.port)
            } else {
                org.jetbrains.compose.resources.getString(Res.string.settings_accounts_no_browser)
            }
        }
    }
}


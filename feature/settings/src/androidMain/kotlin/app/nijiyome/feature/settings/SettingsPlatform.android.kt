// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_accounts_no_browser
import app.nijiyome.feature.settings.resources.settings_logs_share
import app.nijiyome.feature.settings.resources.settings_data_hint
import app.nijiyome.feature.settings.resources.settings_onboarding_notifications
import app.nijiyome.feature.settings.resources.settings_onboarding_notifications_allow
import app.nijiyome.feature.settings.resources.settings_onboarding_notifications_denied
import app.nijiyome.feature.settings.resources.settings_onboarding_notifications_granted
import app.nijiyome.feature.settings.resources.settings_onboarding_notifications_hint
import app.nijiyome.model.AppError
import app.nijiyome.model.AppSettings
import app.nijiyome.model.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal actual val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Visible to the app through the manifest `<queries>` (app module). */
private fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getApplicationInfo(pkg, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

@Composable
internal actual fun rememberInstalledPackages(packages: Set<String>, key: Any?): Set<String> {
    val context = LocalContext.current
    return remember(key) { packages.filter { isPackageInstalled(context, it) }.toSet() }
}

@Composable
internal actual fun rememberAppVersionParts(): List<String> {
    val context = LocalContext.current
    val version = produceState(initialValue = "") {
        value = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }.value
    return listOf(version, context.packageName)
}

/**
 * THIRD_PARTY_NOTICES.md (copied into the app assets at `licenses/THIRD_PARTY_NOTICES.md` by :app's build) followed by the OFL texts
 * of the bundled fonts (`licenses/OFL-*.txt`, shipped by :core:designsystem).
 */
@Composable
internal actual fun rememberLicensesLoader(): suspend () -> LoadState<List<String>> {
    val context = LocalContext.current
    return remember(context) { { withContext(Dispatchers.IO) { loadLicenses(context) } } }
}

private fun loadLicenses(context: Context): LoadState<List<String>> = try {
    val assets = context.assets
    val files = buildList {
        if (assets.list("licenses")?.contains("THIRD_PARTY_NOTICES.md") == true) add("licenses/THIRD_PARTY_NOTICES.md")
        assets.list("licenses")?.filter { it.startsWith("OFL-") }?.sorted()?.forEach { add("licenses/$it") }
    }
    val paragraphs = files.flatMap { path ->
        val text = assets.open(path).bufferedReader().use { it.readText() }
        listOf("── ${path.substringAfterLast('/')} ──") + text.split(Regex("\\n\\s*\\n")).map(String::trim).filter(String::isNotEmpty)
    }
    LoadState.Ok(paragraphs)
} catch (e: java.io.IOException) {
    LoadState.Err(AppError.Unknown(e.message ?: "IOException"))
}

@Composable
internal actual fun rememberRestartAfterReset(): () -> Unit {
    val context = LocalContext.current
    // parity wave A (worker q, system.reset-app): recreate the activity; the shell shows the first-run wizard again
    return remember(context) { { (context as? Activity)?.recreate() } }
}

@Composable
internal actual fun rememberSaveJsonDocument(onDone: suspend (Boolean) -> Unit): (fileName: String, json: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false)
            }
            done(ok)
        }
    }
    return { fileName, json ->
        pendingExport = json
        launcher.launch(fileName)
    }
}

@Composable
internal actual fun rememberOpenJsonDocument(onResult: suspend (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // a list export is small; refuse anything absurd instead of reading it into memory
                        val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                        if (bytes.size > MAX_IMPORT_BYTES) null else String(bytes)
                    }
                }.getOrNull()
            }
            result(json)
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}

/** `InputStream.readNBytes` is API 33+; this reads at most [max] bytes on every API level. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < max) {
        val n = read(buf, 0, minOf(buf.size, max - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

@Composable
internal actual fun rememberOpenAuthUrl(): (url: String) -> Boolean {
    val context = LocalContext.current
    return remember(context) { { url -> context.openAuthUrl(url) } }
}

/** Custom Tab first (OAuth must not run in a WebView), any browser second. False when nothing can open it. */
private fun Context.openAuthUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        true
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e2: ActivityNotFoundException) {
            false
        }
    }
}

@Composable
internal actual fun rememberOpenReleaseUrl(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { url -> context.openUrl(url) } }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // no browser: nothing to do, the version is already shown
    }
}

@Composable
internal actual fun rememberShareLog(system: SystemViewModel): () -> Boolean {
    val context = LocalContext.current
    val chooserTitle = stringResource(Res.string.settings_logs_share)
    return { context.shareLog(system, chooserTitle) }
}

/** Shares `filesDir/logs/app.log` through the `${applicationId}.logs` FileProvider declared by :core:data. */
private fun Context.shareLog(system: SystemViewModel, chooserTitle: String): Boolean {
    val file = system.logFile() ?: return false
    return try {
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Nijiyome log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: ActivityNotFoundException) {
        false
    }
}

internal actual val notificationsNeedPermission: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Android's wizard keeps its steps. */
internal actual val platformOnboardingSteps: Set<OnboardingStep> = emptySet()

@Composable
internal actual fun OnboardingPlatformStep(step: OnboardingStep) = Unit

/** Android keeps its JSON list export and import only. */
internal actual val listFileFormatsOffered: Boolean = false

/**
 * Notifications (Android 13+): torrent downloads in the background and the playback controls. Asking is optional; a denial only says
 * where to turn them on later (the torrent client asks again when background downloads are enabled).
 */
@Composable
internal actual fun OnboardingNotifications() {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok || granted()
        asked = true
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_onboarding_notifications), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        when {
                            allowed -> Res.string.settings_onboarding_notifications_granted
                            asked -> Res.string.settings_onboarding_notifications_denied
                            else -> Res.string.settings_onboarding_notifications_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (allowed) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                FilledTonalButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(Res.string.settings_onboarding_notifications_allow))
                }
            }
        }
    }
}

@Composable
internal actual fun FrameGenSettingsSlot() = FrameGenSettingsSection() // 0.4.0 (worker B): phone frame generation, its own file

internal actual val extraPresets: List<String> = emptyList() // v1.1 (F2): the same presets as before

@Composable
internal actual fun DecoderStatusSlot() = Unit // v1.1 (F2): desktop only; Android shows what it showed before

/** Versions of what is bundled (technical strings, not translated). */
internal actual val componentVersions: List<Pair<String, String>> = listOf(
    "Media3" to "1.11.1",
    "libmpv" to "1.1.0 (dev.jdtech.mpv)",
    "libtorrent4j" to "2.1.0-39",
    "Nijiyome Link" to "v1 · openapi.v1.json",
)

@Composable
internal actual fun dataLocationHint(): String = org.jetbrains.compose.resources.stringResource(Res.string.settings_data_hint)

/** Android has no tray, autostart or Discord rows. */
@Composable
internal actual fun PlatformSystemRows() = Unit

/** Hito 6 (D1): the data folder row is desktop's; Android keeps Ajustes › Datos as it was. */
@Composable
internal actual fun DataFolderRow() = Unit

/** Hito 6 (D1): Android keeps its one-button confirmation dialog. */
internal actual val resetNeedsTypedWord: Boolean = false

/** Hito 6 (D1): Android's metadata cache row shows only the size, as before. */
internal actual val metadataCacheClearOffered: Boolean = false

/** Hito 6 (D1): the text Android always showed when no browser could open the sign-in. */
@Composable
internal actual fun rememberAuthOpenFailureText(): suspend () -> String =
    remember { { org.jetbrains.compose.resources.getString(Res.string.settings_accounts_no_browser) } }

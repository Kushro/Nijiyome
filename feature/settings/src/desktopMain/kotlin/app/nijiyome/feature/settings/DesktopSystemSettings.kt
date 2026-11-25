// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_system_autostart
import app.nijiyome.feature.settings.resources.settings_system_autostart_hint
import app.nijiyome.feature.settings.resources.settings_system_close_to_tray
import app.nijiyome.feature.settings.resources.settings_system_close_to_tray_hint
import app.nijiyome.feature.settings.resources.settings_system_discord
import app.nijiyome.feature.settings.resources.settings_system_discord_hint
import app.nijiyome.feature.settings.resources.settings_system_discord_unconfigured
import app.nijiyome.feature.settings.resources.settings_system_link_handler
import app.nijiyome.feature.settings.resources.settings_system_link_handler_hint
import app.nijiyome.feature.settings.resources.settings_system_secrets_file
import app.nijiyome.feature.settings.resources.settings_system_secrets_unreadable
import app.nijiyome.feature.settings.resources.settings_system_secrets_unreadable_hint
import app.nijiyome.feature.settings.resources.settings_system_secrets_unreadable_ok
import app.nijiyome.feature.settings.resources.settings_system_start_minimized
import app.nijiyome.feature.settings.resources.settings_system_start_minimized_hint
import app.nijiyome.feature.settings.resources.settings_system_tray
import app.nijiyome.feature.settings.resources.settings_system_tray_hint
import app.nijiyome.feature.settings.resources.settings_system_tray_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/** The desktop-only system preferences (Tatakai Next's `SystemPrefs` / `flow.discord_presence`, same defaults). */
data class DesktopSystemPrefs(
    /** Icon in the system tray with Show and Quit. */
    val trayIcon: Boolean = true,
    /** The close button hides the window to the tray instead of quitting (only with [trayIcon] and a working tray). */
    val closeToTray: Boolean = false,
    /** Started by the system (autostart entry): only the tray icon appears. */
    val startMinimized: Boolean = false,
    /** Start with the system (XDG autostart on Linux, the `Run` key on Windows). */
    val startWithSystem: Boolean = false,
    /** Discord Rich Presence: what is being watched, over Discord's local IPC. */
    val discordPresence: Boolean = false,
    /** Use the OS native window title bar instead of the custom frameless title bar (requires restart). */
    val nativeTitleBar: Boolean = false,
)

/**
 * What the desktop app gives Settings for its system rows (bound by `desktopApp`). [update] applies a change and its side effect
 * (the autostart entry is created / removed, the tray icon appears / goes, the presence clears).
 */
interface DesktopSystemControl {
    val prefs: StateFlow<DesktopSystemPrefs>

    fun update(transform: (DesktopSystemPrefs) -> DesktopSystemPrefs)

    /** A tray host answered (StatusNotifierItem on Linux, the notification area on Windows). */
    val trayAvailable: StateFlow<Boolean>

    /** This build can register itself to start with the system (a packaged app, not a development run). */
    val autostartSupported: Boolean

    /** A Discord application id is configured for this build. */
    val discordConfigured: Boolean

    /** Where the secrets key is kept: "secret-service", "dpapi", "file" (no keyring) or null when not used yet. */
    val secretsBackend: String?

    /**
     * 1.1.2: opens the secret store if nothing did yet and answers the name of the `secrets.bin.unreadable-<date>` file set aside
     * because no key opened it (the notice "link your accounts again"), or null. Blocking.
     */
    fun secretsUnreadable(): String? = null

    /** Dismisses that notice (the set-aside file stays). */
    fun dismissSecretsUnreadable() {}

    /** Reads the system's autostart entry back into [prefs] (the switch shows the real state, not only the saved one). */
    fun refreshAutostart() {}

    /** v1.1 (F6): this build can open `magnet:` links and `.torrent` files from the system (a packaged app). */
    val linkHandlerSupported: Boolean get() = false

    /** Whether the system opens magnet links with this app now (read from the system, blocking). */
    fun linkHandlerEnabled(): Boolean = false

    /** Takes (true) or gives back (false) magnet links and `.torrent` files; false when the system refused. Blocking. */
    fun setLinkHandler(enabled: Boolean): Boolean = false
}

@Composable
internal actual fun PlatformSystemRows() {
    val koin = getKoin()
    val control = remember(koin) { koin.getOrNull<DesktopSystemControl>() } ?: return
    val prefs by control.prefs.collectAsState()
    val trayAvailable by control.trayAvailable.collectAsState()
    SwitchRow(
        label = stringResource(Res.string.settings_system_tray),
        hint = stringResource(if (trayAvailable || !prefs.trayIcon) Res.string.settings_system_tray_hint else Res.string.settings_system_tray_unavailable),
        checked = prefs.trayIcon,
        onChange = { on -> control.update { it.copy(trayIcon = on) } },
    )
    SwitchRow(
        label = stringResource(Res.string.settings_system_close_to_tray),
        hint = stringResource(Res.string.settings_system_close_to_tray_hint),
        checked = prefs.closeToTray,
        enabled = prefs.trayIcon && trayAvailable,
        onChange = { on -> control.update { it.copy(closeToTray = on) } },
    )
    if (control.autostartSupported) {
        // The entry may have been removed (or added) outside the app since the last look.
        LaunchedEffect(control) { withContext(Dispatchers.IO) { control.refreshAutostart() } }
        SwitchRow(
            label = stringResource(Res.string.settings_system_autostart),
            hint = stringResource(Res.string.settings_system_autostart_hint),
            checked = prefs.startWithSystem,
            onChange = { on -> control.update { it.copy(startWithSystem = on) } },
        )
        SwitchRow(
            label = stringResource(Res.string.settings_system_start_minimized),
            hint = stringResource(Res.string.settings_system_start_minimized_hint),
            checked = prefs.startMinimized,
            enabled = prefs.startWithSystem && prefs.trayIcon && trayAvailable,
            onChange = { on -> control.update { it.copy(startMinimized = on) } },
        )
    }
    SwitchRow(
        label = stringResource(Res.string.settings_system_discord),
        hint = stringResource(if (control.discordConfigured) Res.string.settings_system_discord_hint else Res.string.settings_system_discord_unconfigured),
        checked = prefs.discordPresence && control.discordConfigured,
        enabled = control.discordConfigured,
        onChange = { on -> control.update { it.copy(discordPresence = on) } },
    )
    SwitchRow(
        label = "Barra de título nativa del sistema",
        hint = "Usa las decoraciones de ventana del sistema operativo (aplica al reiniciar).",
        checked = prefs.nativeTitleBar,
        onChange = { on -> control.update { it.copy(nativeTitleBar = on) } },
    )
    if (control.linkHandlerSupported) LinkHandlerRow(control)
    if (control.secretsBackend == "file") SectionHint(stringResource(Res.string.settings_system_secrets_file))
    SecretsUnreadableNotice(control)
}

/** 1.1.2: "The saved secrets could not be opened; link your accounts again", until dismissed. */
@Composable
private fun SecretsUnreadableNotice(control: DesktopSystemControl) {
    var backup by remember(control) { mutableStateOf<String?>(null) }
    LaunchedEffect(control) { backup = withContext(Dispatchers.IO) { control.secretsUnreadable() } }
    val name = backup ?: return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                Text(stringResource(Res.string.settings_system_secrets_unreadable), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(Res.string.settings_system_secrets_unreadable_hint, name), style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = {
                    control.dismissSecretsUnreadable()
                    backup = null
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) { Text(stringResource(Res.string.settings_system_secrets_unreadable_ok)) }
        }
    }
}

/** v1.1 (F6): "Abrir enlaces magnet con Nijiyome", showing the system's real state (null while it is read). */
@Composable
private fun LinkHandlerRow(control: DesktopSystemControl) {
    var on by remember(control) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(control) { on = withContext(Dispatchers.IO) { control.linkHandlerEnabled() } }
    SwitchRow(
        label = stringResource(Res.string.settings_system_link_handler),
        hint = stringResource(Res.string.settings_system_link_handler_hint),
        checked = on == true,
        enabled = on != null,
        onChange = { wanted ->
            on = null
            scope.launch {
                on = withContext(Dispatchers.IO) {
                    control.setLinkHandler(wanted)
                    control.linkHandlerEnabled()
                }
            }
        },
    )
}

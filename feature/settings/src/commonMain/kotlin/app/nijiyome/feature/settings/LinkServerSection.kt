// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.designsystem.components.focusOnOpen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.ExtPasswordResult
import app.nijiyome.model.ExtensionServerLogEntry
import app.nijiyome.model.FirewallResult
import app.nijiyome.model.LinkDeviceHistoryMode
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerPorts
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.LinkServerDevice
import app.nijiyome.model.QrMatrix
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.patterns.FolderPathPicker
import app.nijiyome.ui.patterns.PortCheck
import app.nijiyome.ui.patterns.PortPicker
import app.nijiyome.designsystem.theme.NijiyomeFonts
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/**
 * Settings › Nijiyome Link: the phone's client rows on Android ([LinkSection]); on desktop, where a [LinkServerController] is
 * registered, the server's name and the way to the Link screen ([LinkServerSummary]).
 */
@Composable
internal fun LinkSectionForPlatform(state: SettingsUiState, vm: SettingsViewModel, onOpenLinked: () -> Unit) {
    val koin = getKoin()
    val server = remember(koin) { koin.getOrNull<LinkServerController>() }
    if (server != null) {
        // The rows behind these ids moved to the Link screen: a jump to one opens that screen instead of a missing row.
        val jump = LocalSettingsJump.current
        LaunchedEffect(jump?.targetId, jump?.serial) {
            if (jump != null && jump.targetId?.let { it in MOVED_TO_LINK_SCREEN } == true) {
                jump.consume()
                onOpenLinked()
            }
        }
        SettingAnchor("link_pairing") { LinkServerSummary(server, onOpenLinked) }
    } else {
        SettingAnchor("link_pairing") { LinkSection(state, vm, onOpenLinked) }
    }
}

/** Catalog ids of desktop Link rows that live on the Link screen now. */
private val MOVED_TO_LINK_SCREEN = setOf("link_cache", "link_ports", "link_ext_server")

/** The desktop network controller (proxy, FlareSolverr, media proxy), or null where none is bound (Android). */
@Composable
internal fun rememberNetworkController(): app.nijiyome.model.NetworkSettingsController? {
    val koin = getKoin()
    return remember(koin) { koin.getOrNull<app.nijiyome.model.NetworkSettingsController>() }
}

/** Pairing codes: a mono, tabular face whatever display face the direction brings (some draw headlines with a brush). */
@Composable
internal fun codeTextStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontFamily = NijiyomeFonts.jetBrainsMono,
    fontFeatureSettings = "tnum",
    letterSpacing = 2.sp,
)

/**
 * Settings › Nijiyome Link on desktop: only the name phones see and the way to the Link screen, which has the rest (turn the
 * server on, pairing, devices, ports, cache, library folder, firewall, the extension server and its log).
 */
@Composable
internal fun LinkServerSummary(server: LinkServerController, onOpenLinked: () -> Unit) {
    val st by server.state.collectAsState()
    SectionHint(stringResource(Res.string.rd_s10_link_moved))
    val status = when {
        st.starting -> stringResource(Res.string.settings_linksrv_starting)
        st.error != null -> stringResource(Res.string.settings_linksrv_error_other)
        st.running -> stringResource(Res.string.settings_linksrv_running, st.addresses.ifEmpty { listOf(":${st.port}") }.joinToString(", "))
        else -> stringResource(Res.string.settings_linksrv_stopped)
    }
    Text(status, style = MaterialTheme.typography.bodyMedium, color = if (st.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    TextSetting(
        label = stringResource(Res.string.settings_linksrv_name),
        hint = stringResource(Res.string.settings_linksrv_name_hint, st.defaultServerName),
        value = st.serverName,
        onSave = server::setServerName,
    )
    FilledTonalButton(onClick = onOpenLinked, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(Res.string.rd_s10_link_open_screen))
    }
}

/** `hace 2 min · source_failed · /resolve · Toko · 3 · 120 ms · timeout`, with the parts the entry has. Pure. */
internal fun logLine(e: ExtensionServerLogEntry, time: String): String = listOfNotNull(
    time,
    e.event,
    e.route,
    e.name,
    e.count?.toString(),
    e.durationMs?.let { "$it ms" },
    e.error,
).joinToString(" · ")

/** A text the server re-announces when it changes (the name phones see): draft, then the explicit bar. */
@Composable
private fun TextSetting(label: String, hint: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    SettingRow(label = label, hint = hint, stack = true) {
        OutlinedTextField(value = text, onValueChange = { text = it.take(64) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CommitBar(dirty = text != value, busy = false, canApply = text.isNotBlank(), onDiscard = { text = value }, onApply = { onSave(text.trim()) })
    }
}

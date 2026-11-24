// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.link

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LinkStatusAvatar
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.DiscoveredPc
import app.nijiyome.model.LinkCapabilities
import app.nijiyome.model.LinkHandoff
import app.nijiyome.model.RemoteAction
import app.nijiyome.model.LinkProfile
import app.nijiyome.model.LinkProfileFallback
import app.nijiyome.model.ProfileSupport
import app.nijiyome.model.LinkState
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.platform.rememberNotificationPermission
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// android wave contract (docs/redesign/12 §3.13). Owner: worker b. Name and signature FIXED.

/**
 * "Dispositivo enlazado" (prototype android.js `S.linked`): unpaired -> EmptyState + scan QR (CameraX + zxing, QrScanner.kt) / paste
 * code; paired -> status card (connected / unreachable with error + retry), PC capabilities, enhance profile list
 * (LinkRepository.setPreferredProfile), remote control of the PC player (wave B: GET /v1/player every 2 s, POST /v1/control),
 * "Continuar en la PC", PCs found over mDNS, the "Reproducir aquí" card of a PC -> phone hand-off, forget dialog. Replaces LinkScreen
 * for Route.Link and Route.Linked.
 *
 * The "enhance a stream" card of the former LinkScreen is kept below the remote control (it is how [onPlay] starts a PC session).
 */
@Composable
fun LinkedDeviceScreen(
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    /** docs/redesign/13 §3.10: opens the PC extension server screen; null hides its entry card. */
    onOpenExtensionServers: (() -> Unit)? = null,
) {
    val viewModel: LinkViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnPlay by rememberUpdatedState(onPlay)
    val snackbar = LocalAppSnackbar.current
    val handedOff = stringResource(Res.string.linked_continue_done)
    val failedPrefix = stringResource(Res.string.linked_action_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LinkEvent.NavigateToPlayer -> currentOnPlay(event.args)
                LinkEvent.HandedOff -> snackbar.show(handedOff)
                is LinkEvent.Failed -> snackbar.show(failedPrefix.format(linkErrorText(event.error)))
            }
        }
    }
    // parity wave B: the "Enviar a dispositivo" notification needs POST_NOTIFICATIONS; asked here once a PC is paired (16 §0.12).
    val notifyPermission = rememberNotificationPermission()
    val paired = state.link is LinkState.Paired
    LaunchedEffect(paired) {
        if (paired && !notifyPermission.isGranted()) notifyPermission.request()
    }
    // parity wave B: GET /v1/player every 2 s while this screen is visible (link.remote-control).
    LaunchedEffect(viewModel, paired) { if (paired) viewModel.pollRemote() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.linked_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.linked_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.pendingHandoff?.let { h ->
                    HandoffCard(h, onPlay = viewModel::playHandoff, onDismiss = viewModel::dismissHandoff)
                }
                if (onOpenExtensionServers != null) ExtensionServerCard(onOpen = onOpenExtensionServers)
                when (val link = state.link) {
                    LinkState.Unpaired, LinkState.Pairing -> {
                        UnpairedContent(
                            pairing = state.pairing || link == LinkState.Pairing,
                            error = state.pairError?.userMessage(),
                            isValid = viewModel::isValidPairingText,
                            onPair = viewModel::pair,
                        )
                        DiscoveredPanel(
                            pcs = LinkLogic.others(state.discovered, null, null),
                            pairing = state.pairing || link == LinkState.Pairing,
                            enabled = viewModel.canPairDiscovered,
                            fingerprintOf = viewModel::fingerprintOf,
                            onPair = viewModel::pairDiscovered,
                        )
                    }
                    is LinkState.Failed -> {
                        ErrorState(
                            message = link.message,
                            retryLabel = stringResource(UiR.string.ui_retry),
                            onRetry = viewModel::retryCapabilities,
                            title = stringResource(Res.string.linked_problem),
                        )
                        ForgetButton(onForget = viewModel::forget)
                    }
                    is LinkState.Paired -> PairedContent(
                        state = state,
                        paired = link,
                        onRetry = viewModel::retryCapabilities,
                        onSelectProfile = viewModel::selectProfile,
                        onForget = viewModel::forget,
                        onStart = viewModel::startSession,
                        onContinue = viewModel::continueOnPc,
                        onRemote = viewModel::remote,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- unpaired

@Composable
private fun UnpairedContent(pairing: Boolean, error: String?, isValid: (String) -> Boolean, onPair: (String) -> Unit) {
    var scanning by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    val permission = rememberCameraPermission { granted ->
        denied = !granted
        scanning = granted
    }

    EmptyState(
        icon = Icons.Outlined.Computer,
        title = stringResource(Res.string.linked_none),
        message = stringResource(Res.string.linked_none_body),
    )
    Panel(icon = Icons.Outlined.QrCode, title = stringResource(Res.string.linked_pair_title)) {
        if (scanning) {
            QrScanner(
                onResult = {
                    scanning = false
                    onPair(it)
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large),
            )
            TextButton(onClick = { scanning = false }) { Text(stringResource(Res.string.linked_scan_cancel)) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (permission.isGranted()) scanning = true else permission.request()
                    },
                    enabled = !pairing,
                ) {
                    if (pairing) {
                        LoadingIndicator(Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Text(
                        stringResource(if (pairing) Res.string.linked_pairing else Res.string.linked_scan),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                FilledTonalButton(onClick = { pasting = true }, enabled = !pairing) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.linked_paste), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (denied) {
            Text(
                stringResource(Res.string.linked_camera_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Hint(stringResource(Res.string.linked_camera))
    }
    if (pasting) {
        PasteCodeDialog(
            isValid = isValid,
            onDismiss = { pasting = false },
            onPair = {
                pasting = false
                onPair(it)
            },
        )
    }
}

@Composable
private fun PasteCodeDialog(isValid: (String) -> Boolean, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val invalid = showError && !isValid(code)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
        title = { Text(stringResource(Res.string.linked_paste)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.linked_paste_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        showError = false
                    },
                    label = { Text(stringResource(Res.string.linked_code_label)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(Res.string.linked_paste_invalid)) }
                    } else {
                        null
                    },
                    textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid(code)) onPair(code.trim()) else showError = true },
                enabled = code.isNotBlank(),
            ) { Text(stringResource(Res.string.linked_pair)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

// ---------------------------------------------------------------- paired

@Composable
private fun PairedContent(
    state: LinkUiState,
    paired: LinkState.Paired,
    onRetry: () -> Unit,
    onSelectProfile: (String?) -> Unit,
    onForget: () -> Unit,
    onStart: (String) -> Unit,
    onContinue: () -> Unit,
    onRemote: (RemoteAction) -> Unit,
) {
    StatusCard(state, paired, onRetry = onRetry, onContinue = onContinue, onForget = onForget)
    CapabilitiesPanel(state, onRetry = onRetry, onSelectProfile = onSelectProfile)
    RemotePanel(state, onRemote = onRemote)
    EnhancePanel(state, onStart = onStart)
}

@Composable
private fun StatusCard(state: LinkUiState, paired: LinkState.Paired, onRetry: () -> Unit, onContinue: () -> Unit, onForget: () -> Unit) {
    val online = paired.reachable
    val device = state.device
    Surface(
        color = if (online) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (online) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LinkStatusAvatar(connected = online, icon = Icons.Outlined.Computer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(if (online) Res.string.linked_connected else Res.string.linked_unreachable),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${device?.serverName ?: paired.pc.serverName} · https://${paired.pc.host}:${paired.pc.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    device?.fingerprint?.let { fp ->
                        // BUG-090 / BUG-105: the label in the normal font on its own line, and only the fingerprint in monospace below:
                        // "B452 3914 … 0A7C DF6D" (~22 characters, far from the ~288 dp of the column, so the last group, the one compared
                        // with the PC, is never cut). "Ver detalles" shows the whole one in rows of 4 groups, broken on purpose.
                        var full by rememberSaveable { mutableStateOf(false) }
                        val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono)
                        Column(Modifier.padding(top = 4.dp)) {
                            Text(stringResource(Res.string.linked_fingerprint_label), style = MaterialTheme.typography.bodySmall)
                            if (full) {
                                LinkLogic.fingerprintRows(fp).forEach { Text(it, style = mono, maxLines = 1, softWrap = false) }
                            } else {
                                Text(LinkLogic.shortFingerprint(fp), style = mono, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = { full = !full },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                            ) {
                                Text(
                                    stringResource(if (full) Res.string.linked_fingerprint_hide else Res.string.linked_fingerprint_show),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = TextDecoration.Underline,
                                )
                            }
                        }
                    }
                    device?.lastSeenMs?.let {
                        // Re-read every 30 s so "visto hace N min" stays true while the screen is open (link.last-seen).
                        val tick by produceState(0, it) { while (true) { delay(30_000); value++ } }
                        val label = remember(it, tick) { it }
                        Text(stringResource(Res.string.linked_last_seen, relativeTime(label)), style = MaterialTheme.typography.bodySmall)
                    }
                    device?.localDeviceName?.let {
                        Text(stringResource(Res.string.linked_registered_as, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!online) {
                Text(stringResource(Res.string.linked_unreachable_hint), style = MaterialTheme.typography.bodySmall)
            }
            // 2026-11 history per device: how the PC treats this phone's history, and a running resync
            LinkLogic.historyNotices(state.historyMode, state.historyResyncing).forEach { notice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (notice == HistoryNotice.RESYNCING) LoadingIndicator(Modifier.size(16.dp))
                    Text(
                        stringResource(
                            when (notice) {
                                HistoryNotice.RECEIVE_ONLY -> Res.string.linked_history_receive_only
                                HistoryNotice.PAUSED -> Res.string.linked_history_paused
                                HistoryNotice.RESYNCING -> Res.string.linked_history_resyncing
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (online) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = onContinue, enabled = state.continueEntry != null && !state.handingOff) {
                            if (state.handingOff) {
                                LoadingIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Outlined.Cast, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            }
                            Text(stringResource(Res.string.linked_continue), modifier = Modifier.padding(start = 8.dp))
                        }
                        Text(
                            state.continueEntry?.let { e ->
                                stringResource(Res.string.linked_continue_what, e.title, e.episode, LinkLogic.clock(e.progressSec, null))
                            } ?: stringResource(Res.string.linked_continue_none),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(UiR.string.ui_retry), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            // BUG-090: forgetting the PC belongs to this card (it was under the remote's transport buttons), as a
            // secondary destructive action on its own line (393 dp: it does not fit next to "Continuar en la PC")
            ForgetButton(onForget)
        }
    }
}

@Composable
private fun CapabilitiesPanel(state: LinkUiState, onRetry: () -> Unit, onSelectProfile: (String?) -> Unit) {
    Panel(icon = Icons.Outlined.Memory, title = stringResource(Res.string.linked_caps)) {
        when (val caps = state.capabilities) {
            null, LoadState.Loading -> if (rememberAfterDelay(150)) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(5f))
                SkeletonBox(Modifier.fillMaxWidth(0.6f).aspectRatio(8f))
            }
            is LoadState.Err -> ErrorState(
                message = caps.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = onRetry,
                compact = true,
            )
            is LoadState.Ok -> CapabilitiesBody(
                caps.value,
                state.selectedProfile,
                state.unsupportedProfiles,
                state.mayDropFramesProfiles,
                state.profileFallback,
                onSelectProfile,
            )
        }
    }
}

@Composable
private fun CapabilitiesBody(
    caps: LinkCapabilities,
    selected: String?,
    unsupported: Map<String, ProfileSupport>,
    mayDropFrames: Set<String>,
    fallback: LinkProfileFallback?,
    onSelect: (String?) -> Unit,
) {
    Cap(stringResource(Res.string.linked_caps_server), "${caps.serverName} · ${caps.serverVersion}")
    Cap(
        stringResource(Res.string.linked_caps_frame_gen),
        stringResource(if (caps.clientFrameGenerationAllowed) Res.string.linked_yes else Res.string.linked_no),
    )
    // Prototype: profiles measured as not sustainable are hidden from the segmented.
    val usable = caps.profiles.filter { it.measuredSustain != "false" }
    if (usable.isEmpty()) {
        Hint(stringResource(Res.string.linked_profiles_none))
        return
    }
    Text(stringResource(Res.string.linked_profile), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    // BUG-132: the profiles this phone cannot decode stay in the list, disabled with the reason; never the selected one
    val playable = usable.filter { it.id !in unsupported }
    val current = selected?.takeIf { id -> playable.any { it.id == id } }
        ?: caps.defaultProfile.takeIf { id -> playable.any { it.id == id } }
        ?: (playable.firstOrNull() ?: usable.first()).id
    // A vertical list: with LSFG and Anime4K + LSFG a PC offers ~15 profiles, too many for a segmented row.
    ProfileList(profiles = usable, selected = current, unavailable = unsupported, mayDropFrames = mayDropFrames, onSelect = { onSelect(it) })
    if (fallback != null) {
        val from = caps.profiles.firstOrNull { it.id == fallback.from }
        val to = caps.profiles.firstOrNull { it.id == fallback.to }
        if (from != null && to != null) Hint(stringResource(Res.string.linked_profile_fallback, profileLabel(from), profileLabel(to)))
    }
    usable.firstOrNull { it.id == current }?.let { profile ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(sustainText(profile), tone = if (profile.measuredSustain == "true") PillTone.OK else PillTone.NEUTRAL)
            Text(
                "${profile.outputWidth}×${profile.outputHeight}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sustainText(profile: LinkProfile): String = stringResource(
    when (profile.measuredSustain) {
        "true" -> Res.string.linked_sustain_ok
        "false" -> Res.string.linked_sustain_no
        else -> Res.string.linked_sustain_unknown
    },
)

@Composable
private fun RemotePanel(state: LinkUiState, onRemote: (RemoteAction) -> Unit) {
    val remote = state.remote
    Panel(icon = Icons.Outlined.PhoneAndroid, title = stringResource(Res.string.linked_remote)) {
        if (remote == null) {
            Hint(stringResource(Res.string.linked_remote_idle))
        } else {
            Text(
                remote.title?.let { t -> remote.episode?.let { stringResource(Res.string.linked_remote_now_episode, t, it) } ?: t }
                    ?: stringResource(Res.string.linked_remote_now_unknown),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (remote.playing) Res.string.linked_remote_playing else Res.string.linked_remote_paused) + " · " +
                    LinkLogic.clock(remote.positionSec, remote.durationSec),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val enabled = remote != null && !state.remoteBusy
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onRemote(RemoteAction.PREV) }, enabled = enabled) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(Res.string.linked_remote_prev))
            }
            FilledIconButton(onClick = { onRemote(RemoteAction.PLAY_PAUSE) }, enabled = enabled, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (remote?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (remote?.playing == true) Res.string.linked_remote_pause else Res.string.linked_remote_play),
                )
            }
            FilledTonalIconButton(onClick = { onRemote(RemoteAction.NEXT) }, enabled = enabled) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(Res.string.linked_remote_next))
            }
        }
    }
}

/** "La PC te envió …" (link.send-to-device): play the PC's Link session here or dismiss it. */
@Composable
private fun HandoffCard(h: LinkHandoff, onPlay: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                h.title?.let { stringResource(Res.string.linked_handoff_title, it) } ?: stringResource(Res.string.linked_handoff_title_generic),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    h.episode?.let { stringResource(Res.string.linked_handoff_episode, it) },
                    stringResource(Res.string.linked_handoff_from, LinkLogic.clock(h.positionSec, null)),
                    handoffAge(h.receivedAtMs),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.linked_handoff_play), modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.linked_handoff_dismiss)) }
            }
        }
    }
}

/** BUG-138: "hace 3 min" of a send at least a minute old (refreshed every 30 s), null for a fresh or undated one. */
@Composable
private fun handoffAge(receivedAtMs: Long?): String? {
    if (receivedAtMs == null) return null
    val now by produceState(System.currentTimeMillis(), receivedAtMs) {
        while (true) {
            delay(HANDOFF_AGE_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    return if (LinkLogic.handoffAgeShown(receivedAtMs, now)) relativeTime(receivedAtMs, now) else null
}

private const val HANDOFF_AGE_TICK_MS = 30_000L

/** PCs announcing `_nijiyome-link._tcp` (link.discovery): "Emparejar" shows the fingerprint (TOFU) and asks the code the PC shows. */
@Composable
private fun DiscoveredPanel(
    pcs: List<DiscoveredPc>,
    pairing: Boolean,
    enabled: Boolean,
    fingerprintOf: (DiscoveredPc) -> String?,
    onPair: (DiscoveredPc, String) -> Unit,
) {
    var target by remember { mutableStateOf<DiscoveredPc?>(null) }
    Panel(icon = Icons.Outlined.Wifi, title = stringResource(Res.string.linked_discovered_title)) {
        if (pcs.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(24.dp))
                Hint(stringResource(Res.string.linked_discovered_searching))
            }
        } else {
            pcs.forEach { pc ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Computer, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(pc.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pc.host}:${pc.port}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
                    }
                    FilledTonalButton(onClick = { target = pc }, enabled = enabled && !pairing) { Text(stringResource(Res.string.linked_pair)) }
                }
            }
        }
    }
    target?.let { pc ->
        DiscoveredPairDialog(pc, fingerprintOf(pc), onDismiss = { target = null }) { code ->
            target = null
            onPair(pc, code)
        }
    }
}

@Composable
private fun DiscoveredPairDialog(pc: DiscoveredPc, fingerprint: String?, onDismiss: () -> Unit, onPair: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val valid = code.filter { it.isLetterOrDigit() }.length == 10
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        title = { Text(stringResource(Res.string.linked_discovered_pair_title, pc.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.linked_discovered_pair_body), style = MaterialTheme.typography.bodyMedium)
                // BUG-105: the same readable rows as the "PC conectada" card, to compare group by group with the PC
                Column {
                    Text(stringResource(Res.string.linked_fingerprint_label), style = MaterialTheme.typography.bodySmall)
                    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono)
                    (fingerprint?.let { LinkLogic.fingerprintRows(it) } ?: listOf("?")).forEach { Text(it, style = mono, maxLines = 1, softWrap = false) }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(14) },
                    label = { Text(stringResource(Res.string.linked_code_label)) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = NijiyomeFonts.jetBrainsMono),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(code.trim()) }, enabled = valid && fingerprint != null) { Text(stringResource(Res.string.linked_discovered_pair_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_cancel)) } },
    )
}

/** Short readable text of a Link failure for the snackbar. */
private suspend fun linkErrorText(error: app.nijiyome.model.AppError): String = when (error) {
    is app.nijiyome.model.AppError.Http -> if (error.code == 409) getString(Res.string.linked_remote_no_player) else getString(Res.string.linked_error_http, error.code)
    app.nijiyome.model.AppError.Offline -> getString(Res.string.linked_unreachable)
    else -> getString(Res.string.linked_problem)
}

@Composable
private fun ForgetButton(onForget: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    // BUG-090: a secondary, destructive action (error-coloured text button) next to the card's main action
    TextButton(onClick = { confirming = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(Res.string.linked_forget), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(stringResource(Res.string.linked_forget_title)) },
            text = { Text(stringResource(Res.string.linked_forget_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onForget()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.linked_forget)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun EnhancePanel(state: LinkUiState, onStart: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    Panel(icon = Icons.Outlined.Cast, title = stringResource(Res.string.linked_enhance_title)) {
        val canStart = !state.startingSession && url.isNotBlank() && state.capabilities is LoadState.Ok
        val problem = state.enhanceProblem?.let { enhanceProblemText(it) }
        // BUG-024: a URL keyboard without auto-capitalisation or correction, and "Go" on the keyboard (the button can sit under it)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.linked_enhance_url)) },
            // BUG-104: only the URL's own problem goes under the field (red, with isError); the PC's answer is the red line below
            supportingText = { Text(LinkLogic.fieldProblem(state.enhanceProblem)?.let { enhanceProblemText(it) } ?: stringResource(Res.string.linked_enhance_hint)) },
            isError = state.enhanceProblem == EnhanceProblem.INVALID_URL,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (canStart) onStart(url) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.enhanceProblem != null && LinkLogic.fieldProblem(state.enhanceProblem) == null) {
            Text(problem.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.sessionError?.let {
            Text(it.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { onStart(url) },
            enabled = canStart,
        ) {
            if (state.startingSession) LoadingIndicator(Modifier.padding(end = 8.dp).size(20.dp))
            Text(stringResource(if (state.startingSession) Res.string.linked_enhance_starting else Res.string.linked_enhance_start))
        }
    }
}

@Composable
private fun enhanceProblemText(p: EnhanceProblem): String = stringResource(
    when (p) {
        EnhanceProblem.INVALID_URL -> Res.string.linked_enhance_invalid
        EnhanceProblem.REJECTED -> Res.string.linked_enhance_rejected
        EnhanceProblem.TIMEOUT -> Res.string.linked_enhance_timeout
    },
)

// ---------------------------------------------------------------- PC extension server entry (docs/redesign/13 §3.10)

@Composable
private fun ExtensionServerCard(onOpen: () -> Unit) {
    Panel(icon = Icons.Outlined.Extension, title = stringResource(Res.string.extserver_entry_title)) {
        Hint(stringResource(Res.string.extserver_entry_body))
        FilledTonalButton(onClick = onOpen) {
            Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(Res.string.extserver_entry_open), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- building blocks

/** Prototype `.panel` with a titled header. */
@Composable
private fun Panel(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
private fun Cap(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

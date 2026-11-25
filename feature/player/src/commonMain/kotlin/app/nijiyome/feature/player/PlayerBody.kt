// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeTheme
import app.nijiyome.feature.player.sources.SourcePickerSheet
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.state.contrastLevel
import app.nijiyome.ui.state.rememberIsOnline
import app.nijiyome.ui.state.userMessage
import org.koin.compose.getKoin
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/** The view model key of one player entry: a new episode / source / session is a new view model. */
internal fun playerViewModelKey(args: PlayerArgs): String =
    "player-${args.media.anilistId}-${args.media.malId}-${args.episode}-${args.remoteSessionId}-${args.sourceKey}-" +
        "${args.torrentId}-${args.fileIndex}-${args.localUri}-${args.provider?.historyId}-${args.provider?.entryId}"

/**
 * Milestone 4: the player screen around [vm] (built by each platform's [PlayerScreen]): the settings' theme in its dark AMOLED variant
 * (video chrome), the replace / notification wiring, [PlayerBody] and system back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerScreenContent(
    vm: PlayerViewModel,
    args: PlayerArgs,
    onBack: () -> Unit,
    onReplace: (PlayerArgs) -> Unit,
    onOpenTorrent: (torrentId: String) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenDetails: (anilistId: Int) -> Unit,
) {
    val koin = getKoin()
    val settingsRepo = remember { koin.getOrNull<SettingsRepository>() }
    val appSettings = settingsRepo?.settings?.collectAsStateWithLifecycle()?.value
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings ?: state.settings

    LaunchedEffect(vm) { vm.replace.collect { onReplace(it) } }
    // Media notification / lock screen (PlaybackService): title + "Episode N".
    val notificationLine = if (args.remoteSessionId == null) stringResource(Res.string.player_next_episode_n, args.episode) else null
    LaunchedEffect(vm, notificationLine) { vm.setNotificationText(args.title, notificationLine) }

    NijiyomeTheme(
        paletteId = settings.paletteId,
        amoled = true,
        contrast = settings.contrastLevel(),
        dynamicColor = settings.dynamicColor,
        darkTheme = true,
        direction = app.nijiyome.designsystem.direction.Directions.byId(settings.look.design.direction.id),
        directionPalette = settings.look.design.palette,
    ) {
        PlayerBody(
            vm = vm,
            state = state,
            args = args,
            onBack = onBack,
            onOpenTorrent = { id -> vm.pauseForClient(); onOpenTorrent(id) },
            onOpenSettings = onOpenSettings,
            onOpenExtensions = onOpenExtensions,
            onOpenLinked = onOpenLinked,
            onOpenDetails = args.media.anilistId?.let { id -> { onOpenDetails(id) } },
        )
    }
    PlatformBackHandler(enabled = true, onBack = onBack)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerBody(
    vm: PlayerViewModel,
    state: PlayerUiState,
    args: PlayerArgs,
    onBack: () -> Unit,
    onOpenTorrent: (String) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenDetails: (() -> Unit)?,
) {
    val title = if (args.remoteSessionId != null || args.isLooseFile) args.title else stringResource(Res.string.player_episode_title, args.title, args.episode)
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var manualPrompted by rememberSaveable { mutableStateOf(false) }
    var flash by remember { mutableStateOf<PlayerNotice?>(null) }

    PlayerWindowScope()
    LaunchedEffect(vm) { vm.notices.collect { flash = it } }
    // When the network drops for longer than the player's retries, playback comes back by itself once the network returns (same as
    // tapping Retry; the PC keeps a Link session 120 s). Only an offline -> online change retries, so an error the server itself
    // returns never loops. BUG-042 (b): every source, not only Link sessions (a direct web source stayed on the error screen).
    run {
        val online = rememberIsOnline()
        var wentOffline by remember { mutableStateOf(false) }
        LaunchedEffect(online) {
            if (!online) {
                wentOffline = true
            } else if (wentOffline) {
                wentOffline = false
                if (vm.state.value.phase == PlayerPhase.ERROR) vm.retry()
            }
        }
    }
    // Manual mode: open the picker once by itself (prototype: 300 ms after entering).
    LaunchedEffect(state.phase) {
        if (state.phase == PlayerPhase.MANUAL && !manualPrompted) {
            manualPrompted = true
            kotlinx.coroutines.delay(300)
            pickerOpen = true
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val session = state.session
        when {
            state.phase == PlayerPhase.EXTERNAL && state.external != null -> ExternalBranch(vm, state, title, onBack, onOpenSettings, onOpenPicker = { pickerOpen = true })
            session != null && state.phase != PlayerPhase.ERROR -> androidx.compose.runtime.CompositionLocalProvider(LocalDownloadArgs provides args) { PlayerContent(
                vm = vm,
                session = session,
                state = state,
                title = title,
                flash = flash,
                onFlashDone = { flash = null },
                onBack = onBack,
                onOpenPicker = { pickerOpen = true },
                onOpenTorrent = onOpenTorrent,
                onOpenSettings = onOpenSettings, // 0.4.0 (worker D): "Importar Lossless.dll" from the Mejoras panel
                args = args,
                onOpenDetails = onOpenDetails,
            ) }
            else -> StatusBody(vm, state, onOpenTorrent, onOpenExtensions, onOpenLinked, onPick = { pickerOpen = true })
        }
        // Back is always reachable, also while sources load or fail.
        if (session == null || state.phase == PlayerPhase.ERROR) {
            if (state.phase != PlayerPhase.EXTERNAL) {
                IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.player_back), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            if (session == null) {
                flash?.let { FlashToast(it, onDone = { flash = null }, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)) }
            }
        }
    }

    state.metered?.let { prompt ->
        MeteredDialog(
            prompt = prompt,
            onWatch = vm::acceptMetered,
            onWeb = vm::declineMetered,
            onSettings = { vm.declineMetered(); onOpenSettings(SettingsSection.TORRENTS) },
        )
    }

    if (pickerOpen) {
        SourcePickerSheet(
            sources = state.sources,
            loading = state.candidatesLoading,
            error = state.candidatesError,
            mode = state.mode,
            onPick = { s -> pickerOpen = false; vm.pickSource(s.key) },
            onDismiss = { pickerOpen = false },
            onRetry = vm::reloadSources,
            onOpenSettings = { pickerOpen = false; onOpenSettings(SettingsSection.PLAYER) },
            rememberedPick = state.rememberedPick,
        )
    }
}

/** Everything that is not a running engine: loading, Manual, no sources, errors. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusBody(
    vm: PlayerViewModel,
    state: PlayerUiState,
    onOpenTorrent: (String) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onPick: () -> Unit,
) {
    val onColor = MaterialTheme.colorScheme.onBackground
    when (state.phase) {
        PlayerPhase.LOADING, PlayerPhase.RESOLVING, PlayerPhase.BUFFERING, PlayerPhase.PLAYING -> {
            val show = rememberAfterDelay(150)
            if (show) {
                Centered {
                    LoadingIndicator(Modifier.size(56.dp))
                    val torrent = state.torrent
                    if (torrent != null) {
                        TorrentStatusText(torrent)
                    } else {
                        val name = state.current?.candidate?.let { "${it.extName} · ${it.label}" }
                        Text(
                            if (name != null) stringResource(Res.string.player_resolving_from, name) else stringResource(Res.string.player_loading_sources),
                            color = onColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                    state.position?.let { (n, total) ->
                        Text(stringResource(Res.string.player_source_n, n, total), color = onColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        PlayerPhase.MANUAL -> Centered {
            // BUG-079: a state line, not the sheet's words (the sheet opened on top repeated "Elige una fuente para este episodio").
            Text(stringResource(Res.string.player_no_source_yet), color = onColor, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onPick) { Text(stringResource(Res.string.player_pick_source)) }
        }
        PlayerPhase.NO_SOURCES -> Centered {
            EmptyState(
                icon = Icons.Rounded.Extension,
                // BUG-082 parity with the PC: installed extensions that returned nothing are not "install an extension".
                title = stringResource(if (state.sourcesAsked) Res.string.player_no_sources_found_title else Res.string.player_no_sources_title),
                message = stringResource(if (state.sourcesAsked) Res.string.player_no_sources_found else Res.string.player_no_sources),
                actionLabel = stringResource(Res.string.player_go_extensions),
                onAction = onOpenExtensions,
            )
        }
        PlayerPhase.REMOTE_UNAVAILABLE -> Centered {
            EmptyState(
                icon = Icons.Rounded.Devices,
                title = stringResource(Res.string.player_remote_unavailable),
                actionLabel = stringResource(Res.string.player_open_linked),
                onAction = onOpenLinked,
            )
        }
        PlayerPhase.FAILED -> Centered {
            ErrorState(
                message = state.error?.userMessage() ?: stringResource(Res.string.player_error),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = vm::retry,
                title = stringResource(Res.string.player_error),
            )
        }
        PlayerPhase.ERROR -> PlaybackErrorView(
            state = state,
            onRetry = vm::retry,
            onUseWeb = vm::useBestWeb,
            onPick = onPick,
            onOpenTorrent = onOpenTorrent,
            onOpenExtensions = onOpenExtensions,
            onPlayOnPhone = vm::playOnPhoneInstead,
        )
        PlayerPhase.EXTERNAL -> Unit
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) { content() }
}

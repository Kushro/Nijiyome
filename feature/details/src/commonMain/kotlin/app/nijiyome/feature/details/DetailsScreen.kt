@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.details

import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.platform.systemShareAvailable
import app.nijiyome.ui.platform.rememberOpenUrl
import app.nijiyome.ui.platform.rememberShareText
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import app.nijiyome.ui.media.displayTitle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.nijiyome.model.PageStyle
import app.nijiyome.ui.look.LocalLook
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NoAdultContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.NijiyomeFloatingToolbar
import app.nijiyome.designsystem.theme.NijiyomeSeedTheme
import app.nijiyome.designsystem.theme.parseHexColor
import app.nijiyome.feature.library.ListButton
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.patterns.rememberShowUndo
import app.nijiyome.ui.patterns.toListStatus
import app.nijiyome.feature.player.sources.SourcePickerSheet
import app.nijiyome.feature.torrents.rememberDownloadGate
import app.nijiyome.model.AppError
import app.nijiyome.model.LinkState
import app.nijiyome.model.PhoneTarget
import app.nijiyome.model.LoadState
import app.nijiyome.model.HeaderLayout
import app.nijiyome.model.Media
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SettingsSection
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.state.userMessageText
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.state.LoadStateContent
import app.nijiyome.ui.state.providerLabel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import app.nijiyome.designsystem.theme.NijiyomeControls

@Composable
fun DetailsScreen(
    kind: MediaKind,
    anilistId: Int,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onOpenExtensions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = koinViewModel(
        key = "details-$kind-$anilistId",
        parameters = { parametersOf(kind, MediaRef(anilistId = anilistId)) },
    ),
    // --- android wave contract (docs/redesign/12 §3.14): new callbacks appended, wired by :app (worker b) ---
    /** After "Descargar" or from a torrent source -> Route.Torrents(OVERVIEW, torrentId). */
    onOpenTorrent: (torrentId: String) -> Unit = {},
    /** Source panel banner "Ajustes" -> Route.SettingsSection(section). */
    onOpenSettings: (SettingsSection) -> Unit = {},
    /** "Enviar a dispositivo" without a paired PC -> Route.Linked. */
    onOpenLinked: () -> Unit = {},
    /** Snackbar action after a download was queued -> Route.Torrents(DOWNLOADS). */
    onOpenDownloads: () -> Unit = {},
    /** Genre chip -> Route.SearchFor(genre = genre). */
    onOpenGenre: (String) -> Unit = {},
    // --- parity wave A (docs/redesign/15 §3.1, worker o): appended, wired by :app ---
    /** Characters tab card -> Route.Character(anilistId of the character). */
    onOpenCharacter: (characterId: Int) -> Unit = {},
    /** Studio chip of the technical sheet -> Route.SearchFor(studio = name). */
    onOpenStudio: (String) -> Unit = {},
) {
    // Related titles, seasons and recommendations open their own Details in place (the contract gives Details no "open title"
    // callback): a stack of AniList ids, popped by back. Each level is a full DetailsScreen with its own ViewModel.
    var stack by rememberSaveable(kind, anilistId) { mutableStateOf("") }
    val ids = stack.split(',').mapNotNull { it.toIntOrNull() }
    val top = ids.lastOrNull()
    if (top != null) {
        val pop = { stack = ids.dropLast(1).joinToString(",") }
        PlatformBackHandler(onBack = pop)
        DetailsScreen(
            kind = kind,
            anilistId = top,
            onBack = pop,
            onPlay = onPlay,
            onOpenExtensions = onOpenExtensions,
            modifier = modifier,
            onOpenTorrent = onOpenTorrent,
            onOpenSettings = onOpenSettings,
            onOpenLinked = onOpenLinked,
            onOpenDownloads = onOpenDownloads,
            onOpenGenre = onOpenGenre,
            onOpenCharacter = onOpenCharacter,
            onOpenStudio = onOpenStudio,
        )
        return
    }
    val openMedia: (Media) -> Unit = { media ->
        val id = media.ref.anilistId
        if (id != null && id != anilistId) stack = (ids + id).joinToString(",")
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailsPresence((state.media as? LoadState.Ok)?.value?.title)
    val clipboard = LocalClipboardManager.current
    val shareText = rememberShareText()
    val openUrl = rememberOpenUrl()
    // read at composition: the share callback runs outside a coroutine
    val shareCopied = stringResource(Res.string.details_share_copied)
    val shareSend = stringResource(Res.string.details_share_send)
    val shareTitle = stringResource(Res.string.details_share)
    val titleCopied = stringResource(Res.string.details_title_copied)
    val snackbar = LocalAppSnackbar.current
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnOpenDownloads by rememberUpdatedState(onOpenDownloads)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailsEvent.NavigateToPlayer -> currentOnPlay(event.args)
                is DetailsEvent.SourceChosen -> snackbar.show(
                    getString(
                        Res.string.details_src_using,
                        getString(Res.string.details_src_label, event.extName, event.label),
                    ),
                )
                is DetailsEvent.DownloadQueued -> snackbar.show(
                    message = getString(Res.string.details_download_queued),
                    actionLabel = getString(Res.string.details_download_open),
                    onAction = { currentOnOpenDownloads() },
                )
                is DetailsEvent.DebridDownloadQueued -> snackbar.show(
                    message = getString(Res.string.details_download_queued_debrid, event.service),
                    actionLabel = getString(Res.string.details_download_open),
                    onAction = { currentOnOpenDownloads() },
                )
                is DetailsEvent.DebridDownloadFallback -> snackbar.show(
                    getString(Res.string.details_download_debrid_fallback, event.service, getString(event.problem.labelRes())),
                )
                is DetailsEvent.DownloadFailed -> snackbar.show(
                    getString(Res.string.details_download_failed, event.cause.userMessageText()),
                )
                DetailsEvent.NoTorrentSource -> snackbar.show(getString(Res.string.details_download_none))
                is DetailsEvent.SendFailed -> snackbar.show(sendFailedMessage(event))
                is DetailsEvent.SentOther -> snackbar.show(
                    getString(
                        if (event.chosenWasTorrent) Res.string.details_send_other_torrent else Res.string.details_send_other,
                        event.label,
                    ),
                )
                DetailsEvent.NoWebSource -> snackbar.show(getString(Res.string.details_send_no_web))
                is DetailsEvent.ListUpdateFailed -> snackbar.show(
                    getString(Res.string.details_mark_failed, event.cause.userMessageText()),
                )
                is DetailsEvent.FavoriteFailed -> snackbar.show(
                    getString(Res.string.details_favorite_failed, event.cause.userMessageText()),
                )
                is DetailsEvent.SentToPhone -> snackbar.show(getString(Res.string.details_send_phone_done, event.name))
                is DetailsEvent.PhoneSendFailed -> snackbar.show(phoneSendFailedMessage(event.reason))
            }
        }
    }
    // Every background download goes through the "Entiendo" gate of the torrent client (11 §4.3).
    val gate = rememberDownloadGate()
    var sendDialog by rememberSaveable { mutableStateOf(false) }
    // desktop: the paired phone picked in "Enviar a dispositivo", waiting for the confirmation
    var phoneTarget by remember { mutableStateOf<PhoneTarget?>(null) }
    // Sparkles redesign: list changes from the Ficha are undoable (status picker, remove, watched marks).
    val showUndo = rememberShowUndo()
    val undoScope = rememberCoroutineScope()
    val statusMessages = StatusSlot.entries.associateWith { stringResource(Res.string.rd_s4_status_changed, it.label()) }
    val removedMessage = stringResource(Res.string.rd_s4_removed)
    val markEpisodes: ((Set<Int>, Boolean) -> Unit)? = if (viewModel.canToggleWatched) {
        { episodes, watched ->
            viewModel.setEpisodesWatched(episodes, watched)?.let { before ->
                undoScope.launch {
                    val message = when {
                        episodes.size == 1 && watched -> getString(Res.string.rd_s4_marked_one_watched, episodes.first())
                        episodes.size == 1 -> getString(Res.string.rd_s4_marked_one_unwatched, episodes.first())
                        watched -> getString(Res.string.rd_s4_marked_watched, episodes.size)
                        else -> getString(Res.string.rd_s4_marked_unwatched, episodes.size)
                    }
                    showUndo(message) { viewModel.restoreList(before) }
                }
            }
        }
    } else {
        null
    }
    val actions = DetailsActions(
        onBack = onBack,
        onRetry = viewModel::retry,
        onWatch = viewModel::onWatch,
        onOpenPicker = viewModel::openPicker,
        onEpisodeClick = viewModel::onEpisodeClick,
        onRetrySources = viewModel::retrySources,
        onDownloadSource = { source, episode -> gate.launch { viewModel.download(source, episode) } },
        onDownloadEpisode = { episode -> gate.launch { viewModel.downloadEpisode(episode) } },
        onDownloadSeason = { gate.launch { viewModel.downloadSeason() } },
        onSendToDevice = {
            if (state.link is LinkState.Paired) sendDialog = true else onOpenLinked()
        },
        onSendToPhone = { phoneTarget = it },
        onOpenLinked = onOpenLinked,
        onOpenExtensions = onOpenExtensions,
        onOpenSettings = onOpenSettings,
        onOpenGenre = onOpenGenre,
        onLoadRelated = viewModel::loadRelated,
        onOpenMedia = openMedia,
        onOpenCharacter = onOpenCharacter,
        onOpenStudio = onOpenStudio,
        onShare = { media ->
            shareLink(media)?.let { link ->
                clipboard.setText(AnnotatedString(link))
                // Desktop has no share sheet (Tatakai Next only copies): no "Enviar" that would copy the same text again.
                snackbar.show(
                    message = shareCopied,
                    actionLabel = shareSend.takeIf { systemShareAvailable },
                    onAction = { shareText(link, shareTitle) }.takeIf { systemShareAvailable },
                )
            }
        },
        onOpenUrl = openUrl,
        onCopyTitle = { title ->
            clipboard.setText(AnnotatedString(title))
            snackbar.show(message = titleCopied)
        },
        onRetryPrimary = viewModel::retryPrimaryProvider,
        onLoadCharacters = viewModel::loadCharacters,
        onRetryDetails = viewModel::retryDetails,
        onEpisodeQuery = viewModel::setEpisodeQuery,
        onEpisodeView = viewModel::setEpisodeView,
        onRelationsView = viewModel::setRelationsView,
        onAcceptAdult = viewModel::acceptAdult,
        onRevealCover = viewModel::revealCover,
        onToggleWatched = markEpisodes?.let { mark -> { n: Int -> mark(setOf(n), !viewModel.state.value.isWatched(n)) } },
        onToggleFavorite = viewModel::toggleFavorite.takeIf { viewModel.canToggleWatched },
        onSetStatus = if (viewModel.canToggleWatched) {
            { slot ->
                viewModel.setListStatus(slot.toListStatus())?.let { before ->
                    showUndo(statusMessages.getValue(slot)) { viewModel.restoreList(before) }
                }
            }
        } else {
            null
        },
        onRemoveFromList = {
            viewModel.removeFromList()?.let { before -> showUndo(removedMessage) { viewModel.restoreList(before) } }
        },
        onMarkEpisodes = markEpisodes,
        onDownloadEpisodes = { episodes -> gate.launch { viewModel.downloadEpisodes(episodes) } },
    )
    val seed = parseHexColor((state.media as? LoadState.Ok)?.value?.color)
    NijiyomeSeedTheme(seed = seed) {
        // The minute ticker drives the "Al día" -> "Continuar" switch and the unaired marks (no clock reads in getters).
        val nowSec = rememberNowSec()
        val clocked = remember(state, nowSec) { state.copy(clockSec = nowSec) }
        DetailsContent(state = clocked, actions = actions, targetKey = "${kind.name.lowercase()}:$anilistId", modifier = modifier)
        state.picker?.let { request ->
            val panel = state.panel?.takeIf { it.episode == request.episode }
            SourcePickerSheet(
                sources = (panel?.sources as? LoadState.Ok)?.value.orEmpty(),
                loading = panel == null || panel.sources is LoadState.Loading || panel.loadingMore,
                error = (panel?.sources as? LoadState.Err)?.cause,
                mode = state.mode,
                onPick = viewModel::onPick,
                onDismiss = viewModel::dismissPicker,
                onRetry = viewModel::retrySources,
                onOpenSettings = {
                    viewModel.dismissPicker()
                    onOpenSettings(SettingsSection.PLAYER)
                },
                onDownload = { source ->
                    viewModel.dismissPicker()
                    actions.onDownloadSource(source, request.episode)
                },
                rememberedPick = panel?.rememberedPick,
                onOpenExtensions = {
                    viewModel.dismissPicker()
                    onOpenExtensions()
                },
                episode = request.episode,
            )
        }
        if (sendDialog) {
            val media = (state.media as? LoadState.Ok)?.value
            val pc = state.pairedPc
            if (media != null && pc != null) {
                SendToDeviceDialog(
                    media = media,
                    episode = state.continueEpisode,
                    pcName = pc.serverName,
                    reachable = state.pcAvailable,
                    checking = state.pcChecking,
                    onRecheck = viewModel::recheckPc,
                    onConfirm = {
                        sendDialog = false
                        viewModel.sendToDevice()
                    },
                    onDismiss = { sendDialog = false },
                )
            }
        }
        phoneTarget?.let { phone ->
            val media = (state.media as? LoadState.Ok)?.value
            if (media == null) {
                phoneTarget = null
            } else {
                SendToPhoneDialog(
                    media = media,
                    episode = state.continueEpisode,
                    phone = phone,
                    onConfirm = {
                        phoneTarget = null
                        viewModel.sendToPhone(phone.id)
                    },
                    onDismiss = { phoneTarget = null },
                )
            }
        }
    }
}

/** Every callback of the Details content (the gate-wrapped download calls included). */
internal data class DetailsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onWatch: () -> Unit = {},
    val onOpenPicker: (playOnPick: Boolean) -> Unit = {},
    val onEpisodeClick: (Int) -> Unit = {},
    val onRetrySources: () -> Unit = {},
    val onDownloadSource: (RankedSource, Int?) -> Unit = { _, _ -> },
    val onDownloadEpisode: (Int) -> Unit = {},
    val onDownloadSeason: () -> Unit = {},
    val onSendToDevice: () -> Unit = {},
    /** Desktop: a paired phone was picked in "Enviar a dispositivo" (asks before sending). */
    val onSendToPhone: (PhoneTarget) -> Unit = {},
    val onOpenLinked: () -> Unit = {},
    val onOpenExtensions: () -> Unit = {},
    val onOpenSettings: (SettingsSection) -> Unit = {},
    val onOpenGenre: (String) -> Unit = {},
    val onLoadRelated: () -> Unit = {},
    // --- parity wave A (15 §5 o.2) ---
    val onOpenMedia: (Media) -> Unit = {},
    val onOpenCharacter: (Int) -> Unit = {},
    val onOpenStudio: (String) -> Unit = {},
    val onShare: (Media) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onCopyTitle: (String) -> Unit = {},
    val onRetryPrimary: () -> Unit = {},
    val onLoadCharacters: () -> Unit = {},
    val onRetryDetails: () -> Unit = {},
    val onEpisodeQuery: (String) -> Unit = {},
    val onEpisodeView: (EpisodeView) -> Unit = {},
    /** 2026-11 UI refresh: Relacionados graph / list toggle. */
    val onRelationsView: (app.nijiyome.model.RelationsView) -> Unit = {},
    val onAcceptAdult: () -> Unit = {},
    /** matureContent BLUR: tap on the blurred banner / cover reveals them. */
    val onRevealCover: () -> Unit = {},
    /** Episode row "Marcar como visto / no visto" (list progress); null = no lists layer (the button is hidden). */
    val onToggleWatched: ((Int) -> Unit)? = null,
    /** Header "Favorito"; null = no lists layer (the action is hidden). */
    val onToggleFavorite: (() -> Unit)? = null,
    // --- Sparkles redesign (lane S4) ---
    /** Header status picker (one tap, undoable); null = no lists layer (the picker is hidden). */
    val onSetStatus: ((StatusSlot) -> Unit)? = null,
    /** Header "Quitar de la lista" (undoable). */
    val onRemoveFromList: () -> Unit = {},
    /** Batch bar "Marcar N como vistos / no vistos" (undoable); null = no lists layer. */
    val onMarkEpisodes: ((Set<Int>, Boolean) -> Unit)? = null,
    /** Batch bar "Descargar N" (through the download gate). */
    val onDownloadEpisodes: (Collection<Int>) -> Unit = {},
)

@Composable
internal fun DetailsContent(
    state: DetailsUiState,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
    targetKey: String? = null,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadStateContent(state = state.media, onRetry = actions.onRetry, loading = { FichaLoading(state, actions, targetKey = targetKey) }) { media ->
            if (state.adultGate) AdultGate(actions) else FichaRoot(media, state, actions)
        }
        // Loading, error and the 18+ gate have no header of their own: only the back button.
        if (state.media !is LoadState.Ok || state.adultGate) FloatingBack(actions.onBack, Modifier.align(Alignment.TopStart))
    }
}

@Composable
internal fun watchLabel(state: DetailsUiState): String = when (state.watchAction) {
    WatchAction.START -> stringResource(Res.string.details_watch_ep, state.continueEpisode)
    WatchAction.CONTINUE -> stringResource(Res.string.details_continue, state.continueEpisode)
    WatchAction.REWATCH -> stringResource(Res.string.details_rewatch)
    WatchAction.UP_TO_DATE -> stringResource(if (state.hasProgress) Res.string.details_up_to_date else Res.string.details_coming_soon)
}

@Composable
private fun SendToDeviceDialog(
    media: Media,
    episode: Int,
    pcName: String,
    reachable: Boolean,
    checking: Boolean,
    onRecheck: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Cast, contentDescription = null) },
        title = { Text(stringResource(Res.string.details_send_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.details_send_body, media.title, episode, pcName))
                if (!reachable) {
                    Text(
                        stringResource(Res.string.details_send_unreachable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        // BUG-039: no "Enviar" while the PC does not answer (the send would fail); check again instead
        confirmButton = {
            if (reachable) {
                TextButton(onClick = onConfirm, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_send_action)) }
            } else {
                TextButton(onClick = onRecheck, enabled = !checking, shape = NijiyomeControls.shape) {
                    Text(stringResource(if (checking) Res.string.details_send_checking else Res.string.details_send_recheck), maxLines = 1)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_cancel)) } },
    )
}

/** Desktop: "title · Episodio N → phone", then send. */
@Composable
private fun SendToPhoneDialog(media: Media, episode: Int, phone: PhoneTarget, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Cast, contentDescription = null) },
        title = { Text(stringResource(Res.string.details_send_device)) },
        text = { Text(stringResource(Res.string.details_send_body, media.title, episode, phone.name)) },
        confirmButton = { TextButton(onClick = onConfirm, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_send_action)) } },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_cancel)) } },
    )
}

/** Desktop: the failure of sending to a phone in words. */
internal suspend fun phoneSendFailedMessage(reason: PhoneSendFail): String = when (reason) {
    PhoneSendFail.NO_SOURCE -> getString(Res.string.details_send_phone_no_source)
    PhoneSendFail.TIMEOUT -> getString(Res.string.details_send_timeout)
    PhoneSendFail.SOURCE -> getString(Res.string.details_send_phone_source)
    PhoneSendFail.NOT_PAIRED -> getString(Res.string.details_send_phone_not_paired)
    PhoneSendFail.LINK_OFF -> getString(Res.string.details_send_phone_link_off)
    PhoneSendFail.OTHER -> getString(Res.string.details_send_phone_failed)
}

/** BUG-023: the "Enviar a dispositivo" failure in words, never a raw code. */
internal suspend fun sendFailedMessage(event: DetailsEvent.SendFailed): String = when (event.reason) {
    SendFailReason.SOURCE_DENIED -> getString(Res.string.details_send_denied)
    SendFailReason.SOURCE_UNAVAILABLE -> getString(Res.string.details_send_unavailable)
    SendFailReason.PROFILE_UNAVAILABLE -> getString(Res.string.details_send_profile)
    SendFailReason.OFFLINE -> getString(Res.string.details_send_pc_offline)
    SendFailReason.NOT_PAIRED -> getString(Res.string.details_send_not_paired)
    SendFailReason.RESOLVE_TIMEOUT -> getString(Res.string.details_send_timeout)
    SendFailReason.OTHER -> getString(Res.string.details_send_failed, event.cause.userMessageText())
}


/** Classic AdultWarningGate: an 18+ title asks once; "Mostrar" is remembered per title (DetailsPrefs). */
@Composable
private fun AdultGate(actions: DetailsActions) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Icons.Outlined.NoAdultContent,
            title = stringResource(Res.string.details_adult_title),
            message = stringResource(Res.string.details_adult_body),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = actions.onBack, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_adult_back)) }
            Button(onClick = actions.onAcceptAdult, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.details_adult_show)) }
        }
    }
}

internal enum class DetailsTab { EPISODES, INFO, CHARACTERS, RELATED, REVIEWS }

internal const val RANGE_SIZE = 100

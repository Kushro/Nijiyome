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
        onLoadCharacters = viewModel::loadCharacters,
        onRetryDetails = viewModel::retryDetails,
        onEpisodeQuery = viewModel::setEpisodeQuery,
        onEpisodeView = viewModel::setEpisodeView,
        onRelationsView = viewModel::setRelationsView,
        onAcceptAdult = viewModel::acceptAdult,
        onRevealCover = viewModel::revealCover,
        onToggleWatched = viewModel::toggleWatched.takeIf { viewModel.canToggleWatched },
        onToggleFavorite = viewModel::toggleFavorite.takeIf { viewModel.canToggleWatched },
    )
    val seed = parseHexColor((state.media as? LoadState.Ok)?.value?.color)
    NijiyomeSeedTheme(seed = seed) {
        // The minute ticker drives the "Al día" -> "Continuar" switch and the unaired marks (no clock reads in getters).
        val nowSec = rememberNowSec()
        val clocked = remember(state, nowSec) { state.copy(clockSec = nowSec) }
        DetailsContent(state = clocked, actions = actions, modifier = modifier)
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
                onDownload = { source -> actions.onDownloadSource(source, request.episode) },
                rememberedPick = panel?.rememberedPick,
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
)

@Composable
internal fun DetailsContent(
    state: DetailsUiState,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // BUG-062 / BUG-078: the list scrolled past the header (title + "Continuar" button).
    val pastHeader by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val density = LocalDensity.current
    var toolbarHeight by remember { mutableStateOf(0.dp) }
    // Ficha "Primero el episodio": the tab lives here so the copy pinned in the top bar and the one in the list are the same.
    var tab by rememberSaveable { mutableIntStateOf(DetailsTab.EPISODES.ordinal) }
    val selectedTab = DetailsTab.entries[tab]
    // Status bar + bar row: where the list's tabs count as "under the bar".
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barRowPx = with(density) { (statusTop + 56.dp).roundToPx() }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= WIDE_BREAKPOINT
        val phone = !wide
        val tabsAt = TABS_INDEX
        // The list's tabs went under the top bar: the bar shows its own copy (never both visible). layoutInfo is read only here.
        val tabsPinned by remember(tabsAt, barRowPx) {
            derivedStateOf {
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TABS_KEY }
                if (info == null) listState.firstVisibleItemIndex > tabsAt else info.offset <= barRowPx + 1
            }
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LoadStateContent(state = state.media, onRetry = actions.onRetry) { media ->
            if (state.adultGate) {
                AdultGate(actions)
            } else {
                // BUG-063: wide windows reserve the floating toolbar's measured height (plus its margins and the navigation bar), so
                // the last card is never under it. Phones have no floating toolbar: the action sits in the pinned top bar.
                val bottom = if (wide) toolbarHeight + 32.dp + navBottom else navBottom + 24.dp
                DetailsBody(media, state, actions, listState, bottom, wide, maxWidth, selectedTab, tabsPinned) { tab = it.ordinal }
            }
        }
        val loaded = (state.media as? LoadState.Ok)?.value
        // BUG-062 (UX-03) / BUG-108: once the list scrolls, an opaque top bar (status bar + back + title) replaces the floating back
        // button. Ficha "Primero el episodio": `surface` (pure black on AMOLED, no seed tint), a tonal "▶ Ep. N" on phones and the
        // pinned tabs once the list's ones passed under it.
        // Wide: the bar spans the list column only, so it never covers the side source / send panels.
        val barWidth = if (wide) Modifier.width(maxWidth - SIDE_WIDTH) else Modifier.fillMaxWidth()
        AnimatedVisibility(scrolled, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).then(barWidth)) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                        Text(
                            // the header's title (same title-language choice), not the raw provider title
                            loaded?.displayTitle(state.titleLanguage).orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        if (phone && loaded != null && !state.adultGate && state.playable) {
                            // "Ep. 4: Continuar episodio 4": the visible text first (label in name)
                            val long = stringResource(Res.string.details_fact_cd, stringResource(Res.string.details_bar_ep, state.continueEpisode), watchLabels(state).long)
                            FilledTonalButton(
                                onClick = actions.onWatch,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = long },
                                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                                // primaryContainer / onPrimaryContainer: the seeded secondary pair read as disabled in dark mode
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(Res.string.details_bar_ep, state.continueEpisode),
                                    modifier = Modifier.padding(start = 6.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (loaded != null && !state.adultGate && tabsPinned) {
                        DetailsTabs(
                            selectedTab,
                            onTab = { t ->
                                tab = t.ordinal
                                // back to the top of the new tab's content, the tabs right under the bar row
                                scope.launch { listState.scrollToItem(tabsAt, scrollOffset = -barRowPx) }
                            },
                            modifier = Modifier,
                            containerColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
        }
        // The back arrow has its own container, readable on a light banner (only over the header, before scrolling).
        AnimatedVisibility(!scrolled, enter = fadeIn(), exit = fadeOut()) {
            FilledTonalIconButton(
                onClick = actions.onBack,
                modifier = Modifier.statusBarsPadding().padding(4.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
            }
        }
        if (wide && loaded != null && !state.adultGate) {
            // Floating toolbar (prototype `.ftb`, wide windows only): the main actions stay at hand while the episode list scrolls.
            // BUG-078 / BUG-056: it shows up only once the header (with its own "Continuar" button) left the screen.
            AnimatedVisibility(
                visible = pastHeader,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                NijiyomeFloatingToolbar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } },
                ) {
                    TextButton(onClick = actions.onWatch, enabled = state.playable) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(watchLabel(state), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                    }
                    if (state.sendAvailable) {
                        IconButton(onClick = actions.onSendToDevice) {
                            Icon(Icons.Outlined.Cast, contentDescription = stringResource(Res.string.details_send_device))
                        }
                    } else if (state.phones.isNotEmpty()) {
                        SendToPhoneButton(state, actions)
                    }
                    DownloadMenuButton(state, actions, icon = Icons.Outlined.Download)
                    if (rememberListsAvailable()) ListButton(media = loaded, compact = true)
                }
            }
        }
    }
}

@Composable
internal fun watchLabel(state: DetailsUiState): String = when (state.watchAction) {
    WatchAction.START -> stringResource(Res.string.details_watch_ep, state.continueEpisode)
    WatchAction.CONTINUE -> stringResource(Res.string.details_continue, state.continueEpisode)
    WatchAction.REWATCH -> stringResource(Res.string.details_rewatch)
    WatchAction.UP_TO_DATE -> stringResource(if (state.hasProgress) Res.string.details_up_to_date else Res.string.details_coming_soon)
}

/** Phone: one column (header, source line, tabs). Wide (≥ 840 dp): tabs on the left, source + send panels on the right. */
@Composable
private fun DetailsBody(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    listState: LazyListState,
    bottomPadding: Dp,
    wide: Boolean,
    /** Width of the content: from 600 dp C caps its card to a reading width, from 720 dp D puts it beside the facts. */
    bodyWidth: Dp,
    selectedTab: DetailsTab,
    /** The top bar shows its copy of the tabs: the list's one is hidden from TalkBack. */
    tabsPinned: Boolean,
    onTab: (DetailsTab) -> Unit,
) {
    var range by rememberSaveable(media.ref) { mutableIntStateOf((state.continueEpisode - 1).coerceAtLeast(0) / RANGE_SIZE) }
    var showEarlier by rememberSaveable(media.ref, range) { mutableStateOf(false) }
    val look = LocalLook.current
    val editorial = look.pageStyle == PageStyle.EDITORIAL
    val layout = look.headerLayout
    val place = when {
        wide -> CardPlace.SIDE_COLUMN
        layout == HeaderLayout.NEXT_UP && bodyWidth >= MEDIUM_BREAKPOINT -> CardPlace.BESIDE
        // under ~720 dp the grid beside a 340 dp card has ~50 dp cells
        layout == HeaderLayout.FACTS_NEXT_UP && bodyWidth >= BESIDE_BREAKPOINT -> CardPlace.BESIDE
        else -> CardPlace.STACKED
    }
    val head = rememberHeadModel(media, state, layout, editorial, place)
    // Built once per data change, not in every pass of the list builder (scroll jank in long series).
    val eps = (state.episodes as? LoadState.Ok)?.value
    val nextAiring = state.detailsOrNull?.nextAiring
    val nowSec = state.clockSec ?: (System.currentTimeMillis() / 1000)
    val all = remember(eps, nextAiring, media.episodes, nowSec) { eps?.let { buildEpisodeItems(it, nextAiring, media.episodes, nowSec) } }
    val lists = remember(all, state.episodeQuery) { all?.let { EpisodeLists(it, filterEpisodes(it, state.episodeQuery)) } }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottomPadding)) {
                detailsItems(
                    media, state, actions, selectedTab, onTab, range, { range = it },
                    phone = false, editorial = editorial, episodeLists = lists,
                    showEarlier = showEarlier, onShowEarlier = { showEarlier = true }, tabsPinned = tabsPinned, head = head, cardColumns = 2,
                )
            }
            Column(
                Modifier
                    .width(SIDE_WIDTH)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(16.dp)
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // C / D: the next-episode card heads the side column (the panel below already shows the source)
                if (layout.hasCard) NextUpCard(head, media, state, actions, footer = false, modifier = Modifier.fillMaxWidth())
                SourcePanelCard(state, actions)
                if (state.sendAvailable) SendToDevicePanel(state, actions) else if (state.phones.isNotEmpty()) SendToPhonePanel(state, actions)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottomPadding)) {
            detailsItems(
                media, state, actions, selectedTab, onTab, range, { range = it },
                phone = true, editorial = editorial, episodeLists = lists,
                showEarlier = showEarlier, onShowEarlier = { showEarlier = true }, tabsPinned = tabsPinned, head = head,
            )
        }
    }
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
                TextButton(onClick = onConfirm) { Text(stringResource(Res.string.details_send_action)) }
            } else {
                TextButton(onClick = onRecheck, enabled = !checking) {
                    Text(stringResource(if (checking) Res.string.details_send_checking else Res.string.details_send_recheck), maxLines = 1)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.details_cancel)) } },
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
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.details_send_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.details_cancel)) } },
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
            OutlinedButton(onClick = actions.onBack) { Text(stringResource(Res.string.details_adult_back)) }
            Button(onClick = actions.onAcceptAdult) { Text(stringResource(Res.string.details_adult_show)) }
        }
    }
}

internal enum class DetailsTab { EPISODES, INFO, CHARACTERS, RELATED }

internal const val RANGE_SIZE = 100
private val WIDE_BREAKPOINT = 840.dp
private val MEDIUM_BREAKPOINT = 600.dp
private val BESIDE_BREAKPOINT = 720.dp
private val SIDE_WIDTH = 380.dp

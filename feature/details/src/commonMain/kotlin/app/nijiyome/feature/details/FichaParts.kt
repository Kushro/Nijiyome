// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardToDetailsTarget
import app.nijiyome.designsystem.motion.motionFadeIn
import app.nijiyome.designsystem.motion.motionFadeOut
import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import app.nijiyome.feature.home.rememberFollowControl
import app.nijiyome.feature.library.ListButton
import app.nijiyome.model.AiringRepository
import app.nijiyome.model.HeaderLayout
import app.nijiyome.model.ListStatus
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PageStyle
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.alternativeTitle
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuEntriesDropdown
import app.nijiyome.ui.patterns.MenuEntriesSheet
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.SelectionState
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.rememberSelectionState
import app.nijiyome.ui.platform.platformPostsNotifications
import app.nijiyome.ui.platform.rememberNotificationPermission
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.ui_back
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.ui.media.UpscalableImage
import app.nijiyome.upscale.UpscaleImageKind

// Sparkles redesign, lane S4: the pieces the three Ficha structures (Panel, Ruta, Cine) share. The structures only arrange them;
// state, view model and actions are the same (docs/redesign/artefacto/screens-a.js `ficha`).

/** UI state of one Ficha that every structure shares: tab, episode range, the folded earlier episodes, selection and focus. */
@Stable
internal class FichaUi(
    val selection: SelectionState<Int>,
    private val tabState: MutableIntState,
    private val rangeState: MutableIntState,
) {
    var tab: DetailsTab
        get() = DetailsTab.entries[tabState.intValue.coerceIn(0, DetailsTab.entries.size - 1)]
        set(value) {
            tabState.intValue = value.ordinal
        }

    var range: Int
        get() = rangeState.intValue
        set(value) {
            if (value != rangeState.intValue) showEarlier = false
            rangeState.intValue = value
        }

    /** The episodes before the one to continue are folded behind one row until this is set. */
    var showEarlier by mutableStateOf(false)

    /** Ruta: the station shown in the detail card (null = the episode to continue). */
    var focusEpisode by mutableStateOf<Int?>(null)

    /** Cine (wide): the side drawer with the tabs is open. */
    var drawerOpen by mutableStateOf(true)
}

@Composable
internal fun rememberFichaUi(media: Media, state: DetailsUiState): FichaUi {
    val selection = rememberSelectionState<Int>()
    val tab = rememberSaveable(media.ref) { mutableIntStateOf(DetailsTab.EPISODES.ordinal) }
    val range = rememberSaveable(media.ref) { mutableIntStateOf((state.continueEpisode - 1).coerceAtLeast(0) / RANGE_SIZE) }
    return remember(media.ref, selection) { FichaUi(selection, tab, range) }
}

/** Episode rows built once per data change: all of them and the search matches. */
@Composable
internal fun rememberEpisodeLists(media: Media, state: DetailsUiState): EpisodeLists? {
    val eps = (state.episodes as? LoadState.Ok)?.value
    val nextAiring = state.detailsOrNull?.nextAiring
    val nowSec = state.clockSec ?: (System.currentTimeMillis() / 1000)
    val all = remember(eps, nextAiring, media.episodes, nowSec) { eps?.let { buildEpisodeItems(it, nextAiring, media.episodes, nowSec) } }
    return remember(all, state.episodeQuery) { all?.let { EpisodeLists(it, filterEpisodes(it, state.episodeQuery)) } }
}

// ---------------------------------------------------------------- hero

/**
 * The title header: backdrop, poster (the target of the card-to-Ficha transition, with the same key as the cards), title, alternative
 * title and the copy / AniList / MAL quick actions. A phone with Diseño y estilo › Página = Editorial gets the editorial hero instead.
 * [backdrop] = false draws only the poster row (Ruta).
 */
@Composable
internal fun FichaHero(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    compact: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Boolean = true,
) {
    val key = media.toCardData().key
    val adult = state.detailsOrNull?.isAdult == true || media.looksAdult()
    if (compact && backdrop && LocalLook.current.pageStyle == PageStyle.EDITORIAL) {
        Box(modifier.fillMaxWidth().decor(DecorRegion.HERO, rememberHeroRegion(media, state)).cardToDetailsTarget(key)) {
            EditorialHeader(media, state, actions, adultBadge = adult)
        }
        return
    }
    val background = MaterialTheme.colorScheme.background
    val title = media.displayTitle(state.titleLanguage)
    val blurred = state.coverBlurred
    val blurModifier = if (blurred) Modifier.blur(24.dp).clickable(onClick = actions.onRevealCover) else Modifier
    val art = headerBackdrop(media, state.artwork)
    val bannerHeight = if (compact) 176.dp else 248.dp
    val posterWidth = if (compact) 96.dp else 120.dp
    Box(modifier.fillMaxWidth().decor(DecorRegion.HERO, rememberHeroRegion(media, state))) {
        if (backdrop) {
            Box(Modifier.fillMaxWidth().height(bannerHeight)) {
                UpscalableImage(
                    url = art.url,
                    contentDescription = null,
                    kind = UpscaleImageKind.BACKDROP,
                    upscale = !blurred,
                    contentScale = ContentScale.Crop,
                    alignment = if (art.lowRes && media.banner != null) Alignment.BottomCenter else Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest).then(blurModifier),
                )
                // A veil that ends in the page background keeps the title readable on any artwork.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(background.copy(alpha = if (art.lowRes) 0.35f else 0.1f), background.copy(alpha = 0.7f), background)),
                    ),
                )
                if (blurred) MatureReveal(actions, Modifier.align(Alignment.Center))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = if (backdrop) bannerHeight - 72.dp else 0.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MediaCover(
                url = media.cover,
                contentDescription = stringResource(Res.string.rd_s4_poster_of, title),
                modifier = Modifier.width(posterWidth).cardToDetailsTarget(key).then(blurModifier),
            )
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (!compact) {
                            media.alternativeTitle(title)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
                TitleQuickActions(title = title, media = media, actions = actions, adultBadge = adult)
            }
        }
    }
}

// ---------------------------------------------------------------- facts

/**
 * The facts in the composition of Diseño y estilo › Cabecera: a quiet line (Línea, Siguiente) or the labelled facts (Datos, Datos y
 * siguiente: one line of label-over-value pairs from [FACTS_STRIP_MIN], the grid below it or at a large font), then the series
 * progress as a segmented track ("3 de 12 vistos", the words left out when the Episodios fact already says them).
 */
@Composable
internal fun FichaFacts(head: HeadModel, media: Media, state: DetailsUiState, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val strip = head.layout.usesFacts && maxWidth >= FACTS_STRIP_MIN && fontScale < 1.3f
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                strip -> FactsStrip(head, media, state)
                head.layout.usesFacts -> FactsGrid(head)
                else -> FactsLine(head.facts, adult = state.detailsOrNull?.isAdult == true || media.looksAdult(), nextLast = head.layout == HeaderLayout.NEXT_UP)
            }
            SeriesProgress(state, showLabel = !(strip && head.facts.any { it is HeadFact.Episodes }))
        }
    }
}

/** "3 de 12 vistos" over the direction's track, one segment per episode when the total is known. */
@Composable
internal fun SeriesProgress(state: DetailsUiState, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val total = state.totalEpisodes
    val watched = remember(state.progress, state.listProgress, total) { watchedCount(state.progress, state.listProgress, total) }
    if (watched == 0 && total == null) return
    val label = if (total != null) {
        stringResource(Res.string.rd_s4_series_progress, watched, total)
    } else {
        stringResource(Res.string.rd_s4_series_progress_open, watched)
    }
    Column(modifier.widthIn(max = 480.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (total != null && total > 0) {
            ProgressTrack(progress = (watched.toFloat() / total).coerceIn(0f, 1f), segments = total.takeIf { it <= MAX_TRACK_SEGMENTS })
        }
    }
}

/** From this width the labelled facts fit on one line. */
private val FACTS_STRIP_MIN = 560.dp

// ---------------------------------------------------------------- list status

/**
 * "En tu lista": the six statuses one tap away (the shared status picker), the entry editor (progress, score, notes and dates of the
 * library) and "Quitar de la lista". Hidden without a lists layer.
 */
@Composable
internal fun FichaStatusRow(media: Media, state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    val onSet = actions.onSetStatus ?: return
    val compact = isCompactWindow()
    val entry = state.listEntry
    val canRewatch = entry?.status == ListStatus.COMPLETED || entry?.status == ListStatus.REPEATING
    val onlyCompleted = stringResource(Res.string.rd_s4_rewatch_needs_completed)
    val picker: @Composable (Modifier) -> Unit = { m ->
        ListStatusPicker(
            selected = entry?.status?.slot(),
            onSelect = onSet,
            modifier = m,
            disabledReason = { if (it == StatusSlot.REWATCHING && !canRewatch) onlyCompleted else null },
            compact = compact,
        )
    }
    val tools: @Composable () -> Unit = {
        ListButton(
            media = media,
            anchor = { _, _, open ->
                IconButton(onClick = open) { Icon(Icons.Outlined.EditNote, contentDescription = stringResource(Res.string.rd_s4_edit_entry)) }
            },
        )
        if (entry != null) {
            IconButton(onClick = actions.onRemoveFromList) {
                Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = stringResource(Res.string.rd_s4_remove_from_list))
            }
        }
    }
    val label = stringResource(Res.string.rd_s4_in_list)
    if (compact) {
        Column(modifier.fillMaxWidth().semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                tools()
            }
            picker(Modifier.fillMaxWidth())
        }
    } else {
        // The six statuses are named, never cut to icons: beside the label and tools only where the row really has the room
        // (Panel at full width); narrower (Cine's column, a pane beside a list) the picker takes a full line under them.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            if (maxWidth >= STATUS_ONE_ROW) {
                Row(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    picker(Modifier.weight(1f))
                    tools()
                }
            } else {
                Column(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        tools()
                    }
                    picker(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** From this width the status label, the six named statuses and the tools share one row. */
private val STATUS_ONE_ROW = 860.dp

// ---------------------------------------------------------------- actions

/**
 * The visible actions of the title (DET-07, DET-09, DET-13): Favorito, Seguir (with the per-title reminder), Tráiler, Descargar (episode,
 * season or "Elegir episodios"), Compartir and Enviar a dispositivo. Named outlined buttons on wide windows, icon-over-label tiles that
 * slide sideways on phones.
 */
@Composable
internal fun FichaActionsRow(media: Media, state: DetailsUiState, actions: DetailsActions, ui: FichaUi, modifier: Modifier = Modifier) {
    val compact = isCompactWindow()
    val follow = rememberFollowControl(media)
    val reminder = rememberReminder(media, following = follow?.following == true)
    val trailer = state.detailsOrNull?.trailer
    val share = remember(media) { shareLink(media) }
    val items: @Composable () -> Unit = {
        actions.onToggleFavorite?.let { toggle ->
            FichaAction(
                icon = if (state.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(Res.string.rd_s4_action_favorite),
                onClick = toggle,
                compact = compact,
                checked = state.favorite,
            )
        }
        follow?.let { f ->
            FichaAction(
                icon = if (f.following) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd,
                label = stringResource(if (f.following) Res.string.rd_s4_action_following else Res.string.rd_s4_action_follow),
                onClick = f.toggle,
                compact = compact,
                checked = f.following,
            )
        }
        trailer?.let { t ->
            FichaAction(Icons.Outlined.Theaters, stringResource(Res.string.rd_s4_action_trailer), onClick = { actions.onOpenUrl(t.url) }, compact = compact)
        }
        DownloadAction(state, actions, ui, compact)
        if (share != null) {
            FichaAction(Icons.Outlined.Share, stringResource(Res.string.rd_s4_action_share), onClick = { actions.onShare(media) }, compact = compact)
        }
        SendAction(state, actions, compact)
        if (!compact && reminder != null) ReminderChip(reminder)
    }
    val group = Modifier.semantics { isTraversalGroup = true }
    if (compact) {
        Column(modifier.fillMaxWidth().then(group), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { items() }
            reminder?.let { ReminderChip(it, Modifier.padding(horizontal = 16.dp)) }
        }
    } else {
        FlowRow(
            modifier.fillMaxWidth().then(group),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items() }
    }
}

/** One action: a named outlined button (wide) or an icon tile with its name under it (phone). [checked] != null = a toggle. */
@Composable
private fun FichaAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
    checked: Boolean? = null,
    enabled: Boolean = true,
    description: String = label,
) {
    val scheme = MaterialTheme.colorScheme
    val on = checked == true
    val semantics = Modifier.semantics {
        if (description != label) contentDescription = description
        if (checked != null) role = Role.Switch
    }
    if (compact) {
        val content: @Composable () -> Unit = {
            Column(
                Modifier.width(76.dp).heightIn(min = 64.dp).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(width = 48.dp, height = 32.dp).background(if (on) scheme.secondaryContainer else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant) }
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        if (checked != null) {
            Surface(checked = checked, onCheckedChange = { onClick() }, enabled = enabled, shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = semantics, content = content)
        } else {
            Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = semantics, content = content)
        }
        return
    }
    val border = BorderStroke(1.dp, if (on) scheme.primary else scheme.outline)
    val content: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
    val container = if (on) scheme.secondaryContainer else Color.Transparent
    val contentColor = if (on) scheme.onSecondaryContainer else scheme.onSurface
    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        // OBI-02 / T-02: action buttons follow the direction's control radius instead of forcing pills.
        val shape = NijiyomeControls.shape
        if (checked != null) {
            Surface(checked = checked, onCheckedChange = { onClick() }, enabled = enabled, shape = shape, color = container, contentColor = contentColor, border = border, modifier = semantics, content = content)
        } else {
            Surface(onClick = onClick, enabled = enabled, shape = shape, color = container, contentColor = contentColor, border = border, modifier = semantics, content = content)
        }
    }
}

/** "Descargar": the episode to continue, the whole season when the best torrent is a batch, or "Elegir episodios" (selection). */
@Composable
private fun DownloadAction(state: DetailsUiState, actions: DetailsActions, ui: FichaUi, compact: Boolean) {
    var open by remember { mutableStateOf(false) }
    val episode = state.panel?.episode ?: state.continueEpisode
    val best = state.bestTorrent
    val episodeLabel = stringResource(Res.string.rd_s4_dl_episode, episode)
    val seasonLabel = stringResource(Res.string.rd_s4_dl_season)
    val pickLabel = stringResource(Res.string.rd_s4_dl_pick)
    val entries: List<MenuEntry> = buildList {
        add(
            MenuAction(episodeLabel, icon = Icons.Outlined.Download, enabled = state.playable, onClick = {
                open = false
                if (best != null && state.panel?.episode == episode) actions.onDownloadSource(best, episode) else actions.onDownloadEpisode(episode)
            }),
        )
        if (state.canDownloadSeason) {
            add(MenuAction(seasonLabel, icon = Icons.Outlined.Movie, onClick = { open = false; actions.onDownloadSeason() }))
        }
        add(
            MenuAction(pickLabel, icon = Icons.Outlined.CheckBox, onClick = {
                open = false
                ui.tab = DetailsTab.EPISODES
                ui.selection.start()
            }),
        )
    }
    Box {
        FichaAction(Icons.Outlined.Download, stringResource(Res.string.rd_s4_action_download), onClick = { open = true }, compact = compact)
        if (open) {
            if (compact) {
                MenuEntriesSheet(entries, onDismiss = { open = false })
            } else {
                MenuEntriesDropdown(expanded = true, entries = entries, onDismiss = { open = false })
            }
        }
    }
}

/**
 * "Enviar a dispositivo": Android sends to the paired PC (or opens Link to pair one); the desktop sends to a paired phone (straight to
 * it when there is one, else a menu of them).
 */
@Composable
private fun SendAction(state: DetailsUiState, actions: DetailsActions, compact: Boolean) {
    if (state.sendAvailable) {
        FichaAction(
            Icons.Outlined.Cast,
            stringResource(if (state.sending) Res.string.details_send_sending else Res.string.rd_s4_send_pc),
            onClick = actions.onSendToDevice,
            compact = compact,
            enabled = !state.sending && state.playable,
        )
        return
    }
    if (state.phones.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val entries: List<MenuEntry> = state.phones.map { phone ->
        MenuAction(phone.name, icon = Icons.Outlined.PhoneAndroid, onClick = { open = false; actions.onSendToPhone(phone) })
    }
    Box {
        FichaAction(
            Icons.Outlined.PhoneAndroid,
            stringResource(if (state.sending) Res.string.details_send_sending else Res.string.rd_s4_send_phone),
            onClick = { if (state.phones.size == 1) actions.onSendToPhone(state.phones.single()) else open = true },
            compact = compact,
            enabled = !state.sending && state.playable,
        )
        if (open) {
            if (compact) MenuEntriesSheet(entries, onDismiss = { open = false }) else MenuEntriesDropdown(true, entries, onDismiss = { open = false })
        }
    }
}

/** The per-title reminder of a followed show (A-OCULTA-01): on = a notification when an episode airs. */
internal class Reminder(val on: Boolean, val set: (Boolean) -> Unit)

@Composable
private fun rememberReminder(media: Media, following: Boolean): Reminder? {
    if (!following || !platformPostsNotifications) return null
    val koin = getKoin()
    val airing = remember(koin) { koin.getOrNull<AiringRepository>() } ?: return null
    val shows by airing.followed.collectAsState()
    val show = shows.firstOrNull { sameTitle(it.ref, media.ref) } ?: return null
    val scope = rememberCoroutineScope()
    val permission = rememberNotificationPermission { granted ->
        if (granted) scope.launch { runCatching { airing.setReminder(media.ref, true) } }
    }
    return Reminder(show.remind) { on ->
        if (on && !permission.isGranted()) {
            permission.request()
        } else {
            scope.launch { runCatching { airing.setReminder(media.ref, on) } }
        }
    }
}

private fun sameTitle(a: MediaRef, b: MediaRef): Boolean =
    (a.anilistId != null && a.anilistId == b.anilistId) || (a.malId != null && a.malId == b.malId)

@Composable
private fun ReminderChip(reminder: Reminder, modifier: Modifier = Modifier) {
    val label = stringResource(if (reminder.on) Res.string.rd_s4_remind_on else Res.string.rd_s4_remind_off)
    val description = stringResource(Res.string.rd_s4_remind_cd, label)
    val scheme = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        Surface(
            checked = reminder.on,
            onCheckedChange = reminder.set,
            shape = NijiyomeControls.shape,
            color = Color.Transparent,
            contentColor = if (reminder.on) scheme.primary else scheme.onSurfaceVariant,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.semantics {
                contentDescription = description
                role = Role.Switch
            },
        ) {
            Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (reminder.on) Icons.Outlined.Alarm else Icons.Outlined.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------- watch block

/**
 * The watch block of Diseño y estilo › Cabecera: Línea / Datos = the split Watch ▾ button and the source line, Siguiente / Datos y
 * siguiente = the next-episode card (button and source line inside). [sideColumn] = the Panel side column already shows the card.
 */
@Composable
internal fun FichaWatchBlock(head: HeadModel, media: Media, state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    val next = head.nextUp
    val sub = next.progress?.let { resumeSub(it) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (head.layout.hasCard) {
            NextUpCard(head, media, state, actions, footer = true, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp))
        } else {
            WatchButton(state, actions, Modifier.widthIn(max = 480.dp), height = 56.dp, sub = sub, progress = next.progress?.fraction)
            SourceLine(state, actions, inCard = true, withEpisode = head.layout == HeaderLayout.FACTS)
        }
        if (state.sending) {
            Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.details_send_sending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The Panel side column on wide windows: Continuar, the episode's sources and Enviar a dispositivo. */
@Composable
internal fun FichaSidePanels(head: HeadModel, media: Media, state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    Column(modifier.semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NextUpCard(head, media, state, actions, footer = true, modifier = Modifier.fillMaxWidth())
        SourcePanelCard(state, actions)
        if (state.sendAvailable) SendToDevicePanel(state, actions) else if (state.phones.isNotEmpty()) SendToPhonePanel(state, actions)
    }
}

// ---------------------------------------------------------------- top bar

/**
 * The bar over a scrolled Ficha (Panel, Ruta): back, the title, "▶ Ep. N" on phones and the pinned tabs once the list's ones went
 * under it. Before scrolling only the floating back button shows over the artwork.
 */
@Composable
internal fun FichaScrollBar(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    ui: FichaUi,
    scrolled: Boolean,
    tabsPinned: Boolean,
    onPinnedTab: (DetailsTab) -> Unit,
    modifier: Modifier = Modifier,
    /** false when the header already has its own back button (Ruta on wide windows). */
    floatingBack: Boolean = true,
) {
    val compact = isCompactWindow()
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(scrolled, enter = motionFadeIn(), exit = motionFadeOut()) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                        Text(
                            media.displayTitle(state.titleLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        if (compact && state.playable) {
                            val long = stringResource(Res.string.details_fact_cd, stringResource(Res.string.details_bar_ep, state.continueEpisode), watchLabels(state).long)
                            FilledTonalButton(
                                onClick = actions.onWatch,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = long },
                                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                shape = NijiyomeControls.shape,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.details_bar_ep, state.continueEpisode), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                        }
                    }
                    if (tabsPinned) {
                        DetailsTabs(ui.tab, onTab = onPinnedTab, modifier = Modifier, containerColor = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
        AnimatedVisibility(!scrolled && floatingBack, enter = motionFadeIn(), exit = motionFadeOut()) {
            FloatingBack(actions.onBack)
        }
    }
}

/** The back arrow with its own container, readable over any artwork. */
@Composable
internal fun FloatingBack(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onBack,
        modifier = modifier.statusBarsPadding().padding(8.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
    }
}

/** Side padding of the Ficha content. */
internal val FichaGutter: Dp = 16.dp

/** Above this many episodes the series track is one bar, not one segment each. */
private const val MAX_TRACK_SEGMENTS = 52

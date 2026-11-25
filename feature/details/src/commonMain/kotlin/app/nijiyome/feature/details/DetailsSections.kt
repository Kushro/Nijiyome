@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.details

import app.nijiyome.feature.details.resources.Res
import app.nijiyome.feature.details.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.model.RelationsView
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NoAdultContent
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.nijiyome.ui.labels.genreLabel
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.HorizontalScrollStrip
import app.nijiyome.designsystem.components.InfoChip
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SectionCard
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.feature.downloads.DownloadSeasonSlot
import app.nijiyome.feature.home.FollowShowRow
import app.nijiyome.feature.library.ListButton
import app.nijiyome.feature.social.AniListSocialSlot
import app.nijiyome.feature.player.sources.SourceRow
import app.nijiyome.model.CharacterEdge
import app.nijiyome.model.Episode
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.SocialRepository
import org.koin.compose.getKoin
import app.nijiyome.model.MediaDetails
import app.nijiyome.model.MediaRelation
import app.nijiyome.model.MediaTag
import app.nijiyome.model.NextAiring
import app.nijiyome.model.Recommendation
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.StaffEdge
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.media.MediaCard
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.alternativeTitle
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.progressFraction
import app.nijiyome.ui.state.userMessage
import coil3.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.delay

/** Episode rows built once per data change (not in every pass of the list builder): all of them and the search matches. */
internal data class EpisodeLists(val all: List<EpisodeItem>, val filtered: List<EpisodeItem>)

/** Key of the tabs item: the pinned copy in the top bar shows once it went under the bar. */
internal const val TABS_KEY = "tabs"

/**
 * Mount points of wave B in Details (checklist `details.download-season-mount`, `details.comments-mount`): rendered under the
 * episode list. FollowShowRow was removed here because the Header already has the primary Seguir action toggle.
 */
@Composable
internal fun WaveBSlots(media: Media) {
    DownloadSeasonSlot(media)
    // socialModule binds SocialRepository on Android (always); the slot's ViewModel needs it.
    if (rememberSocialAvailable()) AniListSocialSlot(media)
}

@Composable
internal fun rememberSocialAvailable(): Boolean {
    val koin = getKoin()
    return remember(koin) { koin.getOrNull<SocialRepository>() != null }
}

/** Epoch seconds, refreshed every minute while composed (next-episode countdown). */
@Composable
internal fun rememberNowSec(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(COUNTDOWN_TICK_MS)
            now = System.currentTimeMillis() / 1000
        }
    }
    return now
}

/** Download icon with its menu: "Descargar episodio N" and, when the best torrent is a batch, "Descargar temporada". */
@Composable
internal fun DownloadMenuButton(state: DetailsUiState, actions: DetailsActions, icon: ImageVector, outlined: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.details_download)
    Box {
        if (outlined) {
            OutlinedIconButton(onClick = { open = true }) { Icon(icon, contentDescription = description) }
        } else {
            IconButton(onClick = { open = true }) { Icon(icon, contentDescription = description) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val panel = state.panel
            val best = state.bestTorrent
            if (panel != null && best != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.details_download_episode, panel.episode)) },
                    onClick = {
                        open = false
                        actions.onDownloadSource(best, panel.episode)
                    },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                )
                if (state.canDownloadSeason) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.details_download_season)) },
                        onClick = {
                            open = false
                            actions.onDownloadSeason()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Movie, contentDescription = null) },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.details_download_none)) },
                    onClick = { open = false },
                    enabled = false,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- source panel

/** sources.js `summary`: mode + Settings link, the chosen row (SourceRow of :feature:player), who chose it, "Ver todas (n)". */
@Composable
internal fun SourcePanelCard(state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    val panel = state.panel
    SectionCard(modifier = modifier, title = stringResource(Res.string.details_src_title, panel?.episode ?: state.continueEpisode)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val mode = stringResource(if (state.mode == SourceMode.MANUAL) UiR.string.src_mode_manual else UiR.string.src_mode_auto)
            Text(
                stringResource(Res.string.details_src_mode, mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { actions.onOpenSettings(SettingsSection.PLAYER) }) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.details_src_settings), modifier = Modifier.padding(start = 4.dp))
            }
        }
        when {
            !state.hasExtensions -> EmptyState(
                icon = Icons.Outlined.Extension,
                title = stringResource(Res.string.details_no_extension_title),
                message = stringResource(Res.string.details_no_extension_body),
                actionLabel = stringResource(Res.string.details_go_extensions),
                onAction = actions.onOpenExtensions,
            )
            panel == null -> DelayedLoading()
            else -> when (val sources = panel.sources) {
                LoadState.Loading -> DelayedLoading()
                is LoadState.Err -> ErrorState(
                    message = sources.cause.userMessage(),
                    retryLabel = stringResource(UiR.string.ui_retry),
                    onRetry = actions.onRetrySources,
                    compact = true,
                )
                is LoadState.Ok -> {
                    val shown = state.shownSource
                    if (shown == null) {
                        EmptyState(
                            icon = Icons.Outlined.Extension,
                            title = stringResource(Res.string.details_no_sources_title),
                            message = stringResource(Res.string.details_no_sources_message),
                            actionLabel = stringResource(Res.string.details_open_extensions),
                            onAction = actions.onOpenExtensions,
                        )
                    } else {
                        SourceRow(
                            source = shown,
                            onClick = { actions.onOpenPicker(false) },
                            compact = true,
                            onDownload = if (shown.candidate.kind == SourceKind.TORRENT) {
                                { actions.onDownloadSource(shown, panel.episode) }
                            } else {
                                null
                            },
                        )
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(if (state.selectedKey == null) Res.string.details_src_by_system else Res.string.details_src_by_you),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { actions.onOpenPicker(false) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.details_src_all, sources.value.size), modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/** Side panel "Enviar a dispositivo" of the wide layout (prototype `details__side`). */
@Composable
internal fun SendToDevicePanel(state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, title = stringResource(Res.string.details_send_device)) {
        val pc = state.pairedPc
        if (pc == null) {
            Text(
                stringResource(Res.string.details_send_unpaired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = actions.onOpenLinked) { Text(stringResource(Res.string.details_send_pair)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(pc.serverName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (state.pcAvailable) Res.string.details_send_online else Res.string.details_send_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = actions.onSendToDevice, enabled = !state.sending) {
                    if (state.sending) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (state.sending) Res.string.details_send_sending else Res.string.details_send_action))
                }
            }
        }
    }
}

/**
 * Desktop side panel "Enviar a dispositivo": this PC's paired phones (Tatakai Next's Ficha menu). Picking one asks before the
 * Link session is started for it.
 */
@Composable
internal fun SendToPhonePanel(state: DetailsUiState, actions: DetailsActions, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, title = stringResource(Res.string.details_send_device)) {
        for (phone in state.phones) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(phone.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (phone.online) Res.string.details_send_phone_online else Res.string.details_send_phone_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { actions.onSendToPhone(phone) }, enabled = !state.sending && state.playable) {
                    if (state.sending) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (state.sending) Res.string.details_send_sending else Res.string.details_send_action))
                }
            }
        }
    }
}

/** Desktop compact toolbar: "Enviar a dispositivo" with a menu of the paired phones (straight to the one when there is one). */
@Composable
internal fun SendToPhoneButton(state: DetailsUiState, actions: DetailsActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { if (state.phones.size == 1) actions.onSendToPhone(state.phones.single()) else open = true },
            enabled = !state.sending && state.playable,
        ) { Icon(Icons.Outlined.Cast, contentDescription = stringResource(Res.string.details_send_device)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (phone in state.phones) {
                DropdownMenuItem(
                    text = { Text(phone.name) },
                    leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onSendToPhone(phone)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- tabs

@Composable
internal fun DetailsTabs(
    tab: DetailsTab,
    onTab: (DetailsTab) -> Unit,
    modifier: Modifier = Modifier.padding(top = 4.dp),
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    // Text tabs from the 16 dp margin (each Tab pads 16 dp itself): no 52 dp edge padding and no 90 dp minimum, so the row only
    // scrolls when the labels really do not fit. One divider under the row.
    PrimaryScrollableTabRow(
        selectedTabIndex = tab.ordinal,
        modifier = modifier,
        containerColor = containerColor,
        edgePadding = 0.dp,
        divider = { HorizontalDivider() },
        minTabWidth = 0.dp,
    ) {
        DetailsTab.entries.forEach { t ->
            Tab(
                selected = t == tab,
                onClick = { onTab(t) },
                text = { Text(stringResource(t.labelRes())) },
            )
        }
    }
}

private fun DetailsTab.labelRes(): StringResource = when (this) {
    DetailsTab.EPISODES -> Res.string.details_episodes
    DetailsTab.INFO -> Res.string.details_info
    DetailsTab.CHARACTERS -> Res.string.details_characters
    DetailsTab.RELATED -> Res.string.details_related
    DetailsTab.REVIEWS -> Res.string.rd_s4_tab_reviews
}

/**
 * Ficha "Primero el episodio": "Episodios anteriores (a–b)", the fold above the episode to continue; one tap shows them (remembered
 * per title and range).
 */
@Composable
internal fun EarlierEpisodesRow(from: Int, to: Int, onShow: () -> Unit) {
    val description = stringResource(Res.string.details_earlier_show, from, to)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onShow)
            .semantics { contentDescription = description }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            stringResource(Res.string.details_earlier, from, to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ---------------------------------------------------------------- info

/** Technical sheet (classic AnimeDetailLayout facts), tags with relevance, trailer, external links, ids and titles. */
@Composable
internal fun InfoTab(media: Media, state: DetailsUiState, actions: DetailsActions) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = stringResource(Res.string.details_synopsis)) {
            Text(
                media.synopsis?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.details_no_synopsis),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Every header composition leaves the genres here (one place, tappable).
        if (media.genres.isNotEmpty()) {
            SectionCard(title = stringResource(Res.string.details_genres)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    media.genres.forEach { genre ->
                        AssistChip(onClick = { actions.onOpenGenre(genre) }, label = { Text(genreLabel(genre)) }) // BUG-072
                    }
                }
            }
        }
        when (val d = state.details) {
            LoadState.Loading -> DelayedLoading()
            is LoadState.Err -> ErrorState(
                message = d.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = actions.onRetryDetails,
                compact = true,
            )
            is LoadState.Ok -> d.value?.let { details -> FactsCards(details, actions) }
        }
        SectionCard(title = stringResource(Res.string.details_info_ids)) {
            media.ref.anilistId?.let { KeyValue(stringResource(Res.string.details_info_anilist), "#$it") }
            media.ref.malId?.let { KeyValue(stringResource(Res.string.details_info_mal), "#$it") }
            media.ref.kitsuId?.let { KeyValue(stringResource(Res.string.details_info_kitsu), "#$it") }
            KeyValue(stringResource(Res.string.details_info_provider), media.sourceProvider)
            statusPill(media.status)?.let { (label, _) -> KeyValue(stringResource(Res.string.details_info_status), label, mono = false) }
            val season = listOfNotNull(media.season?.let { seasonLabel(it) }, media.year?.toString()).joinToString(" ")
            if (season.isNotEmpty()) KeyValue(stringResource(Res.string.details_info_season), season, mono = false)
            media.episodes?.let { KeyValue(stringResource(Res.string.details_info_episodes), it.toString()) }
        }
        val others = (media.titles.values + state.detailsOrNull?.synonyms.orEmpty())
            .filter { it.isNotBlank() && it != media.displayTitle(state.titleLanguage) }
            .distinct()
        if (others.isNotEmpty()) {
            SectionCard(title = stringResource(Res.string.details_info_titles)) {
                others.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun FactsCards(details: MediaDetails, actions: DetailsActions) {
    SectionCard(title = stringResource(Res.string.details_facts)) {
        details.format?.let { KeyValue(stringResource(Res.string.details_fact_format), formatLabel(it), mono = false) }
        details.source?.let { KeyValue(stringResource(Res.string.details_fact_source), sourceLabel(it), mono = false) }
        details.durationMin?.let { KeyValue(stringResource(Res.string.details_fact_duration), stringResource(Res.string.details_fact_minutes, it), mono = false) }
        details.startDate?.let { KeyValue(stringResource(Res.string.details_fact_start), formatAirDate(it), mono = false) }
        details.endDate?.let { KeyValue(stringResource(Res.string.details_fact_end), formatAirDate(it), mono = false) }
        details.meanScore?.let { KeyValue(stringResource(Res.string.details_fact_mean_score), "$it%") }
        details.popularity?.let { KeyValue(stringResource(Res.string.details_fact_popularity), String.format(Locale.getDefault(), "%,d", it)) }
        details.favourites?.let { KeyValue(stringResource(Res.string.details_fact_favourites), String.format(Locale.getDefault(), "%,d", it)) }
        details.countryOfOrigin?.let { KeyValue(stringResource(Res.string.details_fact_country), countryLabel(it), mono = false) }
        if (details.producers.isNotEmpty()) {
            KeyValue(stringResource(Res.string.details_fact_producers), details.producers.joinToString(", "), mono = false)
        }
        if (details.studios.isNotEmpty()) {
            Text(
                stringResource(Res.string.details_fact_studios),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details.studios.forEach { studio ->
                    AssistChip(
                        onClick = { actions.onOpenStudio(studio) },
                        label = { Text(studio) },
                        leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
    if (details.tags.isNotEmpty()) TagsCard(details.tags)
    details.trailer?.let { trailer ->
        SectionCard(title = stringResource(Res.string.details_trailer)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { actions.onOpenUrl(trailer.url) },
                contentAlignment = Alignment.Center,
            ) {
                trailer.thumbnail?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = stringResource(Res.string.details_trailer_play),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
    if (details.externalLinks.isNotEmpty()) {
        SectionCard(title = stringResource(Res.string.details_links)) {
            details.externalLinks.forEach { (label, url) ->
                TextButton(onClick = { actions.onOpenUrl(url) }) {
                    Text(label, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
    }
}

/** Tags with their relevance %; spoiler tags hidden unless the switch is on (classic behaviour). */
@Composable
private fun TagsCard(tags: List<MediaTag>) {
    var spoilers by rememberSaveable { mutableStateOf(false) }
    var all by rememberSaveable { mutableStateOf(false) }
    val visible = tags.filter { spoilers || !it.isSpoiler }.sortedByDescending { it.rank ?: 0 }
    val hiddenSpoilers = tags.count { it.isSpoiler }
    SectionCard(title = stringResource(Res.string.details_tags)) {
        if (hiddenSpoilers > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.details_tags_spoilers, hiddenSpoilers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = spoilers, onCheckedChange = { spoilers = it })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (all) visible else visible.take(TAGS_COLLAPSED)).forEach { tag ->
                Pill(
                    tag.rank?.let { stringResource(Res.string.details_tag_rank, tag.name, it) } ?: tag.name,
                    tone = if (tag.isSpoiler) PillTone.WARN else PillTone.NEUTRAL,
                )
            }
        }
        if (!all && visible.size > TAGS_COLLAPSED) {
            TextButton(onClick = { all = true }) { Text(stringResource(Res.string.details_tags_all, visible.size)) }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, mono: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // A long value (Productoras) wraps in its own 60 %: the label keeps 40 % and never breaks letter by letter.
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

// ---------------------------------------------------------------- characters

/** Characters tab: AniList character edges (paged, infinite scroll) with the Japanese voice actor, then the staff. */
internal fun LazyListScope.charactersItems(state: DetailsUiState, actions: DetailsActions) {
    item(key = "characters-start", contentType = "trigger") {
        LaunchedEffect(Unit) { if (state.characters == null) actions.onLoadCharacters() }
    }
    val c = state.characters
    when {
        c == null || (c.items.isEmpty() && c.loading) -> item(key = "characters-loading", contentType = "loading") { DelayedLoading() }
        c.items.isEmpty() && c.error != null -> item(key = "characters-error", contentType = "error") {
            ErrorState(
                message = c.error.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = actions.onLoadCharacters,
                compact = true,
            )
        }
        c.items.isEmpty() -> item(key = "characters-empty", contentType = "empty") {
            EmptyState(
                icon = Icons.Outlined.Groups,
                title = stringResource(Res.string.details_characters_empty_title),
                message = stringResource(Res.string.details_characters_empty),
            )
        }
        else -> {
            items(c.items, key = { "ch-${it.character.id}" }, contentType = { "character" }) { edge ->
                CharacterEdgeRow(edge, onClick = { actions.onOpenCharacter(edge.character.id) })
            }
            if (c.hasNext || c.error != null) {
                item(key = "characters-more-${c.page}", contentType = "load-more") {
                    if (c.error == null) LaunchedEffect(c.page) { actions.onLoadCharacters() }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        if (c.loading) {
                            DelayedLoading()
                        } else {
                            OutlinedButton(onClick = actions.onLoadCharacters) { Text(stringResource(Res.string.details_characters_more)) }
                        }
                    }
                }
            }
        }
    }
    item(key = "staff", contentType = "staff") { StaffSection(state.staff) }
}

@Composable
private fun CharacterEdgeRow(edge: CharacterEdge, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(url = edge.character.image, modifier = Modifier.width(48.dp), shape = CircleShape, aspectRatio = 1f)
        Column(Modifier.weight(1f)) {
            Text(edge.character.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            edge.role?.let {
                Text(characterRoleLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        preferredVoiceActor(edge)?.let { va ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(va.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                va.language?.let {
                    Text(languageLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MediaCover(url = va.image, modifier = Modifier.width(48.dp), shape = CircleShape, aspectRatio = 1f)
        }
    }
}

@Composable
private fun StaffSection(staff: LoadState<List<StaffEdge>>?) {
    when (staff) {
        null, LoadState.Loading -> Unit
        is LoadState.Err -> Unit // the characters list carries the error / retry; staff is secondary
        is LoadState.Ok -> if (staff.value.isNotEmpty()) {
            SectionCard(modifier = Modifier.padding(16.dp), title = stringResource(Res.string.details_staff)) {
                staff.value.forEach { edge ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaCover(url = edge.staff.image, modifier = Modifier.width(40.dp), shape = CircleShape, aspectRatio = 1f)
                        Text(edge.staff.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        edge.role?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- related

/** Seasons / watch order (current highlighted), AniList relations by type and recommendations; every card opens its Details. */
@Composable
internal fun RelatedTab(media: Media, state: DetailsUiState, actions: DetailsActions) {
    LaunchedEffect(Unit) { actions.onLoadRelated() }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SeasonsSection(media, state.seasons, state.titleLanguage, actions)
        // 2026-11 UI refresh: graph or list (Diseño y estilo › Relacionados; the toggle here changes and remembers it).
        val relations = state.relations
        val seasons = (state.seasons as? LoadState.Ok)?.value.orEmpty()
        if (relations is LoadState.Ok && relations.value.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle(stringResource(Res.string.details_related)) }
                    Segmented(
                        options = listOf(
                            SegmentedOption(RelationsView.GRAPH, stringResource(Res.string.details_relations_graph), icon = Icons.Outlined.AccountTree),
                            SegmentedOption(RelationsView.LIST, stringResource(Res.string.details_relations_list), icon = Icons.AutoMirrored.Outlined.ViewList),
                        ),
                        selected = state.relationsView,
                        onSelect = actions.onRelationsView,
                        small = true,
                        iconOnly = true,
                    )
                }
                if (state.relationsView == RelationsView.GRAPH) {
                    val layout = remember(media, seasons, relations.value) { layoutRelationsGraph(media, seasons, relations.value) }
                    RelationsGraphView(layout, state.titleLanguage, actions.onOpenMedia)
                } else {
                    RelationsSection(relations, state.titleLanguage, actions, showTitle = false)
                }
            }
        } else {
            RelationsSection(relations, state.titleLanguage, actions)
        }
        RecommendationsSection(state.recommendations, state.titleLanguage, actions)
    }
}

@Composable
private fun SectionTitle(text: String) {
    app.nijiyome.designsystem.direction.SectionHeader(text, Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SeasonsSection(media: Media, seasons: LoadState<List<Media>>?, language: TitleLanguage, actions: DetailsActions) {
    when (seasons) {
        null, LoadState.Loading -> DelayedLoading()
        is LoadState.Err -> ErrorState(
            message = seasons.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = actions.onLoadRelated,
            compact = true,
            title = stringResource(Res.string.details_seasons),
        )
        is LoadState.Ok -> if (seasons.value.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(Res.string.details_seasons))
                val scroll = rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().decor(DecorRegion.ROW).horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    seasons.value.forEachIndexed { index, season ->
                        val isCurrent = season.ref == media.ref
                        Column(
                            Modifier
                                .then(
                                    if (isCurrent) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(4.dp),
                        ) {
                            Text(
                                stringResource(if (isCurrent) Res.string.details_season_current else Res.string.details_season_order, index + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TitleCard(
                                data = season.toCardData(language).copy(subtitle = season.year?.toString()),
                                onClick = { if (!isCurrent) actions.onOpenMedia(season) },
                                context = CardContext.ROW,
                                modifier = Modifier.cardToDetailsSource(season.toCardData(language).key),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationsSection(
    relations: LoadState<List<MediaRelation>>?,
    language: TitleLanguage,
    actions: DetailsActions,
    showTitle: Boolean = true,
) {
    when (relations) {
        null, LoadState.Loading -> DelayedLoading()
        is LoadState.Err -> ErrorState(
            message = relations.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = actions.onLoadRelated,
            compact = true,
            title = stringResource(Res.string.details_related),
        )
        is LoadState.Ok -> if (relations.value.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.TravelExplore,
                title = stringResource(Res.string.details_related_empty_title),
                message = stringResource(Res.string.details_related_empty_body),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showTitle) SectionTitle(stringResource(Res.string.details_related))
                val scroll = rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().decor(DecorRegion.ROW).horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    relations.value.forEach { rel ->
                        Column {
                            Text(
                                relationLabel(rel.relation),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                            val data = rel.media.toCardData(language)
                            TitleCard(
                                data = data.copy(subtitle = listOfNotNull(mediaKindLabel(rel.media), rel.media.year?.toString()).joinToString(" · ")),
                                // Manga / novels open their Details too (AniList ids); reading them is wave B.
                                onClick = { actions.onOpenMedia(rel.media) },
                                context = CardContext.ROW,
                                modifier = Modifier.cardToDetailsSource(data.key),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSection(recs: LoadState<List<Recommendation>>?, language: TitleLanguage, actions: DetailsActions) {
    when (recs) {
        null, LoadState.Loading -> DelayedLoading()
        is LoadState.Err -> ErrorState(
            message = recs.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = actions.onLoadRelated,
            compact = true,
            title = stringResource(Res.string.details_recommendations),
        )
        is LoadState.Ok -> if (recs.value.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(Res.string.details_recommendations))
                val scroll = rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().decor(DecorRegion.ROW).horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    recs.value.forEach { rec ->
                        val data = rec.media.toCardData(language)
                        TitleCard(
                            data = data.copy(
                                subtitle = if (rec.rating > 0) stringResource(Res.string.details_recommendation_votes, rec.rating) else rec.media.year?.toString(),
                            ),
                            onClick = { actions.onOpenMedia(rec.media) },
                            context = CardContext.ROW,
                            modifier = Modifier.cardToDetailsSource(data.key),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DelayedLoading() {
    Box(Modifier.fillMaxWidth().height(120.dp)) {
        if (rememberAfterDelay()) LoadingState()
    }
}

// ---------------------------------------------------------------- labels

@Composable
internal fun statusPill(status: String?): Pair<String, PillTone>? = when (status?.uppercase()) {
    "RELEASING" -> stringResource(Res.string.details_status_releasing) to PillTone.OK
    "FINISHED" -> stringResource(Res.string.details_status_finished) to PillTone.NEUTRAL
    "NOT_YET_RELEASED" -> stringResource(Res.string.details_status_upcoming) to PillTone.WARN
    "CANCELLED" -> stringResource(Res.string.details_status_cancelled) to PillTone.ERR
    "HIATUS" -> stringResource(Res.string.details_status_hiatus) to PillTone.WARN
    else -> null
}

@Composable
internal fun seasonLabel(season: String): String = when (season.uppercase()) {
    "WINTER" -> stringResource(Res.string.details_season_winter)
    "SPRING" -> stringResource(Res.string.details_season_spring)
    "SUMMER" -> stringResource(Res.string.details_season_summer)
    "FALL" -> stringResource(Res.string.details_season_fall)
    else -> season
}

internal fun formatScore(score: Int): String = String.format(Locale.ROOT, "%.1f", score / 10.0)

private const val SYNOPSIS_LINES = 4
private const val SYNOPSIS_TOGGLE_CHARS = 120
private const val TAGS_COLLAPSED = 12

/** `1999-10-20T14:15:00Z` / `1999-10-20` -> the localized medium date ("20 oct 1999"); anything else is shown as it came. */
internal fun formatAirDate(raw: String, locale: Locale = Locale.getDefault()): String =
    runCatching {
        java.time.LocalDate.parse(raw.take(10))
            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
    }.getOrDefault(raw)

private const val COUNTDOWN_TICK_MS = 60_000L
private const val UNAIRED_ALPHA = 0.5f
private val GRID_CARD_WIDTH = 120.dp

@Composable
internal fun formatLabel(format: String): String = when (format.uppercase()) {
    "TV" -> stringResource(Res.string.details_format_tv)
    "TV_SHORT" -> stringResource(Res.string.details_format_tv_short)
    "MOVIE" -> stringResource(Res.string.details_format_movie)
    "OVA" -> stringResource(Res.string.details_format_ova)
    "ONA" -> stringResource(Res.string.details_format_ona)
    "SPECIAL" -> stringResource(Res.string.details_format_special)
    "MUSIC" -> stringResource(Res.string.details_format_music)
    "MANGA" -> stringResource(Res.string.details_format_manga)
    "NOVEL" -> stringResource(Res.string.details_format_novel)
    "ONE_SHOT" -> stringResource(Res.string.details_format_one_shot)
    else -> format
}

/** AniList `MediaSource`. */
@Composable
internal fun sourceLabel(source: String): String = when (source.uppercase()) {
    "ORIGINAL" -> stringResource(Res.string.details_source_original)
    "MANGA" -> stringResource(Res.string.details_source_manga)
    "LIGHT_NOVEL" -> stringResource(Res.string.details_source_light_novel)
    "NOVEL" -> stringResource(Res.string.details_source_novel)
    "WEB_NOVEL" -> stringResource(Res.string.details_source_web_novel)
    "VISUAL_NOVEL" -> stringResource(Res.string.details_source_visual_novel)
    "VIDEO_GAME", "GAME" -> stringResource(Res.string.details_source_game)
    "WEB_MANGA" -> stringResource(Res.string.details_source_web_manga)
    "ANIME" -> stringResource(Res.string.details_source_anime)
    else -> stringResource(Res.string.details_source_other)
}

/** AniList `MediaRelation`. */
@Composable
internal fun relationLabel(relation: String): String = when (relation.uppercase()) {
    "PREQUEL" -> stringResource(Res.string.details_rel_prequel)
    "SEQUEL" -> stringResource(Res.string.details_rel_sequel)
    "PARENT" -> stringResource(Res.string.details_rel_parent)
    "SIDE_STORY" -> stringResource(Res.string.details_rel_side_story)
    "SPIN_OFF" -> stringResource(Res.string.details_rel_spin_off)
    "ALTERNATIVE" -> stringResource(Res.string.details_rel_alternative)
    "SUMMARY" -> stringResource(Res.string.details_rel_summary)
    "ADAPTATION" -> stringResource(Res.string.details_rel_adaptation)
    "SOURCE" -> stringResource(Res.string.details_rel_source)
    "CHARACTER" -> stringResource(Res.string.details_rel_character)
    "COMPILATION" -> stringResource(Res.string.details_rel_compilation)
    "CONTAINS" -> stringResource(Res.string.details_rel_contains)
    else -> stringResource(Res.string.details_rel_other)
}

@Composable
private fun mediaKindLabel(media: Media): String? =
    if (media.kind == app.nijiyome.model.MediaKind.MANGA) stringResource(Res.string.details_kind_manga) else null

/** ISO 3166 code of AniList `countryOfOrigin` -> localized country name. */
internal fun countryLabel(code: String, locale: Locale = Locale.getDefault()): String =
    Locale("", code).getDisplayCountry(locale).takeIf { it.isNotBlank() && it != code } ?: code

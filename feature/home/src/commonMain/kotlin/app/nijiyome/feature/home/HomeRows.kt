// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.HorizontalScrollStrip
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.motion.homeEntrance
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.ExtensionHomeRow
import app.nijiyome.model.FeedKind
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.SocialRepository
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.episodeAgoLine
import app.nijiyome.ui.media.mediaKey
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import kotlin.math.roundToInt
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S2: every Home row by its HomeRowIds id. The three structures place these rows in the user's order
// (DesignPrefs.homeRows); each row loads, fails and retries on its own (inline compact error), as before.

/** What a row needs to draw itself, shared by the three structures. */
internal class HomeRowCtx(
    val state: HomeUiState,
    val actions: HomeActions,
    val gate: MatureGate,
    val tonight: List<TonightItem>,
    val genres: List<String>,
    /** Feeds offered by the "Catálogo" row (Trending too when the structure has no stage). */
    val feedKinds: List<FeedKind>,
    val feed: FeedKind,
    val onFeed: (FeedKind) -> Unit,
    /** Editar Inicio is open: each row header offers "Hide". */
    val editing: Boolean,
    val onHide: (String) -> Unit,
    val nowSec: Long,
    val compact: Boolean,
) {
    val discover: HomeDiscoverState get() = state.discover
}

/** The rows [ids] (already in the user's order, hidden ones removed) as items of a Home list; [firstIndex] feeds the entrance cascade. */
internal fun LazyListScope.homeRows(ids: List<String>, ctx: HomeRowCtx, firstIndex: Int = 0) {
    ids.forEachIndexed { i, id ->
        if (id == ROW_MORE) {
            moreGenreRows(ctx, firstIndex + i)
        } else {
            item(key = "row:$id", contentType = id) { HomeRow(id, firstIndex + i, ctx) }
        }
    }
}

@Composable
private fun HomeRow(id: String, index: Int, ctx: HomeRowCtx) {
    val d = ctx.discover
    val a = ctx.actions
    val hide: (() -> Unit)? = if (ctx.editing) ({ ctx.onHide(id) }) else null
    val m = Modifier.homeEntrance(index)
    when (id) {
        ROW_CONTINUE -> HomeSection(stringResource(Res.string.rd_s2_tonight), m, onSeeAll = a.onOpenLibrary, onHide = hide) {
            TonightRow(ctx)
        }
        ROW_DOWNLOADS -> d.activity?.takeIf { it.total > 0 }?.let { activity ->
            HomeSection(stringResource(Res.string.home_downloading, activity.total), m, onSeeAll = a.onOpenDownloads, onHide = hide) {
                DownloadsStrip(activity, a.onOpenDownloads)
            }
        }
        ROW_FOLLOWED -> HomeSection(stringResource(Res.string.home_followed_title), m, onSeeAll = a.onOpenCalendar, onHide = hide) {
            RowBody(d.followed, a.onRetryFollowed) { list -> AiringCards("fol", list, ctx) }
        }
        ROW_LATEST -> HomeSection(stringResource(Res.string.home_latest_title), m, onSeeAll = a.onOpenCalendar, onHide = hide) {
            RowBody(d.latest, a.onRetryLatest) { list -> AiringCards("lat", list, ctx) }
        }
        ROW_TOP -> HomeSection(stringResource(Res.string.home_top10_title), m, onHide = hide) {
            Segmented(
                options = listOf(
                    SegmentedOption(TopWindow.TODAY, stringResource(Res.string.home_top10_today)),
                    SegmentedOption(TopWindow.WEEK, stringResource(Res.string.home_top10_week)),
                    SegmentedOption(TopWindow.MONTH, stringResource(Res.string.home_top10_month)),
                ),
                selected = d.topWindow,
                onSelect = a.onTopWindow,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            RowBody(d.top10, { a.onTopWindow(d.topWindow) }, emptyText = stringResource(Res.string.home_row_empty)) { list ->
                HomeCardRow {
                    itemsIndexed(list, key = { _, media -> "top:" + mediaKey(media) }) { i, media -> RankedCard(i + 1, media, ctx) }
                }
            }
        }
        ROW_FOR_YOU -> HomeSection(stringResource(Res.string.home_for_you_title), m, onSeeAll = a.onOpenForYou, onHide = hide) {
            RowBody(d.forYou, a.onRetryForYou) { list ->
                HomeCardRow {
                    items(list, key = { "fy:" + mediaKey(it.media) }) { item ->
                        val match = stringResource(Res.string.home_recs_affinity, (item.affinity * 100).roundToInt())
                        GatedTitleCard(d.cardFor(item.media, subtitle = match), item.media, ctx.gate, onClick = { a.onMediaClick(item.media) })
                    }
                }
            }
        }
        ROW_BECAUSE -> d.because?.let { because ->
            HomeSection(stringResource(Res.string.home_because_title, because.sourceTitle), m, onSeeAll = a.onOpenForYou, onHide = hide) {
                RowBody(because.state, a.onRetryBecause) { list -> MediaCards("bec", list, ctx) }
            }
        }
        ROW_GENRES -> HomeSection(stringResource(Res.string.home_genres), m, onHide = hide) { GenreChips(ctx.genres, a.onOpenGenre) }
        ROW_FEEDS -> FeedsSection(ctx, m, hide)
        ROW_PREMIERES -> HomeSection(stringResource(Res.string.home_upcoming_title), m, onHide = hide) {
            RowBody(d.upcoming, a.onRetryUpcoming) { list ->
                HomeCardRow {
                    items(list, key = { "up:" + mediaKey(it) }) { media ->
                        GatedTitleCard(
                            d.cardFor(media, nextLabel = premiereLabel(media)),
                            media,
                            ctx.gate,
                            onClick = { a.onMediaClick(media) },
                            context = CardContext.WISH,
                        )
                    }
                }
            }
        }
        ROW_FAVOURITES -> HomeSection(stringResource(Res.string.home_favourites_title), m, onHide = hide) {
            RowBody(d.favourites, a.onRetryFavourites) { list -> MediaCards("fav", list, ctx) }
        }
        ROW_EXTENSIONS -> HomeSection(
            stringResource(Res.string.home_ext_rows),
            m,
            onSeeAll = a.onOpenExtensions,
            seeAllLabel = stringResource(Res.string.rd_s2_manage),
            onHide = hide,
        ) {
            // Composed only when scrolled into view: the lazy request of customHomeRows() (07 §1, prototype IntersectionObserver).
            LaunchedEffect(Unit) { a.onLoadExtensionRows() }
            ExtensionRows(ctx)
        }
    }
}

/** "Catálogo": the base feeds behind one selector (Airing now, This season, Popular, Top rated; Trending without a stage). */
@Composable
private fun FeedsSection(ctx: HomeRowCtx, modifier: Modifier, hide: (() -> Unit)?) {
    val a = ctx.actions
    val row = ctx.state.feeds.firstOrNull { it.kind == ctx.feed } ?: ctx.state.feeds.firstOrNull { it.kind in ctx.feedKinds } ?: return
    HomeSection(stringResource(row.kind.labelRes()), modifier, onSeeAll = { a.onOpenFeed(row.kind) }, onHide = hide) {
        val scroll = rememberScrollState()
        Row(
            Modifier.fillMaxWidth().horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ctx.feedKinds.forEach { kind ->
                FilterChip(selected = kind == row.kind, onClick = { ctx.onFeed(kind) }, label = { Text(stringResource(kind.labelRes())) })
            }
        }
        RowBody(row.state, { a.onRetry(row.kind) }) { list -> MediaCards("feed:" + row.kind.name, list, ctx) }
    }
}

/** "Más de <género>": appended one at a time when the sentinel at the end of Home is composed (infinite genre rows). */
private fun LazyListScope.moreGenreRows(ctx: HomeRowCtx, index: Int) {
    val d = ctx.discover
    d.genreRows.filter { r -> (r.state as? LoadState.Ok)?.value?.isEmpty() != true }.forEach { row ->
        item(key = "genre-row:" + row.genre, contentType = ROW_MORE) {
            HomeSection(
                stringResource(Res.string.home_genre_row, genreLabel(row.genre)),
                Modifier.homeEntrance(index),
                onSeeAll = { ctx.actions.onOpenGenre(row.genre) },
                onHide = if (ctx.editing) ({ ctx.onHide(ROW_MORE) }) else null,
            ) {
                RowBody(row.state, { ctx.actions.onRetryGenre(row.genre) }) { list -> MediaCards("g:" + row.genre, list, ctx) }
            }
        }
    }
    if (!d.genreRowsDone) {
        item(key = "genre-sentinel:" + d.genreRows.size, contentType = "sentinel") {
            LaunchedEffect(d.genreRows.size) { ctx.actions.onNextGenreRow(ctx.genres) }
            Box(Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

@Composable
private fun MediaCards(prefix: String, list: List<Media>, ctx: HomeRowCtx) {
    HomeCardRow {
        items(list, key = { "$prefix:" + mediaKey(it) }) { media ->
            GatedTitleCard(ctx.discover.cardFor(media), media, ctx.gate, onClick = { ctx.actions.onMediaClick(media) })
        }
    }
}

@Composable
private fun AiringCards(prefix: String, list: List<AiringEpisode>, ctx: HomeRowCtx) {
    HomeCardRow {
        items(list, key = { "$prefix:" + mediaKey(it.media) + ":" + it.episode }) { ep ->
            val ago = (ctx.nowSec - ep.airingAtEpochSec).takeIf { ep.airingAtEpochSec > 0 }
            GatedTitleCard(
                ctx.discover.cardFor(ep.media, nextLabel = episodeAgoLine(ep.episode, ago), aired = ep.episode),
                ep.media,
                ctx.gate,
                onClick = { ctx.actions.onMediaClick(ep.media) },
            )
        }
    }
}

@Composable
private fun RankedCard(rank: Int, media: Media, ctx: HomeRowCtx) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 4.dp, bottom = 12.dp),
        )
        GatedTitleCard(ctx.discover.cardFor(media), media, ctx.gate, onClick = { ctx.actions.onMediaClick(media) })
    }
}

@Composable
private fun ExtensionRows(ctx: HomeRowCtx) {
    when (val state = ctx.state.extensionRows) {
        null, LoadState.Loading -> RowPlaceholder()
        is LoadState.Err -> ErrorState(
            message = stringResource(Res.string.home_ext_rows_error) + " " + state.cause.userMessage(),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = ctx.actions.onLoadExtensionRows,
            compact = true,
        )
        is LoadState.Ok -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.value.forEach { row -> ExtensionRow(row, ctx) }
        }
    }
}

@Composable
private fun ExtensionRow(row: ExtensionHomeRow, ctx: HomeRowCtx) {
    val a = ctx.actions
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.home_ext_row_from, HomeLogic.extensionRowSubtitle(row, ctx.discover.extensionNames)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { a.onOpenProvider(row.extensionId, row.sourceId) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.home_see_all)) }
        }
        HomeCardRow {
            items(row.items, key = { "ext:" + row.extensionId + ":" + it.id }) { item ->
                TitleCard(
                    TitleCardData(key = "ext:${row.extensionId}:${row.sourceId}:${item.id}", title = item.title, coverUrl = item.poster),
                    onClick = { a.onOpenProviderItem(row.extensionId, row.sourceId, item.id) },
                )
            }
        }
    }
}

@Composable
internal fun GenreChips(genres: List<String>, onGenre: (String) -> Unit) {
    val scroll = rememberScrollState()
    HorizontalScrollStrip(scrollState = scroll, showChevrons = true) {
        Row(
            Modifier.fillMaxWidth().horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { genre -> AssistChip(onClick = { onGenre(genre) }, label = { Text(genreLabel(genre)) }) }
        }
    }
}

/** "Descargando": active torrents and web downloads; every item opens Transferencias. */
@Composable
internal fun DownloadsStrip(activity: DownloadActivity, onOpen: () -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(activity.web, key = { "w:" + it.id }) { item ->
            DownloadItemCard(
                item.poster, item.title, stringResource(Res.string.home_downloading_episode, item.episode), item.progress.toFloat(), web = true,
                onClick = onOpen, modifier = Modifier.width(DOWNLOAD_CARD_WIDTH),
            )
        }
        items(activity.torrents, key = { "t:" + it.id.toString() }) { t ->
            DownloadItemCard(
                t.poster,
                t.series ?: t.name,
                t.episode?.let { stringResource(Res.string.home_downloading_episode, it) } ?: stringResource(Res.string.home_downloading_torrent),
                t.progress.toFloat(),
                web = false,
                onClick = onOpen,
                modifier = Modifier.width(DOWNLOAD_CARD_WIDTH),
            )
        }
    }
}

@Composable
internal fun DownloadItemCard(
    poster: String?,
    title: String,
    subtitle: String,
    progress: Float,
    web: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    val line = stringResource(Res.string.rd_s2_download_line, subtitle, pct)
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (poster != null) {
                MediaCover(url = poster, modifier = Modifier.width(40.dp))
            } else {
                Icon(if (web) Icons.Outlined.CloudDownload else Icons.Outlined.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                ProgressTrack(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Badge of the bell: `SocialRepository.unreadCount` when the social module is loaded (t), else 0. */
@Composable
internal fun rememberUnreadNotifications(): Int {
    val koin = getKoin()
    val flow = remember { koin.getOrNull<SocialRepository>()?.unreadCount ?: MutableStateFlow(0) }
    val count by flow.collectAsStateWithLifecycle()
    return count
}

@Composable
internal fun NotificationBell(unread: Int, onClick: () -> Unit, onScrim: Boolean = false) {
    val label = if (unread > 0) {
        stringResource(Res.string.rd_s2_notifications_unread, unread)
    } else {
        stringResource(Res.string.home_notifications)
    }
    val content: @Composable () -> Unit = {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
            Icon(Icons.Outlined.Notifications, contentDescription = label)
        }
    }
    if (onScrim) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = content,
        )
    } else {
        IconButton(onClick = onClick, content = content)
    }
}

/** Quick chips of phones: Calendario · Para ti · Manga · Novelas (phones reach the readers from here, 16 §0.11). */
@Composable
internal fun QuickChips(actions: HomeActions, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().horizontalFadingEdge(scroll).horizontalScroll(scroll).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip(stringResource(Res.string.home_chip_calendar), Icons.Outlined.CalendarMonth, actions.onOpenCalendar)
        QuickChip(stringResource(Res.string.home_for_you_title), Icons.Outlined.Explore, actions.onOpenForYou)
        QuickChip(stringResource(UiR.string.nav_manga), Icons.AutoMirrored.Outlined.MenuBook, actions.onOpenManga)
        QuickChip(stringResource(UiR.string.nav_novels), Icons.Outlined.AutoStories, actions.onOpenNovels)
    }
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/** "Primavera 2027" / "2027" / "Pronto". */
@Composable
internal fun premiereLabel(media: Media): String {
    val season = when (media.season?.uppercase()) {
        "WINTER" -> stringResource(Res.string.home_season_winter)
        "SPRING" -> stringResource(Res.string.home_season_spring)
        "SUMMER" -> stringResource(Res.string.home_season_summer)
        "FALL" -> stringResource(Res.string.home_season_fall)
        else -> null
    }
    val year = media.year
    return when {
        season != null && year != null -> "$season $year"
        year != null -> year.toString()
        else -> stringResource(Res.string.home_premiere_tba)
    }
}

internal fun FeedKind.labelRes(): org.jetbrains.compose.resources.StringResource = when (this) {
    FeedKind.TRENDING -> Res.string.home_feed_trending
    FeedKind.POPULAR -> Res.string.home_feed_popular
    FeedKind.TOP_RATED -> Res.string.home_feed_top_rated
    FeedKind.AIRING -> Res.string.home_feed_airing
    FeedKind.UPCOMING -> Res.string.home_feed_upcoming
    FeedKind.SEASONAL -> Res.string.home_feed_seasonal
    FeedKind.RECENTLY_COMPLETED -> Res.string.home_feed_completed
}

private val DOWNLOAD_CARD_WIDTH = 260.dp

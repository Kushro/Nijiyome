// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import app.nijiyome.ui.platform.platformPostsNotifications
import org.jetbrains.compose.resources.getString
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.InfoChip
import app.nijiyome.designsystem.components.ListGroupHeader
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.AiringRepository
import app.nijiyome.model.AtlasPoint
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.RecReason
import app.nijiyome.model.RecReasonKind
import app.nijiyome.model.RecommendationItem
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.labels.genreLabel
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.hypot
import kotlin.math.roundToInt

// Parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.1, worker r). Signatures FINAL (the shell and Details
// wire them).

/** Asks POST_NOTIFICATIONS (API 33+) once the user turns on something that notifies (never at app start, 16 §0.12). */
@Composable
internal fun rememberNotificationPermission(): () -> Unit {
    val permission = app.nijiyome.ui.platform.rememberNotificationPermission()
    return remember(permission) { { if (!permission.isGranted()) permission.request() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoverTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            // 2026-11 UI refresh: null when the screen is a bar / rail destination.
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
            }
        },
        actions = { actions() },
    )
}

@Composable
internal fun CalendarRow(ep: AiringEpisode, time: String, tracked: Boolean, followed: Boolean, onClick: () -> Unit, onToggleFollow: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (tracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaCover(url = ep.media.cover, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Text(ep.media.displayTitle(LocalTitleLanguage.current), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.home_calendar_episode_at, ep.episode, time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleFollow) {
                Icon(
                    // BUG-064: "Seguir" (airing calendar + notices) is not the list button's bookmark.
                    if (followed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd,
                    contentDescription = stringResource(if (followed) Res.string.home_unfollow else Res.string.home_follow),
                )
            }
        }
    }
}

/**
 * `Route.ForYou` (checklist home.recs.for-you / .explanations / .feedback / .atlas): the full "Para ti" list (`RecsRepository.forYou`)
 * with affinity %, reasons chips, "No me interesa" (undo snackbar), the taste profile summary and the atlas tab (`atlas()`, Canvas with
 * clusters; tap a dot → [onMediaClick]).
 */
@Composable
fun ForYouScreen(onBack: (() -> Unit)?, onMediaClick: (Media) -> Unit, modifier: Modifier = Modifier) {
    val vm: ForYouViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val undoLabel = stringResource(Res.string.home_recs_undo)
    val dismissedMsg = stringResource(Res.string.home_recs_dismissed)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = { DiscoverTopBar(stringResource(Res.string.home_for_you_title), onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Segmented(
                    options = listOf(
                        SegmentedOption(ForYouTab.LIST, stringResource(Res.string.home_recs_tab_list), icon = Icons.Outlined.Explore),
                        SegmentedOption(ForYouTab.ATLAS, stringResource(Res.string.home_recs_tab_atlas), icon = Icons.Outlined.Hub),
                    ),
                    selected = state.tab,
                    onSelect = vm::selectTab,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                when (state.tab) {
                    ForYouTab.LIST -> RecsList(state, onMediaClick, onRetry = vm::load, onNotInterested = { item ->
                        vm.notInterested(item.media.ref)
                        snackbar.show(dismissedMsg, undoLabel) { vm.undo(item.media.ref) }
                    })
                    ForYouTab.ATLAS -> AtlasTab(state.atlas, onMediaClick, onRetry = vm::loadAtlas)
                }
            }
        }
    }
}

@Composable
private fun RecsList(state: ForYouUiState, onMediaClick: (Media) -> Unit, onRetry: () -> Unit, onNotInterested: (RecommendationItem) -> Unit) {
    when (val items = state.visible) {
        LoadState.Loading -> if (rememberAfterDelay()) LoadingState()
        is LoadState.Err -> ErrorState(message = items.cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = onRetry)
        is LoadState.Ok -> if (items.value.isEmpty()) {
            EmptyState(icon = Icons.Outlined.Explore, title = stringResource(Res.string.home_recs_empty), message = stringResource(Res.string.home_recs_empty_body))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                state.profile?.let { p ->
                    item(key = "profile") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(Res.string.home_recs_profile, p.sampleSize), style = MaterialTheme.typography.titleSmall)
                            val scroll = rememberScrollState()
                            Row(
                                Modifier.fillMaxWidth().horizontalFadingEdge(scroll).horizontalScroll(scroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                p.genres.entries.sortedByDescending { it.value }.take(6).forEach { InfoChip(genreLabel(it.key)) }
                            }
                        }
                    }
                }
                items(items.value, key = { "r:" + (it.media.ref.anilistId ?: it.media.ref.malId) }) { item ->
                    RecRow(item, onClick = { onMediaClick(item.media) }, onNotInterested = { onNotInterested(item) })
                }
            }
        }
    }
}

@Composable
internal fun reasonLabel(reason: RecReason): String = when (reason.kind) {
    RecReasonKind.GENRE -> stringResource(Res.string.home_recs_reason_genre, reason.label)
    RecReasonKind.STUDIO -> stringResource(Res.string.home_recs_reason_studio, reason.label)
    RecReasonKind.ERA -> stringResource(Res.string.home_recs_reason_era, reason.label)
    RecReasonKind.SCORE -> stringResource(Res.string.home_recs_reason_score)
    RecReasonKind.SIMILAR_TO -> stringResource(Res.string.home_recs_reason_similar, reason.label)
}

@Composable
private fun RecRow(item: RecommendationItem, onClick: () -> Unit, onNotInterested: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaCover(url = item.media.cover, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.media.displayTitle(LocalTitleLanguage.current), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Pill(stringResource(Res.string.home_recs_affinity, (item.affinity * 100).roundToInt()), tone = PillTone.OK)
            val reasonScroll = rememberScrollState()
            Row(
                Modifier.fillMaxWidth().horizontalFadingEdge(reasonScroll).horizontalScroll(reasonScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.reasons.forEach { InfoChip(reasonLabel(it)) }
            }
            TextButton(onClick = onNotInterested, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(Res.string.home_recs_not_interested)) }
        }
    }
}

@Composable
private fun AtlasTab(atlas: LoadState<List<AtlasPoint>>?, onMediaClick: (Media) -> Unit, onRetry: () -> Unit) {
    when (atlas) {
        null, LoadState.Loading -> if (rememberAfterDelay()) LoadingState()
        is LoadState.Err -> ErrorState(message = atlas.cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = onRetry)
        is LoadState.Ok -> if (atlas.value.isEmpty()) {
            EmptyState(icon = Icons.Outlined.Hub, title = stringResource(Res.string.home_recs_atlas_empty), message = stringResource(Res.string.home_recs_empty_body))
        } else {
            AtlasCanvas(atlas.value, onMediaClick)
        }
    }
}

@Composable
private fun AtlasCanvas(points: List<AtlasPoint>, onMediaClick: (Media) -> Unit) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.inversePrimary, MaterialTheme.colorScheme.outline,
    )
    val ring = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.home_recs_atlas_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth().heightIn(max = 520.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = true).pointerInput(points) {
                    detectTapGestures { tap ->
                        val pad = 20.dp.toPx()
                        val hitRadius = 28.dp.toPx()
                        val w = size.width - 2 * pad
                        val h = size.height - 2 * pad
                        val hit = points.minByOrNull { p -> hypot(pad + p.x * w - tap.x, pad + p.y * h - tap.y) }
                        if (hit != null && hypot(pad + hit.x * w - tap.x, pad + hit.y * h - tap.y) < hitRadius) onMediaClick(hit.media)
                    }
                },
            ) {
                val pad = 20.dp.toPx()
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                points.forEach { p ->
                    val c = Offset(pad + p.x * w, pad + p.y * h)
                    val color = palette[p.cluster.mod(palette.size)]
                    drawCircle(color = color, radius = if (p.recommended) 5.dp.toPx() else 7.dp.toPx(), center = c)
                    if (p.recommended) drawCircle(color = ring, radius = 7.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(stringResource(Res.string.home_recs_atlas_seen))
            InfoChip(stringResource(Res.string.home_recs_atlas_recommended))
        }
    }
}

/**
 * Checklist details.track-show: "Seguir" / "Siguiendo" row mounted by `WaveBSlots` of Details (o's file, closed) under the episode list;
 * toggles `AiringRepository.follow / unfollow` and the reminder switch (`setReminder`). Only for `kind == ANIME` and
 * `status == RELEASING | NOT_YET_RELEASED`.
 */
@Composable
fun FollowShowRow(media: Media, modifier: Modifier = Modifier) {
    if (media.kind != MediaKind.ANIME) return
    val status = media.status?.uppercase()
    if (status != "RELEASING" && status != "NOT_YET_RELEASED") return
    val koin = getKoin()
    val airing = remember { koin.getOrNull<AiringRepository>() } ?: return
    val followedFlow = remember(media.ref) { airing.isFollowed(media.ref) }
    val following by followedFlow.collectAsStateWithLifecycle(initialValue = false)
    val shows by airing.followed.collectAsStateWithLifecycle()
    val show = shows.firstOrNull { sameRef(it.ref, media.ref) }
    val scope = rememberCoroutineScope()
    val askPermission = rememberNotificationPermission()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.home_follow_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.home_follow_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (following) {
                    OutlinedButton(onClick = { scope.launch { runCatching { airing.unfollow(media.ref) } } }) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Text(stringResource(Res.string.home_following), Modifier.padding(start = 6.dp))
                    }
                } else {
                    FilledTonalButton(onClick = {
                        if (platformPostsNotifications) askPermission()
                        scope.launch { runCatching { airing.follow(media, remind = platformPostsNotifications) } }
                    }) {
                        Icon(Icons.Outlined.NotificationAdd, contentDescription = null)
                        Text(stringResource(Res.string.home_follow), Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (following && show != null && platformPostsNotifications) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Text(stringResource(Res.string.home_follow_reminder), Modifier.weight(1f).padding(horizontal = 12.dp))
                    Switch(checked = show.remind, onCheckedChange = { on ->
                        if (on) askPermission()
                        scope.launch { runCatching { airing.setReminder(media.ref, on) } }
                    })
                }
            }
        }
    }
}

/** Details header "Seguir" / "Siguiendo" action: [following] now and the [toggle] (following asks for the notification permission). */
class FollowControl(val following: Boolean, val toggle: () -> Unit)

/**
 * The follow toggle of [FollowShowRow] without its row, for the Details header; null when the title cannot be followed (not an anime
 * airing or announced, or no airing layer), so the action is left out.
 */
@Composable
fun rememberFollowControl(media: Media): FollowControl? {
    if (media.kind != MediaKind.ANIME) return null
    val status = media.status?.uppercase()
    if (status != "RELEASING" && status != "NOT_YET_RELEASED") return null
    val koin = getKoin()
    val airing = remember { koin.getOrNull<AiringRepository>() } ?: return null
    val snackbar = LocalAppSnackbar.current
    // the already loaded list first: no "Seguir" -> "Siguiendo" flash while the per-title flow starts
    val initial = remember(media.ref) { airing.followed.value.any { sameRef(it.ref, media.ref) } }
    val followedFlow = remember(media.ref) { airing.isFollowed(media.ref) }
    val following by followedFlow.collectAsStateWithLifecycle(initialValue = initial)
    // not tied to the screen: a toggle right before leaving still lands
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val run: (suspend () -> Unit) -> Unit = { block ->
        scope.launch {
            runCatching { block() }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                snackbar.show(getString(Res.string.home_follow_failed))
            }
        }
    }
    // Same rule as FollowShowRow (following works without notifications: the calendar highlights it), but the reminder is only on
    // when the permission is granted; a denied permission follows without reminders and says so.
    val permission = app.nijiyome.ui.platform.rememberNotificationPermission { granted ->
        run { airing.follow(media, remind = granted) }
        if (!granted) scope.launch { snackbar.show(getString(Res.string.home_follow_no_reminders)) }
    }
    return FollowControl(following) {
        if (following) {
            run { airing.unfollow(media.ref) }
        } else if (!platformPostsNotifications) {
            run { airing.follow(media, remind = false) }
        } else if (!permission.isGranted()) {
            permission.request()
        } else {
            run { airing.follow(media, remind = true) }
        }
    }
}

/** Kept small for the Home rows: a header with an arrow to a full screen. */
@Composable
internal fun SeeAllArrow(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.home_see_all)) }
}

/** Progress line for a download card of the "Descargando" row. */
@Composable
internal fun ProgressLine(progress: Float) {
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}

/** Assist chip helper (genre cloud). */
@Composable
internal fun GenreChip(genre: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(app.nijiyome.ui.labels.genreLabel(genre)) }) // BUG-072
}

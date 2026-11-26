// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.CalendarStructure
import app.nijiyome.model.CalendarView
import app.nijiyome.model.DesignPrefs
import app.nijiyome.model.FollowedShow
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MetadataFallbackState
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.SettingsRepository
import app.nijiyome.ui.format.formatDayRange
import app.nijiyome.ui.format.formatFullDate
import app.nijiyome.ui.format.formatLongDate
import app.nijiyome.ui.format.formatMonthYear
import app.nijiyome.ui.format.formatWeekdayDay
import app.nijiyome.ui.format.weekdayLong
import app.nijiyome.ui.format.weekdayNarrow
import app.nijiyome.ui.format.weekdayShort
import app.nijiyome.ui.look.lookFadeSpec
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.rememberShowUndo
import app.nijiyome.ui.platform.platformPostsNotifications
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.resources.ui_back
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

/**
 * `Route.Calendar` (Sparkles redesign, lane S2). The structure picked in Ajustes › Apariencia (`structures.calendar`, also switchable
 * on the screen) decides the composition; they share [CalendarViewModel]:
 * - Semana: seven columns on wide windows, a day strip and that day's list on phones; a Semana / Mes switch keeps the month grid.
 * - Tablero de salidas: one day as a departure board (time, title, episode, state, bell) with the "now" marker; it is the former
 *   Día view, and the chips pick another day of the week.
 * - Agenda: one block per day with episodes and the line of the current time.
 * Wide windows add a side column with the month (jump to any day) and "Lo que sigues" with the reminder of each title; phones keep
 * that list in an expander. "Solo lo que sigo" filters every structure; the bell on every episode follows / unfollows.
 */
@Composable
fun CalendarScreen(onBack: (() -> Unit)?, onMediaClick: (Media) -> Unit, modifier: Modifier = Modifier) {
    val vm: CalendarViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val structure = structures.calendar
    // The data of each structure: Semana loads a week (or the month when its Mes switch is on), the board one day, Agenda a week.
    LaunchedEffect(structure) { vm.setView(CalendarLayout.viewFor(structure, vm.state.value.view)) }
    val askPermission = rememberNotificationPermission()
    val updateDesign = rememberDesignUpdate()
    val metadata = rememberMetadataRepository()
    val fallback by (metadata?.fallbackState ?: NO_FALLBACK).collectAsStateWithLifecycle()
    val nowSec = rememberNowSec()
    val zone = remember { ZoneId.systemDefault() }
    val ctx = CalendarCtx(
        state = state,
        zone = zone,
        nowSec = nowSec,
        onOpen = onMediaClick,
        onToggleFollow = { ep ->
            if (!state.isFollowed(ep.media.ref)) askPermission()
            vm.toggleFollow(ep)
        },
    )
    var showFollowed by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            BoxWithConstraints {
                if (maxWidth < WIDE_MIN) {
                    DiscoverTopBar(stringResource(Res.string.rd_s2_calendar_title), onBack) {
                        IconButton(onClick = vm::goToday) { Icon(Icons.Outlined.Today, contentDescription = stringResource(Res.string.home_cal_today)) }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= WIDE_MIN
            val aside = maxWidth >= ASIDE_MIN
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (wide) {
                        item(key = "head", contentType = "head") {
                            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.rd_s2_calendar_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                                if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(app.nijiyome.ui.resources.Res.string.ui_back)) }
                            }
                        }
                    }
                    if (fallback != null) {
                        item(key = "fallback", contentType = "notice") {
                            app.nijiyome.ui.state.MetadataFallbackBanner(
                                state = fallback,
                                onRetryPrimary = {
                                    metadata?.resetProviderHealth()
                                    vm.load()
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    item(key = "tools", contentType = "tools") {
                        CalendarToolbar(
                            structure = structure,
                            state = state,
                            wide = wide,
                            onStructure = { s -> updateDesign { it.copy(structures = it.structures.copy(calendar = s)) } },
                            onShift = vm::shift,
                            onToday = vm::goToday,
                            onOnlyFollowed = { vm.setOnlyFollowed(!state.onlyFollowed) },
                        )
                    }
                    when (val days = state.visibleDays) {
                        LoadState.Loading -> item(key = "loading", contentType = "loading") { CalendarLoading() }
                        is LoadState.Err -> item(key = "error", contentType = "error") {
                            app.nijiyome.ui.state.AppErrorState(cause = days.cause, onRetry = vm::load, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        is LoadState.Ok -> when (structure) {
                            CalendarStructure.WEEK -> weekStructure(days.value, ctx, wide, monthSwitch = !aside, onView = vm::setView, onSelect = vm::selectDay)
                            CalendarStructure.DEPARTURES -> boardStructure(days.value, ctx, wide, onSelect = vm::selectDay, onShowAll = { vm.setOnlyFollowed(false) })
                            CalendarStructure.AGENDA -> agendaStructure(days.value, ctx, onShowAll = { vm.setOnlyFollowed(false) })
                        }
                    }
                    if (!aside) {
                        item(key = "followed", contentType = "followed") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FollowedExpander(state.followed.size, showFollowed) { showFollowed = !showFollowed }
                                if (showFollowed) FollowedPanel(state, ctx, askPermission, vm::setReminder, vm::unfollow)
                            }
                        }
                    }
                }
                if (aside) {
                    Column(
                        Modifier.width(ASIDE_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MiniMonth(
                            state,
                            onSelect = vm::selectDay,
                            // CAL-03: with the side column, Semana's Mes switch is the link in the month's header (one switch, not two).
                            onView = if (structure == CalendarStructure.WEEK) vm::setView else null,
                        )
                        FollowedPanel(state, ctx, askPermission, vm::setReminder, vm::unfollow)
                    }
                }
            }
        }
    }
}

/** Pure mapping between the calendar structures and the views of [CalendarViewModel] (JVM-tested). */
internal object CalendarLayout {
    /** The view whose range a [structure] needs; Semana keeps the month grid when it was showing. */
    fun viewFor(structure: CalendarStructure, current: CalendarView): CalendarView = when (structure) {
        CalendarStructure.WEEK -> if (current == CalendarView.MONTH) CalendarView.MONTH else CalendarView.WEEK
        CalendarStructure.DEPARTURES -> CalendarView.DAY
        CalendarStructure.AGENDA -> CalendarView.AGENDA
    }

    /** Index of the first episode still to air, or the list size: where the "now" marker goes. */
    fun nowIndex(episodes: List<AiringEpisode>, nowSec: Long): Int =
        episodes.indexOfFirst { it.airingAtEpochSec > nowSec }.let { if (it < 0) episodes.size else it }

    fun boardState(ep: AiringEpisode, nowSec: Long): BoardState = when {
        ep.airingAtEpochSec > nowSec -> BoardState.SCHEDULED
        nowSec - ep.airingAtEpochSec < HomeLogic.JUST_OUT_SEC -> BoardState.AIRING
        else -> BoardState.AIRED
    }
}

/** State column of the departure board. */
internal enum class BoardState { AIRED, AIRING, SCHEDULED }

/** What every structure needs to draw an episode. */
private class CalendarCtx(
    val state: CalendarUiState,
    val zone: ZoneId,
    val nowSec: Long,
    val onOpen: (Media) -> Unit,
    val onToggleFollow: (AiringEpisode) -> Unit,
) {
    val today: LocalDate get() = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()

    fun timeOf(ep: AiringEpisode): String = formatTime(ep.airingAtEpochSec)
}

// ---------------------------------------------------------------- toolbar

@Composable
private fun CalendarToolbar(
    structure: CalendarStructure,
    state: CalendarUiState,
    wide: Boolean,
    onStructure: (CalendarStructure) -> Unit,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
    onOnlyFollowed: () -> Unit,
) {
    val options = listOf(
        SegmentedOption(CalendarStructure.DEPARTURES, stringResource(Res.string.rd_s2_cal_board), icon = Icons.Outlined.DepartureBoard),
        SegmentedOption(CalendarStructure.WEEK, stringResource(Res.string.home_cal_view_week), icon = Icons.Outlined.ViewWeek),
        SegmentedOption(CalendarStructure.AGENDA, stringResource(Res.string.home_cal_view_agenda), icon = Icons.Outlined.ViewAgenda),
    )
    val filter: @Composable () -> Unit = {
        FilterChip(
            selected = state.onlyFollowed,
            onClick = onOnlyFollowed,
            label = { Text(stringResource(Res.string.rd_s2_cal_only_mine)) },
            leadingIcon = { Icon(if (state.onlyFollowed) Icons.Outlined.Check else Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
    if (wide) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Segmented(options = options, selected = structure, onSelect = onStructure)
            PeriodNav(state, onShift, onToday, Modifier.widthIn(max = 460.dp))
            filter()
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(options = options, selected = structure, onSelect = onStructure, fillWidth = true, small = true)
            PeriodNav(state, onShift, onToday, Modifier.fillMaxWidth())
            filter()
        }
    }
}

/** Previous / next page of the current view, its title and "Hoy" when the page is not the current one. */
@Composable
private fun PeriodNav(state: CalendarUiState, onShift: (Long) -> Unit, onToday: () -> Unit, modifier: Modifier) {
    val view = state.view
    val anchor = state.anchor
    val today = LocalDate.now()
    val title = when (view) {
        CalendarView.WEEK, CalendarView.AGENDA -> {
            val monday = CalendarLogic.mondayOf(anchor)
            val range = formatDayRange(monday, monday.plusDays(6))
            if (monday == CalendarLogic.mondayOf(today)) stringResource(Res.string.rd_s2_cal_this_week, range) else stringResource(Res.string.home_calendar_week_of, range)
        }
        CalendarView.MONTH -> formatMonthYear(YearMonth.from(anchor)).replaceFirstChar { it.titlecase() }
        CalendarView.DAY -> formatFullDate(anchor)
    }
    val current = when (view) {
        CalendarView.WEEK, CalendarView.AGENDA -> CalendarLogic.mondayOf(anchor) == CalendarLogic.mondayOf(today)
        CalendarView.MONTH -> YearMonth.from(anchor) == YearMonth.from(today)
        CalendarView.DAY -> anchor == today
    }
    val prev = stringResource(if (view == CalendarView.DAY) Res.string.rd_s2_cal_prev_day else if (view == CalendarView.MONTH) Res.string.rd_s2_cal_prev_month else Res.string.rd_s2_cal_prev_week)
    val next = stringResource(if (view == CalendarView.DAY) Res.string.rd_s2_cal_next_day else if (view == CalendarView.MONTH) Res.string.rd_s2_cal_next_month else Res.string.rd_s2_cal_next_week)
    val fade = lookFadeSpec<Float>()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = prev) }
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            modifier = Modifier.weight(1f, fill = false),
            label = "calendarTitle",
        ) { t ->
            Text(t, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = next) }
        if (!current) OutlinedButton(onClick = onToday) { Text(stringResource(Res.string.rd_s2_today)) }
    }
}

@Composable
private fun CalendarLoading() {
    Column(Modifier.fillMaxWidth().padding(16.dp).decor(DecorRegion.AIRING), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(LOADING_ROWS) { LoadingPlaceholder(Modifier.fillMaxWidth().heightIn(min = 56.dp)) }
    }
}

// ---------------------------------------------------------------- Semana

private fun LazyListScope.weekStructure(
    days: List<CalendarDay>,
    ctx: CalendarCtx,
    wide: Boolean,
    monthSwitch: Boolean,
    onView: (CalendarView) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val month = ctx.state.view == CalendarView.MONTH
    if (monthSwitch) item(key = "week-month", contentType = "switch") {
        Segmented(
            options = listOf(
                SegmentedOption(CalendarView.WEEK, stringResource(Res.string.home_cal_view_week), icon = Icons.Outlined.CalendarViewWeek),
                SegmentedOption(CalendarView.MONTH, stringResource(Res.string.home_cal_view_month), icon = Icons.Outlined.CalendarMonth),
            ),
            selected = if (month) CalendarView.MONTH else CalendarView.WEEK,
            onSelect = onView,
            small = true,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    when {
        month -> {
            item(key = "month-grid", contentType = "month-grid") { MonthGrid(days, ctx.state.anchor, wide, ctx, onSelect) }
            selectedDay(days, ctx.state.anchor, ctx)
        }
        wide -> item(key = "week-grid", contentType = "week-grid") { WeekColumns(days, ctx) }
        else -> {
            item(key = "week-strip", contentType = "week-strip") { WeekStrip(days, ctx.state.anchor, ctx.today, onSelect) }
            selectedDay(days, ctx.state.anchor, ctx)
        }
    }
}

/** The selected day of the week strip / month grid. */
private fun LazyListScope.selectedDay(days: List<CalendarDay>, anchor: LocalDate, ctx: CalendarCtx) {
    val day = days.firstOrNull { it.date == anchor } ?: return
    item(key = "sel:${day.date}", contentType = "day-header") { DayHeader(day.date, day.episodes.size, day.date == ctx.today) }
    if (day.episodes.isEmpty()) {
        emptyItem("sel-empty", ctx.state.onlyFollowed, whole = false, onShowAll = null)
    } else {
        eventItems("sel", day.episodes, ctx, nowLine = day.date == ctx.today)
    }
}

/** Phone week: seven touch targets (weekday, day number, episode count), the selected one filled. */
@Composable
private fun WeekStrip(days: List<CalendarDay>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).decor(DecorRegion.AIRING),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val isSel = day.date == selected
            val count = day.episodes.size
            val description = dayTitle(day.date) + ", " + pluralStringResource(Res.plurals.home_cal_episodes, count, count)
            Surface(
                onClick = { onSelect(day.date) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics {
                    contentDescription = description
                    this.selected = isSel
                },
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val onSel = MaterialTheme.colorScheme.onPrimaryContainer
                    Text(
                        weekdayShort(day.date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) onSel else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (day.date == today || isSel) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSel -> onSel
                            day.date == today -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        if (count > 0) count.toString() else "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) onSel else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Wide week: seven columns, each with its episodes and their bells. The columns share the width left of the side column (CAL-01:
 * Sunday is never cut by it); the row scrolls only when a column would fall under [WEEK_COL_MIN]. Today is outlined (CAL-04).
 */
@Composable
private fun WeekColumns(days: List<CalendarDay>, ctx: CalendarCtx) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val gaps = WEEK_COL_GAP * (days.size - 1).coerceAtLeast(0)
        val colWidth = ((maxWidth - gaps) / days.size.coerceAtLeast(1)).coerceAtLeast(WEEK_COL_MIN)
        val fits = colWidth * days.size + gaps <= maxWidth
        Row(
            (if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())).decor(DecorRegion.AIRING),
            horizontalArrangement = Arrangement.spacedBy(WEEK_COL_GAP),
        ) {
            days.forEach { day ->
                val isToday = day.date == ctx.today
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isToday) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) else null,
                    modifier = Modifier.width(colWidth),
                ) {
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.padding(start = 2.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                weekdayShort(day.date.dayOfWeek),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (isToday) TodayPill()
                        }
                        if (day.episodes.isEmpty()) {
                            Text(
                                stringResource(if (ctx.state.onlyFollowed) Res.string.rd_s2_cal_none_mine else Res.string.rd_s2_cal_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        day.episodes.forEach { ep -> EventCard(ep, ctx) }
                    }
                }
            }
        }
    }
}

/**
 * A compact episode of a week column (CAL-02): the time with a small bell in the corner, then the poster with the title (two lines at
 * most) and the episode. The episode on air now is framed in the warning color (CAL-04).
 */
@Composable
private fun EventCard(ep: AiringEpisode, ctx: CalendarCtx) {
    val tracked = ctx.state.isTracked(ep.media.ref)
    val airing = CalendarLayout.boardState(ep, ctx.nowSec) == BoardState.AIRING
    Surface(
        onClick = { ctx.onOpen(ep.media) },
        shape = MaterialTheme.shapes.small,
        color = if (tracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (airing) BorderStroke(1.5.dp, LocalSignalColors.current.warn) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (airing) stringResource(Res.string.rd_s2_cal_time_now, ctx.timeOf(ep)) else ctx.timeOf(ep),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FollowBell(ep, ctx, compact = true)
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaCover(url = ep.media.cover, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(ep.media.displayTitle(LocalTitleLanguage.current), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        episodeLabel(ep),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Month grid: weekday initials and one cell per day (days of the neighbour months dimmed). Phone cells show the day number and up
 * to three dots (tracked shows in the primary color); wide cells up to three small posters. Tapping selects the day.
 */
@Composable
private fun MonthGrid(days: List<CalendarDay>, selected: LocalDate, wide: Boolean, ctx: CalendarCtx, onSelect: (LocalDate) -> Unit) {
    val month = selected.month
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).decor(DecorRegion.AIRING),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AiringContent {
            Row(Modifier.fillMaxWidth()) {
                days.take(7).forEach { d ->
                    Text(
                        weekdayNarrow(d.date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = airingSecondaryText(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val isSel = day.date == selected
                    val inMonth = day.date.month == month
                    val count = day.episodes.size
                    val description = dayTitle(day.date) + ", " + pluralStringResource(Res.plurals.home_cal_episodes, count, count)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (wide) 0.9f else 0.8f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .then(if (day.date == ctx.today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                            .clickable { onSelect(day.date) }
                            .semantics {
                                contentDescription = description
                                this.selected = isSel
                            }
                            .padding(4.dp),
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            val onSel = MaterialTheme.colorScheme.onPrimaryContainer
                            Text(
                                day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    isSel -> onSel
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                },
                            )
                            if (wide) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), modifier = Modifier.padding(top = 4.dp)) {
                                    day.episodes.take(3).forEach { ep -> MediaCover(url = ep.media.cover, modifier = Modifier.width(26.dp)) }
                                }
                                if (count > 3) {
                                    Text(
                                        stringResource(Res.string.rd_s2_cal_more, count - 3),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSel) onSel else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else if (count > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    day.episodes.take(3).forEach { ep ->
                                        val color = when {
                                            isSel -> onSel
                                            ctx.state.isTracked(ep.media.ref) -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                                    }
                                }
                                Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = if (isSel) onSel else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tablero de salidas

private fun LazyListScope.boardStructure(
    days: List<CalendarDay>,
    ctx: CalendarCtx,
    wide: Boolean,
    onSelect: (LocalDate) -> Unit,
    onShowAll: () -> Unit,
) {
    val anchor = ctx.state.anchor
    val episodes = days.firstOrNull { it.date == anchor }?.episodes ?: days.flatMap { it.episodes }
    val isToday = anchor == ctx.today
    item(key = "board-head", contentType = "board-head") { BoardHead(anchor, isToday, ctx, onSelect) }
    if (episodes.isEmpty()) {
        emptyItem("board-empty", ctx.state.onlyFollowed, whole = false, onShowAll = onShowAll.takeIf { ctx.state.onlyFollowed })
        return
    }
    val now = if (isToday) CalendarLayout.nowIndex(episodes, ctx.nowSec) else -1
    item(key = "board", contentType = "board") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.AIRING),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AiringContent {
                if (wide) BoardColumns()
                episodes.forEachIndexed { i, ep ->
                    if (i == now) NowTick(vertical = false, nowSec = ctx.nowSec)
                    BoardRow(ep, ctx, wide)
                }
                if (now == episodes.size) NowTick(vertical = false, nowSec = ctx.nowSec)
            }
        }
    }
}

@Composable
private fun BoardHead(day: LocalDate, isToday: Boolean, ctx: CalendarCtx, onSelect: (LocalDate) -> Unit) {
    val monday = CalendarLogic.mondayOf(day)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isToday) {
                        stringResource(Res.string.rd_s2_board_today)
                    } else {
                        stringResource(Res.string.rd_s2_board_day, weekdayLong(day.dayOfWeek))
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(Res.string.rd_s2_board_sub, formatFullDate(day)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isToday) {
                val clock = formatTime(ctx.nowSec)
                val label = stringResource(Res.string.rd_s2_board_clock, clock)
                Text(
                    clock,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0L until 7L).map { monday.plusDays(it) }.forEach { d ->
                FilterChip(
                    selected = d == day,
                    onClick = { onSelect(d) },
                    label = { Text(formatWeekdayDay(d)) },
                )
            }
        }
    }
}

@Composable
private fun BoardColumns() {
    val style = MaterialTheme.typography.labelMedium
    val color = airingSecondaryText()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.rd_s2_board_col_time), style = style, color = color, modifier = Modifier.width(BOARD_TIME_WIDTH))
        Text(stringResource(Res.string.rd_s2_board_col_title), style = style, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.rd_s2_board_col_episode), style = style, color = color, modifier = Modifier.width(BOARD_EP_WIDTH))
        Text(stringResource(Res.string.rd_s2_board_col_state), style = style, color = color, modifier = Modifier.width(BOARD_STATE_WIDTH))
        Box(Modifier.width(48.dp))
    }
    HorizontalDivider(color = onAiring(MaterialTheme.colorScheme.outlineVariant, LocalContentColor.current.copy(alpha = 0.3f)))
}

@Composable
private fun BoardRow(ep: AiringEpisode, ctx: CalendarCtx, wide: Boolean) {
    val state = CalendarLayout.boardState(ep, ctx.nowSec)
    val tracked = ctx.state.isTracked(ep.media.ref)
    val past = state == BoardState.AIRED
    val title = ep.media.displayTitle(LocalTitleLanguage.current)
    Surface(
        onClick = { ctx.onOpen(ep.media) },
        shape = MaterialTheme.shapes.small,
        color = if (tracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        // ALT-04: the departure on air now is framed in the warning amber; the past ones are dimmed below.
        border = if (state == BoardState.AIRING) BorderStroke(1.5.dp, LocalSignalColors.current.warn) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dim = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            // ALT-04: board digits, monospaced and large.
            Text(
                ctx.timeOf(ep),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = dim,
                maxLines = 1,
                modifier = Modifier.width(BOARD_TIME_WIDTH),
            )
            if (wide) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MediaCover(url = ep.media.cover, modifier = Modifier.width(28.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(episodeLabel(ep), style = MaterialTheme.typography.bodyMedium, color = dim, modifier = Modifier.width(BOARD_EP_WIDTH))
                Box(Modifier.width(BOARD_STATE_WIDTH)) { StatePill(state) }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(episodeLabel(ep), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatePill(state)
            }
            FollowBell(ep, ctx)
        }
    }
}

@Composable
private fun StatePill(state: BoardState) {
    val signals = LocalSignalColors.current
    val (label, color) = when (state) {
        BoardState.AIRED -> stringResource(Res.string.rd_s2_board_aired) to MaterialTheme.colorScheme.onSurfaceVariant
        BoardState.AIRING -> stringResource(Res.string.rd_s2_board_airing) to signals.ok
        BoardState.SCHEDULED -> stringResource(Res.string.rd_s2_board_scheduled) to signals.info
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Agenda

private fun LazyListScope.agendaStructure(days: List<CalendarDay>, ctx: CalendarCtx, onShowAll: () -> Unit) {
    if (days.all { it.episodes.isEmpty() }) {
        emptyItem("empty", ctx.state.onlyFollowed, whole = true, onShowAll = onShowAll.takeIf { ctx.state.onlyFollowed })
        return
    }
    days.filter { it.episodes.isNotEmpty() }.forEach { day ->
        val isToday = day.date == ctx.today
        item(key = "d:${day.date}", contentType = "day-header") { DayHeader(day.date, day.episodes.size, isToday) }
        eventItems(day.date.toString(), day.episodes, ctx, nowLine = isToday)
    }
}

// ---------------------------------------------------------------- shared rows

@Composable
private fun DayHeader(date: LocalDate, count: Int, today: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (today) TodayPill()
        Text(dayTitle(date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(pluralStringResource(Res.plurals.home_cal_episodes, count, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayPill() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        Text(
            stringResource(Res.string.rd_s2_today),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Episode rows of one day, with the "now" line among them when the day is today. */
private fun LazyListScope.eventItems(prefix: String, episodes: List<AiringEpisode>, ctx: CalendarCtx, nowLine: Boolean) {
    val now = if (nowLine) CalendarLayout.nowIndex(episodes, ctx.nowSec) else -1
    item(key = "evs:$prefix", contentType = "events") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).decor(DecorRegion.AIRING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AiringContent {
                episodes.forEachIndexed { i, ep ->
                    if (i == now) NowTick(vertical = false, nowSec = ctx.nowSec)
                    EventRow(ep, ctx)
                }
                if (now == episodes.size) NowTick(vertical = false, nowSec = ctx.nowSec)
            }
        }
    }
}

@Composable
private fun EventRow(ep: AiringEpisode, ctx: CalendarCtx) {
    val tracked = ctx.state.isTracked(ep.media.ref)
    val airing = CalendarLayout.boardState(ep, ctx.nowSec) == BoardState.AIRING
    Surface(
        onClick = { ctx.onOpen(ep.media) },
        color = if (tracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        // ALT-13: the episode on air now framed in the warning amber.
        border = if (airing) BorderStroke(1.5.dp, LocalSignalColors.current.warn) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaCover(url = ep.media.cover, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (airing) stringResource(Res.string.rd_s2_cal_time_now, ctx.timeOf(ep)) else ctx.timeOf(ep),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(ep.media.displayTitle(LocalTitleLanguage.current), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(episodeLabel(ep), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FollowBell(ep, ctx)
        }
    }
}

/** The bell of an episode: follow the series (with its reminder) or stop following it, with undo. */
@Composable
private fun FollowBell(ep: AiringEpisode, ctx: CalendarCtx, compact: Boolean = false) {
    val followed = ctx.state.isFollowed(ep.media.ref)
    val title = ep.media.displayTitle(LocalTitleLanguage.current)
    val description = stringResource(if (followed) Res.string.rd_s2_unfollow_title else Res.string.rd_s2_follow_title, title)
    val followedMsg = stringResource(Res.string.rd_s2_followed_msg, title)
    val unfollowedMsg = stringResource(Res.string.rd_s2_unfollowed_msg, title)
    val showUndo = rememberShowUndo()
    val snackbar = app.nijiyome.designsystem.components.LocalAppSnackbar.current
    val button: @Composable (Modifier) -> Unit = { m ->
        IconToggleButton(
            checked = followed,
            onCheckedChange = {
                ctx.onToggleFollow(ep)
                if (followed) showUndo(unfollowedMsg) { ctx.onToggleFollow(ep) } else snackbar.show(followedMsg)
            },
            modifier = m,
        ) {
            Icon(
                if (followed) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd,
                contentDescription = description,
                modifier = if (compact) Modifier.size(18.dp) else Modifier,
            )
        }
    }
    if (compact) {
        // A small bell in the corner of a week cell: it takes 24 dp of the layout, its 48 dp target overflows around it.
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { button(Modifier.wrapContentSize(unbounded = true)) }
    } else {
        button(Modifier)
    }
}

private fun LazyListScope.emptyItem(key: String, onlyFollowed: Boolean, whole: Boolean, onShowAll: (() -> Unit)?) {
    item(key = key, contentType = "empty") {
        Column(Modifier.fillMaxWidth().decor(DecorRegion.EMPTY), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyArt(EmptyKind.CALENDAR)
            EmptyState(
                icon = Icons.Outlined.Today,
                title = stringResource(
                    when {
                        onlyFollowed -> Res.string.rd_s2_cal_empty_mine
                        whole -> Res.string.home_calendar_empty
                        else -> Res.string.home_cal_empty_day
                    },
                ),
                message = stringResource(if (onlyFollowed) Res.string.rd_s2_cal_empty_mine_body else Res.string.home_calendar_empty_body),
                actionLabel = if (onShowAll != null) stringResource(Res.string.rd_s2_cal_show_all) else null,
                onAction = onShowAll,
            )
        }
    }
}

// ---------------------------------------------------------------- side column / expander

@Composable
private fun FollowedExpander(count: Int, open: Boolean, onToggle: () -> Unit) {
    Surface(onClick = onToggle, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
            Text(stringResource(Res.string.rd_s2_cal_followed_expander), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
    }
}

/**
 * "Lo que sigues": every followed series with when it airs next (from the loaded range), its reminder bell and Dejar de seguir.
 * The reminder of each title needs the global "Estrenos de lo que sigues" switch on; a note says so when it is off.
 */
@Composable
private fun FollowedPanel(
    state: CalendarUiState,
    ctx: CalendarCtx,
    askPermission: () -> Unit,
    onReminder: (app.nijiyome.model.MediaRef, Boolean) -> Unit,
    onUnfollow: (app.nijiyome.model.MediaRef) -> Unit,
) {
    val globalOn = rememberAiringRemindersOn()
    val upcoming = (state.days as? LoadState.Ok)?.value.orEmpty().flatMap { it.episodes }.filter { it.airingAtEpochSec > ctx.nowSec }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.rd_s2_cal_followed_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(state.followed.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(
                    when {
                        !platformPostsNotifications -> Res.string.rd_s2_cal_followed_no_notify
                        !globalOn -> Res.string.rd_s2_cal_followed_global_off
                        else -> Res.string.rd_s2_cal_followed_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.followed.isEmpty()) {
                Text(stringResource(Res.string.rd_s2_cal_followed_empty), style = MaterialTheme.typography.bodyMedium)
            }
            state.followed.forEach { show -> FollowedRow(show, upcoming.firstOrNull { sameRef(it.media.ref, show.ref) }, askPermission, onReminder, onUnfollow) }
        }
    }
}

@Composable
private fun FollowedRow(
    show: FollowedShow,
    next: AiringEpisode?,
    askPermission: () -> Unit,
    onReminder: (app.nijiyome.model.MediaRef, Boolean) -> Unit,
    onUnfollow: (app.nijiyome.model.MediaRef) -> Unit,
) {
    val on = show.remind && platformPostsNotifications
    val description = stringResource(if (on) Res.string.rd_s2_remind_off else Res.string.rd_s2_remind_on, show.title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaCover(url = show.poster, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                next?.let {
                    val at = Instant.ofEpochSecond(it.airingAtEpochSec).atZone(ZoneId.systemDefault())
                    weekdayShort(at.dayOfWeek) + " " + formatTime(it.airingAtEpochSec)
                } ?: stringResource(Res.string.rd_s2_cal_no_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconToggleButton(
            checked = on,
            enabled = platformPostsNotifications,
            onCheckedChange = { value ->
                if (value) askPermission()
                onReminder(show.ref, value)
            },
        ) {
            Icon(if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff, contentDescription = description)
        }
        TextButton(onClick = { onUnfollow(show.ref) }) { Text(stringResource(Res.string.home_unfollow)) }
    }
}

/**
 * Desktop side column: the month of the selected day; a day jumps there (and reloads when it is outside the range). Days of the
 * neighbour months are left blank (CAL-05). With [onView] the header's link switches Semana between the week and the month grid.
 */
@Composable
private fun MiniMonth(state: CalendarUiState, onSelect: (LocalDate) -> Unit, onView: ((CalendarView) -> Unit)?) {
    val anchor = state.anchor
    val month = YearMonth.from(anchor)
    val today = LocalDate.now()
    val counts = (state.visibleDays as? LoadState.Ok)?.value.orEmpty().associate { it.date to it.episodes.size }
    val cells = CalendarLogic.monthGrid(month)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMonthYear(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onView != null) {
                    val showingMonth = state.view == CalendarView.MONTH
                    TextButton(onClick = { onView(if (showingMonth) CalendarView.WEEK else CalendarView.MONTH) }) {
                        Text(stringResource(if (showingMonth) Res.string.home_cal_view_week else Res.string.home_cal_view_month))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                cells.take(7).forEach { d ->
                    Text(
                        weekdayNarrow(d.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day.month != month.month) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            MiniMonthDay(day, isSel = day == anchor, isToday = day == today, count = counts[day], onSelect = onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MiniMonthDay(day: LocalDate, isSel: Boolean, isToday: Boolean, count: Int?, onSelect: (LocalDate) -> Unit) {
    val label = dayTitle(day) + (count?.let { ", " + pluralStringResource(Res.plurals.home_cal_episodes, it, it) } ?: "")
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .clickable { onSelect(day) }
            .semantics {
                contentDescription = label
                this.selected = isSel
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (count != null && count > 0) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ---------------------------------------------------------------- helpers

@Composable
private fun episodeLabel(ep: AiringEpisode): String =
    if (ep.episode == 1) stringResource(Res.string.rd_s2_cal_premiere, ep.episode) else stringResource(Res.string.rd_s2_ep_short, ep.episode)

@Composable
private fun dayTitle(date: LocalDate): String = formatLongDate(date)

/** Writes a change of `LookPrefs.design` (the structure switch of this screen). */
@Composable
private fun rememberDesignUpdate(): ((DesignPrefs) -> DesignPrefs) -> Unit {
    val koin = getKoin()
    val repo = remember(koin) { runCatching { koin.getOrNull<SettingsRepository>() }.getOrNull() }
    val scope = rememberCoroutineScope()
    return remember(repo, scope) {
        { change ->
            repo?.let { r -> scope.launch { runCatching { r.update { s -> s.copy(look = s.look.copy(design = change(s.look.design))) } } } }
        }
    }
}

@Composable
private fun rememberMetadataRepository(): MetadataRepository? {
    val koin = getKoin()
    return remember(koin) { runCatching { koin.getOrNull<MetadataRepository>() }.getOrNull() }
}

/** `AppSettings.airingReminders` (Ajustes › Avisos › Estrenos de lo que sigues); true when settings are not bound. */
@Composable
private fun rememberAiringRemindersOn(): Boolean {
    val koin = getKoin()
    val repo = remember(koin) { runCatching { koin.getOrNull<SettingsRepository>() }.getOrNull() } ?: return true
    val settings by repo.settings.collectAsStateWithLifecycle()
    return settings.airingReminders
}

private val NO_FALLBACK: StateFlow<MetadataFallbackState?> = MutableStateFlow(null)
private val WIDE_MIN = 600.dp
private val WEEK_COL_MIN = 112.dp
private val WEEK_COL_GAP = 8.dp
private val ASIDE_MIN = 1000.dp
private val ASIDE_WIDTH = 320.dp
private val BOARD_TIME_WIDTH = 80.dp
private val BOARD_EP_WIDTH = 96.dp
private val BOARD_STATE_WIDTH = 120.dp
private const val LOADING_ROWS = 5

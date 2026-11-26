// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.mediaKey
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuSheetHeader
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S2: the "Esta noche" pieces (artifact screens-a.js `tonight`, `agenda`, `agendaList`): the row that joins
// what to resume with what came out and what airs later today, the agenda of today with a "now" marker and a bell per title.

/** The "Esta noche" row: every card hangs from the row's rod (DecorRegion.TONIGHT) and sways out of phase with its neighbours. */
@Composable
internal fun TonightRow(ctx: HomeRowCtx) {
    HomeCardRow(region = DecorRegion.TONIGHT) {
        itemsIndexed(ctx.tonight, key = { _, it -> it.key }) { i, item -> TonightSlot(item, i, ctx) }
    }
}

@Composable
private fun TonightSlot(item: TonightItem, index: Int, ctx: HomeRowCtx) {
    val a = ctx.actions
    val d = ctx.discover
    val openLabel = stringResource(Res.string.rd_s2_open_details)
    when (item) {
        is TonightItem.Resume -> {
            val e = item.entry
            val resumeLabel = stringResource(Res.string.home_continue_play, e.title, e.episode)
            val data = d.cardFor(e)
            ContextMenuArea(
                entries = {
                    listOf(
                        MenuAction(resumeLabel, { a.onResumeEntry(e) }, icon = Icons.Filled.PlayArrow),
                        MenuAction(openLabel, { a.onOpenEntry(e) }, icon = Icons.Outlined.Info),
                    )
                },
                modifier = Modifier.cardSway(index),
                sheetHeader = { MenuSheetHeader(e.title) },
            ) { menu ->
                CardWithCaption(
                    card = {
                        TitleCard(
                            data,
                            onClick = { a.onOpenEntry(e) },
                            context = CardContext.CONTINUE,
                            onLongClick = menu::showSheet,
                            onPlay = { a.onResumeEntry(e) },
                            index = index,
                        )
                    },
                    caption = {
                        SlotCaption(Icons.Outlined.PlayCircle, stringResource(Res.string.rd_s2_tonight_resume)) {
                            CaptionPlay(resumeLabel) { a.onResumeEntry(e) }
                        }
                    },
                )
            }
        }
        is TonightItem.Aired -> {
            val ep = item.episode
            val media = ep.media
            val title = media.displayTitle(LocalTitleLanguage.current)
            val playLabel = stringResource(Res.string.rd_s2_play_episode, title, ep.episode)
            val out = if (item.today) {
                stringResource(Res.string.rd_s2_tonight_out_at, ep.episode, timeOf(ep))
            } else {
                stringResource(Res.string.rd_s2_tonight_out, ep.episode)
            }
            val caption = if (item.behind > 1) stringResource(Res.string.rd_s2_tonight_behind, out, item.behind) else out
            val play = { ctx.gate.open(media) { a.onPlayEpisode(media, ep.episode) } }
            val data = d.cardFor(media, nextLabel = stringResource(Res.string.rd_s2_ep_short, ep.episode), aired = ep.episode, isNew = true)
            ContextMenuArea(
                entries = {
                    listOf(
                        MenuAction(playLabel, play, icon = Icons.Filled.PlayArrow),
                        MenuAction(openLabel, { a.onMediaClick(media) }, icon = Icons.Outlined.Info),
                    )
                },
                modifier = Modifier.cardSway(index),
                sheetHeader = { MenuSheetHeader(title) },
            ) { menu ->
                CardWithCaption(
                    card = {
                        GatedTitleCard(
                            data,
                            media,
                            ctx.gate,
                            onClick = { a.onMediaClick(media) },
                            context = CardContext.CONTINUE,
                            onLongClick = menu::showSheet,
                            onPlay = { a.onPlayEpisode(media, ep.episode) },
                            index = index,
                        )
                    },
                    caption = { SlotCaption(Icons.Outlined.NewReleases, caption) { CaptionPlay(playLabel, onClick = play) } },
                )
            }
        }
        is TonightItem.Upcoming -> {
            val ep = item.episode
            val media = ep.media
            val data = d.cardFor(media, nextLabel = stringResource(Res.string.rd_s2_ep_short, ep.episode), aired = (ep.episode - 1).takeIf { it > 0 })
            CardWithCaption(
                modifier = Modifier.cardSway(index),
                card = { GatedTitleCard(data, media, ctx.gate, onClick = { a.onMediaClick(media) }, context = CardContext.CONTINUE, index = index) },
                caption = {
                    SlotCaption(Icons.Outlined.Schedule, stringResource(Res.string.rd_s2_tonight_airs_at, ep.episode, timeOf(ep))) {
                        ReminderBell(media, on = d.reminds(media.ref), onToggle = a.onToggleReminder)
                    }
                },
            )
        }
    }
}

/**
 * The label under a slot of Esta noche (INI-02): the whole sentence (up to three lines) and a small action at the end. The card's own
 * play disc is the main control; this one stays as a compact icon (with its description) because not every card style draws a disc.
 * It takes 32 dp of the row and keeps a 48 dp target that overflows around it.
 */
@Composable
private fun SlotCaption(icon: ImageVector, text: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(CAPTION_ACTION), contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) { action() }
        }
    }
}

/** The compact play of a caption: one quiet style for every slot (the card's disc carries the emphasis). */
@Composable
private fun CaptionPlay(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.PlayArrow, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlayButton(description: String, filled: Boolean, onClick: () -> Unit) {
    val icon: @Composable () -> Unit = { Icon(Icons.Filled.PlayArrow, contentDescription = description) }
    if (filled) FilledIconButton(onClick = onClick, content = icon) else FilledTonalIconButton(onClick = onClick, content = icon)
}

/**
 * The bell of a title (artifact `remind`): off = follow it with notices; on = its reminder is active. A snackbar confirms; turning it
 * on asks for the notification permission first (Android 13+).
 */
@Composable
internal fun ReminderBell(media: Media, on: Boolean, onToggle: (Media) -> Boolean?) {
    val title = media.displayTitle(LocalTitleLanguage.current)
    val ask = rememberNotificationPermission()
    val snackbar = LocalAppSnackbar.current
    val onMsg = stringResource(Res.string.rd_s2_remind_on_msg, title)
    val offMsg = stringResource(Res.string.rd_s2_remind_off_msg, title)
    val description = stringResource(if (on) Res.string.rd_s2_remind_off else Res.string.rd_s2_remind_on, title)
    IconToggleButton(
        checked = on,
        onCheckedChange = {
            val now = onToggle(media)
            if (now == true) ask()
            if (now != null) snackbar.show(if (now) onMsg else offMsg)
        },
    ) {
        Icon(if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd, contentDescription = description)
    }
}

/** "Hoy en emisión": what airs today, in time order, with the "now" marker; a strip on wide windows, a short list on phones. */
@Composable
internal fun TodayAgenda(ctx: HomeRowCtx, modifier: Modifier = Modifier) {
    val a = ctx.actions
    val d = ctx.discover
    val today = d.today
    if (today is LoadState.Ok && today.value.isEmpty()) return
    val items = (today as? LoadState.Ok)?.value?.let { HomeLogic.agenda(it, d::isTracked) }.orEmpty()
    // INI-06: the count goes in the header line, beside "Abrir calendario", not under the rule.
    HomeSection(
        stringResource(Res.string.rd_s2_today_title),
        modifier,
        onSeeAll = a.onOpenCalendar,
        seeAllLabel = stringResource(Res.string.rd_s2_open_calendar),
        trailing = {
            if (items.isNotEmpty()) {
                Text(
                    stringResource(Res.string.rd_s2_today_sub, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        },
    ) {
        RowBody(today, a.onRetryToday) { _ ->
            if (ctx.compact) AgendaList(items, ctx) else AgendaStrip(items, ctx)
        }
    }
}

private sealed interface AgendaLine {
    data class Ep(val episode: AiringEpisode) : AgendaLine
    data object Now : AgendaLine
}

private fun agendaLines(items: List<AiringEpisode>, nowSec: Long): List<AgendaLine> {
    val out = mutableListOf<AgendaLine>()
    var placed = false
    items.forEach { ep ->
        if (!placed && ep.airingAtEpochSec > nowSec) {
            out += AgendaLine.Now
            placed = true
        }
        out += AgendaLine.Ep(ep)
    }
    return out
}

@Composable
private fun AgendaStrip(items: List<AiringEpisode>, ctx: HomeRowCtx) {
    val lines = remember(items, ctx.nowSec) { agendaLines(items, ctx.nowSec) }
    // INI-06: open on what airs around now (two slots before the marker), not on the first episode of the morning.
    val start = remember(lines) { (lines.indexOfFirst { it == AgendaLine.Now }.takeIf { it >= 0 } ?: lines.size) - 2 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
    AiringContent {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().decor(DecorRegion.AIRING),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(lines, key = { line -> if (line is AgendaLine.Ep) "ag:" + mediaKey(line.episode.media) + ":" + line.episode.episode else "ag:now" }) { line ->
                when (line) {
                    AgendaLine.Now -> NowTick(vertical = true, nowSec = ctx.nowSec)
                    is AgendaLine.Ep -> AgendaSlot(line.episode, ctx)
                }
            }
        }
    }
}

@Composable
private fun AgendaSlot(ep: AiringEpisode, ctx: HomeRowCtx) {
    val state = HomeLogic.airState(ep, ctx.nowSec)
    val media = ep.media
    val title = media.displayTitle(LocalTitleLanguage.current)
    val label = stringResource(Res.string.rd_s2_agenda_item, title, ep.episode)
    // INI-06: what already aired is compact, the episode on air now is framed in the warning amber, as in the artifact.
    val aired = state == AirState.AIRED
    Surface(
        onClick = { ctx.gate.open(media) { ctx.actions.onMediaClick(media) } },
        shape = MaterialTheme.shapes.medium,
        color = if (state == AirState.JUST_OUT) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (state == AirState.JUST_OUT) BorderStroke(2.dp, LocalSignalColors.current.warn) else null,
        modifier = Modifier.width(if (aired) AGENDA_SLOT_WIDTH_AIRED else AGENDA_SLOT_WIDTH).semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeOf(ep), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(airStateLabel(state, ep, ctx.nowSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaCover(url = media.cover, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, maxLines = if (aired) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.rd_s2_ep_short, ep.episode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AgendaAction(ep, state, ctx)
            }
        }
    }
}

/** Phones: the evening part of the day (from a few titles before now), a link to the earlier ones in the calendar. */
@Composable
private fun AgendaList(items: List<AiringEpisode>, ctx: HomeRowCtx) {
    val firstNext = items.indexOfFirst { it.airingAtEpochSec > ctx.nowSec }.let { if (it < 0) items.size else it }
    val from = (firstNext - PHONE_EARLIER).coerceAtLeast(0)
    val shown = items.drop(from).take(PHONE_MAX)
    val lines = remember(shown, ctx.nowSec) { agendaLines(shown, ctx.nowSec) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.AIRING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AiringContent {
            lines.forEach { line ->
                when (line) {
                    AgendaLine.Now -> NowTick(vertical = false, nowSec = ctx.nowSec)
                    is AgendaLine.Ep -> AgendaListRow(line.episode, ctx)
                }
            }
            if (from > 0) {
                TextButton(
                    onClick = ctx.actions.onOpenCalendar,
                    colors = ButtonDefaults.textButtonColors(contentColor = onAiring(MaterialTheme.colorScheme.primary, LocalContentColor.current)),
                    shape = NijiyomeControls.shape,
                ) { Text(stringResource(Res.string.rd_s2_agenda_earlier, from)) }
            }
        }
    }
}

@Composable
private fun AgendaListRow(ep: AiringEpisode, ctx: HomeRowCtx) {
    val state = HomeLogic.airState(ep, ctx.nowSec)
    val media = ep.media
    val title = media.displayTitle(LocalTitleLanguage.current)
    Surface(
        onClick = { ctx.gate.open(media) { ctx.actions.onMediaClick(media) } },
        shape = MaterialTheme.shapes.medium,
        color = if (state == AirState.JUST_OUT) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(timeOf(ep), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            MediaCover(url = media.cover, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.rd_s2_ep_state, ep.episode, airStateLabel(state, ep, ctx.nowSec)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            AgendaAction(ep, state, ctx)
        }
    }
}

@Composable
private fun AgendaAction(ep: AiringEpisode, state: AirState, ctx: HomeRowCtx) {
    val media = ep.media
    if (state == AirState.NEXT) {
        ReminderBell(media, on = ctx.discover.reminds(media.ref), onToggle = ctx.actions.onToggleReminder)
    } else {
        val title = media.displayTitle(LocalTitleLanguage.current)
        PlayButton(stringResource(Res.string.rd_s2_play_episode, title, ep.episode), filled = state == AirState.JUST_OUT) {
            ctx.gate.open(media) { ctx.actions.onPlayEpisode(media, ep.episode) }
        }
    }
}

@Composable
private fun airStateLabel(state: AirState, ep: AiringEpisode, nowSec: Long): String = when (state) {
    AirState.JUST_OUT -> stringResource(Res.string.rd_s2_air_just_out)
    AirState.AIRED -> stringResource(Res.string.rd_s2_air_available)
    AirState.NEXT -> {
        val minutes = ((ep.airingAtEpochSec - nowSec + 59) / 60).toInt()
        if (minutes >= 60) stringResource(Res.string.rd_s2_air_in_hm, minutes / 60, minutes % 60) else stringResource(Res.string.rd_s2_air_in_m, minutes)
    }
}

/** The "now" marker of an agenda: a vertical tick between slots on wide windows, a line across the list on phones. */
@Composable
internal fun NowTick(vertical: Boolean, nowSec: Long) {
    val time = formatTime(nowSec)
    val label = stringResource(Res.string.rd_s2_now_at, time)
    // On a dark airing island of a light theme the light error container is the readable red.
    val color = onAiring(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
    if (vertical) {
        Column(Modifier.semantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            VerticalDivider(Modifier.height(NOW_TICK_HEIGHT).padding(top = 4.dp), thickness = 2.dp, color = color)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = color)
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Short local time ("19:30"), in the locale the app runs in now (Ajustes › Idioma can change it while the app is open). */
internal fun formatTime(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).format(TIME_FORMAT.withLocale(java.util.Locale.getDefault()))

private fun timeOf(ep: AiringEpisode): String = formatTime(ep.airingAtEpochSec)

private val AGENDA_SLOT_WIDTH = 220.dp
private val AGENDA_SLOT_WIDTH_AIRED = 176.dp
private val CAPTION_ACTION = 32.dp
private val NOW_TICK_HEIGHT = 84.dp
private const val PHONE_EARLIER = 2
private const val PHONE_MAX = 7

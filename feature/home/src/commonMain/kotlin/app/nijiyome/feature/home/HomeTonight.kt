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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.NoAdultContent
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.CardSize
import app.nijiyome.model.LoadState
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.Media
import app.nijiyome.model.TonightLayoutIds
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.tanabata.TanzakuTonightCard
import app.nijiyome.ui.cards.tanabata.TonightCardAction
import app.nijiyome.ui.cards.tanabata.TonightCardInfo
import app.nijiyome.ui.cards.tanabata.TonightCardKind
import app.nijiyome.ui.cards.tanabata.tanzakuTonightCards
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.mediaKey
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.MenuAction
import app.nijiyome.ui.patterns.MenuEntry
import app.nijiyome.ui.patterns.MenuSheetHeader
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane S2: the "Esta noche" pieces (artifact screens-a.js `tonight`, `agenda`, `agendaList`): the row that joins
// what to resume with what came out and what airs later today, the agenda of today with a "now" marker and a bell per title.

/** D (Destacado y lista) closes its sheet with its own "Ver todo"; the section then drops its header link (no duplicate). */
@Composable
internal fun tonightOwnsSeeAll(): Boolean = LocalLook.current.design.tonightLayoutId() == TonightLayoutIds.FEATURED

/**
 * The "Esta noche" row in the layout the user picked (v1.3.5, docs/redesign/movimiento; each one is in HomeTonightLayouts.kt): A one
 * row of big cards, B up to two rows, C one short row per kind, D the one to watch now beside an agenda sheet (the default), E the
 * night as a timeline. Every layout keeps each item's context menu and the mature gate.
 */
@Composable
internal fun TonightRow(ctx: HomeRowCtx) {
    if (ctx.tonight.isEmpty()) return
    when (LocalLook.current.design.tonightLayoutId()) {
        TonightLayoutIds.WIDE -> TonightWide(ctx)
        TonightLayoutIds.GRID -> TonightGrid(ctx)
        TonightLayoutIds.BRANCHES -> TonightBranches(ctx)
        TonightLayoutIds.NIGHT -> TonightNight(ctx)
        else -> TonightFeatured(ctx)
    }
}

/**
 * One "Esta noche" item with every text and action its layouts draw. [action] and the [menu] entries are already behind the mature
 * gate; [onOpen] and [onPlay] are raw (the cards that take them gate themselves, as GatedTitleCard does).
 */
internal class TonightModel(
    val key: String,
    val title: String,
    /** The title behind the mature gate; null for a history entry to resume. */
    val media: Media?,
    val data: TitleCardData,
    val kind: TonightCardKind,
    /** In-card line 1: "Continuar", "Salió 21:30", "Sale 23:30". */
    val headline: String,
    /** In-card line 2: "Ep. 6 · quedan 11 min", "Ep. 9 · te faltan 2", "Ep. 11 · con aviso". */
    val detail: String,
    /** The two-line caption under another kit's card: "Salió a las 11:30" over "Te faltan 8". */
    val captionIcon: ImageVector,
    val caption: String,
    val captionDetail: String,
    /** The state line of the agenda sheet: "Salió a las 21:30 · Ep. 9 · te faltan 2". */
    val line: String,
    val tag: String?,
    val progress: Float?,
    /** The featured card's wide button ("Ver Ep. 9"); null for a title still to air, which keeps its bell. */
    val cta: String?,
    val action: TonightCardAction,
    val onOpen: () -> Unit,
    val onPlay: (() -> Unit)?,
    val menu: () -> List<MenuEntry>,
)

/** [action] behind the mature gate when the item is a title. */
internal fun TonightModel.gated(gate: MatureGate, action: () -> Unit): () -> Unit =
    media?.let { m -> { gate.open(m, action) } } ?: action

@Composable
internal fun tonightModel(item: TonightItem, ctx: HomeRowCtx): TonightModel {
    val a = ctx.actions
    val d = ctx.discover
    val openLabel = stringResource(Res.string.rd_s2_open_details)
    return when (item) {
        is TonightItem.Resume -> {
            val e = item.entry
            val data = d.cardFor(e)
            val resumeLabel = stringResource(Res.string.home_continue_play, e.title, e.episode)
            val resume = { a.onResumeEntry(e) }
            val open = { a.onOpenEntry(e) }
            val resumeText = stringResource(Res.string.rd_s2_tonight_resume)
            val where = stringResource(Res.string.rd_s2_tonight_resume_detail)
            val detail = data.nextLabel ?: stringResource(Res.string.rd_s2_ep_short, e.episode)
            TonightModel(
                key = item.key,
                title = e.title,
                media = null,
                data = data,
                kind = TonightCardKind.RESUME,
                headline = resumeText,
                detail = detail,
                captionIcon = Icons.Outlined.PlayCircle,
                caption = resumeText,
                captionDetail = where,
                line = "$where · $detail",
                tag = null,
                progress = data.progress,
                cta = stringResource(Res.string.rd_s2_tonight_cta_resume, e.episode),
                action = TonightCardAction(resumeLabel, resume),
                onOpen = open,
                onPlay = resume,
                menu = {
                    listOf(
                        MenuAction(resumeLabel, resume, icon = Icons.Filled.PlayArrow),
                        MenuAction(openLabel, open, icon = Icons.Outlined.Info),
                    )
                },
            )
        }
        is TonightItem.Aired -> {
            val ep = item.episode
            val media = ep.media
            val title = media.displayTitle(LocalTitleLanguage.current)
            val playLabel = stringResource(Res.string.rd_s2_play_episode, title, ep.episode)
            val time = timeOf(ep)
            val epShort = stringResource(Res.string.rd_s2_ep_short, ep.episode)
            // v1.3.5: two fixed lines (when / which episode) so every slot of the row is as tall as its neighbours.
            val caption = if (item.today) stringResource(Res.string.rd_s2_tonight_out_at, time) else stringResource(Res.string.rd_s2_tonight_out)
            val headline = if (item.today) stringResource(Res.string.rd_s2_tonight_out_short, time) else stringResource(Res.string.rd_s2_tonight_out)
            val behind = item.behind > 1
            val captionDetail = if (behind) stringResource(Res.string.rd_s2_tonight_behind, item.behind) else epShort
            val detail = if (behind) stringResource(Res.string.rd_s2_tonight_ep_behind, ep.episode, item.behind) else epShort
            val playRaw = { a.onPlayEpisode(media, ep.episode) }
            val play = { ctx.gate.open(media, playRaw) }
            val open = { a.onMediaClick(media) }
            TonightModel(
                key = item.key,
                title = title,
                media = media,
                data = d.cardFor(media, nextLabel = epShort, aired = ep.episode, isNew = true),
                kind = TonightCardKind.AIRED,
                headline = headline,
                detail = detail,
                captionIcon = Icons.Outlined.NewReleases,
                caption = caption,
                captionDetail = captionDetail,
                line = "$caption · $detail",
                tag = stringResource(Res.string.rd_s2_tonight_tag_new),
                progress = null,
                cta = stringResource(Res.string.rd_s2_tonight_cta_watch, ep.episode),
                action = TonightCardAction(playLabel, play),
                onOpen = open,
                onPlay = playRaw,
                menu = {
                    listOf(
                        MenuAction(playLabel, play, icon = Icons.Filled.PlayArrow),
                        MenuAction(openLabel, open, icon = Icons.Outlined.Info),
                    )
                },
            )
        }
        is TonightItem.Upcoming -> {
            val ep = item.episode
            val media = ep.media
            val title = media.displayTitle(LocalTitleLanguage.current)
            val on = d.reminds(media.ref)
            val toggle = rememberReminderToggle(media, a.onToggleReminder)
            val remindLabel = stringResource(if (on) Res.string.rd_s2_remind_off else Res.string.rd_s2_remind_on, title)
            val time = timeOf(ep)
            val epShort = stringResource(Res.string.rd_s2_ep_short, ep.episode)
            val caption = stringResource(Res.string.rd_s2_tonight_airs_at, time)
            val detail = if (on) stringResource(Res.string.rd_s2_tonight_ep_reminded, ep.episode) else epShort
            val open = { a.onMediaClick(media) }
            TonightModel(
                key = item.key,
                title = title,
                media = media,
                data = d.cardFor(media, nextLabel = epShort, aired = (ep.episode - 1).takeIf { it > 0 }),
                kind = TonightCardKind.UPCOMING,
                headline = stringResource(Res.string.rd_s2_tonight_airs_short, time),
                detail = detail,
                captionIcon = Icons.Outlined.Schedule,
                caption = caption,
                captionDetail = epShort,
                line = "$caption · $detail",
                tag = time,
                progress = null,
                cta = null,
                action = TonightCardAction(remindLabel, toggle, bell = true, on = on),
                onOpen = open,
                onPlay = null,
                // v1.3.5: a title still to air gets a menu too (it had none): its reminder and its details.
                menu = {
                    listOf(
                        MenuAction(remindLabel, toggle, icon = if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd),
                        MenuAction(openLabel, open, icon = Icons.Outlined.Info),
                    )
                },
            )
        }
    }
}

/** How a layout wants a card drawn (the tanzaku reads all of it; other kits only [pile], since they keep their own card and caption). */
@Immutable
internal class TonightLook(
    val size: CardSize? = null,
    /** Strip width before the skin's scale (the layouts' medium cards). */
    val width: Dp? = null,
    /** The featured card of D: "Para ahora" tag and the wide button. */
    val featured: Boolean = false,
    val cordTag: String? = null,
    val dim: Boolean = false,
    val pile: TonightPile? = null,
)

/** The collapsed pile of the night layout, drawn on its top card: the count on the cord, papers behind, a tap opens it. */
@Immutable
internal class TonightPile(
    val ghosts: List<StatusSlot?>,
    /** "Para seguir · 3". */
    val tag: String,
    /** "Toca para abrir". */
    val hint: String,
    /** "Abrir la pila: 3 para seguir". */
    val label: String,
    val onOpen: () -> Unit,
)

/**
 * One item of "Esta noche" in any layout: the skinned tanzaku with everything inside its paper when the user's card style is the
 * tanzaku, else the kit's own Continuar card with the two-line caption under it. Its context menu (right click, long press) and the
 * mature gate come along in both.
 */
@Composable
internal fun TonightCard(item: TonightItem, index: Int, ctx: HomeRowCtx, modifier: Modifier = Modifier, look: TonightLook = TonightLook()) {
    val m = tonightModel(item, ctx)
    ContextMenuArea(
        entries = m.menu,
        modifier = modifier.cardSway(index),
        sheetHeader = { MenuSheetHeader(m.title) },
    ) { menu ->
        if (tanzakuTonightCards()) TonightPaper(m, index, ctx, look, menu::showSheet) else TonightKitSlot(m, index, ctx, look, menu::showSheet)
    }
}

@Composable
private fun TonightPaper(m: TonightModel, index: Int, ctx: HomeRowCtx, look: TonightLook, onLongClick: () -> Unit) {
    val pile = look.pile
    val info = TonightCardInfo(
        kind = m.kind,
        headline = m.headline,
        detail = m.detail,
        tag = m.tag,
        action = m.action,
        cta = if (look.featured) m.cta else null,
        nowTag = if (look.featured) stringResource(Res.string.rd_s2_tonight_for_now) else null,
        cordTag = pile?.tag ?: look.cordTag,
        progress = m.progress,
        dim = look.dim,
        pile = pile?.ghosts.orEmpty(),
        width = look.width,
    )
    val hidden = m.media?.let { ctx.gate.isHidden(it) && ctx.gate.mode == MatureContentMode.BLUR } == true
    val described = if (pile != null) Modifier.semantics { contentDescription = pile.label } else Modifier
    Box {
        TanzakuTonightCard(
            m.data,
            info,
            onClick = pile?.onOpen ?: m.gated(ctx.gate, m.onOpen),
            modifier = described.then(if (hidden) Modifier.blur(18.dp) else Modifier),
            onLongClick = onLongClick,
            size = look.size ?: LocalLook.current.cardSize,
            index = index,
        )
        if (hidden) MatureTapLabel(Modifier.align(Alignment.Center))
    }
}

/** Other kits: their own Continuar card (behind the gate for a title) and the two-line caption with play or the bell. */
@Composable
private fun TonightKitSlot(m: TonightModel, index: Int, ctx: HomeRowCtx, look: TonightLook, onLongClick: () -> Unit) {
    val pile = look.pile
    CardWithCaption(
        card = {
            if (m.media != null && pile == null) {
                GatedTitleCard(
                    m.data,
                    m.media,
                    ctx.gate,
                    onClick = m.onOpen,
                    context = CardContext.CONTINUE,
                    onLongClick = onLongClick,
                    onPlay = m.onPlay,
                    index = index,
                )
            } else {
                TitleCard(
                    m.data,
                    onClick = pile?.onOpen ?: m.onOpen,
                    modifier = if (pile != null) Modifier.semantics { contentDescription = pile.label } else Modifier,
                    context = CardContext.CONTINUE,
                    onLongClick = onLongClick,
                    onPlay = m.onPlay,
                    index = index,
                )
            }
        },
        caption = {
            if (pile != null) {
                SlotCaption(Icons.Outlined.Layers, pile.tag, pile.hint) {
                    IconButton(onClick = pile.onOpen) { Icon(Icons.Outlined.UnfoldMore, contentDescription = pile.label, modifier = Modifier.size(20.dp)) }
                }
            } else {
                SlotCaption(m.captionIcon, m.caption, m.captionDetail) { CaptionAction(m.action) }
            }
        },
    )
}

/** "Maduro · toca para ver" over a blurred card (as GatedTitleCard draws it). */
@Composable
internal fun MatureTapLabel(modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.NoAdultContent, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(Res.string.home_mature_tap), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The label under a slot of Esta noche (INI-02): when (one line) over which episode (one quieter line, kept empty when there is
 * none), and a small action at the end. Always two lines, so the slots of a row line up (v1.3.5: a free sentence wrapped to 2 or 3
 * lines and the row looked ragged). The card's own play disc is the main control; this one stays as a compact icon (with its
 * description) because not every card style draws a disc. It takes 32 dp of the row and keeps a 48 dp target that overflows around it.
 */
@Composable
private fun SlotCaption(icon: ImageVector, text: String, detail: String?, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(CAPTION_ACTION), contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) { action() }
        }
    }
}

/** The caption's action: the compact play, or the reminder bell of a title still to air. */
@Composable
private fun CaptionAction(action: TonightCardAction) {
    if (action.bell) TonightBell(action) else CaptionPlay(action.label, action.onClick)
}

/** The compact play of a caption: one quiet style for every slot (the card's disc carries the emphasis). */
@Composable
private fun CaptionPlay(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.PlayArrow, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** The bell of a [TonightModel] (its toggle already asks for the permission and confirms with a snackbar). */
@Composable
internal fun TonightBell(action: TonightCardAction) {
    IconToggleButton(checked = action.on, onCheckedChange = { action.onClick() }) {
        Icon(if (action.on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd, contentDescription = action.label)
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
    val toggle = rememberReminderToggle(media, onToggle)
    val description = stringResource(if (on) Res.string.rd_s2_remind_off else Res.string.rd_s2_remind_on, title)
    IconToggleButton(checked = on, onCheckedChange = { toggle() }) {
        Icon(if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationAdd, contentDescription = description)
    }
}

/** What a tap on a reminder bell does: toggle it, ask for the notification permission when it turns on, confirm with a snackbar. */
@Composable
internal fun rememberReminderToggle(media: Media, onToggle: (Media) -> Boolean?): () -> Unit {
    val title = media.displayTitle(LocalTitleLanguage.current)
    val ask = rememberNotificationPermission()
    val snackbar = LocalAppSnackbar.current
    val onMsg = stringResource(Res.string.rd_s2_remind_on_msg, title)
    val offMsg = stringResource(Res.string.rd_s2_remind_off_msg, title)
    return {
        val now = onToggle(media)
        if (now == true) ask()
        if (now != null) snackbar.show(if (now) onMsg else offMsg)
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
    val now = ctx.nowSec()
    val lines = remember(items, now) { agendaLines(items, now) }
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
                    AgendaLine.Now -> NowTick(vertical = true, nowSec = ctx.nowSec())
                    is AgendaLine.Ep -> AgendaSlot(line.episode, ctx)
                }
            }
        }
    }
}

@Composable
private fun AgendaSlot(ep: AiringEpisode, ctx: HomeRowCtx) {
    val now = ctx.nowSec()
    val state = HomeLogic.airState(ep, now)
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
                Text(airStateLabel(state, ep, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    val now = ctx.nowSec()
    val firstNext = items.indexOfFirst { it.airingAtEpochSec > now }.let { if (it < 0) items.size else it }
    val from = (firstNext - PHONE_EARLIER).coerceAtLeast(0)
    val shown = items.drop(from).take(PHONE_MAX)
    val lines = remember(shown, now) { agendaLines(shown, now) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).decor(DecorRegion.AIRING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AiringContent {
            lines.forEach { line ->
                when (line) {
                    AgendaLine.Now -> NowTick(vertical = false, nowSec = now)
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
    val now = ctx.nowSec()
    val state = HomeLogic.airState(ep, now)
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
                    stringResource(Res.string.rd_s2_ep_state, ep.episode, airStateLabel(state, ep, now)),
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

// HORA-01: explicit 24 h pattern ("09:30", "21:00") so bundling jdk.localedata never flips bare "es" locales to 12 h "a. m. / p. m."
// and the departure board's monospaced time column always fits without clipping.
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Short local 24 h time ("19:30"). */
internal fun formatTime(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private fun timeOf(ep: AiringEpisode): String = formatTime(ep.airingAtEpochSec)

private val AGENDA_SLOT_WIDTH = 220.dp
private val AGENDA_SLOT_WIDTH_AIRED = 176.dp
private val CAPTION_ACTION = 32.dp
private val NOW_TICK_HEIGHT = 84.dp
private const val PHONE_EARLIER = 2
private const val PHONE_MAX = 7

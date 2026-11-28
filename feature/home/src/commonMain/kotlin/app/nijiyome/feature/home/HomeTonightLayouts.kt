// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalFoundationApi::class)

package app.nijiyome.feature.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.direction.tanabata.TanabataMaterials
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.scroll.NjLazyRow
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.CardSize
import app.nijiyome.model.MatureContentMode
import app.nijiyome.model.MediaRef
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.tanabata.TanzakuTonightAction
import app.nijiyome.ui.cards.tanabata.TanzakuTonightCard
import app.nijiyome.ui.cards.tanabata.TanzakuTonightSheet
import app.nijiyome.ui.cards.tanabata.TonightCardAction
import app.nijiyome.ui.cards.tanabata.TonightCardInfo
import app.nijiyome.ui.cards.tanabata.TonightCardKind
import app.nijiyome.ui.cards.tanabata.TonightSheetColors
import app.nijiyome.ui.cards.tanabata.tanzakuTonightCards
import app.nijiyome.ui.cards.tanabata.tanzakuTonightWidth
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.rowSpacing
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.patterns.ContextMenuArea
import app.nijiyome.ui.patterns.MenuSheetHeader
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

// v1.3.5: the five layouts of "Esta noche" (docs/redesign/movimiento; the owner kept all five as a setting, D by default). They only
// arrange the items: each one is a TonightCard (HomeTonight.kt), the skinned tanzaku when the user's cards are tanzaku, the kit's own
// Continuar card and caption otherwise. Under Tanabata every rod is a `decor(DecorRegion.TONIGHT)` row the cards hang from.

/** The medium strip of B, C and E (artifact 146 to 156 px), before the skin's own scale. */
private val TONIGHT_MEDIUM = 156.dp

// ---------------------------------------------------------------- A · one row of big cards

/** A: the big tanzaku of Novedades on one rod (large at least, so the kind lines fit; medium on a phone); the row scrolls. */
@Composable
internal fun TonightWide(ctx: HomeRowCtx) {
    val size = if (ctx.compact) CardSize.MEDIUM else maxOf(LocalLook.current.cardSize, CardSize.LARGE)
    val look = remember(size) { TonightLook(size = size) }
    HomeCardRow(region = DecorRegion.TONIGHT) {
        itemsIndexed(ctx.tonight, key = { _, it -> it.key }) { i, item -> TonightCard(item, i, ctx, look = look) }
    }
}

// ---------------------------------------------------------------- groups (C, D)

internal enum class TonightGroupKind { RESUME, AIRED, LATER }

internal data class TonightGroup(val kind: TonightGroupKind, val items: List<TonightItem>)

internal val TonightItem.groupKind: TonightGroupKind
    get() = when (this) {
        is TonightItem.Resume -> TonightGroupKind.RESUME
        is TonightItem.Aired -> TonightGroupKind.AIRED
        is TonightItem.Upcoming -> TonightGroupKind.LATER
    }

private val TonightItem.airingAtSec: Long?
    get() = when (this) {
        is TonightItem.Resume -> null
        is TonightItem.Aired -> episode.airingAtEpochSec
        is TonightItem.Upcoming -> episode.airingAtEpochSec
    }

/** The items of each kind in [order], empty kinds left out. What airs later goes by its time; the rest keeps the app's priority. */
internal fun tonightGroups(items: List<TonightItem>, order: List<TonightGroupKind>): List<TonightGroup> = order.mapNotNull { kind ->
    val of = items.filter { it.groupKind == kind }
    val sorted = if (kind == TonightGroupKind.LATER) of.sortedBy { it.airingAtSec ?: Long.MAX_VALUE } else of
    if (sorted.isEmpty()) null else TonightGroup(kind, sorted)
}

/** C: to continue, out, later (artifact order). */
private val BRANCH_ORDER = listOf(TonightGroupKind.RESUME, TonightGroupKind.AIRED, TonightGroupKind.LATER)

/** D's sheet: what else came out first (the featured card is usually the one to resume), then to continue, then later. */
private val SHEET_ORDER = listOf(TonightGroupKind.AIRED, TonightGroupKind.RESUME, TonightGroupKind.LATER)

private fun TonightGroupKind.icon(): ImageVector = when (this) {
    TonightGroupKind.RESUME -> Icons.Outlined.History
    TonightGroupKind.AIRED -> Icons.Outlined.Schedule
    TonightGroupKind.LATER -> Icons.Outlined.NotificationsNone
}

/** "Salió hoy" only when everything in it came out today (an episode from yesterday makes it "Ya salió"); the sheet says "También". */
@Composable
private fun groupName(g: TonightGroup, sheet: Boolean): String = when (g.kind) {
    TonightGroupKind.RESUME -> stringResource(Res.string.rd_s2_tonight_group_resume)
    TonightGroupKind.LATER -> stringResource(Res.string.rd_s2_tonight_group_later)
    TonightGroupKind.AIRED -> {
        val today = g.items.all { (it as? TonightItem.Aired)?.today == true }
        when {
            sheet && today -> stringResource(Res.string.rd_s2_tonight_group_also_out)
            sheet -> stringResource(Res.string.rd_s2_tonight_group_also_out_any)
            today -> stringResource(Res.string.rd_s2_tonight_group_out)
            else -> stringResource(Res.string.rd_s2_tonight_out)
        }
    }
}

// ---------------------------------------------------------------- B · up to two rods

private const val GRID_RODS = 2
private val GRID_ROD_GAP = 12.dp

/** B: how many cards [rods] rods of [perRow] show; one less when the last cell has to become "+N, ver todo". */
internal fun tonightGridShown(count: Int, perRow: Int, rods: Int = GRID_RODS): Int {
    val capacity = perRow.coerceAtLeast(1) * rods
    return if (count <= capacity) count else capacity - 1
}

private enum class GridSlot { PROBE, BODY }

/**
 * B: medium cards on up to two rods that fill left to right, no scrolling. One card measured on its own gives the cell (every card of
 * a layout is as wide and as tall as the others), so the rods know how many fit at this width; past two full rods the last cell is
 * the "+N" paper, which opens the same list as the row's "Ver todo".
 */
@Composable
internal fun TonightGrid(ctx: HomeRowCtx) {
    val items = ctx.tonight
    val gap = rowSpacing()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A phone fits two medium strips only a little narrower (1 dp spare against rounding); never below the small strip, so the
        // kind lines still fit.
        val fit = if (ctx.compact) tanzakuTonightWidth((maxWidth - ROW_SIDE * 2 - gap) / 2 - 1.dp).coerceIn(GRID_PHONE_MIN, TONIGHT_MEDIUM) else TONIGHT_MEDIUM
        val look = remember(fit) { TonightLook(size = CardSize.MEDIUM, width = fit) }
        TonightGridBody(items, ctx, look, gap)
    }
}

private val GRID_PHONE_MIN = 112.dp

@Composable
private fun TonightGridBody(items: List<TonightItem>, ctx: HomeRowCtx, look: TonightLook, gap: Dp) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val maxW = constraints.maxWidth
        val room = (maxW - 2 * ROW_SIDE.roundToPx()).coerceAtLeast(0)
        val g = gap.roundToPx()
        val probe = subcompose(GridSlot.PROBE) { TonightCard(items.first(), 0, ctx, look = look) }
            .map { it.measure(Constraints(maxWidth = room)) }
        val cellW = (probe.maxOfOrNull { it.width } ?: 1).coerceAtLeast(1)
        val cellH = probe.maxOfOrNull { it.height } ?: 0
        val perRow = ((room + g) / (cellW + g)).coerceAtLeast(1)
        val shown = tonightGridShown(items.size, perRow)
        val body = subcompose(GridSlot.BODY) { TonightGridRods(items, perRow, shown, ctx, look, gap, cellW.toDp(), cellH.toDp()) }
            .map { it.measure(Constraints(minWidth = maxW, maxWidth = maxW)) }
        layout(maxW, body.maxOfOrNull { it.height } ?: 0) { body.forEach { it.placeRelative(0, 0) } }
    }
}

@Composable
private fun TonightGridRods(
    items: List<TonightItem>,
    perRow: Int,
    shown: Int,
    ctx: HomeRowCtx,
    look: TonightLook,
    gap: Dp,
    cellW: Dp,
    cellH: Dp,
) {
    val hidden = items.size - shown
    val cells: List<TonightItem?> = items.take(shown) + if (hidden > 0) listOf(null) else emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GRID_ROD_GAP)) {
        cells.chunked(perRow).forEachIndexed { r, rod ->
            Row(
                Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT).padding(horizontal = ROW_SIDE),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                rod.forEachIndexed { c, item ->
                    val i = r * perRow + c
                    if (item == null) {
                        TonightMoreCell(hidden, items.size, i, ctx, look, cellW, cellH)
                    } else {
                        key(item.key) { TonightCard(item, i, ctx, look = look) }
                    }
                }
            }
        }
    }
}

/** The "+N, ver todo" cell of B: a tanzaku of its own with the count in place of the poster, or a quiet surface of the cell's size. */
@Composable
private fun TonightMoreCell(hidden: Int, total: Int, index: Int, ctx: HomeRowCtx, look: TonightLook, width: Dp, height: Dp) {
    val seeAll = ctx.actions.onOpenLibrary
    val plus = "+$hidden"
    val line = stringResource(Res.string.rd_s2_tonight_more_tonight)
    val title = stringResource(Res.string.home_see_all)
    if (tanzakuTonightCards()) {
        val info = TonightCardInfo(
            kind = TonightCardKind.MORE,
            headline = stringResource(Res.string.rd_s2_tonight_open_list),
            detail = stringResource(Res.string.rd_s2_tonight_total, total),
            more = plus,
            moreLine = line,
            width = look.width,
        )
        val data = remember(title) { TitleCardData(key = "tonight:more", title = title, coverUrl = null, listStatus = StatusSlot.PLANNING) }
        TanzakuTonightCard(data, info, onClick = seeAll, size = look.size ?: CardSize.MEDIUM, index = index)
    } else {
        val cs = MaterialTheme.colorScheme
        Surface(
            onClick = seeAll,
            modifier = Modifier.size(width, height),
            shape = MaterialTheme.shapes.medium,
            color = cs.surfaceContainerHigh,
            border = BorderStroke(1.dp, cs.outlineVariant),
        ) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(plus, style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, maxLines = 1)
                Text(line, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.size(4.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- C · one rod per kind

/**
 * C: one short rod per kind (Para seguir, Salió hoy, Más tarde) under its name and count; empty kinds are left out. Rods sit side by
 * side while they fit and wrap below otherwise; a rod longer than the width scrolls on its own.
 */
@Composable
internal fun TonightBranches(ctx: HomeRowCtx) {
    val groups = remember(ctx.tonight) { tonightGroups(ctx.tonight, BRANCH_ORDER) }
    val look = remember { TonightLook(size = CardSize.MEDIUM, width = TONIGHT_MEDIUM) }
    TonightFlow(hGap = 24.dp, vGap = 12.dp) {
        groups.forEach { g -> key(g.kind) { TonightBranch(g, ctx, look) } }
    }
}

@Composable
private fun TonightBranch(g: TonightGroup, ctx: HomeRowCtx, look: TonightLook) {
    Column(Modifier.semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BranchHeader(g.kind.icon(), groupName(g, sheet = false), g.items.size, Modifier.padding(start = ROW_SIDE + 4.dp, end = ROW_SIDE))
        // Not HomeCardRow: that one fills the width, and a short rod here takes only what its cards need. Same wheel and paging
        // arrows as the other rows.
        NjLazyRow(
            modifier = Modifier.decor(DecorRegion.TONIGHT),
            contentPadding = PaddingValues(horizontal = ROW_SIDE),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing()),
            pagingArrows = true,
        ) {
            itemsIndexed(g.items, key = { _, it -> it.key }) { i, item -> TonightCard(item, i, ctx, look = look) }
        }
    }
}

@Composable
private fun BranchHeader(icon: ImageVector, name: String, count: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = cs.onSurface, maxLines = 1)
        Text("$count", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = cs.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Places its children left to right and wraps to a new line when the next one does not fit. Children are measured once with the full
 * width (no intrinsics: the rods are lazy rows, which cannot answer them).
 */
@Composable
private fun TonightFlow(hGap: Dp, vGap: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val h = hGap.roundToPx()
        val v = vGap.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = maxW)) }
        val at = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var line = 0
        placeables.forEach { p ->
            if (x > 0 && x + p.width > maxW) {
                x = 0
                y += line + v
                line = 0
            }
            at += IntOffset(x, y)
            x += p.width + h
            line = max(line, p.height)
        }
        layout(maxW, if (placeables.isEmpty()) 0 else y + line) {
            placeables.forEachIndexed { i, p -> p.placeRelative(at[i]) }
        }
    }
}

// ---------------------------------------------------------------- D · featured card and agenda sheet

private val FEATURED_SIDE_BY_SIDE = 640.dp
private val FEATURED_GAP = 40.dp
private val FEATURED_SHEET_MIN = 300.dp
private const val PHONE_SHEET_ROWS = 6

/**
 * D (the default): the first item (the app's own priority, so what to watch now) as a big card with a wide "Ver Ep. N" button, and
 * the rest on an agenda sheet beside it, grouped (También salió hoy, Para seguir, Más tarde), as tall as the card and scrolling
 * inside. On a phone or a narrow window the card comes first and the sheet lists a few rows under it, without its own scroll.
 */
@Composable
internal fun TonightFeatured(ctx: HomeRowCtx) {
    val first = ctx.tonight.first()
    val rest = remember(ctx.tonight) { ctx.tonight.drop(1) }
    val groups = remember(rest) { tonightGroups(rest, SHEET_ORDER) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (ctx.compact || maxWidth < FEATURED_SIDE_BY_SIDE) {
            FeaturedStacked(first, groups, rest.size, ctx)
        } else {
            FeaturedBeside(first, groups, rest.size, ctx)
        }
    }
}

@Composable
private fun FeaturedBeside(first: TonightItem, groups: List<TonightGroup>, count: Int, ctx: HomeRowCtx) {
    val look = remember { TonightLook(size = CardSize.XL, featured = true) }
    val card: @Composable () -> Unit = { TonightCard(first, 0, ctx, look = look) }
    val agenda: @Composable () -> Unit = { if (count > 0) TonightSheet(groups, count, ctx, cap = null, modifier = Modifier.fillMaxSize()) }
    Layout(
        contents = listOf(card, agenda),
        modifier = Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT).padding(horizontal = ROW_SIDE),
    ) { (featured, sheet), constraints ->
        val f = featured.firstOrNull()?.measure(Constraints(maxWidth = constraints.maxWidth))
        val fw = f?.width ?: 0
        // The sheet is exactly as tall as the card (another kit's Continuar card can be short, so never below a usable height).
        val h = max(f?.height ?: 0, FEATURED_SHEET_MIN.roundToPx())
        val g = FEATURED_GAP.roundToPx()
        val sw = (constraints.maxWidth - fw - g).coerceAtLeast(0)
        val s = sheet.firstOrNull()?.measure(Constraints.fixed(sw, h))
        layout(constraints.maxWidth, h) {
            f?.placeRelative(0, 0)
            s?.placeRelative(fw + g, 0)
        }
    }
}

@Composable
private fun FeaturedStacked(first: TonightItem, groups: List<TonightGroup>, count: Int, ctx: HomeRowCtx) {
    val look = remember { TonightLook(size = CardSize.LARGE, featured = true) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT).padding(horizontal = ROW_SIDE)) { TonightCard(first, 0, ctx, look = look) }
        if (count > 0) {
            Box(Modifier.fillMaxWidth().decor(DecorRegion.TONIGHT).padding(horizontal = ROW_SIDE)) {
                TonightSheet(groups, count, ctx, cap = PHONE_SHEET_ROWS, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The agenda sheet: grouped rows with a thumb, the title, the state line and play or the bell, and "Ver todo · N más esta noche" at
 * the foot. [cap] null = every row, scrolling inside a sheet of fixed height; otherwise only the first [cap] rows and no inner scroll.
 * Hung paper when the cards are tanzaku, a surface card otherwise.
 */
@Composable
private fun TonightSheet(groups: List<TonightGroup>, count: Int, ctx: HomeRowCtx, cap: Int?, modifier: Modifier) {
    val paper = tanzakuTonightCards()
    val body: @Composable ColumnScope.(TonightSheetColors) -> Unit = { colors ->
        if (cap == null) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                groups.forEach { g ->
                    item(key = "h:" + g.kind.name, contentType = "header") { SheetHeader(g, colors) }
                    items(g.items, key = { it.key }, contentType = { "row" }) { SheetRow(it, ctx, colors, paper) }
                }
            }
        } else {
            var left = cap
            groups.forEach { g ->
                if (left > 0) {
                    val shown = g.items.take(left)
                    left -= shown.size
                    key(g.kind) { SheetHeader(g, colors) }
                    shown.forEach { item -> key(item.key) { SheetRow(item, ctx, colors, paper) } }
                }
            }
        }
        SheetFooter(count, colors, ctx.actions.onOpenLibrary)
    }
    val grouped = modifier.semantics { isTraversalGroup = true }
    if (paper) {
        TanzakuTonightSheet(grouped) { body(this, it) }
    } else {
        val cs = MaterialTheme.colorScheme
        val colors = TonightSheetColors(cs.surfaceContainer, cs.onSurface, cs.onSurfaceVariant, cs.outlineVariant)
        Surface(grouped, shape = MaterialTheme.shapes.large, color = cs.surfaceContainer, border = BorderStroke(1.dp, cs.outlineVariant)) {
            Column(if (cap == null) Modifier.fillMaxSize().padding(top = 4.dp) else Modifier.fillMaxWidth().padding(top = 4.dp)) { body(this, colors) }
        }
    }
}

@Composable
private fun SheetHeader(g: TonightGroup, colors: TonightSheetColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(g.kind.icon(), contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
        Text(
            groupName(g, sheet = true).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = colors.muted,
            maxLines = 1,
        )
    }
}

/** One row of the sheet (artifact `.lr`): 40 x 60 thumb, title, "Salió a las 21:30 · Ep. 9 · te faltan 2", play or the bell. */
@Composable
private fun SheetRow(item: TonightItem, ctx: HomeRowCtx, colors: TonightSheetColors, paper: Boolean) {
    val m = tonightModel(item, ctx)
    val hidden = m.media?.let { ctx.gate.isHidden(it) && ctx.gate.mode == MatureContentMode.BLUR } == true
    val rule = colors.rule
    ContextMenuArea(entries = m.menu, sheetHeader = { MenuSheetHeader(m.title) }) { menu ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .combinedClickable(role = Role.Button, onLongClick = menu::showSheet, onClick = m.gated(ctx.gate, m.onOpen))
                .drawBehind {
                    val inset = 20.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    drawLine(rule, Offset(inset, 0f), Offset(size.width - inset, 0f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MediaCover(
                url = m.data.coverUrl,
                modifier = Modifier.width(40.dp).then(if (hidden) Modifier.blur(8.dp) else Modifier),
                contentDescription = null,
                shape = RoundedCornerShape(2.dp),
                aspectRatio = 2f / 3f,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Shrinks before it cuts: the state is the point of the row.
                Text(
                    m.line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.muted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.5.sp, maxFontSize = 13.sp),
                )
            }
            SheetAction(m.action, paper)
        }
    }
}

@Composable
private fun SheetAction(action: TonightCardAction, paper: Boolean) {
    when {
        paper -> TanzakuTonightAction(action, disc = 32.dp)
        action.bell -> TonightBell(action)
        else -> FilledTonalIconButton(onClick = action.onClick) { Icon(Icons.Filled.PlayArrow, contentDescription = action.label) }
    }
}

/** "Ver todo · N más esta noche": the same list as the row's own "Ver todo" (Biblioteca › Continuar). */
@Composable
private fun SheetFooter(count: Int, colors: TonightSheetColors, onSeeAll: () -> Unit) {
    val rule = colors.rule
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(rule, size = Size(size.width, 1.dp.toPx())) }
            .clickable(role = Role.Button, onClick = onSeeAll)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.home_see_all), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.rd_s2_tonight_more_count, count),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = colors.muted,
                maxLines = 1,
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------- E · the night on one rod

private const val NIGHT_PILE = "night:pile"
private const val NIGHT_GATHER = "night:gather"
private const val NIGHT_NOW = "night:now"

/** Where "now" sits when the row opens, as a fraction of its width from the start. */
private const val NOW_AT = 0.6f
private const val NOW_WAIT_MS = 1_000L
private const val PILE_FADE_MS = 150
private val FUTURE_WASH = 4000.dp

/**
 * E: the rod is the night. What came out hangs on the left by airing time (its time on the cord), the "now" lantern splits the row,
 * what airs later hangs on the right on duller paper with its bell. What to resume waits in a pile at the start that opens on a tap
 * into separate cards (with "Juntar" to stack them again). The row opens with "now" about 60 % from the left.
 */
@Composable
internal fun TonightNight(ctx: HomeRowCtx) {
    val all = ctx.tonight
    val resume = remember(all) { all.filterIsInstance<TonightItem.Resume>() }
    val aired = remember(all) { all.filterIsInstance<TonightItem.Aired>().sortedBy { it.episode.airingAtEpochSec } }
    val later = remember(all) { all.filterIsInstance<TonightItem.Upcoming>().sortedBy { it.episode.airingAtEpochSec } }
    var open by rememberSaveable { mutableStateOf(false) }
    val piled = resume.size > 1 && !open
    // On a wide window the closed pile stays beside the timeline (artifact `.e-grid`); a phone has no room, so it leads the row.
    val pileApart = piled && !ctx.compact
    val state = rememberLazyListState()
    val rowH = remember { mutableIntStateOf(0) }
    val spatial = LocalMotion.current.spatial
    val pileFocus = remember { FocusRequester() }
    val gatherFocus = remember { FocusRequester() }
    var pileFocused by remember { mutableStateOf(false) }
    var gatherFocused by remember { mutableStateOf(false) }
    // Only a keyboard user (whose focus was on the pile or on "Juntar") gets focus moved to the other one.
    var moveFocus by remember { mutableStateOf(false) }
    val base = remember { TonightLook(size = CardSize.MEDIUM, width = TONIGHT_MEDIUM) }
    val resumeTag = stringResource(Res.string.rd_s2_tonight_group_resume)
    val pile = if (piled) {
        val ghosts = resume.drop(1).take(2).map { r ->
            ctx.discover.entryFor(MediaRef(anilistId = r.entry.anilistId, malId = r.entry.malId))?.status?.slot()
        }
        TonightPile(
            ghosts = ghosts,
            tag = stringResource(Res.string.rd_s2_tonight_pile_tag, resume.size),
            hint = stringResource(Res.string.rd_s2_tonight_pile_hint),
            label = stringResource(Res.string.rd_s2_tonight_pile_open, resume.size),
            onOpen = {
                moveFocus = pileFocused
                open = true
            },
        )
    } else {
        null
    }
    val pileModifier = Modifier.focusRequester(pileFocus).onFocusChanged { pileFocused = it.hasFocus }
    Row(Modifier.fillMaxWidth()) {
        if (pileApart && pile != null) {
            Box(Modifier.decor(DecorRegion.TONIGHT).padding(start = ROW_SIDE, end = 4.dp)) {
                NightPile(resume.first(), pile, ctx, base, pileModifier)
            }
        }
        NjLazyRow(
            modifier = Modifier.weight(1f).decor(DecorRegion.TONIGHT).onSizeChanged { rowH.intValue = it.height },
            state = state,
            contentPadding = PaddingValues(start = if (pileApart) 8.dp else ROW_SIDE, end = ROW_SIDE),
            horizontalArrangement = Arrangement.spacedBy(rowSpacing()),
            pagingArrows = true,
        ) {
            var lead = 0
            if (pile != null && !pileApart) {
                lead = 1
                item(key = NIGHT_PILE) { NightPile(resume.first(), pile, ctx, base, nightMotion(fade = true).then(pileModifier)) }
            }
            if (pile == null) {
                lead = resume.size
                itemsIndexed(resume, key = { _, it -> it.key }) { i, item ->
                    TonightCard(item, i, ctx, nightMotion(fade = true), look = TonightLook(size = base.size, width = base.width, cordTag = resumeTag))
                }
                if (resume.size > 1) {
                    item(key = NIGHT_GATHER) {
                        val top = with(LocalDensity.current) { (rowH.intValue * 0.4f).toDp() }
                        OutlinedButton(
                            onClick = {
                                moveFocus = gatherFocused
                                open = false
                            },
                            modifier = nightMotion(fade = true)
                                .padding(top = top)
                                .focusRequester(gatherFocus)
                                .onFocusChanged { gatherFocused = it.isFocused },
                            shape = NijiyomeControls.shape,
                        ) {
                            Icon(Icons.Outlined.UnfoldLess, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.rd_s2_tonight_pile_gather))
                        }
                    }
                }
            }
            val from = lead
            itemsIndexed(aired, key = { _, it -> it.key }) { i, item ->
                val at = formatTime(item.episode.airingAtEpochSec)
                TonightCard(item, from + i, ctx, nightMotion(fade = false), look = TonightLook(size = base.size, width = base.width, cordTag = at))
            }
            item(key = NIGHT_NOW, contentType = NIGHT_NOW) { NowLantern(ctx.nowSec, { rowH.intValue }, nightMotion(fade = false)) }
            itemsIndexed(later, key = { _, it -> it.key }) { i, item ->
                val at = formatTime(item.episode.airingAtEpochSec)
                TonightCard(
                    item,
                    from + aired.size + i,
                    ctx,
                    nightMotion(fade = false),
                    look = TonightLook(size = base.size, width = base.width, cordTag = at, dim = true),
                )
            }
        }
    }
    LaunchedEffect(open) {
        snapshotFlow { state.layoutInfo.viewportSize.width }.first { it > 0 }
        if (open) {
            if (spatial) state.animateScrollToItem(0) else state.scrollToItem(0)
        } else {
            val before = when {
                pile != null && !pileApart -> 1
                pile == null -> resume.size + if (resume.size > 1) 1 else 0
                else -> 0
            }
            state.scrollToItem(before + aired.size)
            val now = withTimeoutOrNull(NOW_WAIT_MS) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == NIGHT_NOW } }.filterNotNull().first()
            }
            if (now != null) {
                val info = state.layoutInfo
                state.scrollBy(now.offset - info.viewportStartOffset - info.viewportSize.width * NOW_AT)
            }
        }
        if (moveFocus) {
            moveFocus = false
            withFrameNanos { }
            // The other control may be off screen (not composed yet): then focus simply stays where the row put it.
            runCatching { (if (open) gatherFocus else pileFocus).requestFocus() }
        }
    }
}

/**
 * The pile's motion (the one animation this row adds): opening and stacking fade the cards in and out and, under the full level, the
 * rest of the rod springs aside. Calm and light fade in 150 ms without travel; off changes at once.
 */
@Composable
private fun LazyItemScope.nightMotion(fade: Boolean): Modifier = when (LocalMotion.current.level) {
    MotionLevel.FULL -> Modifier.animateItem(
        fadeInSpec = if (fade) spring<Float>(stiffness = Spring.StiffnessMediumLow) else null,
        placementSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow),
        fadeOutSpec = if (fade) spring<Float>(stiffness = Spring.StiffnessMediumLow) else null,
    )
    MotionLevel.CALM, MotionLevel.PERF ->
        if (fade) Modifier.animateItem(fadeInSpec = tween<Float>(PILE_FADE_MS), placementSpec = null, fadeOutSpec = tween<Float>(PILE_FADE_MS)) else Modifier
    MotionLevel.OFF -> Modifier
}

/** The closed pile: its top card (count on the cord, papers behind) and, on paper, "Toca para abrir" under it. */
@Composable
private fun NightPile(first: TonightItem.Resume, pile: TonightPile, ctx: HomeRowCtx, look: TonightLook, modifier: Modifier) {
    val paper = tanzakuTonightCards()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TonightCard(first, 0, ctx, look = TonightLook(size = look.size, width = look.width, pile = pile))
        // Other kits already say it in the card's caption.
        if (paper) {
            Text(
                pile.hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * The "now" marker of the night (artifact `.now`): a lantern pill "Ahora 22:10" that follows the minute clock, its line down the
 * whole rod and a faint wash over what is still to come. The line and the wash are drawn from this item to the rod's height.
 */
@Composable
private fun NowLantern(nowSec: () -> Long, rowHeight: () -> Int, modifier: Modifier) {
    val label = stringResource(Res.string.rd_s2_now_at, formatTime(nowSec()))
    val paperCards = tanzakuTonightCards()
    val tone = LocalThemeTone.current
    val cs = MaterialTheme.colorScheme
    val lantern = if (paperCards) TanabataMaterials.lantern(tone) else cs.tertiary
    val onLantern = when {
        !paperCards -> cs.onTertiary
        tone == ThemeTone.LIGHT -> Color.White
        else -> TanabataMaterials.ink
    }
    val future = cs.onSurface.copy(alpha = 0.04f)
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics { contentDescription = label }
            .drawBehind {
                val h = max(rowHeight(), size.height.toInt()).toFloat()
                val cx = size.width / 2f
                drawRect(future, Offset(cx, 0f), Size(FUTURE_WASH.toPx(), h))
                drawLine(
                    Brush.verticalGradient(0f to lantern, 0.6f to lantern, 1f to lantern.copy(alpha = 0.1f), startY = 0f, endY = h),
                    Offset(cx, 0f),
                    Offset(cx, h),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = onLantern,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp).background(lantern, CircleShape).padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

/** Side padding of every rod (the rows of Home use the same 16 dp). */
private val ROW_SIDE = 16.dp

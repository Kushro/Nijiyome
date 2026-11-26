// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalFoundationApi::class)

package app.nijiyome.ui.cards.tanabata

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.direction.LocalDirection
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.tanabata.TanabataDirection
import app.nijiyome.designsystem.direction.tanabata.TanabataMaterials
import app.nijiyome.designsystem.direction.tanabata.drawHole
import app.nijiyome.designsystem.direction.tanabata.drawInkDown
import app.nijiyome.designsystem.direction.tanabata.drawThread
import app.nijiyome.designsystem.direction.tanabata.tanzakuPaper
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.ui.cards.CardPlayTarget
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.look.CardSizing
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_contract_stamp_new
import app.nijiyome.ui.resources.rd_contract_stamp_soon
import app.nijiyome.ui.resources.rd_tanabata_continue
import app.nijiyome.ui.resources.rd_tanabata_movie
import app.nijiyome.ui.resources.ui_episodes_short
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D1. What the four tanzaku styles share (docs/redesign/artefacto/dir-tanabata.{css,js}): widths, text lines,
// ink progress, the hanging thread, the focus ring, and the two contexts every style draws the same way (Compacta, Continuar).

/** Poster ratio of the tanzaku cards (artifact `aspect-ratio: 2 / 3`). */
internal const val TZ_POSTER_RATIO = 2f / 3f

/** Thread above a hanging card in a row, and the longer one of "Esta noche" (artifact `--tz-thread-h`). */
internal val TZ_THREAD = 16.dp
internal val TZ_THREAD_TONIGHT = 22.dp

/** The hole of a paper sits 9 dp below its top edge (6 dp + its 3 dp radius); the thread ends there with its knot. */
internal val TZ_HOLE_Y = 9.dp

internal val tzPaperShape = RoundedCornerShape(2.dp)

/** Compact density packs every card 13 % narrower, as the Material cards do. */
@Composable
internal fun Dp.dense(): Dp = if (LocalLook.current.density == Density.COMPACT) this * CardSizing.COMPACT_FACTOR else this

/** Width of the tall strip styles (tanzaku, sello, hilo) in a row (artifact 112 / 150 / 196 px; XL one step more). */
internal fun stripWidth(size: CardSize): Dp = when (size) {
    CardSize.SMALL -> 112.dp
    CardSize.MEDIUM -> 150.dp
    CardSize.LARGE -> 196.dp
    CardSize.XL -> 236.dp
}

/** Continuar: SMALL / MEDIUM hang as tall strips (as in "Esta noche"), LARGE / XL as the wide strip (artifact 300 / 340 px). */
internal fun continueWidth(size: CardSize): Dp = when (size) {
    CardSize.SMALL -> 128.dp
    CardSize.MEDIUM -> 138.dp
    CardSize.LARGE -> 300.dp
    CardSize.XL -> 340.dp
}

/** Width of a hanging Continuar strip: the wide sizes hang too, one step wider (artifact `[data-region="tonight"]` 140 / 138 / 160 px). */
internal fun hangingContinueWidth(size: CardSize): Dp = when (size) {
    CardSize.SMALL -> 128.dp
    CardSize.MEDIUM -> 138.dp
    CardSize.LARGE -> 160.dp
    CardSize.XL -> 180.dp
}

/** Whether the cards hang from a rod here: only under Tanabata, whose decor draws the rod over rows (elsewhere the paper shows its hole). */
@Composable
internal fun tzHangs(): Boolean = LocalDirection.current.id == TanabataDirection.id

/** Everything a tanzaku writes, already localised. */
@Immutable
internal class TzTexts(
    val title: String,
    /** "Viendo", or null without a list status or when the screen hides it. */
    val statusName: String?,
    /** "Viendo · 3/12", "Pendiente · 12 ep", or the facts line when there is no status. */
    val statusLine: String,
    /** Only the episodes part: "3/12", "12 ep", "TV Short". */
    val eps: String?,
    /** The wish line: when the next episode airs or where to resume ("Ep 9 · en 6 d"). */
    val wish: String?,
    /** "TV · 2026". */
    val facts: String,
)

@Composable
internal fun tzTexts(data: TitleCardData, show: CardShow): TzTexts {
    val total = data.episodesTotal
    val eps = when {
        total == null || total <= 0 -> null
        total == 1 -> data.format ?: stringResource(Res.string.rd_tanabata_movie)
        data.listStatus == null || data.listStatus == StatusSlot.PLANNING -> pluralStringResource(Res.plurals.ui_episodes_short, total, total)
        else -> "${data.episodesSeen ?: 0}/$total"
    }
    val facts = data.subtitle ?: listOfNotNull(data.format, data.year?.toString()).joinToString(" · ")
    val name = if (show.status) data.listStatusLabel else null
    val statusLine = if (name != null) listOfNotNull(name, eps).joinToString(" · ") else facts
    return TzTexts(
        title = data.title,
        statusName = name,
        statusLine = statusLine,
        eps = eps,
        wish = data.nextLabel?.takeIf { it.isNotBlank() },
        facts = facts,
    )
}

/** Progress drawn as ink: the screen's progress, else episodes seen over total; 0 when the screen hides progress. */
internal fun TitleCardData.ink(show: CardShow): Float {
    if (!show.progress) return 0f
    val series = if (episodesSeen != null && episodesTotal != null && episodesTotal > 0) episodesSeen.toFloat() / episodesTotal else null
    return (progress ?: series ?: 0f).coerceIn(0f, 1f)
}

/** The paper of this title's list status (kinari without one). */
@Composable
internal fun TitleCardData.paper(): Color = TanabataMaterials.paper(listStatus, LocalThemeTone.current)

/** Interaction state of a card, read once at its root. */
@Immutable
internal class TzState(val pressed: Boolean, val focused: Boolean, val hovered: Boolean)

@Composable
internal fun rememberTzState(interaction: MutableInteractionSource): TzState {
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    return TzState(pressed, focused, hovered)
}

/** Click, long click and one merged semantics node (the texts, plus [description] when the visible text is not enough). */
internal fun Modifier.tzClickable(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    description: String? = null,
): Modifier = this
    .combinedClickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onLongClick = onLongClick,
        onClick = onClick,
    )
    .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)

/**
 * Keyboard / D-pad focus: a 2 dp lantern ring 3 dp outside the paper (artifact `:focus-visible` on `.tz-paper`); hover: a faint ink
 * ring on the edge. Apply before the paper clips.
 */
@Composable
internal fun Modifier.tzFocusRing(state: TzState, corner: Dp = 2.dp): Modifier {
    val lantern = TanabataMaterials.lantern(LocalThemeTone.current)
    val hoverInk = TanabataMaterials.ink.copy(alpha = 0.3f)
    if (!state.focused && !state.hovered) return this
    return this.drawWithContent {
        drawContent()
        if (state.focused) {
            val out = 3.dp.toPx() + 1.dp.toPx()
            drawRoundRect(
                color = lantern,
                topLeft = Offset(-out, -out),
                size = Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = CornerRadius(corner.toPx() + out),
                style = Stroke(2.dp.toPx()),
            )
        } else {
            drawRoundRect(hoverInk, cornerRadius = CornerRadius(corner.toPx()), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Draws the thread from the rod (top of the card) down to the hole of a paper whose top edge is at [paperTop]. */
@Composable
internal fun Modifier.tzThread(
    paperTop: Dp,
    centerFraction: Float = 0.5f,
    knot: Boolean = true,
    color: Color? = null,
    dashed: Boolean = false,
): Modifier {
    val thread = color ?: TanabataMaterials.thread(LocalThemeTone.current)
    return this.drawWithContent {
        drawContent()
        val x = size.width * centerFraction
        drawThread(thread, x, top = 2.dp.toPx(), bottom = (paperTop + TZ_HOLE_Y).toPx() + 1.dp.toPx(), knot = knot, dashed = dashed)
    }
}

/** What a hanging Continuar strip of "Esta noche" is (artifact `data-tz-kind`): just out, airing later, or to resume. */
internal enum class TzTonightKind { NEW, SOON, RESUME }

/**
 * NEW when a new episode is out ([TitleCardData.isNew]); SOON when the next one has not aired yet (the screen gave the aired count
 * without anything new, or the title has not started); RESUME otherwise.
 */
internal fun TitleCardData.tzTonightKind(): TzTonightKind = when {
    isNew -> TzTonightKind.NEW
    airedEpisodes != null || airing == MediaStatusKind.UPCOMING -> TzTonightKind.SOON
    else -> TzTonightKind.RESUME
}

/** Lantern amber of a new episode's thread and knot (artifact `#ffb54d`). */
private val TZ_NEW_THREAD = Color(0xFFFFB54D)

/** The "Nuevo" / "Pronto" stamp across the top of a strip (artifact `.tz-paper::after`, tilted 4°). */
@Composable
private fun TzStamp(kind: TzTonightKind, modifier: Modifier = Modifier) {
    if (kind == TzTonightKind.RESUME) return
    val shape = RoundedCornerShape(3.dp)
    val base = modifier.rotate(-4f)
    val styled = if (kind == TzTonightKind.NEW) {
        base.background(Brush.verticalGradient(listOf(Color(0xFFFFC56B), Color(0xFFF59A2A))), shape)
    } else {
        base
            .background(TanabataMaterials.paper(null, LocalThemeTone.current), shape)
            .border(1.dp, Color(0x5914102A), shape)
    }
    Text(
        stringResource(if (kind == TzTonightKind.NEW) Res.string.rd_contract_stamp_new else Res.string.rd_contract_stamp_soon),
        style = TzType.sub(10.5.sp, FontWeight.ExtraBold).copy(letterSpacing = 0.2.sp),
        color = if (kind == TzTonightKind.NEW) Color(0xFF2A1600) else TanabataMaterials.ink,
        maxLines = 1,
        modifier = styled.padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** The thread hole of a paper, drawn over its content at the top center (or at [x] from the start). */
@Composable
internal fun Modifier.tzHole(top: Dp = 6.dp, x: Dp? = null): Modifier {
    val ground = MaterialTheme.colorScheme.background
    return this.drawWithContent {
        drawContent()
        val cx = x?.toPx() ?: (size.width / 2f)
        drawHole(Offset(cx, top.toPx() + 3.dp.toPx()), ground)
    }
}

/** Text styles of the cards, from the active type scale (the family follows the direction). */
internal object TzType {
    @Composable fun title(size: TextUnit = 12.5.sp): TextStyle =
        MaterialTheme.typography.labelLarge.copy(fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.Bold)

    @Composable fun display(): TextStyle =
        MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)

    @Composable fun sub(size: TextUnit = 11.sp, weight: FontWeight = FontWeight.Medium): TextStyle =
        MaterialTheme.typography.labelSmall.copy(fontSize = size, lineHeight = size * 1.35f, fontWeight = weight, letterSpacing = 0.sp)
}

/** Score badge over a poster (artifact `.tz-score`). */
@Composable
internal fun TzScore(score: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(0xD10E1230), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFCF7A), modifier = Modifier.size(12.dp))
        Text(score, style = TzType.sub(weight = FontWeight.ExtraBold), color = Color(0xFFFFF2D6), maxLines = 1)
    }
}

/** A poster with the tanzaku ratio. */
@Composable
internal fun TzPoster(url: String?, shape: Shape, modifier: Modifier = Modifier) {
    MediaCover(url = url, modifier = modifier.fillMaxWidth(), contentDescription = null, shape = shape, aspectRatio = TZ_POSTER_RATIO)
}

/**
 * Text written sideways, top to bottom (artifact `writing-mode: vertical-rl`), at most [length] long; [fixed] keeps the column exactly
 * [length] tall (so strips in a row line up), otherwise it is as tall as the text. The visible text is decorative: the card carries the
 * same words in its description.
 */
@Composable
internal fun VerticalText(text: String, style: TextStyle, color: Color, length: Dp, modifier: Modifier = Modifier, fixed: Boolean = true) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier
            .clearAndSetSemantics { }
            .layout { measurable, _ ->
                val len = length.roundToPx()
                val p = measurable.measure(Constraints(maxWidth = len))
                layout(p.height, if (fixed) len else p.width) {
                    // Turn 90° clockwise about the text's center, then sit the column at the top.
                    p.placeWithLayer(x = (p.height - p.width) / 2, y = (p.width - p.height) / 2) {
                        rotationZ = 90f
                    }
                }
            },
    )
}

/** A status tag: a small tanzaku with its hole (artifact `.tz-tag`). */
@Composable
internal fun TzTag(label: String, status: StatusSlot?, modifier: Modifier = Modifier) {
    val paper = TanabataMaterials.paper(status, LocalThemeTone.current)
    Box(
        modifier
            .tanzakuPaper(paper, RoundedCornerShape(3.dp), elevated = false, fibreTile = 120.dp)
            .tzHole(top = 0.dp, x = 7.dp)
            .padding(start = 15.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Text(label, style = TzType.sub(weight = FontWeight.ExtraBold), color = TanabataMaterials.ink, maxLines = 1)
    }
}

/** Compacta, the same for every style: a list row with a small poster and the status as a paper tag. */
@Composable
internal fun TzCompactRow(
    data: TitleCardData,
    show: CardShow,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val lantern = TanabataMaterials.lantern(LocalThemeTone.current)
    val hover = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.pressed) 0.10f else if (state.hovered) 0.06f else 0f)
    val shape = MaterialTheme.shapes.small
    val sub = buildString {
        append(texts.facts)
        if (data.listStatus != null && data.listStatus != StatusSlot.PLANNING && (data.episodesTotal ?: 0) > 1 && texts.eps != null) {
            append(" · ").append(texts.eps)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (state.focused) Modifier.drawWithContent { drawContent(); drawRoundRect(lantern, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx())) } else Modifier)
            .background(hover, shape)
            .tzClickable(interaction, onClick, onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TzPoster(data.coverUrl, RoundedCornerShape(3.dp), Modifier.width(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(texts.title, style = TzType.title(14.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Text(sub, style = TzType.sub(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (show.status && data.listStatus != null && texts.statusName != null) TzTag(texts.statusName, data.listStatus)
    }
}

/** Which look a Continuar card takes inside its paper. */
internal enum class TzContinueVariant {
    /** tanzaku: the whole strip is the status paper, ink runs down it. */
    PAPER,

    /** tanzaku-vertical: as PAPER, with the title also written sideways beside the thumb. */
    VERTICAL,

    /** tanzaku-sello / tanzaku-hilo: a quiet panel; the status is a tag, progress an accent wash from the left. */
    PANEL,
}

/**
 * Continuar: a tall hanging strip for SMALL / MEDIUM (every style), the wide strip for LARGE / XL. Under a rod (Tanabata) every size
 * hangs as a tall strip: a wide strip has no thread to hang by and read as a bar stuck to the rod ("Extra grande" lost the tanzaku
 * of "Esta noche", with its thread and stamps).
 */
@Composable
internal fun TzContinue(
    data: TitleCardData,
    size: CardSize,
    show: CardShow,
    variant: TzContinueVariant,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    if (size.isWide && !tzHangs()) TzContinueWide(data, size, show, variant, onClick, onLongClick, modifier)
    else TzContinueTall(data, size, show, onClick, onLongClick, modifier)
}

@Composable
private fun continueSub(data: TitleCardData, texts: TzTexts): String = texts.wish ?: data.subtitle.orEmpty()

/** The play disc of Continuar; wrapped in [CardPlayTarget] it is the card's play button when the screen gives one. */
@Composable
internal fun TzPlayDisc(background: Color, glyph: Color, modifier: Modifier = Modifier, lifted: Boolean = false) {
    val perf = LocalMotion.current.level == MotionLevel.PERF
    Box(
        modifier
            .size(44.dp)
            .then(if (lifted && !perf) Modifier.shadow(4.dp, CircleShape) else Modifier)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = glyph, modifier = Modifier.size(24.dp))
    }
}

/** "Esta noche" strip: thread, status paper, poster with the play disc, title, where to resume, status; ink runs down the text. */
@Composable
private fun TzContinueTall(
    data: TitleCardData,
    size: CardSize,
    show: CardShow,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val base = data.paper()
    val paper = if (state.pressed) TanabataMaterials.pressed(base) else base
    val ink = data.ink(show)
    val hangs = tzHangs()
    val threadH = if (hangs) TZ_THREAD_TONIGHT else 0.dp
    val sub = continueSub(data, texts)
    val description = stringResource(Res.string.rd_tanabata_continue, texts.title, sub.ifEmpty { texts.statusLine })
    // Hanging in "Esta noche": a new episode gets the amber thread and knot and the "Nuevo" stamp, one still to air a dotted thread
    // and the "Pronto" stamp; a strip to resume keeps only its ink.
    val kind = if (hangs) data.tzTonightKind() else TzTonightKind.RESUME
    val thread = if (kind == TzTonightKind.NEW) TZ_NEW_THREAD else null
    Column(
        modifier
            .width(hangingContinueWidth(size).dense())
            .tzClickable(interaction, onClick, onLongClick, description)
            .then(if (hangs) Modifier.tzThread(threadH, color = thread, dashed = kind == TzTonightKind.SOON) else Modifier)
            .padding(top = threadH),
    ) {
        Column(
            Modifier
                .tzFocusRing(state)
                .tanzakuPaper(paper)
                .tzHole(),
        ) {
            Box(Modifier.padding(start = 6.dp, end = 6.dp, top = 16.dp)) {
                TzPoster(data.coverUrl, tzPaperShape)
                TzStamp(kind, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 5.dp))
                CardPlayTarget(texts.title, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 8.dp)) {
                    TzPlayDisc(TanabataMaterials.ink, base, lifted = true)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .then(if (ink > 0f) Modifier.drawWithContent { drawInkDown(base, ink); drawContent() } else Modifier)
                    .padding(start = 9.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(texts.title, style = TzType.title(), color = TanabataMaterials.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, style = TzType.sub(), color = TanabataMaterials.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                texts.statusName?.let {
                    Text(it, style = TzType.sub(weight = FontWeight.Bold), color = TanabataMaterials.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The wide Continuar strip: thumb, title, where to resume, status, play disc. */
@Composable
private fun TzContinueWide(
    data: TitleCardData,
    size: CardSize,
    show: CardShow,
    variant: TzContinueVariant,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = rememberTzState(interaction)
    val texts = tzTexts(data, show)
    val base = data.paper()
    val ink = data.ink(show)
    val sub = continueSub(data, texts)
    val description = stringResource(Res.string.rd_tanabata_continue, texts.title, sub.ifEmpty { texts.statusLine })
    val panel = variant == TzContinueVariant.PANEL
    val cs = MaterialTheme.colorScheme
    val textColor = if (panel) cs.onSurface else TanabataMaterials.ink
    val subColor = if (panel) cs.onSurfaceVariant else TanabataMaterials.ink
    val shape = if (panel) MaterialTheme.shapes.medium else tzPaperShape
    val wash = cs.primary.copy(alpha = 0.14f)
    val surface = if (panel) {
        Modifier
            .tzFocusRing(state, corner = 10.dp)
            .background(if (state.pressed) cs.surfaceContainerHighest else cs.surfaceContainerHigh, shape)
            .drawWithContent {
                if (ink > 0f) drawRect(wash, size = Size(this.size.width * ink, this.size.height))
                drawContent()
                drawRoundRect(cs.outlineVariant, cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
    } else {
        Modifier
            .tzFocusRing(state)
            .tanzakuPaper(if (state.pressed) TanabataMaterials.pressed(base) else base, ink = ink)
            .tzHole(top = 1.dp)
    }
    Row(
        modifier
            .width(continueWidth(size).dense())
            .tzClickable(interaction, onClick, onLongClick, description)
            .then(surface)
            .heightIn(min = 100.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumbShape = if (panel) RoundedCornerShape(6.dp) else tzPaperShape
        TzPoster(
            data.coverUrl,
            thumbShape,
            Modifier
                .width(if (variant == TzContinueVariant.VERTICAL) 50.dp else 58.dp)
                .then(if (panel) Modifier.drawWithContent { drawContent(); drawRect(base, size = Size(this.size.width, 3.dp.toPx())) } else Modifier),
        )
        if (variant == TzContinueVariant.VERTICAL) {
            VerticalText(texts.title, TzType.title(13.sp).copy(fontWeight = FontWeight.ExtraBold), textColor, length = 80.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (variant != TzContinueVariant.VERTICAL) {
                Text(texts.title, style = TzType.title(14.sp), color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (sub.isNotEmpty()) Text(sub, style = TzType.sub(), color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            texts.statusName?.let {
                if (panel) TzTag(it, data.listStatus) else Text(it, style = TzType.sub(12.5.sp), color = textColor, maxLines = 1)
            }
        }
        CardPlayTarget(texts.title) {
            if (panel) TzPlayDisc(cs.primary, cs.onPrimary) else TzPlayDisc(TanabataMaterials.ink, base)
        }
    }
}

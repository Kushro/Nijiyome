// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.sumi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_sumi_mark
import app.nijiyome.designsystem.resources.rd_sumi_mark_mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The ink objects of Sumi y hanko, public so the Sumi card styles of :core:ui draw the same seal, ensō and stroke as the decor.
// Two objects do real work: the hanko seal is the list status, the ensō stroke is the progress of what you are watching.

/** Kanji of each list status: 見 watching, 予 planned (予定), 完 completed (完了), 休 paused (休止), 止 dropped (中止), 再 again. */
object SumiKanji {
    /** 済 (済み, done): the seal stamped on a watched episode. */
    const val WATCHED = "済"

    /** 虹 (niji): the seal of the mark. */
    const val NIJI = "虹"

    fun of(status: StatusSlot): String = when (status) {
        StatusSlot.WATCHING -> "見"
        StatusSlot.PLANNING -> "予"
        StatusSlot.COMPLETED -> "完"
        StatusSlot.PAUSED -> "休"
        StatusSlot.DROPPED -> "止"
        StatusSlot.REWATCHING -> "再"
    }
}

/** Exponential ease-out of the direction (`cubic-bezier(.16, 1, .3, 1)`). */
val SumiEase: CubicBezierEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Seal shu red of the mark (`--k-red`). */
val SumiShu: Color = Color(0xFFC63A26)

/** Paper of the mark tile (`--k-paper`), the same in every tone. */
val SumiMarkPaper: Color = Color(0xFFEFF0F1)

/** The kanji ink that reads on a seal of [seal] color: sumi on light seals (shubun), paper on deep ones (hakubun). */
fun sumiKanjiInkOn(seal: Color): Color = if (seal.luminance() > 0.33f) Color(0xFF131217) else Color.White

/**
 * A brush ring of variable width (pressure at the start, a dry tail at the end), the ensō of the mark. Angles in degrees,
 * clockwise from three o'clock like the artifact's `ensoPath`.
 */
fun sumiEnsoPath(cx: Float, cy: Float, r: Float, w: Float, startDeg: Float = -55f, sweepDeg: Float = 330f, steps: Int = 56): Path {
    val outer = ArrayList<Offset>(steps + 1)
    val inner = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = ((startDeg + sweepDeg * t) * PI / 180.0).toFloat()
        val press = if (t < 0.1f) 0.55f + 4.5f * t else 1f - 0.78f * ((t - 0.1f) / 0.9f).toDouble().pow(1.7).toFloat()
        val th = w * press
        val rr = r + sin(t * 9.5f) * w * 0.05f
        val ox = cos(a)
        val oy = sin(a)
        outer += Offset(cx + ox * (rr + th / 2f), cy + oy * (rr + th / 2f))
        inner += Offset(cx + ox * (rr - th / 2f), cy + oy * (rr - th / 2f))
    }
    return Path().apply {
        moveTo(outer[0].x, outer[0].y)
        for (i in 1 until outer.size) lineTo(outer[i].x, outer[i].y)
        for (i in inner.indices.reversed()) lineTo(inner[i].x, inner[i].y)
        close()
    }
}

/** The short brush stroke of `--sm-brush` (viewBox 120 × 8) scaled to [size]. */
fun sumiBrushPath(size: Size): Path {
    val sx = size.width / 120f
    val sy = size.height / 8f
    return Path().apply {
        moveTo(1f * sx, 4.6f * sy)
        cubicTo(8f * sx, 2.2f * sy, 30f * sx, 1.4f * sy, 60f * sx, 1.8f * sy)
        cubicTo(88f * sx, 2.1f * sy, 108f * sx, 2.6f * sy, 119f * sx, 3.4f * sy)
        cubicTo(116f * sx, 4.9f * sy, 104f * sx, 5.6f * sy, 86f * sx, 6f * sy)
        cubicTo(58f * sx, 6.6f * sy, 26f * sx, 6.9f * sy, 4f * sx, 7.2f * sy)
        cubicTo(1.5f * sx, 6.6f * sy, 0.5f * sx, 5.6f * sy, 1f * sx, 4.6f * sy)
        close()
    }
}

/** The ink wash with brushed edges of `--sm-wash` (viewBox 400 × 100) scaled to [size]. */
fun sumiWashPath(size: Size): Path {
    val sx = size.width / 400f
    val sy = size.height / 100f
    return Path().apply {
        moveTo(6f * sx, 10f * sy)
        cubicTo(80f * sx, 4f * sy, 200f * sx, 8f * sy, 330f * sx, 5f * sy)
        cubicTo(360f * sx, 4f * sy, 385f * sx, 6f * sy, 396f * sx, 9f * sy)
        lineTo(394f * sx, 30f * sy)
        lineTo(398f * sx, 55f * sy)
        lineTo(393f * sx, 92f * sy)
        cubicTo(300f * sx, 97f * sy, 150f * sx, 93f * sy, 40f * sx, 96f * sy)
        cubicTo(25f * sx, 96f * sy, 12f * sx, 95f * sy, 4f * sx, 93f * sy)
        lineTo(7f * sx, 60f * sy)
        lineTo(2f * sx, 35f * sy)
        close()
    }
}

/** A short brush stroke in [color] (under hero titles, the active tab), [width] × [height]. */
@Composable
fun SumiBrushStroke(color: Color, modifier: Modifier = Modifier, width: Dp = 88.dp, height: Dp = 6.dp) {
    Canvas(modifier.size(width, height).clearAndSetSemantics { }) { drawPath(sumiBrushPath(size), color) }
}

/**
 * Stamp of a seal when [key] changes after the first composition (the signature of the direction): FULL and PERF drop it from
 * 1.15 to 1 in 420 ms, CALM fades it in 140 ms because it confirms the action, OFF does nothing. [animate] false = the new
 * value lands without a stamp (a seal being lifted, not stamped).
 */
@Composable
fun Modifier.sumiStamp(key: Any?, animate: Boolean = true): Modifier {
    val level = LocalMotion.current.level
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (seen == key) return@LaunchedEffect
        seen = key
        if (!animate) return@LaunchedEffect
        when (level) {
            MotionLevel.FULL, MotionLevel.PERF -> {
                scale.snapTo(1.15f)
                alpha.snapTo(0f)
                launch { alpha.animateTo(1f, tween(150, easing = LinearEasing)) }
                scale.animateTo(1f, tween(420, easing = SumiEase))
            }
            MotionLevel.CALM -> {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(140, easing = LinearEasing))
            }
            MotionLevel.OFF -> Unit
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

/**
 * A hanko seal with the kanji of [status]. [blank] = an uninked seal (outline only, the statuses not chosen in a picker);
 * [round] = the round seal of tags; [brush] = the kanji in the brush face (large seals only); [halo] = a ring in that color
 * around it, for seals stamped on a poster. Decorative: the caller gives the status in words (label, tooltip or semantics).
 */
@Composable
fun HankoSeal(
    status: StatusSlot,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    round: Boolean = false,
    brush: Boolean = false,
    blank: Boolean = false,
    halo: Color? = null,
    color: Color = LocalStatusColors.current.of(status),
) {
    SealBox(SumiKanji.of(status), color, modifier, size, round, brush, blank, halo, rotation = if (round || blank) 0f else -4f)
}

/** The seal box shared by [HankoSeal] and the 済 / 虹 seals. */
@Composable
internal fun SealBox(
    kanji: String,
    ink: Color,
    modifier: Modifier,
    size: Dp,
    round: Boolean,
    brush: Boolean,
    blank: Boolean,
    halo: Color?,
    rotation: Float,
) {
    val density = LocalDensity.current
    val shape = if (round) CircleShape else RoundedCornerShape(if (size >= 30.dp) 4.dp else 3.dp)
    val kanjiInk = if (blank) MaterialTheme.colorScheme.onSurfaceVariant else sumiKanjiInkOn(ink)
    val frame = if (blank) MaterialTheme.colorScheme.outline else kanjiInk.copy(alpha = 0.55f)
    val family = if (brush) sumiBrushFamily() else sumiUiFamily()
    val fontSize = with(density) { (size * if (brush) 0.64f else 0.58f).toSp() }
    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                if (halo != null) {
                    val grow = 2.dp.toPx()
                    val radius = if (round) this.size.minDimension / 2f + grow else 3.dp.toPx() + grow
                    drawRoundRect(
                        color = halo,
                        topLeft = Offset(-grow, -grow),
                        size = Size(this.size.width + grow * 2, this.size.height + grow * 2),
                        cornerRadius = CornerRadius(radius),
                    )
                }
            }
            .clip(shape)
            .then(if (blank) Modifier else Modifier.background(ink))
            .drawBehind {
                // The inner frame of a carved seal: a thin line inset from the edge, in the kanji ink.
                val inset = if (blank) 0.75.dp.toPx() else 2.5.dp.toPx()
                val stroke = if (blank) 1.5.dp.toPx() else 1.dp.toPx()
                val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                if (round) {
                    drawCircle(frame, radius = box.minDimension / 2f, style = Stroke(stroke))
                } else {
                    drawRoundRect(frame, topLeft = Offset(inset, inset), size = box, cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(stroke))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            kanji,
            color = kanjiInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(
                fontFamily = family,
                fontWeight = if (brush) FontWeight.Normal else FontWeight.Bold,
                fontSize = fontSize,
                lineHeight = fontSize,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

/** The round 済 seal of a watched episode, in the completed ink, tilted -8°. */
@Composable
fun WatchedSeal(modifier: Modifier = Modifier, size: Dp = 22.dp, halo: Color? = null) {
    SealBox(SumiKanji.WATCHED, LocalStatusColors.current.completed, modifier, size, round = true, brush = false, blank = false, halo = halo, rotation = -8f)
}

/** The ink progress line (`.sm-line`): 3 dp, a 15 % ink track and the accent fill. */
@Composable
fun SumiInkLine(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, thickness: Dp = 3.dp) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

/**
 * The ensō ring around a play button: a full wash of the brush ring, the real progress arc in [color] and a thinner dry arc
 * outside it. When [traceIndex] is not null the arc is traced from 0 the first time this ring is shown (720 ms, 70 ms stagger
 * by index, at most six): FULL traces every ring, PERF only the first, CALM and OFF show it complete. [traceKey] identifies
 * the ring across recompositions so it is traced once.
 */
@Composable
fun EnsoRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    wash: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
    traceIndex: Int? = null,
    traceKey: String? = null,
) {
    val target = progress.coerceIn(0.02f, 1f)
    val level = LocalMotion.current.level
    val traces = traceIndex != null && traceIndex < 6 && when (level) {
        MotionLevel.FULL -> true
        MotionLevel.PERF -> traceIndex == 0
        else -> false
    }
    var traced by rememberSaveable(traceKey) { mutableStateOf(!traces) }
    val anim = remember { Animatable(if (traced) target else 0f) }
    LaunchedEffect(target, traced) {
        if (!traced) {
            delay((traceIndex ?: 0) * 70L)
            anim.animateTo(target, tween(720, easing = SumiEase))
            traced = true
        } else {
            anim.snapTo(target)
        }
    }
    val washVisible = LocalMotion.current.rich
    Canvas(modifier.clearAndSetSemantics { }) {
        val d = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = d * 25.5f / 60f
        if (washVisible) drawPath(sumiEnsoPath(c.x, c.y, r, d * 5.2f / 60f), wash)
        val arc = 360f * anim.value
        val stroke = d * 3.6f / 60f
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = arc,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val dryR = d * 27.4f / 60f
        drawArc(
            color = color.copy(alpha = 0.55f),
            startAngle = -87f,
            sweepAngle = arc * 0.9f,
            useCenter = false,
            topLeft = Offset(c.x - dryR, c.y - dryR),
            size = Size(dryR * 2, dryR * 2),
            style = Stroke(width = d * 1.1f / 60f, cap = StrokeCap.Round),
        )
    }
}

/**
 * The mark: the kitsune inside the ensō with the 虹 seal (the app icon, `rd_sumi_mark`). [tile] = on its paper tile, as in
 * the title bar. Without a tile, dark tones draw the single-ink mark in [ink] with the red seal on top, so it reads on sumi.
 */
@Composable
fun SumiMark(
    modifier: Modifier = Modifier,
    tile: Boolean = false,
    contentDescription: String? = null,
    ink: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier.clearAndSetSemantics { }
    val onPaper = tile || LocalThemeTone.current == ThemeTone.LIGHT
    Box(
        modifier
            .then(described)
            .then(if (tile) Modifier.clip(RoundedCornerShape(22)).background(SumiMarkPaper) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (onPaper) {
            Image(painterResource(Res.drawable.rd_sumi_mark), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painterResource(Res.drawable.rd_sumi_mark_mono),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.fillMaxSize(),
            )
            // The single-ink file draws the 虹 seal as an outline; ink it in shu (crop: x, y 234..268 of 306).
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 306f
                drawRoundRect(
                    color = SumiShu,
                    topLeft = Offset(233f * u, 233f * u),
                    size = Size(36f * u, 36f * u),
                    cornerRadius = CornerRadius(5f * u),
                )
            }
            NijiOnSeal(Modifier.fillMaxSize())
        }
    }
}

/** The 虹 kanji over the inked seal of [SumiMark]. */
@Composable
private fun NijiOnSeal(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val u = maxWidth / 306f
        val fontSize = with(LocalDensity.current) { (u * 26f).toSp() }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (u * 233f).toPx()
                    translationY = (u * 233f).toPx()
                }
                .size(u * 36f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                SumiKanji.NIJI,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = sumiBrushFamily(),
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}

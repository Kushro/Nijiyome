// SPDX-License-Identifier: AGPL-3.0-or-later
// 0.4.1 (subtitle settings). UI patterns adapted from Enigmata (author's own, AGPL-3.0-or-later):
// `ui/player/controls/components/panels/SubtitleSettings{Typography,Colors,Miscellaneous}Card.kt` (typography / colours / misc groups,
// bold-italic-justify toggles, per-group reset, ASS override switch) and `SubtitlesColorPicker` (R / G / B / alpha sliders). Redrawn with
// Nijiyome's design system over `SubtitleStyle` (DataStore) instead of writing mpv properties from the UI.
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.sheets.Caption
import app.nijiyome.feature.player.sheets.ToggleRow
import app.nijiyome.feature.player.sheets.ValueSlider
import app.nijiyome.designsystem.components.SectionCard
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.AssStyleMode
import app.nijiyome.model.SubtitleAlign
import app.nijiyome.model.SubtitlePreset
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.InstalledSubtitleFont
import app.nijiyome.player.ScreenSubtitleSpec
import org.koin.compose.getKoin
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import app.nijiyome.designsystem.theme.NijiyomeControls

// ---- drawing ------------------------------------------------------------------------------------------------------------------------------

/** Compose family of the style: the imported [fontFile] when it loads, else the bundled font of [font]. */
@Composable
internal fun rememberSubtitleFontFamily(font: String, fontFile: File?, fontRef: String? = null): FontFamily = remember(fontFile, font, fontRef) {
    fontFile?.let { f -> runCatching { fontFamilyOf(f) }.getOrNull() }
        // Hito 6: an installed system font (desktop); on Android there is never one
        ?: InstalledSubtitleFont.family(fontRef)?.let(::installedFontFamily)
        ?: when (font) {
            SubtitleStyle.FONT_SYSTEM -> FontFamily.SansSerif
            SubtitleStyle.FONT_SPACE_GROTESK -> NijiyomeFonts.families.display
            else -> NijiyomeFonts.families.text
        }
}

/**
 * One block of subtitle lines drawn by the screen (Media3 cues, translated and secondary lines, previews) with [spec]. The outline is a
 * real stroke under the fill (0.4.0 faked it with a blurred shadow); the edge softness blurs the outline layer, like libass `\blur`
 * (Android 12+, ignored before).
 */
@Composable
internal fun SubtitleLines(lines: List<String>, spec: ScreenSubtitleSpec, family: FontFamily, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val align = when (spec.align) {
        SubtitleAlign.LEFT -> TextAlign.Left
        SubtitleAlign.RIGHT -> TextAlign.Right
        SubtitleAlign.CENTER -> TextAlign.Center
    }
    val strokePx = with(density) { spec.strokeWidthDp.dp.toPx() }
    val shadowPx = with(density) { spec.shadowOffsetDp.dp.toPx() }
    val base = with(density) {
        TextStyle(
            fontFamily = family,
            fontWeight = if (spec.bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = spec.fontSizeDp.dp.toSp(),
            lineHeight = spec.lineHeightDp.dp.toSp(),
            textAlign = align,
        )
    }
    val shadow = if (shadowPx > 0f) Shadow(Color(spec.shadowColor), Offset(shadowPx, shadowPx), blurRadius = shadowPx * 0.5f) else null
    val outlined = strokePx > 0f
    val fill = base.copy(color = Color(spec.textColor), shadow = if (outlined) null else shadow)
    val stroke = base.copy(color = Color(spec.outlineColor), drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round), shadow = shadow)
    val soft = if (spec.blurDp > 0f) Modifier.blur(spec.blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            val box = spec.boxColor?.let { Modifier.background(Color(it), RoundedCornerShape(4.dp)) } ?: Modifier
            Box(box.padding(horizontal = 8.dp, vertical = 2.dp)) {
                if (outlined) Text(line, style = stroke, modifier = soft)
                Text(line, style = fill, modifier = if (outlined) Modifier else soft)
            }
        }
    }
}

/** A stand-in video frame with two sample lines in [style] (Settings and the player's style panels). */
@Composable
fun SubtitleStylePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    val koin = getKoin()
    val tools = remember { koin.subtitleToolsAccess() }
    val fontFile = remember(style.fontFile) { tools?.fontFile(style.fontFile) }
    val family = rememberSubtitleFontFamily(style.font, fontFile, style.fontFile)
    val spec = ScreenSubtitleSpec.of(style, ScreenSubtitleSpec.PREVIEW_HEIGHT_DP)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF8E6E53), Color(0xFF16222A)))),
    ) {
        val lift = (maxHeight - 48.dp) * spec.liftFraction
        SubtitleLines(
            listOf(stringResource(Res.string.player_settings_preview_line), stringResource(Res.string.sub_style_preview_line2)),
            spec,
            family,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp + lift, start = spec.marginXDp.dp, end = spec.marginXDp.dp),
        )
    }
}

// ---- small building blocks ----------------------------------------------------------------------------------------------------------

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

/** Slider with a label; [onLive] on every step (shown at once), [onDone] when the finger lifts (saved). */
@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onLive: (Float) -> Unit,
    onDone: () -> Unit,
    steps: Int = 0,
    enabled: Boolean = true,
    color: Color? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onLive,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = if (color != null) SliderDefaults.colors(thumbColor = color, activeTrackColor = color) else SliderDefaults.colors(),
        )
    }
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

private fun rgbOf(c: Int): Int = c and 0xFFFFFF

/**
 * Colour row: swatches (compared without alpha), an opacity slider when [opacity] is shown, and "Color personalizado" with R / G / B
 * sliders (Enigmata `SubtitlesColorPicker`). [onLive] while dragging, [onDone] to save.
 */
@Composable
private fun ColorPicker(
    title: String,
    color: Int,
    swatches: List<Int>,
    onLive: (Int) -> Unit,
    onDone: () -> Unit,
    onPick: (Int) -> Unit,
    opacity: Boolean = true,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { c ->
                val selected = rgbOf(c) == rgbOf(color)
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(c or (0xFF shl 24)), CircleShape)
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outline), CircleShape)
                        .clickable(enabled = enabled) { onPick((color and (0xFF shl 24)) or rgbOf(c)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        val light = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) > 150
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = if (light) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            FilterChip(selected = custom, onClick = { custom = !custom }, enabled = enabled, label = { Text(stringResource(Res.string.sub_style_custom_color)) }, leadingIcon = {
                Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
            })
        }
        if (custom) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val a = color and (0xFF shl 24)
            StyleSlider(stringResource(Res.string.sub_style_red, r), r.toFloat(), 0f..255f, { onLive(a or (it.roundToInt() shl 16) or (g shl 8) or b) }, onDone, enabled = enabled, color = Color.Red)
            StyleSlider(stringResource(Res.string.sub_style_green, g), g.toFloat(), 0f..255f, { onLive(a or (r shl 16) or (it.roundToInt() shl 8) or b) }, onDone, enabled = enabled, color = Color.Green)
            StyleSlider(stringResource(Res.string.sub_style_blue, b), b.toFloat(), 0f..255f, { onLive(a or (r shl 16) or (g shl 8) or it.roundToInt()) }, onDone, enabled = enabled, color = Color.Blue)
        }
        if (opacity) {
            val alpha = ((color ushr 24) and 0xFF) / 255f
            StyleSlider(
                stringResource(Res.string.sub_style_opacity, (alpha * 100).roundToInt()),
                alpha,
                0f..1f,
                { onLive(SubtitleStyle.withAlpha(color, it)) },
                onDone,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AssModeRow(draft: SubtitleStyle, commit: (SubtitleStyle) -> Unit) {
    Segmented(
        options = listOf(
            SegmentedOption(AssStyleMode.ORIGINAL, stringResource(Res.string.sub_style_ass_original)),
            SegmentedOption(AssStyleMode.MINE, stringResource(Res.string.sub_style_ass_mine)),
            SegmentedOption(AssStyleMode.PLAIN, stringResource(Res.string.sub_style_ass_plain)),
        ),
        selected = draft.assMode,
        onSelect = { commit(draft.copy(assMode = it)) },
        small = true,
        fillWidth = true,
    )
    Note(
        stringResource(
            when (draft.assMode) {
                AssStyleMode.ORIGINAL -> Res.string.sub_style_ass_hint_original
                AssStyleMode.MINE -> Res.string.sub_style_ass_hint_mine
                AssStyleMode.PLAIN -> Res.string.sub_style_ass_hint_plain
            },
        ),
    )
}

private fun Float.oneDecimal(): String = String.format(Locale.getDefault(), "%.1f", this)

// ---- in-player quick panel ----------------------------------------------------------------------------------------------------------

/**
 * Player > Subtitulos > Estilo: the controls people touch most (size, background and its transparency, position, ASS), live on the
 * video while dragging ([onPreview]) and saved when the finger lifts ([onChange]); "Mas opciones" opens [SubtitleStyleEditor].
 * Sparkles redesign (lane S6): the body is [SubtitleQuickStyleBody], shared with the tracks sheet.
 */
@Composable
internal fun SubtitleStyleQuickPanel(
    style: SubtitleStyle,
    onPreview: (SubtitleStyle) -> Unit,
    onChange: (SubtitleStyle) -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
    showAss: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelHeader(stringResource(Res.string.sub_style_title), onClose)
        SubtitleQuickStyleBody(style, onPreview, onChange, onMore, showAss)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The quick subtitle style with its live preview (artifact pattern `sub`): a stand-in frame with two lines in the draft style, size
 * in percent, background box and its transparency, vertical position, how ASS subtitles are drawn, more style options ([onMore])
 * and reset. Every slider step goes to [onPreview] (the video shows it too); a finished change to [onChange] (saved).
 */
@Composable
internal fun SubtitleQuickStyleBody(
    style: SubtitleStyle,
    onPreview: (SubtitleStyle) -> Unit,
    onChange: (SubtitleStyle) -> Unit,
    onMore: () -> Unit,
    showAss: Boolean,
) {
    var draft by remember(style) { mutableStateOf(style) }
    val live: (SubtitleStyle) -> Unit = { draft = it; onPreview(it) }
    val commit: (SubtitleStyle) -> Unit = { draft = it; onChange(it) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleStylePreview(draft)
        val minPct = (SubtitleStyle.MIN_SCALE * 100).roundToInt()
        val maxPct = (SubtitleStyle.MAX_SCALE * 100).roundToInt()
        val sizePct = (draft.scale * 100).roundToInt()
        ValueSlider(
            label = stringResource(Res.string.rd_s6_sub_size),
            value = sizePct,
            range = minPct..maxPct,
            step = 5,
            valueText = stringResource(Res.string.player_fx_percent, sizePct),
            onChange = { live(draft.copy(scale = it / 100f)) },
            onDone = { commit(draft) },
        )
        ToggleRow(
            title = stringResource(Res.string.sub_style_background_on),
            checked = draft.background,
            onChange = { commit(draft.copy(background = it)) },
            hint = stringResource(Res.string.sub_style_background_hint),
        )
        val transparency = ((1f - draft.backgroundOpacity) * 100).roundToInt()
        ValueSlider(
            label = stringResource(Res.string.rd_s6_sub_transparency),
            value = transparency,
            range = 0..100,
            step = 5,
            valueText = stringResource(Res.string.player_fx_percent, transparency),
            onChange = { live(draft.copy(backgroundOpacity = 1f - it / 100f)) },
            onDone = { commit(draft) },
            enabled = draft.background,
        )
        ValueSlider(
            label = stringResource(Res.string.rd_s6_sub_position),
            value = draft.positionPct,
            range = 0..100,
            step = 1,
            valueText = stringResource(Res.string.player_fx_percent, draft.positionPct),
            onChange = { live(draft.copy(positionPct = it)) },
            onDone = { commit(draft) },
        )
        if (showAss) {
            Caption(stringResource(Res.string.sub_style_ass))
            AssModeRow(draft, commit)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onMore, shape = NijiyomeControls.shape) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s6_sub_more_style), Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { commit(SubtitleStyle()) }, shape = NijiyomeControls.shape) {
                Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.sub_style_reset), Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, onClose: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(Res.string.sub_style_close)) }
    }
}

// ---- full editor ----------------------------------------------------------------------------------------------------------------------

/**
 * Every subtitle option (Settings › Reproductor › Estilo de subtítulos and the player's "Más opciones"): quick looks, text (size, font
 * incl. imported ones, bold / italic, alignment, colour + opacity), outline, shadow, background box (colour + transparency), position
 * and spacing (position, margins, line spacing, edge softness), ASS handling, reset. [onPreview] gets every slider step (the player
 * shows it on the video), [onChange] every finished change (saved).
 */
@Composable
fun SubtitleStyleEditor(
    style: SubtitleStyle,
    onChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (SubtitleStyle) -> Unit = {},
    onClose: (() -> Unit)? = null,
    title: String? = null,
    /** Sparkles redesign (lane S6): false inside a sheet that already shows the title (the tracks sheet's style step). */
    showHeader: Boolean = true,
    /** The side padding of the editor; 0 when the container already pads it. */
    horizontalPadding: Dp = 16.dp,
) {
    var draft by remember(style) { mutableStateOf(style) }
    val live: (SubtitleStyle) -> Unit = { draft = it; onPreview(it) }
    val commit: (SubtitleStyle) -> Unit = { draft = it; onChange(it) }
    val done: () -> Unit = { commit(draft) }
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeader) PanelHeader(title ?: stringResource(Res.string.sub_style_title), onClose)
        SubtitleStylePreview(draft)
        Note(stringResource(Res.string.sub_style_engines))

        // Quick looks
        SectionCard(title = stringResource(Res.string.sub_style_presets)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitlePreset.entries.forEach { p ->
                    val label = when (p) {
                        SubtitlePreset.DEFAULT -> Res.string.sub_style_preset_default
                        SubtitlePreset.OUTLINE -> Res.string.sub_style_preset_outline
                        SubtitlePreset.BOX -> Res.string.sub_style_preset_box
                        SubtitlePreset.YELLOW -> Res.string.sub_style_preset_yellow
                    }
                    FilterChip(selected = draft.withPreset(p) == draft, onClick = { commit(draft.withPreset(p)) }, label = { Text(stringResource(label)) })
                }
            }
        }

        // Text
        SectionCard(title = stringResource(Res.string.sub_style_text)) {
            StyleSlider(
                stringResource(Res.string.sub_style_size, (draft.scale * 100).roundToInt()),
                draft.scale,
                SubtitleStyle.MIN_SCALE..SubtitleStyle.MAX_SCALE,
                { live(draft.copy(scale = (it * 20).roundToInt() / 20f)) },
                done,
            )
            FontPicker(draft, commit)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.bold, onClick = { commit(draft.copy(bold = !draft.bold)) }, label = { Text(stringResource(Res.string.sub_style_bold)) })
                FilterChip(selected = draft.italic, onClick = { commit(draft.copy(italic = !draft.italic)) }, label = { Text(stringResource(Res.string.sub_style_italic)) })
            }
            Text(stringResource(Res.string.sub_style_align), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Segmented(
                options = listOf(
                    SegmentedOption(SubtitleAlign.LEFT, stringResource(Res.string.sub_style_align_left)),
                    SegmentedOption(SubtitleAlign.CENTER, stringResource(Res.string.sub_style_align_center)),
                    SegmentedOption(SubtitleAlign.RIGHT, stringResource(Res.string.sub_style_align_right)),
                ),
                selected = draft.align,
                onSelect = { commit(draft.copy(align = it)) },
                small = true,
                fillWidth = true,
            )
            Spacer(Modifier.height(8.dp))
            ColorPicker(
                stringResource(Res.string.sub_style_text_color),
                draft.textColor,
                SubtitleStyle.TEXT_COLORS,
                onLive = { live(draft.copy(textColor = it)) },
                onDone = done,
                onPick = { commit(draft.copy(textColor = it)) },
            )
        }

        // Outline
        SectionCard(title = stringResource(Res.string.sub_style_outline)) {
            SwitchLine(stringResource(Res.string.sub_style_outline_on), draft.outline, { commit(draft.copy(outline = it)) })
            StyleSlider(
                stringResource(Res.string.sub_style_outline_size, draft.outlineSize.oneDecimal()),
                draft.outlineSize,
                0f..SubtitleStyle.MAX_OUTLINE,
                { live(draft.copy(outlineSize = (it * 4).roundToInt() / 4f)) },
                done,
                enabled = draft.outline,
            )
            ColorPicker(
                stringResource(Res.string.sub_style_outline_color),
                draft.outlineColor,
                SubtitleStyle.OUTLINE_COLORS,
                onLive = { live(draft.copy(outlineColor = it)) },
                onDone = done,
                onPick = { commit(draft.copy(outlineColor = it)) },
                enabled = draft.outline,
            )
        }

        // Background box
        SectionCard(title = stringResource(Res.string.sub_style_background)) {
            SwitchLine(stringResource(Res.string.sub_style_background_on), draft.background, { commit(draft.copy(background = it)) }, stringResource(Res.string.sub_style_background_hint))
            StyleSlider(
                stringResource(Res.string.sub_style_transparency, ((1f - draft.backgroundOpacity) * 100).roundToInt()),
                1f - draft.backgroundOpacity,
                0f..1f,
                { live(draft.copy(backgroundOpacity = 1f - it)) },
                done,
                enabled = draft.background,
            )
            ColorPicker(
                stringResource(Res.string.sub_style_background_color),
                SubtitleStyle.withAlpha(draft.backgroundColor, 1f),
                SubtitleStyle.BACKGROUND_COLORS,
                onLive = { live(draft.copy(backgroundColor = it)) },
                onDone = done,
                onPick = { commit(draft.copy(backgroundColor = it)) },
                opacity = false,
                enabled = draft.background,
            )
        }

        // Shadow
        SectionCard(title = stringResource(Res.string.sub_style_shadow)) {
            if (draft.background) Note(stringResource(Res.string.sub_style_shadow_box_hint))
            StyleSlider(
                stringResource(Res.string.sub_style_shadow_offset, draft.shadowOffset.oneDecimal()),
                draft.shadowOffset,
                0f..SubtitleStyle.MAX_SHADOW,
                { live(draft.copy(shadowOffset = (it * 4).roundToInt() / 4f)) },
                done,
                enabled = !draft.background,
            )
            ColorPicker(
                stringResource(Res.string.sub_style_shadow_color),
                draft.shadowColor,
                SubtitleStyle.OUTLINE_COLORS,
                onLive = { live(draft.copy(shadowColor = it)) },
                onDone = done,
                onPick = { commit(draft.copy(shadowColor = it)) },
                enabled = !draft.background,
            )
        }

        // Position and spacing
        SectionCard(title = stringResource(Res.string.sub_style_layout)) {
            StyleSlider(
                stringResource(Res.string.sub_style_position, draft.positionPct),
                draft.positionPct.toFloat(),
                0f..100f,
                { live(draft.copy(positionPct = it.roundToInt())) },
                done,
            )
            Note(stringResource(Res.string.player_settings_position_hint))
            StyleSlider(
                stringResource(Res.string.sub_style_margin_y, draft.marginY),
                draft.marginY.toFloat(),
                0f..SubtitleStyle.MAX_MARGIN.toFloat(),
                { live(draft.copy(marginY = it.roundToInt())) },
                done,
            )
            StyleSlider(
                stringResource(Res.string.sub_style_margin_x, draft.marginX),
                draft.marginX.toFloat(),
                0f..SubtitleStyle.MAX_MARGIN.toFloat(),
                { live(draft.copy(marginX = it.roundToInt())) },
                done,
            )
            StyleSlider(
                stringResource(Res.string.sub_style_line_spacing, draft.lineSpacing.oneDecimal()),
                draft.lineSpacing,
                0f..SubtitleStyle.MAX_LINE_SPACING,
                { live(draft.copy(lineSpacing = (it * 2).roundToInt() / 2f)) },
                done,
            )
            StyleSlider(
                stringResource(Res.string.sub_style_blur, draft.blur.oneDecimal()),
                draft.blur,
                0f..SubtitleStyle.MAX_BLUR,
                { live(draft.copy(blur = (it * 4).roundToInt() / 4f)) },
                done,
            )
        }

        // ASS
        SectionCard(title = stringResource(Res.string.sub_style_ass)) {
            AssModeRow(draft, commit)
            if (draft.assMode == AssStyleMode.ORIGINAL) {
                SwitchLine(stringResource(Res.string.sub_style_ass_scale), draft.assScale, { commit(draft.copy(assScale = it)) }, stringResource(Res.string.sub_style_ass_scale_hint))
            }
            Note(stringResource(Res.string.sub_style_ass_media3))
        }

        TextButton(onClick = { commit(SubtitleStyle()) }, shape = NijiyomeControls.shape) {
            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.sub_style_reset_all), Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Settings › Reproductor › Estilo de subtítulos: [SubtitleStyleEditor] in a side sheet (wide) or a scrolling bottom sheet. */
@Composable
fun SubtitleStyleSheet(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit, onDismiss: () -> Unit) {
    EnhancementSheet(onDismiss = onDismiss) {
        SubtitleStyleEditor(style = style, onChange = onChange, onClose = onDismiss)
    }
}

/** Bundled fonts as chips, the imported ones (choose / delete) and "Importar fuentes…" (TTF / OTF into `filesDir/fonts`). */
@Composable
private fun FontPicker(draft: SubtitleStyle, commit: (SubtitleStyle) -> Unit) {
    val koin = getKoin()
    val tools = remember { koin.subtitleToolsAccess() }
    var fonts by remember { mutableStateOf(tools?.fonts().orEmpty()) }
    var fontError by remember { mutableStateOf(false) }
    val importFonts = rememberSubtitleFontImporter { failed ->
        val t = tools ?: return@rememberSubtitleFontImporter
        fontError = failed
        fonts = t.fonts()
    }
    Text(stringResource(Res.string.sub_style_font), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleStyle.FONTS.forEach { id ->
            val label = when (id) {
                SubtitleStyle.FONT_SPACE_GROTESK -> Res.string.sub_style_font_space_grotesk
                SubtitleStyle.FONT_SYSTEM -> Res.string.sub_style_font_system
                else -> Res.string.sub_style_font_outfit
            }
            FilterChip(
                selected = draft.fontFile == null && draft.font == id,
                onClick = { commit(draft.copy(font = id, fontFile = null)) },
                label = { Text(stringResource(label)) },
            )
        }
        fonts.forEach { f ->
            InputChip(
                selected = draft.fontFile == f.name,
                onClick = { commit(draft.copy(fontFile = f.name)) },
                label = { Text(f.nameWithoutExtension) },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        stringResource(Res.string.player_settings_fonts_delete),
                        modifier = Modifier.size(18.dp).clickable {
                            tools?.deleteFont(f.name)
                            if (draft.fontFile == f.name) commit(draft.copy(fontFile = null))
                            fonts = tools?.fonts().orEmpty()
                        },
                    )
                },
            )
        }
        // Hito 6: a font installed on the system (desktop; Android offers none, so nothing changes there)
        val installed = remember(tools) { tools?.installedFamilies().orEmpty() }
        if (installed.isNotEmpty()) {
            InstalledFontChooser(installed, selected = InstalledSubtitleFont.family(draft.fontFile)) { family ->
                commit(draft.copy(fontFile = InstalledSubtitleFont.ref(family)))
            }
        }
    }
    if (tools != null) {
        TextButton(onClick = importFonts, shape = NijiyomeControls.shape) {
            Text(stringResource(Res.string.player_settings_fonts_import))
        }
        Note(stringResource(Res.string.player_settings_fonts_hint) + " " + stringResource(Res.string.sub_style_fonts_mpv_note))
        if (fontError) Text(stringResource(Res.string.player_settings_fonts_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

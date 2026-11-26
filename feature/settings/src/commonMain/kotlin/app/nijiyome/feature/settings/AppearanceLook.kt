// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.model.CardSize
import app.nijiyome.model.Density
import app.nijiyome.model.EpisodeView
import app.nijiyome.model.HeaderLayout
import app.nijiyome.model.LookPrefs
import app.nijiyome.model.LookPreset
import app.nijiyome.model.Motion
import app.nijiyome.model.PageStyle
import app.nijiyome.model.RailStyle
import app.nijiyome.model.RelationsView
import kotlin.math.roundToInt

// Settings › Apariencia: the Diseño y estilo options (LookPrefs), each beside the screen it affects since the Sparkles redesign. A
// preset applies every option at once; changing one afterwards shows "Personalizado". Superseded by the screen structures: the Home
// layout (Inicio structure), the calendar default view (Calendario structure) and the card style (the Diseño card picker).
//
// Android adaptation: "Menú lateral" only affects the navigation rail of tablets / wide windows (phones keep the bottom bar, labelled);
// "Efectos de desenfoque" (glass) is not offered: a real-time blur is too heavy for low-end phones, so the field is kept only so the
// presets stay identical to the desktop ones.

/** "Estilo general": the four presets as cards and whether the options still match one ("Personalizado" otherwise). */
@Composable
internal fun LookPresetRow(look: LookPrefs, onPreset: (LookPreset) -> Unit) {
    val matching = look.matchingPreset()
    val presetRow = rememberLazyListState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.settings_look_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            state = presetRow,
            modifier = Modifier.fillMaxWidth().horizontalFadingEdge(presetRow).selectableGroup(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LookPreset.entries, key = { it.id }, contentType = { "preset" }) { preset ->
                PresetCard(preset, selected = preset == matching, onClick = { onPreset(preset) })
            }
        }
        Text(
            if (matching == null) stringResource(Res.string.settings_look_custom) else stringResource(Res.string.settings_look_preset_on, stringResource(matching.labelRes())),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Inicio beside its structure: the banner rotation and its period, and the greeting. */
@Composable
internal fun HomeLookRows(look: LookPrefs, onChange: ((LookPrefs) -> LookPrefs) -> Unit) {
    SwitchRow(
        stringResource(Res.string.settings_look_autoplay),
        look.heroAutoplay,
        { on -> onChange { it.copy(heroAutoplay = on) } },
        hint = stringResource(Res.string.settings_look_autoplay_hint),
    )
    if (look.heroAutoplay) IntervalRow(look.heroIntervalSecs) { secs -> onChange { it.copy(heroIntervalSecs = secs) } }
    SwitchRow(stringResource(Res.string.settings_look_greeting), look.showGreeting, { on -> onChange { it.copy(showGreeting = on) } })
}

/** La ficha beside its structure: page style, header composition, episodes and relations views. */
@Composable
internal fun DetailsLookRows(look: LookPrefs, onChange: ((LookPrefs) -> LookPrefs) -> Unit) {
    LookSegmented(
        Res.string.settings_look_style,
        PageStyle.entries,
        look.pageStyle,
        { it.labelRes() },
        hint = stringResource(Res.string.settings_look_style_hint),
    ) { v -> onChange { it.copy(pageStyle = v) } }
    SettingRow(stringResource(Res.string.settings_look_header), hint = stringResource(Res.string.settings_look_header_hint), stack = true) {
        // 2 x 2, not a sliding row: the default (D) is never off screen, and the cards grow with the font instead of cutting the hint
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderLayout.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { layout ->
                        HeaderLayoutCard(
                            layout,
                            selected = layout == look.headerLayout,
                            onClick = { onChange { it.copy(headerLayout = layout) } },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
    LookSegmented(Res.string.settings_look_episodes, EpisodeView.entries, look.episodeView, { it.labelRes() }) { v -> onChange { it.copy(episodeView = v) } }
    LookSegmented(Res.string.settings_look_relations, RelationsView.entries, look.relationsView, { it.labelRes() }) { v -> onChange { it.copy(relationsView = v) } }
}

/** Card size and density (the card style itself is the Diseño picker). */
@Composable
internal fun CardLookRows(look: LookPrefs, onChange: ((LookPrefs) -> LookPrefs) -> Unit) {
    LookSegmented(
        Res.string.settings_look_card_size,
        CardSize.entries,
        look.cardSize,
        { it.labelRes() },
        hint = stringResource(Res.string.settings_look_card_size_hint),
        small = true,
    ) { v -> onChange { it.copy(cardSize = v) } }
    LookSegmented(Res.string.settings_look_density, Density.entries, look.density, { it.labelRes() }) { v -> onChange { it.copy(density = v) } }
}

/** Animaciones (expressive / subtle / off; off is Reducir movimiento). */
@Composable
internal fun MotionLookRow(look: LookPrefs, onChange: ((LookPrefs) -> LookPrefs) -> Unit) {
    LookSegmented(
        Res.string.settings_look_motion,
        Motion.entries,
        look.motion,
        { it.labelRes() },
        hint = stringResource(Res.string.settings_look_motion_hint),
    ) { v -> onChange { it.copy(motion = v) } }
}

/** Menú lateral (labelled or icons only): wide windows only. */
@Composable
internal fun RailStyleRow(look: LookPrefs, onChange: ((LookPrefs) -> LookPrefs) -> Unit) {
    LookSegmented(
        Res.string.settings_look_rail,
        RailStyle.entries,
        look.railStyle,
        { it.labelRes() },
        hint = stringResource(Res.string.settings_look_rail_hint),
    ) { v -> onChange { it.copy(railStyle = v) } }
}

@Composable
private fun <T> LookSegmented(
    labelRes: StringResource,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> StringResource,
    hint: String? = null,
    small: Boolean = false,
    onSelect: (T) -> Unit,
) {
    // Up to four short options sit beside the label on wide rows (artifact density); more go under it.
    SettingRow(stringResource(labelRes), hint = hint, stack = true, inline = options.size <= 4) {
        Segmented(
            options = options.map { SegmentedOption(it, stringResource(optionLabel(it))) },
            selected = selected,
            onSelect = onSelect,
            fillWidth = true,
            small = small,
        )
    }
}

@Composable
private fun IntervalRow(secs: Int, onChange: (Int) -> Unit) {
    // Local while dragging, saved once on release (no DataStore write per frame).
    var drag by remember(secs) { mutableFloatStateOf(secs.toFloat()) }
    val shown = drag.roundToInt()
    SettingRow(stringResource(Res.string.settings_look_interval), hint = stringResource(Res.string.settings_look_interval_value, shown), stack = true) {
        Slider(
            value = drag,
            onValueChange = { drag = it },
            onValueChangeFinished = { onChange(drag.roundToInt()) },
            valueRange = LookPrefs.HERO_INTERVAL_MIN.toFloat()..LookPrefs.HERO_INTERVAL_MAX.toFloat(),
            steps = LookPrefs.HERO_INTERVAL_MAX - LookPrefs.HERO_INTERVAL_MIN - 1,
            modifier = Modifier.semantics { stateDescription = "$shown s" },
        )
    }
}

@Composable
private fun PresetCard(preset: LookPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.width(156.dp).semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(preset.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(preset.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                minLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun LookPreset.labelRes(): StringResource = when (this) {
    LookPreset.NIJIYOME -> Res.string.settings_look_preset_nijiyome
    LookPreset.CINEMA -> Res.string.settings_look_preset_cinema
    LookPreset.DENSE -> Res.string.settings_look_preset_dense
    LookPreset.CALM -> Res.string.settings_look_preset_calm
}

private fun LookPreset.hintRes(): StringResource = when (this) {
    LookPreset.NIJIYOME -> Res.string.settings_look_preset_nijiyome_hint
    LookPreset.CINEMA -> Res.string.settings_look_preset_cinema_hint
    LookPreset.DENSE -> Res.string.settings_look_preset_dense_hint
    LookPreset.CALM -> Res.string.settings_look_preset_calm_hint
}

/** One composition of the Details header: a drawn thumbnail, its name and a two-line hint (radio card, like [PresetCard]). */
@Composable
private fun HeaderLayoutCard(layout: HeaderLayout, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderLayoutThumb(layout)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(layout.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(layout.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Schematic of [layout] (124:76, as wide as its card): the image band, then a line (A, C) or 3 + 2 fact cells (B, D), the main pill loose (A, B) or
 * inside an outlined card (C, D), and icon dots (A, C) or named tags (B, D). Decorative: the card's text says it.
 */
@Composable
private fun HeaderLayoutThumb(layout: HeaderLayout) {
    val scheme = MaterialTheme.colorScheme
    val band = scheme.surfaceContainerHighest
    val ink = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val faint = scheme.outlineVariant
    val primary = scheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(124f / 76f)
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surface)
            .clearAndSetSemantics {},
    ) {
        // drawn in 76 units of height, so the schematic scales with the card
        val u = size.height / 76f
        val pad = 6 * u
        val w = size.width - 2 * pad
        drawRect(band, size = Size(size.width, 22 * u))
        var y = 26 * u
        fun bar(x: Float, top: Float, width: Float, height: Float, color: Color) =
            drawRoundRect(color, Offset(x, top), Size(width, height), CornerRadius(height / 2))
        fun line() {
            bar(pad, y, w * 0.62f, 3 * u, ink)
            y += 7 * u
        }
        fun grid() {
            val cell = w / 3
            for (i in 0 until 5) {
                val col = i % 3
                val row = i / 3
                bar(pad + col * cell, y + row * 7 * u, cell - 4 * u, 4 * u, ink)
            }
            y += 15 * u
        }
        fun pill(left: Float, width: Float) {
            bar(left, y, width, 7 * u, primary)
            y += 11 * u
        }
        fun dots() {
            for (i in 0 until 5) drawCircle(ink, 2.2f * u, Offset(pad + 3 * u + i * (w - 6 * u) / 4, y + 2 * u))
            y += 8 * u
        }
        fun tags() {
            val tag = 18 * u
            for (i in 0 until 4) {
                drawRoundRect(faint, Offset(pad + i * (tag + 4 * u), y), Size(tag, 6 * u), CornerRadius(3 * u), style = Stroke(u))
            }
            y += 10 * u
        }
        fun card() {
            val h = 18 * u
            drawRoundRect(faint, Offset(pad, y), Size(w, h), CornerRadius(4 * u), style = Stroke(u))
            bar(pad + 4 * u, y + 3 * u, w * 0.4f, 3 * u, ink)
            bar(pad + 4 * u, y + 9 * u, w - 8 * u, 6 * u, primary)
            y += h + 4 * u
        }
        when (layout) {
            HeaderLayout.LINE -> { line(); pill(pad, w); dots() }
            HeaderLayout.FACTS -> { grid(); pill(pad, w); tags() }
            HeaderLayout.NEXT_UP -> { line(); dots(); card() }
            HeaderLayout.FACTS_NEXT_UP -> { grid(); tags(); card() }
        }
    }
}

private fun HeaderLayout.labelRes(): StringResource = when (this) {
    HeaderLayout.LINE -> Res.string.settings_look_header_line
    HeaderLayout.FACTS -> Res.string.settings_look_header_facts
    HeaderLayout.NEXT_UP -> Res.string.settings_look_header_nextup
    HeaderLayout.FACTS_NEXT_UP -> Res.string.settings_look_header_facts_nextup
}

private fun HeaderLayout.hintRes(): StringResource = when (this) {
    HeaderLayout.LINE -> Res.string.settings_look_header_line_hint
    HeaderLayout.FACTS -> Res.string.settings_look_header_facts_hint
    HeaderLayout.NEXT_UP -> Res.string.settings_look_header_nextup_hint
    HeaderLayout.FACTS_NEXT_UP -> Res.string.settings_look_header_facts_nextup_hint
}

private fun PageStyle.labelRes(): StringResource = when (this) {
    PageStyle.STANDARD -> Res.string.settings_look_style_standard
    PageStyle.EDITORIAL -> Res.string.settings_look_style_editorial
}



private fun CardSize.labelRes(): StringResource = when (this) {
    CardSize.SMALL -> Res.string.settings_look_card_size_small
    CardSize.MEDIUM -> Res.string.settings_look_card_size_medium
    CardSize.LARGE -> Res.string.settings_look_card_size_large
    CardSize.XL -> Res.string.settings_look_card_size_xl
}

private fun Density.labelRes(): StringResource = when (this) {
    Density.COMFORTABLE -> Res.string.settings_look_density_comfortable
    Density.COMPACT -> Res.string.settings_look_density_compact
}

private fun Motion.labelRes(): StringResource = when (this) {
    Motion.EXPRESSIVE -> Res.string.settings_look_motion_expressive
    Motion.SUBTLE -> Res.string.settings_look_motion_subtle
    Motion.OFF -> Res.string.settings_look_motion_off
}

private fun EpisodeView.labelRes(): StringResource = when (this) {
    EpisodeView.CARDS -> Res.string.settings_look_episodes_cards
    EpisodeView.LIST -> Res.string.settings_look_episodes_list
    EpisodeView.COMPACT -> Res.string.settings_look_episodes_compact
}

private fun RelationsView.labelRes(): StringResource = when (this) {
    RelationsView.GRAPH -> Res.string.settings_look_relations_graph
    RelationsView.LIST -> Res.string.settings_look_relations_list
}


private fun RailStyle.labelRes(): StringResource = when (this) {
    RailStyle.LABELED -> Res.string.settings_look_rail_labeled
    RailStyle.SLIM -> Res.string.settings_look_rail_slim
}

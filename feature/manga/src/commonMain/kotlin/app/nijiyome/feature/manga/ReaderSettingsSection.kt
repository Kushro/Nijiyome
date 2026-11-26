// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.manga.ReaderKeymap
import app.nijiyome.model.AppSettings
import app.nijiyome.model.ReaderDirection
import app.nijiyome.model.ReaderFit
import app.nijiyome.model.ReaderMode
import app.nijiyome.model.ReaderSettings
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.resources.ui_cancel
import kotlin.math.roundToInt
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * Settings › Lector (`SettingsSection.READER`; MNG-20 / MNG-21): every `ReaderSettings` field + `AppSettings.mangaTitleLanguage` +
 * the keymap editor, as grouped choices (tiles for mode, direction and fit, swatches for the background). The same groups open
 * inside the reader as its quick sheet, there limited to what applies to the current mode. Lane S10 embeds this section.
 */
@Composable
fun ReaderSettingsSection(settings: AppSettings, onUpdate: (transform: (AppSettings) -> AppSettings) -> Unit, modifier: Modifier = Modifier) {
    val reader = settings.reader.sanitized()
    val update: ((ReaderSettings) -> ReaderSettings) -> Unit = { t -> onUpdate { it.copy(reader = t(it.reader).sanitized()) } }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsTitle(stringResource(Res.string.manga_settings_section))
        SettingsGroup(stringResource(Res.string.manga_settings_title_language), stringResource(Res.string.manga_settings_title_language_hint)) {
            Segmented(
                options = listOf(
                    SegmentedOption(TitleLanguage.ROMAJI, stringResource(Res.string.manga_title_romaji)),
                    SegmentedOption(TitleLanguage.ENGLISH, stringResource(Res.string.manga_title_english)),
                    SegmentedOption(TitleLanguage.NATIVE, stringResource(Res.string.manga_title_native)),
                ),
                selected = settings.mangaTitleLanguage,
                onSelect = { v -> onUpdate { it.copy(mangaTitleLanguage = v) } },
                small = true,
            )
        }
        ReaderSettingsGroups(reader, update, modeOnly = false)
        SettingsTitle(stringResource(Res.string.manga_settings_shortcuts))
        ReaderShortcutRows(reader, update)
    }
}

@Composable
internal fun SettingsTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A titled group of reader settings (artifact `nj-c-sub`). */
@Composable
internal fun SettingsGroup(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        content()
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One choice of a tile group: value, icon, label. */
internal class Choice<T>(val value: T, val icon: ImageVector?, val label: String)

/** A radio group of icon tiles (mode, direction, fit); each tile is at least 64 dp tall. */
@Composable
internal fun <T> ChoiceTiles(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            val on = c.value == selected
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).selectable(selected = on, role = Role.RadioButton) { onSelect(c.value) },
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    c.icon?.let { Icon(it, contentDescription = null) }
                    Text(c.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

/** Colour swatches (reader background): each shows its colour beside its name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Swatches(choices: List<Pair<String, String>>, colorOf: @Composable (String) -> Color, selected: String, onSelect: (String) -> Unit) {
    // they wrap instead of running past the sheet's edge ("Te…")
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, label) ->
            val on = id == selected
            val swatch = colorOf(id)
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 48.dp).selectable(selected = on, role = Role.RadioButton) { onSelect(id) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).background(swatch, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A slider with its current value beside the title; the value is saved when the drag ends. */
@Composable
internal fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: @Composable (Float) -> String, onDone: (Float) -> Unit) {
    var drag by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label(drag), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = drag, onValueChange = { drag = it }, onValueChangeFinished = { onDone(drag) }, valueRange = range, steps = steps)
    }
}

/** The reader background as a colour (also the swatches). */
@Composable
internal fun readerBackground(id: String): Color = when (id) {
    "white" -> Color.White
    "gray" -> Color(0xFF424242)
    "theme" -> MaterialTheme.colorScheme.background
    else -> Color.Black
}

/**
 * The reader settings as groups (MNG-15 / MNG-20). [modeOnly] (the in-reader sheet) shows only what applies to the current mode;
 * [onShowZones] adds "Ver zonas en la página" in paged mode.
 */
@Composable
internal fun ReaderSettingsGroups(
    reader: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    modeOnly: Boolean,
    onShowZones: (() -> Unit)? = null,
    zonesShown: Boolean = false,
) {
    val paged = reader.mode == ReaderMode.PAGED
    val webtoon = reader.mode == ReaderMode.WEBTOON
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsGroup(
            stringResource(Res.string.manga_settings_mode),
            stringResource(
                when (reader.mode) {
                    ReaderMode.PAGED -> Res.string.rd_s11_mode_paged_hint
                    ReaderMode.VERTICAL -> Res.string.rd_s11_mode_vertical_hint
                    ReaderMode.WEBTOON -> Res.string.rd_s11_mode_webtoon_hint
                },
            ),
        ) {
            ChoiceTiles(
                listOf(
                    Choice(ReaderMode.PAGED, Icons.Outlined.AutoStories, stringResource(Res.string.rd_s11_mode_paged)),
                    Choice(ReaderMode.VERTICAL, Icons.Outlined.ViewDay, stringResource(Res.string.manga_mode_vertical)),
                    Choice(ReaderMode.WEBTOON, Icons.Outlined.ViewStream, stringResource(Res.string.manga_mode_webtoon)),
                ),
                reader.mode,
                { v -> onUpdate { it.copy(mode = v) } },
            )
        }
        if (paged || !modeOnly) {
            SettingsGroup(stringResource(Res.string.manga_settings_direction)) {
                ChoiceTiles(
                    listOf(
                        Choice(ReaderDirection.RTL, Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.manga_direction_rtl)),
                        Choice(ReaderDirection.LTR, Icons.AutoMirrored.Outlined.ArrowForward, stringResource(Res.string.manga_direction_ltr)),
                    ),
                    reader.direction,
                    { v -> onUpdate { it.copy(direction = v) } },
                )
                SwitchRow(stringResource(Res.string.manga_settings_double_page), stringResource(Res.string.manga_settings_double_page_hint), reader.doublePage) { v -> onUpdate { it.copy(doublePage = v) } }
            }
        }
        if (!webtoon || !modeOnly) {
            SettingsGroup(stringResource(Res.string.manga_settings_fit)) {
                ChoiceTiles(
                    listOf(
                        Choice(ReaderFit.WIDTH, Icons.Outlined.SwapHoriz, stringResource(Res.string.manga_fit_width)),
                        Choice(ReaderFit.HEIGHT, Icons.Outlined.SwapVert, stringResource(Res.string.manga_fit_height)),
                        Choice(ReaderFit.BOTH, Icons.Outlined.FitScreen, stringResource(Res.string.manga_fit_both)),
                        Choice(ReaderFit.NATURAL, Icons.Outlined.Photo, stringResource(Res.string.manga_fit_natural)),
                    ),
                    reader.fit,
                    { v -> onUpdate { it.copy(fit = v) } },
                )
                SliderRow(
                    stringResource(Res.string.manga_settings_max_width),
                    reader.maxWidthDp.toFloat(),
                    0f..2000f,
                    39,
                    label = { v -> if (v.roundToInt() == 0) stringResource(Res.string.manga_settings_no_limit) else stringResource(Res.string.manga_settings_dp, v.roundToInt()) },
                ) { v -> onUpdate { it.copy(maxWidthDp = (v.roundToInt() / 50) * 50) } }
                if (reader.mode == ReaderMode.VERTICAL || !modeOnly) {
                    SliderRow(stringResource(Res.string.manga_settings_gap), reader.pageGapDp.toFloat(), 0f..64f, 15, label = { v -> stringResource(Res.string.manga_settings_dp, v.roundToInt()) }) { v ->
                        onUpdate { it.copy(pageGapDp = v.roundToInt()) }
                    }
                }
            }
        }
        SettingsGroup(stringResource(Res.string.rd_s11_image)) {
            SwitchRow(stringResource(Res.string.manga_settings_crop), stringResource(Res.string.manga_settings_crop_hint), reader.cropBorders) { v -> onUpdate { it.copy(cropBorders = v) } }
            Text(stringResource(Res.string.manga_settings_background), style = MaterialTheme.typography.bodyLarge)
            Swatches(
                listOf(
                    "black" to stringResource(Res.string.manga_bg_black),
                    "gray" to stringResource(Res.string.manga_bg_gray),
                    "white" to stringResource(Res.string.manga_bg_white),
                    "theme" to stringResource(Res.string.manga_bg_theme),
                ),
                colorOf = { readerBackground(it) },
                selected = reader.background,
                onSelect = { v -> onUpdate { it.copy(background = v) } },
            )
            SwitchRow(stringResource(Res.string.manga_settings_brightness_system), null, reader.brightness < 0f) { v -> onUpdate { it.copy(brightness = if (v) -1f else 0.6f) } }
            if (reader.brightness >= 0f) {
                SliderRow(stringResource(Res.string.manga_settings_brightness), reader.brightness, 0.05f..1f, 18, label = { v -> "${(v * 100).roundToInt()} %" }) { v ->
                    onUpdate { it.copy(brightness = v) }
                }
            }
        }
        if (paged && onShowZones != null) {
            SettingsGroup(stringResource(Res.string.rd_s11_tap_zones), stringResource(Res.string.rd_s11_tap_zones_hint)) {
                TextButton(onClick = onShowZones, shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.TouchApp, contentDescription = null)
                    Text(stringResource(if (zonesShown) Res.string.rd_s11_zones_hide else Res.string.rd_s11_zones_show), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (!paged || !modeOnly) {
            SettingsGroup(stringResource(Res.string.rd_s11_scrolling)) {
                SwitchRow(stringResource(Res.string.manga_settings_infinite), stringResource(Res.string.manga_settings_infinite_hint), reader.infiniteScroll) { v -> onUpdate { it.copy(infiniteScroll = v) } }
                SliderRow(
                    stringResource(Res.string.manga_settings_auto_scroll),
                    reader.autoScrollSpeed.toFloat(),
                    0f..600f,
                    29,
                    label = { v -> if (v.roundToInt() == 0) stringResource(Res.string.manga_settings_off) else stringResource(Res.string.manga_settings_px_s, v.roundToInt()) },
                ) { v -> onUpdate { it.copy(autoScrollSpeed = (v.roundToInt() / 20) * 20) } }
            }
        }
        SettingsGroup(stringResource(Res.string.manga_settings_loading)) {
            SwitchRow(stringResource(Res.string.manga_settings_eager), stringResource(Res.string.manga_settings_eager_hint), reader.eagerLoading) { v -> onUpdate { it.copy(eagerLoading = v) } }
            if (!reader.eagerLoading) {
                SliderRow(stringResource(Res.string.manga_settings_prefetch), reader.prefetchPages.toFloat(), 1f..20f, 18, label = { v -> stringResource(Res.string.manga_settings_pages, v.roundToInt()) }) { v ->
                    onUpdate { it.copy(prefetchPages = v.roundToInt()) }
                }
            }
        }
        SettingsGroup(stringResource(Res.string.manga_settings_behavior)) {
            SwitchRow(stringResource(Res.string.manga_settings_fullscreen), null, reader.fullscreenOnOpen) { v -> onUpdate { it.copy(fullscreenOnOpen = v) } }
            SwitchRow(stringResource(Res.string.manga_settings_hide_chrome), stringResource(Res.string.manga_settings_hide_chrome_hint), reader.hideChromeOnTap) { v -> onUpdate { it.copy(hideChromeOnTap = v) } }
            SwitchRow(stringResource(Res.string.manga_settings_progress), null, reader.showProgress) { v -> onUpdate { it.copy(showProgress = v) } }
            SwitchRow(stringResource(Res.string.manga_settings_zoom_keys), stringResource(Res.string.manga_settings_zoom_keys_hint), reader.zoomShortcuts) { v -> onUpdate { it.copy(zoomShortcuts = v) } }
        }
    }
}

@Composable
private fun actionLabel(action: String): String = stringResource(
    when (action) {
        ReaderKeymap.NEXT_PAGE -> Res.string.manga_key_next_page
        ReaderKeymap.PREV_PAGE -> Res.string.manga_key_prev_page
        ReaderKeymap.SCROLL_DOWN -> Res.string.manga_key_scroll_down
        ReaderKeymap.SCROLL_UP -> Res.string.manga_key_scroll_up
        ReaderKeymap.NEXT_CHAPTER -> Res.string.manga_key_next_chapter
        ReaderKeymap.PREV_CHAPTER -> Res.string.manga_key_prev_chapter
        ReaderKeymap.TOGGLE_FULLSCREEN -> Res.string.manga_key_fullscreen
        ReaderKeymap.TOGGLE_MODE -> Res.string.manga_key_mode
        ReaderKeymap.ZOOM_IN -> Res.string.manga_key_zoom_in
        ReaderKeymap.ZOOM_OUT -> Res.string.manga_key_zoom_out
        ReaderKeymap.TOGGLE_CHROME -> Res.string.manga_key_chrome
        else -> Res.string.manga_key_reload
    },
)

@Composable
private fun keyLabel(key: String): String = when (key) {
    "SPACE" -> stringResource(Res.string.manga_key_space)
    "DPAD_RIGHT" -> "→"
    "DPAD_LEFT" -> "←"
    "DPAD_UP" -> "↑"
    "DPAD_DOWN" -> "↓"
    "EQUALS" -> "="
    "MINUS" -> "−"
    else -> key.replace('_', ' ')
}

/** Shortcut editor (MNG-20 / MNG-21): tap an action, press a key; conflicts are marked; "Restablecer atajos". */
@Composable
private fun ReaderShortcutRows(reader: ReaderSettings, onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val resolved = ReaderKeymap.resolve(reader.keymap)
    val conflicts = ReaderKeymap.conflicts(reader.keymap)
    var editing by remember { mutableStateOf<String?>(null) }
    Text(stringResource(Res.string.manga_settings_shortcuts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth()) {
        ReaderKeymap.ACTIONS.forEach { action ->
            val key = resolved.getValue(action)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { editing = action }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(actionLabel(action), style = MaterialTheme.typography.bodyLarge)
                    if (key in conflicts) Text(stringResource(Res.string.manga_settings_shortcut_conflict), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(keyLabel(key), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    TextButton(onClick = { onUpdate { it.copy(keymap = ReaderKeymap.reset()) } }, enabled = reader.keymap.isNotEmpty(), shape = NijiyomeControls.shape) {
        Text(stringResource(Res.string.manga_settings_shortcuts_reset))
    }
    editing?.let { action ->
        var captured by remember(action) { mutableStateOf<String?>(null) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(action) { runCatching { focus.requestFocus() } }
        val k = captured
        val reserved = k != null && k in ReaderKeymap.RESERVED
        val clashes = if (k != null && !reserved) ReaderKeymap.conflictsWith(reader.keymap, action, k) else emptyList()
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(actionLabel(action)) },
            text = {
                Column(
                    Modifier.focusRequester(focus).focusable().onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) {
                            captured = readerKeyName(e) ?: return@onKeyEvent false
                            true
                        } else {
                            false
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Res.string.manga_settings_shortcut_press))
                    Text(k?.let { keyLabel(it) } ?: stringResource(Res.string.manga_settings_shortcut_waiting), style = MaterialTheme.typography.headlineSmall)
                    if (reserved) Text(stringResource(Res.string.manga_settings_shortcut_reserved), color = MaterialTheme.colorScheme.error)
                    if (clashes.isNotEmpty()) {
                        Text(stringResource(Res.string.manga_settings_shortcut_clash, clashes.map { actionLabelPlain(it) }.joinToString(", ")), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val key = k ?: return@TextButton
                    onUpdate { it.copy(keymap = ReaderKeymap.bind(it.keymap, action, key)) }
                    editing = null
                }, enabled = k != null && !reserved, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.manga_settings_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }, shape = NijiyomeControls.shape) { Text(stringResource(app.nijiyome.ui.resources.Res.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun actionLabelPlain(action: String): String = actionLabel(action)

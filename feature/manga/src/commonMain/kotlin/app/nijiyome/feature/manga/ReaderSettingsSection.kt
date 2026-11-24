// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.feature.manga.resources.Res
import app.nijiyome.feature.manga.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlin.math.roundToInt

/**
 * Settings › Lector (`SettingsSection.READER`; checklist manga.settings.section / manga.reader.keybinds / manga.title-language /
 * manga.reader.fit / .loading / .behavior / .modes): every `ReaderSettings` field + `AppSettings.mangaTitleLanguage` + the keymap
 * editor. The same rows (without title language and shortcuts) open inside the reader as a sheet.
 */
@Composable
fun ReaderSettingsSection(settings: AppSettings, onUpdate: (transform: (AppSettings) -> AppSettings) -> Unit, modifier: Modifier = Modifier) {
    val reader = settings.reader.sanitized()
    val update: ((ReaderSettings) -> ReaderSettings) -> Unit = { t -> onUpdate { it.copy(reader = t(it.reader).sanitized()) } }
    Column(modifier.fillMaxWidth()) {
        SettingsTitle(stringResource(Res.string.manga_settings_section))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.manga_settings_title_language)) },
            supportingContent = {
                Column {
                    Text(stringResource(Res.string.manga_settings_title_language_hint))
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
            },
        )
        ReaderSettingsRows(reader, update)
        SettingsTitle(stringResource(Res.string.manga_settings_shortcuts))
        ReaderShortcutRows(reader, update)
    }
}

@Composable
internal fun SettingsTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (hint != null) ({ Text(hint) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: @Composable (Float) -> String, onDone: (Float) -> Unit) {
    var drag by remember(value) { mutableFloatStateOf(value) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(label(drag))
                Slider(value = drag, onValueChange = { drag = it }, onValueChangeFinished = { onDone(drag) }, valueRange = range, steps = steps)
            }
        },
    )
}

@Composable
private fun <T> SegmentRow(title: String, options: List<SegmentedOption<T>>, selected: T, onSelect: (T) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Segmented(options = options, selected = selected, onSelect = onSelect, small = true) },
    )
}

/** Mode, direction, fit, layout, colours, loading and behaviour rows (Settings › Lector and the in-reader sheet). */
@Composable
internal fun ReaderSettingsRows(reader: ReaderSettings, onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    SettingsTitle(stringResource(Res.string.manga_settings_layout))
    SegmentRow(
        stringResource(Res.string.manga_settings_mode),
        listOf(
            SegmentedOption(ReaderMode.PAGED, stringResource(Res.string.manga_mode_paged)),
            SegmentedOption(ReaderMode.VERTICAL, stringResource(Res.string.manga_mode_vertical)),
            SegmentedOption(ReaderMode.WEBTOON, stringResource(Res.string.manga_mode_webtoon)),
        ),
        reader.mode,
    ) { v -> onUpdate { it.copy(mode = v) } }
    SegmentRow(
        stringResource(Res.string.manga_settings_direction),
        listOf(
            SegmentedOption(ReaderDirection.RTL, stringResource(Res.string.manga_direction_rtl)),
            SegmentedOption(ReaderDirection.LTR, stringResource(Res.string.manga_direction_ltr)),
        ),
        reader.direction,
    ) { v -> onUpdate { it.copy(direction = v) } }
    SwitchRow(stringResource(Res.string.manga_settings_double_page), stringResource(Res.string.manga_settings_double_page_hint), reader.doublePage) { v -> onUpdate { it.copy(doublePage = v) } }
    SegmentRow(
        stringResource(Res.string.manga_settings_fit),
        listOf(
            SegmentedOption(ReaderFit.WIDTH, stringResource(Res.string.manga_fit_width)),
            SegmentedOption(ReaderFit.HEIGHT, stringResource(Res.string.manga_fit_height)),
            SegmentedOption(ReaderFit.BOTH, stringResource(Res.string.manga_fit_both)),
            SegmentedOption(ReaderFit.NATURAL, stringResource(Res.string.manga_fit_natural)),
        ),
        reader.fit,
    ) { v -> onUpdate { it.copy(fit = v) } }
    SliderRow(
        stringResource(Res.string.manga_settings_max_width),
        reader.maxWidthDp.toFloat(),
        0f..2000f,
        39,
        label = { v -> if (v.roundToInt() == 0) stringResource(Res.string.manga_settings_no_limit) else stringResource(Res.string.manga_settings_dp, v.roundToInt()) },
    ) { v -> onUpdate { it.copy(maxWidthDp = (v.roundToInt() / 50) * 50) } }
    SliderRow(stringResource(Res.string.manga_settings_gap), reader.pageGapDp.toFloat(), 0f..64f, 15, label = { v -> stringResource(Res.string.manga_settings_dp, v.roundToInt()) }) { v ->
        onUpdate { it.copy(pageGapDp = v.roundToInt()) }
    }
    SwitchRow(stringResource(Res.string.manga_settings_crop), stringResource(Res.string.manga_settings_crop_hint), reader.cropBorders) { v -> onUpdate { it.copy(cropBorders = v) } }

    SettingsTitle(stringResource(Res.string.manga_settings_colors))
    SegmentRow(
        stringResource(Res.string.manga_settings_background),
        listOf(
            SegmentedOption("black", stringResource(Res.string.manga_bg_black)),
            SegmentedOption("gray", stringResource(Res.string.manga_bg_gray)),
            SegmentedOption("white", stringResource(Res.string.manga_bg_white)),
            SegmentedOption("theme", stringResource(Res.string.manga_bg_theme)),
        ),
        reader.background,
    ) { v -> onUpdate { it.copy(background = v) } }
    SwitchRow(stringResource(Res.string.manga_settings_brightness_system), null, reader.brightness < 0f) { v -> onUpdate { it.copy(brightness = if (v) -1f else 0.6f) } }
    if (reader.brightness >= 0f) {
        SliderRow(stringResource(Res.string.manga_settings_brightness), reader.brightness, 0.05f..1f, 18, label = { v -> "${(v * 100).roundToInt()}%" }) { v ->
            onUpdate { it.copy(brightness = v) }
        }
    }

    SettingsTitle(stringResource(Res.string.manga_settings_loading))
    SwitchRow(stringResource(Res.string.manga_settings_eager), stringResource(Res.string.manga_settings_eager_hint), reader.eagerLoading) { v -> onUpdate { it.copy(eagerLoading = v) } }
    if (!reader.eagerLoading) {
        SliderRow(stringResource(Res.string.manga_settings_prefetch), reader.prefetchPages.toFloat(), 1f..20f, 18, label = { v -> stringResource(Res.string.manga_settings_pages, v.roundToInt()) }) { v ->
            onUpdate { it.copy(prefetchPages = v.roundToInt()) }
        }
    }
    SwitchRow(stringResource(Res.string.manga_settings_infinite), stringResource(Res.string.manga_settings_infinite_hint), reader.infiniteScroll) { v -> onUpdate { it.copy(infiniteScroll = v) } }

    SettingsTitle(stringResource(Res.string.manga_settings_behavior))
    SliderRow(
        stringResource(Res.string.manga_settings_auto_scroll),
        reader.autoScrollSpeed.toFloat(),
        0f..600f,
        29,
        label = { v -> if (v.roundToInt() == 0) stringResource(Res.string.manga_settings_off) else stringResource(Res.string.manga_settings_px_s, v.roundToInt()) },
    ) { v -> onUpdate { it.copy(autoScrollSpeed = (v.roundToInt() / 20) * 20) } }
    SwitchRow(stringResource(Res.string.manga_settings_fullscreen), null, reader.fullscreenOnOpen) { v -> onUpdate { it.copy(fullscreenOnOpen = v) } }
    SwitchRow(stringResource(Res.string.manga_settings_hide_chrome), stringResource(Res.string.manga_settings_hide_chrome_hint), reader.hideChromeOnTap) { v -> onUpdate { it.copy(hideChromeOnTap = v) } }
    SwitchRow(stringResource(Res.string.manga_settings_progress), null, reader.showProgress) { v -> onUpdate { it.copy(showProgress = v) } }
    SwitchRow(stringResource(Res.string.manga_settings_zoom_keys), stringResource(Res.string.manga_settings_zoom_keys_hint), reader.zoomShortcuts) { v -> onUpdate { it.copy(zoomShortcuts = v) } }
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

/** Shortcut editor (checklist manga.reader.keybinds): tap an action, press a key; conflicts are marked; "Restablecer". */
@Composable
private fun ReaderShortcutRows(reader: ReaderSettings, onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val resolved = ReaderKeymap.resolve(reader.keymap)
    val conflicts = ReaderKeymap.conflicts(reader.keymap)
    var editing by remember { mutableStateOf<String?>(null) }
    Text(stringResource(Res.string.manga_settings_shortcuts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    ReaderKeymap.ACTIONS.forEach { action ->
        val key = resolved.getValue(action)
        ListItem(
            headlineContent = { Text(actionLabel(action)) },
            supportingContent = if (key in conflicts) ({ Text(stringResource(Res.string.manga_settings_shortcut_conflict), color = MaterialTheme.colorScheme.error) }) else null,
            trailingContent = {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(keyLabel(key), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                }
            },
            modifier = Modifier.clickable { editing = action },
        )
    }
    TextButton(onClick = { onUpdate { it.copy(keymap = ReaderKeymap.reset()) } }, enabled = reader.keymap.isNotEmpty(), modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(Res.string.manga_settings_shortcuts_reset))
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
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
                }, enabled = k != null && !reserved) { Text(stringResource(Res.string.manga_settings_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(app.nijiyome.ui.resources.Res.string.ui_cancel)) } },
        )
    }
}

@Composable
private fun actionLabelPlain(action: String): String = actionLabel(action)

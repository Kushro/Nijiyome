// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nijiyome.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.designsystem.direction.CanonDirection
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.Directions
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.generated.NijiyomeTokens
import app.nijiyome.designsystem.generated.PaletteInfo
import app.nijiyome.designsystem.motion.rememberLowEndDevice
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.AppLanguage
import app.nijiyome.model.AppSettings
import app.nijiyome.model.CalendarStructure
import app.nijiyome.model.ContrastPref
import app.nijiyome.model.DesignDirection
import app.nijiyome.model.DesignPrefs
import app.nijiyome.model.DetailsStructure
import app.nijiyome.model.ExploreStructure
import app.nijiyome.model.HomeRowIds
import app.nijiyome.model.HomeStructure
import app.nijiyome.model.LibraryStructure
import app.nijiyome.model.LinkStructure
import app.nijiyome.model.LookPrefs
import app.nijiyome.model.LookPreset
import app.nijiyome.model.MotionMoments
import app.nijiyome.model.MotionProfile
import app.nijiyome.model.NavStyle
import app.nijiyome.model.PerfMode
import app.nijiyome.model.PlayerStructure
import app.nijiyome.model.ScreenStructures
import app.nijiyome.model.SettingsStructure
import app.nijiyome.model.SourcePickerStructure
import app.nijiyome.model.SourcesStructure
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.TransfersStructure
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCards
import app.nijiyome.ui.look.effectiveCardId
import app.nijiyome.ui.patterns.LivePreviewScaffold
import app.nijiyome.ui.patterns.ReorderableRow
import app.nijiyome.ui.patterns.gallery.gallerySampleCards
import app.nijiyome.ui.patterns.moved
import app.nijiyome.ui.patterns.rememberReorderState
import app.nijiyome.ui.patterns.rememberShowUndo
import app.nijiyome.ui.patterns.reorderContainer
import app.nijiyome.ui.patterns.reorderPlacement
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.model.CardSize as ModelCardSize

// Ajustes › Apariencia (artifact screen `ajustes`, category `apariencia`) with the Sparkles design picker: everything the redesign
// offers is chosen here and written through `setLook { it.copy(design = …) }`. The picker lists and previews every direction, card
// and motion variant; it never special-cases one (the only Canon rule is the contract's: Canon's palettes are `AppSettings.paletteId`).

/** One option of a choice row: its value, name and one-line description (artifact `structures[]`). */
internal class ChoiceOption<T>(val value: T, val label: String, val desc: String? = null)

/** A radio group of cards, one per option, each with its name and description. */
@Composable
internal fun <T> StructureChoiceRow(
    label: String,
    hint: String?,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    divider: Boolean = true,
) {
    SettingRow(label, hint, stack = true, divider = divider) {
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { o -> ChoiceCard(o.label, o.desc, selected = o.value == selected, onClick = { onSelect(o.value) }, modifier = Modifier.width(232.dp)) }
        }
    }
}

@Composable
private fun ChoiceCard(label: String, desc: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, top: (@Composable () -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.large).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (top != null) top()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- page

/** Apariencia: the controls scroll beside a live preview (pattern `sub`); on narrow panes the preview sits on top. */
@Composable
internal fun AppearancePage(state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val stacked = maxWidth < 760.dp
        LivePreviewScaffold(
            preview = { AppearanceLivePreview(state.settings) },
            caption = stringResource(Res.string.rd_s10_preview_caption),
            stacked = stacked,
            controlsWidth = (maxWidth - 300.dp).coerceAtLeast(360.dp),
        ) {
            AppearanceGroups(state, vm, actions)
        }
    }
}

@Composable
private fun AppearanceGroups(state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions) {
    val s = state.settings
    val look = s.look
    val design = look.design
    val showUndo = rememberShowUndo()

    // ---- Tema y color
    SettingsGroup(stringResource(Res.string.rd_s10_grp_theme), icon = Icons.Outlined.Contrast) {
        SettingAnchor("theme_mode") {
            SettingRow(stringResource(Res.string.settings_mode), stack = true, divider = false) {
                Segmented(
                    options = listOf(
                        SegmentedOption(ThemeMode.SYSTEM, stringResource(Res.string.settings_mode_system)),
                        SegmentedOption(ThemeMode.LIGHT, stringResource(Res.string.settings_mode_light)),
                        SegmentedOption(ThemeMode.DARK, stringResource(Res.string.settings_mode_dark)),
                    ),
                    selected = s.themeMode,
                    onSelect = vm::setThemeMode,
                    fillWidth = true,
                )
            }
        }
        SettingAnchor("amoled") {
            val amoledApplicable = s.themeMode != ThemeMode.LIGHT
            SwitchRow(
                label = stringResource(Res.string.settings_amoled),
                checked = s.amoled && amoledApplicable,
                onChange = vm::setAmoled,
                hint = stringResource(if (amoledApplicable) Res.string.settings_amoled_hint else Res.string.rd_s10_amoled_needs_dark),
                enabled = amoledApplicable,
            )
        }
        SettingAnchor("palette") { PaletteRow(s, onCanonPalette = vm::setPalette, onDirectionPalette = vm::setDirectionPalette) }
        if (dynamicColorSupported) {
            SettingAnchor("dynamic_color") {
                SwitchRow(stringResource(Res.string.settings_dynamic), s.dynamicColor, vm::setDynamicColor, hint = stringResource(Res.string.rd_s10_dynamic_replaces))
            }
        }
        SettingAnchor("contrast") {
            val contrastEnabled = !(s.dynamicColor && dynamicColorSupported)
            SettingRow(stringResource(Res.string.settings_contrast), hint = if (contrastEnabled) null else stringResource(Res.string.rd_s10_contrast_needs_palette), stack = true) {
                Segmented(
                    options = ContrastPref.entries.map { SegmentedOption(it, stringResource(it.labelRes()), enabled = contrastEnabled) },
                    selected = s.contrast,
                    onSelect = vm::setContrast,
                    fillWidth = true,
                )
            }
        }
        SettingAnchor("language") {
            SettingRow(stringResource(Res.string.settings_language), stack = true) {
                Segmented(
                    options = listOf(
                        SegmentedOption(AppLanguage.SYSTEM, stringResource(Res.string.settings_lang_system)),
                        SegmentedOption(AppLanguage.EN, stringResource(Res.string.settings_lang_en)),
                        SegmentedOption(AppLanguage.ES, stringResource(Res.string.settings_lang_es)),
                    ),
                    selected = s.language,
                    onSelect = vm::setLanguage,
                    fillWidth = true,
                )
            }
        }
    }

    // ---- Diseño
    SettingsGroup(stringResource(Res.string.rd_s10_grp_design), icon = Icons.Outlined.AutoAwesome, hint = stringResource(Res.string.rd_s10_grp_design_hint)) {
        SettingAnchor("design_direction") {
            val applied = Directions.all.associate { it.id to stringResource(Res.string.rd_s10_undo_direction, it.name()) }
            DirectionPicker(s) { spec ->
                val prev = design
                val dir = DesignDirection.entries.firstOrNull { it.id == spec.id } ?: return@DirectionPicker
                if (dir == design.direction) return@DirectionPicker
                vm.setDirection(dir)
                showUndo(applied.getValue(spec.id)) { vm.setDesign { prev } }
            }
        }
        SettingAnchor("design_card") { CardStylePicker(look) { id -> vm.setDesign { it.copy(card = id) } } }
        SettingAnchor("design_gallery") {
            SettingRow(stringResource(Res.string.rd_s10_design_gallery), stringResource(Res.string.rd_s10_design_gallery_hint)) {
                FilledTonalButton(onClick = actions.onOpenGallery) {
                    Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s10_design_gallery_open), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        SettingAnchor("design_reset") {
            val done = stringResource(Res.string.rd_s10_design_reset_done)
            SettingRow(stringResource(Res.string.rd_s10_design_reset), stringResource(Res.string.rd_s10_design_reset_hint)) {
                OutlinedButton(
                    enabled = design != DesignPrefs(introSeen = design.introSeen),
                    onClick = {
                        val prev = design
                        vm.setDesign { DesignPrefs(introSeen = true) }
                        showUndo(done) { vm.setDesign { prev } }
                    },
                ) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s10_design_reset_action), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    // ---- Estilo general (presets of the Diseño y estilo options; they keep the Sparkles design)
    SettingsGroup(stringResource(Res.string.settings_look_title), icon = Icons.Outlined.Style) {
        SettingAnchor("look_presets") {
            val applied = LookPreset.entries.associateWith { stringResource(Res.string.rd_s10_undo_preset, stringResource(it.labelRes())) }
            LookPresetRow(look) { preset ->
                val prev = look
                vm.applyLookPreset(preset)
                showUndo(applied.getValue(preset)) { vm.setLook { prev } }
            }
        }
    }

    // ---- Movimiento
    SettingsGroup(stringResource(Res.string.rd_s10_grp_motion), icon = Icons.Outlined.Animation) {
        SettingAnchor("motion") { MotionLookRow(look, vm::setLook) }
        SettingAnchor("design_motion_profile") {
            StructureChoiceRow(
                stringResource(Res.string.rd_s10_design_motion_profile),
                stringResource(Res.string.rd_s10_design_motion_profile_hint),
                listOf(
                    ChoiceOption(MotionProfile.FULL, stringResource(Res.string.rd_s10_mp_full), stringResource(Res.string.rd_s10_mp_full_desc)),
                    ChoiceOption(MotionProfile.CALM, stringResource(Res.string.rd_s10_mp_calm), stringResource(Res.string.rd_s10_mp_calm_desc)),
                    ChoiceOption(MotionProfile.PERF, stringResource(Res.string.rd_s10_mp_perf), stringResource(Res.string.rd_s10_mp_perf_desc)),
                ),
                design.motionProfile,
                { p -> vm.setDesign { it.copy(motionProfile = p) } },
            )
        }
        SettingAnchor("design_perf_mode") {
            val lowEnd = rememberLowEndDevice()
            SettingRow(stringResource(Res.string.rd_s10_design_perf), stringResource(Res.string.rd_s10_design_perf_hint), stack = true) {
                Segmented(
                    options = listOf(
                        SegmentedOption(PerfMode.AUTO, stringResource(Res.string.rd_s10_perf_auto)),
                        SegmentedOption(PerfMode.ON, stringResource(Res.string.rd_s10_perf_on)),
                        SegmentedOption(PerfMode.OFF, stringResource(Res.string.rd_s10_perf_off)),
                    ),
                    selected = design.perfMode,
                    onSelect = { m -> vm.setDesign { it.copy(perfMode = m) } },
                    fillWidth = true,
                )
                // Only a suggestion: the device looks slow, the user turned the mode off; nothing is forced.
                if (lowEnd && design.perfMode == PerfMode.OFF) {
                    Text(
                        stringResource(Res.string.rd_s10_perf_suggest),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSignalColors.current.warn,
                        modifier = Modifier.politeLiveRegion(),
                    )
                }
            }
        }
        SettingAnchor("design_motion_moments") {
            SettingRow(stringResource(Res.string.rd_s10_design_moments), stringResource(Res.string.rd_s10_design_moments_hint), stack = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotionMoments.VARIANTS.forEach { (moment, variants) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(momentNameRes(moment)), style = MaterialTheme.typography.labelLarge)
                            Segmented(
                                options = variants.map { v -> SegmentedOption(v, stringResource(variantNameRes(v))) },
                                selected = design.motionVariant(moment),
                                onSelect = { v -> vm.setDesign { it.copy(motionVariants = it.motionVariants + (moment to v)) } },
                                small = true,
                                fillWidth = true,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Navegación (wide windows; phones keep the bottom bar)
    if (actions.wide) {
        SettingsGroup(stringResource(Res.string.rd_s10_grp_navigation), icon = Icons.Outlined.ViewSidebar) {
            SettingAnchor("design_nav") {
                StructureChoiceRow(
                    stringResource(Res.string.rd_s10_design_nav),
                    stringResource(Res.string.rd_s10_design_nav_hint),
                    listOf(
                        ChoiceOption(NavStyle.RAIL, stringResource(Res.string.rd_s10_nav_rail), stringResource(Res.string.rd_s10_nav_rail_desc)),
                        ChoiceOption(NavStyle.MEDIUM, stringResource(Res.string.rd_s10_nav_medium), stringResource(Res.string.rd_s10_nav_medium_desc)),
                        ChoiceOption(NavStyle.COMMAND, stringResource(Res.string.rd_s10_nav_command), stringResource(Res.string.rd_s10_nav_command_desc)),
                    ),
                    design.nav,
                    { n -> vm.setDesign { it.copy(nav = n) } },
                    divider = false,
                )
            }
            SettingAnchor("rail_style") { RailStyleRow(look, vm::setLook) }
        }
    }

    // ---- Pantallas: the structure of every screen, the Diseño y estilo options of that screen beside it
    SettingsGroup(stringResource(Res.string.rd_s10_design_structures), icon = Icons.Outlined.Dashboard, hint = stringResource(Res.string.rd_s10_design_structures_hint)) {
        SettingAnchor("design_structures") { ScreenStructureRows(look, vm) }
    }
}

@Composable
private fun ScreenStructureRows(look: LookPrefs, vm: SettingsViewModel) {
    val st = look.design.structures
    fun set(t: (ScreenStructures) -> ScreenStructures) = vm.setDesign { it.copy(structures = t(it.structures)) }

    SettingAnchor("look_home") {
        SubHeading(stringResource(Res.string.rd_s10_screen_home))
        StructureChoiceRow(stringResource(Res.string.rd_s10_structure), null, homeOptions(), st.home, { v -> set { it.copy(home = v) } }, divider = false)
        HomeLookRows(look, vm::setLook)
    }
    SettingAnchor("design_home_rows") { HomeRowsEditor(look.design, vm) }

    SettingAnchor("look_details") {
        SubHeading(stringResource(Res.string.rd_s10_screen_details))
        StructureChoiceRow(stringResource(Res.string.rd_s10_structure), null, detailsOptions(), st.details, { v -> set { it.copy(details = v) } }, divider = false)
        DetailsLookRows(look, vm::setLook)
        StructureChoiceRow(
            stringResource(Res.string.rd_s10_screen_source_picker), null,
            listOf(
                ChoiceOption(SourcePickerStructure.FACETS, stringResource(Res.string.rd_s10_st_facets), stringResource(Res.string.rd_s10_st_src_facets_desc)),
                ChoiceOption(SourcePickerStructure.COMPARE, stringResource(Res.string.rd_s10_st_compare), stringResource(Res.string.rd_s10_st_compare_desc)),
            ),
            st.sourcePicker, { v -> set { it.copy(sourcePicker = v) } },
        )
    }

    SettingAnchor("look_cards") {
        SubHeading(stringResource(Res.string.rd_s10_screen_cards))
        CardLookRows(look, vm::setLook)
    }

    SubHeading(stringResource(Res.string.rd_s10_screen_player))
    StructureChoiceRow(stringResource(Res.string.rd_s10_structure), stringResource(Res.string.rd_s10_player_structure_hint), playerStructureOptions(), st.player, vm::setPlayerStructure, divider = false)

    SubHeading(stringResource(Res.string.rd_s10_screen_explore))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(ExploreStructure.FACETS, stringResource(Res.string.rd_s10_st_facets), stringResource(Res.string.rd_s10_st_explore_facets_desc)),
            ChoiceOption(ExploreStructure.CANVAS, stringResource(Res.string.rd_s10_st_canvas), stringResource(Res.string.rd_s10_st_canvas_desc)),
        ),
        st.explore, { v -> set { it.copy(explore = v) } }, divider = false,
    )

    SettingAnchor("look_calendar") {
        SubHeading(stringResource(Res.string.rd_s10_screen_calendar))
        // Replaces the calendar's default view of Diseño y estilo.
        StructureChoiceRow(
            stringResource(Res.string.rd_s10_structure), stringResource(Res.string.rd_s10_look_calendar_hint),
            listOf(
                ChoiceOption(CalendarStructure.WEEK, stringResource(Res.string.rd_s10_st_week), stringResource(Res.string.rd_s10_st_week_desc)),
                ChoiceOption(CalendarStructure.DEPARTURES, stringResource(Res.string.rd_s10_st_departures), stringResource(Res.string.rd_s10_st_departures_desc)),
                ChoiceOption(CalendarStructure.AGENDA, stringResource(Res.string.rd_s10_st_agenda), stringResource(Res.string.rd_s10_st_agenda_desc)),
            ),
            st.calendar, { v -> set { it.copy(calendar = v) } }, divider = false,
        )
    }

    SubHeading(stringResource(Res.string.rd_s10_screen_library))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(LibraryStructure.SHELVES, stringResource(Res.string.rd_s10_st_shelves), stringResource(Res.string.rd_s10_st_lib_shelves_desc)),
            ChoiceOption(LibraryStructure.BOARD, stringResource(Res.string.rd_s10_st_board), stringResource(Res.string.rd_s10_st_board_desc)),
            ChoiceOption(LibraryStructure.LIST, stringResource(Res.string.rd_s10_st_list), stringResource(Res.string.rd_s10_st_list_desc)),
        ),
        st.library, { v -> set { it.copy(library = v) } }, divider = false,
    )

    SubHeading(stringResource(Res.string.rd_s10_screen_transfers))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(TransfersStructure.QUEUE, stringResource(Res.string.rd_s10_st_queue), stringResource(Res.string.rd_s10_st_queue_desc)),
            ChoiceOption(TransfersStructure.MASTER_DETAIL, stringResource(Res.string.rd_s10_st_master), stringResource(Res.string.rd_s10_st_master_desc)),
        ),
        st.transfers, { v -> set { it.copy(transfers = v) } }, divider = false,
    )

    SubHeading(stringResource(Res.string.rd_s10_screen_sources))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(SourcesStructure.TABS, stringResource(Res.string.rd_s10_st_tabs), stringResource(Res.string.rd_s10_st_tabs_desc)),
            ChoiceOption(SourcesStructure.STORE, stringResource(Res.string.rd_s10_st_store), stringResource(Res.string.rd_s10_st_store_desc)),
        ),
        st.sources, { v -> set { it.copy(sources = v) } }, divider = false,
    )

    SubHeading(stringResource(Res.string.rd_s10_screen_link))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(LinkStructure.MAP, stringResource(Res.string.rd_s10_st_map), stringResource(Res.string.rd_s10_st_map_desc)),
            ChoiceOption(LinkStructure.PANEL, stringResource(Res.string.rd_s10_st_cards), stringResource(Res.string.rd_s10_st_cards_desc)),
        ),
        st.link, { v -> set { it.copy(link = v) } }, divider = false,
    )

    SubHeading(stringResource(Res.string.rd_s10_screen_settings))
    StructureChoiceRow(
        stringResource(Res.string.rd_s10_structure), null,
        listOf(
            ChoiceOption(SettingsStructure.LIST_DETAIL, stringResource(Res.string.rd_s10_st_list_detail), stringResource(Res.string.rd_s10_st_list_detail_desc)),
            ChoiceOption(SettingsStructure.SEARCH_FIRST, stringResource(Res.string.rd_s10_st_search_first), stringResource(Res.string.rd_s10_st_search_first_desc)),
        ),
        st.settings, { v -> set { it.copy(settings = v) } }, divider = false,
    )
}

@Composable
private fun homeOptions(): List<ChoiceOption<HomeStructure>> = listOf(
    ChoiceOption(HomeStructure.TONIGHT, stringResource(Res.string.rd_s10_st_tonight), stringResource(Res.string.rd_s10_st_tonight_desc)),
    ChoiceOption(HomeStructure.STAGE, stringResource(Res.string.rd_s10_st_stage), stringResource(Res.string.rd_s10_st_stage_desc)),
    ChoiceOption(HomeStructure.SHELVES, stringResource(Res.string.rd_s10_st_shelves), stringResource(Res.string.rd_s10_st_home_shelves_desc)),
)

@Composable
private fun detailsOptions(): List<ChoiceOption<DetailsStructure>> = listOf(
    ChoiceOption(DetailsStructure.PANEL, stringResource(Res.string.rd_s10_st_panel), stringResource(Res.string.rd_s10_st_details_panel_desc)),
    ChoiceOption(DetailsStructure.ROUTE, stringResource(Res.string.rd_s10_st_route), stringResource(Res.string.rd_s10_st_route_desc)),
    ChoiceOption(DetailsStructure.CINEMA, stringResource(Res.string.rd_s10_st_cinema), stringResource(Res.string.rd_s10_st_details_cinema_desc)),
)

/** The player structures (also the touch "Estilo del reproductor" of before: Cine and Mínimo). */
@Composable
internal fun playerStructureOptions(): List<ChoiceOption<PlayerStructure>> = listOf(
    ChoiceOption(PlayerStructure.CINEMA, stringResource(Res.string.rd_s10_st_cinema), stringResource(Res.string.rd_s10_st_player_cinema_desc)),
    ChoiceOption(PlayerStructure.MINIMAL, stringResource(Res.string.rd_s10_st_minimal), stringResource(Res.string.rd_s10_st_minimal_desc)),
    ChoiceOption(PlayerStructure.PANEL, stringResource(Res.string.rd_s10_st_panel), stringResource(Res.string.rd_s10_st_player_panel_desc)),
)

// ---------------------------------------------------------------- palette

/** The palettes of the chosen direction (Canon: the 20 of before, `AppSettings.paletteId`). */
@Composable
internal fun PaletteRow(s: AppSettings, onCanonPalette: (String) -> Unit, onDirectionPalette: (String) -> Unit, divider: Boolean = true) {
    val spec = Directions.byId(s.look.design.direction.id)
    val dynamicOn = s.dynamicColor && dynamicColorSupported
    val list = rememberLazyListState()
    val canon = spec.id == CanonDirection.id
    SettingRow(
        stringResource(Res.string.settings_palette),
        stringResource(
            when {
                dynamicOn -> Res.string.rd_s10_palette_dynamic_on
                canon -> Res.string.settings_palette_hint
                spec.palettes.size > 1 -> Res.string.rd_s10_palette_hint_direction
                else -> Res.string.rd_s10_palette_hint_single
            },
        ),
        stack = true,
        divider = divider,
    ) {
        LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().horizontalFadingEdge(list).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (canon) {
                // Canon keeps the 20 palettes of before (AppSettings.paletteId).
                items(NijiyomeTokens.paletteInfo, key = { it.id }) { info ->
                    PaletteSwatch(info.seed, info.label, selected = info.id == s.paletteId && !dynamicOn, onClick = { onCanonPalette(info.id) })
                }
            } else {
                val current = spec.palette(s.look.design.palette).id
                items(spec.palettes, key = { it.id }) { p ->
                    PaletteSwatch(p.swatch, p.name(), selected = p.id == current && !dynamicOn, onClick = { onDirectionPalette(p.id) })
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val checkTint = if (color.luminance() > 0.45f) Color(0xFF0A0A0D) else Color.White
    Column(
        Modifier.width(64.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary), CircleShape).padding(3.dp) else Modifier)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun ContrastPref.labelRes(): StringResource = when (this) {
    ContrastPref.STANDARD -> Res.string.settings_contrast_standard
    ContrastPref.MEDIUM -> Res.string.settings_contrast_medium
    ContrastPref.HIGH -> Res.string.settings_contrast_high
}

internal fun ContrastPref.level(): ContrastLevel = when (this) {
    ContrastPref.STANDARD -> ContrastLevel.Standard
    ContrastPref.MEDIUM -> ContrastLevel.Medium
    ContrastPref.HIGH -> ContrastLevel.High
}

// ---------------------------------------------------------------- direction

/** Every direction with its name, tagline and a small preview drawn with its own palette in the current tone. */
@Composable
internal fun DirectionPicker(s: AppSettings, compact: Boolean = false, onPick: (DirectionSpec) -> Unit) {
    val tone = LocalThemeTone.current
    val level = s.contrast.level()
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Directions.all.forEach { spec ->
            val selected = spec.id == s.look.design.direction.id
            val palette = when {
                spec.id == CanonDirection.id -> spec.palette(s.paletteId)
                selected -> spec.palette(s.look.design.palette)
                else -> spec.palette(null)
            }
            ChoiceCard(
                label = spec.name(),
                desc = if (compact) null else spec.tagline(),
                selected = selected,
                onClick = { onPick(spec) },
                modifier = Modifier.width(if (compact) 150.dp else 232.dp),
                top = { DirectionThumb(palette, tone, level) },
            )
        }
    }
}

/** A mock screen in [palette]'s colors: background, a rail, a primary button, two cards and text lines. Decorative. */
@Composable
internal fun DirectionThumb(palette: DirectionPalette, tone: ThemeTone, level: ContrastLevel) {
    val c = palette.scheme(tone, level)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.background)
            .border(1.dp, c.outlineVariant, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.width(12.dp).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.surfaceContainer).padding(2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.outlineVariant))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(CircleShape).background(c.onBackground))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(if (i == 0) c.tertiaryContainer else c.surfaceContainerHigh))
                }
            }
            Box(Modifier.width(36.dp).height(8.dp).clip(CircleShape).background(c.primary))
        }
    }
}

// ---------------------------------------------------------------- cards

/** "The direction's default" plus every style of [TitleCards.all], each previewed with a sample title. */
@Composable
private fun CardStylePicker(look: LookPrefs, onPick: (String?) -> Unit) {
    val sample = gallerySampleCards().firstOrNull()
    val design = look.design
    SettingRow(stringResource(Res.string.rd_s10_design_card), stringResource(Res.string.rd_s10_design_card_hint), stack = true) {
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val defaultId = look.copy(design = design.copy(card = null)).effectiveCardId()
            CardOption(
                label = stringResource(Res.string.rd_s10_card_default, TitleCards.byId(defaultId).name()),
                styleId = defaultId,
                sample = sample,
                selected = design.card == null,
                onClick = { onPick(null) },
            )
            TitleCards.all.forEach { style ->
                CardOption(label = style.name(), styleId = style.id, sample = sample, selected = design.card == style.id, onClick = { onPick(style.id) })
            }
        }
    }
}

@Composable
private fun CardOption(label: String, styleId: String, sample: TitleCardData?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(136.dp)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sample != null) {
                // The preview only shows the style: its own click and semantics are covered by the option.
                Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    TitleCards.byId(styleId).Card(sample, CardContext.GRID, ModelCardSize.SMALL, CardShow(), onClick, Modifier.fillMaxWidth(), null)
                    Box(Modifier.matchParentSize().clickable(onClick = onClick))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Home rows

/** Inicio's rows: drag (or Alt+↑/↓) to reorder, the switch shows or hides each. */
@Composable
private fun HomeRowsEditor(design: DesignPrefs, vm: SettingsViewModel) {
    val order = remember(design.homeRowOrder) { (design.homeRowOrder.filter { it in HomeRowIds.DEFAULT_ORDER } + HomeRowIds.DEFAULT_ORDER).distinct() }
    val reorder = rememberReorderState(onMove = { from, to -> vm.setDesign { it.copy(homeRowOrder = order.moved(from, to)) } })
    val hiddenLabel = stringResource(Res.string.rd_s10_row_hidden)
    SettingRow(stringResource(Res.string.rd_s10_design_home_rows), stringResource(Res.string.rd_s10_design_home_rows_hint), stack = true) {
        LazyColumn(
            state = reorder.listState,
            modifier = Modifier.fillMaxWidth().height((order.size * 57).dp).reorderContainer(reorder),
            userScrollEnabled = false,
        ) {
            itemsIndexed(order, key = { _, id -> id }) { i, id ->
                val visible = id !in design.homeRowsHidden
                val name = stringResource(homeRowNameRes(id))
                ReorderableRow(
                    state = reorder,
                    index = i,
                    count = order.size,
                    title = name,
                    supporting = if (visible) null else hiddenLabel,
                    modifier = Modifier.reorderPlacement(),
                    trailing = {
                        Switch(
                            checked = visible,
                            onCheckedChange = { on ->
                                vm.setDesign { it.copy(homeRowsHidden = if (on) it.homeRowsHidden - id else it.homeRowsHidden + id) }
                            },
                            modifier = Modifier.semantics { contentDescription = name },
                        )
                    },
                )
            }
        }
    }
}

private fun homeRowNameRes(id: String): StringResource = when (id) {
    "continuar" -> Res.string.rd_s10_row_continuar
    "descargando" -> Res.string.rd_s10_row_descargando
    "siguiendo" -> Res.string.rd_s10_row_siguiendo
    "ultimos" -> Res.string.rd_s10_row_ultimos
    "top" -> Res.string.rd_s10_row_top
    "parati" -> Res.string.rd_s10_row_parati
    "porque" -> Res.string.rd_s10_row_porque
    "generos" -> Res.string.rd_s10_row_generos
    "feeds" -> Res.string.rd_s10_row_feeds
    "estrenos" -> Res.string.rd_s10_row_estrenos
    "favoritos" -> Res.string.rd_s10_row_favoritos
    "extensiones" -> Res.string.rd_s10_row_extensiones
    else -> Res.string.rd_s10_row_mas
}

// ---------------------------------------------------------------- motion names

private fun momentNameRes(moment: String): StringResource = when (moment) {
    MotionMoments.CARD_TO_DETAILS -> Res.string.rd_s10_mo_ficha
    MotionMoments.CARD_SWAY -> Res.string.rd_s10_mo_tanzaku
    MotionMoments.HOME_ENTRANCE -> Res.string.rd_s10_mo_inicio
    MotionMoments.MARK_WATCHED -> Res.string.rd_s10_mo_visto
    MotionMoments.PLAYER_CONTROLS -> Res.string.rd_s10_mo_controles
    MotionMoments.LOADING -> Res.string.rd_s10_mo_carga
    MotionMoments.STATUS_CHANGE -> Res.string.rd_s10_mo_estado
    else -> Res.string.rd_s10_mo_snack
}

private fun variantNameRes(variant: String): StringResource = when (variant) {
    "ct" -> Res.string.rd_s10_mv_ct
    "axis" -> Res.string.rd_s10_mv_axis
    "paper" -> Res.string.rd_s10_mv_paper
    "spring" -> Res.string.rd_s10_mv_spring
    "wind" -> Res.string.rd_s10_mv_wind
    "scroll" -> Res.string.rd_s10_mv_scroll
    "cascade" -> Res.string.rd_s10_mv_cascade
    "rows" -> Res.string.rd_s10_mv_rows
    "none" -> Res.string.rd_s10_mv_none
    "stamp" -> Res.string.rd_s10_mv_stamp
    "stations" -> Res.string.rd_s10_mv_stations
    "band" -> Res.string.rd_s10_mv_band
    "fade" -> Res.string.rd_s10_mv_fade
    "slide" -> Res.string.rd_s10_mv_slide
    "min" -> Res.string.rd_s10_mv_min
    "shimmer" -> Res.string.rd_s10_mv_shimmer
    "ink" -> Res.string.rd_s10_mv_ink
    "count" -> Res.string.rd_s10_mv_count
    "ind" -> Res.string.rd_s10_mv_ind
    "ribbon" -> Res.string.rd_s10_mv_ribbon
    "odo" -> Res.string.rd_s10_mv_odo
    "rise" -> Res.string.rd_s10_mv_rise
    "stack" -> Res.string.rd_s10_mv_stack
    else -> Res.string.rd_s10_mv_origin
}

// ---------------------------------------------------------------- live preview

/** Live mini-preview reacting at once to direction, palette, tone, contrast, card style and density (all through the theme). */
@Composable
internal fun AppearanceLivePreview(s: AppSettings) {
    val scheme = MaterialTheme.colorScheme
    val sample = gallerySampleCards().take(2)
    Surface(
        color = scheme.background,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.rd_s10_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    text = stringResource(
                        when {
                            s.amoled && s.themeMode != ThemeMode.LIGHT -> Res.string.rd_s10_tone_oled
                            s.themeMode == ThemeMode.LIGHT -> Res.string.settings_mode_light
                            s.themeMode == ThemeMode.DARK -> Res.string.settings_mode_dark
                            else -> Res.string.settings_mode_system
                        },
                    ),
                    tone = PillTone.NEUTRAL,
                )
            }
            if (s.look.showGreeting) Text(stringResource(Res.string.rd_s10_preview_greeting), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics {}) {
                sample.forEach { card ->
                    Box(Modifier.width(96.dp)) { TitleCard(card, onClick = {}, context = CardContext.GRID) }
                }
            }
            StatusMark(StatusSlot.WATCHING, stringResource(Res.string.rd_s10_preview_watching), selected = true)
            Text(stringResource(Res.string.rd_s10_preview_secondary), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

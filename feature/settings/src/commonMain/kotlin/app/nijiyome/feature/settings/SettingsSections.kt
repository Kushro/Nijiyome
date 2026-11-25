// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.search.DiscoverSettingsExtras
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.downloads.DownloadsSettingsSection
import app.nijiyome.feature.extensions.SourcesSettingsExtras
import app.nijiyome.feature.manga.ReaderSettingsSection
import app.nijiyome.feature.novel.NovelReaderSettingsSection
import app.nijiyome.feature.player.PlayerPlaybackSettings
import app.nijiyome.feature.player.PlayerShortcutSettings
import app.nijiyome.feature.player.SubtitleLanguageSettings
import app.nijiyome.feature.player.SubtitleStyleEditor
import app.nijiyome.feature.player.SubtitleTranslationSettings
import app.nijiyome.model.LoadState
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.PlayerQualityPrefs
import app.nijiyome.model.Quality
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.patterns.ReorderableRow
import app.nijiyome.ui.patterns.moved
import app.nijiyome.ui.patterns.rememberReorderState
import app.nijiyome.ui.patterns.reorderContainer
import app.nijiyome.ui.patterns.reorderPlacement
import app.nijiyome.ui.state.userMessage
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/** Navigation a category may need; built once by `SettingsScreen`. */
internal class SettingsActions(
    val onOpenExtensions: () -> Unit,
    val onOpenLinked: () -> Unit,
    val onOpenDebrid: () -> Unit,
    val onOpenCalendar: (() -> Unit)?,
    val onOpenGallery: () -> Unit,
    /** The window is wide (rail style and navigation style only apply there). */
    val wide: Boolean,
)

/**
 * The groups of every category that scrolls as one column (all but Apariencia, which has its live preview, and Torrents y descargas,
 * whose torrent page scrolls on its own). One `when` over the category; no branch on the direction or the structure.
 */
@Composable
internal fun ColumnScope.CategoryGroups(category: SettingsCategory, state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions) {
    when (category) {
        SettingsCategory.PLAYBACK -> PlaybackCategory(state, vm)
        SettingsCategory.ENHANCEMENTS -> EnhancementsCategory(state, vm)
        SettingsCategory.SUBTITLES -> SubtitlesCategory(state, vm)
        SettingsCategory.SOURCES -> SourcesCategory(state, vm, actions)
        SettingsCategory.ACCOUNTS -> SettingsGroup(stringResource(Res.string.settings_accounts)) {
            SettingAnchor("accounts_anilist_mal") { AccountsSection(state, vm) }
        }
        SettingsCategory.METADATA -> MetadataCategory(state, vm)
        SettingsCategory.LINK -> SettingsGroup(stringResource(Res.string.settings_link)) {
            LinkSectionForPlatform(state, vm, actions.onOpenLinked)
        }
        SettingsCategory.READERS -> {
            SettingsGroup(stringResource(Res.string.rd_s10_set_manga)) {
                SettingAnchor("reader_manga") { ReaderSettingsSection(settings = state.settings, onUpdate = vm::updateSettings) }
            }
            SettingsGroup(stringResource(Res.string.rd_s10_set_novel)) {
                SettingAnchor("reader_novel_tts") { NovelReaderSettingsSection(settings = state.settings, onUpdate = vm::updateSettings) }
            }
        }
        SettingsCategory.NOTIFICATIONS -> NotificationsCategory(state, vm, actions)
        SettingsCategory.SYSTEM -> SystemCategory()
        SettingsCategory.DATA -> DataCategory(state, vm)
        SettingsCategory.ABOUT -> AboutCategory(state, vm)
        // Drawn by their own pages (SettingsScreen): listed so the `when` stays exhaustive.
        SettingsCategory.APPEARANCE, SettingsCategory.TRANSFERS -> Unit
    }
}

// ==================================================================== Reproducción

@Composable
private fun PlaybackCategory(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    SettingsGroup(stringResource(Res.string.rd_s10_grp_engine)) {
        SettingAnchor("player_engine") {
            SettingRow(stringResource(Res.string.settings_engine), stringResource(s.playerEngine.shown().hintRes()), stack = true, divider = false) {
                Segmented(options = engineOptions(), selected = s.playerEngine.shown(), onSelect = vm::setPlayerEngine, fillWidth = true)
            }
            // What the External engine needs is the platform's (Android: the app to hand the video to; desktop: mpv).
            if (s.playerEngine == PlayerEngine.EXTERNAL) ExternalPlayerSettingsRows(state, vm)
        }
        if (s.playerEngine == PlayerEngine.MPV && mpvTlsSettingShown) {
            SettingAnchor("player_mpv_tls") {
                SwitchRow(
                    label = stringResource(Res.string.settings_mpv_tls_verify),
                    checked = s.mpvTlsVerify,
                    onChange = vm::setMpvTlsVerify,
                    hint = stringResource(Res.string.settings_mpv_tls_verify_hint),
                )
            }
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_grp_controls)) {
        SettingAnchor("player_chrome_style") {
            StructureChoiceRow(
                label = stringResource(Res.string.rd_s10_player_structure),
                hint = stringResource(Res.string.rd_s10_player_structure_hint),
                options = playerStructureOptions(),
                selected = s.look.design.structures.player,
                onSelect = vm::setPlayerStructure,
                divider = false,
            )
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_grp_episodes)) {
        SettingAnchor("player_autonext") {
            SwitchRow(stringResource(Res.string.settings_autonext), s.autoNext, vm::setAutoNext, hint = stringResource(Res.string.settings_autonext_hint), divider = false)
        }
        SettingAnchor("player_skip_intro") {
            SwitchRow(stringResource(Res.string.settings_skip), s.skipIntro, vm::setSkipIntro, hint = stringResource(Res.string.settings_skip_hint))
        }
        // Countdown, stable volume, background playback (lane S6's entry point in :feature:player).
        SettingAnchor("player_playback") { PlayerPlaybackSettings(settings = s, onUpdate = vm::updateSettings) }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_shortcuts)) {
        SettingAnchor("player_shortcuts") { PlayerShortcutSettings(settings = s, onUpdate = vm::updateSettings) }
    }
}

internal fun PlayerEngine.hintRes(): StringResource = when (this) {
    PlayerEngine.MEDIA3 -> Res.string.settings_engine_media3_hint
    PlayerEngine.MPV -> mpvEngineHintRes
    PlayerEngine.EXTERNAL -> externalEngineHintRes
}

/** The stored engine as the selector shows it: one this platform does not offer (Media3 on desktop) plays with libmpv. */
internal fun PlayerEngine.shown(): PlayerEngine = if (this in offeredEngines) this else PlayerEngine.MPV

@Composable
internal fun engineOptions(): List<SegmentedOption<PlayerEngine>> = offeredEngines.map { engine ->
    SegmentedOption(
        engine,
        stringResource(
            when (engine) {
                PlayerEngine.MEDIA3 -> Res.string.settings_engine_media3
                PlayerEngine.MPV -> Res.string.settings_engine_mpv
                PlayerEngine.EXTERNAL -> Res.string.settings_engine_external
            },
        ),
    )
}

// ==================================================================== Mejoras de video

/**
 * The desktop presets (only the mpv engine applies them). ArtCNN is left out of the defaults menu: it needs the Vulkan renderer,
 * which the bundled libmpv does not have (the player lists it disabled with that reason).
 */
private val PRESETS = QualityPresets.ALL - QualityPresets.ARTCNN + extraPresets

@Composable
private fun EnhancementsCategory(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    SettingsGroup(stringResource(Res.string.rd_s10_grp_upscale)) {
        SettingAnchor("player_preset") {
            val presetHint = buildString {
                append(stringResource(platformHint(Res.string.settings_preset_battery)))
                if (s.playerEngine != PlayerEngine.MPV) append(' ').append(stringResource(Res.string.settings_preset_mpv_only))
            }
            SettingRow(stringResource(Res.string.settings_preset), presetHint, stack = true, divider = false) {
                PresetMenu(selected = s.playerPreset, onSelect = vm::setPlayerPreset)
            }
        }
        // Only libmpv has the quality engine; the rows stay hidden for Media3 / External.
        if (s.playerEngine == PlayerEngine.MPV) {
            SettingAnchor("enh_quality") { QualityDefaultsRows(s.playerQuality, vm::setPlayerQuality) }
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_framegen)) {
        SettingAnchor("player_framegen") { FrameGenSettingsSlot() }
    }
}

private fun presetLabelRes(id: String): StringResource = when (id) {
    "anime4k-a" -> Res.string.settings_preset_anime4k_a
    "anime4k-aa" -> Res.string.settings_preset_anime4k_aa
    "anime4k-b" -> Res.string.settings_preset_anime4k_b
    "anime4k-c" -> Res.string.settings_preset_anime4k_c
    "anime4k-bb" -> Res.string.settings_preset_anime4k_bb
    "anime4k-ca" -> Res.string.settings_preset_anime4k_ca
    "fsr" -> Res.string.settings_preset_fsr
    "fidelity" -> Res.string.settings_preset_fidelity
    "cas" -> Res.string.settings_preset_cas
    "nvscaler" -> Res.string.settings_preset_nvscaler
    else -> Res.string.settings_preset_off
}

internal fun presetLabel(id: String): StringResource = presetLabelRes(id)

private fun presetHintRes(id: String): StringResource = when (id) {
    "anime4k-a", "anime4k-aa" -> Res.string.settings_preset_hint_restore
    "anime4k-b" -> Res.string.settings_preset_hint_soft
    "anime4k-c" -> Res.string.settings_preset_hint_clean
    "anime4k-bb" -> Res.string.settings_preset_hint_bb
    "anime4k-ca" -> Res.string.settings_preset_hint_ca
    "fsr" -> Res.string.settings_preset_hint_fsr
    "fidelity" -> Res.string.settings_preset_hint_fidelity
    "cas" -> Res.string.settings_preset_hint_cas
    "nvscaler" -> Res.string.settings_preset_hint_nvscaler
    else -> Res.string.settings_preset_hint_off
}

/** Defaults of the libmpv quality engine under the preset. Each playback starts from these; the player changes them live. */
@Composable
private fun QualityDefaultsRows(q: PlayerQualityPrefs, onChange: ((PlayerQualityPrefs) -> PlayerQualityPrefs) -> Unit) {
    SettingRow(stringResource(Res.string.settings_quality_tier), stringResource(Res.string.settings_quality_tier_hint), stack = true) {
        Segmented(
            options = listOf(
                SegmentedOption(QualityTier.FAST, stringResource(Res.string.settings_quality_tier_fast)),
                SegmentedOption(QualityTier.BALANCED, stringResource(Res.string.settings_quality_tier_balanced)),
                SegmentedOption(QualityTier.HQ, stringResource(Res.string.settings_quality_tier_hq)),
            ),
            selected = q.tier,
            onSelect = { t -> onChange { it.copy(tier = t) } },
            fillWidth = true,
        )
    }
    SwitchRow(
        stringResource(Res.string.settings_deband),
        q.deband.enabled,
        { on -> onChange { it.copy(deband = it.deband.copy(enabled = on)) } },
        hint = stringResource(platformHint(Res.string.settings_deband_hint)),
    )
    SwitchRow(
        stringResource(Res.string.settings_quality_auto),
        q.autoAdjust,
        { on -> onChange { it.copy(autoAdjust = on) } },
        hint = stringResource(platformHint(Res.string.settings_quality_auto_hint)),
    )
    SettingRow(stringResource(Res.string.settings_decoder), stringResource(platformHint(Res.string.settings_decoder_hint)), stack = true) {
        Segmented(
            options = listOf(
                SegmentedOption(DecoderMode.AUTO, stringResource(Res.string.settings_decoder_auto)),
                SegmentedOption(DecoderMode.HW, stringResource(Res.string.settings_decoder_hw)),
                SegmentedOption(DecoderMode.HW_PLUS, stringResource(Res.string.settings_decoder_hw_plus)),
                SegmentedOption(DecoderMode.SW, stringResource(Res.string.settings_decoder_sw)),
            ),
            selected = q.decoder,
            onSelect = { d -> onChange { it.copy(decoder = d) } },
            small = true,
            fillWidth = true,
        )
    }
    DecoderStatusSlot()
    SwitchRow(
        stringResource(Res.string.settings_smoothing),
        q.smoothing,
        { on -> onChange { it.copy(smoothing = on) } },
        hint = stringResource(Res.string.settings_smoothing_hint),
    )
}

/** The shader preset as a dropdown (label + hint per option). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetMenu(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = stringResource(presetLabelRes(selected)),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            supportingText = { Text(stringResource(presetHintRes(selected)), maxLines = 2) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(presetLabelRes(preset)), maxLines = 1)
                            Text(stringResource(presetHintRes(preset)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { open = false; onSelect(preset) },
                )
            }
        }
    }
}

// ==================================================================== Subtítulos y audio

/** The languages offered as one-tap chips; any other one comes from the searchable list. */
internal val COMMON_SUB_LANGS = listOf("es", "en", "pt")
internal val COMMON_DUB_LANGS = listOf("ja", "es", "en")

@Composable
private fun SubtitlesCategory(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    val prefs = s.sourcePrefs
    SettingsGroup(stringResource(Res.string.rd_s10_set_languages)) {
        SettingAnchor("player_audio_sub_lang") {
            LanguagePickerRow(stringResource(Res.string.settings_sub_lang), prefs.subLang, COMMON_SUB_LANGS, vm::setSubLang)
            LanguagePickerRow(stringResource(Res.string.settings_dub_lang), prefs.dubLang, COMMON_DUB_LANGS, vm::setDubLang)
        }
        SettingAnchor("sub_second") { SubtitleLanguageSettings(settings = s, onUpdate = vm::updateSettings) }
    }
    SettingsGroup(stringResource(Res.string.settings_subs)) {
        // The full editor (lane S6) with its own preview; every finished change is saved.
        SettingAnchor("player_sub_style") {
            SubtitleStyleEditor(style = s.subtitleStyle, onChange = vm::setSubtitleStyle, showHeader = false, horizontalPadding = 0.dp)
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_translation)) {
        SettingAnchor("sub_translation") { SubtitleTranslationSettings(settings = s, onUpdate = vm::updateSettings) }
    }
}

// ==================================================================== Fuentes de episodios

@Composable
private fun SourcesCategory(state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions) {
    val s = state.settings
    val prefs = s.sourcePrefs
    SettingsGroup(stringResource(Res.string.rd_s10_grp_selection)) {
        SettingAnchor("player_sources") {
            SettingRow(stringResource(Res.string.settings_source_mode), stringResource(Res.string.settings_source_mode_hint), stack = true, divider = false) {
                Segmented(
                    options = listOf(
                        SegmentedOption(SourceMode.AUTO, stringResource(UiR.string.src_mode_auto)),
                        SegmentedOption(SourceMode.MANUAL, stringResource(UiR.string.src_mode_manual)),
                    ),
                    selected = prefs.mode,
                    onSelect = vm::setSourceMode,
                    fillWidth = true,
                )
            }
            SettingRow(stringResource(Res.string.settings_prefer), stack = true) {
                Segmented(
                    options = listOf<SegmentedOption<SourceKind?>>(
                        SegmentedOption(null, stringResource(Res.string.settings_prefer_any)),
                        SegmentedOption(SourceKind.WEB, stringResource(UiR.string.src_web)),
                        SegmentedOption(SourceKind.TORRENT, stringResource(UiR.string.src_torrent)),
                    ),
                    selected = prefs.prefer,
                    onSelect = vm::setPreferredKind,
                    fillWidth = true,
                )
            }
        }
        SettingAnchor("src_min_quality") {
            SettingRow(stringResource(Res.string.settings_min_quality), stack = true) {
                Segmented(
                    options = listOf(Quality.P480, Quality.P720, Quality.P1080).map { SegmentedOption(it, it.label()) },
                    selected = prefs.minQuality,
                    onSelect = vm::setMinQuality,
                    fillWidth = true,
                )
            }
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_embed)) {
        // The embed sources switch + timeout (:feature:extensions), behind the hidden browser rows on desktop.
        SettingAnchor("player_embed") { EmbedSourcesRows { SourcesSettingsExtras(settings = s, onUpdate = vm::updateSettings) } }
    }
    val net = rememberNetworkController()
    if (net != null) {
        SettingsGroup(stringResource(Res.string.settings_net_flaresolverr), commit = true) {
            SettingAnchor("network_flaresolverr") { FlareSolverrSettings(net) }
        }
    }
    SettingAnchor("src_extensions") {
        NavRow(Icons.Outlined.Extension, stringResource(Res.string.settings_extensions), actions.onOpenExtensions, supporting = stringResource(Res.string.rd_s10_extensions_sub))
    }
}

// ==================================================================== Metadatos y descubrir

private val TTL_HOURS = listOf(6, 24, 72, 168)

@Composable
private fun MetadataCategory(state: SettingsUiState, vm: SettingsViewModel) {
    SettingsGroup(stringResource(Res.string.settings_metadata), hint = stringResource(Res.string.settings_metadata_hint)) {
        SettingAnchor("metadata_providers") { ProviderPriority(state.settings.providerPriority, vm) }
    }
    SettingsGroup(stringResource(Res.string.settings_metadata_cache)) {
        SettingAnchor("metadata_cache") {
            SettingRow(stringResource(Res.string.settings_metadata_cache), cacheSummary(state.storage), divider = false) {
                // Desktop empties only the metadata responses here (Android's row has no button).
                if (metadataCacheClearOffered) {
                    TextButton(onClick = vm::clearMetadataCache, enabled = !state.clearingCache && state.storage is LoadState.Ok) {
                        Text(stringResource(Res.string.settings_metadata_cache_clear))
                    }
                }
            }
        }
        SettingAnchor("metadata_ttl") {
            SettingRow(stringResource(Res.string.settings_metadata_ttl), stringResource(Res.string.settings_metadata_ttl_hint), stack = true) {
                Segmented(
                    options = TTL_HOURS.map { h ->
                        SegmentedOption(h, if (h < 48) stringResource(Res.string.settings_ttl_hours, h) else stringResource(Res.string.settings_ttl_days, h / 24))
                    },
                    selected = state.settings.metadataCacheTtlHours,
                    onSelect = vm::setMetadataTtl,
                    fillWidth = true,
                )
            }
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_discover)) {
        // Title language, 18+ content, clear recent searches (lane S3's rows in :feature:search).
        SettingAnchor("metadata_title_lang", "metadata_adult", "metadata_search_history") {
            DiscoverSettingsExtras(settings = state.settings, onUpdate = vm::updateSettings)
        }
    }
}

/** The provider priority as the reorder pattern: drag the handle, Alt+↑/↓, or the TalkBack actions. */
@Composable
private fun ProviderPriority(priority: List<String>, vm: SettingsViewModel) {
    val reorder = rememberReorderState(onMove = { from, to -> vm.setProviderPriority(priority.moved(from, to)) })
    LazyColumn(
        state = reorder.listState,
        modifier = Modifier.fillMaxWidth().height((priority.size * 60).dp).reorderContainer(reorder),
        userScrollEnabled = false,
    ) {
        itemsIndexed(priority, key = { _, id -> id }) { i, id ->
            ReorderableRow(
                state = reorder,
                index = i,
                count = priority.size,
                title = providerName(id),
                supporting = providerSubRes(id)?.let { stringResource(it) },
                modifier = Modifier.reorderPlacement(),
                leading = {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

@Composable
private fun cacheSummary(storage: LoadState<StorageInfo>): String = when (storage) {
    LoadState.Loading -> stringResource(Res.string.settings_measuring)
    is LoadState.Err -> stringResource(Res.string.settings_unknown_size)
    is LoadState.Ok -> storage.value.cache?.let { stringResource(Res.string.settings_cache_value, formatBytes(it.bytes), it.entries) }
        ?: stringResource(Res.string.settings_unknown_size)
}

/** Proper names of the metadata providers (not translated). */
private fun providerName(id: String): String = when (id) {
    "anilist" -> "AniList"
    "anizip" -> "ani.zip"
    "kitsu" -> "Kitsu"
    "mal" -> "MyAnimeList"
    "shikimori" -> "Shikimori"
    "tenrai" -> "Tenrai"
    else -> id
}

private fun providerSubRes(id: String): StringResource? = when (id) {
    "anilist" -> Res.string.settings_provider_anilist_sub
    "anizip" -> Res.string.settings_provider_anizip_sub
    "kitsu" -> Res.string.settings_provider_kitsu_sub
    else -> null
}

// ==================================================================== Nijiyome Link (Android client)

/**
 * The linked-device entry plus the history sync between the user's devices over Nijiyome Link (`AppSettings.historySyncOverLink`,
 * last sync stamp, "Sincronizar ahora"). Pairing and its state live on the Link screen.
 */
@Composable
internal fun LinkSection(state: SettingsUiState, vm: SettingsViewModel, onOpenLinked: () -> Unit) {
    SectionHint(stringResource(Res.string.settings_link_hint))
    FilledTonalButton(onClick = onOpenLinked) {
        Icon(Icons.Outlined.Computer, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(Res.string.settings_link_open), modifier = Modifier.padding(start = 8.dp))
    }
    // where a normal playback starts; the player switches it any time ("Procesar en")
    SettingAnchor("link_processing") {
        SettingRow(
            label = stringResource(Res.string.settings_link_processing_title),
            hint = stringResource(if (state.linkPaired) Res.string.settings_link_processing_hint else Res.string.settings_link_processing_unpaired),
            stack = true,
        ) {
            Segmented(
                options = app.nijiyome.model.ProcessingLocation.entries.map { loc ->
                    SegmentedOption(
                        value = loc,
                        label = stringResource(
                            when (loc) {
                                app.nijiyome.model.ProcessingLocation.PHONE -> Res.string.settings_link_processing_phone
                                app.nijiyome.model.ProcessingLocation.PC -> Res.string.settings_link_processing_pc
                                app.nijiyome.model.ProcessingLocation.MIXED -> Res.string.settings_link_processing_mixed
                            },
                        ),
                        enabled = state.linkPaired,
                    )
                },
                selected = state.linkDefaultProcessing,
                onSelect = vm::setLinkDefaultProcessing,
                fillWidth = true,
            )
        }
    }
    SettingAnchor("link_history") {
        SubHeading(stringResource(Res.string.settings_link_history_title))
        SectionHint(stringResource(Res.string.settings_link_history_hint))
        SwitchRow(
            label = stringResource(Res.string.settings_link_history_switch),
            checked = state.settings.historySyncOverLink,
            onChange = { on -> vm.updateSettings { it.copy(historySyncOverLink = on) } },
        )
        Text(
            when {
                !state.linkPaired -> stringResource(Res.string.settings_link_history_unpaired)
                state.lastSyncedAt != null -> stringResource(Res.string.settings_account_synced, relativeTime(state.lastSyncedAt))
                else -> stringResource(Res.string.settings_account_never)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = vm::syncNow,
            enabled = state.linkPaired && state.settings.historySyncOverLink && !state.syncing,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (state.syncing) {
                LoadingIndicator(Modifier.size(20.dp))
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Text(stringResource(if (state.syncing) Res.string.settings_syncing else Res.string.settings_sync_now), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ==================================================================== Torrents y descargas

/** Torrents keeps its own settings page (lane S8, scrolls by itself); Debrid opens Fuentes › Debrid. */
@Composable
internal fun TransfersLinks(onOpenDebrid: () -> Unit) {
    SettingAnchor("torrents_debrid") {
        NavRow(
            Icons.Outlined.CloudDone,
            stringResource(Res.string.settings_torrents_debrid),
            onOpenDebrid,
            supporting = stringResource(Res.string.settings_torrents_debrid_sub),
        )
    }
}

@Composable
internal fun DownloadsGroup(state: SettingsUiState, vm: SettingsViewModel) {
    SettingsGroup(stringResource(Res.string.rd_s10_set_downloads), icon = Icons.Outlined.Download) {
        SettingAnchor("downloads_web") { DownloadsSettingsSection(settings = state.settings, onUpdate = vm::updateSettings) }
    }
}

// ==================================================================== Notificaciones

@Composable
private fun NotificationsCategory(state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions) {
    SettingsGroup(stringResource(Res.string.rd_s10_cat_notifications)) {
        if (notificationsNeedPermission) {
            SettingAnchor("notif_permission") { OnboardingNotifications() }
        }
        SettingAnchor("metadata_notifications") {
            SwitchRow(
                label = stringResource(Res.string.rd_s10_notif_airing),
                checked = state.settings.airingReminders,
                onChange = vm::setAiringReminders,
                hint = stringResource(Res.string.rd_s10_notif_airing_hint),
                divider = notificationsNeedPermission,
            )
            val openCalendar = actions.onOpenCalendar
            if (openCalendar != null) {
                NavRow(Icons.Outlined.CalendarMonth, stringResource(Res.string.rd_s10_notif_calendar), openCalendar, supporting = stringResource(Res.string.rd_s10_notif_calendar_sub))
            }
        }
        SettingAnchor("notif_anilist") {
            SwitchRow(
                label = stringResource(Res.string.rd_s10_notif_anilist),
                checked = state.settings.socialNotifications,
                onChange = vm::setSocialNotifications,
                hint = stringResource(Res.string.rd_s10_notif_anilist_hint),
            )
        }
    }
}

// ==================================================================== Sistema (desktop)

@Composable
private fun SystemCategory() {
    SettingsGroup(stringResource(Res.string.rd_s10_set_system_rows)) {
        SettingAnchor("data_desktop_system") { PlatformSystemRows() }
    }
    val net = rememberNetworkController()
    if (net != null) {
        SettingsGroup(stringResource(Res.string.rd_s10_grp_network), hint = stringResource(Res.string.settings_net_hint), commit = true) {
            SettingAnchor("network_proxy") { ProxySettings(net) }
        }
        SettingsGroup(stringResource(Res.string.settings_net_local_proxy)) {
            SettingAnchor("network_media_proxy") { MediaProxyRow(net) }
        }
    }
}

// ==================================================================== Datos y almacenamiento

@Composable
private fun DataCategory(state: SettingsUiState, vm: SettingsViewModel) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(stringResource(Res.string.rd_s10_set_storage)) {
        SettingAnchor("data_storage") {
            SectionHint(dataLocationHint())
            DataFolderRow()
            when (val storage = state.storage) {
                LoadState.Loading -> if (rememberAfterDelay(150)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(12.dp), shape = CircleShape)
                    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
                }
                is LoadState.Err -> ErrorState(
                    message = storage.cause.userMessage(),
                    retryLabel = stringResource(UiR.string.ui_retry),
                    onRetry = vm::refreshStorage,
                    title = stringResource(Res.string.settings_storage_error),
                    compact = true,
                )
                is LoadState.Ok -> StorageBreakdown(storage.value, state.extraStorage)
            }
            SettingRow(stringResource(Res.string.settings_telemetry), stringResource(Res.string.settings_telemetry_hint)) {
                Pill(stringResource(Res.string.settings_telemetry_none), tone = PillTone.OK)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { confirmClear = true }, enabled = !state.clearingCache) {
                    if (state.clearingCache) {
                        LoadingIndicator(Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Text(stringResource(Res.string.settings_clear_cache), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    SettingsGroup(stringResource(Res.string.rd_s10_set_reset)) {
        // Exportar historial, Borrar historial and Restablecer la app (confirmations inside).
        SettingAnchor("data_reset") { ResetAppRow(vm) }
    }
    if (confirmClear) {
        val bytes = (state.storage as? LoadState.Ok)?.value?.clearableBytes
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_clear_cache)) },
            text = {
                Text(
                    if (bytes != null) stringResource(Res.string.settings_clear_cache_body, formatBytes(bytes)) else stringResource(Res.string.settings_clear_cache_body_unknown),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        vm.clearCache()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.settings_clear_cache)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(UiR.string.ui_cancel)) } },
        )
    }
}

/** Storage bar: metadata cache, torrents, other folders, free space; figures in the mono font. */
@Composable
private fun StorageBreakdown(info: StorageInfo, extra: List<ExtraStorageItem> = emptyList()) {
    val cache = info.clearableBytes ?: 0L
    val torrents = info.torrents?.usedBytes ?: 0L
    val others = extra.filterNot { it.inTorrents }.sumOf { it.bytes }
    val free = info.torrents?.freeBytes ?: 0L
    val total = (cache + torrents + others + free).coerceAtLeast(1L)
    val cacheColor = MaterialTheme.colorScheme.primary
    val torrentColor = MaterialTheme.colorScheme.tertiary
    val otherColor = MaterialTheme.colorScheme.secondary
    val freeColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(freeColor)) {
        if (cache > 0) Box(Modifier.weight(cache.toFloat() / total).height(12.dp).background(cacheColor))
        if (torrents > 0) Box(Modifier.weight(torrents.toFloat() / total).height(12.dp).background(torrentColor))
        if (others > 0) Box(Modifier.weight(others.toFloat() / total).height(12.dp).background(otherColor))
        val rest = (total - cache - torrents - others).coerceAtLeast(0L)
        if (rest > 0) Box(Modifier.weight(rest.toFloat() / total).height(12.dp))
    }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow(cacheColor, stringResource(Res.string.settings_data_cache), info.cache?.let { formatBytes(it.bytes) })
        LegendRow(cacheColor, stringResource(Res.string.settings_data_images), info.imageBytes?.let { formatBytes(it) })
        LegendRow(torrentColor, stringResource(Res.string.settings_data_torrents), info.torrents?.let { formatBytes(it.usedBytes) })
        extra.forEach { item -> LegendRow(if (item.inTorrents) torrentColor else otherColor, stringResource(item.kind.labelRes()), formatBytes(item.bytes)) }
        LegendRow(freeColor, stringResource(Res.string.settings_data_free), info.torrents?.let { formatBytes(it.freeBytes) })
        info.torrents?.location?.let { location ->
            Text(
                stringResource(if (location is StorageLocation.UserFolder) Res.string.settings_data_location_folder else Res.string.settings_data_location_app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value ?: stringResource(Res.string.settings_unknown_size), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
    }
}

private fun ExtraStorageItem.Kind.labelRes(): StringResource = when (this) {
    ExtraStorageItem.Kind.WEB_DOWNLOADS -> Res.string.settings_data_web_downloads
    ExtraStorageItem.Kind.LOGS -> Res.string.settings_data_logs
    ExtraStorageItem.Kind.EXTENSIONS -> Res.string.settings_data_extensions
}

// ==================================================================== Acerca de

@Composable
private fun AboutCategory(state: SettingsUiState, vm: SettingsViewModel) {
    val system: SystemViewModel = koinViewModel()
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val versionParts = rememberAppVersionParts()
    SettingsGroup(stringResource(Res.string.settings_about), hint = stringResource(platformHint(Res.string.settings_about_body))) {
        AboutRow("Nijiyome", versionParts.filter(String::isNotBlank).joinToString(" · "))
        componentVersions.forEach { (name, v) -> AboutRow(name, v) }
        AboutRow(stringResource(Res.string.settings_about_license), "AGPL-3.0-or-later")
        // GitHub Releases of Kushro/Nijiyome, at most once a day on its own.
        if (system.hasUpdates) {
            SettingAnchor("about_updates") {
                UpdateCheckRow(
                    system = system,
                    updateCheckEnabled = state.settings.updateCheck,
                    onToggle = { on -> vm.updateSettings { it.copy(updateCheck = on) } },
                )
            }
        }
        SettingAnchor("about_licenses", "about_logs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = { showLicenses = true }) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(Res.string.settings_licenses), modifier = Modifier.padding(start = 8.dp))
                }
                if (system.hasLogs) {
                    TextButton(onClick = { showLogs = true }) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(Res.string.settings_logs), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (showLicenses) LicensesSheet(onDismiss = { showLicenses = false })
    if (showLogs) LogsSheet(system = system, onDismiss = { showLogs = false })
}

@Composable
private fun AboutRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono), modifier = Modifier.weight(1.6f))
    }
}

/** The bundled third-party notices and font licenses ([rememberLicensesLoader]). */
@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val loadLicenses = rememberLicensesLoader()
    val retryKey = rememberSaveable { mutableStateOf(0) }
    val notices by produceState<LoadState<List<String>>>(LoadState.Loading, retryKey.value) {
        value = loadLicenses()
    }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Text(stringResource(Res.string.settings_licenses), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        when (val n = notices) {
            LoadState.Loading -> if (rememberAfterDelay(150)) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            is LoadState.Err -> ErrorState(message = n.cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = { retryKey.value++ })
            is LoadState.Ok -> if (n.value.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Description, title = stringResource(Res.string.settings_licenses_missing))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(n.value) { paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodySmall.copy(fontFamily = NijiyomeFonts.jetBrainsMono))
                    }
                }
            }
        }
    }
}

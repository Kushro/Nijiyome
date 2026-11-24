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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ClosedCaption
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.generated.PaletteInfo
import app.nijiyome.designsystem.generated.NijiyomeTokens
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.extensions.SourcesSettingsExtras
import app.nijiyome.feature.player.PlayerSettingsExtras
import app.nijiyome.feature.player.SubtitleStyleSheet
import app.nijiyome.model.AppLanguage
import app.nijiyome.model.ContrastPref
import app.nijiyome.model.LoadState
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.PlayerQualityPrefs
import app.nijiyome.model.Quality
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.torrent.StorageLocation
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.state.userMessage
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// ==================================================================== Apariencia

@Composable
internal fun AppearanceSection(state: SettingsUiState, vm: SettingsViewModel) {
    val s = state.settings
    SettingRow(stringResource(Res.string.settings_palette), stringResource(Res.string.settings_palette_hint), stack = true, divider = false) {
        LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(NijiyomeTokens.paletteInfo, key = { it.id }, contentType = { "palette" }) { info ->
                PaletteSwatch(
                    info,
                    selected = info.id == s.paletteId && !(s.dynamicColor && dynamicColorSupported),
                    onClick = { vm.setPalette(info.id) },
                )
            }
        }
    }
    if (dynamicColorSupported) {
        SwitchRow(stringResource(Res.string.settings_dynamic), s.dynamicColor, vm::setDynamicColor, hint = stringResource(Res.string.settings_dynamic_hint))
    }
    SettingRow(stringResource(Res.string.settings_mode), stack = true) {
        Segmented(
            options = listOf(
                SegmentedOption(ThemeMode.SYSTEM, stringResource(Res.string.settings_mode_system)),
                SegmentedOption(ThemeMode.LIGHT, stringResource(Res.string.settings_mode_light)),
                SegmentedOption(ThemeMode.DARK, stringResource(Res.string.settings_mode_dark)),
            ),
            selected = s.themeMode,
            onSelect = vm::setThemeMode,
        )
    }
    SwitchRow(stringResource(Res.string.settings_amoled), s.amoled, vm::setAmoled, hint = stringResource(Res.string.settings_amoled_hint))
    SettingRow(stringResource(Res.string.settings_contrast), stack = true) {
        Segmented(
            options = ContrastPref.entries.map { SegmentedOption(it, stringResource(it.labelRes())) },
            selected = s.contrast,
            onSelect = vm::setContrast,
        )
    }
    SettingRow(stringResource(Res.string.settings_language), stack = true) {
        Segmented(
            options = listOf(
                SegmentedOption(AppLanguage.SYSTEM, stringResource(Res.string.settings_lang_system)),
                SegmentedOption(AppLanguage.EN, stringResource(Res.string.settings_lang_en)),
                SegmentedOption(AppLanguage.ES, stringResource(Res.string.settings_lang_es)),
            ),
            selected = s.language,
            onSelect = vm::setLanguage,
        )
    }
    SwitchRow(
        stringResource(Res.string.settings_reduce_motion),
        s.reduceMotion,
        vm::setReduceMotion,
        hint = stringResource(Res.string.settings_reduce_motion_hint),
    )
    // 2026-11 UI refresh: Diseño y estilo (presets + every option).
    LookGroup(s.look, onChange = vm::setLook, onPreset = vm::applyLookPreset)
}

private fun ContrastPref.labelRes(): StringResource = when (this) {
    ContrastPref.STANDARD -> Res.string.settings_contrast_standard
    ContrastPref.MEDIUM -> Res.string.settings_contrast_medium
    ContrastPref.HIGH -> Res.string.settings_contrast_high
}

@Composable
private fun PaletteSwatch(info: PaletteInfo, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(info.seed)
                .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(info.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

// ==================================================================== Reproductor

/**
 * 0.4.0: the desktop presets (only the mpv engine applies them). ArtCNN is left out of the defaults menu: it needs the Vulkan renderer,
 * which the bundled libmpv does not have (the player lists it disabled with that reason).
 */
private val PRESETS = QualityPresets.ALL - QualityPresets.ARTCNN + extraPresets

@Composable
internal fun PlayerSection(state: SettingsUiState, vm: SettingsViewModel, onOpenExtensions: () -> Unit) {
    val s = state.settings
    val prefs = s.sourcePrefs
    var editingSubs by rememberSaveable { mutableStateOf(false) }

    SettingRow(stringResource(Res.string.settings_engine), stringResource(s.playerEngine.shown().hintRes()), stack = true, divider = false) {
        Segmented(
            options = engineOptions(),
            selected = s.playerEngine.shown(),
            onSelect = vm::setPlayerEngine,
            // BUG-069: three short options share the width ("App exter…" was cut by the scrolling row).
            fillWidth = true,
        )
    }
    // Hito 4, paso D: what the External engine needs is the platform's (Android: the app to hand the video to; desktop: mpv).
    if (s.playerEngine == PlayerEngine.EXTERNAL) ExternalPlayerSettingsRows(state, vm)
    val presetHint = buildString {
        append(stringResource(platformHint(Res.string.settings_preset_battery)))
        if (s.playerEngine != PlayerEngine.MPV) append(' ').append(stringResource(Res.string.settings_preset_mpv_only))
    }
    SettingRow(stringResource(Res.string.settings_preset), presetHint, stack = true) {
        // BUG-069 (UX-10): six presets do not fit a segmented row (it ran past the card); a menu with each preset's hint.
        PresetMenu(selected = s.playerPreset, onSelect = vm::setPlayerPreset)
    }
    // 0.4.0: only libmpv has the quality engine; the rows stay hidden for Media3 / External so the section does not grow for nothing.
    if (s.playerEngine == PlayerEngine.MPV) QualityDefaultsRows(s.playerQuality, vm::setPlayerQuality)
    // v1.1: off by default; see AppSettings.mpvTlsVerify for why.
    if (s.playerEngine == PlayerEngine.MPV && mpvTlsSettingShown) {
        SwitchRow(
            label = stringResource(Res.string.settings_mpv_tls_verify),
            checked = s.mpvTlsVerify,
            onChange = vm::setMpvTlsVerify,
            hint = stringResource(Res.string.settings_mpv_tls_verify_hint),
        )
    }
    SettingRow(stringResource(Res.string.settings_source_mode), stringResource(Res.string.settings_source_mode_hint), stack = true) {
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
            small = true,
        )
    }
    SettingRow(stringResource(Res.string.settings_min_quality), stack = true) {
        Segmented(
            options = listOf(Quality.P480, Quality.P720, Quality.P1080).map { SegmentedOption(it, it.label()) },
            selected = prefs.minQuality,
            onSelect = vm::setMinQuality,
            small = true,
        )
    }
    SettingRow(stringResource(Res.string.settings_sub_lang), stack = true) {
        Segmented(options = langOptions(listOf("es", "en", "pt")), selected = prefs.subLang, onSelect = vm::setSubLang, small = true)
    }
    SettingRow(stringResource(Res.string.settings_dub_lang), stack = true) {
        Segmented(options = langOptions(listOf("ja", "es", "en")), selected = prefs.dubLang, onSelect = vm::setDubLang, small = true)
    }
    SwitchRow(stringResource(Res.string.settings_autonext), s.autoNext, vm::setAutoNext, hint = stringResource(Res.string.settings_autonext_hint))
    SwitchRow(stringResource(Res.string.settings_skip), s.skipIntro, vm::setSkipIntro, hint = stringResource(Res.string.settings_skip_hint))
    // parity wave A (15 §3.2): worker p's rows (translation, secondary subtitle, countdown, stable volume, background playback, shortcuts).
    PlayerSettingsExtras(settings = s, onUpdate = vm::updateSettings)
    // parity wave B (16 §3.3, worker r): embed sources (sniffer) switch + timeout; rows owned by r in :feature:extensions.
    EmbedSourcesRows { SourcesSettingsExtras(settings = s, onUpdate = vm::updateSettings) }
    SettingRow(stringResource(Res.string.settings_subs), subtitleSummary(s.subtitleStyle)) {
        FilledTonalButton(onClick = { editingSubs = true }) {
            Icon(Icons.Outlined.ClosedCaption, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(Res.string.settings_subs_edit), modifier = Modifier.padding(start = 8.dp))
        }
    }
    NavRow(Icons.Outlined.Extension, stringResource(Res.string.settings_extensions), onOpenExtensions)
    if (editingSubs) {
        SubtitleStyleSheet(style = s.subtitleStyle, onChange = vm::setSubtitleStyle, onDismiss = { editingSubs = false })
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

/**
 * 0.4.0 (worker A): defaults of the libmpv quality engine under the preset (tier, deband, automatic adjustments, decoder, smoothing).
 * Each playback starts from these; the player changes them live for that playback.
 */
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

/** ISO 639-1 codes shown as upper-case codes (technical, not translated). */
internal fun langOptions(codes: List<String>): List<SegmentedOption<String>> =
    codes.map { SegmentedOption(it, it.uppercase(Locale.ROOT)) }

@Composable
private fun subtitleSummary(style: SubtitleStyle): String = stringResource(
    Res.string.settings_subs_summary,
    style.fontFile?.substringBeforeLast('.') ?: stringResource(fontLabelRes(style.font)),
    (style.scale * 100).roundToInt(),
    // 0.4.1: the box has its own switch; "off" reads as 0 % like before.
    if (style.background) (style.backgroundOpacity * 100).roundToInt() else 0,
)

private fun fontLabelRes(font: String): StringResource = when (font) {
    "space_grotesk" -> Res.string.settings_subs_font_space_grotesk
    "system" -> Res.string.settings_subs_font_system
    else -> Res.string.settings_subs_font_outfit
}
// ==================================================================== Metadatos

private val TTL_HOURS = listOf(6, 24, 72, 168)

@Composable
internal fun MetadataSection(state: SettingsUiState, vm: SettingsViewModel) {
    val priority = state.settings.providerPriority
    SectionHint(stringResource(Res.string.settings_metadata_hint))
    priority.forEachIndexed { index, id ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(providerName(id), style = MaterialTheme.typography.bodyLarge)
                providerSubRes(id)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { vm.moveProvider(id, -1) }, enabled = index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(Res.string.settings_move_up_named, providerName(id)))
            }
            IconButton(onClick = { vm.moveProvider(id, 1) }, enabled = index < priority.lastIndex) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.settings_move_down_named, providerName(id)))
            }
        }
    }
    SettingRow(stringResource(Res.string.settings_metadata_cache), cacheSummary(state.storage)) {
        // Hito 6 (D1): desktop empties only the metadata responses here (Android's row has no button).
        if (metadataCacheClearOffered) {
            TextButton(onClick = vm::clearMetadataCache, enabled = !state.clearingCache && state.storage is LoadState.Ok) {
                Text(stringResource(Res.string.settings_metadata_cache_clear))
            }
        }
    }
    SettingRow(stringResource(Res.string.settings_metadata_ttl), stringResource(Res.string.settings_metadata_ttl_hint), stack = true) {
        Segmented(
            options = TTL_HOURS.map { h ->
                SegmentedOption(
                    h,
                    if (h < 48) stringResource(Res.string.settings_ttl_hours, h) else stringResource(Res.string.settings_ttl_days, h / 24),
                )
            },
            selected = state.settings.metadataCacheTtlHours,
            onSelect = vm::setMetadataTtl,
            small = true,
        )
    }
    // parity wave A (15 §3.1): worker o's rows (title language, 18+ content, clear recent searches); :feature:search, Android only.
    // parity wave A (15 §3.1): worker o's rows (title language, 18+ content, clear recent searches).
    DiscoverSettingsExtras(settings = state.settings, onUpdate = vm::updateSettings)
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
    else -> id
}

private fun providerSubRes(id: String): StringResource? = when (id) {
    "anilist" -> Res.string.settings_provider_anilist_sub
    "anizip" -> Res.string.settings_provider_anizip_sub
    "kitsu" -> Res.string.settings_provider_kitsu_sub
    else -> null
}

// ==================================================================== Enlace / Torrents

/**
 * Enlace: the linked-device entry plus, since the 2026-11-09 amendment (no own login), the history sync between the user's devices over
 * Nijiyome Link (`AppSettings.historySyncOverLink`, last sync stamp, "Sincronizar ahora"). Owner: worker q.
 */
@Composable
internal fun LinkSection(state: SettingsUiState, vm: SettingsViewModel, onOpenLinked: () -> Unit) {
    SectionHint(stringResource(Res.string.settings_link_hint))
    FilledTonalButton(onClick = onOpenLinked) {
        Icon(Icons.Outlined.Computer, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(Res.string.settings_link_open), modifier = Modifier.padding(start = 8.dp))
    }
    // 0.4.0 (worker C): where a normal playback starts; the player switches it any time ("Procesar en")
    SettingRow(
        label = stringResource(Res.string.settings_link_processing_title),
        hint = stringResource(if (state.linkPaired) Res.string.settings_link_processing_hint else Res.string.settings_link_processing_unpaired),
        stack = true,
    ) {
        app.nijiyome.designsystem.components.Segmented(
            options = app.nijiyome.model.ProcessingLocation.entries.map { loc ->
                app.nijiyome.designsystem.components.SegmentedOption(
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
    Text(
        stringResource(Res.string.settings_link_history_title),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 16.dp),
    )
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
        Text(
            stringResource(if (state.syncing) Res.string.settings_syncing else Res.string.settings_sync_now),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun TorrentsSection(onOpenTorrents: () -> Unit) {
    SectionHint(stringResource(Res.string.settings_torrents_hint))
    NavRow(
        Icons.Outlined.Download,
        stringResource(Res.string.settings_torrents_open),
        onOpenTorrents,
        supporting = stringResource(Res.string.settings_torrents_open_sub),
    )
}

// ==================================================================== Datos y almacenamiento

@Composable
internal fun DataSection(state: SettingsUiState, vm: SettingsViewModel) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
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
    PlatformSystemRows()
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
    if (confirmClear) {
        val bytes = (state.storage as? LoadState.Ok)?.value?.clearableBytes
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.settings_clear_cache)) },
            text = {
                Text(
                    if (bytes != null) {
                        stringResource(Res.string.settings_clear_cache_body, formatBytes(bytes))
                    } else {
                        stringResource(Res.string.settings_clear_cache_body_unknown)
                    },
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
    // parity wave A, 2026-11-09 amendment (worker q): Exportar historial, Borrar historial and Restablecer la app live here.
    ResetAppRow(vm)
}

/** Storage bar (prototype `.storage__bar`): metadata cache, torrents, free space; figures in the mono font. */
@Composable
private fun StorageBreakdown(info: StorageInfo, extra: List<ExtraStorageItem> = emptyList()) {
    // metadata table + image disk cache (both go with "Limpiar caché"), shown as one slice and two legend rows
    val cache = info.clearableBytes ?: 0L
    val torrents = info.torrents?.usedBytes ?: 0L
    // Hito 6 (D1): what only desktop measures (web downloads, logs, extensions) as one more slice; empty on Android.
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
                stringResource(
                    if (location is StorageLocation.UserFolder) Res.string.settings_data_location_folder else Res.string.settings_data_location_app,
                ),
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
        Text(
            value ?: stringResource(Res.string.settings_unknown_size),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NijiyomeFonts.jetBrainsMono),
        )
    }
}

// ==================================================================== Acerca de


@Composable
internal fun AboutSection(state: SettingsUiState, vm: SettingsViewModel) {
    val system: SystemViewModel = koinViewModel()
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val versionParts = rememberAppVersionParts()
    SectionHint(stringResource(platformHint(Res.string.settings_about_body)))
    AboutRow("Nijiyome", versionParts.filter(String::isNotBlank).joinToString(" · "))
    componentVersions.forEach { (name, v) -> AboutRow(name, v) }
    AboutRow(stringResource(Res.string.settings_about_license), "AGPL-3.0-or-later")
    // parity wave A (worker q, checklist system.update-check): GitHub Releases of Kushro/Nijiyome, at most once a day on its own.
    if (system.hasUpdates) {
        UpdateCheckRow(
            system = system,
            updateCheckEnabled = state.settings.updateCheck,
            onToggle = { on -> vm.updateSettings { it.copy(updateCheck = on) } },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        FilledTonalButton(onClick = { showLicenses = true }) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(Res.string.settings_licenses), modifier = Modifier.padding(start = 8.dp))
        }
        // parity wave A (worker q, checklist system.open-logs)
        if (system.hasLogs) {
            TextButton(onClick = { showLogs = true }) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(Res.string.settings_logs), modifier = Modifier.padding(start = 8.dp))
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
        Text(
            stringResource(Res.string.settings_licenses),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val n = notices) {
            LoadState.Loading -> if (rememberAfterDelay(150)) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            is LoadState.Err -> ErrorState(
                message = n.cause.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = { retryKey.value++ },
            )
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

/** BUG-069: the shader preset as a dropdown (label + hint per option) instead of a segmented row that did not fit. */
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
                            Text(
                                stringResource(presetHintRes(preset)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { open = false; onSelect(preset) },
                )
            }
        }
    }
}

private fun ExtraStorageItem.Kind.labelRes(): StringResource = when (this) {
    ExtraStorageItem.Kind.WEB_DOWNLOADS -> Res.string.settings_data_web_downloads
    ExtraStorageItem.Kind.LOGS -> Res.string.settings_data_logs
    ExtraStorageItem.Kind.EXTENSIONS -> Res.string.settings_data_extensions
}

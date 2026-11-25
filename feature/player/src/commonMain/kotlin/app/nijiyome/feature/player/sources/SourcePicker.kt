// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.player.sources

import app.nijiyome.designsystem.components.HorizontalScrollStrip
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.designsystem.components.horizontalFadingEdge
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import org.koin.compose.getKoin
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.SettingsRepository
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SIDE_SHEET_MIN_WIDTH_DP
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.model.AppError
import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.player.CodecCaps
import app.nijiyome.feature.player.deviceCodecCaps
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.player.decodesInHardware
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// android wave contract (docs/redesign/12 §3.11). Owner: worker d; also called by worker c (Details). Names and signatures FIXED.

private enum class KindTab { ALL, WEB, TORRENT }

private val FiltersSaver = listSaver<SourceFilters, String>(save = { it.encode() }, restore = { SourceFilters.decode(it) })

/**
 * Source selector (07 §3.4, 11 §3.6): `ModalBottomSheet` on compact, side sheet (AdaptiveSheet) elsewhere. Banner with the current
 * [mode] + link to Settings ([onOpenSettings]), segmented All / Web / Torrent, horizontally scrolling FilterChips (BD/WEB/REMUX,
 * resolution, codec, 10-bit, Dual audio, Multi-sub, Batch, SeaDex best, min reliability, "Compatible with this device"), list in RANKED
 * order (filtering never reorders), filter state local (`rememberSaveable`). Loading / empty / error states inside.
 * [onDownload] non-null adds the secondary "Descargar" action to torrent rows (Details passes it; the player passes null).
 * [rememberedPick] (BUG-140): the user's remembered pick for this episode ([RankedSource.key]); when the list does not have it the sheet
 * says so ([missingPickLabel]) instead of showing the recommended row in silence.
 */
@Composable
fun SourcePickerSheet(
    sources: List<RankedSource>,
    loading: Boolean,
    error: AppError?,
    mode: SourceMode,
    onPick: (RankedSource) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: ((RankedSource) -> Unit)? = null,
    rememberedPick: String? = null,
) {
    val wide = windowWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    if (wide) {
        // AdaptiveSheet scrolls its content itself (a nested vertical scroll would crash).
        AdaptiveSheet(onDismissRequest = onDismiss, modifier = modifier) {
            PickerContent(sources, loading, error, mode, onPick, onRetry, onOpenSettings, onDownload, rememberedPick)
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
            TrackModal()
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                PickerContent(sources, loading, error, mode, onPick, onRetry, onOpenSettings, onDownload, rememberedPick)
            }
        }
    }
}

@Composable
private fun PickerContent(
    sources: List<RankedSource>,
    loading: Boolean,
    error: AppError?,
    mode: SourceMode,
    onPick: (RankedSource) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownload: ((RankedSource) -> Unit)?,
    rememberedPick: String?,
) {
    var filters by rememberSaveable(stateSaver = FiltersSaver) { mutableStateOf(SourceFilters()) }
    val caps = remember { deviceCodecCaps() }
    val scopedSources = remember(sources, filters.kind) {
        if (filters.kind == null) sources else sources.filter { it.candidate.kind == filters.kind }
    }
    val options = remember(scopedSources, caps) { FilterOptions.of(scopedSources, caps) }
    // BUG-018 (plan §11.1): the earlier choice is preselected at the top; the rest keeps the ranking order.
    val shown = remember(sources, filters, caps) { filters.apply(sources, caps).sortedByDescending { it.picked } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.src_picker_title), style = MaterialTheme.typography.titleLarge)
        ModeBanner(mode = mode, choosing = mode == SourceMode.MANUAL, onOpenSettings = onOpenSettings)

        when {
            loading && sources.isEmpty() -> PickerLoading()
            error != null && sources.isEmpty() -> ErrorState(
                message = error.userMessage(),
                retryLabel = stringResource(UiR.string.ui_retry),
                onRetry = onRetry,
                title = stringResource(Res.string.src_picker_error_title),
            )
            sources.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Extension,
                title = stringResource(Res.string.src_picker_empty_title),
                message = stringResource(Res.string.src_picker_empty_body),
                actionLabel = stringResource(UiR.string.ui_retry),
                onAction = onRetry,
            )
            else -> {
                Segmented(
                    options = listOf(
                        SegmentedOption(KindTab.ALL, stringResource(Res.string.src_filter_all)),
                        SegmentedOption(KindTab.WEB, stringResource(UiR.string.src_web), icon = Icons.Rounded.Language),
                        SegmentedOption(KindTab.TORRENT, stringResource(UiR.string.src_torrent), icon = Icons.Rounded.Hub),
                    ),
                    selected = when (filters.kind) {
                        null -> KindTab.ALL
                        SourceKind.WEB -> KindTab.WEB
                        SourceKind.TORRENT -> KindTab.TORRENT
                    },
                    onSelect = { tab ->
                        filters = filters.copy(
                            kind = when (tab) {
                                KindTab.ALL -> null
                                KindTab.WEB -> SourceKind.WEB
                                KindTab.TORRENT -> SourceKind.TORRENT
                            },
                        )
                    },
                    small = true,
                )
                FilterBar(filters, options, onChange = { filters = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.src_filter_count, shown.size, sources.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (filters.active() > 0) {
                        TextButton(onClick = { filters = filters.cleared() }) { Text(stringResource(Res.string.src_filter_clear)) }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                // BUG-140: the remembered pick did not come up this time: say it, never check another row in its place.
                // BUG-142: only once the list is complete (while it loads the pick may still arrive, so nothing is said).
                val missing = remember(sources, rememberedPick, loading) { missingPickLabel(sources, rememberedPick, loading) }
                if (missing != null) {
                    Text(
                        stringResource(Res.string.src_pick_missing, missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (shown.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.FilterListOff,
                        title = stringResource(Res.string.src_filter_empty_title),
                        message = stringResource(Res.string.src_filter_empty_body),
                        actionLabel = stringResource(Res.string.src_filter_clear),
                        onAction = { filters = SourceFilters() },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { s ->
                            SourceRowContent(
                                source = s,
                                onClick = { onPick(s) },
                                onDownload = if (onDownload != null && s.candidate.kind == SourceKind.TORRENT) ({ onDownload(s) }) else null,
                                caps = caps,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeBanner(mode: SourceMode, choosing: Boolean, onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (mode == SourceMode.MANUAL) Icons.Rounded.Tune else Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (choosing) {
                    stringResource(Res.string.src_picker_manual_hint)
                } else {
                    stringResource(
                        Res.string.src_picker_mode_line,
                        stringResource(if (mode == SourceMode.MANUAL) UiR.string.src_mode_manual else UiR.string.src_mode_auto),
                        stringResource(Res.string.src_picker_failover),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            ) { Text(stringResource(Res.string.src_picker_settings)) }
        }
    }
}

@Composable
private fun PickerLoading() {
    val show = rememberAfterDelay(150)
    if (!show) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(Res.string.src_picker_loading), style = MaterialTheme.typography.bodyMedium)
        }
        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(96.dp)) }
    }
}

@Composable
private fun FilterBar(filters: SourceFilters, options: FilterOptions, onChange: (SourceFilters) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val advancedCount = filters.activeAdvanced()
    val scroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalScrollStrip(scrollState = scroll, showChevrons = true) {
            Row(
                Modifier.fillMaxWidth().horizontalFadingEdge(scroll).horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (options.debridCached || filters.debridOnly) {
                    FilterChip(
                        selected = filters.debridOnly,
                        onClick = { onChange(filters.copy(debridOnly = !filters.debridOnly)) },
                        leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(Res.string.src_filter_debrid)) },
                    )
                }
                val visibleModes = (options.audioModes + filters.audioModes).distinct()
                visibleModes.forEach { mode ->
                    val labelRes = when (mode) {
                        AudioFilter.SUB -> Res.string.src_filter_sub
                        AudioFilter.DUB -> Res.string.src_filter_dub
                        AudioFilter.DUAL_AUDIO -> Res.string.src_tag_dual_audio
                        AudioFilter.MULTI_DUB -> Res.string.src_filter_multi_dub
                    }
                    FilterChip(
                        selected = mode in filters.audioModes || (mode == AudioFilter.DUAL_AUDIO && filters.dualAudio),
                        onClick = {
                            if (mode == AudioFilter.DUAL_AUDIO && filters.dualAudio) {
                                onChange(filters.copy(dualAudio = false, audioModes = filters.audioModes - AudioFilter.DUAL_AUDIO))
                            } else {
                                onChange(filters.copy(audioModes = filters.audioModes.toggled(mode)))
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
                if (options.multiSub || filters.multiSub) {
                    FilterChip(
                        selected = filters.multiSub,
                        onClick = { onChange(filters.copy(multiSub = !filters.multiSub)) },
                        label = { Text(stringResource(Res.string.src_tag_multi_sub)) },
                    )
                }
                options.resolutions.forEach { q ->
                    FilterChip(
                        selected = q in filters.resolutions,
                        onClick = { onChange(filters.copy(resolutions = filters.resolutions.toggled(q))) },
                        label = { Text(q.label()) },
                    )
                }
                FilterChip(
                    selected = filters.minReliability == RelFloor.MID,
                    onClick = { onChange(filters.toggleFloor(RelFloor.MID)) },
                    label = { Text(stringResource(Res.string.src_filter_rel_mid)) },
                )
                FilterChip(
                    selected = filters.minReliability == RelFloor.HIGH,
                    onClick = { onChange(filters.toggleFloor(RelFloor.HIGH)) },
                    label = { Text(stringResource(Res.string.src_filter_rel_high)) },
                )
                if (options.hasAdvanced || advancedCount > 0) {
                    FilterChip(
                        selected = expanded || advancedCount > 0,
                        onClick = { expanded = !expanded },
                        trailingIcon = {
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Text(
                                if (advancedCount > 0) stringResource(Res.string.src_filter_more_count, advancedCount)
                                else stringResource(Res.string.src_filter_more),
                            )
                        },
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val audioLangList = (options.audioLangs + filters.audioLangs).distinct().sorted()
                    if (audioLangList.isNotEmpty()) {
                        FilterGroupLabel(stringResource(Res.string.src_filter_section_audio))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            audioLangList.forEach { code ->
                                FilterChip(
                                    selected = code in filters.audioLangs,
                                    onClick = { onChange(filters.copy(audioLangs = filters.audioLangs.toggled(code))) },
                                    label = { Text(code) },
                                )
                            }
                        }
                    }
                    val subLangList = (options.subLangs + filters.subLangs).distinct().sorted()
                    if (subLangList.isNotEmpty()) {
                        FilterGroupLabel(stringResource(Res.string.src_filter_section_subs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            subLangList.forEach { code ->
                                FilterChip(
                                    selected = code in filters.subLangs,
                                    onClick = { onChange(filters.copy(subLangs = filters.subLangs.toggled(code))) },
                                    label = { Text(code) },
                                )
                            }
                        }
                    }
                    val hasVideoChips = options.sources.isNotEmpty() || options.codecs.isNotEmpty() ||
                        options.tenBit || options.batch || options.seadexBest ||
                        options.deviceIncompatible || filters.deviceCompatible ||
                        filters.sources.isNotEmpty() || filters.codecs.isNotEmpty() ||
                        filters.tenBit || filters.batch || filters.seadexBest
                    if (hasVideoChips) {
                        FilterGroupLabel(stringResource(Res.string.src_filter_section_video))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (options.sources + filters.sources).distinct().forEach { src ->
                                FilterChip(
                                    selected = src in filters.sources,
                                    onClick = { onChange(filters.copy(sources = filters.sources.toggled(src))) },
                                    label = { Text(src.label()) },
                                )
                            }
                            (options.codecs + filters.codecs).distinct().forEach { c ->
                                FilterChip(
                                    selected = c in filters.codecs,
                                    onClick = { onChange(filters.copy(codecs = filters.codecs.toggled(c))) },
                                    label = { Text(c.label()) },
                                )
                            }
                            if (options.tenBit || filters.tenBit) {
                                FilterChip(
                                    selected = filters.tenBit,
                                    onClick = { onChange(filters.copy(tenBit = !filters.tenBit)) },
                                    label = { Text(stringResource(Res.string.src_tag_bit_depth, 10)) },
                                )
                            }
                            if (options.batch || filters.batch) {
                                FilterChip(
                                    selected = filters.batch,
                                    onClick = { onChange(filters.copy(batch = !filters.batch)) },
                                    label = { Text(stringResource(Res.string.src_tag_batch)) },
                                )
                            }
                            if (options.seadexBest || filters.seadexBest) {
                                FilterChip(
                                    selected = filters.seadexBest,
                                    onClick = { onChange(filters.copy(seadexBest = !filters.seadexBest)) },
                                    label = { Text(stringResource(Res.string.src_tag_seadex_best)) },
                                )
                            }
                            if (options.deviceIncompatible || filters.deviceCompatible) {
                                FilterChip(
                                    selected = filters.deviceCompatible,
                                    onClick = { onChange(filters.copy(deviceCompatible = !filters.deviceCompatible)) },
                                    label = { Text(stringResource(Res.string.src_filter_device)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One source row (07 §3.4 `SourceRow`): kind icon, `Ext · Server/Group`, pills Webstream/Torrent, Recomendada, Ext desactivada, tags as
 * small AssistChips (SeaDex best = primaryContainer), key-value facts (web: server, quality+container, latency, subs, dubs; torrent:
 * group, quality+codec, size, seeders, leechers, subs, dubs, hash), reliability bar (PillTone OK/WARN/ERR). Used by the picker and by
 * the Details source summary panel (worker c).
 */
@Composable
fun SourceRow(
    source: RankedSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDownload: (() -> Unit)? = null,
) {
    val caps = remember { deviceCodecCaps() }
    SourceRowContent(source, onClick, modifier, compact, onDownload, caps)
}

@Composable
private fun SourceRowContent(
    source: RankedSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDownload: (() -> Unit)? = null,
    caps: CodecCaps,
) {
    val c = source.candidate
    val enabled = source.extEnabled
    val torrent = c.kind == SourceKind.TORRENT
    val scheme = MaterialTheme.colorScheme
    // BUG-017 (plan §11.2): no hardware decoder for this video = software decoding, "may be slow on this device".
    val compatible = decodesInHardware(c.tags, caps)
    Surface(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (source.picked) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (source.picked) scheme.onSecondaryContainer else scheme.onSurface,
        border = if (source.recommended) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    // BUG-054: the type icon is not the download action's arrow (the "Descargar" button below keeps it).
                    if (torrent) Icons.Rounded.Hub else Icons.Rounded.Language,
                    contentDescription = stringResource(if (torrent) UiR.string.src_torrent else UiR.string.src_web),
                    tint = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "${c.extName} · ${c.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (source.picked) {
                    Icon(Icons.Rounded.CheckCircle, stringResource(Res.string.src_picker_picked), tint = scheme.primary, modifier = Modifier.size(18.dp))
                }
                if (!compatible) {
                    Icon(Icons.Rounded.WarningAmber, stringResource(Res.string.src_picker_incompatible), tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(stringResource(if (torrent) UiR.string.src_torrent else UiR.string.src_web), plain = true)
                if (source.recommended) Pill(stringResource(UiR.string.src_recommended), tone = PillTone.PRIMARY)
                // 15 §3.9: torrent cached by a linked debrid service (plays as a direct HTTPS stream).
                c.debrid?.takeIf { it.cached }?.let { d -> Pill(stringResource(Res.string.src_tag_debrid_cached, d.service.label), tone = PillTone.PRIMARY) }
                if (!enabled) Pill(stringResource(UiR.string.src_ext_off, c.extName), tone = PillTone.ERR, plain = true)
                c.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            val chips = tagChips(source)
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { chip -> TagChipView(chip) }
                }
            }
            if (!compact) {
                Facts(source)
                // BUG-087: no raw info-hash on the card (desktop does not show it either).
                if (!compatible) {
                    Text(stringResource(Res.string.src_picker_incompatible), style = MaterialTheme.typography.bodySmall, color = scheme.tertiary)
                }
            }
            ReliabilityBar(c.reliability)
            if (onDownload != null && torrent) {
                TextButton(onClick = onDownload, enabled = enabled) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.src_picker_download))
                }
            }
        }
    }
}

@Composable
private fun TagChipView(chip: TagChip) {
    val scheme = MaterialTheme.colorScheme
    val text = when (chip) {
        TagChip.SeadexBest -> stringResource(Res.string.src_tag_seadex_best)
        is TagChip.Group -> chip.name
        is TagChip.Code -> chip.text
        is TagChip.BitDepth -> stringResource(Res.string.src_tag_bit_depth, chip.bits)
        TagChip.DualAudio -> stringResource(Res.string.src_tag_dual_audio)
        TagChip.MultiDub -> stringResource(Res.string.src_tag_multi_dub)
        TagChip.MultiSub -> stringResource(Res.string.src_tag_multi_sub)
        is TagChip.Batch -> if (chip.from != null && chip.to != null) {
            stringResource(Res.string.src_tag_batch_range, chip.from, chip.to)
        } else {
            stringResource(Res.string.src_tag_batch)
        }
    }
    // BUG-066: the tags only describe the source; the card is what you tap. A Pill is not a button for touch or TalkBack.
    val tone = when (chip) {
        TagChip.SeadexBest -> PillTone.OK
        is TagChip.Batch -> PillTone.WARN
        else -> PillTone.NEUTRAL
    }
    Pill(text, tone = tone)
}

/**
 * BUG-065 (UX-06): the facts of a source in one or two lines of body text (`anizone · 720p HLS · Subs ES-419 · EN +12 · Doblaje JA`),
 * instead of a label-over-value grid in monospace that made each card ~9 lines tall on a phone. Languages are short codes with the
 * preferred one first (as on the desktop).
 */
@Composable
private fun Facts(source: RankedSource) {
    val c = source.candidate
    val koin = getKoin()
    val prefs = remember { koin.getOrNull<SettingsRepository>()?.settings?.value?.sourcePrefs } ?: SourcePrefs()
    val parts = mutableListOf<String>()
    if (c.kind == SourceKind.WEB) {
        parts += c.label
        // parity wave B (t, contract §3.3): an embed page shows the translated "Embed" instead of the raw container label.
        val container = if (c.isEmbed) stringResource(UiR.string.src_embed) else c.container
        val quality = listOfNotNull(c.quality.takeIf { it != Quality.UNKNOWN }?.label(), container).joinToString(" ").trim()
        if (quality.isNotEmpty()) parts += quality
        c.web?.latencyMs?.let { parts += stringResource(Res.string.src_picker_latency_ms, it) }
    } else {
        c.torrent?.sizeBytes?.let { parts += formatBytes(it) }
        c.torrent?.let {
            // Unknown seeders (SubsPlease's feed, BUG-030) omit the count instead of claiming 0.
            it.seeders?.let { n -> parts += stringResource(Res.string.src_picker_seeders_short, n) }
            parts += stringResource(Res.string.src_picker_leechers_short, it.leechers)
        }
    }
    // BUG-004: no language list is not "none": a "Multi-sub" release has several, and otherwise the source simply did not say.
    val unknown = stringResource(Res.string.src_picker_unknown)
    val several = stringResource(Res.string.src_picker_several)
    val subs = langsShort(c.subs, prefs.subLang) ?: if (c.tags.multiSub) several else unknown
    val dubs = langsShort(c.dubs, prefs.dubLang) ?: if (c.tags.dualAudio) several else unknown
    parts += stringResource(Res.string.src_picker_subs_inline, subs)
    parts += stringResource(Res.string.src_picker_dubs_inline, dubs)
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            contentDescription = parts.dropLast(2).joinToString(", ") +
                ", ${c.subs.joinToString(", ").ifEmpty { subs }}, ${c.dubs.joinToString(", ").ifEmpty { dubs }}"
        },
    )
}

/**
 * Ficha "Primero el episodio": the one-line summary of the Details source line (`Toko · anizone · 1080p · Subs ES`): extension,
 * server / group, quality and the subtitles (the same short codes as [Facts]); nothing about the languages when the source did not say.
 */
@Composable
fun sourceSummaryLine(source: RankedSource): String {
    val c = source.candidate
    val koin = getKoin()
    val prefs = remember { koin.getOrNull<SettingsRepository>()?.settings?.value?.sourcePrefs } ?: SourcePrefs()
    val parts = mutableListOf<String>()
    c.debrid?.takeIf { it.cached }?.let { parts += stringResource(Res.string.src_tag_debrid_cached, it.service.label) }
    parts += c.extName
    parts += c.label
    c.quality.takeIf { it != Quality.UNKNOWN }?.label()?.let { parts += it }
    val subs = langsShort(c.subs, prefs.subLang) ?: if (c.tags.multiSub) stringResource(Res.string.src_picker_several) else null
    subs?.let { parts += stringResource(Res.string.src_picker_subs_inline, it) }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun ReliabilityBar(reliability: Float) {
    val scheme = MaterialTheme.colorScheme
    val tone = reliabilityTone(reliability)
    val color = when (tone) {
        RelTone.HIGH -> scheme.primary
        RelTone.MID -> scheme.tertiary
        RelTone.LOW -> scheme.error
    }
    val label = stringResource(
        when (tone) {
            RelTone.HIGH -> UiR.string.src_rel_high
            RelTone.MID -> UiR.string.src_rel_mid
            RelTone.LOW -> UiR.string.src_rel_low
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { reliability.coerceIn(0f, 1f) },
            modifier = Modifier.width(72.dp),
            color = color,
            trackColor = scheme.surfaceContainerHighest,
        )
        Text(
            stringResource(Res.string.src_picker_reliability, label, (reliability * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

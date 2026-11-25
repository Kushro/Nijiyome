// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.nijiyome.feature.player.sources

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import org.koin.compose.getKoin
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.SettingsRepository
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.SIDE_SHEET_MIN_WIDTH_DP
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.model.AppError
import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import app.nijiyome.model.ReleaseSource
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.SourcePickerStructure
import app.nijiyome.model.VideoCodec
import app.nijiyome.player.CodecCaps
import app.nijiyome.feature.player.deviceCodecCaps
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.patterns.Facet
import app.nijiyome.ui.patterns.FacetBar
import app.nijiyome.ui.patterns.FacetOption
import app.nijiyome.ui.patterns.FacetSelection
import app.nijiyome.ui.patterns.RuleChipRow
import app.nijiyome.ui.patterns.RuleOption
import app.nijiyome.ui.patterns.RuleSummary
import app.nijiyome.ui.patterns.SourceChoiceBadge
import app.nijiyome.ui.patterns.WhyNotRow
import app.nijiyome.ui.patterns.with
import app.nijiyome.ui.platform.windowWidthDp
import app.nijiyome.player.decodesInHardware
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.state.userMessage
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import kotlinx.coroutines.launch

// android wave contract (docs/redesign/12 §3.11). Owner: worker d; also called by worker c (Details). Names and signatures FIXED.
// Sparkles redesign, lane S4 (docs/redesign/artefacto/screens-a.js `fuentes-episodio`): the sheet in two structures chosen in
// Ajustes › Apariencia (`structures.sourcePicker`). Facetas = one row of facet chips with counts, a sort and the list grouped by kind;
// Comparador = the same facets over a sortable table that marks the best value of each column. Both keep the automatic / manual mode,
// every filter of before, "why this source" and the automatic rules as chips.

private val FiltersSaver = listSaver<SourceFilters, String>(save = { it.encode() }, restore = { SourceFilters.decode(it) })

/**
 * Source selector (07 §3.4, 11 §3.6): `ModalBottomSheet` on compact, side sheet (AdaptiveSheet) elsewhere. Mode (automatic / manual,
 * stored in Ajustes › Fuentes de episodios) with a link to Settings ([onOpenSettings]), the automatic rules as chips, the facets
 * (kind, audio, languages, resolution, codec, origin, extras, reliability, cached on debrid), the sort, and the list or the table.
 * Filtering never reorders: only the sort does. Loading / no sources / extension error states inside.
 * [onDownload] non-null adds "Descargar para después" to torrent rows (Details passes it; the player passes null).
 * [rememberedPick] (BUG-140): the user's remembered pick for this episode ([RankedSource.key]); when the list does not have it the sheet
 * says so ([missingPickLabel]) instead of showing the recommended row in silence.
 * [onOpenExtensions] non-null adds "Administrar extensiones" to the no-sources and error states; [episode] titles the sheet.
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
    onOpenExtensions: (() -> Unit)? = null,
    episode: Int? = null,
) {
    val wide = windowWidthDp() >= SIDE_SHEET_MIN_WIDTH_DP
    val content: @Composable () -> Unit = {
        PickerContent(sources, loading, error, mode, onPick, onDismiss, onRetry, onOpenSettings, onDownload, rememberedPick, onOpenExtensions, episode)
    }
    if (wide) {
        // AdaptiveSheet scrolls its content itself (a nested vertical scroll would crash).
        AdaptiveSheet(onDismissRequest = onDismiss, modifier = modifier.decor(DecorRegion.SHEET)) { content() }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.decor(DecorRegion.SHEET)) {
            TrackModal()
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) { content() }
        }
    }
}

/** The automatic rules (Ajustes › Fuentes de episodios) read live and written back from the sheet; read-only without settings. */
internal class SourceRules(val prefs: SourcePrefs, val editable: Boolean, val set: (SourcePrefs) -> Unit)

@Composable
internal fun rememberSourceRules(mode: SourceMode): SourceRules {
    val koin = getKoin()
    val repo = remember(koin) { koin.getOrNull<SettingsRepository>() }
    val scope = rememberCoroutineScope()
    if (repo == null) return SourceRules(SourcePrefs(mode = mode), editable = false) {}
    val settings by repo.settings.collectAsState()
    return SourceRules(settings.sourcePrefs, editable = true) { p -> scope.launch { runCatching { repo.setSourcePrefs(p) } } }
}

/** Everything both structures share, computed once at the root. */
private class PickerModel(
    val sources: List<RankedSource>,
    val shown: List<RankedSource>,
    val facets: List<Facet>,
    val selection: FacetSelection,
    val rules: SourceRules,
    val caps: CodecCaps,
    val options: FilterOptions,
)

@Composable
private fun PickerContent(
    sources: List<RankedSource>,
    loading: Boolean,
    error: AppError?,
    mode: SourceMode,
    onPick: (RankedSource) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDownload: ((RankedSource) -> Unit)?,
    rememberedPick: String?,
    onOpenExtensions: (() -> Unit)?,
    episode: Int?,
) {
    var filters by rememberSaveable(stateSaver = FiltersSaver) { mutableStateOf(SourceFilters()) }
    var sort by rememberSaveable { mutableStateOf(SourceSort.RECOMMENDED) }
    val caps = remember { deviceCodecCaps() }
    val rules = rememberSourceRules(mode)
    val options = remember(sources, caps) { FilterOptions.of(sources, caps) }
    val selection = filters.toSelection()
    val facets = sourceFacets(sources, options, filters, caps)
    val shown = remember(sources, filters, caps, sort) { sortSources(filters.apply(sources, caps), sort) }
    val model = PickerModel(sources, shown, facets, selection, rules, caps, options)
    val onSelection: (FacetSelection) -> Unit = { filters = filters.withSelection(it) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerHeader(episode, onDismiss)
        ModeBlock(rules, mode, onOpenSettings)
        when {
            loading && sources.isEmpty() -> PickerLoading()
            error != null && sources.isEmpty() -> ExtensionError(error, onRetry, onOpenExtensions)
            sources.isEmpty() -> NoSources(onRetry, onOpenExtensions)
            else -> {
                ChoiceNote(model)
                if (error != null) PartialError(error, onRetry)
                RulesBlock(model)
                DebridAndFacets(model, filters, onFilters = { filters = it }, onSelection = onSelection)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                // BUG-140 / BUG-142: the remembered pick did not come up this time (said only once the list is complete).
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
                val onClear = { filters = filters.cleared().copy(kind = null) }
                when (structures.sourcePicker) {
                    SourcePickerStructure.FACETS -> FacetsResults(model, sort, onSort = { sort = it }, filters.active() + (if (filters.kind != null) 1 else 0), onClear, onPick, onDownload)
                    SourcePickerStructure.COMPARE -> CompareResults(model, filters.active() + (if (filters.kind != null) 1 else 0), onClear, onPick, onDownload)
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(episode: Int?, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.rd_s4_src_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            episode?.let {
                Text(stringResource(Res.string.rd_s4_src_episode, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.rd_s4_src_close)) }
    }
}

/** Automática / Manual (the stored mode), what it means, and Ajustes. */
@Composable
private fun ModeBlock(rules: SourceRules, mode: SourceMode, onOpenSettings: () -> Unit) {
    val current = if (rules.editable) rules.prefs.mode else mode
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(
                options = listOf(
                    SegmentedOption(SourceMode.AUTO, stringResource(Res.string.rd_s4_src_auto), enabled = rules.editable),
                    SegmentedOption(SourceMode.MANUAL, stringResource(Res.string.rd_s4_src_manual), enabled = rules.editable),
                ),
                selected = current,
                onSelect = { rules.set(rules.prefs.copy(mode = it)) },
                small = true,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.src_picker_settings), modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(
            stringResource(if (current == SourceMode.MANUAL) Res.string.rd_s4_src_hint_manual else Res.string.rd_s4_src_hint_auto),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Why this source": who chose it and, for the system, which rules it meets. */
@Composable
private fun ChoiceNote(model: PickerModel) {
    val chosen = chosenSource(model.sources) ?: return
    val name = "${chosen.candidate.extName} · ${chosen.candidate.label}"
    val manual = chosen.picked
    val text = if (manual) {
        stringResource(Res.string.rd_s4_src_chosen_by_you, name)
    } else {
        val reasons = whyChosen(chosen, model.rules.prefs)
        if (reasons.isEmpty()) stringResource(Res.string.rd_s4_src_chosen_by_system_plain, name)
        else stringResource(Res.string.rd_s4_src_chosen_by_system, name, reasons.joinToString(", "))
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChoiceBadge(manual = manual)
            RuleSummary(text)
        }
    }
}

/** The rules [s] meets, in words ("en caché en TorBox", "1080p", "subtítulos ES", "confiabilidad 92 %"). */
@Composable
private fun whyChosen(s: RankedSource, prefs: SourcePrefs): List<String> {
    val c = s.candidate
    return buildList {
        c.debrid?.takeIf { it.cached }?.let { add(stringResource(Res.string.rd_s4_src_why_cached, it.service.label)) }
        if (c.quality != Quality.UNKNOWN && c.quality.rank() >= prefs.minQuality.rank()) add(c.quality.label())
        if (prefs.prefer != null && c.kind == prefs.prefer) add(stringResource(if (c.kind == SourceKind.TORRENT) UiR.string.src_torrent else UiR.string.src_web))
        val sub = langCode(prefs.subLang)
        if (sub.isNotEmpty() && c.subs.any { langCode(it).startsWith(sub) }) add(stringResource(Res.string.rd_s4_src_why_subs, sub))
        val dub = langCode(prefs.dubLang)
        if (dub.isNotEmpty() && c.dubs.any { langCode(it).startsWith(dub) }) add(stringResource(Res.string.rd_s4_src_why_dub, dub))
        add(stringResource(Res.string.rd_s4_src_why_rel, (c.reliability * 100).toInt()))
    }
}

/** The rules [s] misses, in words (shown under the rows the system passes over). */
@Composable
internal fun whyNot(s: RankedSource, prefs: SourcePrefs): List<String> = ruleMisses(s, prefs).map { miss ->
    when (miss) {
        RuleMiss.EXTENSION_OFF -> stringResource(UiR.string.src_ext_off, s.candidate.extName)
        RuleMiss.BELOW_QUALITY -> stringResource(Res.string.rd_s4_src_fail_quality, prefs.minQuality.label())
        RuleMiss.OTHER_KIND -> stringResource(Res.string.rd_s4_src_fail_kind, stringResource(if (prefs.prefer == SourceKind.TORRENT) UiR.string.src_torrent else UiR.string.src_web))
        RuleMiss.NO_PREFERRED_SUBS -> stringResource(Res.string.rd_s4_src_fail_subs, langCode(prefs.subLang))
        RuleMiss.LOW_RELIABILITY -> stringResource(Res.string.rd_s4_src_fail_rel)
    }
}

/** The automatic rules as chips (preferred kind, minimum quality, subtitle and audio language); open by default in Automatic. */
@Composable
private fun RulesBlock(model: PickerModel) {
    val rules = model.rules
    if (!rules.editable) return
    var open by rememberSaveable { mutableStateOf(rules.prefs.mode == SourceMode.AUTO) }
    val prefs = rules.prefs
    val expandedState = stringResource(if (open) Res.string.rd_s4_src_expanded else Res.string.rd_s4_src_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }.semantics {
                stateDescription = expandedState
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.rd_s4_src_rules), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (!open) return@Column
        RuleChipRow(
            label = stringResource(Res.string.rd_s4_src_rule_kind),
            options = listOf(
                RuleOption("any", stringResource(Res.string.rd_s4_src_rule_any)),
                RuleOption("web", stringResource(UiR.string.src_web)),
                RuleOption("torrent", stringResource(UiR.string.src_torrent)),
            ),
            selected = setOf(prefs.prefer?.let { if (it == SourceKind.TORRENT) "torrent" else "web" } ?: "any"),
            single = true,
            onToggle = { id ->
                rules.set(prefs.copy(prefer = when (id) { "web" -> SourceKind.WEB; "torrent" -> SourceKind.TORRENT; else -> null }))
            },
        )
        RuleChipRow(
            label = stringResource(Res.string.rd_s4_src_rule_quality),
            options = listOf(Quality.P480, Quality.P720, Quality.P1080).map { RuleOption(it.name, it.label()) },
            selected = setOf(prefs.minQuality.name),
            single = true,
            onToggle = { id -> Quality.entries.firstOrNull { it.name == id }?.let { rules.set(prefs.copy(minQuality = it)) } },
        )
        val subCodes = (model.options.subLangs + langCode(prefs.subLang)).filter { it.isNotEmpty() }.distinct()
        if (subCodes.isNotEmpty()) {
            RuleChipRow(
                label = stringResource(Res.string.rd_s4_src_rule_subs),
                options = subCodes.map { RuleOption(it, it) },
                selected = setOf(langCode(prefs.subLang)),
                single = true,
                onToggle = { rules.set(prefs.copy(subLang = it.lowercase())) },
            )
        }
        val dubCodes = (model.options.audioLangs + langCode(prefs.dubLang)).filter { it.isNotEmpty() }.distinct()
        if (dubCodes.isNotEmpty()) {
            RuleChipRow(
                label = stringResource(Res.string.rd_s4_src_rule_dub),
                options = dubCodes.map { RuleOption(it, it) },
                selected = setOf(langCode(prefs.dubLang)),
                single = true,
                onToggle = { rules.set(prefs.copy(dubLang = it.lowercase())) },
            )
        }
    }
}

// ---------------------------------------------------------------- facets

private const val F_KIND = "kind"
private const val F_AUDIO = "audio"
private const val F_ALANG = "alang"
private const val F_SLANG = "slang"
private const val F_RES = "res"
private const val F_CODEC = "codec"
private const val F_ORIGIN = "origin"
private const val F_EXTRAS = "extras"
private const val F_REL = "rel"
private const val X_TEN_BIT = "tenBit"
private const val X_BATCH = "batch"
private const val X_SEADEX = "seadex"
private const val X_DEVICE = "device"
private const val X_MULTI_SUB = "multiSub"

/** [SourceFilters] as facet picks (the debrid chip stays apart, as in the artifact). */
private fun SourceFilters.toSelection(): FacetSelection = buildMap {
    kind?.let { put(F_KIND, setOf(it.name)) }
    if (audioModes.isNotEmpty() || dualAudio) put(F_AUDIO, audioModes.map { it.name }.toSet() + (if (dualAudio) setOf(AudioFilter.DUAL_AUDIO.name) else emptySet()))
    if (audioLangs.isNotEmpty()) put(F_ALANG, audioLangs)
    if (subLangs.isNotEmpty() || multiSub) put(F_SLANG, subLangs + (if (multiSub) setOf(X_MULTI_SUB) else emptySet()))
    if (resolutions.isNotEmpty()) put(F_RES, resolutions.map { it.name }.toSet())
    if (codecs.isNotEmpty()) put(F_CODEC, codecs.map { it.name }.toSet())
    if (sources.isNotEmpty()) put(F_ORIGIN, sources.map { it.name }.toSet())
    val extras = buildSet {
        if (tenBit) add(X_TEN_BIT)
        if (batch) add(X_BATCH)
        if (seadexBest) add(X_SEADEX)
        if (deviceCompatible) add(X_DEVICE)
    }
    if (extras.isNotEmpty()) put(F_EXTRAS, extras)
    minReliability?.let { put(F_REL, setOf(it.name)) }
}

/** The facet picks back into [SourceFilters] (the debrid flag is kept). */
private fun SourceFilters.withSelection(sel: FacetSelection): SourceFilters {
    fun ids(f: String) = sel[f].orEmpty()
    val audio = ids(F_AUDIO)
    val slang = ids(F_SLANG)
    val extras = ids(F_EXTRAS)
    return copy(
        kind = ids(F_KIND).firstOrNull()?.let { id -> SourceKind.entries.firstOrNull { it.name == id } },
        audioModes = audio.mapNotNull { id -> AudioFilter.entries.firstOrNull { it.name == id } }.toSet(),
        dualAudio = false,
        audioLangs = ids(F_ALANG),
        subLangs = slang - X_MULTI_SUB,
        multiSub = X_MULTI_SUB in slang,
        resolutions = ids(F_RES).mapNotNull { id -> Quality.entries.firstOrNull { it.name == id } }.toSet(),
        codecs = ids(F_CODEC).mapNotNull { id -> VideoCodec.entries.firstOrNull { it.name == id } }.toSet(),
        sources = ids(F_ORIGIN).mapNotNull { id -> ReleaseSource.entries.firstOrNull { it.name == id } }.toSet(),
        tenBit = X_TEN_BIT in extras,
        batch = X_BATCH in extras,
        seadexBest = X_SEADEX in extras,
        deviceCompatible = X_DEVICE in extras,
        minReliability = ids(F_REL).firstOrNull()?.let { id -> RelFloor.entries.firstOrNull { it.name == id } },
    )
}

/** The facets that can match something in [sources], each option with how many sources it would leave with the other facets. */
@Composable
private fun sourceFacets(sources: List<RankedSource>, options: FilterOptions, filters: SourceFilters, caps: CodecCaps): List<Facet> {
    val sel = filters.toSelection()
    fun count(facet: String, option: String, single: Boolean): Int {
        val picked = if (single) setOf(option) else setOf(option)
        val f = filters.withSelection(sel - facet + (facet to picked))
        return sources.count { f.matches(it, caps) }
    }
    fun opts(facet: String, single: Boolean, list: List<Pair<String, String>>) = list.map { (id, label) -> FacetOption(id, label, count(facet, id, single)) }
    val kindWeb = stringResource(UiR.string.src_web)
    val kindTorrent = stringResource(UiR.string.src_torrent)
    val audioLabels = mapOf(
        AudioFilter.SUB to stringResource(Res.string.src_filter_sub),
        AudioFilter.DUB to stringResource(Res.string.src_filter_dub),
        AudioFilter.DUAL_AUDIO to stringResource(Res.string.src_tag_dual_audio),
        AudioFilter.MULTI_DUB to stringResource(Res.string.src_filter_multi_dub),
    )
    val tenBit = stringResource(Res.string.src_tag_bit_depth, 10)
    val batch = stringResource(Res.string.src_tag_batch)
    val seadex = stringResource(Res.string.src_tag_seadex_best)
    val device = stringResource(Res.string.src_filter_device)
    val multiSub = stringResource(Res.string.src_tag_multi_sub)
    val relMid = stringResource(Res.string.src_filter_rel_mid)
    val relHigh = stringResource(Res.string.src_filter_rel_high)
    val labels = FacetLabels(
        kind = stringResource(Res.string.rd_s4_src_f_kind),
        audio = stringResource(Res.string.rd_s4_src_f_audio),
        audioLang = stringResource(Res.string.rd_s4_src_f_audio_lang),
        subs = stringResource(Res.string.rd_s4_src_f_subs),
        res = stringResource(Res.string.rd_s4_src_f_res),
        codec = stringResource(Res.string.rd_s4_src_f_codec),
        origin = stringResource(Res.string.rd_s4_src_f_origin),
        extras = stringResource(Res.string.rd_s4_src_f_extras),
        rel = stringResource(Res.string.rd_s4_src_f_rel),
    )
    return remember(sources, options, filters, caps, labels) {
        buildList {
            val kinds = SourceKind.entries.filter { k -> sources.any { it.candidate.kind == k } }
            if (kinds.size > 1) add(Facet(F_KIND, labels.kind, opts(F_KIND, true, kinds.map { it.name to if (it == SourceKind.WEB) kindWeb else kindTorrent }), single = true))
            val modes = (options.audioModes + filters.audioModes).distinct()
            if (modes.isNotEmpty()) add(Facet(F_AUDIO, labels.audio, opts(F_AUDIO, false, modes.map { it.name to audioLabels.getValue(it) })))
            val alangs = (options.audioLangs + filters.audioLangs).distinct().sorted()
            if (alangs.isNotEmpty()) add(Facet(F_ALANG, labels.audioLang, opts(F_ALANG, false, alangs.map { it to it })))
            val slangs = (options.subLangs + filters.subLangs).distinct().sorted().map { it to it } +
                (if (options.multiSub || filters.multiSub) listOf(X_MULTI_SUB to multiSub) else emptyList())
            if (slangs.isNotEmpty()) add(Facet(F_SLANG, labels.subs, opts(F_SLANG, false, slangs)))
            val res = (options.resolutions + filters.resolutions).distinct().sortedByDescending { it.rank() }
            if (res.isNotEmpty()) add(Facet(F_RES, labels.res, opts(F_RES, false, res.map { it.name to it.label() })))
            val codecs = (options.codecs + filters.codecs).distinct()
            if (codecs.isNotEmpty()) add(Facet(F_CODEC, labels.codec, opts(F_CODEC, false, codecs.map { it.name to it.label() })))
            val origins = (options.sources + filters.sources).distinct()
            if (origins.isNotEmpty()) add(Facet(F_ORIGIN, labels.origin, opts(F_ORIGIN, false, origins.map { it.name to it.label() })))
            val extras = buildList {
                if (options.tenBit || filters.tenBit) add(X_TEN_BIT to tenBit)
                if (options.batch || filters.batch) add(X_BATCH to batch)
                if (options.seadexBest || filters.seadexBest) add(X_SEADEX to seadex)
                if (options.deviceIncompatible || filters.deviceCompatible) add(X_DEVICE to device)
            }
            if (extras.isNotEmpty()) add(Facet(F_EXTRAS, labels.extras, opts(F_EXTRAS, false, extras)))
            add(Facet(F_REL, labels.rel, opts(F_REL, true, listOf(RelFloor.MID.name to relMid, RelFloor.HIGH.name to relHigh)), single = true))
        }
    }
}

private data class FacetLabels(
    val kind: String,
    val audio: String,
    val audioLang: String,
    val subs: String,
    val res: String,
    val codec: String,
    val origin: String,
    val extras: String,
    val rel: String,
)

/** "Solo en caché debrid" (when a source is cached) and the facet row with the result count. */
@Composable
private fun DebridAndFacets(model: PickerModel, filters: SourceFilters, onFilters: (SourceFilters) -> Unit, onSelection: (FacetSelection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.options.debridCached || filters.debridOnly) {
            FilterChip(
                selected = filters.debridOnly,
                onClick = { onFilters(filters.copy(debridOnly = !filters.debridOnly)) },
                leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(Res.string.rd_s4_src_debrid_only)) },
            )
        }
        FacetBar(
            facets = model.facets,
            selection = model.selection,
            onSelectionChange = onSelection,
            resultCount = model.shown.size,
            resultText = stringResource(Res.string.rd_s4_src_count, model.shown.size, model.sources.size),
        )
    }
}

@Composable
private fun CountRow(model: PickerModel, active: Int, onClear: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.rd_s4_src_count, model.shown.size, model.sources.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (active > 0) TextButton(onClick = onClear) { Text(stringResource(Res.string.rd_s4_src_clear_n, active)) }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun NoMatch(onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().decor(DecorRegion.EMPTY).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EmptyArt(EmptyKind.SEARCH)
        Text(stringResource(Res.string.src_filter_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.src_filter_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onClear) { Text(stringResource(Res.string.src_filter_clear)) }
    }
}

// ---------------------------------------------------------------- Facetas

/** Facetas: count, clear and sort, then the list grouped by kind (web and torrent apart) while the order is the ranking. */
@Composable
private fun FacetsResults(
    model: PickerModel,
    sort: SourceSort,
    onSort: (SourceSort) -> Unit,
    active: Int,
    onClear: () -> Unit,
    onPick: (RankedSource) -> Unit,
    onDownload: ((RankedSource) -> Unit)?,
) {
    CountRow(model, active, onClear)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Segmented(
            options = listOf(
                SegmentedOption(SourceSort.RECOMMENDED, stringResource(Res.string.rd_s4_src_sort_rec)),
                SegmentedOption(SourceSort.QUALITY, stringResource(Res.string.rd_s4_src_sort_quality)),
                SegmentedOption(SourceSort.SEEDERS, stringResource(Res.string.rd_s4_src_sort_seeders)),
                SegmentedOption(SourceSort.SIZE, stringResource(Res.string.rd_s4_src_sort_size)),
            ),
            selected = sort,
            onSelect = onSort,
            small = true,
        )
    }
    if (model.shown.isEmpty()) {
        NoMatch(onClear)
        return
    }
    val groups = if (sort == SourceSort.RECOMMENDED) groupByKind(model.shown) else listOf(null to model.shown)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (kind, list) ->
            if (kind != null && groups.size > 1) {
                SectionHeader(stringResource(if (kind == SourceKind.TORRENT) UiR.string.src_torrent else UiR.string.src_web))
            }
            list.forEach { s ->
                SourceRowContent(
                    source = s,
                    onClick = { onPick(s) },
                    onDownload = if (onDownload != null && s.candidate.kind == SourceKind.TORRENT) ({ onDownload(s) }) else null,
                    caps = model.caps,
                    whyNot = if (s.picked || s.recommended) emptyList() else whyNot(s, model.rules.prefs),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Comparador

private val COL_SOURCE: Dp = 208.dp
private val COL: Dp = 108.dp
private val COL_ACTION: Dp = 56.dp

/** Comparador: a table sorted by any column header; the best resolution, size, seeders and reliability are marked. */
@Composable
private fun CompareResults(model: PickerModel, active: Int, onClear: () -> Unit, onPick: (RankedSource) -> Unit, onDownload: ((RankedSource) -> Unit)?) {
    CountRow(model, active, onClear)
    if (model.shown.isEmpty()) {
        NoMatch(onClear)
        return
    }
    var sortName by rememberSaveable { mutableStateOf<String?>(null) }
    var descending by rememberSaveable { mutableStateOf(true) }
    val sortColumn = sortName?.let { n -> CompareColumn.entries.firstOrNull { it.name == n } }
    val rows = remember(model.shown, sortColumn, descending) {
        if (sortColumn == null) sortSources(model.shown, SourceSort.RECOMMENDED) else sortByColumn(model.shown, sortColumn, descending)
    }
    val best = remember(model.shown) { bestValues(model.shown) }
    val scheme = MaterialTheme.colorScheme
    val caption = stringResource(Res.string.rd_s4_src_compare_cd)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = caption }) {
        Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainer, border = BorderStroke(1.dp, scheme.outlineVariant)) {
            Column {
                Row(Modifier.background(scheme.surfaceContainerHigh), verticalAlignment = Alignment.CenterVertically) {
                    CompareColumn.entries.forEach { col ->
                        HeaderCell(col, sortColumn == col, descending, Modifier.width(if (col == CompareColumn.SOURCE) COL_SOURCE else COL)) {
                            if (sortColumn == col) descending = !descending else {
                                sortName = col.name
                                descending = col.higherIsBetter
                            }
                        }
                    }
                    Spacer(Modifier.width(COL_ACTION))
                }
                rows.forEach { s -> CompareRow(s, best, onPick, onDownload) }
            }
        }
    }
}

@Composable
private fun columnLabel(col: CompareColumn): String = stringResource(
    when (col) {
        CompareColumn.SOURCE -> Res.string.rd_s4_src_col_source
        CompareColumn.RESOLUTION -> Res.string.rd_s4_src_col_res
        CompareColumn.CODEC -> Res.string.rd_s4_src_col_codec
        CompareColumn.AUDIO -> Res.string.rd_s4_src_col_audio
        CompareColumn.SUBS -> Res.string.rd_s4_src_col_subs
        CompareColumn.SIZE -> Res.string.rd_s4_src_col_size
        CompareColumn.SEEDERS -> Res.string.rd_s4_src_col_seeders
        CompareColumn.CACHE -> Res.string.rd_s4_src_col_cache
        CompareColumn.RELIABILITY -> Res.string.rd_s4_src_col_rel
    },
)

@Composable
private fun HeaderCell(col: CompareColumn, sorted: Boolean, descending: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = columnLabel(col)
    val state = stringResource(
        when {
            !sorted -> Res.string.rd_s4_src_sort_none
            descending -> Res.string.rd_s4_src_sort_desc
            else -> Res.string.rd_s4_src_sort_asc
        },
    )
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(
            when {
                !sorted -> Icons.Rounded.UnfoldMore
                descending -> Icons.Rounded.ArrowDownward
                else -> Icons.Rounded.ArrowUpward
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CompareRow(s: RankedSource, best: Map<CompareColumn, Double>, onPick: (RankedSource) -> Unit, onDownload: ((RankedSource) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val c = s.candidate
    val torrent = c.kind == SourceKind.TORRENT
    val bestLabel = stringResource(Res.string.rd_s4_src_best)
    val ok = LocalSignalColors.current.ok
    val several = stringResource(Res.string.src_picker_several)
    val dash = "—"
    Row(
        Modifier
            .background(if (s.picked) scheme.secondaryContainer else scheme.surfaceContainer)
            .clickable(enabled = s.extEnabled, role = Role.RadioButton) { onPick(s) }
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompareColumn.entries.forEach { col ->
            val width = if (col == CompareColumn.SOURCE) COL_SOURCE else COL
            if (col == CompareColumn.SOURCE) {
                Row(Modifier.width(width).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (s.picked) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (s.picked) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(c.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${c.extName} · ${stringResource(if (torrent) UiR.string.src_torrent else UiR.string.src_web)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                return@forEach
            }
            val text = when (col) {
                CompareColumn.RESOLUTION -> c.quality.label().ifEmpty { dash }
                CompareColumn.CODEC -> listOfNotNull(c.tags.codec?.label(), c.tags.bitDepth?.let { stringResource(Res.string.src_tag_bit_depth, it) }).joinToString(" ").ifEmpty { c.container ?: dash }
                CompareColumn.AUDIO -> langsShort(c.dubs, null) ?: if (c.tags.dualAudio) several else dash
                CompareColumn.SUBS -> langsShort(c.subs, null) ?: if (c.tags.multiSub) several else dash
                CompareColumn.SIZE -> if (!torrent) stringResource(Res.string.rd_s4_src_stream) else c.torrent?.sizeBytes?.let { formatBytes(it) } ?: dash
                CompareColumn.SEEDERS -> c.torrent?.seeders?.toString() ?: dash
                CompareColumn.CACHE -> c.debrid?.takeIf { it.cached }?.service?.label ?: stringResource(Res.string.rd_s4_src_no)
                CompareColumn.RELIABILITY -> "${(c.reliability * 100).toInt()} %"
                CompareColumn.SOURCE -> ""
            }
            val isBest = best[col]?.let { b -> compareValue(s, col) == b } == true
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBest) FontWeight.Bold else null,
                color = if (isBest) ok else if (s.extEnabled) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width).padding(horizontal = 12.dp).semantics { if (isBest) contentDescription = "$text $bestLabel" },
            )
        }
        Box(Modifier.width(COL_ACTION), contentAlignment = Alignment.Center) {
            if (onDownload != null && torrent) {
                IconButton(onClick = { onDownload(s) }, enabled = s.extEnabled) {
                    Icon(Icons.Rounded.Download, contentDescription = stringResource(Res.string.rd_s4_src_download_cd, c.label))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
private fun PickerLoading() {
    val show = rememberAfterDelay(150)
    if (!show) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(stringResource(Res.string.src_picker_loading), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(96.dp)) }
    }
}

/** No source for this episode: search again or manage the extensions. */
@Composable
private fun NoSources(onRetry: () -> Unit, onOpenExtensions: (() -> Unit)?) {
    StateBlock(
        kind = EmptyKind.SOURCES,
        title = stringResource(Res.string.src_picker_empty_title),
        body = stringResource(Res.string.src_picker_empty_body),
        onRetry = onRetry,
        retryLabel = stringResource(Res.string.rd_s4_src_search_again),
        onOpenExtensions = onOpenExtensions,
    )
}

/** Every extension failed: what happened, try again, or go fix the extensions. */
@Composable
private fun ExtensionError(error: AppError, onRetry: () -> Unit, onOpenExtensions: (() -> Unit)?) {
    StateBlock(
        kind = EmptyKind.ERROR,
        title = stringResource(Res.string.src_picker_error_title),
        body = error.userMessage(),
        onRetry = onRetry,
        retryLabel = stringResource(UiR.string.ui_retry),
        onOpenExtensions = onOpenExtensions,
    )
}

@Composable
private fun StateBlock(kind: EmptyKind, title: String, body: String, onRetry: () -> Unit, retryLabel: String, onOpenExtensions: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().decor(DecorRegion.EMPTY).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmptyArt(kind)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(retryLabel, modifier = Modifier.padding(start = 8.dp))
            }
            if (onOpenExtensions != null) {
                OutlinedButton(onClick = onOpenExtensions) {
                    Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.rd_s4_src_manage_ext), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Some sources came back but an extension failed: say so over the list, with a retry. */
@Composable
private fun PartialError(error: AppError, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.rd_s4_src_ext_error, error.userMessage()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(UiR.string.ui_retry)) }
        }
    }
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
    /** The automatic rules this source misses (Sparkles: "why not this one"). */
    whyNot: List<String> = emptyList(),
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
            WhyNotRow(whyNot)
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

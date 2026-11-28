// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_fac_any
import app.nijiyome.ui.resources.rd_i1_fac_clear
import app.nijiyome.ui.resources.rd_i1_fac_no_match
import app.nijiyome.ui.resources.rd_i1_fac_remove
import app.nijiyome.ui.resources.rd_i1_fac_search
import app.nijiyome.ui.resources.rd_i1_fac_show
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls
import app.nijiyome.designsystem.scroll.njVerticalScroll

// Sparkles redesign, lane I1. Pattern `fac` of docs/redesign/artefacto/lab.js: every chip says its current value, opens a popover (a
// sheet on phones) with a search box and multi-selection, and clears with ×. Each option shows how many titles would remain and the
// total changes live while marking.

/** One value of a facet. [count] = how many results remain if it is picked with the other facets as they are (null = unknown). */
@Immutable
data class FacetOption(val id: String, val label: String, val count: Int? = null)

/**
 * A filter dimension (status, genre, format, score). [single] facets pick one option or none ("Any"); the others pick any number.
 * [searchable] adds the search box (long option lists such as genres).
 */
@Immutable
data class Facet(
    val id: String,
    val label: String,
    val options: List<FacetOption>,
    val single: Boolean = false,
    val searchable: Boolean = options.size > 10,
)

/** facet id -> picked option ids. A facet without entry (or with an empty set) is inactive. */
typealias FacetSelection = Map<String, Set<String>>

/** The facet has something picked. */
fun FacetSelection.isActive(facet: Facet): Boolean = this[facet.id].orEmpty().isNotEmpty()

/** [selection] with [facet] set to [picked]. */
fun FacetSelection.with(facet: Facet, picked: Set<String>): FacetSelection =
    if (picked.isEmpty()) this - facet.id else this + (facet.id to picked)

/**
 * The chip text: the facet's name alone when inactive, else "Genre: Action, Drama +2".
 *
 * ```kotlin
 * facetSummary(genre, selection)   // "Genre: Action, Drama +2"
 * ```
 */
fun facetSummary(facet: Facet, selection: FacetSelection): String {
    val picked = selection[facet.id].orEmpty()
    if (picked.isEmpty()) return facet.label
    val names = facet.options.filter { it.id in picked }.map { it.label }.ifEmpty { picked.toList() }
    val more = if (names.size > 2) " +${names.size - 2}" else ""
    return "${facet.label}: ${names.take(2).joinToString(", ")}$more"
}

/**
 * The facet bar: one chip per facet that opens its panel, a × beside each active one, and the live [resultCount] line ("12 of 40
 * titles", passed already formatted). Wide windows wrap the chips and open popovers; phones scroll the chips sideways and open sheets.
 *
 * ```kotlin
 * var selection by remember { mutableStateOf<FacetSelection>(emptyMap()) }
 * FacetBar(facets, selection, onSelectionChange = { selection = it }, resultCount = results.size, resultText = "${results.size} of ${all.size}")
 * ```
 */
@Composable
fun FacetBar(
    facets: List<Facet>,
    selection: FacetSelection,
    onSelectionChange: (FacetSelection) -> Unit,
    resultCount: Int,
    modifier: Modifier = Modifier,
    resultText: String? = null,
    compact: Boolean = isCompactWindow(),
    /** Text of the panels' confirm button; null = "Show N titles". Pass the right noun when the results are not titles (sources). */
    showLabel: String? = null,
) {
    var open by remember { mutableStateOf<String?>(null) }
    val chips: @Composable () -> Unit = {
        facets.forEach { facet ->
            FacetChip(
                facet = facet,
                selection = selection,
                open = open == facet.id,
                onOpen = { open = if (open == facet.id) null else facet.id },
                onClose = { open = null },
                onSelectionChange = onSelectionChange,
                resultCount = resultCount,
                compact = compact,
                showLabel = showLabel,
            )
        }
    }
    val count: @Composable () -> Unit = {
        if (resultText != null) {
            Text(
                resultText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 8.dp),
                maxLines = 1,
            )
        }
    }
    if (compact) {
        Column(modifier) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { chips() }
            count()
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
            Spacer(Modifier.width(8.dp))
            count()
        }
    }
}

@Composable
private fun FacetChip(
    facet: Facet,
    selection: FacetSelection,
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSelectionChange: (FacetSelection) -> Unit,
    resultCount: Int,
    compact: Boolean,
    showLabel: String?,
) {
    val active = selection.isActive(facet)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilterChip(
                selected = active,
                onClick = onOpen,
                label = { Text(facetSummary(facet, selection), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            if (!compact) {
                DropdownMenu(expanded = open, onDismissRequest = onClose) {
                    FacetPanel(
                        facet = facet,
                        picked = selection[facet.id].orEmpty(),
                        onPickedChange = { onSelectionChange(selection.with(facet, it)) },
                        resultCount = resultCount, showLabel = showLabel,
                        onDone = onClose,
                        modifier = Modifier.width(320.dp).padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (active) {
            IconButton(onClick = { onSelectionChange(selection.with(facet, emptySet())) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.rd_i1_fac_remove, facet.label),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (compact && open) {
        AdaptiveSheet(onDismissRequest = onClose) {
            Text(
                facet.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            FacetPanel(
                facet = facet,
                picked = selection[facet.id].orEmpty(),
                onPickedChange = { onSelectionChange(selection.with(facet, it)) },
                resultCount = resultCount, showLabel = showLabel,
                onDone = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * The inside of a facet's popover or sheet: search (when [Facet.searchable]), the options with their counts, "Clear" and the
 * "Show N titles" button ([showLabel] replaces its text). Usable on its own, e.g. inside a filters side panel.
 */
@Composable
fun FacetPanel(
    facet: Facet,
    picked: Set<String>,
    onPickedChange: (Set<String>) -> Unit,
    resultCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text of the confirm button; null = "Show N titles" ([resultCount]). */
    showLabel: String? = null,
) {
    var query by remember(facet.id) { mutableStateOf("") }
    val visible = if (query.isBlank()) facet.options else facet.options.filter { it.label.contains(query.trim(), ignoreCase = true) }
    Column(modifier) {
        if (facet.searchable) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.rd_i1_fac_search, facet.label)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusOnOpen(),
            )
        }
        Column(Modifier.heightIn(max = 360.dp).njVerticalScroll(rememberScrollState(), scrollToTopOnReselect = false)) {
            if (facet.single) {
                FacetOptionRow(stringResource(Res.string.rd_i1_fac_any), null, picked.isEmpty(), single = true) { onPickedChange(emptySet()) }
            }
            visible.forEach { option ->
                val checked = option.id in picked
                FacetOptionRow(option.label, option.count, checked, single = facet.single) {
                    onPickedChange(
                        when {
                            facet.single -> setOf(option.id)
                            checked -> picked - option.id
                            else -> picked + option.id
                        },
                    )
                }
            }
            if (visible.isEmpty()) {
                Text(
                    stringResource(Res.string.rd_i1_fac_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (picked.isNotEmpty()) TextButton(shape = NijiyomeControls.shape, onClick = { onPickedChange(emptySet()) }) { Text(stringResource(Res.string.rd_i1_fac_clear)) }
            Spacer(Modifier.weight(1f))
            Button(shape = NijiyomeControls.shape, onClick = onDone) { Text(showLabel ?: pluralStringResource(Res.plurals.rd_i1_fac_show, resultCount, resultCount)) }
        }
    }
}

@Composable
private fun FacetOptionRow(label: String, count: Int?, checked: Boolean, single: Boolean, onClick: () -> Unit) {
    val rowModifier = if (single) {
        Modifier.selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Row(
        rowModifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (single) RadioButton(selected = checked, onClick = null) else Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

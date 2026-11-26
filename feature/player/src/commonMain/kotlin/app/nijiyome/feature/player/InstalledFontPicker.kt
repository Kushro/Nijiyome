// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.designsystem.components.focusOnOpen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.sub_style_font_installed
import app.nijiyome.feature.player.resources.sub_style_font_installed_choose
import app.nijiyome.feature.player.resources.sub_style_font_installed_none
import app.nijiyome.feature.player.resources.sub_style_font_installed_search

import app.nijiyome.feature.player.resources.player_cancel
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * Hito 6 (desktop, Tatakai Next's `font_families`): a chip with the chosen installed font, and the list of the [families] installed
 * on the system to choose one from, with a search field. [selected] = the installed family in use, null when the font is another.
 */
@Composable
internal fun InstalledFontChooser(families: List<String>, selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected != null,
        onClick = { open = true },
        label = {
            Text(
                if (selected != null) stringResource(Res.string.sub_style_font_installed, selected) else stringResource(Res.string.sub_style_font_installed_choose),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
    if (!open) return
    var query by remember { mutableStateOf("") }
    val shown = remember(families, query) { filterFamilies(families, query) }
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { open = false }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_cancel)) } },
        title = { Text(stringResource(Res.string.sub_style_font_installed_choose)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.sub_style_font_installed_search)) },
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(
                        stringResource(Res.string.sub_style_font_installed_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it }) { family ->
                        ListItem(
                            headlineContent = {
                                Text(family, fontFamily = remember(family) { installedFontFamily(family) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { if (family == selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onPick(family)
                                open = false
                            },
                        )
                    }
                }
            }
        },
    )
}

/** The families whose name contains every word of [query] (case-insensitive), in their order. */
internal fun filterFamilies(families: List<String>, query: String): List<String> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return families
    return families.filter { f -> val l = f.lowercase(); words.all { it in l } }
}

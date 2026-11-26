// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.ui.labels.languageName
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import app.nijiyome.designsystem.theme.NijiyomeControls

/** Every ISO 639-1 language, by code. */
private val ISO_LANGUAGES: List<String> by lazy { Locale.getISOLanguages().toList() }

/** "Japanese" / "Japonés" for "ja" in the app's language; the code itself when the platform has no name for it. */
internal fun displayLanguage(code: String, locale: Locale = Locale.getDefault()): String =
    languageName(code, locale).ifBlank { code.uppercase(Locale.ROOT) }.replaceFirstChar { it.titlecase(locale) }

/**
 * Choose-don't-type for a language code (subtitles, audio): the [common] codes as chips and "Otro idioma…", which opens a searchable
 * list of every language by its name. [value] stays an ISO 639-1 code in the settings.
 */
@Composable
internal fun LanguagePickerRow(label: String, value: String, common: List<String>, onSelect: (String) -> Unit, hint: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingRow(label, hint, stack = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val shown = if (value in common) common else common + value
            shown.forEach { code ->
                FilterChip(
                    selected = code == value,
                    onClick = { onSelect(code) },
                    label = { Text(displayLanguage(code)) },
                    leadingIcon = if (code == value) ({ Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
                )
            }
            OutlinedButton(onClick = { open = true }, shape = NijiyomeControls.shape) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s10_lang_other), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (open) {
        LanguageSearchSheet(title = label, value = value, onPick = { onSelect(it); open = false }, onDismiss = { open = false })
    }
}

@Composable
private fun LanguageSearchSheet(title: String, value: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val locale = Locale.getDefault()
    val all = remember(locale) { ISO_LANGUAGES.map { it to displayLanguage(it, locale) }.sortedBy { it.second.lowercase(locale) } }
    val q = SettingsCatalog.fold(query.trim())
    val shown = if (q.isEmpty()) all else all.filter { (code, name) -> SettingsCatalog.fold(name).contains(q) || code.startsWith(q) }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.rd_s10_lang_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s10_search_clear)) }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusOnOpen(),
            )
            if (shown.isEmpty()) {
                Text(stringResource(Res.string.rd_s10_lang_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shown, key = { it.first }) { (code, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = code == value, role = Role.RadioButton, onClick = { onPick(code) })
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (code == value) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

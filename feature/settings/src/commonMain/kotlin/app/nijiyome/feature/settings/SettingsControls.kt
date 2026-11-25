// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.theme.LocalNijiyomeAmoled
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton

/**
 * One group of a category (artifact `.nj-b-group`): a titled card with its rows. [commit] marks a group whose changes apply with a
 * button (credentials, a connection that restarts): it shows "Se aplica con botón" next to the title.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    icon: ImageVector? = null,
    hint: String? = null,
    commit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val amoled = LocalNijiyomeAmoled.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (commit) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.rd_s10_commit_tag), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (hint != null) SectionHint(hint)
            content()
        }
    }
}

/** A sub-heading inside a group (the screen a block of Apariencia options belongs to). */
@Composable
internal fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * The one explicit-save pattern of Settings ("Probar y guardar"): shown under a group whose changes need a test or restart a
 * connection. "Cambios sin aplicar" while [dirty], Descartar and the apply button (which tests first when the caller does), and the
 * outcome line.
 */
@Composable
internal fun CommitBar(
    dirty: Boolean,
    busy: Boolean,
    canApply: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    applyLabel: String = stringResource(Res.string.rd_s10_commit_apply),
    result: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (dirty) Icons.Outlined.EditNote else Icons.Outlined.Check,
                contentDescription = null,
                tint = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(if (dirty) Res.string.rd_s10_commit_dirty else Res.string.rd_s10_commit_applied),
                style = MaterialTheme.typography.labelLarge,
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).politeLiveRegion(),
            )
            if (dirty) {
                TextButton(onClick = onDiscard, enabled = !busy) { Text(stringResource(Res.string.rd_s10_commit_discard)) }
                Button(onClick = onApply, enabled = canApply && !busy) { Text(if (busy) stringResource(Res.string.rd_s10_commit_testing) else applyLabel) }
            }
        }
        if (result != null) result()
    }
}

/**
 * Prototype `.setting`: label + optional hint, control on the right; [stack] puts the control under the text (wide segmented,
 * swatches). Rows are separated by a hairline like the prototype.
 */
@Composable
internal fun SettingRow(
    label: String,
    hint: String? = null,
    stack: Boolean = false,
    divider: Boolean = true,
    control: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (stack) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingText(label, hint)
                control()
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) { SettingText(label, hint) }
                control()
            }
        }
    }
}

@Composable
private fun SettingText(label: String, hint: String?) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    if (hint != null) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Whole row toggles (Role.Switch), switch on the right. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) { SettingText(label, hint) }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** Row that opens another screen / subpage. */
@Composable
internal fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

/** Muted paragraph under a section title. */
@Composable
internal fun SectionHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

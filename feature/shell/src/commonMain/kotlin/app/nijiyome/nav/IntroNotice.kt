// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_intro_body
import app.nijiyome.feature.shell.resources.rd_s1_intro_dismiss
import app.nijiyome.feature.shell.resources.rd_s1_intro_open
import app.nijiyome.feature.shell.resources.rd_s1_intro_title
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time notice after updating to Sparkles (`DesignPrefs.introSeen == false`, wizard already done): a banner on top of the
 * content. Both buttons mark it seen; [onOpen] also opens Ajustes › Apariencia.
 */
@Composable
internal fun IntroNotice(onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.rd_s1_intro_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.rd_s1_intro_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rd_s1_intro_dismiss)) }
                    Button(onClick = onOpen) { Text(stringResource(Res.string.rd_s1_intro_open)) }
                }
            }
        }
    }
}

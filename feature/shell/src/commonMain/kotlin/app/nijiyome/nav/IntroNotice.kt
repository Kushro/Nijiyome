// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_intro_body
import app.nijiyome.feature.shell.resources.rd_s1_intro_dismiss
import app.nijiyome.feature.shell.resources.rd_s1_intro_open
import app.nijiyome.feature.shell.resources.rd_s1_intro_title
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * The one-time notice after updating to Sparkles (`DesignPrefs.introSeen == false`, wizard already done): a slim band on top of
 * the content. Both buttons mark it seen; [onOpen] also opens Ajustes › Apariencia. On wide windows it is one line (icon, text,
 * buttons) so it does not push the page down; narrow windows put the buttons under the text.
 */
@Composable
internal fun IntroNotice(onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        BoxWithConstraints {
            val oneLine = maxWidth >= ONE_LINE_MIN_WIDTH
            val title = stringResource(Res.string.rd_s1_intro_title)
            val body = stringResource(Res.string.rd_s1_intro_body)
            val buttons: @Composable () -> Unit = {
                TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s1_intro_dismiss)) }
                Button(onClick = onOpen, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.rd_s1_intro_open)) }
            }
            if (oneLine) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { buttons() }
                }
            } else {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) { buttons() }
                    }
                }
            }
        }
    }
}

private val ONE_LINE_MIN_WIDTH = 720.dp

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.ui.patterns.ReorderableRow
import app.nijiyome.ui.patterns.rememberReorderState
import app.nijiyome.ui.patterns.reorderContainer
import app.nijiyome.ui.patterns.reorderPlacement
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane S2: "Editar Inicio" (artifact screens-a.js `editPanel`, lab.js pattern `reo`). Every Home row in the user's
// order with a drag handle (Alt+Up / Alt+Down and TalkBack actions come with the pattern), up / down buttons on wide windows and a
// visibility toggle. Changes are saved at once (LookPrefs.design.homeRowOrder / homeRowsHidden) and show behind the panel.

/** Name of a Home row as the edit panel lists it. [because] = title of the "Because you watched" source, when known. */
@Composable
internal fun homeRowTitle(id: String, because: String?): String = when (id) {
    ROW_CONTINUE -> stringResource(Res.string.rd_s2_tonight)
    ROW_DOWNLOADS -> stringResource(Res.string.rd_s2_row_downloads)
    ROW_FOLLOWED -> stringResource(Res.string.home_followed_title)
    ROW_LATEST -> stringResource(Res.string.home_latest_title)
    ROW_TOP -> stringResource(Res.string.home_top10_title)
    ROW_FOR_YOU -> stringResource(Res.string.home_for_you_title)
    ROW_BECAUSE -> because?.let { stringResource(Res.string.home_because_title, it) } ?: stringResource(Res.string.rd_s2_row_because)
    ROW_GENRES -> stringResource(Res.string.home_genres)
    ROW_FEEDS -> stringResource(Res.string.rd_s2_row_feeds)
    ROW_PREMIERES -> stringResource(Res.string.home_upcoming_title)
    ROW_FAVOURITES -> stringResource(Res.string.home_favourites_title)
    ROW_EXTENSIONS -> stringResource(Res.string.home_ext_rows)
    ROW_MORE -> stringResource(Res.string.rd_s2_row_more)
    else -> id
}

/** Hide button of a row header while Editar Inicio is open. */
@Composable
internal fun HideRowButton(title: String, onHide: () -> Unit) {
    IconButton(onClick = onHide) {
        Icon(Icons.Outlined.VisibilityOff, contentDescription = stringResource(Res.string.rd_s2_hide_row, title))
    }
}

/**
 * The edit panel: a side panel on wide windows (Home stays visible and live beside it), a bottom sheet on phones. [order] = every
 * row id (hidden ones too) in the user's order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeEditPanel(
    order: List<String>,
    hidden: Set<String>,
    because: String?,
    onMove: (from: Int, to: Int) -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        // The decor sits on the content inside the sheet's surface (on the sheet itself only its corners showed).
        ModalBottomSheet(onDismissRequest = onDone, modifier = modifier) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT).decor(DecorRegion.SHEET)) {
                EditBody(order, hidden, because, onMove, onToggle, onReset, onDone, compact = true)
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.width(PANEL_WIDTH).fillMaxHeight(),
        ) {
            Column(Modifier.fillMaxHeight().decor(DecorRegion.SHEET)) {
                EditBody(order, hidden, because, onMove, onToggle, onReset, onDone, compact = false)
            }
        }
    }
}

@Composable
private fun ColumnScope.EditBody(
    order: List<String>,
    hidden: Set<String>,
    because: String?,
    onMove: (Int, Int) -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    compact: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.rd_s2_edit_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Button(onClick = onDone) { Text(stringResource(Res.string.rd_s2_edit_done)) }
    }
    Text(
        stringResource(if (compact) Res.string.rd_s2_edit_hint_touch else Res.string.rd_s2_edit_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    val reorder = rememberReorderState(onMove = onMove)
    val hiddenLabel = stringResource(Res.string.rd_s2_row_hidden)
    LazyColumn(state = reorder.listState, modifier = Modifier.weight(1f).fillMaxWidth().reorderContainer(reorder)) {
        itemsIndexed(order, key = { _, id -> id }) { i, id ->
            val on = id !in hidden
            val title = homeRowTitle(id, because)
            ReorderableRow(
                state = reorder,
                index = i,
                count = order.size,
                title = title,
                modifier = reorderPlacement().padding(horizontal = 8.dp),
                supporting = if (on) null else hiddenLabel,
                trailing = {
                    if (!compact) {
                        IconButton(onClick = { reorder.move(i, i - 1, order.size) }, enabled = i > 0) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(Res.string.rd_s2_row_up, title))
                        }
                        IconButton(onClick = { reorder.move(i, i + 1, order.size) }, enabled = i < order.size - 1) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(Res.string.rd_s2_row_down, title))
                        }
                    }
                    IconToggleButton(checked = on, onCheckedChange = { onToggle(id) }) {
                        Icon(
                            if (on) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = stringResource(Res.string.rd_s2_show_row, title),
                        )
                    }
                },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onReset) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.rd_s2_edit_reset), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val PANEL_WIDTH = 360.dp
private const val SHEET_HEIGHT = 0.85f

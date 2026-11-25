// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.feature.search.resources.Res
import app.nijiyome.feature.search.resources.rd_contract_rank_cd
import app.nijiyome.ui.patterns.isCompactWindow
import org.jetbrains.compose.resources.stringResource

/**
 * A trending card with its place in the ranking (artifact `explorar` › `lienzo`, `.nj-b-rank`): the outlined number in the display
 * face behind the card's bottom-start corner, the card overlapping it by 10 dp. The rank is information, so it is read: the number is
 * its own node ("Rank 3") in a traversal group with the card, so a screen reader says it right before the card.
 */
@Composable
internal fun RankedCard(rank: Int, card: @Composable () -> Unit) {
    val compact = isCompactWindow()
    val label = stringResource(Res.string.rd_contract_rank_cd, rank)
    val stroke = with(LocalDensity.current) { 1.5.dp.toPx() }
    val size = if (compact) 44.sp else 64.sp
    Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.Bottom) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                lineHeight = size * 0.8f,
                fontWeight = FontWeight.ExtraBold,
                drawStyle = Stroke(width = stroke),
            ),
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            modifier = Modifier
                .padding(bottom = if (compact) 40.dp else 44.dp)
                .semantics { contentDescription = label }
                // The card overlaps the number by 10 dp (artifact `margin-right: -10px`).
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    val overlap = 10.dp.roundToPx().coerceAtMost(p.width)
                    layout(p.width - overlap, p.height) { p.place(0, 0) }
                },
        )
        Box { card() }
    }
}

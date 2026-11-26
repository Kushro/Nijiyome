// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.model.QrMatrix

// Sparkles S9: building blocks shared by the Link client (phone) and the Link server (desktop) screens: the titled panel, the device map
// of the `mapa` structure and the QR drawing of a pairing window.

/** Artifact `.nj-c-panel` with a titled header and an optional action at its end. */
@Composable
internal fun Panel(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
internal fun Cap(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
    }
}

/**
 * ALT-08: the status cards of the PANEL structure in even columns (three on a wide window, so six cards are 3 + 3, two on a medium one,
 * one on a phone), every card of a row as tall as the tallest. The container is the direction's `LINK` region; the cards sit on the
 * page itself, as in the artifact.
 */
@Composable
internal fun StatCardGrid(cards: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().decor(DecorRegion.LINK)) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 520.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** State of a device on the map and of the wire that reaches it. */
enum class MapNodeState { CONNECTED, IDLE, FAILED }

@Immutable
internal data class MapNode(
    val id: String,
    val name: String,
    val sub: String?,
    val icon: ImageVector,
    val state: MapNodeState,
)

private val NODE_W = 168.dp
private val NODE_H = 96.dp

/** Vertical space between two devices on the map. */
private val NODE_GAP = 28.dp

/**
 * The device map (artifact `lnMap`): [hub] on the left, [nodes] stacked on the right, a curved wire from the hub to each one (solid when
 * connected, dashed when idle or failing), an optional [wireLabel] over the first wire ("Anime4K 1440p · ×2 · 14 ms") and an optional
 * [extra] node under the hub (the dashed "Emparejar"). The container is the direction's `LINK` region. Nodes are selectable when
 * [onSelect] is given. Nothing moves: the wire state is drawn, never animated in a loop.
 */
@Composable
internal fun DeviceMap(
    hub: MapNode,
    nodes: List<MapNode>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: ((String) -> Unit)? = null,
    wireLabel: String? = null,
    description: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val signals = LocalSignalColors.current
    val connected = MaterialTheme.colorScheme.primary
    // LNK-02: an offline wire is dashed, not faint: onSurfaceVariant reads on the map surface in every theme (outline did not).
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val failed = signals.err
    val rows = nodes.size.coerceAtLeast(1)
    val height: Dp = maxOf(240.dp, NODE_H * rows + NODE_GAP * (rows + 1) + if (extra != null) 64.dp else 0.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .decor(DecorRegion.LINK)
            .semantics { description?.let { contentDescription = it } },
    ) {
        val w = maxWidth
        val h = if (extra != null) maxHeight - 64.dp else maxHeight
        val half = NODE_W / 2
        val hubX = (w * 0.24f).coerceAtLeast(half + 12.dp)
        val hubY = h / 2
        // LNK-02: every device gets its own slot, NODE_GAP apart (they used to touch), and every other one steps left so the wires
        // fan out instead of running parallel. The column stays inside the map whatever its width.
        val nodeColumn = (w * 0.74f).coerceAtMost(w - half - 12.dp)
        val stagger = if (nodes.size > 1) minOf(w * 0.08f, 56.dp) else 0.dp
        val nodeX: (Int) -> Dp = { i -> if (i % 2 == 0) nodeColumn else (nodeColumn - stagger) }
        val block = NODE_H * nodes.size + NODE_GAP * (nodes.size - 1).coerceAtLeast(0)
        val top = ((h - block) / 2).coerceAtLeast(NODE_GAP / 2)
        val nodeY: (Int) -> Dp = { i -> top + NODE_H / 2 + (NODE_H + NODE_GAP) * i }
        Canvas(Modifier.fillMaxSize()) {
            nodes.forEachIndexed { i, n ->
                val start = Offset((hubX + half).toPx().coerceAtMost(this.size.width), hubY.toPx())
                val end = Offset((nodeX(i) - half).toPx().coerceAtLeast(0f), nodeY(i).toPx())
                val midX = (start.x + end.x) / 2
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                }
                val color: Color = when (n.state) {
                    MapNodeState.CONNECTED -> connected
                    MapNodeState.IDLE -> idle
                    MapNodeState.FAILED -> failed
                }
                drawPath(
                    path,
                    color = color,
                    style = Stroke(
                        width = if (n.state == MapNodeState.CONNECTED) 3.dp.toPx() else 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (n.state == MapNodeState.CONNECTED) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
        }
        MapNodeBox(hub, isHub = true, selected = selected == hub.id, onSelect = onSelect, modifier = Modifier.offset(hubX - half, hubY - NODE_H / 2))
        nodes.forEachIndexed { i, n ->
            MapNodeBox(n, isHub = false, selected = selected == n.id, onSelect = onSelect, modifier = Modifier.offset(nodeX(i) - half, nodeY(i) - NODE_H / 2))
        }
        if (wireLabel != null && nodes.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                Text(wireLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
            }
        }
        if (extra != null) {
            Box(Modifier.align(Alignment.BottomStart).offset(x = (hubX - NODE_W / 2).coerceAtLeast(8.dp)).padding(bottom = 12.dp)) { extra() }
        }
    }
}

@Composable
private fun MapNodeBox(node: MapNode, isHub: Boolean, selected: Boolean, onSelect: ((String) -> Unit)?, modifier: Modifier) {
    val signals = LocalSignalColors.current
    val accent = when (node.state) {
        MapNodeState.CONNECTED -> MaterialTheme.colorScheme.primary
        MapNodeState.IDLE -> MaterialTheme.colorScheme.outlineVariant
        MapNodeState.FAILED -> signals.err
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected || isHub) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else accent),
        modifier = modifier
            .width(NODE_W)
            .height(NODE_H)
            .then(if (onSelect != null) Modifier.selectable(selected = selected, role = Role.Button, onClick = { onSelect(node.id) }) else Modifier),
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(node.icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (isHub) 28.dp else 24.dp))
            Text(node.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            node.sub?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A pairing QR from the server's [QrMatrix]: dark modules on a light quiet zone, whatever the theme (phones read it). */
@Composable
internal fun QrMatrixImage(qr: QrMatrix, description: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(1f)
            .background(Color.White)
            .padding(12.dp)
            .semantics { contentDescription = description },
    ) {
        val cell = size.minDimension / qr.size
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr[x, y]) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

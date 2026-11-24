// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import app.nijiyome.ui.platform.AppWindow
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.abs

private val TITLE_BAR_HEIGHT = 36.dp
private val PIP_TITLE_BAR_HEIGHT = 28.dp
private val BUTTON_WIDTH = 44.dp
private val EDGE_THICKNESS = 5.dp
private val TOP_EDGE_THICKNESS = 4.dp
private val CORNER_SIZE = 10.dp
private const val DOUBLE_CLICK_MS = 350L
private const val DRAG_SLOP_PX = 4

/**
 * Frameless desktop title bar (after Tatakai Next `title_bar.rs`): app name, status chips (`Link :<port>` while the Link
 * server runs, `Sin conexión` / `Offline` when offline), drag-to-move, double-click to maximize/restore, and minimize /
 * maximize / close buttons. Hidden in fullscreen so players and readers stay edge-to-edge.
 */
@Composable
fun DesktopTitleBar(
    window: ComposeWindow,
    windowState: WindowState,
    appWindow: AppWindow,
    online: Boolean,
    linkPort: Int?,
    spanish: Boolean,
    windows: Boolean,
    onClose: () -> Unit,
) {
    if (appWindow.fullscreen || windowState.placement == WindowPlacement.Fullscreen) return
    val pip = appWindow.pictureInPicture
    val maximized = windowState.placement == WindowPlacement.Maximized
    val scheme = MaterialTheme.colorScheme
    val toggleMaximize: () -> Unit = {
        if (maximized) {
            windowState.placement = WindowPlacement.Floating
        } else {
            if (windows) applyWindowsMaximizedBounds(window)
            windowState.placement = WindowPlacement.Maximized
        }
    }

    var lastClickMs by remember { mutableLongStateOf(0L) }
    var lastClickPos by remember { mutableStateOf<Point?>(null) }

    Surface(
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (pip) PIP_TITLE_BAR_HEIGHT else TITLE_BAR_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(window, windowState, maximized) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startScreen = currentPointerLocation() ?: return@awaitEachGesture
                                val now = System.currentTimeMillis()
                                val prev = lastClickPos
                                val isDoubleClick = !pip && prev != null &&
                                    now - lastClickMs <= DOUBLE_CLICK_MS &&
                                    abs(startScreen.x - prev.x) <= 6 &&
                                    abs(startScreen.y - prev.y) <= 6
                                if (isDoubleClick) {
                                    lastClickMs = 0L
                                    lastClickPos = null
                                    down.consume()
                                    toggleMaximize()
                                    return@awaitEachGesture
                                }
                                lastClickMs = now
                                lastClickPos = startScreen

                                var startWinX = window.x
                                var startWinY = window.y
                                var dragOrigin = startScreen
                                var dragging = false
                                var wasMaximized = windowState.placement == WindowPlacement.Maximized

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed || !event.buttons.isPrimaryPressed) break
                                    val cur = currentPointerLocation() ?: continue
                                    val dx = cur.x - dragOrigin.x
                                    val dy = cur.y - dragOrigin.y
                                    if (!dragging && (abs(dx) >= DRAG_SLOP_PX || abs(dy) >= DRAG_SLOP_PX)) {
                                        dragging = true
                                        lastClickMs = 0L
                                        lastClickPos = null
                                        if (wasMaximized) {
                                            val oldW = window.width.coerceAtLeast(1)
                                            val fraction = ((cur.x - window.x).toFloat() / oldW).coerceIn(0.15f, 0.85f)
                                            windowState.placement = WindowPlacement.Floating
                                            wasMaximized = false
                                            val restoredW = window.width.coerceAtLeast(window.minimumSize.width)
                                            startWinX = (cur.x - restoredW * fraction).toInt()
                                            startWinY = (cur.y - 16).coerceAtLeast(0)
                                            dragOrigin = cur
                                            window.setLocation(startWinX, startWinY)
                                        }
                                    }
                                    if (dragging) {
                                        change.consume()
                                        window.setLocation(startWinX + (cur.x - dragOrigin.x), startWinY + (cur.y - dragOrigin.y))
                                    }
                                }
                            }
                        }
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Nijiyome",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    if (!pip && linkPort != null && linkPort > 0) {
                        LinkPortChip(port = linkPort)
                    }
                    if (!pip && !online) {
                        OfflineChip(label = if (spanish) "Sin conexión" else "Offline")
                    }
                    Spacer(Modifier.weight(1f))
                }

                if (!pip) {
                    WindowControlButton(
                        contentDescription = if (spanish) "Minimizar" else "Minimize",
                        onClick = { windowState.isMinimized = true },
                    ) { color ->
                        Canvas(Modifier.size(11.dp)) {
                            val y = size.height * 0.55f
                            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    WindowControlButton(
                        contentDescription = if (maximized) {
                            if (spanish) "Restaurar" else "Restore"
                        } else {
                            if (spanish) "Maximizar" else "Maximize"
                        },
                        onClick = toggleMaximize,
                    ) { color ->
                        Canvas(Modifier.size(11.dp)) {
                            val stroke = Stroke(width = 1.3.dp.toPx())
                            if (maximized) {
                                val s = size.width * 0.72f
                                val off = size.width - s
                                drawRect(color, topLeft = Offset(off, 0f), size = Size(s, s), style = stroke)
                                drawRect(color, topLeft = Offset(0f, off), size = Size(s, s), style = stroke)
                            } else {
                                drawRect(color, topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()), size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), style = stroke)
                            }
                        }
                    }
                }
                WindowControlButton(
                    contentDescription = if (spanish) "Cerrar" else "Close",
                    danger = true,
                    onClick = onClose,
                ) { color ->
                    Canvas(Modifier.size(10.dp)) {
                        val w = 1.4.dp.toPx()
                        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = w, cap = StrokeCap.Round)
                        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = w, cap = StrokeCap.Round)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = scheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun LinkPortChip(port: Int) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(6.dp).background(scheme.primary, CircleShape))
            Text(
                text = "Link :$port",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OfflineChip(label: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WindowControlButton(
    contentDescription: String,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && danger -> scheme.error
        hovered -> scheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val fg = if (hovered && danger) scheme.onError else scheme.onSurface
    Box(
        modifier = Modifier
            .width(BUTTON_WIDTH)
            .fillMaxHeight()
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon(fg)
    }
}

/**
 * Wraps [content] with thin invisible edge and corner resize strips while the undecorated window is floating, so resizing
 * from every border and corner works identically on Linux (KDE Wayland via XWayland) and Windows.
 */
@Composable
fun DesktopResizableFrame(
    window: ComposeWindow,
    windowState: WindowState,
    appWindow: AppWindow,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (windowState.placement == WindowPlacement.Floating && !appWindow.fullscreen) {
            ResizeHandle(window, Alignment.TopCenter, Modifier.fillMaxWidth().height(TOP_EDGE_THICKNESS), ResizeEdge.N)
            ResizeHandle(window, Alignment.BottomCenter, Modifier.fillMaxWidth().height(EDGE_THICKNESS), ResizeEdge.S)
            ResizeHandle(window, Alignment.CenterStart, Modifier.fillMaxHeight().width(EDGE_THICKNESS), ResizeEdge.W)
            ResizeHandle(window, Alignment.CenterEnd, Modifier.fillMaxHeight().width(EDGE_THICKNESS), ResizeEdge.E)
            ResizeHandle(window, Alignment.TopStart, Modifier.size(CORNER_SIZE), ResizeEdge.NW)
            ResizeHandle(window, Alignment.TopEnd, Modifier.size(CORNER_SIZE), ResizeEdge.NE)
            ResizeHandle(window, Alignment.BottomStart, Modifier.size(CORNER_SIZE), ResizeEdge.SW)
            ResizeHandle(window, Alignment.BottomEnd, Modifier.size(CORNER_SIZE), ResizeEdge.SE)
        }
    }
}

private enum class ResizeEdge(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean, val cursor: Int) {
    N(false, false, true, false, Cursor.N_RESIZE_CURSOR),
    S(false, false, false, true, Cursor.S_RESIZE_CURSOR),
    W(true, false, false, false, Cursor.W_RESIZE_CURSOR),
    E(false, true, false, false, Cursor.E_RESIZE_CURSOR),
    NW(true, false, true, false, Cursor.NW_RESIZE_CURSOR),
    NE(false, true, true, false, Cursor.NE_RESIZE_CURSOR),
    SW(true, false, false, true, Cursor.SW_RESIZE_CURSOR),
    SE(false, true, false, true, Cursor.SE_RESIZE_CURSOR),
}

@Composable
private fun BoxScope.ResizeHandle(
    window: ComposeWindow,
    alignment: Alignment,
    sizeModifier: Modifier,
    edge: ResizeEdge,
) {
    val icon = remember(edge) { PointerIcon(Cursor.getPredefinedCursor(edge.cursor)) }
    Box(
        modifier = Modifier
            .align(alignment)
            .then(sizeModifier)
            .pointerHoverIcon(icon)
            .pointerInput(window, edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startMouse = currentPointerLocation() ?: return@awaitEachGesture
                    val startBounds = Rectangle(window.bounds)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed || !event.buttons.isPrimaryPressed) break
                        change.consume()
                        val cur = currentPointerLocation() ?: continue
                        val minW = window.minimumSize.width.coerceAtLeast(160)
                        val minH = window.minimumSize.height.coerceAtLeast(120)
                        val next = computeResizedBounds(startBounds, cur.x - startMouse.x, cur.y - startMouse.y, edge, minW, minH)
                        if (next != window.bounds) window.bounds = next
                    }
                }
            },
    )
}

internal fun computeResizedBounds(
    start: Rectangle,
    dx: Int,
    dy: Int,
    left: Boolean,
    right: Boolean,
    top: Boolean,
    bottom: Boolean,
    minW: Int,
    minH: Int,
): Rectangle {
    var x = start.x
    var y = start.y
    var w = start.width
    var h = start.height
    if (right) w = (start.width + dx).coerceAtLeast(minW)
    if (bottom) h = (start.height + dy).coerceAtLeast(minH)
    if (left) {
        w = (start.width - dx).coerceAtLeast(minW)
        x = start.x + (start.width - w)
    }
    if (top) {
        h = (start.height - dy).coerceAtLeast(minH)
        y = start.y + (start.height - h)
    }
    return Rectangle(x, y, w, h)
}

private fun computeResizedBounds(start: Rectangle, dx: Int, dy: Int, edge: ResizeEdge, minW: Int, minH: Int): Rectangle =
    computeResizedBounds(start, dx, dy, edge.left, edge.right, edge.top, edge.bottom, minW, minH)

/** Keeps an undecorated maximized JFrame clear of the Windows taskbar on the window's active monitor. */
private fun applyWindowsMaximizedBounds(window: ComposeWindow) {
    runCatching {
        val gc = window.graphicsConfiguration ?: return
        val screen = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        window.maximizedBounds = Rectangle(
            screen.x + insets.left,
            screen.y + insets.top,
            (screen.width - insets.left - insets.right).coerceAtLeast(1),
            (screen.height - insets.top - insets.bottom).coerceAtLeast(1),
        )
    }
}

private fun currentPointerLocation(): Point? = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

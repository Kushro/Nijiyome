// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.patterns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_ctx_back
import app.nijiyome.ui.resources.rd_i1_ctx_more
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Sparkles redesign, lane I1. Pattern `ctx` of docs/redesign/artefacto/lab.js: the ⋮ button, the right click, Shift+F10 / the Menu key
// and the long-press open the same menu with the same actions. Pointer and keyboard get a dropdown at the pointer (it flips to stay in
// the window); touch gets a sheet with the same list. One description of the actions ([MenuEntry]) feeds both.

/** An entry of a context menu: an action, a submenu or a divider. */
sealed interface MenuEntry

/**
 * One action. [checked] = true draws a check (the current status in a "Change status" submenu); [destructive] uses the error color.
 * [shortcut] is shown at the end on wide windows ("Ctrl+D").
 */
@Immutable
data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val checked: Boolean? = null,
    val shortcut: String? = null,
) : MenuEntry

/** A submenu ("Change status ›"); it replaces the list in place, with a Back entry, in both the dropdown and the sheet. */
@Immutable
data class MenuSubmenu(val label: String, val entries: List<MenuEntry>, val icon: ImageVector? = null) : MenuEntry

data object MenuDivider : MenuEntry

/** How the menu was opened. */
enum class ContextMenuForm { DROPDOWN, SHEET }

/**
 * Open / closed state of one context menu and where it opens. [showAt] = dropdown at a point of the area (right click, keyboard);
 * [showSheet] = the touch sheet (long-press).
 */
@Stable
class ContextMenuState {
    var form: ContextMenuForm? by mutableStateOf(null)
        private set
    internal var at: Offset by mutableStateOf(Offset.Zero)

    val isOpen: Boolean get() = form != null

    fun showAt(offset: Offset) {
        at = offset
        form = ContextMenuForm.DROPDOWN
    }

    fun showSheet() {
        form = ContextMenuForm.SHEET
    }

    fun dismiss() {
        form = null
    }
}

@Composable
fun rememberContextMenuState(): ContextMenuState = remember { ContextMenuState() }

/**
 * The area that opens a context menu: right click opens it at the pointer, Shift+F10 or the Menu key with the focus inside opens it
 * near the top start, and [ContextMenuState.showSheet] (pass it as the card's `onLongClick`) opens the touch sheet. [entries] is read
 * when the menu opens; [sheetHeader] tops the sheet (poster, title, current status).
 *
 * ```kotlin
 * ContextMenuArea(entries = { titleActions(item) }, sheetHeader = { MenuSheetHeader(item.title, statusLabel) }) { menu ->
 *     TitleCard(item, onClick = { open(item) }, onLongClick = menu::showSheet)
 * }
 * ```
 */
@Composable
fun ContextMenuArea(
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    state: ContextMenuState = rememberContextMenuState(),
    enabled: Boolean = true,
    sheetHeader: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(ContextMenuState) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .onSizeChanged { size = it }
            .then(if (enabled) Modifier.contextMenuGestures(state) { size } else Modifier),
    ) {
        content(state)
        if (state.isOpen) {
            val list = remember(state.form) { entries() }
            when (state.form) {
                ContextMenuForm.SHEET -> MenuEntriesSheet(list, onDismiss = state::dismiss, header = sheetHeader)
                else -> Box(Modifier.offset { IntOffset(state.at.x.roundToInt(), state.at.y.roundToInt()) }.size(1.dp)) {
                    MenuEntriesDropdown(expanded = true, entries = list, onDismiss = state::dismiss)
                }
            }
        }
    }
}

/** Right click (secondary button press) and Shift+F10 / Menu key open [state] as a dropdown. Used by [ContextMenuArea]. */
fun Modifier.contextMenuGestures(state: ContextMenuState, areaSize: () -> IntSize): Modifier = this
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    val change = event.changes.firstOrNull() ?: continue
                    event.changes.forEach { it.consume() }
                    state.showAt(change.position)
                }
            }
        }
    }
    .onPreviewKeyEvent { e ->
        val menuKey = e.key == Key.Menu || (e.key == Key.F10 && e.isShiftPressed)
        if (e.type != KeyEventType.KeyDown || !menuKey) return@onPreviewKeyEvent false
        val s = areaSize()
        state.showAt(Offset(12f, s.height * 0.6f))
        true
    }

/**
 * The ⋮ button with the same entries: a dropdown under the button on wide windows, the sheet on phones.
 *
 * ```kotlin
 * ContextMenuButton(entries = { titleActions(item) }, contentDescription = moreFor(item.title))
 * ```
 */
@Composable
fun ContextMenuButton(
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(Res.string.rd_i1_ctx_more),
    sheetHeader: (@Composable () -> Unit)? = null,
    compact: Boolean = isCompactWindow(),
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = !open }) { Icon(Icons.Outlined.MoreVert, contentDescription = contentDescription) }
        if (open) {
            val list = remember { entries() }
            if (compact) {
                MenuEntriesSheet(list, onDismiss = { open = false }, header = sheetHeader)
            } else {
                MenuEntriesDropdown(expanded = true, entries = list, onDismiss = { open = false })
            }
        }
    }
}

/**
 * A dropdown of [entries] anchored to its parent; submenus replace the list in place. Usable on its own for an existing anchor (an
 * overflow button of a top bar).
 */
@Composable
fun MenuEntriesDropdown(expanded: Boolean, entries: List<MenuEntry>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var sub by remember(entries) { mutableStateOf<MenuSubmenu?>(null) }
    DropdownMenu(expanded = expanded, onDismissRequest = { sub = null; onDismiss() }, modifier = modifier) {
        val shown = sub
        if (shown != null) {
            DropdownMenuItem(
                text = { Text(shown.label) },
                leadingIcon = { Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(Res.string.rd_i1_ctx_back)) },
                onClick = { sub = null },
                modifier = Modifier.onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { sub = null; true } else false
                },
            )
            HorizontalDivider()
        }
        (shown?.entries ?: entries).forEach { entry ->
            when (entry) {
                is MenuDivider -> HorizontalDivider()
                is MenuSubmenu -> DropdownMenuItem(
                    text = { Text(entry.label) },
                    leadingIcon = entry.icon?.let { { Icon(it, contentDescription = null) } },
                    trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                    onClick = { sub = entry },
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { sub = entry; true } else false
                    },
                )
                is MenuAction -> {
                    val color = if (entry.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    DropdownMenuItem(
                        text = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = entry.icon?.let { { Icon(it, contentDescription = null) } },
                        trailingIcon = when {
                            entry.checked == true -> { { Icon(Icons.Outlined.Check, contentDescription = null) } }
                            entry.shortcut != null -> { { Text(entry.shortcut, style = MaterialTheme.typography.labelMedium) } }
                            else -> null
                        },
                        enabled = entry.enabled,
                        colors = MenuDefaults.itemColors(
                            textColor = color,
                            leadingIconColor = color,
                            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        onClick = {
                            sub = null
                            onDismiss()
                            entry.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** The touch form: a sheet with an optional [header] and one 56 dp row per entry; submenus replace the list in place. */
@Composable
fun MenuEntriesSheet(entries: List<MenuEntry>, onDismiss: () -> Unit, header: (@Composable () -> Unit)? = null) {
    var sub by remember(entries) { mutableStateOf<MenuSubmenu?>(null) }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        if (header != null) Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { header() }
        val shown = sub
        if (shown != null) {
            SheetRow(shown.label, Icons.Outlined.ChevronLeft, enabled = true, color = MaterialTheme.colorScheme.onSurface, trailing = null) { sub = null }
            HorizontalDivider()
        }
        (shown?.entries ?: entries).forEach { entry ->
            when (entry) {
                is MenuDivider -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                is MenuSubmenu -> SheetRow(entry.label, entry.icon, true, MaterialTheme.colorScheme.onSurface, Icons.Outlined.ChevronRight) { sub = entry }
                is MenuAction -> SheetRow(
                    entry.label,
                    entry.icon,
                    entry.enabled,
                    if (entry.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    if (entry.checked == true) Icons.Outlined.Check else null,
                ) {
                    onDismiss()
                    entry.onClick()
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, icon: ImageVector?, enabled: Boolean, color: Color, trailing: ImageVector?, onClick: () -> Unit) {
    val tint = if (enabled) color else color.copy(alpha = 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The usual sheet header: [title] in title style and an optional [supporting] line or composable (the status mark).
 *
 * ```kotlin
 * MenuSheetHeader(item.title) { StatusMark(item.listStatus!!, item.listStatusLabel!!, selected = true) }
 * ```
 */
@Composable
fun MenuSheetHeader(title: String, modifier: Modifier = Modifier, supporting: (@Composable () -> Unit)? = null) {
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (supporting != null) supporting()
    }
}

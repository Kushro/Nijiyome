// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_drop_add
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_action
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_done
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_failed
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_versions
import app.nijiyome.feature.shell.resources.rd_s1_drop_magnet_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_play
import app.nijiyome.feature.shell.resources.rd_s1_drop_sub_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_torrent_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_video_detail
import app.nijiyome.feature.shell.resources.shell_not_available
import app.nijiyome.model.ExtensionPackageInfo
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.patterns.ActionEmphasis
import app.nijiyome.ui.patterns.DropAction
import app.nijiyome.ui.patterns.DropKind
import app.nijiyome.ui.patterns.FileDropOverlay
import app.nijiyome.ui.patterns.FileDropResult
import app.nijiyome.ui.patterns.classifyDropName
import app.nijiyome.ui.platform.ExternalDrop
import app.nijiyome.ui.platform.cleanPastedPath
import app.nijiyome.ui.platform.externalDropTarget
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// The overlay and the result dialog are the shared `drop` pattern (core/ui patterns/FileDrop.kt); the shell keeps what each kind does.

internal data class DroppedItem(val kind: DropKind, val target: String, val name: String)

/** The first file, else the first line of the dropped text (a magnet from a browser, a `file://` URI turned into its path). */
internal fun ExternalDrop.toDroppedItem(): DroppedItem? {
    val raw = files.firstOrNull()
        ?: text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.let { line ->
            if (line.startsWith("file:")) cleanPastedPath(line) else line
        }
        ?: return null
    val name = if (raw.startsWith("magnet:", ignoreCase = true)) raw else raw.substringAfterLast('/').substringAfterLast('\\')
    return DroppedItem(classifyDropName(raw), raw, name)
}

/**
 * The window-wide drop target (desktop): while something is dragged over the window a full layer says it will be looked at; on
 * drop a dialog says what will happen to that kind and offers the action. Screens with their own target (Fuentes' install panel,
 * image search) keep theirs: the innermost target takes the drop. [openExternal] hands a magnet or a `.torrent` to the same path as
 * the command line (desktop `LaunchRouter`); null on Android, where files arrive by Open with / Share.
 */
@Composable
internal fun FileDropHost(
    enabled: Boolean,
    openExternal: ((String) -> Boolean)?,
    onOpenRoute: (Route) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DroppedItem?>(null) }
    Box(
        modifier.then(
            if (enabled) {
                Modifier.externalDropTarget(hovering = { hovering = it }) { drop ->
                    val item = drop.toDroppedItem()
                    pending = item
                    item != null
                }
            } else {
                Modifier
            },
        ),
    ) {
        content()
        // The platform does not say what is dragged until the drop, so the layer shows the generic kind.
        FileDropOverlay(visible = enabled && hovering)
    }
    pending?.let { item ->
        DropResultDialog(item, openExternal, onOpenRoute, onDismiss = { pending = null })
    }
}

@Composable
private fun DropResultDialog(
    item: DroppedItem,
    openExternal: ((String) -> Boolean)?,
    onOpenRoute: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val koin = getKoin()
    val extensions = remember(koin) { runCatching { koin.getOrNull<ExtensionRepository>() }.getOrNull() }
    // A .kai of an extension already installed with another version says both, so an older package never replaces a newer one unseen.
    var versions by remember(item) { mutableStateOf<String?>(null) }
    if (item.kind == DropKind.EXTENSION && extensions != null) {
        LaunchedEffect(item) {
            val pkg: ExtensionPackageInfo? = runCatching { extensions.peekPackage(item.target) }.getOrNull()
            val current = pkg?.let { p -> extensions.installed.value.firstOrNull { it.id == p.id } }
            if (pkg != null && current != null && current.version != pkg.version) {
                versions = getString(Res.string.rd_s1_drop_kai_versions, current.version, pkg.version)
            }
        }
    }
    val notAvailable = stringResource(Res.string.shell_not_available)

    fun act() {
        onDismiss()
        when (item.kind) {
            DropKind.EXTENSION -> if (extensions == null) {
                onOpenRoute(Route.Extensions)
            } else {
                scope.launch {
                    val result = extensions.installFromUri(item.target)
                    snackbar.show(
                        result.fold(
                            onSuccess = { getString(Res.string.rd_s1_drop_kai_done, it.name) },
                            onFailure = { getString(Res.string.rd_s1_drop_kai_failed) },
                        ),
                    )
                }
            }
            DropKind.TORRENT, DropKind.MAGNET -> if (openExternal?.invoke(item.target) != true) snackbar.show(notAvailable)
            DropKind.VIDEO -> onOpenRoute(
                Route.Player(
                    PlayerArgs(
                        media = MediaRef(),
                        title = item.name.substringBeforeLast('.'),
                        episode = 1,
                        localUri = item.target,
                        looseFile = true,
                    ),
                ),
            )
            DropKind.SUBTITLE, DropKind.UNSUPPORTED, DropKind.UNKNOWN -> Unit
        }
    }

    // The shell's own texts where it does something narrower than the pattern's defaults (no title matching, no saved subtitle).
    val detail = when (item.kind) {
        DropKind.EXTENSION -> versions ?: stringResource(Res.string.rd_s1_drop_kai_detail)
        DropKind.TORRENT -> stringResource(Res.string.rd_s1_drop_torrent_detail)
        DropKind.MAGNET -> stringResource(Res.string.rd_s1_drop_magnet_detail)
        DropKind.VIDEO -> stringResource(Res.string.rd_s1_drop_video_detail)
        DropKind.SUBTITLE -> stringResource(Res.string.rd_s1_drop_sub_detail)
        DropKind.UNSUPPORTED, DropKind.UNKNOWN -> null
    }
    val actionLabel = when (item.kind) {
        DropKind.EXTENSION -> stringResource(Res.string.rd_s1_drop_kai_action)
        DropKind.TORRENT, DropKind.MAGNET -> stringResource(Res.string.rd_s1_drop_add)
        DropKind.VIDEO -> stringResource(Res.string.rd_s1_drop_play)
        DropKind.SUBTITLE, DropKind.UNSUPPORTED, DropKind.UNKNOWN -> null
    }
    FileDropResult(
        kind = item.kind,
        name = item.name,
        actions = listOfNotNull(actionLabel?.let { DropAction(it, ::act, ActionEmphasis.FILLED) }),
        onDismiss = onDismiss,
        detail = detail,
    )
}

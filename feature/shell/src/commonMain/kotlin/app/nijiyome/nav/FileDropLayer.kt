// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_drop_add
import app.nijiyome.feature.shell.resources.rd_s1_drop_cancel
import app.nijiyome.feature.shell.resources.rd_s1_drop_formats
import app.nijiyome.feature.shell.resources.rd_s1_drop_hover
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_action
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_done
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_failed
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_title
import app.nijiyome.feature.shell.resources.rd_s1_drop_kai_versions
import app.nijiyome.feature.shell.resources.rd_s1_drop_magnet_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_magnet_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_magnet_title
import app.nijiyome.feature.shell.resources.rd_s1_drop_none_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_none_title
import app.nijiyome.feature.shell.resources.rd_s1_drop_ok
import app.nijiyome.feature.shell.resources.rd_s1_drop_play
import app.nijiyome.feature.shell.resources.rd_s1_drop_sub_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_sub_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_sub_title
import app.nijiyome.feature.shell.resources.rd_s1_drop_torrent_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_torrent_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_torrent_title
import app.nijiyome.feature.shell.resources.rd_s1_drop_video_detail
import app.nijiyome.feature.shell.resources.rd_s1_drop_video_label
import app.nijiyome.feature.shell.resources.rd_s1_drop_video_title
import app.nijiyome.feature.shell.resources.shell_not_available
import app.nijiyome.model.ExtensionPackageInfo
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PlayerArgs
import app.nijiyome.ui.platform.ExternalDrop
import app.nijiyome.ui.platform.cleanPastedPath
import app.nijiyome.ui.platform.externalDropTarget
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// Lane S1 note: lane I1 writes a reusable drop overlay under core/ui/.../patterns/ in parallel; this one lives in the shell until the
// integrator swaps it for that pattern.

/** What a dropped file or link is, by its name (Sparkles `drop` pattern `classify`). */
internal enum class DropKind { KAI, TORRENT, MAGNET, VIDEO, SUBTITLE, NONE }

internal data class DroppedItem(val kind: DropKind, val target: String, val name: String)

internal fun classifyDrop(name: String): DropKind {
    val n = name.trim().lowercase()
    if (n.startsWith("magnet:")) return DropKind.MAGNET
    return when (n.substringAfterLast('.', "")) {
        "kai" -> DropKind.KAI
        "torrent" -> DropKind.TORRENT
        in VIDEO_EXTENSIONS -> DropKind.VIDEO
        in SUBTITLE_EXTENSIONS -> DropKind.SUBTITLE
        else -> DropKind.NONE
    }
}

/** The first file, else the first line of the dropped text (a magnet from a browser, a `file://` URI). */
internal fun ExternalDrop.toDroppedItem(): DroppedItem? {
    val raw = files.firstOrNull()
        ?: text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.let { line ->
            if (line.startsWith("file:")) cleanPastedPath(line) else line
        }
        ?: return null
    val name = if (raw.startsWith("magnet:", ignoreCase = true)) raw else raw.substringAfterLast('/').substringAfterLast('\\')
    return DroppedItem(classifyDrop(raw), raw, name)
}

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v", "ts")
private val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")

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
        DropHoverLayer(visible = enabled && hovering)
    }
    pending?.let { item ->
        DropResultDialog(item, openExternal, onOpenRoute, onDismiss = { pending = null })
    }
}

@Composable
private fun DropHoverLayer(visible: Boolean) {
    val level = LocalMotion.current.level
    val still = level == MotionLevel.OFF
    val ms = if (level == MotionLevel.FULL) 180 else 120
    AnimatedVisibility(
        visible = visible,
        enter = if (still) EnterTransition.None else fadeIn(tween(ms)),
        exit = if (still) ExitTransition.None else fadeOut(tween(ms)),
    ) {
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.55f)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .border(2.dp, scheme.primary, RoundedCornerShape(24.dp))
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(
                    Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(40.dp))
                    Text(stringResource(Res.string.rd_s1_drop_hover), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        stringResource(Res.string.rd_s1_drop_formats),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private class DropCopy(val label: StringResource, val title: StringResource, val detail: StringResource, val icon: ImageVector, val action: StringResource?)

private fun DropKind.copy(): DropCopy = when (this) {
    DropKind.KAI -> DropCopy(Res.string.rd_s1_drop_kai_label, Res.string.rd_s1_drop_kai_title, Res.string.rd_s1_drop_kai_detail, Icons.Outlined.Extension, Res.string.rd_s1_drop_kai_action)
    DropKind.TORRENT -> DropCopy(Res.string.rd_s1_drop_torrent_label, Res.string.rd_s1_drop_torrent_title, Res.string.rd_s1_drop_torrent_detail, Icons.Outlined.CloudDownload, Res.string.rd_s1_drop_add)
    DropKind.MAGNET -> DropCopy(Res.string.rd_s1_drop_magnet_label, Res.string.rd_s1_drop_magnet_title, Res.string.rd_s1_drop_magnet_detail, Icons.Outlined.Link, Res.string.rd_s1_drop_add)
    DropKind.VIDEO -> DropCopy(Res.string.rd_s1_drop_video_label, Res.string.rd_s1_drop_video_title, Res.string.rd_s1_drop_video_detail, Icons.Outlined.Movie, Res.string.rd_s1_drop_play)
    DropKind.SUBTITLE -> DropCopy(Res.string.rd_s1_drop_sub_label, Res.string.rd_s1_drop_sub_title, Res.string.rd_s1_drop_sub_detail, Icons.Outlined.Subtitles, null)
    DropKind.NONE -> DropCopy(Res.string.rd_s1_drop_none_label, Res.string.rd_s1_drop_none_title, Res.string.rd_s1_drop_formats, Icons.Outlined.Block, null)
}

@Composable
private fun DropResultDialog(
    item: DroppedItem,
    openExternal: ((String) -> Boolean)?,
    onOpenRoute: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = item.kind.copy()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val koin = getKoin()
    val extensions = remember(koin) { runCatching { koin.getOrNull<ExtensionRepository>() }.getOrNull() }
    // A .kai of an extension already installed with another version says both, so an older package never replaces a newer one unseen.
    var versions by remember(item) { mutableStateOf<String?>(null) }
    if (item.kind == DropKind.KAI && extensions != null) {
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
            DropKind.KAI -> if (extensions == null) {
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
            DropKind.SUBTITLE, DropKind.NONE -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(copy.icon, contentDescription = null) },
        title = { Text(stringResource(copy.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(copy.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(versions ?: stringResource(copy.detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val action = copy.action
            if (action != null) {
                Button(onClick = ::act) { Text(stringResource(action)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rd_s1_drop_ok)) }
            }
        },
        dismissButton = if (copy.action != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.rd_s1_drop_cancel)) } }
        } else {
            null
        },
    )
}

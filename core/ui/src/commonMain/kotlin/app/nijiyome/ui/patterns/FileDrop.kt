// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.ui.platform.ExternalDrop
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_drop_cancel
import app.nijiyome.ui.resources.rd_i1_drop_detail_extension
import app.nijiyome.ui.resources.rd_i1_drop_detail_magnet
import app.nijiyome.ui.resources.rd_i1_drop_detail_subtitle
import app.nijiyome.ui.resources.rd_i1_drop_detail_torrent
import app.nijiyome.ui.resources.rd_i1_drop_detail_unsupported
import app.nijiyome.ui.resources.rd_i1_drop_detail_video
import app.nijiyome.ui.resources.rd_i1_drop_kind_extension
import app.nijiyome.ui.resources.rd_i1_drop_kind_magnet
import app.nijiyome.ui.resources.rd_i1_drop_kind_subtitle
import app.nijiyome.ui.resources.rd_i1_drop_kind_torrent
import app.nijiyome.ui.resources.rd_i1_drop_kind_unknown
import app.nijiyome.ui.resources.rd_i1_drop_kind_unsupported
import app.nijiyome.ui.resources.rd_i1_drop_kind_video
import app.nijiyome.ui.resources.rd_i1_drop_ok
import app.nijiyome.ui.resources.rd_i1_drop_title_extension
import app.nijiyome.ui.resources.rd_i1_drop_title_magnet
import app.nijiyome.ui.resources.rd_i1_drop_title_subtitle
import app.nijiyome.ui.resources.rd_i1_drop_title_torrent
import app.nijiyome.ui.resources.rd_i1_drop_title_unsupported
import app.nijiyome.ui.resources.rd_i1_drop_title_video
import app.nijiyome.ui.resources.rd_i1_drop_verb_extension
import app.nijiyome.ui.resources.rd_i1_drop_verb_magnet
import app.nijiyome.ui.resources.rd_i1_drop_verb_subtitle
import app.nijiyome.ui.resources.rd_i1_drop_verb_torrent
import app.nijiyome.ui.resources.rd_i1_drop_verb_unknown
import app.nijiyome.ui.resources.rd_i1_drop_verb_unsupported
import app.nijiyome.ui.resources.rd_i1_drop_verb_video
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane I1. Pattern `drop` of docs/redesign/artefacto/lab.js: one target layer covers the window while something is
// dragged over it and says what will happen; on drop a dialog (a sheet on phones) offers the concrete actions, and what Nijiyome cannot
// open is refused with the list of formats it does open. The shell (lane S1) wires this to `Modifier.externalDropTarget` on desktop
// and to the "Open with" / "Share" intents on Android, which land directly on [FileDropResult].

/** What a dropped or shared file is, by its name (or a magnet link). */
enum class DropKind {
    /** A `.kai` extension bundle (compatible with Tatakai Next). */
    EXTENSION,
    TORRENT,
    MAGNET,

    /** A local video (.mkv, .mp4, .webm, .avi, .mov, .m4v, .ts). */
    VIDEO,

    /** A subtitle (.ass, .ssa, .srt, .vtt, .sub). */
    SUBTITLE,

    /** Something Nijiyome does not open. */
    UNSUPPORTED,

    /** Still being dragged: the platform does not say what it is until the drop. */
    UNKNOWN,
}

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "webm", "avi", "mov", "m4v", "ts")
private val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt", "sub")

/**
 * The kind of a file name, path, URI or pasted text.
 *
 * ```kotlin
 * classifyDropName("Grand Blue S02 [1080p].torrent")   // DropKind.TORRENT
 * ```
 */
fun classifyDropName(name: String): DropKind {
    val n = name.trim().lowercase()
    if (n.isEmpty()) return DropKind.UNSUPPORTED
    if (n.startsWith("magnet:")) return DropKind.MAGNET
    val ext = n.substringBefore('?').substringAfterLast('/').substringAfterLast('\\').substringAfterLast('.', missingDelimiterValue = "")
    return when {
        ext == "kai" -> DropKind.EXTENSION
        ext == "torrent" -> DropKind.TORRENT
        ext in VIDEO_EXTENSIONS -> DropKind.VIDEO
        ext in SUBTITLE_EXTENSIONS -> DropKind.SUBTITLE
        else -> DropKind.UNSUPPORTED
    }
}

/** The kind of a drop: its first file, else its text (a magnet link dragged from a browser). */
fun ExternalDrop.kind(): DropKind {
    val first = files.firstOrNull()
    if (first != null) return classifyDropName(first)
    val t = text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return classifyDropName(t)
}

/** What to show as the dropped thing's name: the file name without its folders, or the text's first line. */
fun ExternalDrop.displayName(): String {
    val first = files.firstOrNull()
    if (first != null) return first.substringAfterLast('/').substringAfterLast('\\')
    return text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
}

/** The icon of a [DropKind]. */
val DropKind.icon: ImageVector
    get() = when (this) {
        DropKind.EXTENSION -> Icons.Outlined.Extension
        DropKind.TORRENT -> Icons.Outlined.CloudDownload
        DropKind.MAGNET -> Icons.Outlined.Link
        DropKind.VIDEO -> Icons.Outlined.Movie
        DropKind.SUBTITLE -> Icons.Outlined.Subtitles
        DropKind.UNSUPPORTED -> Icons.Outlined.Block
        DropKind.UNKNOWN -> Icons.Outlined.Description
    }

/** Short name of the kind ("Torrent file"). */
@Composable
fun DropKind.label(): String = stringResource(
    when (this) {
        DropKind.EXTENSION -> Res.string.rd_i1_drop_kind_extension
        DropKind.TORRENT -> Res.string.rd_i1_drop_kind_torrent
        DropKind.MAGNET -> Res.string.rd_i1_drop_kind_magnet
        DropKind.VIDEO -> Res.string.rd_i1_drop_kind_video
        DropKind.SUBTITLE -> Res.string.rd_i1_drop_kind_subtitle
        DropKind.UNSUPPORTED -> Res.string.rd_i1_drop_kind_unsupported
        DropKind.UNKNOWN -> Res.string.rd_i1_drop_kind_unknown
    },
)

/** What dropping does, shown while hovering ("Drop to add the torrent"). */
@Composable
fun DropKind.hoverText(): String = stringResource(
    when (this) {
        DropKind.EXTENSION -> Res.string.rd_i1_drop_verb_extension
        DropKind.TORRENT -> Res.string.rd_i1_drop_verb_torrent
        DropKind.MAGNET -> Res.string.rd_i1_drop_verb_magnet
        DropKind.VIDEO -> Res.string.rd_i1_drop_verb_video
        DropKind.SUBTITLE -> Res.string.rd_i1_drop_verb_subtitle
        DropKind.UNSUPPORTED -> Res.string.rd_i1_drop_verb_unsupported
        DropKind.UNKNOWN -> Res.string.rd_i1_drop_verb_unknown
    },
)

/** Title of the result dialog ("Add torrent"). */
@Composable
fun DropKind.resultTitle(): String = stringResource(
    when (this) {
        DropKind.EXTENSION -> Res.string.rd_i1_drop_title_extension
        DropKind.TORRENT -> Res.string.rd_i1_drop_title_torrent
        DropKind.MAGNET -> Res.string.rd_i1_drop_title_magnet
        DropKind.VIDEO -> Res.string.rd_i1_drop_title_video
        DropKind.SUBTITLE -> Res.string.rd_i1_drop_title_subtitle
        DropKind.UNSUPPORTED, DropKind.UNKNOWN -> Res.string.rd_i1_drop_title_unsupported
    },
)

/** Default explanation under the title; callers may pass a more specific one (the matched title and episode). */
@Composable
fun DropKind.resultDetail(): String = stringResource(
    when (this) {
        DropKind.EXTENSION -> Res.string.rd_i1_drop_detail_extension
        DropKind.TORRENT -> Res.string.rd_i1_drop_detail_torrent
        DropKind.MAGNET -> Res.string.rd_i1_drop_detail_magnet
        DropKind.VIDEO -> Res.string.rd_i1_drop_detail_video
        DropKind.SUBTITLE -> Res.string.rd_i1_drop_detail_subtitle
        DropKind.UNSUPPORTED, DropKind.UNKNOWN -> Res.string.rd_i1_drop_detail_unsupported
    },
)

/** One action of the drop result ("Add and watch", "Download only"). The first one should be [ActionEmphasis.FILLED]. */
@Immutable
data class DropAction(val label: String, val onClick: () -> Unit, val emphasis: ActionEmphasis = ActionEmphasis.TONAL)

/**
 * The layer over the whole window while files are dragged over it (desktop). Put it last in the window's root `Box`, with
 * `visible = hovering` from `Modifier.externalDropTarget(hovering = { hovering = it }, …)`. It does not take pointer input, so the
 * system drop still reaches the target below. [kind] = [DropKind.UNKNOWN] while the platform does not tell the type.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize().externalDropTarget(hovering = { over = it }, onDrop = { dropped = it; true })) {
 *     App(); FileDropOverlay(visible = over)
 * }
 * ```
 */
@Composable
fun FileDropOverlay(visible: Boolean, modifier: Modifier = Modifier, kind: DropKind = DropKind.UNKNOWN, name: String? = null) {
    AnimatedVisibility(visible = visible, modifier = modifier.fillMaxSize(), enter = overlayEnter(), exit = overlayExit()) {
        val bad = kind == DropKind.UNSUPPORTED
        val accent = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(2.dp, accent),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Icon(kind.icon, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
                    Text(kind.hoverText(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (!name.isNullOrBlank()) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Pill(kind.label(), tone = if (bad) PillTone.ERR else PillTone.NEUTRAL)
                }
            }
        }
    }
}

/**
 * What to do with a dropped or shared file: a dialog on wide windows, a sheet on phones. The first action takes the focus. For
 * [DropKind.UNSUPPORTED] pass no actions: it then explains the accepted formats and offers only "Got it".
 *
 * ```kotlin
 * dropped?.let { d ->
 *     FileDropResult(d.kind(), d.displayName(), onDismiss = { dropped = null }, actions = listOf(
 *         DropAction(addAndWatch, { addTorrent(d, watch = true); dropped = null }, ActionEmphasis.FILLED),
 *         DropAction(downloadOnly, { addTorrent(d, watch = false); dropped = null })))
 * }
 * ```
 */
@Composable
fun FileDropResult(
    kind: DropKind,
    name: String,
    actions: List<DropAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /** Badge prefix on Android ("From Files"). */
    origin: String? = null,
    compact: Boolean = isCompactWindow(),
) {
    val body: @Composable ColumnScope.() -> Unit = {
        FileDropResultBody(kind, name, actions, onDismiss, detail ?: kind.resultDetail(), origin)
    }
    if (compact) {
        AdaptiveSheet(onDismissRequest = onDismiss, modifier = modifier) { Column(Modifier.padding(horizontal = 24.dp), content = body) }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            TrackModal()
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = modifier.widthIn(min = 320.dp, max = 520.dp),
            ) {
                Column(Modifier.padding(24.dp), content = body)
            }
        }
    }
}

@Composable
private fun ColumnScope.FileDropResultBody(
    kind: DropKind,
    name: String,
    actions: List<DropAction>,
    onDismiss: () -> Unit,
    detail: String,
    origin: String?,
) {
    val bad = kind == DropKind.UNSUPPORTED || kind == DropKind.UNKNOWN
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            kind.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill(if (origin != null) "$origin · ${kind.label()}" else kind.label(), tone = if (bad) PillTone.ERR else PillTone.NEUTRAL)
            Text(kind.resultTitle(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
    }
    if (name.isNotBlank()) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(shape = NijiyomeControls.shape, onClick = onDismiss) {
            Text(stringResource(if (actions.isEmpty()) Res.string.rd_i1_drop_ok else Res.string.rd_i1_drop_cancel))
        }
        actions.forEachIndexed { i, action ->
            ActionButton(
                label = action.label,
                onClick = action.onClick,
                emphasis = action.emphasis,
                modifier = if (i == 0) Modifier.focusOnOpen() else Modifier,
            )
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AppSnackbar
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.feature.torrents.vm.TorrentMessage
import app.nijiyome.model.AppError
import app.nijiyome.model.LoadState
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentLibraryItem
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.state.userMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

// Shared pieces of the torrent client screens (11 §3): states, formatting, error texts, pills.

/** Mono text style for figures (01 §3: JetBrains Mono for numbers, desktop `--tk-font-mono`). */
@Composable
internal fun monoStyle(base: TextStyle = MaterialTheme.typography.bodyMedium): TextStyle = base.copy(fontFamily = NijiyomeFonts.families.mono)

/** Loading indicator only after 150 ms (02 §5), nothing before. */
@Composable
internal fun DelayedLoading(modifier: Modifier = Modifier) {
    if (rememberAfterDelay(150)) LoadingState(modifier)
}

/** The three states of a [LoadState] with torrent-aware error texts. */
@Composable
internal fun <T> TorrentLoadState(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorTitle: String? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> DelayedLoading(modifier)
        is LoadState.Err -> ErrorState(
            message = torrentErrorMessage(state.cause),
            retryLabel = stringResource(UiR.string.ui_retry),
            onRetry = onRetry,
            modifier = modifier,
            title = errorTitle ?: stringResource(UiR.string.ui_error_title),
        )
        is LoadState.Ok -> content(state.value)
    }
}

/** Torrent error codes of TorrentRepositoryImpl (`torrent:<code>[:detail]`) -> readable text; anything else -> the shared mapping. */
@Composable
internal fun torrentErrorMessage(error: AppError): String {
    val msg = (error as? AppError.Unknown)?.msg.orEmpty()
    if (!msg.startsWith("torrent:")) return error.userMessage()
    val parts = msg.split(':')
    return when (parts.getOrNull(1)) {
        "missing" -> stringResource(Res.string.torrent_shell_missing)
        "moved" -> stringResource(Res.string.torrent_android_moved)
        "no_space" -> stringResource(Res.string.torrent_android_no_space, formatBytes(parts.getOrNull(2)?.toLongOrNull() ?: 0))
        "no_file" -> stringResource(Res.string.torrent_android_no_file)
        "background_off" -> stringResource(Res.string.rd_s8_tor_background_off)
        "incomplete" -> stringResource(Res.string.torrent_android_incomplete)
        "export_unsupported" -> stringResource(Res.string.torrent_android_export_unsupported)
        "invalid_source" -> stringResource(Res.string.torrent_android_invalid_source)
        "invalid_file" -> stringResource(Res.string.torrent_add_invalid_file)
        else -> stringResource(Res.string.torrent_overview_action_error)
    }
}

/** Sends [TorrentMessage]s of a ViewModel to the global snackbar. */
@Composable
internal fun MessagesEffect(messages: Flow<TorrentMessage>, snackbar: AppSnackbar) {
    val texts = MessageTexts.current()
    LaunchedEffect(messages, snackbar) {
        messages.collect { m ->
            val text = when (m) {
                is TorrentMessage.Removed -> if (m.count == 1) texts.removedOne else getPluralString(Res.plurals.torrent_library_removed, m.count, m.count)
                is TorrentMessage.Exported -> texts.exported
                is TorrentMessage.Saved -> texts.saved
                is TorrentMessage.Failed -> texts.failed
            }
            snackbar.show(text)
        }
    }
}

/** Pre-resolved snackbar strings (collected outside composition). */
internal data class MessageTexts(val removedOne: String, val exported: String, val saved: String, val failed: String) {
    companion object {
        @Composable
        fun current(): MessageTexts = MessageTexts(
            removedOne = stringResource(Res.string.torrent_overview_removed),
            exported = stringResource(Res.string.torrent_android_exported),
            saved = stringResource(UiR.string.ui_settings_saved),
            failed = stringResource(Res.string.torrent_library_action_failed),
        )
    }
}

/** Tone of a torrent state pill (12 §3.9: downloading/seeding OK, paused NEUTRAL, error ERR, initializing PRIMARY). */
internal fun TorrentState.tone(): PillTone = when (this) {
    TorrentState.DOWNLOADING, TorrentState.SEEDING -> PillTone.OK
    TorrentState.PAUSED -> PillTone.NEUTRAL
    TorrentState.ERROR -> PillTone.ERR
    TorrentState.INITIALIZING -> PillTone.PRIMARY
}

@Composable
internal fun StatePill(state: TorrentState, modifier: Modifier = Modifier) {
    Pill(text = stringResource(state.labelRes()), modifier = modifier, tone = state.tone())
}

/** "42%" / "42 %" (locale-specific through `torrent_fmt_percent`). */
@Composable
internal fun percentText(fraction: Double): String =
    stringResource(Res.string.torrent_fmt_percent, (fraction.coerceIn(0.0, 1.0) * 100).roundToInt())

/** Desktop duration format: `12 s`, `3 min 4 s`, `1 h 5 min`, `2 d 3 h`; null -> `—`. */
@Composable
internal fun durationText(secs: Long?): String {
    if (secs == null || secs < 0) return stringResource(Res.string.torrent_fmt_none)
    val d = secs / 86_400
    val h = (secs % 86_400) / 3_600
    val m = (secs % 3_600) / 60
    val s = secs % 60
    return when {
        d > 0 -> stringResource(Res.string.torrent_fmt_dur_dh, d, h)
        h > 0 -> stringResource(Res.string.torrent_fmt_dur_hm, h, m)
        m > 0 -> stringResource(Res.string.torrent_fmt_dur_ms, m, s)
        else -> stringResource(Res.string.torrent_fmt_dur_s, s)
    }
}

/** `HistoryEntry.animeIdOf` inverse: "123" -> anilist 123, "mal:45" -> MAL 45. */
internal fun mediaRefOf(mediaId: String?): MediaRef {
    val id = mediaId?.trim().orEmpty()
    return when {
        id.startsWith("mal:") -> MediaRef(malId = id.removePrefix("mal:").toIntOrNull())
        else -> MediaRef(anilistId = id.toIntOrNull())
    }
}

/** Player arguments for a file of a library torrent (client "Play this file", Library "Play"). */
internal fun playArgsFor(item: TorrentLibraryItem?, torrentId: String, fileIndex: Int?, episode: Int?, fallbackTitle: String): PlayerArgs =
    PlayerArgs(
        media = mediaRefOf(item?.mediaId),
        title = item?.series ?: fallbackTitle,
        episode = episode ?: item?.episode ?: 1,
        torrentId = torrentId,
        fileIndex = fileIndex,
    )

internal fun magnetOf(id: String): String = "magnet:?xt=urn:btih:$id"

internal fun copyToClipboard(clipboard: ClipboardManager, text: String) {
    runCatching { clipboard.setText(AnnotatedString(text)) }
}

/** Label + value cell of the Overview grid. */
@Composable
internal fun FigureCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = monoStyle(MaterialTheme.typography.bodyLarge), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Inline banner (error / info) colored by role. */
@Composable
internal fun Banner(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

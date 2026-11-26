// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.library

import app.nijiyome.feature.library.resources.Res
import app.nijiyome.feature.library.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.AdaptiveSheet
import app.nijiyome.designsystem.components.AdaptiveSheetScrollColumn
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.model.ListEntry
import app.nijiyome.model.ListStatus
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.Media
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.look.slot
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.patterns.ListStatusPicker
import app.nijiyome.ui.patterns.ProgressStepper
import app.nijiyome.ui.patterns.toListStatus
import app.nijiyome.ui.state.rememberIsOnline
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import kotlin.math.roundToInt
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.list_add
import app.nijiyome.ui.resources.list_favorite
import app.nijiyome.ui.resources.list_queued_offline
import app.nijiyome.ui.resources.list_remove
import app.nijiyome.designsystem.theme.NijiyomeControls

// Parity wave A origin (worker q). Sparkles S7: the entry editor uses the shared status picker and progress stepper (pattern `stp`);
// `ListButton` (mounted by Details and Home) keeps its signature.

/**
 * Editor of one entry (LIB-06): the six statuses in view (radio group), progress with − and +, score (0..100, 0 = no score),
 * favorite and "Quitar de la lista". Each change saves at once (local first, queued for the linked providers).
 * [onRemove] replaces the plain removal (Library passes one with undo); without it removal calls `onStatus(null)` and closes.
 */
@Composable
internal fun ListEntryEditor(
    entry: ListEntry?,
    onDismiss: () -> Unit,
    onStatus: (ListStatus?) -> Unit,
    onProgress: (Int) -> Unit,
    onScore: (Int?) -> Unit,
    onFavorite: (Boolean) -> Unit,
    title: String = entry?.title.orEmpty(),
    totalEpisodes: Int? = entry?.totalEpisodes,
    onRemove: (() -> Unit)? = null,
) {
    var status by remember(entry?.status) { mutableStateOf(entry?.status) }
    var progress by remember(entry?.progress) { mutableIntStateOf(entry?.progress ?: 0) }
    var score by remember(entry?.score) { mutableFloatStateOf((entry?.score ?: 0).toFloat()) }
    var favorite by remember(entry?.favorite) { mutableStateOf(entry?.favorite == true) }
    AdaptiveSheet(onDismissRequest = onDismiss) {
        AdaptiveSheetScrollColumn(
            Modifier.decor(DecorRegion.SHEET).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry?.poster != null) MediaCover(url = entry.poster, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.rd_s7_editor_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(Res.string.rd_s7_editor_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(stringResource(Res.string.library_lists_status), style = MaterialTheme.typography.titleSmall)
                ListStatusPicker(
                    selected = status?.slot(),
                    onSelect = { slot ->
                        val s = slot.toListStatus()
                        status = s
                        if (s == ListStatus.COMPLETED && totalEpisodes != null) progress = totalEpisodes
                        onStatus(s)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status != null) {
                    Text(stringResource(Res.string.library_lists_progress_label), style = MaterialTheme.typography.titleSmall)
                    ProgressStepper(
                        value = progress,
                        onValueChange = {
                            progress = it
                            onProgress(it)
                        },
                        max = totalEpisodes ?: Int.MAX_VALUE,
                        total = totalEpisodes,
                    )
                    Text(
                        if (score.roundToInt() > 0) {
                            stringResource(Res.string.library_lists_score_label, score.roundToInt())
                        } else {
                            stringResource(Res.string.library_lists_score_none)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = score,
                        onValueChange = { score = (it / 5f).roundToInt() * 5f },
                        onValueChangeFinished = { onScore(score.roundToInt().takeIf { it > 0 }) },
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                    Text(
                        stringResource(Res.string.rd_s7_score_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        favorite = !favorite
                        onFavorite(favorite)
                    }, shape = NijiyomeControls.shape) {
                        Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(if (favorite) Res.string.rd_s7_favorite_on else Res.string.rd_s7_add_favorite),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (status != null) {
                        TextButton(
                            onClick = {
                                if (onRemove != null) {
                                    onRemove()
                                } else {
                                    status = null
                                    onStatus(null)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = NijiyomeControls.shape,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(UiR.string.list_remove), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Añadir a lista" button of Details (classic `WatchlistButton`), mounted in the Details header / floating toolbar.
 * Label = current status or "Añadir a lista"; the menu lists every status, "Editar…" (the editor sheet), "Quitar de la lista"
 * and the favorite toggle. Writes through `ListsRepository`; offline writes are queued (snackbar "Se sincronizará").
 * [compact] = icon-only variant for the floating toolbar.
 */
@Composable
fun ListButton(
    media: Media,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Ficha "Primero el episodio": size of the compact icon button (Details matches its 52 dp action row). */
    compactButtonModifier: Modifier = Modifier,
    /**
     * Details header ("Cabecera de la ficha"): draws the control that opens the menu instead of the built-in buttons; [label] is the
     * current status or "Añadir a lista", [entry] the list row (null = not on a list).
     */
    anchor: (@Composable (label: String, entry: ListEntry?, open: () -> Unit) -> Unit)? = null,
) {
    // Without the lists service (desktop, for now) there is nothing to add the title to: the button is not drawn.
    val koin = getKoin()
    val lists = remember(koin) { koin.getOrNull<ListsRepository>() } ?: return
    val entryFlow = remember(media.ref) { lists.entry(media.ref) }
    val entry by entryFlow.collectAsState(initial = null)
    // the favorite flag on its own: a favorite that is on no list has no entry
    val favoriteFlow = remember(media.ref) { lists.isFavorite(media.ref) }
    val favorite by favoriteFlow.collectAsState(initial = false)
    val accountsFlow = remember(lists) { lists.accounts }
    val accounts by accountsFlow.collectAsState()
    val online = rememberIsOnline()
    val scope = rememberCoroutineScope()
    val snackbar = LocalAppSnackbar.current
    var menu by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }

    val write: (suspend () -> Result<Unit>) -> Unit = { block ->
        scope.launch {
            val result = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) {
                if (accounts.isNotEmpty() && !online) snackbar.show(org.jetbrains.compose.resources.getString(UiR.string.list_queued_offline))
            } else {
                snackbar.show(org.jetbrains.compose.resources.getString(Res.string.library_lists_write_failed))
            }
        }
    }

    val current = entry
    val label = current?.let { stringResource(it.status.labelRes()) } ?: stringResource(UiR.string.list_add)
    Box(modifier) {
        if (anchor != null) {
            anchor(label, current) { menu = true }
        } else if (compact) {
            FilledTonalIconButton(onClick = { menu = true }, modifier = compactButtonModifier.semantics { contentDescription = label }) {
                Icon(if (current != null) Icons.Outlined.Bookmarks else Icons.Outlined.BookmarkAdd, contentDescription = null)
            }
        } else {
            FilledTonalButton(onClick = { menu = true }, shape = NijiyomeControls.shape) {
                Icon(if (current != null) Icons.Outlined.Bookmarks else Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, modifier = Modifier.padding(horizontal = 6.dp))
                if (favorite) {
                    Icon(Icons.Filled.Favorite, contentDescription = stringResource(UiR.string.list_favorite), modifier = Modifier.size(16.dp))
                }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ListStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.labelRes())) },
                    onClick = {
                        menu = false
                        write { lists.setStatus(media, s) }
                    },
                    trailingIcon = if (current?.status == s) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
            HorizontalDivider()
            if (current != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.library_lists_edit)) },
                    onClick = {
                        menu = false
                        editor = true
                    },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                )
            }
            val fav = favorite
            DropdownMenuItem(
                text = { Text((if (fav) stringResource(Res.string.library_favorites_remove) else stringResource(UiR.string.list_favorite))) },
                onClick = {
                    menu = false
                    write { lists.setFavorite(media, !fav) }
                },
                leadingIcon = { Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null) },
            )
            if (current != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(UiR.string.list_remove)) },
                    onClick = {
                        menu = false
                        write { lists.setStatus(media, null) }
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                )
            }
        }
    }
    if (editor && current != null) {
        ListEntryEditor(
            entry = current,
            onDismiss = { editor = false },
            onStatus = { s -> write { lists.setStatus(media, s) } },
            onProgress = { p -> write { lists.setProgress(media.ref, p) } },
            onScore = { sc -> write { lists.setScore(media.ref, sc) } },
            onFavorite = { f -> write { lists.setFavorite(media, f) } },
            title = media.displayTitle(LocalTitleLanguage.current),
            totalEpisodes = current.totalEpisodes ?: media.episodes,
        )
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_quality
import app.nijiyome.feature.player.resources.player_quality_auto
import app.nijiyome.model.Quality
import app.nijiyome.model.RankedSource
import org.jetbrains.compose.resources.stringResource

// Hito 6 (desktop, Tatakai Next `FlowState::qualities` / `source_for_quality`): the Quality menu of an mpv session, whose stream has
// no renditions of its own to choose from, lists the qualities of the episode's sources; choosing one plays the best-ranked source of
// that quality, and Auto goes back to the one the ranking recommends.

/** The known qualities of the playable [sources], best first. */
internal fun sourceQualities(sources: List<RankedSource>): List<Quality> =
    sources.filter { it.extEnabled && it.candidate.quality != Quality.UNKNOWN }.map { it.candidate.quality }.distinct().sortedByDescending { it.rank() }

/** The best-ranked playable source of [quality], one that has not failed yet when there is one. */
internal fun sourceForQuality(sources: List<RankedSource>, quality: Quality, failed: Set<String>): RankedSource? {
    val all = sources.filter { it.extEnabled && it.candidate.quality == quality }
    return all.firstOrNull { it.key !in failed } ?: all.firstOrNull()
}

/** The source Auto plays: the ranking's recommended one (else the first playable). */
internal fun autoSource(sources: List<RankedSource>): RankedSource? = sources.firstOrNull { it.recommended } ?: sources.firstOrNull { it.extEnabled }

/** The chip's label: the playing source's quality, or "Calidad"; null when the sources name no quality (no chip). */
@Composable
internal fun sourceQualityChip(state: PlayerUiState): String? {
    if (sourceQualities(state.sources).isEmpty()) return null
    return state.current?.candidate?.quality?.takeIf { it != Quality.UNKNOWN }?.label() ?: stringResource(Res.string.player_quality)
}

/** The rows of the Quality sheet: Auto, then each quality; the playing one is checked. */
@Composable
internal fun SourceQualityRows(vm: PlayerViewModel, state: PlayerUiState, onDone: () -> Unit) {
    val autoOn = isAutoSource(state)
    Row(stringResource(Res.string.player_quality_auto), autoOn) {
        pickSourceQuality(vm, state, null)
        onDone()
    }
    sourceQualities(state.sources).forEach { q ->
        Row(q.label(), !autoOn && state.current?.candidate?.quality == q) {
            pickSourceQuality(vm, state, q)
            onDone()
        }
    }
}

/** The ranking's recommended source plays (the menu's "Auto"). */
internal fun isAutoSource(state: PlayerUiState): Boolean {
    val auto = autoSource(state.sources)
    return auto != null && state.current?.key == auto.key
}

/**
 * Plays the best source of [quality] (null = Auto: the recommended one), unless it already plays. Desktop's quality menu: the stream
 * has no renditions, so another quality is another source.
 */
internal fun pickSourceQuality(vm: PlayerViewModel, state: PlayerUiState, quality: Quality?) {
    val target = if (quality == null) {
        autoSource(state.sources)
    } else {
        sourceForQuality(state.sources, quality, state.failed.map { it.key }.toSet())
    }
    target?.takeIf { it.key != state.current?.key }?.let { vm.pickSource(it.key) }
}

@Composable
private fun Row(text: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

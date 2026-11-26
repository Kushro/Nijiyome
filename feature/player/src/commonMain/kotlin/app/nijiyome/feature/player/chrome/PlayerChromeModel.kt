// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.nijiyome.feature.player.EnhancementChip
import app.nijiyome.feature.player.PlayerCapabilities
import app.nijiyome.feature.player.PlayerUiState
import app.nijiyome.feature.player.SendToDevice
import app.nijiyome.feature.player.Sheet
import app.nijiyome.model.AspectMode
import app.nijiyome.model.LinkProcessingInfo
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession

// Sparkles S5: what every player structure (Cine, Mínimo, Panel) reads and calls. The three share this state and these actions; only
// the composition changes (docs/redesign/CONTRATO-COMPOSE.md).

/** What the chrome shows. Built once per frame by `PlayerContent`. */
@Stable
internal class PlayerChromeModel(
    val session: PlayerSession,
    val ps: PlaybackState,
    val state: PlayerUiState,
    /** The engine is buffering or the source is resolving (the play button turns into a loading indicator). */
    val buffering: Boolean,
    /** "Title · Episode N" (or the file / remote title). */
    val title: String,
    /** The episode's own title when the episode list knows it. */
    val episodeTitle: String?,
    val aspectMode: AspectMode,
    val fullscreen: Boolean,
    val enhancementChip: EnhancementChip,
    /** What the PC does (PC / Mixto), for the seek bar's "Procesado por la PC" run; null when the phone plays the source itself. */
    val pc: LinkProcessingInfo?,
    val capabilities: PlayerCapabilities,
    /** Keyboard shortcuts only mean something with a hardware keyboard (BUG-061). */
    val hasKeyboard: Boolean,
    /** Desktop "Enviar a dispositivo" with a paired phone; null where there is none. */
    val sendToDevice: SendToDevice?,
    /** The sheet open now (modal) or the panel's tab (Panel), for the pressed state of its button. */
    val openSheet: Sheet?,
    /** The seek bar's drag and the time toggle; held by the screen so hiding the controls keeps them. */
    val seek: SeekUiState,
) {
    /** Next / previous episode and the episode list are offered. */
    val canBrowseEpisodes: Boolean get() = state.remoteless && !state.looseFile
    val canGoNext: Boolean get() = state.hasNextEpisode && state.remoteless
}

/** What the chrome does. Every structure calls the same ones. */
@Stable
internal class PlayerChromeActions(
    val onBack: () -> Unit,
    val onPip: () -> Unit,
    val onOpenSheet: (Sheet) -> Unit,
    val onOpenPicker: () -> Unit,
    val onOpenTorrent: (String) -> Unit,
    val onNext: () -> Unit,
    val onPrev: () -> Unit,
    /** Any touch of the chrome: restarts the hide timer. */
    val onInteract: () -> Unit,
    val onLock: () -> Unit,
    val onScreenshot: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onHelp: () -> Unit,
    val onCycleAspect: () -> Unit,
)

/** The seek bar's drag and the time label's total / remaining toggle, shared by the bar and the time text of one structure. */
@Stable
internal class SeekUiState {
    var dragging by mutableStateOf(false)
    var dragFraction by mutableFloatStateOf(0f)
    var dragStartMs by mutableLongStateOf(0L)
    var showRemaining by mutableStateOf(false)

    /** The position the bar and the time show: the dragged one while dragging. */
    fun shownMs(ps: PlaybackState): Long {
        val duration = ps.durationMs.coerceAtLeast(1L)
        return if (dragging) (dragFraction * duration).toLong() else ps.positionMs.coerceIn(0L, duration)
    }
}

@Composable
internal fun rememberSeekUiState(): SeekUiState = remember { SeekUiState() }

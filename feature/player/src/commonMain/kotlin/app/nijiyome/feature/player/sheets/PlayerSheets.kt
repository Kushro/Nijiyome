// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import app.nijiyome.feature.player.PhoneFrameGen
import app.nijiyome.feature.player.PlayerUiState
import app.nijiyome.feature.player.PlayerViewModel
import app.nijiyome.feature.player.ProcessingUiState
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.model.AspectMode
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.QualityUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign (lane S6): the player's grouped sheets (artifact `screens-a.js`, screen `reproductor`): one sheet per concern,
// grouped sections inside, instead of the long "Más" list. The chrome (lane S5) opens them; the episode list stays S5's sheet.
//
// Entry points for the chrome:
// - [PlayerSheetHost]: the sheet over the video (side panel on wide windows, bottom sheet on phones).
// - [PlayerSheetContent]: the same content without a container, for the fixed side panel of `PlayerStructure.PANEL`.

/** The sheets of the player. The chrome's buttons map to them: Pistas, Mejoras, Imagen, Velocidad y temporizador, Más. */
enum class PlayerSheetKind {
    /** Audio and subtitle tracks, delay, second track, quick style (with the full editor as a step), translation. */
    TRACKS,

    /** Statistics, "Procesar en", what the PC does and its profiles, upscaling families, frame generation. */
    ENHANCEMENTS,

    /** Stream quality (or the source of another quality on desktop), aspect, zoom, colour, decoder. */
    PICTURE,

    /** Speed, sleep timer, loop and stable volume. */
    SPEED,

    /** Controls layout and the less used actions (screenshot, download, torrent client, source, shortcuts, lock, settings). */
    MORE,
}

/** What the sheets read. The chrome already holds every value (PlayerContent collects them). */
@Immutable
internal class PlayerSheetData(
    val vm: PlayerViewModel,
    val session: PlayerSession,
    val state: PlayerUiState,
    val ps: PlaybackState,
    val quality: QualityUiState,
    val processing: ProcessingUiState,
    /** The phone's frame generation (Android libmpv); null elsewhere. */
    val frameGen: PhoneFrameGen?,
    /** The statistics overlay is shown (the chrome draws it). */
    val statsVisible: Boolean = false,
    /** The aspect the video surface uses now; null = the quality engine's ([QualityUiState.requested]). */
    val aspect: AspectMode? = null,
)

/**
 * What the sheets ask the chrome to do. Every callback has a default: a null action hides its row, so a chrome that does not offer
 * something (no screenshot on this platform, no lock on desktop) simply leaves it out.
 */
@Immutable
internal class PlayerSheetActions(
    val onStatsChange: (Boolean) -> Unit = {},
    /** A subtitle style slider moved (not saved yet). Default: the view model's live preview on the engine. */
    val onPreviewSubtitleStyle: ((SubtitleStyle) -> Unit)? = null,
    /** A subtitle style change is final (saved). Default: the view model saves it. */
    val onSaveSubtitleStyle: ((SubtitleStyle) -> Unit)? = null,
    /** "Cargar archivo…": the platform's subtitle file picker; null where there is none. */
    val onLoadSubtitleFile: (() -> Unit)? = null,
    /** Settings at a section (Lossless.dll import, translation key, Link pairing, player settings). */
    val onOpenSettings: (SettingsSection) -> Unit = {},
    /** The aspect was picked: the chrome keeps it for the video surface (Media3 has no quality engine to hold it). */
    val onAspect: ((AspectMode) -> Unit)? = null,
    /** Another sheet (the "Mejoras" row of "Más"). */
    val onOpenSheet: (PlayerSheetKind) -> Unit = {},
    val onScreenshot: (() -> Unit)? = null,
    /** Lock the controls (touch). */
    val onLock: (() -> Unit)? = null,
    /** The keyboard shortcuts panel. */
    val onShortcuts: (() -> Unit)? = null,
    /** The episode's source list. */
    val onOpenPicker: (() -> Unit)? = null,
    /** The torrent of the playing file in the client. */
    val onOpenTorrent: ((String) -> Unit)? = null,
    /** The episode list sheet. */
    val onEpisodes: (() -> Unit)? = null,
)

/** Title of a sheet. */
internal fun sheetTitle(kind: PlayerSheetKind): StringResource = when (kind) {
    PlayerSheetKind.TRACKS -> Res.string.rd_s6_sheet_tracks
    PlayerSheetKind.ENHANCEMENTS -> Res.string.player_fx_title
    PlayerSheetKind.PICTURE -> Res.string.rd_s6_sheet_picture
    PlayerSheetKind.SPEED -> Res.string.rd_s6_sheet_speed
    PlayerSheetKind.MORE -> Res.string.player_more
}

/** A player sheet over the video, in the common container ([PlayerSheetFrame]). */
@Composable
internal fun PlayerSheetHost(kind: PlayerSheetKind, data: PlayerSheetData, actions: PlayerSheetActions, onDismiss: () -> Unit) {
    PlayerSheetFrame(title = stringResource(sheetTitle(kind)), onDismiss = onDismiss) {
        PlayerSheetContent(kind, data, actions, onClose = onDismiss)
    }
}

/**
 * The content of a sheet without a container (the panel structure hosts it in its fixed side panel with tabs). [onClose] is what
 * "done" does: closing the sheet, or nothing in a fixed panel.
 */
@Composable
internal fun PlayerSheetContent(
    kind: PlayerSheetKind,
    data: PlayerSheetData,
    actions: PlayerSheetActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Settings leave the player: pause first and close the sheet (what the "Importar Lossless.dll" link always did).
    val openSettings: (SettingsSection) -> Unit = { section ->
        data.session.pause()
        onClose()
        actions.onOpenSettings(section)
    }
    Column(modifier.fillMaxWidth()) {
        when (kind) {
            PlayerSheetKind.TRACKS -> TracksSheetContent(data, actions, openSettings, onClose)
            PlayerSheetKind.ENHANCEMENTS -> EnhancementsSheetContent(
                vm = data.vm,
                session = data.session,
                state = data.state,
                quality = data.quality,
                processing = data.processing,
                frameGen = data.frameGen,
                statsVisible = data.statsVisible,
                onStatsChange = actions.onStatsChange,
                onOpenFgSettings = { openSettings(SettingsSection.PLAYER) },
                onOpenSettings = openSettings,
                onClose = onClose,
            )
            PlayerSheetKind.PICTURE -> PictureSheetContent(data, actions, onClose)
            PlayerSheetKind.SPEED -> SpeedSheetContent(data)
            PlayerSheetKind.MORE -> MoreSheetContent(data, actions, openSettings, onClose)
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.runtime.Composable
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsSection

/**
 * Full-screen player. [args] with `remoteSessionId` plays the HLS of that Link session through the mTLS client of the pairing; `localUri`
 * plays a completed download offline; `torrentId` plays a file of a torrent already in the session; otherwise sources come from the
 * installed extensions (Auto / Manual, 12 §4 d). The engine is `AppSettings.playerEngine` (Media3, libmpv or an external app). Progress
 * goes to the history (E9 keys) and the screen survives rotation. Android: the Media3 / libmpv player. Desktop: the same
 * screen over libmpv ("not available yet" while no libmpv factory is registered).
 */
@Composable
expect fun PlayerScreen(
    args: PlayerArgs,
    onBack: () -> Unit,
    // --- android wave contract (docs/redesign/12 §3.11): new callbacks, wired by :app (worker b), used by worker d ---
    /** Replace this player entry with another episode / source (next episode, batch next, source change that needs a new route). */
    onReplace: (PlayerArgs) -> Unit = {},
    /** Chip "Abrir en el cliente" -> Route.Torrents(OVERVIEW, torrentId). */
    onOpenTorrent: (torrentId: String) -> Unit = {},
    /** Source picker banner "Ajustes" / metered dialog -> Route.SettingsSection(section). */
    onOpenSettings: (SettingsSection) -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    onOpenLinked: () -> Unit = {},
    /** 0.4.1 end screen "Volver a la ficha": the title's Details (the app goes back to it when it is the previous entry). */
    onOpenDetails: (anilistId: Int) -> Unit = {},
)

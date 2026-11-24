// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.network.outbound
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * The player module of desktop: what is shared with Android ([playerCoreModule]: the player's OkHttp client and AniSkip) and, with
 * [cacheDir] and [fontsDir], the subtitle tools ([DesktopSubtitleTools]: "Load file…", translation, imported fonts). The libmpv
 * [PlayerSessionFactory] is in :core:player-mpv; it must read the same [fontsDir].
 *
 * Android's other player preferences (`PlayerPrefs`) only move 0.4.0's subtitle extras into the settings once; desktop never had
 * them, and its own player settings are the settings store and `desktop-player.json` (:core:player-mpv).
 */
fun desktopPlayerModule(cacheDir: File? = null, fontsDir: File? = null): Module = module {
    includes(playerCoreModule { outbound() })
    if (cacheDir != null && fontsDir != null) {
        single { DesktopSubtitleTools(cacheDir, fontsDir, get(named(PLAYER_HTTP))) }
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Creates engine sessions. Media3 [PlaybackController]s come from here; the mpv engine registers a [PlayerSessionFactory] with the
 * [MPV_SESSION_FACTORY] qualifier in `mpvPlayerModule` (:core:player-mpv). A new client per app: nothing from the app's network stack
 * (auth interceptors) reaches CDNs.
 */
@UnstableApi
class PlaybackControllerFactory(private val context: Context, private val client: OkHttpClient) : PlayerSessionFactory {
    override fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlaybackController =
        PlaybackController(context, client, scope, listener, remote)
}

@UnstableApi
val playerModule: Module = module {
    // The player's OkHttp client and AniSkip (commonMain).
    includes(playerCoreModule())
    single { PlaybackControllerFactory(androidContext(), get(named(PLAYER_HTTP))) }
    // parity wave A (15 §3.2, worker p): private player preferences, subtitle fetch / translation / fonts.
    // 0.4.1: created at start so the 0.4.0 subtitle extras move into AppSettings.subtitleStyle before any screen reads them.
    single(createdAtStart = true) { PlayerPrefs(androidContext(), getOrNull()) }
    single { SubtitleTools(androidContext(), get(named(PLAYER_HTTP))) }
}

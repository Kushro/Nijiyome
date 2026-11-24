// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.link.RemotePlaybackAccess
import app.nijiyome.model.Container
import app.nijiyome.model.SkipMarksRepository
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Creates one [PlayerSession] of an engine (main thread). */
fun interface PlayerSessionFactory {
    fun create(scope: CoroutineScope, listener: PlaybackListener, remote: Boolean): PlayerSession
}

/** Koin qualifier of the libmpv [PlayerSessionFactory] (bound by `mpvPlayerModule`). */
const val MPV_SESSION_FACTORY = "mpv-session-factory"

/** Remote (Link) playback: the PC's HLS is fetched with the mTLS client of the pairing. */
object RemotePlayback {
    fun source(access: RemotePlaybackAccess, sessionId: String, label: String): PlayableSource? {
        val session = access.session(sessionId) ?: return null
        val client = access.playbackClient() ?: return null
        return PlayableSource(url = session.playbackUrl, headers = emptyMap(), container = Container.HLS, label = label, httpClient = client)
    }
}

/** Koin qualifier of the player's own OkHttp client (no app interceptors reach CDNs). */
const val PLAYER_HTTP = "player-http"

/**
 * Integration hook: `:core:player` must not depend on `:core:data`, so the app binds
 * `single<HistoryFlush> { HistoryFlush { get<HistorySyncScheduler>().requestSync(immediate = true) } }`.
 * The player calls it on pause and when leaving; when unbound the coalesced background sync still runs.
 */
fun interface HistoryFlush {
    fun requestImmediate()
}

/**
 * What every platform's player module shares: the player's own OkHttp client ([PLAYER_HTTP]) and the AniSkip marks. Android adds the
 * Media3 engine, its preferences and subtitle tools in `playerModule` (androidMain).
 */
fun playerCoreModule(configureHttp: OkHttpClient.Builder.() -> Unit = {}): Module = module {
    // [configureHttp]: desktop adds the outbound proxy of Settings › Red; Android passes nothing.
    single(named(PLAYER_HTTP)) { OkHttpClient.Builder().apply(configureHttp).build() }
    // android wave (12 §3.2, §3.16): AniSkip intro / outro / recap marks for web AND torrent playback.
    single<SkipMarksRepository> { AniSkipRepository(get(named(PLAYER_HTTP))) }
}

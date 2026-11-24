// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.link.RemotePlaybackAccess
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LocalEnhancementGate
import app.nijiyome.model.PhoneFrameGenGate
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentEngineUnavailable
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.player.DesktopSubtitleTools
import app.nijiyome.player.ExternalSessionFactory
import app.nijiyome.player.MPV_SESSION_FACTORY
import app.nijiyome.player.PlayerSessionFactory
import org.koin.core.Koin
import org.koin.core.qualifier.named

/**
 * Milestone 3: the shared [PlayerViewModel] with what desktop has, for the desktop player screen (a later milestone). libmpv (the
 * [PlayerSessionFactory] bound with [MPV_SESSION_FACTORY]) is the only engine; the repositories come from the graph when they are
 * registered there. Throws when no libmpv factory is bound: the desktop player screen must not open without it.
 */
fun Koin.desktopPlayerViewModel(args: PlayerArgs): PlayerViewModel {
    val torrents = getOrNull<TorrentRepository>()
    return PlayerViewModel(
        args = args,
        metadata = get(),
        extensions = get(),
        history = get(),
        // Desktop (by design): desktop is not a Link client; without a registered one the player is always unpaired
        link = getOrNull<LinkRepository>() ?: NoLinkRepository,
        remote = getOrNull<RemotePlaybackAccess>() ?: NoRemotePlayback,
        // Desktop (by design): no Media3 on desktop; every playback is libmpv
        media3 = null,
        flush = getOrNull(),
        settingsRepository = getOrNull(),
        torrents = torrents,
        stats = getOrNull(),
        skipMarks = getOrNull(),
        mpvFactory = get<PlayerSessionFactory>(named(MPV_SESSION_FACTORY)),
        // "Load file…", external tracks, translation and its cache (desktopPlayerModule); mpv draws every track itself.
        subtitleTools = getOrNull<DesktopSubtitleTools>()?.let(::DesktopDocumentSubtitleTools),
        // Translation API keys, from the desktop secret store (system keyring).
        secrets = getOrNull(),
        debrid = getOrNull(),
        embedSniffer = getOrNull(),
        enhancementGate = getOrNull<LocalEnhancementGate>() ?: LocalEnhancementGate.NONE,
        frameGenGate = getOrNull<PhoneFrameGenGate>() ?: PhoneFrameGenGate.NONE,
        // Desktop has no other app to hand a video to: its EXTERNAL engine is mpv in its own window, as a session (below).
        externalPlayer = null,
        externalSessions = getOrNull<ExternalSessionFactory>(),
        // Without libtorrent4j's native library the torrent candidates would all fail: go straight to the web sources.
        skipTorrents = torrents == null || torrents is TorrentEngineUnavailable,
    )
}

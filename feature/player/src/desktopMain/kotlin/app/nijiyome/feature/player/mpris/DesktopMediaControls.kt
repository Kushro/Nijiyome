// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.mpris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.nijiyome.feature.player.PlayerViewModel
import app.nijiyome.model.NowPlaying
import app.nijiyome.model.NowPlayingInfo
import app.nijiyome.model.PlayerArgs
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * v1.1 (F2): while the player screen is open it is the app's MPRIS player (Linux): the media keys and the desktop's media widget play
 * / pause / skip episodes and show the episode ([NowPlaying], published by the screen) with its poster. Nothing on other systems.
 */
@Composable
internal fun DesktopMediaControls(vm: PlayerViewModel, args: PlayerArgs, service: MprisService? = MprisService.instance) {
    if (service == null) return
    val state by vm.state.collectAsState()
    val session = state.session
    val scope = rememberCoroutineScope()
    val owner = remember { Any() }
    val port = remember(session, vm) { session?.let { ScreenPort(vm, it, args, scope) } }
    // v1.1 review #5: on the service's own thread, not this screen's scope (cancelled with the screen before a launch could run).
    DisposableEffect(service, port) {
        if (port != null) service.attachAsync(owner, port)
        onDispose { service.detachAsync(owner) }
    }
    // Anything MPRIS shows changed: play / pause, the episode, the duration, next / previous, the title or poster.
    LaunchedEffect(service, port) {
        val p = port ?: return@LaunchedEffect
        var lastPos = -1L
        var lastAt = 0L
        combine(p.session.state, vm.state, NowPlaying.current) { ps, vs, now -> Triple(ps, vs.hasNextEpisode to vs.hasPrevEpisode, now) }
            .collect { (ps, _, _) ->
                service.post { changed() }
                // A jump of the position (a seek from the screen, the keyboard or Link) is MPRIS's Seeked; regular progress is not.
                val at = System.nanoTime() / 1_000_000
                if (lastPos >= 0 && isJump(lastPos, lastAt, ps.positionMs, at, ps.isPlaying)) {
                    val us = ps.positionMs * 1000
                    service.post { seeked(us) }
                }
                lastPos = ps.positionMs
                lastAt = at
            }
    }
}

/** A position change that regular playback ([playing] or not) cannot explain over the wall-clock time between two samples. */
internal fun isJump(fromMs: Long, fromAt: Long, toMs: Long, toAt: Long, playing: Boolean): Boolean {
    val expected = if (playing) (toAt - fromAt).coerceAtLeast(0) else 0
    return kotlin.math.abs(toMs - fromMs - expected) > JUMP_MS
}

private const val JUMP_MS = 1_500L

/** The screen's session and view model as an [MprisPort]: reads are thread safe, commands run on the screen's (Swing) thread. */
private class ScreenPort(
    private val vm: PlayerViewModel,
    val session: PlayerSession,
    private val args: PlayerArgs,
    private val scope: CoroutineScope,
) : MprisPort {
    override fun snapshot(): MprisSnapshot {
        val ps = session.state.value
        val vs = vm.state.value
        return snapshotOf(ps, NowPlaying.current.value, args, vs.hasNextEpisode && vs.canNavigateEpisodes, vs.hasPrevEpisode)
    }

    private fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    override fun play() = onMain { session.play() }
    override fun pause() = onMain { session.pause() }
    override fun playPause() = onMain { if (session.state.value.isPlaying) session.pause() else session.play() }
    override fun next() = onMain { if (vm.state.value.hasNextEpisode && vm.state.value.canNavigateEpisodes) vm.playNext() }
    override fun previous() = onMain { vm.playPrevious() }
    override fun seekTo(positionUs: Long) = onMain { session.seekTo(positionUs / 1000) }
}

/** What MPRIS shows for the screen's state (pure; unit tested). */
internal fun snapshotOf(ps: PlaybackState, now: NowPlayingInfo?, args: PlayerArgs, canNext: Boolean, canPrevious: Boolean): MprisSnapshot {
    val status = when {
        ps.isPlaying -> MprisSnapshot.Status.PLAYING
        else -> MprisSnapshot.Status.PAUSED
    }
    return MprisSnapshot(
        status = status,
        title = now?.title ?: args.title,
        subtitle = now?.subtitle,
        artUrl = now?.artworkUrl,
        lengthUs = ps.durationMs.coerceAtLeast(0) * 1000,
        positionUs = ps.positionMs.coerceAtLeast(0) * 1000,
        canGoNext = canNext,
        canGoPrevious = canPrevious,
        trackKey = "${args.media.anilistId ?: args.media.malId ?: 0}_${args.episode}",
    )
}

/** v1.1 review #5: what the desktop app calls when it quits (the system media controls leave the session bus). */
object DesktopSystemMediaControls {
    fun shutdown() = MprisService.shutdownInstance()
}

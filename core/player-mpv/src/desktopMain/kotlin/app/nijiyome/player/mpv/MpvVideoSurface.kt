// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import app.nijiyome.player.PlayerSession
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface

/**
 * Hito 4: the video of a desktop mpv session ([DesktopMpvSessionFactory]), the counterpart of Android's `MpvPlayerSurface`. mpv
 * renders into GPU textures shared with Compose's own Skia context (MediaMP's render ring, sized to this composable), so the video is
 * an ordinary draw of the Compose scene: the player's controls are drawn on top of it with plain Compose, no heavyweight window.
 * Aspect, zoom, crop and stretch are mpv's (`keepaspect`, `panscan`, `video-zoom`... written by the quality engine) inside that
 * target. Draws nothing for a session of another engine.
 *
 * A load waits (briefly) for this surface's render context before opening the file, like Android waits for its SurfaceView (see
 * DesktopMpvEngine.awaitRenderContext).
 */
@Composable
fun MpvVideoSurface(session: PlayerSession, modifier: Modifier = Modifier) {
    val engine = (session as? DesktopMpvPlayerSession)?.engine ?: return
    DisposableEffect(engine) {
        engine.surfaceAttached = true
        onDispose {
            engine.surfaceAttached = false
            engine.onViewport(0, 0)
        }
    }
    // v1.1 (F2): the render target is this composable's size in real pixels (onSizeChanged), so the video is 1:1 with the window's
    // pixels whatever density the app uses (desktopApp's LinuxUiScale). A monitor whose scale differs from XWayland's is resampled by
    // the compositor, outside the app (docs/spikes/reproductor.md §4.2).
    MpvMediampPlayerSurface(engine.player, modifier.onSizeChanged { engine.onViewport(it.width, it.height) })
}

// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(UnstableApi::class)

package app.nijiyome.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.AspectMode
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.PlaybackController
import app.nijiyome.player.PlaybackService
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.SavedScreenshot
import app.nijiyome.player.Screenshots
import app.nijiyome.player.VolumeDrag
import app.nijiyome.player.mpv.MpvPlayerSession
import app.nijiyome.player.mpv.MpvPlayerSurface
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

// Milestone 4: the Android side of PlayerScreenPlatform.kt, moved verbatim from PlayerControls.kt / PlayerExtras.kt /
// PlayerOverlays.kt (androidMain until then): the Media3 / libmpv surfaces, the touch gestures, the window (orientation, bars,
// brightness, keep-awake), PiP, the system volume, PixelCopy screenshots and Media3's cues.

// ---- video surface ------------------------------------------------------------------------------------------------------------------

@Composable
internal actual fun PlayerVideoSurface(session: PlayerSession, modifier: Modifier, aspectMode: AspectMode) {
    when (session) {
        is PlaybackController -> {
            val scale = when (aspectMode) {
                AspectMode.FIT -> ContentScale.Fit
                AspectMode.FILL -> ContentScale.Crop
                AspectMode.STRETCH -> ContentScale.FillBounds
                AspectMode.RATIO_16_9, AspectMode.RATIO_4_3 -> ContentScale.FillBounds
            }
            val ratio = when (aspectMode) {
                AspectMode.RATIO_16_9 -> 16f / 9f
                AspectMode.RATIO_4_3 -> 4f / 3f
                else -> null
            }
            if (ratio != null) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    ContentFrame(
                        player = session.player,
                        modifier = Modifier.aspectRatio(ratio),
                        contentScale = scale,
                    )
                }
            } else {
                ContentFrame(player = session.player, modifier = modifier, contentScale = scale)
            }
        }
        is MpvPlayerSession -> MpvPlayerSurface(session.engine, modifier)
    }
}

// ---- gestures -----------------------------------------------------------------------------------------------------------------------

/**
 * Tap toggles the controls, double tap on either half seeks 10 s, horizontal drag scrubs, vertical drag on the left half changes the
 * brightness and on the right half the volume.
 */
@Composable
internal actual fun PlayerGestureLayer(
    session: PlayerSession,
    onToggleControls: () -> Unit,
    onHud: (Hud) -> Unit,
    // A touch screen has no pointer to follow; fullscreen is the controls' button.
    controlsShown: Boolean,
    onPointerMoved: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightness by remember {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (current >= 0f) current else 0.5f)
    }
    // BUG-044: the volume drag is re-created from the live system volume and mute state on every drag start (never a value cached
    // when the player opened), so a muted or zeroed stream can't jump to 15/15.
    var volumeDrag by remember { mutableStateOf<VolumeDrag?>(null) }
    var onLeft by remember { mutableStateOf(true) }
    var scrubStartMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var lastDoubleTapAt by remember { mutableLongStateOf(0L) }
    var lastDoubleTapSide by remember { mutableStateOf(0) }
    var doubleTapAccumSec by remember { mutableStateOf(0) }
    // player.android.long-press-speed: hold = 2x, release = the previous speed.
    var speedBeforeHold by remember { mutableStateOf<Float?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(session) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        speedBeforeHold?.let { prev ->
                            session.setSpeed(prev)
                            speedBeforeHold = null
                        }
                    },
                    onLongPress = {
                        if (speedBeforeHold == null) {
                            speedBeforeHold = session.state.value.speed
                            session.setSpeed(HOLD_SPEED)
                            onHud(Hud(Hud.Kind.Hold))
                        }
                    },
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val back = offset.x < size.width / 2f
                        val side = if (back) -1 else 1
                        val now = System.currentTimeMillis()
                        val stepSec = (SEEK_STEP_MS / 1000L).toInt()
                        if (side == lastDoubleTapSide && now - lastDoubleTapAt < 950L) {
                            doubleTapAccumSec += if (back) -stepSec else stepSec
                        } else {
                            doubleTapAccumSec = if (back) -stepSec else stepSec
                        }
                        lastDoubleTapSide = side
                        lastDoubleTapAt = now
                        session.seekBy(if (back) -SEEK_STEP_MS else SEEK_STEP_MS)
                        onHud(Hud(Hud.Kind.DoubleTapSeek, seconds = doubleTapAccumSec, doubleTapSide = side))
                    },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        onLeft = it.x < size.width / 2f
                        volumeDrag = if (onLeft) null else VolumeDrag(
                            startIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                            maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
                        )
                    },
                    onDragEnd = { volumeDrag = null },
                    onDragCancel = { volumeDrag = null },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val delta = -dy / size.height * 1.2f
                        if (onLeft) {
                            brightness = (brightness + delta).coerceIn(0.01f, 1f)
                            activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = brightness } }
                            onHud(Hud(Hud.Kind.Brightness, percent = (brightness * 100).roundToInt()))
                        } else {
                            volumeDrag?.let { drag ->
                                drag.onDrag(-dy / size.height)?.let { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
                                onHud(Hud(Hud.Kind.Volume, percent = drag.percent, muted = drag.muted))
                            }
                        }
                    },
                )
            }
            .pointerInput(session) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubStartMs = session.currentPositionMs(); scrubDeltaMs = 0 },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        // A full-width drag scrubs 90 s.
                        scrubDeltaMs += (dx / size.width * 90_000f).toLong()
                        val st = session.state.value
                        val dur = st.durationMs.coerceAtLeast(1L)
                        val target = (scrubStartMs + scrubDeltaMs).coerceIn(0L, dur)
                        onHud(
                            Hud(
                                kind = Hud.Kind.Scrub,
                                seconds = ((target - scrubStartMs) / 1000L).toInt(),
                                targetMs = target,
                                durationMs = st.durationMs,
                                bufferedMs = st.bufferedMs,
                            ),
                        )
                    },
                    onDragEnd = {
                        if (abs(scrubDeltaMs) > 500) {
                            val dur = session.state.value.durationMs
                            val target = if (dur > 0) (scrubStartMs + scrubDeltaMs).coerceIn(0L, dur) else (scrubStartMs + scrubDeltaMs).coerceAtLeast(0L)
                            session.seekTo(target)
                        }
                    },
                )
            },
    )
}

// ---- window -------------------------------------------------------------------------------------------------------------------------

/**
 * Captures the window (orientation, system bars, brightness) when the first player screen opens and restores it when the
 * last one closes. Called from PlayerBody, which is composed in the same frame as the navigation change: going to the next
 * episode composes the new player before the old one is disposed, and the new one's content only appears once its source
 * resolves. A per-content capture saved the old player's forced landscape as "previous" (the app stayed in landscape after
 * a binge) and the old player's restore turned the next episode portrait while it resolved.
 */
@Composable
internal actual fun PlayerWindowScope() {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        PlayerWindowOwner.acquire(activity)
        onDispose {
            PlayerWindowOwner.release(activity) { orientation, brightness ->
                view.keepScreenOn = false
                insets?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = orientation
                if (window != null && brightness != null) {
                    window.attributes = window.attributes.also { it.screenBrightness = brightness }
                }
            }
        }
    }
}

/** Window state from before the first player screen opened, shared by overlapping ones. Main thread only. */
internal object PlayerWindowOwner {
    /** Player screens open right now (2 for a moment while going to the next episode). */
    var active = 0
        private set
    private var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var brightness: Float? = null

    fun acquire(activity: Activity?) {
        if (active++ == 0) {
            // Only the player forces landscape; a leftover from a player that did not clean up is not a baseline.
            orientation = activity?.requestedOrientation
                ?.takeUnless { it == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            brightness = activity?.window?.attributes?.screenBrightness
        }
    }

    fun release(activity: Activity?, restore: (orientation: Int, brightness: Float?) -> Unit) {
        check(active > 0) { "PlayerWindowOwner released more often than acquired" }
        active--
        if (active == 0 && activity != null) restore(orientation, brightness)
    }
}

@Composable
internal actual fun PlayerContentEffects(
    session: PlayerSession,
    isPlaying: Boolean,
    fullscreen: Boolean,
    backgroundPlayback: Boolean,
    isInPip: () -> Boolean,
    onPipChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val pipChange by rememberUpdatedState(onPipChange)
    // Keep-awake is this view's: cleared on exit unless another player screen is still open (going to the next episode, both are
    // composed for a moment and share the view).
    DisposableEffect(view) {
        onDispose { if (PlayerWindowOwner.active <= 1) view.keepScreenOn = false }
    }
    // player.android.fullscreen: immersive + forced landscape; off = system bars and the device's own orientation.
    LaunchedEffect(fullscreen, activity) {
        val act = activity ?: return@LaunchedEffect
        val insets = WindowCompat.getInsetsController(act.window, view)
        if (fullscreen) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Keep-awake only while the video plays (FLAG_KEEP_SCREEN_ON through the view).
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying }
    // Background playback (15 §3.2): the MediaSession service runs while playing when the setting is on.
    LaunchedEffect(isPlaying, backgroundPlayback) {
        if (isPlaying && backgroundPlayback) PlaybackService.start(context)
    }
    // Picture in picture state (the session keeps playing; only the chrome is hidden).
    DisposableEffect(activity) {
        val host = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { pipChange(it.isInPictureInPictureMode) }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    // Android 12+: leave to PiP automatically while playing.
    LaunchedEffect(isPlaying, activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.setPictureInPictureParams(pipParams(autoEnter = isPlaying))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!isInPip() && !backgroundPlayback) session.pause() }
}

private fun pipParams(autoEnter: Boolean): PictureInPictureParams =
    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter)
    }.build()

private fun enterPip(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.enterPictureInPictureMode(pipParams(autoEnter = false))
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

// ---- capabilities and actions -------------------------------------------------------------------------------------------------------

internal actual val playerCapabilities: PlayerCapabilities = PlayerCapabilities(
    pictureInPicture = true,
    fullscreenToggle = true,
    escapeLeavesFullscreen = false,
    screenLock = true,
    screenshot = true,
    subtitleFile = true,
    backgroundPlayback = true,
    touchFirst = true,
)

@Composable
internal actual fun initialFullscreen(): Boolean = true

@Composable
internal actual fun hasHardwareKeyboard(): Boolean = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS

@Composable
internal actual fun rememberPlayerPlatformActions(): PlayerPlatformActions {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    return remember(context, activity) { AndroidPlayerActions(context, activity) }
}

private class AndroidPlayerActions(private val context: Context, private val activity: Activity?) : PlayerPlatformActions {
    override fun enterPictureInPicture() {
        activity?.let { enterPip(it) }
    }

    // The system media volume, with its own panel (no HUD).
    override fun adjustVolume(session: PlayerSession, step: VolumeStep): Hud? {
        adjustVolume(
            context,
            when (step) {
                VolumeStep.TOGGLE_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
                VolumeStep.RAISE -> AudioManager.ADJUST_RAISE
                VolumeStep.LOWER -> AudioManager.ADJUST_LOWER
            },
        )
        return null
    }

    override suspend fun screenshot(session: PlayerSession, title: String, episode: Int?): String? =
        captureScreenshot(activity, session, title, episode)?.displayPath
}

/** M / ↑ / ↓: the media stream volume (engine independent). */
internal fun adjustVolume(context: Context, direction: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}

// ---- screenshots (player.screenshot, player.android.screenshot) -----------------------------------------------------------------------

/**
 * Saves the current frame to Pictures/Nijiyome: mpv writes its own PNG (`screenshot-to-file`); Media3 (or an mpv failure) copies the video
 * `SurfaceView` with `PixelCopy`. Returns the saved file or null.
 */
internal suspend fun captureScreenshot(activity: Activity?, session: PlayerSession, title: String, episode: Int?): SavedScreenshot? {
    val context = activity ?: return null
    val name = Screenshots.fileName(title, episode)
    if (session !is PlaybackController) {
        val tmp = File(context.cacheDir, "screenshot.png")
        val ok = runCatching { session.screenshotToFile(tmp.absolutePath) }.getOrDefault(false)
        if (ok) return Screenshots.saveFile(context, tmp, name).also { tmp.delete() }
    }
    val surface = findSurfaceView(activity.window.decorView) ?: return null
    val w = surface.width
    val h = surface.height
    if (w <= 0 || h <= 0) return null
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(surface, bitmap, { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resume(false)
        }
    }
    if (!copied) return null
    return Screenshots.saveBitmap(context, bitmap, name).also { bitmap.recycle() }
}

private fun findSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView && view.isShown && view.width > 0) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) findSurfaceView(view.getChildAt(i))?.let { return it }
    }
    return null
}

// ---- keys ---------------------------------------------------------------------------------------------------------------------------

/** `KeyEvent` keycode -> [PlayerKeymap] key name (`SPACE`, `DPAD_LEFT`, `F`). */
internal fun keyNameOf(keyCode: Int): String = PlayerKeymap.keyName(android.view.KeyEvent.keyCodeToString(keyCode))

// ---- subtitles ----------------------------------------------------------------------------------------------------------------------

@Composable
internal actual fun rememberSubtitleFilePicker(onPicked: (SubtitleDocument) -> Unit): () -> Unit {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    return { picker.launch(SUBTITLE_MIME_TYPES) }
}

internal actual val PlayerSession.isMedia3: Boolean get() = this is PlaybackController

@Composable
internal actual fun media3CueTexts(session: PlayerSession): List<String> {
    val cues by (session as PlaybackController).cues.collectAsState()
    return cueTexts(cues)
}

private fun cueTexts(cues: List<Cue>): List<String> = cues.mapNotNull { it.text?.toString()?.takeIf { t -> t.isNotBlank() } }

/**
 * Media3 subtitle cues drawn by us (ContentFrame does not render text): Settings › Estilo de subtítulos = scale, background box opacity,
 * font (Outfit / Space Grotesk / system), bold and outline. ASS styling is lost under Media3 (the player shows the simplified notice).
 */
@Composable
internal fun SubtitleOverlay(cues: List<Cue>, style: SubtitleStyle, modifier: Modifier = Modifier) {
    val texts = cues.mapNotNull { it.text?.toString()?.takeIf { t -> t.isNotBlank() } }
    if (texts.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val family = when (style.font) {
        "system" -> FontFamily.SansSerif
        "space_grotesk" -> NijiyomeFonts.families.display
        else -> NijiyomeFonts.families.text
    }
    val fg = scheme.onBackground
    val shadow = if (style.outline) Shadow(color = scheme.background, blurRadius = 6f) else null
    val textStyle = TextStyle(
        fontFamily = family,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = (18 * style.scale.coerceIn(0.5f, 2f)).sp,
        lineHeight = (24 * style.scale.coerceIn(0.5f, 2f)).sp,
        textAlign = TextAlign.Center,
        color = fg,
        shadow = shadow,
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        texts.forEach { line ->
            Text(
                line,
                style = textStyle,
                modifier = Modifier
                    .background(scheme.background.copy(alpha = style.backgroundOpacity.coerceIn(0f, 1f)), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private const val HOLD_SPEED = 2f
private val SUBTITLE_MIME_TYPES = arrayOf("application/x-subrip", "text/vtt", "text/x-ssa", "text/plain", "application/octet-stream", "*/*")

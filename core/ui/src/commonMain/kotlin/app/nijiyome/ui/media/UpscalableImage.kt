// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.MotionDurations
import app.nijiyome.designsystem.motion.MotionExtra
import app.nijiyome.designsystem.motion.MotionLevel
import app.nijiyome.designsystem.motion.motionExtraOn
import app.nijiyome.upscale.ImageUpscaleSettings
import app.nijiyome.upscale.UpscaleImageKind
import app.nijiyome.upscale.UpscaleQueue
import app.nijiyome.upscale.UpscaleState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.StateFlow
import okio.Path.Companion.toPath

// GPU image upscaling, UI side (docs/plan-upscaling.md §4.2). The app provides both locals (step 4); without them every
// UpscalableImage is the plain AsyncImage it replaced.

/** The upscale queue of this build, or null where there is none (then images load exactly as before). */
val LocalUpscaleQueue: ProvidableCompositionLocal<UpscaleQueue?> = staticCompositionLocalOf { null }

/** The user's upscale choices; when null the queue alone decides (it answers Skipped for a kind that is off). */
val LocalUpscaleSettings: ProvidableCompositionLocal<StateFlow<ImageUpscaleSettings>?> = staticCompositionLocalOf { null }

/** Results are looked up per 64 px of on-screen size, the rounding of the cache key (plan §4.5): resizing does not re-request per pixel. */
private const val TARGET_BUCKET_PX = 64

/**
 * v1.3.5 [MotionExtra.IMAGE_FADE]: how long a freshly loaded image fades in (FULL 200 ms, CALM 150 ms, PERF / OFF / switched off 0).
 * Per request because the app's ImageLoader is built once, outside composition, and cannot see the level. Coil skips the fade on
 * memory-cache hits, so scrolling back over loaded posters never blinks.
 */
@Composable
private fun imageFadeMs(): Int {
    if (!motionExtraOn(MotionExtra.IMAGE_FADE)) return 0
    return when (LocalMotion.current.level) {
        MotionLevel.FULL -> MotionDurations.STATE
        MotionLevel.CALM -> MotionDurations.CALM_FADE
        MotionLevel.PERF, MotionLevel.OFF -> 0
    }
}

/** [url] as a request that fades in over [fadeMs] (the plain url when 0, exactly what the image loaded before). */
@Composable
private fun fadingModel(url: String?, fadeMs: Int): Any? {
    if (url.isNullOrBlank() || fadeMs <= 0) return url
    val context = LocalPlatformContext.current
    return remember(context, url, fadeMs) { ImageRequest.Builder(context).data(url).crossfade(fadeMs).build() }
}

/** Avatars smaller than this are never worth a GPU job (plan §4.3). */
private val AVATAR_MIN = 64.dp

/**
 * `AsyncImage` of [url] that swaps to the GPU-upscaled copy when the queue has one (plan §4.2). The original paints as always; once
 * it loaded, its decoded size and the on-screen size go to [LocalUpscaleQueue], and a finished result replaces it in place, with the
 * original as the placeholder so nothing flashes. A result already on disk is used from the first frame. [upscale] = false for images
 * shown blurred or otherwise not worth it: they never reach the queue.
 */
@Composable
fun UpscalableImage(
    url: String?,
    contentDescription: String?,
    kind: UpscaleImageKind,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    upscale: Boolean = true,
) {
    val queue = LocalUpscaleQueue.current
    val prefs = LocalUpscaleSettings.current?.collectAsState()?.value
    val wanted = queue != null && upscale && !url.isNullOrBlank() && (prefs == null || (prefs.enabled && kind in prefs.kinds))
    val fadeMs = imageFadeMs()
    if (!wanted) {
        AsyncImage(
            model = fadingModel(url, fadeMs),
            contentDescription = contentDescription,
            modifier = modifier,
            onSuccess = onSuccess,
            onError = onError,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
        )
        return
    }
    UpscalingImage(queue!!, url!!, contentDescription, kind, modifier, alignment, contentScale, alpha, onSuccess, onError, fadeMs)
}

@Composable
private fun UpscalingImage(
    queue: UpscaleQueue,
    url: String,
    contentDescription: String?,
    kind: UpscaleImageKind,
    modifier: Modifier,
    alignment: Alignment,
    contentScale: ContentScale,
    alpha: Float,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)?,
    onError: ((AsyncImagePainter.State.Error) -> Unit)?,
    fadeMs: Int,
) {
    var target by remember(url) { mutableStateOf(IntSize.Zero) }
    var source by remember(url) { mutableStateOf(IntSize.Zero) }
    var originalKey by remember(url) { mutableStateOf<MemoryCache.Key?>(null) }
    var done by remember(url, kind) { mutableStateOf<String?>(null) }
    var broken by remember(url, kind) { mutableStateOf<String?>(null) }

    val avatarMinPx = with(LocalDensity.current) { AVATAR_MIN.roundToPx() }
    val tooSmall = kind == UpscaleImageKind.AVATAR && maxOf(target.width, target.height) < avatarMinPx
    val bucketW = (target.width + TARGET_BUCKET_PX - 1) / TARGET_BUCKET_PX
    val bucketH = (target.height + TARGET_BUCKET_PX - 1) / TARGET_BUCKET_PX
    val sized = target.width > 0 && target.height > 0 && !tooSmall

    // A result already on disk for this size: shown at once, no job.
    val cached = remember(queue, url, kind, bucketW, bucketH, sized) {
        if (sized) queue.cached(url, kind, target.width, target.height) else null
    }

    // Ask once both sizes are known; the ticket is released when the image leaves the screen or its size bucket changes.
    var ticketKey by remember(url, kind) { mutableStateOf<String?>(null) }
    val ask = sized && cached == null && source.width > 0 && source.height > 0
    if (ask) {
        DisposableEffect(queue, url, kind, source, bucketW, bucketH) {
            val ticket = queue.request(url, kind, source.width, source.height, target.width, target.height)
            ticketKey = ticket.key
            onDispose { ticket.release() }
        }
    }
    val state = ticketKey?.takeIf { ask }?.let { key -> remember(queue, key) { queue.state(key) }.collectAsState().value }
    LaunchedEffect(state) {
        if (state is UpscaleState.Done) done = state.path
    }

    val path = (cached ?: done)?.takeIf { it != broken }
    val context = LocalPlatformContext.current
    val model: Any = if (path == null) {
        fadingModel(url, fadeMs) ?: url
    } else {
        remember(context, path, originalKey, fadeMs) {
            ImageRequest.Builder(context)
                .data(path.toPath())
                // the original stays on screen while the upscaled file decodes
                .placeholderMemoryCacheKey(originalKey)
                // The swap original -> upscaled is a refinement, not a load: no fade, the sharper copy simply replaces it. Only an
                // upscaled file shown first (no original on screen yet) fades in like any image.
                .crossfade(if (originalKey == null) fadeMs else 0)
                .build()
        }
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged { target = it },
        onSuccess = { s ->
            // only the original tells the source size (the upscaled file would report its own)
            if (s.result.request.data == url) {
                source = IntSize(s.result.image.width, s.result.image.height)
                originalKey = s.result.memoryCacheKey
            }
            onSuccess?.invoke(s)
        },
        onError = { e ->
            // An upscaled file gone missing (cache evicted) falls back to the original; the caller only hears about the original.
            if (path != null && e.result.request.data != url) {
                broken = path
            } else {
                onError?.invoke(e)
            }
        },
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
    )
}

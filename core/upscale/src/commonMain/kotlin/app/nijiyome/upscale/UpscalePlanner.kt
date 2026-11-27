// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Per-platform budget of the queue (plan §4.3, §4.5, §4.6). [platformUpscaleLimits] gives the shipped values. */
data class UpscaleLimits(
    /** Long side of a stored result: 2560 px on desktop, 1440 px on Android. */
    val maxOutputLongSide: Int,
    /** Cache cap used when [ImageUpscaleSettings.cacheMb] is null: 256 MB on desktop, 128 MB on Android. */
    val defaultCacheMb: Int,
    /** Pause between two jobs so the GPU is never kept busy: 50 ms on desktop, 250 ms on Android. */
    val pauseBetweenJobsMillis: Long,
    /** Idle time after which the engine gives its VRAM back. */
    val idleReleaseMillis: Long = 60_000,
    /** Pending jobs kept at most; the oldest are dropped past it (plan §4.4). */
    val maxPending: Int = 64,
)

/** What one upscale does: run the model at [scale], then scale the result down to [outWidth] x [outHeight] before storing it. */
data class UpscalePlan(val scale: Int, val outWidth: Int, val outHeight: Int)

sealed interface UpscaleDecision {
    data class Upscale(val plan: UpscalePlan) : UpscaleDecision
    data class Skip(val why: String) : UpscaleDecision
}

/**
 * Qualification and factor choice (plan §4.3). The needed factor is the one of `ContentScale.Crop`, the common case:
 * `max(targetW / sourceW, targetH / sourceH)`, where target is the on-screen size in pixels (dp x density).
 *
 * [targetWidth]/[targetHeight] should already be rounded up with [UpscaleKey.bucket], so every request sharing a key gets the same plan.
 * The rules that need more than pixels (blurred images, avatars under 64 dp, clearlogos, manga pages) are the caller's: the UI does
 * not request those at all.
 */
object UpscalePlanner {
    /** At or below this factor the original is already sharp enough. */
    const val MIN_FACTOR = 1.15

    /** Sources with a side under this are icons or placeholders, not worth the GPU. */
    const val MIN_SOURCE_SIDE = 48

    fun maxScale(quality: UpscaleQuality): Int = when (quality) {
        UpscaleQuality.FAST -> 2
        UpscaleQuality.BALANCED -> 3
        UpscaleQuality.BEST -> 4
    }

    fun decide(
        settings: ImageUpscaleSettings,
        limits: UpscaleLimits,
        kind: UpscaleImageKind,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): UpscaleDecision {
        if (!settings.enabled) return UpscaleDecision.Skip("disabled")
        if (kind !in settings.kinds) return UpscaleDecision.Skip("kind off")
        if (targetWidth <= 0 || targetHeight <= 0) return UpscaleDecision.Skip("not laid out")
        if (min(sourceWidth, sourceHeight) < MIN_SOURCE_SIDE) return UpscaleDecision.Skip("source too small")

        val needed = max(targetWidth.toDouble() / sourceWidth, targetHeight.toDouble() / sourceHeight)
        if (needed <= MIN_FACTOR) return UpscaleDecision.Skip("already large enough")

        // The output cap bounds the factor: a result never has a long side over the platform limit.
        val sourceLong = max(sourceWidth, sourceHeight)
        val capped = min(needed, limits.maxOutputLongSide.toDouble() / sourceLong)
        if (capped <= MIN_FACTOR) return UpscaleDecision.Skip("output cap")

        val scale = min(modelScaleFor(capped), maxScale(settings.quality))
        // A quality cap below the need stores the full model output; otherwise the result is scaled down to what is shown.
        val factor = min(capped, scale.toDouble())
        val outWidth = (sourceWidth * factor).roundToInt().coerceAtLeast(1)
        val outHeight = (sourceHeight * factor).roundToInt().coerceAtLeast(1)
        return UpscaleDecision.Upscale(UpscalePlan(scale = scale, outWidth = outWidth, outHeight = outHeight))
    }

    /** x2 up to a factor of 2, x3 up to 3, x4 beyond. */
    fun modelScaleFor(factor: Double): Int = when {
        factor <= 2.0 -> 2
        factor <= 3.0 -> 3
        else -> 4
    }
}

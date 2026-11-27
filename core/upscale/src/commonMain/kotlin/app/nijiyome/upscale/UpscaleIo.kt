// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

/** Decoded pixels: [rgba] is `width * height * 4` bytes, row-major, non-premultiplied RGBA. */
class RgbaImage(val width: Int, val height: Int, val rgba: ByteArray) {
    init {
        require(width > 0 && height > 0 && rgba.size == width * height * 4) { "bad RGBA buffer ${width}x$height (${rgba.size} bytes)" }
    }
}

/**
 * Where the source bytes come from (plan §4.2 step 3). Step 4 implements it over Coil's disk cache (`diskCache.openSnapshot(url)`)
 * and never downloads: null when the source is not cached, and the job is skipped.
 */
fun interface UpscaleSourceProvider {
    suspend fun read(url: String): ByteArray?
}

/**
 * RGBA <-> encoded image, one per platform (step 4: `BitmapFactory`/`Bitmap.compress` on Android, Skia on desktop). Calls are
 * blocking and run on the queue's worker.
 */
interface UpscaleImageCodec {
    /** Decodes [bytes] at full size, or null when it cannot. */
    fun decode(bytes: ByteArray): RgbaImage?

    /**
     * Scales [image] to [width] x [height] with a bilinear filter (a no-op when it is that size already) and encodes it as lossy
     * WebP at quality 92 (plan §4.5). Null on failure.
     */
    fun encode(image: RgbaImage, width: Int, height: Int): ByteArray?
}

/** A codec that decodes nothing (tests; the platforms ship their own since step 4). */
object UnavailableUpscaleCodec : UpscaleImageCodec {
    override fun decode(bytes: ByteArray): RgbaImage? = null
    override fun encode(image: RgbaImage, width: Int, height: Int): ByteArray? = null
}

/** The shipped [UpscaleLimits] of this platform. */
expect fun platformUpscaleLimits(): UpscaleLimits

/** This platform's [UpscaleImageCodec]. */
expect fun platformUpscaleCodec(): UpscaleImageCodec

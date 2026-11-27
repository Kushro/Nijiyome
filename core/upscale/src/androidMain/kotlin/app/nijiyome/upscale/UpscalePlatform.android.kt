// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

actual fun platformUpscaleLimits(): UpscaleLimits =
    UpscaleLimits(maxOutputLongSide = 1440, defaultCacheMb = 128, pauseBetweenJobsMillis = 250)

actual fun platformUpscaleCodec(): UpscaleImageCodec = AndroidUpscaleCodec

/**
 * BitmapFactory decode to straight-alpha RGBA (ARGB_8888 is R, G, B, A in memory), `createScaledBitmap(filter = true)` and
 * `compress(WEBP_LOSSY, 92)` (WEBP, lossy below quality 100, before API 30).
 */
internal object AndroidUpscaleCodec : UpscaleImageCodec {
    private const val WEBP_QUALITY = 92

    override fun decode(bytes: ByteArray): RgbaImage? = runCatching {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
            inMutable = false
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() } ?: return null
        }
        try {
            val buffer = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(buffer)
            RgbaImage(bitmap.width, bitmap.height, buffer.array())
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    override fun encode(image: RgbaImage, width: Int, height: Int): ByteArray? = runCatching {
        // A premultiplied bitmap: Canvas (inside createScaledBitmap) refuses straight alpha, and compress writes straight alpha back.
        val full = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        var scaled: Bitmap? = null
        try {
            val opaque = premultiplied(image.rgba)
            full.copyPixelsFromBuffer(ByteBuffer.wrap(opaque.first))
            full.setHasAlpha(!opaque.second)
            val out = if (width == image.width && height == image.height) full else {
                Bitmap.createScaledBitmap(full, width, height, true).also { scaled = it }
            }
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val stream = ByteArrayOutputStream()
            if (!out.compress(format, WEBP_QUALITY, stream)) return null
            stream.toByteArray()
        } finally {
            scaled?.takeIf { it !== full }?.recycle()
            full.recycle()
        }
    }.getOrNull()

    /** The pixels premultiplied (a copy only when some alpha is below 255), and whether the image is fully opaque. */
    private fun premultiplied(rgba: ByteArray): Pair<ByteArray, Boolean> {
        var opaque = true
        var i = 3
        while (i < rgba.size) {
            if (rgba[i] != 0xFF.toByte()) {
                opaque = false
                break
            }
            i += 4
        }
        if (opaque) return rgba to true
        val out = rgba.copyOf()
        var p = 0
        while (p < out.size) {
            val a = out[p + 3].toInt() and 0xFF
            if (a != 255) {
                out[p] = ((out[p].toInt() and 0xFF) * a / 255).toByte()
                out[p + 1] = ((out[p + 1].toInt() and 0xFF) * a / 255).toByte()
                out[p + 2] = ((out[p + 2].toInt() and 0xFF) * a / 255).toByte()
            }
            p += 4
        }
        return out to false
    }
}

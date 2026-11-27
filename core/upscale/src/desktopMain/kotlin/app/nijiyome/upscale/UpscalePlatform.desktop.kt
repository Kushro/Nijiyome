// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

actual fun platformUpscaleLimits(): UpscaleLimits =
    UpscaleLimits(maxOutputLongSide = 2560, defaultCacheMb = 256, pauseBetweenJobsMillis = 50)

actual fun platformUpscaleCodec(): UpscaleImageCodec = SkiaUpscaleCodec

/** Skia (the Skiko Compose Desktop and Coil already ship): decode to unpremultiplied RGBA, bilinear resize, lossy WebP 92. */
internal object SkiaUpscaleCodec : UpscaleImageCodec {
    private const val WEBP_QUALITY = 92

    override fun decode(bytes: ByteArray): RgbaImage? = runCatching {
        val image = Image.makeFromEncoded(bytes)
        try {
            val w = image.width
            val h = image.height
            if (w <= 0 || h <= 0) return null
            val bitmap = Bitmap.makeFromImage(image)
            try {
                val pixels = bitmap.readPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), w * 4, 0, 0)
                    ?: return null
                RgbaImage(w, h, pixels)
            } finally {
                bitmap.close()
            }
        } finally {
            image.close()
        }
    }.getOrNull()

    override fun encode(image: RgbaImage, width: Int, height: Int): ByteArray? = runCatching {
        val source = Image.makeRaster(
            ImageInfo(image.width, image.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            image.rgba,
            image.width * 4,
        )
        try {
            if (width == image.width && height == image.height) {
                source.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)?.bytes
            } else {
                // Raster surfaces are premultiplied; drawing converts, and the encoder writes straight alpha again.
                val surface = Surface.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
                try {
                    val paint = Paint()
                    try {
                        surface.canvas.drawImageRect(
                            source,
                            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                            Rect.makeWH(width.toFloat(), height.toFloat()),
                            SamplingMode.LINEAR,
                            paint,
                            true,
                        )
                    } finally {
                        paint.close()
                    }
                    val scaled = surface.makeImageSnapshot()
                    try {
                        scaled.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)?.bytes
                    } finally {
                        scaled.close()
                    }
                } finally {
                    surface.close()
                }
            }
        } finally {
            source.close()
        }
    }.getOrNull()
}

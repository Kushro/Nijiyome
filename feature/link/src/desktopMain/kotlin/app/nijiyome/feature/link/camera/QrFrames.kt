// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link.camera

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.awt.image.BufferedImage

/** One camera frame as 8-bit luminance, row by row ([width] bytes per row): what ZXing reads and what the preview shows. */
internal class GrayFrame(val width: Int, val height: Int, val luma: ByteArray) {
    init {
        require(width > 0 && height > 0 && luma.size >= width * height) { "frame $width x $height with ${luma.size} bytes" }
    }
}

/** QR decoding of camera frames (ZXing, the same hints as Android's analyzer). One instance per scan; not thread-safe. */
internal class QrFrameDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    /** The text of the QR code in [frame], or null when there is none (the normal case for most frames). */
    fun decode(frame: GrayFrame): String? = try {
        val source = PlanarYUVLuminanceSource(frame.luma, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text?.takeIf { it.isNotBlank() }
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

/** Frames of an open camera. [next] waits up to [timeoutMs] and answers null when no frame came in time. */
internal interface FrameStream : AutoCloseable {
    fun next(timeoutMs: Int): GrayFrame?

    override fun close()
}

/**
 * Reads [stream] until a frame holds a QR code and returns its text. Every frame goes to [onFrame] first (the preview). Runs on
 * the caller's (blocking-friendly) dispatcher; cancelling the coroutine stops it between frames.
 */
internal suspend fun scanQr(stream: FrameStream, onFrame: (GrayFrame) -> Unit = {}): String {
    val decoder = QrFrameDecoder()
    while (true) {
        currentCoroutineContext().ensureActive()
        val frame = stream.next(FRAME_WAIT_MS) ?: continue
        onFrame(frame)
        decoder.decode(frame)?.let { return it }
    }
}

private const val FRAME_WAIT_MS = 500

/** YUYV 4:2:2 (Y0 U Y1 V), [stride] bytes per row, to luminance: the Y bytes, which is all a QR decoder needs. */
internal fun yuyvToGray(data: ByteArray, width: Int, height: Int, stride: Int = width * 2): GrayFrame {
    val out = ByteArray(width * height)
    for (y in 0 until height) {
        val row = y * stride
        val dst = y * width
        for (x in 0 until width) out[dst + x] = data[row + x * 2]
    }
    return GrayFrame(width, height, out)
}

/** An image (a test picture, a decoded camera frame) as luminance (ITU-R BT.601 weights). */
internal fun BufferedImage.toGrayFrame(): GrayFrame {
    val out = ByteArray(width * height)
    val rgb = getRGB(0, 0, width, height, null, 0, width)
    for (i in rgb.indices) {
        val c = rgb[i]
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        out[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
    }
    return GrayFrame(width, height, out)
}

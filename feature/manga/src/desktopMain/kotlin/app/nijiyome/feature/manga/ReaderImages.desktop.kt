// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.model.ChapterPage
import app.nijiyome.ui.platform.DesktopPictures
import app.nijiyome.ui.platform.writeReplacing
import coil3.Bitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.CopyOption
import java.nio.file.Path

internal actual fun PlatformContext.imageLoaderContext(): PlatformContext = this

/**
 * v1.1 (F6): the page as PNG in `<pictures>/Nijiyome` (the same folder as the player's screenshots, as in Tatakai Next), never over
 * an earlier capture. Returns the path as the snackbar shows it (`~/Imágenes/Nijiyome/…`).
 */
internal actual suspend fun capturePage(context: PlatformContext, page: ChapterPage, fileStem: String): String? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context).data(page.url).apply { if (page.headers.isNotEmpty()) pageHeaders(page) }.build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withContext null
    val png = encodePng(result.image.toBitmap()) ?: return@withContext null
    val file = saveCapture(File(DesktopPictures.dir(), CAPTURE_ALBUM), captureFileName(fileStem), png) ?: return@withContext null
    DesktopPictures.shown(file)
}

private const val CAPTURE_ALBUM = "Nijiyome"

internal fun encodePng(bitmap: Bitmap): ByteArray? = runCatching {
    val image = Image.makeFromBitmap(bitmap)
    try {
        image.encodeToData(EncodedImageFormat.PNG)?.bytes
    } finally {
        image.close()
    }
}.getOrNull()

/**
 * Writes [bytes] as [name] in [dir] (created), or `name (2).png`… when it is taken, through [writeReplacing] (a temporary file and
 * a rename, a plain replace where the file system cannot rename atomically, the temporary file removed on any failure). null when
 * it fails. [move] is there for the tests.
 */
internal fun saveCapture(
    dir: File,
    name: String,
    bytes: ByteArray,
    move: (Path, Path, Array<CopyOption>) -> Unit = { a, b, o -> Files.move(a, b, *o) },
): File? = runCatching {
    dir.mkdirs()
    val stem = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "png")
    val target = generateSequence(1) { it + 1 }
        .map { n -> File(dir, if (n == 1) name else "$stem ($n).$ext") }
        .first { !it.exists() }
    writeReplacing(target, bytes, move)
    target
}.getOrNull()

internal actual val CropBordersTransformation: Transformation = DesktopCropBorders

/** v1.1 (F6): [BorderCrop] over the Skia bitmap, the same detection as Android's. */
internal object DesktopCropBorders : Transformation() {
    override val cacheKey: String = "nijiyome-crop-borders"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 32 || h < 32) return input
        val pixels = argbPixels(input) ?: return input
        val box = BorderCrop.bounds(pixels, w, h) ?: return input
        return cropBitmap(input, box[0], box[1], box[2] - box[0], box[3] - box[1]) ?: input
    }
}

/** The pixels of [bitmap] as ARGB ints (what [BorderCrop] reads), or null when Skia cannot read them. */
internal fun argbPixels(bitmap: Bitmap): IntArray? {
    val w = bitmap.width
    val h = bitmap.height
    val bytes = bitmap.readPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), w * 4, 0, 0) ?: return null
    // BGRA bytes read as little-endian ints are 0xAARRGGBB.
    val ints = IntArray(w * h)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints)
    return ints
}

/** A copy of the [width] x [height] area of [bitmap] at ([left], [top]), in its own color type; null when Skia refuses. */
internal fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val info = ImageInfo(width, height, bitmap.colorType, bitmap.alphaType, bitmap.colorSpace)
    val rowBytes = width * info.bytesPerPixel
    val bytes = bitmap.readPixels(info, rowBytes, left, top) ?: return null
    val out = Bitmap()
    if (!out.allocPixels(info, rowBytes) || !out.installPixels(info, bytes, rowBytes)) return null
    out.setImmutable()
    return out
}

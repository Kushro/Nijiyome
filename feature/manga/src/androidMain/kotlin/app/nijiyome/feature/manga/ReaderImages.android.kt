// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.nijiyome.model.ChapterPage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual fun PlatformContext.imageLoaderContext(): PlatformContext = applicationContext

/** MediaStore on API 29+, the app's own Pictures folder before. */
internal actual suspend fun capturePage(context: PlatformContext, page: ChapterPage, fileStem: String): String? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context).data(page.url)
        .apply { if (page.headers.isNotEmpty()) pageHeaders(page) }
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context.applicationContext).execute(request) as? SuccessResult ?: return@withContext null
    val bitmap = result.image.toBitmap()
    val name = captureFileName(fileStem)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nijiyome")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        context.contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: return@withContext null
        "Pictures/Nijiyome/$name"
    } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Nijiyome").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file.absolutePath
    }
}

internal actual val CropBordersTransformation: Transformation = AndroidCropBorders

private object AndroidCropBorders : Transformation() {
    override val cacheKey: String = "nijiyome-crop-borders"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 32 || h < 32) return input
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        val box = BorderCrop.bounds(pixels, w, h) ?: return input
        return Bitmap.createBitmap(input, box[0], box[1], box[2] - box[0], box[3] - box[1])
    }
}

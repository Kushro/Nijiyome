// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import app.nijiyome.model.ChapterPage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.Transformation

// Page images of the manga reader: Coil requests with the extension headers (Referer...), border cropping (checklist manga.reader.fit
// "recorte de paneles"), cache warming (prefetch / eager loading) and the page capture (checklist manga.reader.behavior).

internal fun pageRequest(context: PlatformContext, page: ChapterPage, cropBorders: Boolean, retry: Int = 0): ImageRequest =
    ImageRequest.Builder(context)
        .data(page.url)
        .apply {
            if (page.headers.isNotEmpty()) pageHeaders(page)
            if (cropBorders) transformations(CropBordersTransformation)
            if (retry > 0) memoryCacheKey("${page.url}#retry$retry")
        }
        .build()

/** The extension headers of [page] (Referer...) on an image request. */
internal fun ImageRequest.Builder.pageHeaders(page: ChapterPage): ImageRequest.Builder =
    httpHeaders(NetworkHeaders.Builder().apply { page.headers.forEach { (k, v) -> set(k, v) } }.build())

/** Loads [pages] into the image cache (prefetch of the next chapter, eager loading, lazy look-ahead). */
internal fun warmPages(context: PlatformContext, pages: List<ChapterPage>, cropBorders: Boolean) {
    val loader = SingletonImageLoader.get(context.imageLoaderContext())
    pages.forEach { loader.enqueue(pageRequest(context, it, cropBorders)) }
}

/** The context the shared image loader is taken from (Android: the application context). */
internal expect fun PlatformContext.imageLoaderContext(): PlatformContext

/** Saves the page image to Pictures/Nijiyome. Returns the display path, or null when it could not be saved. */
internal expect suspend fun capturePage(context: PlatformContext, page: ChapterPage, fileStem: String): String?

/** File name of a captured page: [fileStem] without characters a file system rejects, as PNG. */
internal fun captureFileName(fileStem: String): String = fileStem.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(80) + ".png"

/** Trims uniform borders (white or black margins of scans) around a page ([BorderCrop] over the platform bitmap). */
internal expect val CropBordersTransformation: Transformation

/** Pure border detection over ARGB pixels (JVM-tested). */
internal object BorderCrop {
    private const val TOLERANCE = 24

    /** [left, top, right, bottom) of the content, or null when nothing worth cropping (< 2 % per side or the page is blank). */
    fun bounds(pixels: IntArray, w: Int, h: Int): IntArray? {
        val ref = pixels[0]
        fun near(c: Int): Boolean {
            val dr = ((c shr 16) and 0xff) - ((ref shr 16) and 0xff)
            val dg = ((c shr 8) and 0xff) - ((ref shr 8) and 0xff)
            val db = (c and 0xff) - (ref and 0xff)
            return kotlin.math.abs(dr) <= TOLERANCE && kotlin.math.abs(dg) <= TOLERANCE && kotlin.math.abs(db) <= TOLERANCE
        }
        fun rowBlank(y: Int): Boolean = (0 until w step 2).all { x -> near(pixels[y * w + x]) }
        fun colBlank(x: Int, top: Int, bottom: Int): Boolean = (top until bottom step 2).all { y -> near(pixels[y * w + x]) }
        var top = 0
        while (top < h && rowBlank(top)) top++
        if (top >= h) return null
        var bottom = h
        while (bottom > top && rowBlank(bottom - 1)) bottom--
        var left = 0
        while (left < w && colBlank(left, top, bottom)) left++
        var right = w
        while (right > left && colBlank(right - 1, top, bottom)) right--
        val minX = w / 50
        val minY = h / 50
        if (top < minY && left < minX && h - bottom < minY && w - right < minX) return null
        // Keep a small margin so text is not touching the edge.
        val m = 4
        return intArrayOf((left - m).coerceAtLeast(0), (top - m).coerceAtLeast(0), (right + m).coerceAtMost(w), (bottom + m).coerceAtMost(h))
    }
}

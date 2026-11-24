// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.manga

import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderImagesDesktopTest {
    /** A [w] x [h] page of [margin] color with a dark block (the drawing) at [left, top, right, bottom). */
    private fun page(w: Int, h: Int, margin: Int, content: IntArray, ink: Int = 0xFF202020.toInt()): Bitmap {
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in content[0] until content[2] && y in content[1] until content[3]) ink else margin
        }
        val bytes = ByteBuffer.allocate(w * h * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(pixels) }.array()
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        return Bitmap().apply {
            allocPixels(info)
            installPixels(info, bytes, w * 4)
        }
    }

    @Test
    fun whiteMarginsAreCropped() = runBlocking {
        val input = page(400, 600, 0xFFFFFFFF.toInt(), intArrayOf(40, 60, 360, 540))
        val out = DesktopCropBorders.transform(input, Size.ORIGINAL)
        // BorderCrop keeps 4 px around the content.
        assertEquals(360 - 40 + 8, out.width)
        assertEquals(540 - 60 + 8, out.height)
        val px = assertNotNull(argbPixels(out))
        assertEquals(0xFFFFFFFF.toInt(), px[0]) // the kept margin
        assertEquals(0xFF202020.toInt(), px[4 * out.width + 4]) // first pixel of the drawing
    }

    @Test
    fun blackMarginsAreCroppedToo() = runBlocking {
        val input = page(300, 300, 0xFF000000.toInt(), intArrayOf(30, 20, 270, 280), ink = 0xFFE0E0E0.toInt())
        val out = DesktopCropBorders.transform(input, Size.ORIGINAL)
        assertEquals(248, out.width)
        assertEquals(268, out.height)
    }

    @Test
    fun aPageWithoutMarginsIsLeftAlone() = runBlocking {
        val input = page(200, 300, 0xFFFFFFFF.toInt(), intArrayOf(1, 1, 199, 299))
        assertSame(input, DesktopCropBorders.transform(input, Size.ORIGINAL))
        val tiny = page(16, 16, 0xFFFFFFFF.toInt(), intArrayOf(4, 4, 12, 12))
        assertSame(tiny, DesktopCropBorders.transform(tiny, Size.ORIGINAL))
    }

    @Test
    fun theCaptureIsAPngThatNeverReplacesAnEarlierOne() {
        val dir = Files.createTempDirectory("captures").toFile()
        val png = assertNotNull(encodePng(page(64, 48, 0xFFFFFFFF.toInt(), intArrayOf(8, 8, 56, 40))))
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(64, decoded.width)
        assertEquals(48, decoded.height)
        val name = captureFileName("One Piece: 1100/3")
        val first = assertNotNull(saveCapture(dir.resolve("Nijiyome"), name, png))
        val second = assertNotNull(saveCapture(dir.resolve("Nijiyome"), name, png))
        assertEquals("One_Piece_1100_3.png", first.name)
        assertEquals("One_Piece_1100_3 (2).png", second.name)
        assertTrue(first.readBytes().contentEquals(png))
        assertEquals(listOf(first.name, second.name).sorted(), dir.resolve("Nijiyome").list()!!.sorted())
        // Skia reads the file back.
        val image = Image.makeFromEncoded(second.readBytes())
        assertEquals(64, image.width)
        image.close()
        dir.deleteRecursively()
    }

    @Test
    fun withoutAtomicRenamesTheCaptureIsStillSavedAndNoTemporaryFileStays() {
        // v1.1 review #15: some mounts (FUSE, network, exFAT) refuse ATOMIC_MOVE
        val dir = Files.createTempDirectory("captures").toFile()
        val noAtomic: (java.nio.file.Path, java.nio.file.Path, Array<java.nio.file.CopyOption>) -> Unit = { a, b, o ->
            if (java.nio.file.StandardCopyOption.ATOMIC_MOVE in o) throw java.nio.file.AtomicMoveNotSupportedException(a.toString(), b.toString(), "test")
            Files.move(a, b, *o)
        }
        val saved = assertNotNull(saveCapture(dir, "page.png", byteArrayOf(1, 2, 3), noAtomic))
        assertEquals("page.png", saved.name)
        assertTrue(saved.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(listOf("page.png"), dir.list()!!.toList())
        // a write that fails altogether answers null and leaves nothing behind
        val failing: (java.nio.file.Path, java.nio.file.Path, Array<java.nio.file.CopyOption>) -> Unit = { _, _, _ -> throw java.io.IOException("disk full") }
        assertEquals(null, saveCapture(dir, "page.png", byteArrayOf(4), failing))
        assertEquals(listOf("page.png"), dir.list()!!.toList())
        dir.deleteRecursively()
    }
}

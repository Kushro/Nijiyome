// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link.camera

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// No real camera is opened by these tests: frames come from generated pictures, a fake sysfs tree, or FFmpeg reading a PNG.
class QrScannerDesktopTest {
    private val link = "nijiyome://extensionserver/connect?u=https%3A%2F%2F192.168.1.20%3A47321&c=ABCD-1234&t=9f86d081884c7d65"

    /** A QR code of [text] drawn on a light, slightly noisy "desk" background, off-center, like a phone held in front of a webcam. */
    private fun cameraLikeImage(text: String, width: Int = 640, height: Int = 480): BufferedImage {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
        val module = 5
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(196, 188, 170)
        g.fillRect(0, 0, width, height)
        val ox = 150
        val oy = 40
        g.color = Color.WHITE
        g.fillRect(ox, oy, matrix.width * module, matrix.height * module)
        g.color = Color(20, 20, 30)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) g.fillRect(ox + x * module, oy + y * module, module, module)
        g.dispose()
        val rnd = java.util.Random(7)
        for (i in 0 until 4000) {
            val x = rnd.nextInt(width)
            val y = rnd.nextInt(height)
            val c = Color(image.getRGB(x, y))
            val d = rnd.nextInt(41) - 20
            image.setRGB(x, y, Color((c.red + d).coerceIn(0, 255), (c.green + d).coerceIn(0, 255), (c.blue + d).coerceIn(0, 255)).rgb)
        }
        return image
    }

    @Test
    fun decodesTheQrOfAPicture() {
        assertEquals(link, QrFrameDecoder().decode(cameraLikeImage(link).toGrayFrame()))
    }

    @Test
    fun aFrameWithoutQrGivesNull() {
        val blank = BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = Color.GRAY
            g.fillRect(0, 0, 320, 240)
            g.dispose()
        }
        assertNull(QrFrameDecoder().decode(blank.toGrayFrame()))
    }

    @Test
    fun yuyvKeepsTheLuminanceAndHonoursTheStride() {
        // 2 x 2 pixels, rows padded to 6 bytes: Y0 U Y1 V | pad pad
        val data = byteArrayOf(10, 99, 20, 99, 0, 0, 30, 99, 40, 99, 0, 0)
        val frame = yuyvToGray(data, 2, 2, stride = 6)
        assertEquals(listOf<Byte>(10, 20, 30, 40), frame.luma.toList())
    }

    @Test
    fun yuyvFramesOfAQrDecode() {
        val gray = cameraLikeImage(link).toGrayFrame()
        val yuyv = ByteArray(gray.width * gray.height * 2)
        for (i in 0 until gray.width * gray.height) {
            yuyv[i * 2] = gray.luma[i]
            yuyv[i * 2 + 1] = 0x80.toByte()
        }
        assertEquals(link, QrFrameDecoder().decode(yuyvToGray(yuyv, gray.width, gray.height)))
    }

    @Test
    fun scanStopsAtTheFirstFrameWithACode() = runBlocking {
        val frames = ArrayDeque(listOf(null, BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB).toGrayFrame(), cameraLikeImage(link).toGrayFrame()))
        var seen = 0
        var closed = false
        val stream = object : FrameStream {
            override fun next(timeoutMs: Int): GrayFrame? = frames.removeFirstOrNull()

            override fun close() {
                closed = true
            }
        }
        val text = stream.use { s -> scanQr(s) { seen++ } }
        assertEquals(link, text)
        assertEquals(2, seen)
        assertTrue(closed)
    }

    @Test
    fun theVirtualCameraOfAnImageScans() = runBlocking {
        val png = Files.createTempFile("qr", ".png").toFile().apply { deleteOnExit() }
        ImageIO.write(cameraLikeImage(link), "png", png)
        val backend = ImageWebcam(png)
        val camera = backend.devices().single()
        val text = withTimeout(5_000) { backend.open(camera).use { scanQr(it) } }
        assertEquals(link, text)
        assertEquals(emptyList(), ImageWebcam(File(png.path + ".missing")).devices())
    }

    @Test
    fun ioctlNumbersMatchVideodev2() {
        // The values the kernel headers give on x86_64 (linux/videodev2.h).
        assertEquals(0x80685600L, V4l2.VIDIOC_QUERYCAP)
        assertEquals(0xC0D05605L, V4l2.VIDIOC_S_FMT)
        assertEquals(0xC0145608L, V4l2.VIDIOC_REQBUFS)
        assertEquals(0xC0585609L, V4l2.VIDIOC_QUERYBUF)
        assertEquals(0xC058560FL, V4l2.VIDIOC_QBUF)
        assertEquals(0xC0585611L, V4l2.VIDIOC_DQBUF)
        assertEquals(0x40045612L, V4l2.VIDIOC_STREAMON)
        assertEquals(0x40045613L, V4l2.VIDIOC_STREAMOFF)
        assertEquals(0x56595559, V4l2.PIX_YUYV)
    }

    @Test
    fun captureNodesComeFromSysfsWithoutOpeningAnything() {
        val root = Files.createTempDirectory("v4l").toFile()
        fun node(name: String, label: String?, index: String?) = File(root, name).apply {
            mkdirs()
            label?.let { File(this, "name").writeText("$it\n") }
            index?.let { File(this, "index").writeText("$it\n") }
        }
        node("video0", "Integrated Camera: Integrated C", "0")
        node("video1", "Integrated Camera: Integrated C", "1") // UVC metadata node
        node("video10", "OBS Virtual Camera", "0")
        node("video2", null, null)
        node("vbi0", "not video", "0")
        val devices = V4l2.devices(root, File("/dev"))
        assertEquals(listOf("/dev/video0", "/dev/video2", "/dev/video10"), devices.map { it.id })
        assertEquals(listOf("Integrated Camera: Integrated C", "video2", "OBS Virtual Camera"), devices.map { it.name })
        assertEquals(emptyList(), V4l2.devices(File(root, "missing"), File("/dev")))
        root.deleteRecursively()
    }

    @Test
    fun directShowListingGivesTheVideoDevices() {
        val modern = """
            [dshow @ 000001] "Integrated Camera" (video)
            [dshow @ 000001]   Alternative name "@device_pnp_\\?\usb#vid_04f2"
            [dshow @ 000001] "OBS Virtual Camera" (video)
            [dshow @ 000001] "Microphone (Realtek(R) Audio)" (audio)
            [dshow @ 000001]   Alternative name "@device_cm_{33D9A762}"
            dummy: Immediate exit requested
        """.trimIndent()
        assertEquals(listOf("Integrated Camera", "OBS Virtual Camera"), Ffmpeg.dshowDevices(modern).map { it.id })
        val older = """
            [dshow @ 0x1] DirectShow video devices (some may be both video and audio devices)
            [dshow @ 0x1]  "USB2.0 HD UVC WebCam"
            [dshow @ 0x1] DirectShow audio devices
            [dshow @ 0x1]  "Microphone Array"
        """.trimIndent()
        assertEquals(listOf("USB2.0 HD UVC WebCam"), Ffmpeg.dshowDevices(older).map { it.name })
    }

    @Test
    fun ffmpegIsLookedUpOnThePath() {
        val dir = Files.createTempDirectory("bin").toFile()
        val exe = File(dir, "ffmpeg").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals(exe.path, Ffmpeg.find(listOf("/nonexistent", dir.path).joinToString(File.pathSeparator), windows = false))
        assertNull(Ffmpeg.find("/nonexistent", windows = false))
        dir.deleteRecursively()
    }

    /** The FFmpeg reader end to end, with a still picture as its input instead of a camera. Skipped when ffmpeg is not installed. */
    @Test
    fun ffmpegFramesOfATestPictureScan() = runBlocking {
        val ffmpeg = Ffmpeg.find() ?: return@runBlocking println("ffmpeg not on the PATH: skipped")
        val png = Files.createTempFile("qr", ".png").toFile().apply { deleteOnExit() }
        ImageIO.write(cameraLikeImage(link, 800, 600), "png", png)
        val text = withTimeout(20_000) { Ffmpeg.open(ffmpeg, listOf("-loop", "1", "-i", png.path)).use { scanQr(it) } }
        assertEquals(link, text)
    }
}

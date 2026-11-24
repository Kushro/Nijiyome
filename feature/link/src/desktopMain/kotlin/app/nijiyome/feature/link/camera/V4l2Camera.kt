// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link.camera

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.io.File
import java.io.IOException

/**
 * Linux webcams through V4L2 (`/dev/videoN`, memory-mapped streaming, the libc calls through JNA): no extra library and nothing
 * installed. Asks for YUYV at [WIDTH] x [HEIGHT]; a camera that only offers other formats (MJPEG-only) throws
 * [UnsupportedCameraFormat] and the caller falls back to FFmpeg. The structure offsets are the x86_64 ones (the only Linux build).
 */
internal object V4l2 {
    const val WIDTH = 640
    const val HEIGHT = 480

    /** The capture nodes the kernel lists, by name: `index` 0 of a device (UVC adds a metadata node with index 1). No device is opened. */
    fun devices(sysfs: File = File("/sys/class/video4linux"), dev: File = File("/dev")): List<Webcam> =
        sysfs.listFiles { f -> f.name.matches(NODE) }.orEmpty()
            .filter { node -> readText(File(node, "index"))?.trim()?.toIntOrNull()?.let { it == 0 } ?: true }
            .sortedBy { it.name.removePrefix("video").toIntOrNull() ?: Int.MAX_VALUE }
            .map { node ->
                Webcam(id = File(dev, node.name).path, name = readText(File(node, "name"))?.trim()?.takeIf { it.isNotEmpty() } ?: node.name)
            }

    private val NODE = Regex("video\\d+")

    private fun readText(file: File): String? = runCatching { file.readText() }.getOrNull()

    fun open(path: String): FrameStream = V4l2Stream(path)

    // ---------------------------------------------------------------- ioctl numbers (linux/videodev2.h, _IOC on x86_64)

    private fun ioc(dir: Int, nr: Int, size: Int): Long = (dir.toLong() shl 30) or (size.toLong() shl 16) or ('V'.code.toLong() shl 8) or nr.toLong()

    private const val READ = 2
    private const val WRITE = 1
    internal const val CAPABILITY_SIZE = 104
    internal const val FORMAT_SIZE = 208
    internal const val REQUESTBUFFERS_SIZE = 20
    internal const val BUFFER_SIZE = 88
    internal val VIDIOC_QUERYCAP = ioc(READ, 0, CAPABILITY_SIZE)
    internal val VIDIOC_S_FMT = ioc(READ or WRITE, 5, FORMAT_SIZE)
    internal val VIDIOC_REQBUFS = ioc(READ or WRITE, 8, REQUESTBUFFERS_SIZE)
    internal val VIDIOC_QUERYBUF = ioc(READ or WRITE, 9, BUFFER_SIZE)
    internal val VIDIOC_QBUF = ioc(READ or WRITE, 15, BUFFER_SIZE)
    internal val VIDIOC_DQBUF = ioc(READ or WRITE, 17, BUFFER_SIZE)
    internal val VIDIOC_STREAMON = ioc(WRITE, 18, 4)
    internal val VIDIOC_STREAMOFF = ioc(WRITE, 19, 4)

    internal fun fourcc(code: String): Int = code[0].code or (code[1].code shl 8) or (code[2].code shl 16) or (code[3].code shl 24)

    internal val PIX_YUYV = fourcc("YUYV")
    internal val PIX_GREY = fourcc("GREY")

    const val BUF_TYPE_VIDEO_CAPTURE = 1
    const val MEMORY_MMAP = 1
    const val CAP_VIDEO_CAPTURE = 0x1
    const val CAP_STREAMING = 0x04000000
    const val CAP_DEVICE_CAPS = 0x80000000.toInt()

    @Suppress("FunctionName")
    internal interface LibC : Library {
        fun open(path: String, flags: Int): Int

        fun close(fd: Int): Int

        fun ioctl(fd: Int, request: NativeLong, arg: Pointer): Int

        fun mmap(addr: Pointer?, length: NativeLong, prot: Int, flags: Int, fd: Int, offset: NativeLong): Pointer?

        fun munmap(addr: Pointer, length: NativeLong): Int

        fun poll(fds: Pointer, nfds: NativeLong, timeout: Int): Int
    }

    internal val libc: LibC by lazy { Native.load("c", LibC::class.java) }
}

/** The camera answered, but with no format this reader decodes (YUYV / GREY). */
internal class UnsupportedCameraFormat(message: String) : IOException(message)

private class V4l2Stream(path: String) : FrameStream {
    private val c = V4l2.libc
    private val fd: Int = c.open(path, O_RDWR or O_NONBLOCK).also { if (it < 0) throw IOException("open $path: errno ${Native.getLastError()}") }
    private val buffers = mutableListOf<Pair<Pointer, Long>>()
    private var width = V4l2.WIDTH
    private var height = V4l2.HEIGHT
    private var stride = V4l2.WIDTH * 2
    private var pixel = V4l2.PIX_YUYV
    private var streaming = false
    @Volatile private var closed = false

    init {
        try {
            setUp(path)
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    private fun setUp(path: String) {
        val cap = Memory(V4l2.CAPABILITY_SIZE.toLong()).apply { clear() }
        ioctl(V4l2.VIDIOC_QUERYCAP, cap, "QUERYCAP")
        val all = cap.getInt(84)
        val caps = if (all and V4l2.CAP_DEVICE_CAPS != 0) cap.getInt(88) else all
        if (caps and V4l2.CAP_VIDEO_CAPTURE == 0 || caps and V4l2.CAP_STREAMING == 0) throw IOException("$path is not a streaming capture device")

        val fmt = Memory(V4l2.FORMAT_SIZE.toLong()).apply { clear() }
        fmt.setInt(0, V4l2.BUF_TYPE_VIDEO_CAPTURE)
        fmt.setInt(8, V4l2.WIDTH)
        fmt.setInt(12, V4l2.HEIGHT)
        fmt.setInt(16, V4l2.PIX_YUYV)
        ioctl(V4l2.VIDIOC_S_FMT, fmt, "S_FMT")
        width = fmt.getInt(8)
        height = fmt.getInt(12)
        pixel = fmt.getInt(16)
        val bytesPerPixel = if (pixel == V4l2.PIX_GREY) 1 else 2
        stride = fmt.getInt(24).takeIf { it >= width * bytesPerPixel } ?: (width * bytesPerPixel)
        if (pixel != V4l2.PIX_YUYV && pixel != V4l2.PIX_GREY) throw UnsupportedCameraFormat("pixel format 0x${pixel.toUInt().toString(16)}")
        if (width <= 0 || height <= 0) throw IOException("frame size $width x $height")

        val req = Memory(V4l2.REQUESTBUFFERS_SIZE.toLong()).apply { clear() }
        req.setInt(0, BUFFER_COUNT)
        req.setInt(4, V4l2.BUF_TYPE_VIDEO_CAPTURE)
        req.setInt(8, V4l2.MEMORY_MMAP)
        ioctl(V4l2.VIDIOC_REQBUFS, req, "REQBUFS")
        val count = req.getInt(0)
        if (count < 1) throw IOException("no capture buffers")
        for (i in 0 until count) {
            val buf = buffer(i)
            ioctl(V4l2.VIDIOC_QUERYBUF, buf, "QUERYBUF")
            val length = buf.getInt(72).toLong() and 0xffffffffL
            val offset = buf.getInt(64).toLong() and 0xffffffffL
            val mapped = c.mmap(null, NativeLong(length), PROT_READ, MAP_SHARED, fd, NativeLong(offset))
            if (mapped == null || Pointer.nativeValue(mapped) == -1L) throw IOException("mmap: errno ${Native.getLastError()}")
            buffers += mapped to length
            ioctl(V4l2.VIDIOC_QBUF, buf, "QBUF")
        }
        ioctl(V4l2.VIDIOC_STREAMON, typeArg(), "STREAMON")
        streaming = true
    }

    // next() and close() hold the same lock: the mapped buffers and the fd are never released under a read (v1.1 review #2).
    @Synchronized
    override fun next(timeoutMs: Int): GrayFrame? {
        if (closed) return null
        val pfd = Memory(8).apply { clear() }
        pfd.setInt(0, fd)
        pfd.setShort(4, POLLIN)
        val ready = c.poll(pfd, NativeLong(1), timeoutMs)
        if (ready <= 0) return null
        val buf = buffer(0)
        if (c.ioctl(fd, NativeLong(V4l2.VIDIOC_DQBUF), buf) < 0) {
            val errno = Native.getLastError()
            if (errno == EAGAIN || errno == EINTR) return null
            throw IOException("DQBUF: errno $errno")
        }
        val index = buf.getInt(0)
        val used = buf.getInt(8)
        val (mapped, length) = buffers.getOrNull(index) ?: return null
        val size = minOf(if (used > 0) used.toLong() else length, length).toInt()
        val bytes = mapped.getByteArray(0, size)
        ioctl(V4l2.VIDIOC_QBUF, buf, "QBUF")
        val needed = stride * (height - 1) + width * (if (pixel == V4l2.PIX_GREY) 1 else 2)
        if (bytes.size < needed) return null
        return if (pixel == V4l2.PIX_GREY) {
            GrayFrame(width, height, ByteArray(width * height).also { out -> for (y in 0 until height) System.arraycopy(bytes, y * stride, out, y * width, width) })
        } else {
            yuyvToGray(bytes, width, height, stride)
        }
    }

    private fun buffer(index: Int): Memory = Memory(V4l2.BUFFER_SIZE.toLong()).apply {
        clear()
        setInt(0, index)
        setInt(4, V4l2.BUF_TYPE_VIDEO_CAPTURE)
        setInt(60, V4l2.MEMORY_MMAP)
    }

    private fun typeArg(): Memory = Memory(4).apply { setInt(0, V4l2.BUF_TYPE_VIDEO_CAPTURE) }

    private fun ioctl(request: Long, arg: Pointer, what: String) {
        while (true) {
            if (c.ioctl(fd, NativeLong(request), arg) >= 0) return
            val errno = Native.getLastError()
            if (errno != EINTR) throw IOException("$what: errno $errno")
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (streaming) runCatching { c.ioctl(fd, NativeLong(V4l2.VIDIOC_STREAMOFF), typeArg()) }
        streaming = false
        buffers.forEach { (p, len) -> runCatching { c.munmap(p, NativeLong(len)) } }
        buffers.clear()
        c.close(fd)
    }

    private companion object {
        const val BUFFER_COUNT = 4
        const val O_RDWR = 2
        const val O_NONBLOCK = 0x800
        const val PROT_READ = 1
        const val MAP_SHARED = 1
        const val POLLIN: Short = 1
        const val EAGAIN = 11
        const val EINTR = 4
    }
}

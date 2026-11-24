// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link.camera

import androidx.compose.runtime.mutableStateOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import javax.imageio.ImageIO

/** A camera the system lists: [id] is what its backend opens (`/dev/video0`, a DirectShow name, an image path). */
internal data class Webcam(val id: String, val name: String)

/** Lists and opens cameras. Listing never opens a device (the camera light stays off until the user presses "Escanear"). */
internal interface WebcamBackend {
    fun devices(): List<Webcam>

    /** Opens [camera]; throws [IOException] when it cannot. Blocking: call it off the UI thread. */
    fun open(camera: Webcam): FrameStream

    /** [devices] is cheap enough for the UI thread (a few files); false when it runs a program (Windows' FFmpeg listing). */
    val listingIsCheap: Boolean get() = true
}

/**
 * The desktop's cameras. Linux: V4L2 (FFmpeg for a camera without YUYV, when `ffmpeg` is installed). Windows: FFmpeg's DirectShow
 * input when `ffmpeg.exe` is on the PATH; without it there is no scanner and the screens offer pasting the link, as before.
 * The system property `nijiyome.webcam.image` (a PNG / JPEG path) replaces every camera with that picture: a virtual camera for
 * tests and headless runs, so no real camera is ever needed (or opened) to check the scanner.
 */
internal object DesktopWebcams {
    private val os = System.getProperty("os.name").orEmpty().lowercase()

    val backend: WebcamBackend by lazy {
        System.getProperty(IMAGE_PROPERTY)?.takeIf { it.isNotBlank() }?.let { return@lazy ImageWebcam(File(it)) }
        val ffmpeg = Ffmpeg.find()
        when {
            os.startsWith("linux") -> LinuxWebcams(ffmpeg)
            os.startsWith("windows") && ffmpeg != null -> DshowWebcams(ffmpeg)
            else -> NoWebcams
        }
    }

    /**
     * Whether a camera is there, without blocking the caller (it is read while composing). Linux: listed every time from sysfs, a
     * webcam can be plugged in later. Windows: listed once by FFmpeg on a background thread; false until it answers, then the
     * snapshot state recomposes whoever read it (v1.1 review #12).
     */
    val available: Boolean get() = availableOf(backend)

    private val listed = mutableStateOf<Boolean?>(null)
    private val listing = AtomicBoolean(false)

    internal fun availableOf(backend: WebcamBackend): Boolean {
        if (backend.listingIsCheap) return runCatching { backend.devices().isNotEmpty() }.getOrDefault(false)
        listed.value?.let { return it }
        if (listing.compareAndSet(false, true)) {
            thread(isDaemon = true, name = "webcam-list") { listed.value = runCatching { backend.devices().isNotEmpty() }.getOrDefault(false) }
        }
        return false
    }

    const val IMAGE_PROPERTY = "nijiyome.webcam.image"
}

internal object NoWebcams : WebcamBackend {
    override fun devices(): List<Webcam> = emptyList()

    override fun open(camera: Webcam): FrameStream = throw IOException("no camera")
}

internal class LinuxWebcams(private val ffmpeg: String?) : WebcamBackend {
    override fun devices(): List<Webcam> = V4l2.devices()

    override fun open(camera: Webcam): FrameStream = try {
        V4l2.open(camera.id)
    } catch (e: UnsupportedCameraFormat) {
        ffmpeg?.let { Ffmpeg.open(it, listOf("-f", "v4l2", "-i", camera.id)) } ?: throw e
    }
}

internal class DshowWebcams(private val ffmpeg: String) : WebcamBackend {
    private val listed: List<Webcam> by lazy { Ffmpeg.dshowDevices(Ffmpeg.listDshow(ffmpeg)) }

    override fun devices(): List<Webcam> = listed

    override val listingIsCheap: Boolean get() = false

    override fun open(camera: Webcam): FrameStream = Ffmpeg.open(ffmpeg, listOf("-f", "dshow", "-i", "video=${camera.id}"))
}

/** One picture shown as a camera at about 10 frames a second (see [DesktopWebcams]). */
internal class ImageWebcam(private val file: File) : WebcamBackend {
    override fun devices(): List<Webcam> = if (file.isFile) listOf(Webcam(file.path, file.name)) else emptyList()

    override fun open(camera: Webcam): FrameStream {
        val frame = (ImageIO.read(File(camera.id)) ?: throw IOException("not an image: ${camera.id}")).toGrayFrame()
        return object : FrameStream {
            override fun next(timeoutMs: Int): GrayFrame {
                Thread.sleep(minOf(timeoutMs, 100).toLong())
                return frame
            }

            override fun close() = Unit
        }
    }
}

/**
 * The `ffmpeg` program as a camera reader: [open] runs it with an input (`-f v4l2 -i /dev/video0`, `-f dshow -i video=…`, or a
 * test picture) and reads raw 8-bit gray frames of [V4l2.WIDTH] x [V4l2.HEIGHT] (letterboxed) from its output.
 */
internal object Ffmpeg {
    fun find(path: String? = System.getenv("PATH"), windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")): String? {
        val name = if (windows) "ffmpeg.exe" else "ffmpeg"
        return path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && (windows || it.canExecute()) }
            ?.path
    }

    fun command(ffmpeg: String, input: List<String>, width: Int = V4l2.WIDTH, height: Int = V4l2.HEIGHT): List<String> =
        listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin") + input + listOf(
            "-vf",
            "scale=$width:$height:force_original_aspect_ratio=decrease,pad=$width:$height:(ow-iw)/2:(oh-ih)/2,fps=10",
            "-pix_fmt", "gray", "-f", "rawvideo", "-",
        )

    fun open(ffmpeg: String, input: List<String>): FrameStream {
        val process = ProcessBuilder(command(ffmpeg, input)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        return RawGrayStream(process, V4l2.WIDTH, V4l2.HEIGHT)
    }

    /** What `ffmpeg -list_devices true -f dshow -i dummy` prints (on its error stream). */
    fun listDshow(ffmpeg: String): String =
        runProcess(listOf(ffmpeg, "-hide_banner", "-nostdin", "-list_devices", "true", "-f", "dshow", "-i", "dummy"), 5)?.second.orEmpty()

    /** The video devices of a DirectShow listing: `[dshow @ …] "Integrated Camera" (video)` (older builds: under "DirectShow video devices"). */
    fun dshowDevices(listing: String): List<Webcam> {
        val out = mutableListOf<Webcam>()
        var section: String? = null
        for (line in listing.lines()) {
            when {
                line.contains("DirectShow video devices") -> section = "video"
                line.contains("DirectShow audio devices") -> section = "audio"
            }
            if (line.contains("Alternative name")) continue
            val name = QUOTED.find(line)?.groupValues?.get(1) ?: continue
            val kind = KIND.find(line)?.groupValues?.get(1) ?: section
            if (kind == "video" && out.none { it.id == name }) out += Webcam(name, name)
        }
        return out
    }

    private val QUOTED = Regex("\"([^\"]+)\"")
    private val KIND = Regex("\\((video|audio|none)\\)\\s*$")
}

/**
 * Runs [command] (output and errors together) and returns its exit code and output, or null when it could not start or did not
 * end within [timeoutSeconds] (then it is killed). The output is read on its own thread, so a program that never closes it cannot
 * hold the caller past the timeout.
 */
internal fun runProcess(command: List<String>, timeoutSeconds: Long): Pair<Int, String>? {
    val p = try {
        ProcessBuilder(command).redirectErrorStream(true).start()
    } catch (_: IOException) {
        return null
    }
    val out = ByteArrayOutputStream()
    val reader = thread(isDaemon = true, name = "process-output") { runCatching { p.inputStream.copyTo(out) } }
    if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        reader.join(500)
        return null
    }
    reader.join(1_000)
    return p.exitValue() to out.toString(Charsets.UTF_8)
}

/**
 * Raw gray frames from a process's output; closing it stops the process. [next] waits at most its timeout (it never blocks on a
 * camera that stopped sending), so its owner sees a cancellation between frames and closes it on its own thread.
 */
internal class RawGrayStream(private val process: Process, private val width: Int, private val height: Int) : FrameStream {
    private val input: InputStream = process.inputStream
    private val frame = ByteArray(width * height)
    private var filled = 0

    override fun next(timeoutMs: Int): GrayFrame? {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (filled < frame.size) {
            val ready = input.available()
            if (ready > 0) {
                val n = input.read(frame, filled, minOf(ready, frame.size - filled))
                if (n < 0) throw stopped()
                filled += n
                continue
            }
            if (!process.isAlive && input.available() == 0) throw stopped()
            if (System.nanoTime() >= deadline) return null
            Thread.sleep(POLL_MS)
        }
        filled = 0
        return GrayFrame(width, height, frame.copyOf())
    }

    private fun stopped() = IOException("the camera stopped (ffmpeg exit ${runCatching { process.exitValue() }.getOrNull()})")

    override fun close() {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        runCatching { input.close() }
    }

    private companion object {
        const val POLL_MS = 10L
    }
}

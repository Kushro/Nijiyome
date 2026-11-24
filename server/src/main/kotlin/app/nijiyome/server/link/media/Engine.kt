// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/engine.rs and media/job.rs (AGPL-3.0, same author); the process chain follows
// Enigmata-Temple media.rs `run_chain`.
package app.nijiyome.server.link.media

import app.nijiyome.model.AppLog
import app.nijiyome.server.link.SubtitleOutput
import app.nijiyome.server.link.SubtitleTrack
import app.nijiyome.server.link.Subtitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Collections

/** The first two LSFG stages; the third (the encoder that PUTs) is [EngineJob.args]. */
class LsfgStages(val decode: List<String>, val helper: File, val helperArgs: List<String>)

class EngineJob(
    /** The FFmpeg that PUTs the segments (the only process, or the last stage of the LSFG chain). */
    val args: List<String>,
    /** FFmpeg's working directory (holds the Anime4K `.hook`). */
    val workdir: File,
    val startSegment: Int,
    /** `http://127.0.0.1:<port>/sink/<token>/<generation>` */
    val sinkBase: String,
    val lsfg: LsfgStages? = null,
)

enum class EngineExit { SUCCESS, FAILED }

interface EngineProcess {
    suspend fun await(): EngineExit
    fun kill()
}

/** The media engine behind a session generation: real FFmpeg here, a fake that PUTs synthetic segments in tests. */
interface Engine {
    /** @throws IOException when it cannot start. */
    suspend fun spawn(job: EngineJob): EngineProcess

    /** Frame rate and container duration of the loopback input in one probe. */
    suspend fun sourceInfo(inputUrl: String): SourceInfo = SourceInfo()

    /** The subtitle tracks of the loopback input; null = cannot list them (no `ffprobe`) or the listing failed. */
    suspend fun subtitleTracks(inputUrl: String): List<SubtitleTrack>? = null

    /** One pass that writes every file of [outputs] from the loopback input. */
    suspend fun extractSubtitles(inputUrl: String, outputs: List<SubtitleOutput>): Unit = throw IOException("unsupported")
}

/** No FFmpeg installed: every run fails at once (the session reports `engine_unavailable`). */
object NoEngine : Engine {
    override suspend fun spawn(job: EngineJob): EngineProcess = throw IOException("FFmpeg is not installed")
}

/**
 * Processes spawned for Link. A JVM has no `PR_SET_PDEATHSIG` / Job Object: the processes are killed explicitly (session close,
 * server stop) and by a shutdown hook; after a crash they end on their own once their loopback input and sink are gone.
 */
object MediaProcesses {
    private val live: MutableSet<Process> = Collections.synchronizedSet(mutableSetOf())

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ killAll() }, "nijiyome-link-media-cleanup"))
    }

    fun track(p: Process): Process {
        live += p
        p.onExit().thenRun { live -= p }
        return p
    }

    fun kill(p: Process) {
        p.descendants().forEach { it.destroyForcibly() }
        p.destroyForcibly()
    }

    fun killAll() {
        synchronized(live) { live.toList() }.forEach(::kill)
    }

    fun count(): Int = live.size
}

class FfmpegEngine(val program: File, val ffprobe: File? = siblingFfprobe(program)) : Engine {
    private fun builder(program: File, args: List<String>, workdir: File, captureStderr: Boolean = false): ProcessBuilder {
        val pb = ProcessBuilder(listOf(program.path) + args).directory(workdir)
        pb.environment().putAll(Lsfg.VULKAN_EXTRA_ENV)
        // Diagnostics: with NIJIYOME_LINK_MEDIA_LOG=<dir> each media process appends its stderr to <dir>/<program>.log
        val logDir = System.getenv(MEDIA_LOG_ENV)?.let(::File)
        if (logDir != null && (logDir.isDirectory || logDir.mkdirs())) {
            pb.redirectError(ProcessBuilder.Redirect.appendTo(File(logDir, "${program.nameWithoutExtension}.log")))
        } else if (!captureStderr) {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        }
        return pb
    }

    override suspend fun spawn(job: EngineJob): EngineProcess = withContext(Dispatchers.IO) {
        val logDir = System.getenv(MEDIA_LOG_ENV)?.let(::File)
        val capture = logDir == null
        val lsfg = job.lsfg
        if (lsfg == null) {
            val pb = builder(program, job.args, job.workdir, captureStderr = capture)
                .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            Chain(listOf(MediaProcesses.track(pb.start())), capture)
        } else {
            // FFmpeg A | temple-lsfg | FFmpeg B: the JDK connects the pipes; a failed start leaves nothing running
            val a = builder(program, lsfg.decode, job.workdir, captureStderr = capture).redirectInput(ProcessBuilder.Redirect.from(nullFile()))
            val h = builder(lsfg.helper, lsfg.helperArgs, job.workdir, captureStderr = capture)
            val e = builder(program, job.args, job.workdir, captureStderr = capture).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            Chain(ProcessBuilder.startPipeline(listOf(a, h, e)).map(MediaProcesses::track), capture)
        }
    }

    override suspend fun sourceInfo(inputUrl: String): SourceInfo {
        val probe = ffprobe ?: return SourceInfo()
        val out = runTool(probe, Probe.ffprobeArgs(inputUrl), PROBE_TIMEOUT_MS) ?: return SourceInfo()
        // BUG-024: FFmpeg would fail the same way (same binary, same input URL and protocol whitelist)
        if (!out.first) return SourceInfo(unreadable = true)
        if (out.second.length > MAX_PROBE_OUTPUT) return SourceInfo()
        return Probe.parse(out.second)
    }

    override suspend fun subtitleTracks(inputUrl: String): List<SubtitleTrack>? {
        val probe = ffprobe ?: return null
        val (ok, text) = runTool(probe, Subtitles.ffprobeArgs(inputUrl), Subtitles.PROBE_TIMEOUT_MS) ?: return null
        if (!ok || text.length > MAX_PROBE_OUTPUT) return null
        return Subtitles.parseFfprobe(text)
    }

    override suspend fun extractSubtitles(inputUrl: String, outputs: List<SubtitleOutput>) {
        val dir = outputs.firstOrNull()?.path?.parentFile ?: throw IOException("no outputs")
        val p = withContext(Dispatchers.IO) {
            MediaProcesses.track(
                builder(program, Subtitles.extractArgs(inputUrl, outputs), dir)
                    .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start(),
            )
        }
        try {
            val code = p.onExit().await().exitValue()
            if (code != 0) throw IOException("subtitle extraction failed")
        } finally {
            if (p.isAlive) MediaProcesses.kill(p)
        }
    }

    /** `(exit ok, stdout)` of a short tool run, null when it could not run or timed out. */
    private suspend fun runTool(tool: File, args: List<String>, timeoutMs: Long): Pair<Boolean, String>? {
        val p = withContext(Dispatchers.IO) {
            runCatching {
                ProcessBuilder(listOf(tool.path) + args)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                    .start()
            }.getOrNull()
        } ?: return null
        MediaProcesses.track(p)
        return try {
            withTimeoutOrNull(timeoutMs) {
                val text = withContext(Dispatchers.IO) { p.inputStream.use { it.readNBytes(MAX_PROBE_OUTPUT + 1).decodeToString() } }
                val code = p.onExit().await().exitValue()
                (code == 0) to text
            }
        } finally {
            if (p.isAlive) MediaProcesses.kill(p)
        }
    }

    /**
     * A running process chain (one process, or the three LSFG stages): success only when every stage exits with 0; the first
     * failure kills the rest and all are awaited. Keeps a small sanitized tail of stderr to log on unexpected failures.
     */
    private class Chain(private val stages: List<Process>, captureStderr: Boolean) : EngineProcess {
        @Volatile private var killedExternally = false
        private val stderrTail = ArrayDeque<String>()
        private val drainThreads: List<Thread> = if (captureStderr) {
            stages.mapIndexed { idx, p ->
                Thread({
                    runCatching {
                        p.errorStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val cleaned = sanitizeMediaStderr(line)
                                if (cleaned.isEmpty()) continue
                                synchronized(stderrTail) {
                                    if (stderrTail.size >= MAX_STDERR_LINES) stderrTail.removeFirst()
                                    stderrTail.addLast(if (stages.size > 1) "s$idx: $cleaned" else cleaned)
                                }
                            }
                        }
                    }
                }, "link-media-stderr-$idx").apply { isDaemon = true; start() }
            }
        } else {
            emptyList()
        }

        init {
            // whichever stage fails first takes the others down (they usually die of the broken pipe anyway)
            stages.forEach { p -> p.onExit().thenAccept { if (it.exitValue() != 0) killInternal() } }
        }

        override suspend fun await(): EngineExit {
            var ok = true
            val codes = IntArray(stages.size)
            for ((i, p) in stages.withIndex()) {
                val code = p.onExit().await().exitValue()
                codes[i] = code
                if (code != 0) {
                    ok = false
                    killInternal()
                }
            }
            if (!ok && !killedExternally) {
                drainThreads.forEach { runCatching { it.join(150) } }
                val tail = synchronized(stderrTail) { stderrTail.joinToString(" | ").take(MAX_STDERR_CHARS) }
                val suffix = if (tail.isNotEmpty()) ": $tail" else ""
                AppLog.w("link", "media process failed (exit=${codes.joinToString(",")})$suffix")
            }
            return if (ok) EngineExit.SUCCESS else EngineExit.FAILED
        }

        override fun kill() {
            killedExternally = true
            killInternal()
        }

        private fun killInternal() {
            stages.forEach(MediaProcesses::kill)
        }
    }

    companion object {
        const val MEDIA_LOG_ENV = "NIJIYOME_LINK_MEDIA_LOG"
        private const val PROBE_TIMEOUT_MS = 20_000L
        private const val MAX_PROBE_OUTPUT = 256 * 1024
        private const val MAX_STDERR_LINES = 8
        private const val MAX_STDERR_CHARS = 900
        private val URL_RE = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val UNIX_PATH_RE = Regex("""(?<![\w])/(?:[^\s:'",;)]+/)+[^\s:'",;)]*""")
        private val WIN_PATH_RE = Regex("""[A-Za-z]:\\[^\s:'",;)]+""")

        /** Strips URLs, file paths and control characters from a media stderr line before writing it to AppLog. */
        internal fun sanitizeMediaStderr(line: String): String {
            val trimmed = line.trim().filterNot { it.isISOControl() }
            if (trimmed.isEmpty()) return ""
            return trimmed
                .replace(URL_RE, "<url>")
                .replace(WIN_PATH_RE, "<path>")
                .replace(UNIX_PATH_RE, "<path>")
                .take(180)
        }

        /** `ffprobe[.exe]` in FFmpeg's folder. */
        fun siblingFfprobe(ffmpeg: File): File? =
            File(ffmpeg.parentFile ?: File("."), if (Platform.current() == Platform.WINDOWS) "ffprobe.exe" else "ffprobe").takeIf { it.isFile }

        private fun nullFile(): File = File(if (Platform.current() == Platform.WINDOWS) "NUL" else "/dev/null")
    }
}

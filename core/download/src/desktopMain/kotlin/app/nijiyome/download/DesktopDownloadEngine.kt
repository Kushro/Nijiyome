// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import app.nijiyome.model.DownloadProfile
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The desktop download engine (no Media3; port of Tatakai Next's `tk-core/download/engine.rs` over the Android rules): OkHttp with
 * the extension headers of each download on every request (playlists, segments, keys, files).
 * - HLS: the media playlist's segments (AES-128 decrypted, fMP4 init segment included) are fetched [SEGMENT_PARALLELISM] at a
 *   time into `<base>.parts/NNNNN.seg`; a segment file only appears once complete, so a paused or interrupted download goes on
 *   from the missing ones. At the end they are concatenated in playlist order into ONE playable file (`.ts`, or `.mp4` for fMP4)
 *   and the parts go; a `.ts` is then remuxed to `.mp4` without re-encoding when [ffmpeg] names one (Tatakai Next does the same).
 * - Direct files: `<target>.part`, resumed with `Range` from its length (a server that ignores it starts over). A body that turns
 *   out to be an HLS playlist (by its `Content-Type` or its `#EXTM3U` start) is downloaded as HLS instead.
 * - Speed: every byte read goes through one [SpeedLimiter] for the whole engine ([speedLimitKib], 0 = unlimited).
 * Cancelling the coroutine stops the download and keeps what was written.
 */
internal class DesktopDownloadEngine(
    private val http: OkHttpClient,
    /** Total download speed of the engine in KiB/s, read on every chunk (Settings › Descargas); 0 = unlimited. */
    speedLimitKib: () -> Int = { 0 },
    /** The ffmpeg executable that remuxes a finished `.ts`; null = keep the `.ts` (mpv plays both). */
    private val ffmpeg: () -> String? = { null },
    /** Largest playlist, key or HLS segment held in memory (tests lower it). */
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
) {
    private val limiter = SpeedLimiter(speedLimitKib)

    /** [http] that does not follow a redirect between https and http (used for every https:// request). */
    private val noDowngrade: OkHttpClient by lazy { http.newBuilder().followSslRedirects(false).build() }

    /** 1.1 review #1: the client of one request: https:// never downgrades through a redirect. */
    internal fun clientFor(url: okhttp3.HttpUrl): OkHttpClient = if (url.isHttps) noDowngrade else http

    data class Job(val url: String, val headers: Map<String, String>, val container: Container, val base: File)

    /** [fraction] 0..1 (null = unknown); [bytes] written so far. */
    data class Progress(val bytes: Long, val total: Long?, val fraction: Double?)

    class HttpException(val code: Int) : IOException("http $code")

    /** The media playlist to download for [url]: a multivariant master is fetched and its variant picked by [profile] (as on Android). */
    suspend fun resolveHls(url: String, headers: Map<String, String>, profile: DownloadProfile): String = withContext(Dispatchers.IO) {
        when (val parsed = HlsPlaylist.parse(url, fetchText(url, headers))) {
            is HlsPlaylist.Parsed.Media -> url
            is HlsPlaylist.Parsed.Master -> pickVariant(parsed, profile).url
        }
    }

    /**
     * Runs [job] to the end and returns the playable file. Throws on failure; resumable after cancellation. [onHls] is called when a
     * direct file turned out to be an HLS playlist and is being downloaded as HLS (the caller marks the row HLS).
     */
    suspend fun download(job: Job, onHls: () -> Unit = {}, onProgress: (Progress) -> Unit): File = withContext(Dispatchers.IO) {
        if (job.container == Container.HLS) {
            downloadHls(job, onProgress)
        } else {
            try {
                downloadFile(job, onProgress)
            } catch (_: PlaylistBody) {
                discardPartials(job.base)
                onHls()
                downloadHls(job.copy(container = Container.HLS), onProgress)
            }
        }
    }

    /** The body of a direct-file request is an HLS playlist. */
    private class PlaylistBody : IOException("the file is an HLS playlist")

    /** Deletes the partial data of a download (its parts folder and `.part` files). */
    fun discardPartials(base: File) {
        DownloadNames.partsDir(base).deleteRecursively()
        for (ext in listOf("ts", "mp4", "mkv", "webm")) DownloadNames.partFile(DownloadNames.withExtension(base, ext)).delete()
    }

    // ------------------------------------------------------------------ HLS

    private fun pickVariant(master: HlsPlaylist.Parsed.Master, profile: DownloadProfile): HlsPlaylist.Variant {
        val variants = master.variants.mapIndexed { i, v -> DownloadLogic.Variant(i, v.height, v.bandwidth) }
        val pick = DownloadLogic.pickVariant(variants, profile) ?: throw IOException("hls: no variants")
        return master.variants[pick.index]
    }

    private suspend fun downloadHls(job: Job, onProgress: (Progress) -> Unit): File {
        var playlistUrl = job.url
        var parsed = HlsPlaylist.parse(playlistUrl, fetchText(playlistUrl, job.headers))
        if (parsed is HlsPlaylist.Parsed.Master) {
            playlistUrl = pickVariant(parsed, DownloadProfile.BEST).url
            parsed = HlsPlaylist.parse(playlistUrl, fetchText(playlistUrl, job.headers))
        }
        val segments = (parsed as? HlsPlaylist.Parsed.Media)?.segments ?: throw IOException("hls: not a media playlist")
        // The pieces in file order: each segment, preceded by its init segment whenever that changes (fMP4).
        val pieces = ArrayList<HlsPlaylist.Segment>()
        var lastInit: HlsPlaylist.Segment? = null
        for (s in segments) {
            val init = s.init
            if (init != null && (init.url != lastInit?.url || init.range != lastInit?.range)) {
                pieces += init
                lastInit = init
            }
            pieces += s
        }
        val parts = DownloadNames.partsDir(job.base)
        parts.mkdirs()
        if (!parts.isDirectory) throw IOException("cannot create ${parts.name}")
        val keys = ConcurrentHashMap<String, ByteArray>()
        val keyLock = Mutex()
        // SEGMENT_PARALLELISM workers take the next missing piece in playlist order. After a failure no new piece is taken, but
        // the ones in flight finish (they are the resume checkpoint); then the first failure is thrown.
        val next = AtomicInteger(0)
        val done = AtomicInteger(0)
        val bytes = AtomicLong(0)
        val failure = AtomicReference<Throwable?>(null)
        val progressLock = Any()
        var reported = 0
        fun report() = synchronized(progressLock) {
            val n = done.get()
            if (n > reported) {
                reported = n
                onProgress(Progress(bytes.get(), null, n.toDouble() / pieces.size))
            }
        }
        coroutineScope {
            repeat(minOf(SEGMENT_PARALLELISM, pieces.size)) {
                launch {
                    while (failure.get() == null) {
                        val i = next.getAndIncrement()
                        if (i >= pieces.size) break
                        val piece = pieces[i]
                        val file = DownloadNames.segmentFile(parts, i)
                        try {
                            if (!file.isFile) {
                                var data = fetchBytes(piece.url, job.headers, piece.range)
                                val key = piece.key
                                if (key != null) {
                                    val k = keys[key.url] ?: keyLock.withLock { keys[key.url] ?: fetchBytes(key.url, job.headers, null).also { keys[key.url] = it } }
                                    data = HlsCrypto.decrypt(data, k, HlsCrypto.iv(key.iv, piece.sequence))
                                }
                                val tmp = File(parts, file.name + ".tmp")
                                tmp.writeBytes(data)
                                if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure.compareAndSet(null, e)
                            break
                        }
                        bytes.addAndGet(file.length())
                        done.incrementAndGet()
                        report()
                    }
                }
            }
        }
        failure.get()?.let { throw it }
        val ext = if (pieces.any { it.sequence < 0 }) "mp4" else DownloadLogic.extensionOf(Container.HLS)
        val target = DownloadNames.withExtension(job.base, ext)
        val part = DownloadNames.partFile(target)
        part.outputStream().buffered(BUFFER).use { out ->
            for (i in pieces.indices) DownloadNames.segmentFile(parts, i).inputStream().use { it.copyTo(out, BUFFER) }
        }
        // The segments stay until the playable file is final: a pause or cancel during the remux resumes without downloading again.
        if (ext == "ts") remuxToMp4(part, DownloadNames.withExtension(job.base, "mp4"))?.let { mp4 ->
            part.delete()
            parts.deleteRecursively()
            return mp4
        }
        replace(part, target)
        parts.deleteRecursively()
        return target
    }

    /**
     * `ffmpeg -c copy` of the concatenated `.ts` [ts] into [mp4] (Tatakai Next's `remux`). Null when there is no ffmpeg or it fails
     * (the caller keeps the `.ts`). A failed or cancelled remux leaves no [mp4] behind; cancelling still throws.
     */
    private suspend fun remuxToMp4(ts: File, mp4: File): File? {
        val exe = ffmpeg() ?: return null
        var ok = false
        try {
            val process = try {
                ProcessBuilder(remuxCommand(exe, ts, mp4))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(File(if (isWindows()) "NUL" else "/dev/null")))
                    .start()
            } catch (_: IOException) {
                return null
            }
            try {
                while (process.isAlive) {
                    coroutineContext.ensureActive()
                    process.waitFor(200, TimeUnit.MILLISECONDS)
                }
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    // let it go before its output is deleted (Windows keeps open files)
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
            ok = process.exitValue() == 0 && mp4.isFile && mp4.length() > 0
            return if (ok) mp4 else null
        } finally {
            if (!ok) mp4.delete()
        }
    }

    // ------------------------------------------------------------------ direct files

    private suspend fun downloadFile(job: Job, onProgress: (Progress) -> Unit): File {
        val target = DownloadNames.withExtension(job.base, extensionOfUrl(job.url))
        val part = DownloadNames.partFile(target)
        target.parentFile?.mkdirs()
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            try {
                transfer(job, part, onProgress)
                break
            } catch (e: HttpException) {
                if (!retryable(e.code) || ++attempt >= RETRIES) throw e
            } catch (e: IOException) {
                if (++attempt >= RETRIES) throw e
            }
            delay(RETRY_DELAY_MS * attempt)
        }
        replace(part, target)
        return target
    }

    private suspend fun transfer(job: Job, part: File, onProgress: (Progress) -> Unit) {
        val have = if (part.isFile) part.length() else 0L
        val request = request(job.url, job.headers, if (have > 0) "bytes=$have-" else null)
        withCall(request) { resp ->
            if (resp.code == 416 && have > 0) {
                // Nothing left to fetch only when the server says the file is exactly what the part holds (`bytes */<have>`);
                // otherwise the part does not match the file any more (it changed, or the part is damaged): start over.
                if (ContentRange.unsatisfiedSize(resp.header("Content-Range")) == have) return
                part.delete()
                throw IOException("the partial file does not match the remote file (416)")
            }
            if (!resp.isSuccessful) throw HttpException(resp.code)
            if (resp.code == 206 && have > 0) {
                val start = ContentRange.start(resp.header("Content-Range"))
                if (start != have) {
                    // A range that does not continue the part would corrupt it: start over from the beginning.
                    part.delete()
                    throw IOException("the server answered range start $start for bytes=$have-")
                }
            }
            val resumed = resp.code == 206 && have > 0
            if (!resumed && isPlaylist(resp.header("Content-Type"), runCatching { resp.peekBody(PLAYLIST_SNIFF.toLong()).bytes() }.getOrNull())) {
                throw PlaylistBody()
            }
            val total = totalOf(resp, if (resumed) have else 0L)
            RandomAccessFile(part, "rw").use { raf ->
                if (resumed) raf.seek(have) else raf.setLength(0)
                var written = if (resumed) have else 0L
                var lastReport = 0L
                val buf = ByteArray(BUFFER)
                resp.body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        limiter.take(n.toLong())
                        raf.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= REPORT_MS) {
                            lastReport = now
                            onProgress(Progress(written, total, total?.let { written.toDouble() / it }))
                        }
                    }
                }
                if (total != null && written < total) throw IOException("short body: $written of $total bytes")
                onProgress(Progress(written, total ?: written, 1.0))
            }
        }
    }

    /**
     * Runs [request] and hands the response to [block], cancelling the HTTP call as soon as the coroutine is cancelled (pause,
     * cancel): a read blocked on a slow server ends at once instead of when data or the timeout arrive.
     */
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private suspend inline fun <T> withCall(request: Request, block: (Response) -> T): T {
        // 1.1 review #1: an https:// download never follows a redirect down to http:// (the 3xx is answered as an error)
        val call = clientFor(request.url).newCall(request)
        val handle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
        try {
            return call.execute().use(block)
        } finally {
            handle?.dispose()
        }
    }

    private fun totalOf(resp: Response, offset: Long): Long? {
        resp.header("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()?.let { return it }
        val len = resp.body.contentLength()
        return if (len >= 0) len + offset else null
    }

    // ------------------------------------------------------------------ HTTP

    private fun request(url: String, headers: Map<String, String>, range: String?): Request =
        Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (range != null) header("Range", range)
        }.build()

    private suspend fun fetchText(url: String, headers: Map<String, String>): String = String(fetchBytes(url, headers, null), Charsets.UTF_8)

    private suspend fun fetchBytes(url: String, headers: Map<String, String>, range: Pair<Long, Long>?): ByteArray {
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            try {
                withCall(request(url, headers, range?.let(HlsPlaylist::rangeHeader))) { resp ->
                    if (!resp.isSuccessful) throw HttpException(resp.code)
                    val body = readLimited(resp)
                    // A server that ignores the range sends the whole resource: cut the range out of it.
                    if (range != null && resp.code == 200 && body.size.toLong() >= range.second + range.first) {
                        return body.copyOfRange(range.second.toInt(), (range.second + range.first).toInt())
                    }
                    return body
                }
            } catch (e: TooLarge) {
                throw e
            } catch (e: HttpException) {
                if (!retryable(e.code) || ++attempt >= RETRIES) throw e
            } catch (e: IOException) {
                if (++attempt >= RETRIES) throw e
            }
            delay(RETRY_DELAY_MS * attempt)
        }
    }

    /** A playlist, key or segment over [maxBodyBytes] (announced or read): not retried. */
    class TooLarge(limit: Long) : IOException("the answer is larger than $limit bytes")

    /**
     * The whole body, read through the [limiter], at most [maxBodyBytes]. The buffer grows with what actually arrives: the remote
     * `Content-Length` only rejects early and never sizes the allocation past [PREALLOCATE_MAX].
     */
    private suspend fun readLimited(resp: Response): ByteArray {
        val announced = resp.body.contentLength()
        if (announced > maxBodyBytes) throw TooLarge(maxBodyBytes)
        val out = java.io.ByteArrayOutputStream(if (announced in 0..PREALLOCATE_MAX) announced.toInt() else BUFFER)
        val buf = ByteArray(BUFFER)
        var read = 0L
        resp.body.byteStream().use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                read += n
                if (read > maxBodyBytes) throw TooLarge(maxBodyBytes)
                limiter.take(n.toLong())
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun retryable(code: Int) = code == 408 || code == 429 || code >= 500

    private fun replace(part: File, target: File) {
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("cannot rename ${part.name}")
    }

    companion object {
        /** HLS segments fetched at the same time inside one download (Tatakai Next's `SEGMENT_PARALLELISM`). */
        const val SEGMENT_PARALLELISM = 4
        private const val PLAYLIST_SNIFF = 16
        private const val BUFFER = 64 * 1024
        private const val RETRIES = 4
        private const val RETRY_DELAY_MS = 1_000L
        private const val REPORT_MS = 500L

        /** Far above a real HLS segment (a few MiB) or playlist, far below what would exhaust the heap. */
        const val MAX_BODY_BYTES = 256L * 1024 * 1024
        private const val PREALLOCATE_MAX = 1L * 1024 * 1024

        /** A direct-file answer that is really an HLS playlist: an `mpegurl` content type, or a body starting with `#EXTM3U`. */
        fun isPlaylist(contentType: String?, head: ByteArray?): Boolean {
            if (contentType?.lowercase(Locale.ROOT)?.contains("mpegurl") == true) return true
            val text = head?.let { String(it, Charsets.UTF_8) }?.removePrefix("\uFEFF")?.trimStart() ?: return false
            return text.startsWith("#EXTM3U")
        }

        /** The remux command: every stream copied as is (no re-encoding), ADTS AAC fixed for MP4, the index at the front. */
        fun remuxCommand(ffmpeg: String, ts: File, mp4: File): List<String> = listOf(
            ffmpeg, "-nostdin", "-loglevel", "error", "-y", "-f", "mpegts", "-i", ts.path,
            "-map", "0", "-c", "copy", "-bsf:a", "aac_adtstoasc", "-movflags", "+faststart", mp4.path,
        )

        /** An executable [name] found on the PATH (`.exe` on Windows); null = none. */
        fun onPath(name: String, path: String? = System.getenv("PATH")): String? {
            val names = if (isWindows()) listOf("$name.exe", name) else listOf(name)
            return path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.firstNotNullOfOrNull { dir ->
                names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
            }
        }

        private fun isWindows() = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).startsWith("windows")

        /** Extension of the downloaded file: the URL's when it is a video one, else `mp4` (as Android names it). */
        fun extensionOfUrl(url: String): String {
            val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
            val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
            return if (ext in setOf("mp4", "mkv", "webm", "m4v", "mov")) ext else DownloadLogic.extensionOf(Container.MP4)
        }
    }
}

/** `Content-Range` of a ranged answer (RFC 9110 §14.4), pure for tests. */
internal object ContentRange {
    /** `bytes <start>-<end>/<size>` → start; null when absent or malformed. */
    fun start(header: String?): Long? {
        val v = header?.trim()?.takeIf { it.startsWith("bytes ", ignoreCase = true) }?.substring(6)?.trim() ?: return null
        if (v.startsWith("*")) return null
        return v.substringBefore('-').trim().toLongOrNull()
    }

    /** The size of a 416 answer (`bytes *` then `/<size>`); null when absent or malformed. */
    fun unsatisfiedSize(header: String?): Long? {
        val v = header?.trim()?.takeIf { it.startsWith("bytes ", ignoreCase = true) }?.substring(6)?.trim() ?: return null
        if (!v.startsWith("*/")) return null
        return v.removePrefix("*/").trim().toLongOrNull()
    }
}

/**
 * One token bucket for the whole engine (Tatakai Next's `Limiter`): every chunk read waits until the bytes read since the window
 * started fit [kib] KiB/s. The window restarts when the limit changes (the bytes read at the old rate are not owed at the new one)
 * and after [IDLE_RESET_NANOS] without reads (counted from the end of the last reservation, so a pause does not bank a burst while
 * a continuous download keeps its window). 0 = unlimited.
 */
internal class SpeedLimiter(private val kib: () -> Int, private val nowNanos: () -> Long = System::nanoTime) {
    private var windowStart = 0L
    private var windowBytes = 0L
    private var windowLimit = 0
    private var lastEnd = 0L

    /** Milliseconds to wait before [bytes] more may be read (0 = now). Pure over [nowNanos]. */
    @Synchronized
    fun reserve(bytes: Long): Long {
        val limit = kib()
        if (limit <= 0) {
            windowBytes = 0
            return 0
        }
        val now = nowNanos()
        if (windowBytes == 0L || limit != windowLimit || now - lastEnd > IDLE_RESET_NANOS) {
            windowStart = now
            windowBytes = 0
            windowLimit = limit
        }
        windowBytes += bytes
        val dueNanos = windowBytes * 1_000_000_000L / (limit * 1024L)
        val waitNanos = (dueNanos - (now - windowStart)).coerceAtLeast(0)
        lastEnd = now + waitNanos
        return if (waitNanos == 0L) 0 else (waitNanos + 999_999) / 1_000_000
    }

    suspend fun take(bytes: Long) {
        val wait = reserve(bytes)
        if (wait > 0) delay(wait)
    }

    private companion object {
        const val IDLE_RESET_NANOS = 2_000_000_000L
    }
}

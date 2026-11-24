// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/subtitles.rs (AGPL-3.0, same author); the approach follows Enigmata-Temple's
// GET /v1/sessions/{id}/subtitles/{n}.vtt.
package app.nijiyome.server.link

import app.nijiyome.server.link.media.Args
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * 0.4.0: the source's own subtitles on the phone while the PC processes the video. `GET /v1/sessions/{id}` lists them
 * (`subtitles`, `subtitles_state`) and `GET /v1/sessions/{id}/subtitles/{n}.ass|.vtt` serves each text track. One FFmpeg pass
 * extracts every text track the first time the phone asks for any, kept in RAM for the source ([SubtitleStore]), shared by the
 * sessions of the same source (a profile change does not read the source again). The files keep the source's own timeline.
 */
object Subtitles {
    const val MAX_LISTED = 64
    const val MAX_EXTRACTED = 24
    const val MAX_FILE_BYTES = 8L * 1024 * 1024
    const val MAX_STORE_BYTES = 48L * 1024 * 1024
    const val PROBE_TIMEOUT_MS = 20_000L
    const val EXTRACT_TIMEOUT_MS = 15L * 60 * 1000
    /** How long a file request waits for the pass before `503` + `Retry-After` (below the phones' 20 s read timeout). */
    const val REQUEST_WAIT_MS = 8_000L
    const val MAX_TITLE_CHARS = 120
    const val MAX_LANGUAGE_BYTES = 16

    private val TEXT_CODECS = setOf(
        "ass", "ssa", "subrip", "srt", "webvtt", "mov_text", "text", "microdvd", "subviewer", "subviewer1", "sami", "realtext", "mpl2", "vplayer", "pjs",
    )
    private val ASS_CODECS = setOf("ass", "ssa")

    /** `"<n>.ass"` / `"<n>.vtt"` -> `(n, format)`; anything else is null (404). */
    fun parseFileName(file: String): Pair<Int, SubFormat>? {
        val dot = file.indexOf('.')
        if (dot < 0) return null
        val n = file.substring(0, dot)
        val ext = file.substring(dot + 1)
        if (n.isEmpty() || n.length > 3 || !n.all { it in '0'..'9' }) return null
        val format = when (ext) {
            "ass" -> SubFormat.ASS
            "vtt" -> SubFormat.VTT
            else -> return null
        }
        return n.toInt() to format
    }

    fun ffprobeArgs(inputUrl: String): List<String> = listOf(
        "-v", "error", "-protocol_whitelist", Args.PROTOCOL_WHITELIST, "-select_streams", "s",
        "-show_entries", "stream=codec_name:stream_tags=language,title:stream_disposition=default,forced",
        "-print_format", "json", "-i", inputUrl,
    )

    /** The subtitle tracks of `ffprobe -select_streams s -print_format json`, in order; null when it is not that. */
    fun parseFfprobe(stdout: String): List<SubtitleTrack>? {
        val root = runCatching { Json.parseToJsonElement(stdout) as? JsonObject }.getOrNull() ?: return null
        val streams = (root["streams"] as? JsonArray) ?: JsonArray(emptyList())
        var extracted = 0
        return streams.take(MAX_LISTED).mapIndexed { index, el ->
            val s = el as? JsonObject ?: JsonObject(emptyMap())
            val codec = (s["codec_name"]?.jsonPrimitive?.contentOrNull ?: "").filter { it.isLetterOrDigit() && it.code < 128 || it == '_' }.take(32)
            val text = codec in TEXT_CODECS
            val formats = mutableListOf<String>()
            if (text && extracted < MAX_EXTRACTED) {
                extracted++
                if (codec in ASS_CODECS) formats += SubFormat.ASS.ext
                formats += SubFormat.VTT.ext
            }
            val disposition = s["disposition"] as? JsonObject
            val tags = s["tags"] as? JsonObject
            SubtitleTrack(
                index = index,
                language = cleanLanguage(tags?.get("language")?.jsonPrimitive?.contentOrNull),
                title = cleanTitle(tags?.get("title")?.jsonPrimitive?.contentOrNull),
                codec = codec.ifEmpty { "unknown" },
                default = (disposition?.get("default")?.jsonPrimitive?.intOrNull ?: 0) != 0,
                forced = (disposition?.get("forced")?.jsonPrimitive?.intOrNull ?: 0) != 0,
                text = text,
                formats = formats,
            )
        }
    }

    private fun cleanLanguage(raw: String?): String? {
        val l = raw?.trim()?.lowercase() ?: return null
        val ok = l.isNotEmpty() && l.toByteArray().size <= MAX_LANGUAGE_BYTES && l.all { (it.isLetterOrDigit() && it.code < 128) || it == '-' || it == '_' }
        return l.takeIf { ok && it != "und" }
    }

    private fun cleanTitle(raw: String?): String? {
        val t = raw?.filterNot { it.isISOControl() }?.trim() ?: return null
        return t.codePointsTake(MAX_TITLE_CHARS).takeIf { it.isNotEmpty() }
    }

    private fun String.codePointsTake(n: Int): String {
        val sb = StringBuilder()
        var count = 0
        var i = 0
        while (i < length && count < n) {
            val cp = codePointAt(i)
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
            count++
        }
        return sb.toString()
    }

    /** Every file to extract for [tracks] (the formats each one offers), under [dir]. */
    fun planOutputs(tracks: List<SubtitleTrack>, dir: File): List<SubtitleOutput> = tracks.flatMap { t ->
        listOf(SubFormat.ASS, SubFormat.VTT).filter { t.offers(it) }.map { SubtitleOutput(t.index, it, File(dir, "${t.index}.${it.ext}")) }
    }

    /** The single pass: one input (the loopback fetcher) and, per output, its own `-map 0:s:<n>`, encoder and `-fs`. No `-ss`. */
    fun extractArgs(inputUrl: String, outputs: List<SubtitleOutput>): List<String> = buildList {
        addAll(listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-protocol_whitelist", Args.PROTOCOL_WHITELIST, "-i", inputUrl))
        for (o in outputs) {
            addAll(listOf("-map", "0:s:${o.track}", "-c:s", o.format.encoder, "-fs", MAX_FILE_BYTES.toString(), "-f", o.format.encoder, o.path.path))
        }
    }

    /** What a finished pass wrote, within the limits; a missing file (no event for that track) is served empty. */
    fun collectOutputs(outputs: List<SubtitleOutput>): Map<Pair<Int, SubFormat>, Extracted> {
        var total = 0L
        val files = mutableMapOf<Pair<Int, SubFormat>, Extracted>()
        for (o in outputs) {
            val len = if (o.path.isFile) o.path.length() else 0L
            files[o.track to o.format] = if (len >= MAX_FILE_BYTES || total + len > MAX_STORE_BYTES) {
                Extracted.TooLarge
            } else {
                val body = runCatching { o.path.readBytes() }.getOrDefault(ByteArray(0))
                total += body.size
                Extracted.File(body)
            }
        }
        return files
    }
}

enum class SubFormat(val ext: String, val contentType: String, val encoder: String) {
    /** ASS / SSA sources only (styles kept; libmpv renders them with libass). */
    ASS("ass", "text/x-ssa; charset=utf-8", "ass"),
    /** Any text track (Media3 renders WebVTT). */
    VTT("vtt", "text/vtt; charset=utf-8", "webvtt"),
}

/** One subtitle track as `GET /v1/sessions/{id}` lists it; [index] is the `n` of the file URLs (`-map 0:s:<index>`). */
data class SubtitleTrack(
    val index: Int,
    val language: String?,
    val title: String?,
    val codec: String,
    val default: Boolean,
    val forced: Boolean,
    val text: Boolean,
    val formats: List<String>,
) {
    fun offers(format: SubFormat): Boolean = format.ext in formats

    fun toJson(): JsonObject = buildJsonObject {
        put("index", index)
        language?.let { put("language", it) }
        title?.let { put("title", it) }
        put("codec", codec)
        put("default", default)
        put("forced", forced)
        put("text", text)
        put("formats", buildJsonArray { formats.forEach { add(JsonPrimitive(it)) } })
    }
}

data class SubtitleOutput(val track: Int, val format: SubFormat, val path: File)

sealed interface Extracted {
    class File(val bytes: ByteArray) : Extracted
    /** Over [Subtitles.MAX_FILE_BYTES] or past [Subtitles.MAX_STORE_BYTES] (`503 source_unavailable`). */
    data object TooLarge : Extracted
}

/** What a request for one file finds now. */
sealed interface Lookup {
    /** The listing has not finished (wait, or `409 session_not_ready`). */
    data object Listing : Lookup
    /** No such text track / format, or no listing at all (`404`). */
    data object NotFound : Lookup
    /** The pass runs (or must be started: [start]) (wait, or `503` + `Retry-After`). */
    data class Extracting(val start: Boolean) : Lookup
    class File(val bytes: ByteArray) : Lookup
    /** Too large, or the pass failed (`503 source_unavailable`). */
    data object Unavailable : Lookup
}

/**
 * The subtitles of one source: its track list and, once the phone asked for a file, the extracted files. Jobs are owned by the
 * session that started them: when it closes ([abandon]) they stop and the next session starts them again with its own input.
 */
class SubtitleStore {
    private sealed interface Probe {
        data object Idle : Probe
        class Running(val owner: String, val job: CancelToken) : Probe
        class Done(val tracks: List<SubtitleTrack>) : Probe
        data object Failed : Probe
    }

    private sealed interface Extraction {
        data object Idle : Extraction
        class Running(val owner: String, val job: CancelToken) : Extraction
        class Ready(val files: Map<Pair<Int, SubFormat>, Extracted>) : Extraction
        data object Failed : Extraction
    }

    private var probe: Probe = Probe.Idle
    private var extraction: Extraction = Extraction.Idle

    /** Bumped on every change; waiters watch it. */
    val changes = MutableStateFlow(0L)

    private fun changed() {
        changes.value = changes.value + 1
    }

    @Synchronized
    fun tracks(): List<SubtitleTrack>? = (probe as? Probe.Done)?.tracks

    /** `listing` | `unavailable` | `idle` | `extracting` | `ready` | `failed`. */
    @Synchronized
    fun stateString(): String = when (probe) {
        Probe.Idle, is Probe.Running -> "listing"
        Probe.Failed -> "unavailable"
        is Probe.Done -> when (extraction) {
            Extraction.Idle -> "idle"
            is Extraction.Running -> "extracting"
            is Extraction.Ready -> "ready"
            Extraction.Failed -> "failed"
        }
    }

    /** Claims the listing for session [owner] when nobody ran it yet. */
    @Synchronized
    fun beginProbe(owner: String): CancelToken? {
        if (probe != Probe.Idle) return null
        return CancelToken().also { probe = Probe.Running(owner, it) }
    }

    fun finishProbe(owner: String, tracks: List<SubtitleTrack>?) {
        synchronized(this) {
            val p = probe
            if (p !is Probe.Running || p.owner != owner) return
            probe = if (tracks != null) Probe.Done(tracks) else Probe.Failed
        }
        changed()
    }

    /** The text tracks to extract, claimed for session [owner], when the pass has not run yet. */
    @Synchronized
    fun beginExtraction(owner: String): Pair<List<SubtitleTrack>, CancelToken>? {
        val p = probe as? Probe.Done ?: return null
        if (extraction != Extraction.Idle) return null
        val token = CancelToken()
        extraction = Extraction.Running(owner, token)
        return p.tracks to token
    }

    fun finishExtraction(owner: String, files: Map<Pair<Int, SubFormat>, Extracted>?) {
        synchronized(this) {
            val e = extraction
            if (e !is Extraction.Running || e.owner != owner) return
            extraction = if (files != null) Extraction.Ready(files) else Extraction.Failed
        }
        changed()
    }

    /** Session [owner] closed: the jobs it runs stop and go back to idle. */
    fun abandon(owner: String) {
        synchronized(this) {
            (probe as? Probe.Running)?.takeIf { it.owner == owner }?.let {
                it.job.cancel()
                probe = Probe.Idle
            }
            (extraction as? Extraction.Running)?.takeIf { it.owner == owner }?.let {
                it.job.cancel()
                extraction = Extraction.Idle
            }
        }
        changed()
    }

    @Synchronized
    fun lookup(n: Int, format: SubFormat): Lookup {
        val tracks = when (val p = probe) {
            Probe.Idle, is Probe.Running -> return Lookup.Listing
            Probe.Failed -> return Lookup.NotFound
            is Probe.Done -> p.tracks
        }
        if (tracks.none { it.index == n && it.text && it.offers(format) }) return Lookup.NotFound
        return when (val e = extraction) {
            Extraction.Idle -> Lookup.Extracting(start = true)
            is Extraction.Running -> Lookup.Extracting(start = false)
            Extraction.Failed -> Lookup.Unavailable
            is Extraction.Ready -> when (val f = e.files[n to format]) {
                is Extracted.File -> Lookup.File(f.bytes)
                Extracted.TooLarge, null -> Lookup.Unavailable
            }
        }
    }
}

/** A cancellation flag that can also cancel a coroutine [Job] attached to it. */
class CancelToken {
    @Volatile var isCancelled: Boolean = false
        private set

    @Volatile private var job: Job? = null

    fun attach(j: Job) {
        job = j
        if (isCancelled) j.cancel()
    }

    fun cancel() {
        isCancelled = true
        job?.cancel()
    }
}

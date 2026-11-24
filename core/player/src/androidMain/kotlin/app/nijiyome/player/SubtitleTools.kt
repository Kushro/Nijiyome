// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// parity wave A (docs/redesign/15 §3.2, worker p): subtitle helpers of the player screen. Bound in `playerModule`.

/**
 * What the screen needs to draw subtitles itself and to translate them: fetch + parse external tracks (cached per URL for the life of
 * the app), copy a file picked with SAF to `cacheDir/subs/` (mpv `sub-add` needs a path), the translation cache and the font folder.
 */
class SubtitleTools(context: Context, private val client: OkHttpClient) : PlayerSubtitleTools<Uri> {
    private val appContext = context.applicationContext
    val filesDir: File = File(appContext.cacheDir, "subs")
    override val translationCache: TranslationCache = TranslationCache(File(appContext.cacheDir, "subs-translate"))
    val fonts: SubtitleFonts = SubtitleFonts(appContext)
    private val mutex = Mutex()
    private val parsed = LinkedHashMap<String, List<TimedCue>>()

    /**
     * Downloads and parses the external track at [url] with the stream's [headers]. Throws [IOException] on HTTP / parse failure. 0.4.0:
     * [via] = the client of the stream when it is not the player's own (a Link session's subtitle, served by the PC over mTLS).
     */
    override suspend fun fetch(url: String, headers: Map<String, String>, format: String?, via: OkHttpClient?): List<TimedCue> {
        mutex.withLock { parsed[url] }?.let { return it }
        val cues = withContext(Dispatchers.IO) {
            val text = if (url.startsWith("file:") || url.startsWith("/")) {
                File(Uri.parse(url).path ?: url).readText()
            } else {
                val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                (via ?: client).newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                    val body = res.body.bytes()
                    if (body.size > MAX_SUBTITLE_BYTES) throw IOException("subtitle too large")
                    String(body, Charsets.UTF_8)
                }
            }
            SubtitleParser.parse(text, format ?: SubtitleParser.formatOf(url))
        }
        if (cues.isEmpty()) throw IOException("no cues")
        mutex.withLock {
            parsed[url] = cues
            while (parsed.size > MAX_PARSED_TRACKS) parsed.remove(parsed.keys.first())
        }
        return cues
    }

    /** Copies a SAF document to `cacheDir/subs/<name>` and parses it. Returns the file and its cues. */
    override suspend fun import(uri: Uri): Pair<File, List<TimedCue>> = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "subtitle.srt"
        val format = SubtitleParser.formatOf(name) ?: appContext.contentResolver.getType(uri)?.let { SubtitleParser.formatOf(it) }
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).let { if (SubtitleParser.formatOf(it) == null) "$it.${format ?: "srt"}" else it }
        filesDir.mkdirs()
        val target = File(filesDir, safe)
        val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("cannot open")
        input.use { src -> target.outputStream().use { src.copyTo(it) } }
        if (target.length() > MAX_SUBTITLE_BYTES) {
            target.delete()
            throw IOException("subtitle too large")
        }
        val cues = SubtitleParser.parse(target.readText(), format)
        if (cues.isEmpty()) {
            target.delete()
            throw IOException("no cues")
        }
        parsed[target.toURI().toString()] = cues
        target to cues
    }

    /** Translator of [config] on the player's HTTP client. Throws [TranslationException] for missing fields. */
    override fun translator(config: TranslatorConfig): SubtitleTranslator = SubtitleTranslators.create(config, client)

    /**
     * 0.4.0 (sub-translation): random access to the playing file for [EmbeddedSubtitleExtractor]. [torrentRanges] = the downloaded
     * parts of a torrent file as fractions 0..1 (`TorrentRepository.seekMarks`), null when the file is complete / not a torrent.
     * Null for streaming manifests (HLS / DASH: see [HlsSubtitles]) and unknown schemes.
     */
    override fun mediaSource(url: String, headers: Map<String, String>, torrentRanges: (() -> List<ClosedFloatingPointRange<Double>>)?): MediaByteSource? {
        val lower = url.lowercase().substringBefore('?')
        if (lower.endsWith(".m3u8") || lower.endsWith(".mpd")) return null
        val availability: ((Long) -> List<LongRange>?)? = torrentRanges?.let { marks -> { length: Long -> TorrentByteRanges.of(marks(), length) } }
        return when {
            url.startsWith("/") || url.startsWith("file:") -> {
                val path = if (url.startsWith("/")) url else Uri.parse(url).path ?: return null
                val raf = runCatching { java.io.RandomAccessFile(path, "r") }.getOrNull() ?: return null
                ChannelByteSource(raf.channel, raf, availability?.let { a -> { a(raf.length()) } })
            }
            url.startsWith("content:") -> {
                val pfd = runCatching { appContext.contentResolver.openFileDescriptor(Uri.parse(url), "r") }.getOrNull() ?: return null
                val channel = java.io.FileInputStream(pfd.fileDescriptor).channel
                ChannelByteSource(channel, pfd, availability?.let { a -> { a(pfd.statSize) } })
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                val host = Uri.parse(url).host.orEmpty()
                val loopback = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"
                HttpByteSource(client, url, headers, remote = !loopback, availability = availability)
            }
            else -> null
        }
    }

    /** 0.4.0 (sub-translation): cues of the HLS subtitle rendition the player selected; null when the stream has none. */
    override suspend fun hlsSubtitles(masterUrl: String, headers: Map<String, String>, want: EmbeddedTrackRef): List<TimedCue>? =
        HlsSubtitles.fetch(client, masterUrl, headers, want)

    /** Milestone 3: the shared PlayerViewModel's embedded-track reader is this module's [EmbeddedSubtitleExtractor]. */
    override fun embeddedReader(media: MediaByteSource, playheadMs: () -> Long): EmbeddedSubtitleReader =
        EmbeddedSubtitleExtractor(media, playheadMs = playheadMs)

    fun displayName(uri: Uri): String? =
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        const val MAX_SUBTITLE_BYTES = 8L * 1024 * 1024
        const val MAX_PARSED_TRACKS = 12
    }
}

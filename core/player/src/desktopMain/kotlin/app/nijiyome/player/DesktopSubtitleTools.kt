// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI

// Hito 6: the desktop counterpart of Android's SubtitleTools (androidMain). A picked document is a plain file here, so "Load file…"
// copies it like Android copies a SAF document; the translation cache, the fetch of external tracks and the HLS rendition reader
// are the shared ones.

/**
 * The player's subtitle helpers on desktop: fetch + parse external tracks (cached per URL for the life of the app), copy a picked
 * file to [filesDir] (mpv `sub-add` gets that path), the translation cache under [cacheDir] and the imported fonts ([fonts]).
 */
class DesktopSubtitleTools(
    cacheDir: File,
    /** The folder of imported subtitle fonts; it is also the `sub-fonts-dir` of every mpv (see :core:player-mpv). */
    fontsDir: File,
    private val client: OkHttpClient,
) : PlayerSubtitleTools<File> {
    val filesDir: File = File(cacheDir, "subs")
    override val translationCache: TranslationCache = TranslationCache(File(cacheDir, "subs-translate"))
    val fonts: DesktopSubtitleFonts = DesktopSubtitleFonts(fontsDir)
    private val mutex = Mutex()
    private val parsed = LinkedHashMap<String, List<TimedCue>>()

    /**
     * Only `http` / `https` tracks are downloaded (an extension's URL), and a local file only when it is one the user picked in the
     * file selector ([import] copied it into [filesDir]): any other `file:` / path / scheme is refused, so a source cannot make the
     * app read (and send to the translator) a file of its choosing. A download is read in chunks and cut as soon as it passes
     * [MAX_SUBTITLE_BYTES], and a media / image / archive content type is refused before reading it.
     */
    override suspend fun fetch(url: String, headers: Map<String, String>, format: String?, via: OkHttpClient?): List<TimedCue> {
        mutex.withLock { parsed[url] }?.let { return it }
        val cues = withContext(Dispatchers.IO) {
            val text = when (val target = fetchTarget(url, filesDir)) {
                is FetchTarget.Local -> target.file.inputStream().use { String(readCapped(it, MAX_SUBTITLE_BYTES), Charsets.UTF_8) }
                is FetchTarget.Remote -> {
                    val req = Request.Builder().url(target.url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                    (via ?: client).newCall(req).execute().use { res ->
                        if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                        if (!isSubtitleContentType(res.header("Content-Type"))) throw IOException("not a subtitle: ${res.header("Content-Type")}")
                        if (res.body.contentLength() > MAX_SUBTITLE_BYTES) throw IOException("subtitle too large")
                        String(res.body.byteStream().use { readCapped(it, MAX_SUBTITLE_BYTES) }, Charsets.UTF_8)
                    }
                }
            }
            SubtitleParser.parse(text, format ?: SubtitleParser.formatOf(url))
        }
        if (cues.isEmpty()) throw IOException("no cues")
        remember(url, cues)
        return cues
    }

    /** Copies the picked [uri] (a file) to `<cache>/subs/<name>` and parses it. Returns the copy and its cues. */
    override suspend fun import(uri: File): Pair<File, List<TimedCue>> = withContext(Dispatchers.IO) {
        if (!uri.isFile) throw IOException("cannot open")
        if (uri.length() > MAX_SUBTITLE_BYTES) throw IOException("subtitle too large")
        val format = SubtitleParser.formatOf(uri.name)
        val target = File(filesDir, safeName(uri.name, format))
        filesDir.mkdirs()
        if (uri.canonicalFile != target.canonicalFile) uri.copyTo(target, overwrite = true)
        val cues = SubtitleParser.parse(target.readText(), format)
        if (cues.isEmpty()) {
            target.delete()
            throw IOException("no cues")
        }
        remember(target.toURI().toString(), cues)
        target to cues
    }

    override fun translator(config: TranslatorConfig): SubtitleTranslator = SubtitleTranslators.create(config, client)

    /**
     * v1.1 (F2): random access to the playing file for [ContainerSubtitleReader] (Android's SubtitleTools.mediaSource without content
     * URIs): a local path or `file:` URL, the torrent's loopback stream limited to the downloaded ranges, or a remote HTTP file. Null for
     * HLS / DASH manifests (their subtitles are renditions, see [hlsSubtitles]) and other schemes.
     */
    override fun mediaSource(
        url: String,
        headers: Map<String, String>,
        torrentRanges: (() -> List<ClosedFloatingPointRange<Double>>)?,
    ): MediaByteSource? {
        val lower = url.lowercase().substringBefore('?')
        if (lower.endsWith(".m3u8") || lower.endsWith(".mpd")) return null
        val availability: ((Long) -> List<LongRange>?)? = torrentRanges?.let { marks -> { length: Long -> TorrentByteRanges.of(marks(), length) } }
        return when {
            url.startsWith("/") || url.startsWith("file:") || WINDOWS_PATH.matches(url) -> {
                val path = if (!url.startsWith("file:")) url else runCatching { File(URI(url)).path }.getOrNull() ?: return null
                val raf = runCatching { java.io.RandomAccessFile(path, "r") }.getOrNull() ?: return null
                ChannelByteSource(raf.channel, raf, availability?.let { a -> { a(raf.length()) } })
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                val host = runCatching { URI(url).host }.getOrNull().orEmpty()
                val loopback = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "[::1]"
                HttpByteSource(client, url, headers, remote = !loopback, availability = availability)
            }
            else -> null
        }
    }

    override suspend fun hlsSubtitles(masterUrl: String, headers: Map<String, String>, want: EmbeddedTrackRef): List<TimedCue>? =
        HlsSubtitles.fetch(client, masterUrl, headers, want)

    /** v1.1 (F2): the embedded text tracks of a Matroska / MP4 file, read by this module's own container reader. */
    override fun embeddedReader(media: MediaByteSource, playheadMs: () -> Long): EmbeddedSubtitleReader =
        ContainerSubtitleReader(media, playheadMs = playheadMs)

    private suspend fun remember(key: String, cues: List<TimedCue>) = mutex.withLock {
        parsed[key] = cues
        while (parsed.size > MAX_PARSED_TRACKS) parsed.remove(parsed.keys.first())
    }

    /** Where [fetch] reads a track from. */
    internal sealed interface FetchTarget {
        data class Local(val file: File) : FetchTarget
        data class Remote(val url: String) : FetchTarget
    }

    companion object {
        const val MAX_SUBTITLE_BYTES = 8L * 1024 * 1024

        /** `C:\\...` / `C:/...`: a local file on Windows. */
        private val WINDOWS_PATH = Regex("^[A-Za-z]:[\\\\/].*")

        /**
         * [url] as a [FetchTarget]: `http(s)` is remote; `file:` or an absolute path only when it is a file inside [picked] (the
         * copies of the files the user chose); anything else throws.
         */
        internal fun fetchTarget(url: String, picked: File): FetchTarget {
            val scheme = url.substringBefore(':', "").lowercase()
            if (scheme == "http" || scheme == "https") return FetchTarget.Remote(url)
            val file = when {
                scheme == "file" -> runCatching { File(URI(url)) }.getOrNull()
                url.startsWith("/") -> File(url)
                // 1.1.2: a Windows path (`C:\...`, `C:/...`) is not a URL whose scheme is the drive letter; nor is a UNC path
                // (`\\server\share\...`). Either is accepted only where it is absolute on this OS, and still only inside [picked].
                WINDOWS_PATH.matches(url) || url.startsWith("\\\\") -> File(url).takeIf { it.isAbsolute }
                else -> null
            } ?: throw IOException("unsupported subtitle URL scheme")
            val dir = picked.canonicalFile
            val canonical = file.canonicalFile
            if (canonical.parentFile != dir || !canonical.isFile) throw IOException("subtitle file was not picked by the user")
            return FetchTarget.Local(canonical)
        }

        /** Reads [input] in chunks, failing as soon as it passes [max] bytes (nothing past that is kept in memory). */
        internal fun readCapped(input: InputStream, max: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(READ_CHUNK)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw IOException("subtitle too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        /**
         * A content type a subtitle file can come with: none, any text, the subtitle / XML ones and the generic binary one many CDNs
         * use for `.ass` / `.srt`. Video, audio, images, fonts, playlists, archives and the like are refused.
         */
        internal fun isSubtitleContentType(header: String?): Boolean {
            val type = header?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (type.isEmpty() || type.startsWith("text/")) return true
            return type in SUBTITLE_TYPES || SUBTITLE_TYPE_WORDS.any { it in type }
        }

        private val SUBTITLE_TYPES = setOf(
            "application/octet-stream", "binary/octet-stream", "application/xml", "application/ttml+xml",
        )
        private val SUBTITLE_TYPE_WORDS = listOf("subrip", "vtt", "x-ass", "x-ssa", "ttml", "sami", "x-sub")
        private const val READ_CHUNK = 16 * 1024
        const val MAX_PARSED_TRACKS = 12

        /** The copy's name: only safe characters, at most 80 of them, with a subtitle extension. */
        internal fun safeName(name: String, format: String?): String =
            name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).let { if (SubtitleParser.formatOf(it) == null) "$it.${format ?: "srt"}" else it }
    }
}

/**
 * Fonts the user imports for subtitles: TTF / OTF / TTC copied to [dir]. mpv reads the folder (`sub-fonts-dir`, so libass also finds
 * the fonts ASS styles ask for there) and, when one is chosen, gets its family name as `sub-font`. The app's own fonts installed there
 * for libass (named [BUNDLED_PREFIX]...) are not user fonts: [list] hides them and [import] never writes over them.
 */
class DesktopSubtitleFonts(val dir: File) {
    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && isFontName(f.name) && !f.name.startsWith(BUNDLED_PREFIX) }?.sortedBy { it.name.lowercase() }.orEmpty()

    fun file(name: String?): File? = name?.takeIf { InstalledSubtitleFont.family(it) == null }?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Copies [source] in (sanitised name, fonts only, at most [MAX_FONT_BYTES]). Returns the stored file name or null when refused. */
    fun import(source: File): String? {
        val safe = source.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).let { if (it.startsWith(BUNDLED_PREFIX)) "user_$it" else it }
        if (!isFontName(safe) || !source.isFile || source.length() == 0L || source.length() > MAX_FONT_BYTES) return null
        dir.mkdirs()
        val target = File(dir, safe)
        if (source.canonicalFile != target.canonicalFile) source.copyTo(target, overwrite = true)
        return safe
    }

    fun delete(name: String): Boolean = file(name)?.delete() == true

    companion object {
        const val MAX_FONT_BYTES = 30L * 1024 * 1024

        /** File-name prefix of the app's own fonts installed for libass (the same as on Android). */
        const val BUNDLED_PREFIX = "nijiyome_"

        fun isFontName(name: String): Boolean = name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") || it.endsWith(".ttc") }
    }
}

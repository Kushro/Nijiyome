// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/lib.rs `LinkHost` and tk-link/src/library.rs (AGPL-3.0, same author).
package app.nijiyome.server.link

import app.nijiyome.link.LibraryItemDto
import app.nijiyome.link.PlayerStateDto
import app.nijiyome.model.HistoryEntry
import app.nijiyome.server.net.TrustedOrigin
import okhttp3.HttpUrl
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Locale

/** What a `torrent` session source asks the host for (BUG-009). */
data class TorrentRequest(val uri: String, val mediaId: String, val episode: Int)

/** The loopback stream of an opened torrent: `http://127.0.0.1:<port>/<token>/<hash>/<index>/<name>`. */
data class OpenedTorrent(val url: String, val torrentId: String, val title: String? = null)

/**
 * What the app gives the Link server: the library folder, FFmpeg, the extensions' permissions, the watch history, the player
 * (remote control) and the torrent engine. Every default answers "not available", so a host implements only what it has.
 */
interface LinkHost {
    fun library(): List<LibraryItemDto> = emptyList()
    fun libraryPath(id: String): File? = null
    fun ffmpegPath(): File?

    /** `network:domain:*` permissions of the installed extension [id]; null when it is not installed. */
    fun extensionPermissions(id: String): List<String>? = null

    /** Rows with `watchedAt > sinceUnix` (unix s; 0 = all), OLDEST first, at most [limit] (the route asks `limit + 1`). Blocking. */
    fun historySince(sinceUnix: Long, limit: Int): List<HistoryEntry> = emptyList()

    /** Merges rows pushed by a phone (newest wins); returns how many were stored. Blocking. */
    fun historyMerge(rows: List<HistoryEntry>): Int = 0

    /** Rows pushed by [deviceId]; with [authoritative] (its resync window is open) every row wins regardless of timestamps. */
    fun historyMergeFrom(deviceId: String, rows: List<HistoryEntry>, authoritative: Boolean): Int = historyMerge(rows)

    /** Drops the whole local history; false when the host cannot (the route answers `internal`). */
    fun historyClear(): Boolean = false

    /** A paired phone drives this PC's player; false when no player can take it (`409 no_player`). */
    fun control(cmd: ControlCommand): Boolean = false

    /** What the player shows now; null = no player open (`404 no_player`). */
    fun playerState(): PlayerStateDto? = null

    /** BUG-023: exact local endpoints a `url` source may reach although private (this PC's extension server media proxy). */
    fun trustedMediaOrigins(): List<TrustedOrigin> = emptyList()

    /** BUG-009: adds / reuses a torrent and returns its loopback stream. @throws ApiException (default `source_unavailable`). */
    suspend fun openTorrent(req: TorrentRequest): OpenedTorrent = api(ErrorCode.SOURCE_UNAVAILABLE)

    /** BUG-131: a renewed session reads the torrent an earlier session opened; true = the engine still has it (one more reader). */
    suspend fun reuseTorrent(torrentId: String): Boolean = false

    /** A Link session reading [torrentId] ended (one call per successful open / reuse). */
    fun torrentSessionEnded(torrentId: String) {}

    /** Stores diagnostic log lines shipped from a paired device (`POST /v1/logs`); returns accepted count. */
    fun receiveLogs(deviceId: String, deviceName: String, lines: List<String>, appVersion: String? = null, platform: String? = null): Int = 0
}

/** The exact origin of the engine's stream server for [url] (loopback literal + port + `/<token>/`), trusted for that session only. */
fun torrentStreamOrigin(url: HttpUrl): TrustedOrigin? {
    val ip = runCatching { InetAddress.getByName(url.host) }.getOrNull()?.takeIf { it.isLoopbackAddress && it is java.net.Inet4Address && url.host.first().isDigit() } ?: return null
    val token = url.pathSegments.firstOrNull()?.takeIf { t -> t.isNotEmpty() && t.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' } } ?: return null
    return TrustedOrigin(InetSocketAddress(ip, url.port), "/$token/")
}

/** `GET /v1/library`: ONE user-chosen folder, non-recursive. */
object Library {
    const val MAX_ITEMS = 10_000
    private val EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "webm", "mov", "ts", "wmv")

    /** Stable id of a path (never the path itself): 32 lower-case hex characters. */
    fun idFor(path: File): String =
        MessageDigest.getInstance("SHA-256").digest(path.path.lowercase(Locale.ROOT).toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    class Scan(val items: List<LibraryItemDto>, val truncated: Boolean, val paths: Map<String, File>)

    /** Playable files directly inside [dir], sorted by title; durations / sizes from [probe] when given. Blocking. */
    fun scanFolder(dir: File, probe: ((File) -> Triple<Double, Int, Int>?)? = null): Scan {
        val files = (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in EXTENSIONS }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        val kept = files.take(MAX_ITEMS)
        val paths = LinkedHashMap<String, File>()
        val items = kept.map { f ->
            val (duration, w, h) = probe?.invoke(f) ?: Triple(0.0, 0, 0)
            val id = idFor(f)
            paths[id] = f
            LibraryItemDto(id = id, title = f.nameWithoutExtension, durationSecs = duration, width = w, height = h, sizeBytes = f.length())
        }
        return Scan(items, files.size > MAX_ITEMS, paths)
    }
}

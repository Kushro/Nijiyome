// SPDX-License-Identifier: AGPL-3.0-or-later
// The desktop app's side of the Link host, after Tatakai Next tk-app/src/live/link.rs (AppLinkHost) and live/history_sync.rs
// (AGPL-3.0, same author).
package app.nijiyome.server.desktop

import app.nijiyome.database.HistoryDao
import app.nijiyome.database.HistoryRow
import app.nijiyome.link.LibraryItemDto
import app.nijiyome.link.PlayerStateDto
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.HistoryEntry
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.PcPlayerRemote
import app.nijiyome.model.PcRemoteCommand
import app.nijiyome.model.torrent.TorrentEngineUnavailable
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.server.link.ControlCommand
import app.nijiyome.server.link.ErrorCode
import app.nijiyome.server.link.History
import app.nijiyome.server.link.Library
import app.nijiyome.server.link.LinkHost
import app.nijiyome.server.link.OpenedTorrent
import app.nijiyome.server.link.TorrentRequest
import app.nijiyome.server.link.api
import kotlinx.coroutines.launch
import app.nijiyome.server.link.media.Platform
import app.nijiyome.server.net.TrustedOrigin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The PC's watch history (Room, the same rows the desktop player writes) as the Link history peer: pages oldest first from a
 * `watchedAt` cursor, merges by "newest wins, tie -> higher progress" (`HistoryDao.mergeRemote`), authoritative merges while a
 * phone is the chosen source, and the origin device of every pushed row (`history-origins.json`, so the screen can say what each
 * phone contributed; the database schema is not touched).
 */
class RoomLinkHistory(private val dao: HistoryDao, private val originsFile: File) {
    private val origins: MutableMap<String, String> = runCatching {
        Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), originsFile.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    fun since(sinceUnix: Long, limit: Int): List<HistoryEntry> = runBlocking {
        dao.all().filter { it.watchedAtMs / 1000 > sinceUnix }.sortedBy { it.watchedAtMs }.take(limit).map(::entryOf)
    }

    fun mergeFrom(deviceId: String, rows: List<HistoryEntry>, authoritative: Boolean): Int = runBlocking {
        var stored = 0
        for (e in rows) {
            val local = dao.get(e.episodeId)
            // BUG-096: a row played in a Link session can come without a poster: keep the one the PC already has
            val poster = e.poster?.takeIf { it.isNotEmpty() } ?: local?.poster ?: History.knownPoster(dao.all().filter { it.animeId == e.animeId }.map(::entryOf), e.episodeId)
            val row = rowOf(e.copy(poster = poster))
            val took = if (authoritative) {
                dao.upsert(row.copy(syncedAtMs = row.watchedAtMs))
                true
            } else {
                dao.mergeRemote(row)
            }
            if (took) {
                stored++
                synchronized(origins) { origins[e.episodeId] = deviceId }
            }
        }
        saveOrigins()
        stored
    }

    fun clear(): Boolean = runBlocking {
        dao.clear()
        synchronized(origins) { origins.clear() }
        saveOrigins()
        true
    }

    /** History rows per origin device. */
    fun originCounts(): Map<String, Int> = synchronized(origins) { origins.values.groupingBy { it }.eachCount() }

    private fun saveOrigins() {
        runCatching {
            originsFile.parentFile?.mkdirs()
            val text = synchronized(origins) { Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), origins.toMap()) }
            originsFile.writeText(text)
        }
    }

    companion object {
        fun entryOf(r: HistoryRow) = HistoryEntry(
            animeId = r.animeId, title = r.title, poster = r.poster, episodeId = r.episodeId, episode = r.episode, progressSec = r.progressSec,
            durationSec = r.durationSec, completed = r.completed, watchedAt = Instant.ofEpochMilli(r.watchedAtMs), anilistId = r.anilistId, malId = r.malId,
        )

        fun rowOf(e: HistoryEntry) = HistoryRow(
            episodeId = e.episodeId, animeId = e.animeId, title = e.title, poster = e.poster?.takeIf { it.isNotEmpty() }, episode = e.episode,
            progressSec = e.progressSec, durationSec = e.durationSec?.takeIf { it > 0 }, completed = HistoryEntry.isCompleted(e.progressSec, e.durationSec, e.completed),
            watchedAtMs = e.watchedAt.toEpochMilli(), anilistId = e.anilistId, malId = e.malId, syncedAtMs = null,
        )
    }
}

/** The desktop app as the Link host: FFmpeg from the system, the library folder, the installed extensions and the history. */
class DesktopLinkHost(
    private val history: RoomLinkHistory?,
    private val extensions: ExtensionRepository?,
    private val libraryFolder: () -> File?,
    /** The extension server's own media proxy (`ip:port`), trusted as a `url` source (BUG-023); empty while it is off. */
    private val ownMediaOrigins: () -> List<InetSocketAddress>,
    /** The desktop player (remote control, `GET /v1/player`); null = none in this build. */
    private val player: PcPlayerRemote? = null,
    /**
     * The desktop torrent engine (`torrent` sources), asked for only when a phone needs it (getting it loads libtorrent's native
     * library); null or [TorrentEngineUnavailable] = `source_unavailable`.
     */
    private val torrentEngine: () -> TorrentRepository? = { null },
    /** The [Media] a torrent request names (titles help pick the episode file of a batch); null = a bare one. */
    private val mediaOf: suspend (TorrentRequest) -> Media? = { null },
    /** Centralized store for logs shipped by paired mobile devices (`POST /v1/logs`). */
    private val deviceLogs: DeviceLogsStore? = null,
) : LinkHost {
    @Volatile private var scan: Library.Scan? = null

    /** BUG-131: Link sessions reading each torrent; only the last reader's end runs the after-watch policy. */
    private val readers = HashMap<String, Int>()

    private val torrents: TorrentRepository? by lazy { runCatching(torrentEngine).getOrNull() }

    override fun ffmpegPath(): File? = findFfmpeg()

    override fun library(): List<LibraryItemDto> {
        val dir = libraryFolder()?.takeIf { it.isDirectory } ?: return emptyList<LibraryItemDto>().also { scan = null }
        val ffprobe = findFfmpeg()?.let { app.nijiyome.server.link.media.FfmpegEngine.siblingFfprobe(it) }
        return Library.scanFolder(dir, ffprobe?.let { p -> { f: File -> probe(p, f) } }).also { scan = it }.items
    }

    override fun libraryPath(id: String): File? = (scan ?: run { library(); scan })?.paths?.get(id)

    override fun extensionPermissions(id: String): List<String>? = extensions?.installed?.value?.firstOrNull { it.id == id }?.permissions

    override fun historySince(sinceUnix: Long, limit: Int): List<HistoryEntry> = history?.since(sinceUnix, limit).orEmpty()

    override fun historyMerge(rows: List<HistoryEntry>): Int = history?.mergeFrom("", rows, false) ?: 0

    override fun historyMergeFrom(deviceId: String, rows: List<HistoryEntry>, authoritative: Boolean): Int =
        history?.mergeFrom(deviceId, rows, authoritative) ?: 0

    override fun historyClear(): Boolean = history?.clear() ?: false

    override fun trustedMediaOrigins(): List<TrustedOrigin> = ownMediaOrigins().map { TrustedOrigin(it, "/api/server/v1/media/") }

    // ---- remote control (tk-app live/link.rs `control` / `player_state`) ----

    override fun control(cmd: ControlCommand): Boolean {
        val p = player ?: return false
        val st = p.state.value
        // `play` while playing / `pause` while paused change nothing: taken without reaching the player (tk-app `redundant`)
        if (cmd == ControlCommand.Play && st?.playing == true) return true
        if (cmd == ControlCommand.Pause && st != null && !st.playing) return true
        return p.command(remoteCommand(cmd))
    }

    override fun playerState(): PlayerStateDto? = player?.state?.value?.let { s ->
        PlayerStateDto(playing = s.playing, title = s.title?.takeIf { it.isNotBlank() }, episode = s.episode, positionSecs = s.positionSecs, durationSecs = s.durationSecs, mediaId = s.mediaId)
    }

    // ---- torrent sources (tk-app live/link.rs `open_torrent` / `reuse_torrent` / `torrent_session_ended`) ----

    override suspend fun openTorrent(req: TorrentRequest): OpenedTorrent {
        val engine = torrents?.takeIf { it !is TorrentEngineUnavailable } ?: api(ErrorCode.SOURCE_UNAVAILABLE)
        val media = runCatching { mediaOf(req) }.getOrNull() ?: bareMedia(req)
        val played = engine.play(req.uri, media, req.episode).getOrElse { e -> api(torrentError(e)) }
        addReader(played.torrentId)
        val label = media.title.takeIf { it.isNotBlank() } ?: played.fileName.takeIf { it.isNotBlank() }
        return OpenedTorrent(played.stream.url, played.torrentId, label)
    }

    override suspend fun reuseTorrent(torrentId: String): Boolean {
        val engine = torrents?.takeIf { it !is TorrentEngineUnavailable } ?: return false
        val entry = engine.library.value.firstOrNull { it.id.equals(torrentId, ignoreCase = true) } ?: return false
        // counted before the resume, so an after-watch policy waiting out its grace period skips the pause; given back on failure
        addReader(entry.id)
        return try {
            engine.resume(entry.id)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            removeReader(entry.id)
            false
        }
    }

    override fun torrentSessionEnded(torrentId: String) {
        if (!removeReader(torrentId)) return // another Link session still reads it
        val engine = torrents ?: return
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { engine.playbackEnded(torrentId, completed = false) } }
    }

    override fun receiveLogs(deviceId: String, deviceName: String, lines: List<String>, appVersion: String?, platform: String?): Int =
        deviceLogs?.append(deviceId, deviceName, lines, appVersion, platform) ?: 0

    private fun addReader(id: String) = synchronized(readers) { readers[id.lowercase()] = (readers[id.lowercase()] ?: 0) + 1 }

    /** True when that was the last reader. */
    private fun removeReader(id: String): Boolean = synchronized(readers) {
        val k = id.lowercase()
        val n = (readers[k] ?: 0) - 1
        if (n > 0) readers[k] = n else readers.remove(k)
        n <= 0
    }

    internal fun readerCount(id: String): Int = synchronized(readers) { readers[id.lowercase()] ?: 0 }

    companion object {
        /** Wire command -> the player's command (tk-app `remote_vm`). Pure. */
        fun remoteCommand(cmd: ControlCommand): PcRemoteCommand = when (cmd) {
            ControlCommand.Play -> PcRemoteCommand.Play
            ControlCommand.Pause -> PcRemoteCommand.Pause
            ControlCommand.Toggle -> PcRemoteCommand.Toggle
            ControlCommand.Next -> PcRemoteCommand.Next
            ControlCommand.Prev -> PcRemoteCommand.Prev
            is ControlCommand.Seek -> PcRemoteCommand.Seek(cmd.seconds)
            // u32 on the wire: clamped, never wrapped to a negative Int
            is ControlCommand.Open -> PcRemoteCommand.Open(cmd.mediaId, cmd.episode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), cmd.positionSecs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        /** The engine's refusal -> the session's error: no file for that episode = `library_item_not_found` (tk-app NoSource). */
        fun torrentError(e: Throwable): ErrorCode {
            val msg = ((e as? AppException)?.error as? AppError.Unknown)?.msg.orEmpty()
            return if (msg.startsWith("torrent:no_file")) ErrorCode.LIBRARY_ITEM_NOT_FOUND else ErrorCode.SOURCE_UNAVAILABLE
        }

        /** [HistoryEntry.animeId] form (`"21"`, `"mal:5114"`) -> its [MediaRef]. */
        fun refOf(animeId: String): MediaRef = when {
            animeId.startsWith("mal:") -> MediaRef(malId = animeId.removePrefix("mal:").toIntOrNull())
            else -> MediaRef(anilistId = animeId.toIntOrNull())
        }

        /** A torrent request's media when nothing better is known: its ref only (the engine then picks the file by episode). */
        fun bareMedia(req: TorrentRequest): Media = Media(
            ref = refOf(req.mediaId), kind = MediaKind.ANIME, title = "", titles = emptyMap(), cover = null, banner = null, color = null,
            synopsis = null, genres = emptyList(), episodes = null, status = null, season = null, year = null, score = null, sourceProvider = "link",
        )

        /** `NIJIYOME_FFMPEG` (an absolute path), else `ffmpeg` on the PATH. */
        fun findFfmpeg(env: (String) -> String? = System::getenv): File? {
            env("NIJIYOME_FFMPEG")?.let(::File)?.takeIf { it.isAbsolute && it.isFile }?.let { return it }
            val name = if (Platform.current() == Platform.WINDOWS) "ffmpeg.exe" else "ffmpeg"
            return env("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotEmpty() }.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
        }

        /** `(duration, width, height)` of a library file through ffprobe; none on failure or when it hangs past [timeoutMs]. */
        internal fun probe(ffprobe: File, file: File, timeoutMs: Long = 20_000): Triple<Double, Int, Int>? = runCatching {
            val out = app.nijiyome.server.link.media.BoundedProcess.run(
                listOf(ffprobe.path, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", "-select_streams", "v:0", file.path),
                256 * 1024, timeoutMs,
            ) ?: return null
            val o = Json.parseToJsonElement(out) as kotlinx.serialization.json.JsonObject
            val duration = (o["format"] as? kotlinx.serialization.json.JsonObject)?.get("duration")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDoubleOrNull() } ?: 0.0
            val s = (o["streams"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject
            val w = (s?.get("width") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val h = (s?.get("height") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
            Triple(duration, w, h)
        }.getOrNull()
    }
}

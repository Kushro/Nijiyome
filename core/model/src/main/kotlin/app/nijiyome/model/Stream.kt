package app.nijiyome.model

import kotlinx.serialization.Serializable

enum class Container {
    HLS,
    MP4,
    DASH,

    /** android wave (12 §3.2): progressive HTTP file without manifest (torrent loopback MKV/MP4/WebM); the player infers the extractor. */
    PROGRESSIVE,
}

@Serializable
data class SubtitleTrack(val url: String, val lang: String, val label: String, val format: String)

@Serializable
data class StreamSource(
    val url: String,
    val headers: Map<String, String>,
    val container: Container,
    val quality: String?,
    val provider: String,
    val extensionId: String,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

@Serializable
data class PlayerArgs(
    val media: MediaRef,
    val title: String,
    val episode: Int,
    val startMs: Long = 0,
    val remoteSessionId: String? = null,
    // --- android wave (12 §3.2), all optional ---
    /** `sourceKey` chosen in Details (split button / picker): the player tries it first (Manual mode: only it). */
    val sourceKey: String? = null,
    /**
     * BUG-018 / v1.2.0: [sourceKey] was chosen in a picker before opening the player, so "Selección de fuente: Manual" plays it
     * without asking a second time. false = no explicit pick for this session: Manual shows the picker.
     */
    val sourceConfirmed: Boolean = false,
    /** v1.2.0: stable torrent infoHash companion to [sourceKey] so reordering/renumbering (`#2`) never breaks confirmed matching. */
    val sourceInfoHash: String? = null,
    /** Play a file of a torrent already in the session (client "Play this file", batch next episode, completed download). */
    val torrentId: String? = null,
    /** With [torrentId]: the file to play; null = `pickEpisodeFile`. */
    val fileIndex: Int? = null,
    /** A completed download (file path or `content://`): play offline, no network, no source resolution. */
    val localUri: String? = null,
    /**
     * parity wave B (16 §3.4, worker t, checklist ext.custom-providers.watch / .continue): a `watch` entry of a custom provider page. The
     * player resolves it with `ExtensionRepository.providerWatch` instead of `resolveCandidates`; history is recorded under
     * `HistoryEntry.animeId = "ext:<extensionId>:<sourceId>:<itemId>"` (anilistId / malId null) unless [media] has an AniList id.
     */
    val provider: ProviderEntryRef? = null,
    /**
     * Desktop `nijiyome <file>`: a video file opened on its own, with no title behind it (no episode number, no other episode). An
     * unpaired download on Android keeps false: its player behaves as before.
     */
    val looseFile: Boolean = false,
)

/** parity wave B (16 §3.4): coordinates of one entry of a custom provider title (see [PlayerArgs.provider], `MangaReaderArgs`). */
@Serializable
data class ProviderEntryRef(val extensionId: String, val sourceId: String, val itemId: String, val entryId: String) {
    /** `HistoryEntry.animeId` of a provider title (KNOWN-ISSUES ola A: ids `ext:<extensión>:<fuente>:<item>`). */
    val historyId: String get() = "ext:$extensionId:$sourceId:$itemId"
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model.torrent

import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.StreamSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Parity wave A contract, 2026-11-09 amendment (plan 14 §0.1 #11): debrid moves to Android worker q in wave A. Desktop counterpart:
// tk-core/debrid/** (worker i), `DebridServiceVm` / `DebridStatusVm` of the desktop contract §3. Signatures FINAL; bodies: worker q in
// :core:torrent (`DebridRepositoryImpl`, bound in `torrentModule`). User decision: debrid is a section of the Torrents client
// (`TorrentSection.DEBRID`) plus `SourceCandidate.debrid: DebridStatus?`; no new SourceKind.

/** The four services of the classic app. [id] = desktop `DebridServiceVm::id()`: persistence / JSON key and `SecretKeys.debridApiKey`. */
@Serializable
enum class DebridService(val id: String, val label: String) {
    @SerialName("real_debrid") REAL_DEBRID("real_debrid", "Real-Debrid"),
    @SerialName("torbox") TORBOX("torbox", "TorBox"),
    @SerialName("alldebrid") ALLDEBRID("alldebrid", "AllDebrid"),
    @SerialName("premiumize") PREMIUMIZE("premiumize", "Premiumize"),
    ;

    /**
     * 1.1 (F7, user decision 2026-11-23): TorBox is the recommended and tested service; the other three stay visible with an
     * "Experimental" label and keep their inherited behaviour.
     */
    val recommended: Boolean get() = this == TORBOX
}

/**
 * 1.1 (F7): Settings › Torrents › Debrid. Inside [TorrentSettings.debrid]; every field has a default so stored settings keep loading.
 * Without a linked account nothing here has any effect.
 */
@Serializable
data class DebridSettings(
    /** Off = no cache lookup, no instant playback, no debrid downloads: every torrent goes through libtorrent. */
    val enabled: Boolean = true,
    /** "Preferir TorBox cuando esté en caché": a cached torrent gets the ranking bonus and can become the recommended / Auto source. */
    val preferCached: Boolean = true,
    /** "Descargar por TorBox": Download on a cached torrent goes through the web download engine (HTTPS) instead of libtorrent. */
    val downloadCached: Boolean = true,
)

/**
 * 1.1 (F7): one file of a torrent on a debrid service, enough to ask the service for a fresh link later (a download resumed after
 * the link expired). [fileId] is the service's own file id; [magnet] lets the service re-add the torrent if it was removed.
 */
@Serializable
data class DebridFileRef(
    val service: DebridService,
    val infoHash: String,
    val fileId: String,
    val fileName: String,
    val magnet: String? = null,
)

/**
 * 1.1 (F7): why a debrid service could not serve something, from the `AppError` its repository returned (`debrid:*` codes; TorBox
 * envelope errors arrive as `debrid:torbox:<CODE>`). The UI shows the real reason instead of a generic failure.
 */
enum class DebridProblem { INVALID_KEY, ACTIVE_LIMIT, MONTHLY_LIMIT, PLAN, TOO_LARGE, RATE_LIMITED, NOT_CACHED, OFFLINE, BAD_LINK, NO_FILE, SERVICE }

fun debridProblemOf(error: app.nijiyome.model.AppError?): DebridProblem {
    if (error is app.nijiyome.model.AppError.Offline) return DebridProblem.OFFLINE
    if (error is app.nijiyome.model.AppError.Http) return if (error.code == 401 || error.code == 403) DebridProblem.INVALID_KEY else DebridProblem.SERVICE
    val msg = (error as? app.nijiyome.model.AppError.Unknown)?.msg.orEmpty()
    return when {
        msg == "debrid:invalid_key" || msg.endsWith(":BAD_TOKEN") || msg.endsWith(":NO_AUTH") || msg.endsWith(":AUTH_ERROR") -> DebridProblem.INVALID_KEY
        msg.endsWith(":ACTIVE_LIMIT") -> DebridProblem.ACTIVE_LIMIT
        msg.endsWith(":MONTHLY_LIMIT") -> DebridProblem.MONTHLY_LIMIT
        msg.endsWith(":PLAN_RESTRICTED_FEATURE") -> DebridProblem.PLAN
        msg.endsWith(":DOWNLOAD_TOO_LARGE") -> DebridProblem.TOO_LARGE
        msg == "debrid:rate_limited" || msg.endsWith(":COOLDOWN_LIMIT") -> DebridProblem.RATE_LIMITED
        msg.startsWith("debrid:not_cached") -> DebridProblem.NOT_CACHED
        msg == "debrid:bad_link" || msg.endsWith(":LINK_OFFLINE") -> DebridProblem.BAD_LINK
        msg == "debrid:offline" -> DebridProblem.OFFLINE
        msg == "debrid:no_file" -> DebridProblem.NO_FILE
        else -> DebridProblem.SERVICE
    }
}

/** 1.1 (F7): a resolved debrid file: its HTTPS stream and how to renew it. */
data class DebridResolved(val stream: StreamSource, val file: DebridFileRef)

/** Desktop `DebridStatusVm`: a torrent source as seen by a linked debrid service. [cached] = instantly available over HTTPS. */
@Serializable
data class DebridStatus(val service: DebridService, val cached: Boolean)

/** A linked debrid account (the API key itself lives in `SecretStore` under `SecretKeys.debridApiKey`). */
data class DebridAccount(
    val service: DebridService,
    val username: String?,
    /** Epoch ms when premium ends; null = unknown / free. */
    val premiumUntilMs: Long?,
    val linkedAtMs: Long,
)

/** A torrent file the debrid service can serve directly over HTTPS. */
data class DebridFile(val index: Int, val name: String, val sizeBytes: Long, val episode: Int? = null)

/**
 * Debrid services: cached-torrent lookup, HTTPS resolution of a cached torrent, account linking by API key (classic Settings ›
 * Debrid). A cached torrent plays as a plain HTTPS stream (no libtorrent, no background gate); the source picker shows the
 * "En caché · <service>" badge (checklist sources.debrid.cached-badge) and the ranking prefers cached torrents over raw ones
 * (sources.debrid.ranking, worker q in :core:release `score`, additive). Hosts: `api.real-debrid.com`, `api.torbox.app`,
 * `api.alldebrid.com`, `www.premiumize.me` only, with the shared OkHttpClient; the API key travels only to its own service.
 */
interface DebridRepository {
    val accounts: StateFlow<List<DebridAccount>>

    /** Validates [apiKey] against the service (`/user`), stores it in `SecretStore`, returns the account. */
    suspend fun link(service: DebridService, apiKey: String): Result<DebridAccount>

    suspend fun unlink(service: DebridService)

    /**
     * Moves [service] to the front of the preference order (the first linked service that has a torrent plays it). The default
     * does nothing, for implementations without an order.
     */
    suspend fun prefer(service: DebridService) {}

    /** Which linked services have each info-hash cached (instant availability). Unknown / unlinked = absent. Batched, short timeout. */
    suspend fun cached(infoHashes: List<String>): Map<String, Set<DebridService>>

    /**
     * Fills [SourceCandidate.debrid] of the torrent candidates from [cached] (first linked service that has it; `cached = false` for
     * linked services that do not). Called by the player's `SourceFlow` (worker p, one line) before ranking; without linked accounts
     * it returns [candidates] untouched. Default here so `:core:player` needs no dependency on `:core:torrent`.
     */
    suspend fun markCached(candidates: List<SourceCandidate>): List<SourceCandidate> = candidates

    /** Files of a cached torrent as the service lists them (adds the magnet to the account when needed). */
    suspend fun files(service: DebridService, infoHash: String, magnet: String?): Result<List<DebridFile>>

    /**
     * Direct HTTPS stream of one file of a cached torrent (`Container.PROGRESSIVE`, `provider = service.label`, `extensionId` of the
     * torrent source). Fails with `AppException(AppError.Http(status))` when the service rejects it.
     */
    suspend fun resolve(service: DebridService, infoHash: String, magnet: String?, fileIndex: Int?, episode: Int?): Result<StreamSource> =
        resolveFile(service, infoHash, magnet, fileIndex, episode, emptyList()).map { it.stream }

    /**
     * 1.1 (F7): [resolve] plus the file reference (for [refreshLink]). The file is [fileIndex] when given, else the manual episode
     * alignment of the torrent, else `pickEpisodeFile(files, episode, titles)`, and only then the episode regex / largest video.
     */
    suspend fun resolveFile(
        service: DebridService,
        infoHash: String,
        magnet: String?,
        fileIndex: Int?,
        episode: Int?,
        titles: List<String>,
    ): Result<DebridResolved> = Result.failure(UnsupportedOperationException("debrid:unsupported"))

    /** 1.1 (F7): a fresh HTTPS link for [file] (debrid links expire). */
    suspend fun refreshLink(file: DebridFileRef): Result<String> = Result.failure(UnsupportedOperationException("debrid:unsupported"))

    /** 1.1 (F7): "Probar la clave" of a linked account: checks the stored key again and refreshes the account data. */
    suspend fun recheck(service: DebridService): Result<DebridAccount> = Result.failure(UnsupportedOperationException("debrid:unsupported"))

    /** 1.1 (F7): the current [DebridSettings] (enabled / prefer cached / download cached). */
    val settings: DebridSettings get() = DebridSettings()
}

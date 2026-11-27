// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

// The fixed contract of GPU image upscaling (docs/plan-upscaling.md). Steps 2 (this module), 3 (UI and settings) and 4 (platform
// wiring) code against these types in parallel: add members if you must, never rename or remove one without telling the coordinator.

/** What an image is, which decides whether and how far it is upscaled (plan §4.3). */
@Serializable
enum class UpscaleImageKind { POSTER, BANNER, BACKDROP, EPISODE_THUMB, AVATAR, OTHER }

/** The engine. v1.3.3 ships only [REAL_ESRGAN]; [ANIME4K] is reserved for phase 2 and is never offered yet. */
@Serializable
enum class UpscaleEngineId { REAL_ESRGAN, ANIME4K }

/** Speed/quality trade-off: FAST caps the factor at x2, BALANCED at x3, BEST at x4. */
@Serializable
enum class UpscaleQuality { FAST, BALANCED, BEST }

/** The user's choices (Ajustes › Mejoras › Imágenes). Defaults follow the owner's decisions of 2026-11-27 (plan §8, P6). */
@Serializable
data class ImageUpscaleSettings(
    val enabled: Boolean = true,
    val engine: UpscaleEngineId = UpscaleEngineId.REAL_ESRGAN,
    val quality: UpscaleQuality = UpscaleQuality.BALANCED,
    val kinds: Set<UpscaleImageKind> = setOf(
        UpscaleImageKind.POSTER, UpscaleImageKind.BANNER, UpscaleImageKind.BACKDROP, UpscaleImageKind.EPISODE_THUMB,
    ),
    /** Cache cap in MB; the platform default is applied when null (256 desktop, 128 Android). */
    val cacheMb: Int? = null,
    /** Android only: run only while charging. Off by default. */
    val onlyWhileCharging: Boolean = false,
)

/** Why the queue is not working right now; null reasons mean it is running or idle. */
enum class UpscalePauseReason { DISABLED, NO_GPU, PLAYER_OPEN, FRAMEGEN_ACTIVE, LINK_BUSY, APP_HIDDEN, BATTERY_SAVER, LOW_BATTERY, HOT, NOT_CHARGING, ENGINE_FAILED }

/** What the settings screen shows live. */
data class UpscaleStatus(
    /** The GPU in use ("AMD Radeon RX 9070 XT"), or null when there is none. */
    val gpuName: String?,
    val queued: Int,
    val running: Boolean,
    val pausedBy: UpscalePauseReason?,
    /** Bytes used by the upscaled cache. */
    val cacheBytes: Long,
    /** Last measured time of one image, for the status line; null until one ran. */
    val lastMillis: Long?,
)

/** The state of one image. */
sealed interface UpscaleState {
    data object Idle : UpscaleState
    data object Queued : UpscaleState
    data object Running : UpscaleState

    /** The upscaled image, as a file path the image loader can read. */
    data class Done(val path: String) : UpscaleState

    /** Not worth it (already large enough, kind turned off, source not cached); the UI keeps the original. */
    data class Skipped(val why: String) : UpscaleState
    data class Failed(val why: String) : UpscaleState
}

/** One request; release it when the image leaves the screen so a queued job is dropped (plan §4.4, reference counted). */
interface UpscaleTicket {
    val key: String
    fun release()
}

/**
 * The queue the UI talks to. [request] is cheap and idempotent for the same key; the newest visible requests run first.
 * [sourceWidth]/[sourceHeight] are the decoded source pixels, [targetWidth]/[targetHeight] the on-screen pixels.
 */
interface UpscaleQueue {
    val status: StateFlow<UpscaleStatus>

    /** A finished result already on disk, so the first frame can show it. */
    fun cached(url: String, kind: UpscaleImageKind, targetWidth: Int, targetHeight: Int): String?

    fun request(
        url: String,
        kind: UpscaleImageKind,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): UpscaleTicket

    fun state(key: String): StateFlow<UpscaleState>

    /**
     * Added in step 2: the image loader fetched [url] from the network again (Coil `DataSource.NETWORK`). Results made from other
     * source bytes are dropped and the live requests run again (plan §4.5). No-op by default, so fakes need not implement it.
     */
    fun sourceRefreshed(url: String) {}
}

/** Settings persistence and cache housekeeping, for the settings screen. */
interface UpscaleSettingsStore {
    val settings: StateFlow<ImageUpscaleSettings>
    fun update(transform: (ImageUpscaleSettings) -> ImageUpscaleSettings)
    suspend fun clearCache()
}

/** The GPU engine behind the queue (desktop JNI or Android JNI). Never a CPU fallback: no GPU means [probe] returns null. */
interface UpscaleEngine {
    /** The GPU that would run, or null (with the reason logged) when there is no usable Vulkan GPU. */
    fun probe(): String?

    /** Upscales RGBA pixels by [scale]; [cancelled] is polled between tiles. Returns null when cancelled or failed. */
    fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, cancelled: () -> Boolean): ByteArray?

    /** Frees the GPU (called after 60 s idle and on pause). */
    fun release()
}

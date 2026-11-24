// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/profiles.rs (AGPL-3.0, same author), which ports the LSFG catalog of Enigmata-Temple
// `crates/temple-core/src/media/profiles.rs` (AGPL-3.0, same author).
package app.nijiyome.server.link.media

import kotlin.math.ceil

/** Length of one fMP4 HLS segment. */
const val SEGMENT_SECS = 2

/** Platform family: decides the hardware path (D3D11 + AMF / NVENC / QSV on Windows, VAAPI on Linux). */
enum class Platform {
    WINDOWS, LINUX;

    companion object {
        fun current(): Platform = if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) WINDOWS else LINUX
    }
}

enum class Encoder(val wire: String) {
    H264_AMF("h264_amf"),
    H264_NVENC("h264_nvenc"),
    H264_QSV("h264_qsv"),
    /** Linux VAAPI (Mesa radeonsi / iHD); needs a usable render node. */
    H264_VAAPI("h264_vaapi"),
    /** Software, preferred over libopenh264 when FFmpeg has it. */
    LIBX264("libx264"),
    LIBOPENH264("libopenh264"),
    ;

    companion object {
        val WINDOWS_PREFERENCE = listOf(H264_AMF, H264_NVENC, H264_QSV, LIBOPENH264)
        val LINUX_PREFERENCE = listOf(H264_VAAPI, H264_NVENC, LIBX264, LIBOPENH264)

        fun preference(platform: Platform): List<Encoder> = if (platform == Platform.WINDOWS) WINDOWS_PREFERENCE else LINUX_PREFERENCE
    }
}

enum class Anime4kMode(val wire: String) { A("a"), B("b"), C("c") }

enum class Anime4kVariant(val wire: String) { S("s"), M("m"), L("l") }

sealed interface Upscale {
    data object None : Upscale
    data class Anime4k(val mode: Anime4kMode, val variant: Anime4kVariant) : Upscale
}

/** LSFG multiplier: fixed, or chosen per session to bring the output close to [Auto.targetFps]. */
sealed interface LsfgMode {
    data class Fixed(val multiplier: Int) : LsfgMode
    data class Auto(val targetFps: Int) : LsfgMode

    /** Multiplier for a source of [sourceFps] (only the auto mode looks at it). */
    fun multiplier(sourceFps: Double?): Int = when (this) {
        is Fixed -> multiplier
        is Auto -> autoLsfgMultiplier(targetFps, sourceFps)
    }
}

/** Frame generation of a profile; on the wire `none` | `frc_amf` | `lsfg` (the multiplier travels in `fg_multiplier`). */
sealed interface Fg {
    val wire: String

    data object None : Fg { override val wire = "none" }
    data object FrcAmf : Fg { override val wire = "frc_amf" }
    data class Lsfg(val mode: LsfgMode) : Fg { override val wire = "lsfg" }

    fun lsfg(): LsfgMode? = (this as? Lsfg)?.mode
}

data class ProfileSpec(
    val id: String,
    val label: String,
    val upscale: Upscale,
    val fg: Fg,
    val width: Int,
    val height: Int,
    val videoKbps: Int,
)

object Profiles {
    const val P_NONE = "p-none"
    const val DEFAULT_PROFILE = P_NONE

    /** LSFG multipliers the helper accepts. */
    val LSFG_MULTIPLIERS = listOf(2, 3, 4, 5)

    /** Target of the auto profiles: the 120 Hz panel of the phone or tablet. */
    const val LSFG_AUTO_TARGET_FPS = 120

    /** Auto multiplier when the source rate is unknown or unusable: the lightest one. */
    const val LSFG_AUTO_FALLBACK_MULTIPLIER = 2

    private val A4K_1080 = Upscale.Anime4k(Anime4kMode.A, Anime4kVariant.M)
    private val A4K_LARGE = Upscale.Anime4k(Anime4kMode.A, Anime4kVariant.L)

    private fun lsfg(id: String, label: String, upscale: Upscale, mode: LsfgMode, kbps: Int) =
        ProfileSpec(id, label, upscale, Fg.Lsfg(mode), 1920, 1080, kbps)

    /** Temple's LSFG bitrates at 1080p: x2 8 Mb/s, x3 10, x4 11, x5 12, auto (nominal x4) 11. */
    val CATALOG: List<ProfileSpec> = listOf(
        ProfileSpec(P_NONE, "No enhancement", Upscale.None, Fg.None, 1920, 1080, 6000),
        ProfileSpec("p-anime4k-1080", "Anime4K to 1080p", A4K_1080, Fg.None, 1920, 1080, 8000),
        ProfileSpec("p-anime4k-1440", "Anime4K to 1440p", A4K_LARGE, Fg.None, 2560, 1440, 14000),
        ProfileSpec("p-anime4k-2160", "Anime4K to 2160p", A4K_LARGE, Fg.None, 3840, 2160, 28000),
        ProfileSpec("p-anime4k-1080-fg2x", "Anime4K 1080p + frame generation 2x (AMD)", A4K_1080, Fg.FrcAmf, 1920, 1080, 10000),
        lsfg("p-lsfg-1080-x2", "LSFG frame generation 2x, 1080p (experimental)", Upscale.None, LsfgMode.Fixed(2), 8000),
        lsfg("p-lsfg-1080-x3", "LSFG frame generation 3x, 1080p (experimental)", Upscale.None, LsfgMode.Fixed(3), 10_000),
        lsfg("p-lsfg-1080-x4", "LSFG frame generation 4x, 1080p (experimental)", Upscale.None, LsfgMode.Fixed(4), 11_000),
        lsfg("p-lsfg-1080-x5", "LSFG frame generation 5x, 1080p (experimental)", Upscale.None, LsfgMode.Fixed(5), 12_000),
        lsfg("p-lsfg-1080-auto120", "LSFG frame generation to 120 fps, 1080p (experimental)", Upscale.None, LsfgMode.Auto(LSFG_AUTO_TARGET_FPS), 11_000),
        lsfg("p-anime4k-lsfg-1080-x2", "Anime4K 1080p + LSFG 2x (experimental)", A4K_1080, LsfgMode.Fixed(2), 8000),
        lsfg("p-anime4k-lsfg-1080-x3", "Anime4K 1080p + LSFG 3x (experimental)", A4K_1080, LsfgMode.Fixed(3), 10_000),
        lsfg("p-anime4k-lsfg-1080-x4", "Anime4K 1080p + LSFG 4x (experimental)", A4K_1080, LsfgMode.Fixed(4), 11_000),
        lsfg("p-anime4k-lsfg-1080-x5", "Anime4K 1080p + LSFG 5x (experimental)", A4K_1080, LsfgMode.Fixed(5), 12_000),
        lsfg("p-anime4k-lsfg-1080-auto120", "Anime4K 1080p + LSFG to 120 fps (experimental)", A4K_1080, LsfgMode.Auto(LSFG_AUTO_TARGET_FPS), 11_000),
    )

    fun find(id: String): ProfileSpec? = CATALOG.firstOrNull { it.id == id }
}

/**
 * Auto LSFG multiplier bringing [sourceFps] close to [targetFps]: `target / source` rounded to the nearest integer (an exact tie
 * rounds down), clamped to 2..5. With 120 fps: 23.976 and 25 -> x5, 29.97 and 30 -> x4, 50 and 59.94 -> x2.
 */
fun autoLsfgMultiplier(targetFps: Int, sourceFps: Double?): Int {
    val fps = sourceFps?.takeIf { it.isFinite() && it > 0.0 } ?: return Profiles.LSFG_AUTO_FALLBACK_MULTIPLIER
    val min = Profiles.LSFG_MULTIPLIERS.first()
    val max = Profiles.LSFG_MULTIPLIERS.last()
    val nearest = ceil(targetFps.toDouble() / fps - 0.5)
    return when {
        nearest <= min -> min
        nearest >= max -> max
        else -> nearest.toInt()
    }
}

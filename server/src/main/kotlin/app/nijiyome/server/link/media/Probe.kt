// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/probe.rs (AGPL-3.0, same author); FrameRate from Enigmata-Temple media/probe.rs.
package app.nijiyome.server.link.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Source frame rate as an exact fraction (`24000/1001`), as `ffprobe` prints it. */
data class FrameRate(val num: Long, val den: Long) {
    fun fps(): Double = num.toDouble() / den.toDouble()

    /** The rate times [factor], same denominator; null on overflow of 32 bits (what FFmpeg accepts). */
    fun times(factor: Int): FrameRate? = (num * factor).takeIf { it <= UInt.MAX_VALUE.toLong() }?.let { FrameRate(it, den) }

    /** `num/den`, the form of `-framerate` and of the `fps` filter. */
    fun ffmpeg(): String = "$num/$den"

    companion object {
        /** Highest source rate accepted from `ffprobe`. */
        const val MAX_SOURCE_FPS = 240.0

        /** Rate assumed when the probe fails (the decode process forces it with `fps=`; 23.976 is anime's usual one). */
        val FALLBACK = FrameRate(24_000, 1001)

        /** `n/d` (or `n`): both positive and 1..240 fps; `0/0` (unknown) and anything else is null. */
        fun parse(text: String): FrameRate? {
            val t = text.trim()
            val parts = t.split('/')
            val (num, den) = when (parts.size) {
                1 -> (parts[0].trim().toUIntOrNull() ?: return null).toLong() to 1L
                2 -> (parts[0].trim().toUIntOrNull() ?: return null).toLong() to (parts[1].trim().toUIntOrNull() ?: return null).toLong()
                else -> return null
            }
            if (num <= 0 || den <= 0) return null
            val r = FrameRate(num, den)
            return r.takeIf { it.fps() in 1.0..MAX_SOURCE_FPS }
        }
    }
}

/** What one `ffprobe` run tells about the source. */
data class SourceInfo(
    val rate: FrameRate? = null,
    /** Container duration (`format.duration`) when known and sane (above 1 s, at most 24 h). */
    val durationSecs: Double? = null,
    /** BUG-024: `ffprobe` ran and could not read the source at all. A probe that is missing or timed out says nothing. */
    val unreadable: Boolean = false,
)

object Probe {
    /** Longest duration accepted from `ffprobe` (a broken header can claim anything). */
    const val MAX_SOURCE_SECS = 24.0 * 3600.0

    /** `ffprobe` arguments (without the program): the first video stream's rates and the container duration as JSON. */
    fun ffprobeArgs(inputUrl: String): List<String> = listOf(
        "-v", "error", "-protocol_whitelist", Args.PROTOCOL_WHITELIST, "-select_streams", "v:0",
        "-show_entries", "stream=r_frame_rate,avg_frame_rate:format=duration", "-print_format", "json", "-i", inputUrl,
    )

    private fun parseJson(stdout: String): JsonObject? = runCatching { Json.parseToJsonElement(stdout) as? JsonObject }.getOrNull()

    /** The first video stream's rate (`r_frame_rate` first, then `avg_frame_rate`). */
    fun parseRate(stdout: String): FrameRate? {
        val streams = parseJson(stdout)?.get("streams") as? JsonArray ?: return null
        val s = streams.firstOrNull() as? JsonObject ?: return null
        return listOf("r_frame_rate", "avg_frame_rate").firstNotNullOfOrNull { k ->
            s[k]?.jsonPrimitive?.contentOrNull?.let(FrameRate::parse)
        }
    }

    fun parseDuration(stdout: String): Double? {
        val format = parseJson(stdout)?.get("format") as? JsonObject ?: return null
        val d = format["duration"]?.jsonPrimitive?.contentOrNull?.trim()?.toDoubleOrNull() ?: return null
        return d.takeIf { it.isFinite() && it > 1.0 && it <= MAX_SOURCE_SECS }
    }

    fun parse(stdout: String): SourceInfo = SourceInfo(parseRate(stdout), parseDuration(stdout), unreadable = false)
}

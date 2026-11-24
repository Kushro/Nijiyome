// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import app.nijiyome.model.RankedSource
import app.nijiyome.model.ReleaseTags
import app.nijiyome.model.VideoCodec

// android wave (docs/redesign/11 §5.3, 12 §4 d.2): "Compatible with this device" rules of the source picker (pure). The decoder
// detection is per platform (`DeviceCodecs`, MediaCodecList, in androidMain).

/**
 * What the device's decoders can do for the codecs anime releases use. The plain flags count every MediaCodec decoder (software
 * ones included: Media3 can play with them); the `Hw` flags only hardware decoders (BUG-017: without them the video decodes in software
 * and "may be slow on this device").
 */
data class CodecCaps(
    val hevc: Boolean = true,
    val hevcMain10: Boolean = true,
    val av1: Boolean = true,
    val av1Main10: Boolean = true,
    val hevcHw: Boolean = hevc,
    val hevcMain10Hw: Boolean = hevcMain10,
    val av1Hw: Boolean = av1,
    val av1Main10Hw: Boolean = av1Main10,
) {
    companion object {
        /** Assume everything plays (previews, tests, detection failure). */
        val ALL = CodecCaps()
    }
}

/**
 * false when the release tags say the video needs a decoder the device lacks: HEVC (any / 10-bit), AV1 (any / 10-bit). Unknown codec or
 * bit depth = compatible (web sources have empty tags).
 */
fun isDeviceCompatible(tags: ReleaseTags, caps: CodecCaps): Boolean {
    val tenBit = tags.bitDepth == 10
    return when (tags.codec) {
        VideoCodec.HEVC -> caps.hevc && (!tenBit || caps.hevcMain10)
        VideoCodec.AV1 -> caps.av1 && (!tenBit || caps.av1Main10)
        else -> true
    }
}

/**
 * true when a hardware decoder handles the video the release tags describe. Unknown codec (web sources) or H.264 = true: every Android
 * device decodes H.264 in hardware, and nothing is known about the rest.
 */
fun decodesInHardware(tags: ReleaseTags, caps: CodecCaps): Boolean {
    val tenBit = tags.bitDepth == 10
    return when (tags.codec) {
        VideoCodec.HEVC -> caps.hevcHw && (!tenBit || caps.hevcMain10Hw)
        VideoCodec.AV1 -> caps.av1Hw && (!tenBit || caps.av1Main10Hw)
        else -> true
    }
}

/**
 * BUG-017 / BUG-020 (plan §11.2): sources this device decodes in hardware keep their ranking order and go first; the ones that need a
 * software decoder (or no decoder at all) follow, in their order. When the recommended row moves down, the first row becomes the
 * recommendation. The `picked` flag is untouched (an explicit choice still plays).
 */
fun rankForDevice(sources: List<RankedSource>, caps: CodecCaps): List<RankedSource> {
    val (fast, slow) = sources.partition { decodesInHardware(it.candidate.tags, caps) }
    if (slow.isEmpty() || fast.isEmpty()) return sources
    val hadRecommendation = sources.any { it.recommended }
    val out = fast + slow
    return if (!hadRecommendation || out.first().recommended) out else out.mapIndexed { i, s -> s.copy(recommended = i == 0) }
}

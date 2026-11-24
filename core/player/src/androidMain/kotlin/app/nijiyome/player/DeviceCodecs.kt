// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList

// android wave (docs/redesign/11 §5.3, 12 §4 d.2): "Compatible with this device" filter of the source picker. HEVC 10-bit needs a
// decoder announcing HEVCProfileMain10, AV1 needs any AV1 decoder (AV1ProfileMain10 for 10-bit). Detection is Android; the rule is pure
// (CodecCaps.kt, commonMain).

object DeviceCodecs {
    @Volatile
    private var cached: CodecCaps? = null

    /** Decoder capabilities (MediaCodecList, computed once per process; [CodecCaps.ALL] if the query fails). */
    fun capabilities(): CodecCaps = cached ?: detect().also { cached = it }

    private fun detect(): CodecCaps = try {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        fun profiles(mime: String, hardwareOnly: Boolean): List<Int>? {
            val infos = decoders.filter { info ->
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } && (!hardwareOnly || isHardware(info))
            }
            if (infos.isEmpty()) return null
            return infos.flatMap { info ->
                runCatching { info.getCapabilitiesForType(mime).profileLevels.map { it.profile } }.getOrDefault(emptyList())
            }
        }
        fun List<Int>?.hevc10() =
            this?.any { it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 || it == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 } == true
        fun List<Int>?.av1Ten() =
            this?.any { it == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 || it == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 } == true
        val hevc = profiles(MIME_HEVC, hardwareOnly = false)
        val av1 = profiles(MIME_AV1, hardwareOnly = false)
        val hevcHw = profiles(MIME_HEVC, hardwareOnly = true)
        val av1Hw = profiles(MIME_AV1, hardwareOnly = true)
        CodecCaps(
            hevc = hevc != null,
            hevcMain10 = hevc.hevc10(),
            av1 = av1 != null,
            av1Main10 = av1.av1Ten(),
            hevcHw = hevcHw != null,
            hevcMain10Hw = hevcHw.hevc10(),
            av1Hw = av1Hw != null,
            av1Main10Hw = av1Hw.av1Ten(),
        )
    } catch (e: Exception) {
        CodecCaps.ALL
    }

    /** API 29+ asks the codec; before that, the platform's software decoders are recognised by name. */
    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val n = info.name.lowercase(java.util.Locale.ROOT)
            !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.") || n.endsWith(".sw"))
        }

    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AV1 = "video/av01"
}

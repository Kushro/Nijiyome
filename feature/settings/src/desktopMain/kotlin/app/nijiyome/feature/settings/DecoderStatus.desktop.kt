// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_decoder_last
import app.nijiyome.feature.settings.resources.settings_decoder_no_vaapi
import app.nijiyome.feature.settings.resources.settings_decoder_software
import app.nijiyome.feature.settings.resources.settings_decoder_vaapi
import app.nijiyome.feature.settings.resources.settings_decoder_vaapi_no_driver
import app.nijiyome.feature.settings.resources.settings_decoder_why_disabled
import app.nijiyome.feature.settings.resources.settings_decoder_why_failed
import app.nijiyome.feature.settings.resources.settings_decoder_why_missing
import app.nijiyome.feature.settings.resources.settings_decoder_why_old
import app.nijiyome.player.mpv.DesktopVideoDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** v1.1 (F2): NVScaler (its shader is bundled with the desktop player). */
internal actual val extraPresets: List<String> = app.nijiyome.model.QualityPresets.DESKTOP_EXTRA

/**
 * v1.1 (F2): under Settings › Reproductor › Decoder, what the embedded player decodes with: VAAPI through the system libva (driver,
 * codecs) or why not, and the decoder mpv reported for the last video (`hwdec-current`). Windows (no libva): only the last video.
 */
@Composable
internal actual fun DecoderStatusSlot() {
    val libva by DesktopVideoDecoding.libva.collectAsState()
    val last by DesktopVideoDecoding.lastDecoder.collectAsState()
    // The probe already ran at startup (desktopMpvPlayerModule); this only waits for it, off the UI thread.
    val vaapi by produceState<DesktopVideoDecoding.Vaapi?>(null, libva) { value = withContext(Dispatchers.IO) { DesktopVideoDecoding.vaapi } }
    val line = when (libva.source) {
        DesktopVideoDecoding.LibvaSource.SYSTEM -> {
            val v = vaapi
            if (v != null && vaapiCodecs(v).isNotEmpty()) {
                stringResource(Res.string.settings_decoder_vaapi, libva.version.orEmpty(), gpuName(v.vendor), vaapiCodecs(v).joinToString(", "))
            } else {
                stringResource(Res.string.settings_decoder_vaapi_no_driver, libva.version.orEmpty())
            }
        }
        DesktopVideoDecoding.LibvaSource.BUNDLED -> {
            val why = when (libva.why) {
                DesktopVideoDecoding.Why.TOO_OLD -> stringResource(Res.string.settings_decoder_why_old, libva.version.orEmpty())
                DesktopVideoDecoding.Why.DISABLED -> stringResource(Res.string.settings_decoder_why_disabled)
                DesktopVideoDecoding.Why.LOAD_FAILED -> stringResource(Res.string.settings_decoder_why_failed)
                DesktopVideoDecoding.Why.MISSING, null -> stringResource(Res.string.settings_decoder_why_missing)
            }
            stringResource(Res.string.settings_decoder_no_vaapi, why)
        }
        DesktopVideoDecoding.LibvaSource.NONE -> null
    }
    val lastLine = last?.let { d ->
        stringResource(Res.string.settings_decoder_last, if (d == "no") stringResource(Res.string.settings_decoder_software) else d)
    }
    val text = listOfNotNull(line, lastLine).joinToString(" ")
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Codec names (technical, not translated) VAAPI decodes here. */
internal fun vaapiCodecs(v: DesktopVideoDecoding.Vaapi): List<String> = listOfNotNull(
    "H.264".takeIf { v.h264 },
    "HEVC".takeIf { v.hevc },
    "HEVC 10 bits".takeIf { v.hevcMain10 },
    "AV1".takeIf { v.av1 },
    "VP9".takeIf { v.vp9 },
)

/** "Mesa Gallium driver 26.3.1 for AMD Radeon RX 9070 XT (radeonsi, ...)" -> "AMD Radeon RX 9070 XT"; the whole string otherwise. */
internal fun gpuName(vendor: String?): String {
    val v = vendor?.trim().orEmpty()
    if (v.isEmpty()) return "VAAPI"
    val afterFor = v.substringAfter(" for ", v)
    return afterFor.substringBefore(" (").trim().ifEmpty { v }
}

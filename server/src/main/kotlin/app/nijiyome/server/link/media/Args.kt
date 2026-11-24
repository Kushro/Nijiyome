// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/args.rs (AGPL-3.0, same author); the chains follow Enigmata-Temple media/args.rs.
package app.nijiyome.server.link.media

import java.io.File
import kotlin.math.ceil

/** Hardware path of the graph, chosen per platform by [Environment.backend]. */
sealed interface Backend {
    /** Windows: `-hwaccel d3d11va` (+ D3D11 / AMF devices for `h264_amf`). */
    data object D3d11 : Backend

    /** Linux: VAAPI on [device]; [decode] = FFmpeg lists the `vaapi` hwaccel. */
    data class Vaapi(val device: String, val decode: Boolean) : Backend

    /** No hardware decode; only software (or NVENC) encoders. */
    data object Software : Backend
}

data class PipelineParams(
    val profile: ProfileSpec,
    val encoder: Encoder,
    val backend: Backend,
    /** `http://127.0.0.1:<port>/u/<token>/...` from the SSRF fetcher (never an external URL). */
    val inputUrl: String,
    /** `http://127.0.0.1:<port>/sink/<token>/<generation>`: where FFmpeg PUTs init / segments. */
    val sinkBase: String,
    val startSegment: Int,
    val audioTrack: Int,
    /** Relative `.hook` name for Anime4K profiles. */
    val shaderFile: String?,
    /** D3D11 adapter index used for decode / AMF. */
    val adapter: Int = 0,
)

class ArgsException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { MISSING_SHADER, NEEDS_AMF, ENCODER_BACKEND, NOT_LSFG, IS_LSFG, DLL_NOT_ABSOLUTE, RATE_OVERFLOW }
}

/** The three processes of an LSFG generation: FFmpeg A (decode -> raw RGBA), `temple-lsfg`, FFmpeg B (encode -> HLS PUT). */
class LsfgArgs(
    val decode: List<String>,
    val helper: List<String>,
    val encode: List<String>,
    val multiplier: Int,
    val outputRate: FrameRate,
) {
    /** No URLs (they carry tokens) and no library path. */
    override fun toString(): String = "LsfgArgs(multiplier=$multiplier, outputRate=${outputRate.ffmpeg()})"
}

/**
 * FFmpeg argument vectors (no shell, ever). One generation = one FFmpeg producing fMP4 HLS segments `seg_<k>.m4s` that it PUTs to
 * the session's loopback sink starting at `start_segment`, or for LSFG profiles three processes ([buildLsfg]) whose last one PUTs.
 * `-protocol_whitelist http,tcp,crypto` goes before the input AND before the output (`crypto` is needed for `#EXT-X-KEY` AES-128).
 */
object Args {
    const val PROTOCOL_WHITELIST = "http,tcp,crypto"
    private const val D3D11_DEVICE = "dx"
    private const val AMF_DEVICE = "amf0"
    /** `frc_amf` with `use_future_frame=0` holds back 1 input frame; 2 output frames are prepended and dropped by `trim`. */
    private const val FRC_LEAD_FRAMES = 2
    /** Upper bound of the source frame rate, only used to size `-g` (keyframes are forced every segment anyway). */
    private const val MAX_FPS = 60
    /** MaxMBPS of H.264 level 4.2 (1080p at 64 fps): above it a level is declared (LSFG). */
    private const val LEVEL_4_2_MBPS = 522_240L
    private val HEAD = listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y")

    private fun vaapiDeviceArg(device: String) = "vaapi=${Vaapi.DEVICE_NAME}:$device"

    /** The encoder must match the backend: AMF only over D3D11, VAAPI only over a VAAPI node. */
    private fun checkEncoder(p: PipelineParams) {
        val ok = when (p.encoder) {
            Encoder.H264_AMF -> p.backend is Backend.D3d11
            Encoder.H264_VAAPI -> p.backend is Backend.Vaapi
            else -> true
        }
        if (!ok) throw ArgsException(ArgsException.Reason.ENCODER_BACKEND)
    }

    private fun MutableList<String>.decode(backend: Backend) {
        when (backend) {
            Backend.D3d11 -> addAll(listOf("-hwaccel", "d3d11va"))
            is Backend.Vaapi -> if (backend.decode) addAll(listOf("-hwaccel", "vaapi", "-hwaccel_device", Vaapi.DEVICE_NAME))
            Backend.Software -> Unit
        }
    }

    /** `-init_hw_device` set of the single-process graph and of the LSFG encode process ([decoding] = it also decodes). */
    private fun MutableList<String>.devices(p: PipelineParams, decoding: Boolean, frc: Boolean) {
        when (val b = p.backend) {
            Backend.D3d11 -> if (p.encoder == Encoder.H264_AMF) {
                addAll(listOf("-init_hw_device", "d3d11va=$D3D11_DEVICE:${p.adapter}", "-init_hw_device", "amf=$AMF_DEVICE@$D3D11_DEVICE"))
                if (frc) addAll(listOf("-filter_hw_device", AMF_DEVICE))
            }
            is Backend.Vaapi -> {
                val encode = p.encoder == Encoder.H264_VAAPI
                if (encode || (decoding && b.decode)) addAll(listOf("-init_hw_device", vaapiDeviceArg(b.device)))
                // `hwupload` before `h264_vaapi` needs the filter device
                if (encode) addAll(listOf("-filter_hw_device", Vaapi.DEVICE_NAME))
            }
            Backend.Software -> Unit
        }
    }

    private fun videoFilter(p: PipelineParams): String {
        val w = p.profile.width
        val h = p.profile.height
        var f = when (p.profile.upscale) {
            Upscale.None -> "scale=w='min(iw,$w)':h='min(ih,$h)':force_original_aspect_ratio=decrease:force_divisible_by=2,format=nv12"
            is Upscale.Anime4k -> {
                val shader = p.shaderFile ?: throw ArgsException(ArgsException.Reason.MISSING_SHADER)
                var s = "libplacebo=w=$w:h=$h:force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12:custom_shader_path=$shader"
                if (p.profile.fg == Fg.FrcAmf) {
                    if (p.encoder != Encoder.H264_AMF) throw ArgsException(ArgsException.Reason.NEEDS_AMF)
                    val lead = FRC_LEAD_FRAMES
                    s += ",tpad=stop=1:stop_mode=clone,hwupload,frc_amf=profile=high:use_future_frame=0:fallback_mode=dup," +
                        "trim=start_frame=$lead,settb=AVTB,setpts=STARTPTS+(N-$lead)/(FRAME_RATE*TB)"
                }
                s
            }
        }
        if (p.encoder == Encoder.H264_VAAPI) f += ",hwupload"
        return f
    }

    /** `-level` for [width]x[height] at [fps]: none up to 4.2, then the lowest level whose MaxMBPS fits. */
    fun h264Level(width: Int, height: Int, fps: Double): String? {
        val macroblocks = ((width + 15) / 16).toLong() * ((height + 15) / 16).toLong()
        val perSecond = ceil(macroblocks * fps).toLong()
        return when {
            perSecond <= LEVEL_4_2_MBPS -> null
            perSecond <= 983_040 -> "5.1"
            perSecond <= 2_073_600 -> "5.2"
            perSecond <= 4_177_920 -> "6.0"
            perSecond <= 8_355_840 -> "6.1"
            else -> "6.2"
        }
    }

    private fun encoderArgs(encoder: Encoder, videoKbps: Int, gop: Int, level: String?): List<String> {
        val kbps = "${videoKbps}k"
        val keys = "expr:gte(t,n_forced*$SEGMENT_SECS)"
        val lvl = when {
            encoder == Encoder.LIBOPENH264 || level == null -> emptyList()
            // `h264_vaapi` takes the numeric level_idc (`5.1` -> `51`)
            encoder == Encoder.H264_VAAPI -> listOf("-level", level.replace(".", ""))
            else -> listOf("-level", level)
        }
        val a = when (encoder) {
            Encoder.H264_AMF -> mutableListOf("-c:v", "h264_amf", "-usage", "transcoding", "-quality", "balanced", "-rc", "vbr_peak", "-b:v", kbps, "-maxrate", kbps, "-bf", "0")
            Encoder.H264_NVENC -> mutableListOf("-c:v", "h264_nvenc", "-preset", "p4", "-rc", "vbr", "-b:v", kbps, "-maxrate", kbps, "-bf", "0")
            Encoder.H264_QSV -> mutableListOf("-c:v", "h264_qsv", "-preset", "medium", "-b:v", kbps, "-maxrate", kbps, "-bf", "0")
            Encoder.H264_VAAPI -> mutableListOf("-c:v", "h264_vaapi", "-rc_mode", "VBR", "-b:v", kbps, "-maxrate", kbps, "-bf", "0")
            Encoder.LIBX264 -> mutableListOf(
                "-c:v", "libx264", "-preset", "veryfast", "-b:v", kbps, "-maxrate", kbps, "-bufsize", "${videoKbps * 2}k", "-pix_fmt", "yuv420p", "-bf", "0",
            )
            Encoder.LIBOPENH264 -> return listOf("-c:v", "libopenh264", "-b:v", kbps, "-pix_fmt", "yuv420p", "-force_key_frames", keys)
        }
        a.addAll(lvl)
        a.addAll(listOf("-g", gop.toString()))
        when (encoder) {
            Encoder.H264_AMF -> a.addAll(listOf("-forced_idr", "1"))
            Encoder.H264_NVENC -> a.addAll(listOf("-forced-idr", "1"))
            else -> Unit
        }
        a.addAll(listOf("-force_key_frames", keys))
        return a
    }

    /** Audio encode, `-output_ts_offset` and the HLS PUT output (shared by both builders). */
    private fun MutableList<String>.tail(p: PipelineParams) {
        addAll(listOf("-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2"))
        if (p.startSegment > 0) addAll(listOf("-output_ts_offset", (p.startSegment * SEGMENT_SECS).toString()))
        addAll(
            listOf(
                "-f", "hls", "-hls_time", SEGMENT_SECS.toString(), "-hls_segment_type", "fmp4", "-hls_flags", "independent_segments",
                "-hls_list_size", "6", "-start_number", p.startSegment.toString(), "-hls_fmp4_init_filename", "init.mp4",
                "-hls_segment_options", "movflags=+frag_discont:use_editlist=0:avoid_negative_ts=disabled",
                "-hls_segment_filename", "${p.sinkBase}/seg_%d.m4s", "-method", "PUT",
                "-protocol_whitelist", PROTOCOL_WHITELIST, "${p.sinkBase}/live.m3u8",
            ),
        )
    }

    /** `-ss k*S` (input option) for a generation starting at k > 0: `-ss 0` damages the first audio packet. */
    private fun MutableList<String>.seek(p: PipelineParams) {
        if (p.startSegment > 0) addAll(listOf("-ss", (p.startSegment * SEGMENT_SECS).toString()))
    }

    /** Complete argument vector (without the program) of a single-process profile. */
    fun build(p: PipelineParams): List<String> {
        if (p.profile.fg.lsfg() != null) throw ArgsException(ArgsException.Reason.IS_LSFG)
        checkEncoder(p)
        val a = HEAD.toMutableList()
        a.devices(p, decoding = true, frc = p.profile.fg == Fg.FrcAmf)
        a.decode(p.backend)
        a.seek(p)
        a.addAll(listOf("-protocol_whitelist", PROTOCOL_WHITELIST, "-i", p.inputUrl))
        a.addAll(listOf("-map", "0:v:0", "-map", "0:a:${p.audioTrack}?"))
        a.addAll(listOf("-vf", videoFilter(p)))
        // the graph fixes the cadence; keep source pts (and their relation to the audio)
        if (p.encoder == Encoder.H264_AMF) a.addAll(listOf("-fps_mode", "passthrough"))
        a.addAll(encoderArgs(p.encoder, p.profile.videoKbps, MAX_FPS * SEGMENT_SECS, null))
        a.tail(p)
        return a
    }

    /**
     * Raw frames must be exactly WxH: without upscale the picture is fitted and centred with bars; with Anime4K it is first
     * padded to the output aspect ratio and libplacebo scales the whole frame.
     */
    private fun lsfgScale(p: PipelineParams): String {
        val w = p.profile.width
        val h = p.profile.height
        return when (p.profile.upscale) {
            Upscale.None -> "scale=w=$w:h=$h:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=$w:$h:(ow-iw)/2:(oh-ih)/2"
            is Upscale.Anime4k -> {
                val shader = p.shaderFile ?: throw ArgsException(ArgsException.Reason.MISSING_SHADER)
                "pad=w='2*ceil(max(iw,ih*$w/$h)/2)':h='2*ceil(max(ih,iw*$h/$w)/2)':x='(ow-iw)/2':y='(oh-ih)/2'," +
                    "libplacebo=w=$w:h=$h:format=nv12:custom_shader_path=$shader"
            }
        }
    }

    /**
     * The three LSFG processes. [sourceRate] is the probed rate (or the fallback): FFmpeg A forces it with `fps=<rate>:start_time=0`,
     * so FFmpeg B can trust `-framerate`. [dll] is the absolute path of `Lossless.dll`; only the helper gets it.
     */
    fun buildLsfg(p: PipelineParams, sourceRate: FrameRate, dll: File): LsfgArgs {
        val mode = p.profile.fg.lsfg() ?: throw ArgsException(ArgsException.Reason.NOT_LSFG)
        if (!dll.isAbsolute) throw ArgsException(ArgsException.Reason.DLL_NOT_ABSOLUTE)
        checkEncoder(p)
        val multiplier = mode.multiplier(sourceRate.fps())
        val outRate = sourceRate.times(multiplier) ?: throw ArgsException(ArgsException.Reason.RATE_OVERFLOW)
        val w = p.profile.width
        val h = p.profile.height

        val d = HEAD.toMutableList()
        when (val b = p.backend) {
            Backend.D3d11 -> d.addAll(listOf("-hwaccel", "d3d11va"))
            is Backend.Vaapi -> if (b.decode) {
                d.addAll(listOf("-init_hw_device", vaapiDeviceArg(b.device)))
                d.decode(b)
            }
            Backend.Software -> Unit
        }
        d.seek(p)
        d.addAll(listOf("-protocol_whitelist", PROTOCOL_WHITELIST, "-i", p.inputUrl, "-map", "0:v:0"))
        d.addAll(listOf("-vf", "fps=${sourceRate.ffmpeg()}:start_time=0,${lsfgScale(p)},format=rgba", "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"))

        val helper = listOf("--dll", dll.path, "--width", w.toString(), "--height", h.toString(), "--multiplier", multiplier.toString())

        val e = HEAD.toMutableList()
        e.devices(p, decoding = false, frc = false)
        e.addAll(listOf("-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${w}x$h", "-framerate", outRate.ffmpeg(), "-i", "pipe:0"))
        e.seek(p)
        e.addAll(listOf("-protocol_whitelist", PROTOCOL_WHITELIST, "-i", p.inputUrl))
        e.addAll(listOf("-map", "0:v:0", "-map", "1:a:${p.audioTrack}?"))
        e.addAll(listOf("-vf", if (p.encoder == Encoder.H264_VAAPI) "format=nv12,hwupload" else "format=nv12", "-fps_mode", "passthrough"))
        val gop = maxOf(ceil(outRate.fps()).toInt(), 1) * SEGMENT_SECS
        e.addAll(encoderArgs(p.encoder, p.profile.videoKbps, gop, h264Level(w, h, outRate.fps())))
        e.tail(p)
        return LsfgArgs(d, helper, e, multiplier, outRate)
    }

    /** The Anime4K `.hook` for [profile], written into [workdir]; null for profiles without upscale. */
    fun prepareShader(profile: ProfileSpec, shaders: File?, workdir: File): String? = when (val u = profile.upscale) {
        Upscale.None -> null
        is Upscale.Anime4k -> {
            if (shaders == null) throw java.io.IOException("Anime4K shaders are not installed")
            Anime4k.writeShader(shaders, workdir, u.mode, u.variant)
        }
    }
}

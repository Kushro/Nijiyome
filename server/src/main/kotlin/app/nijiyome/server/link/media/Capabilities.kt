// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/capabilities.rs (AGPL-3.0, same author).
package app.nijiyome.server.link.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val MAX_RAM_BYTES: Long = 512L * 1024 * 1024
const val SEGMENT_WAIT_SECS = 15L

/** Runs an external program and returns its stdout (injected so detection is testable without FFmpeg). */
fun interface CommandRunner {
    suspend fun run(program: File, args: List<String>): String
}

object SystemRunner : CommandRunner {
    override suspend fun run(program: File, args: List<String>): String = withContext(Dispatchers.IO) {
        BoundedProcess.run(listOf(program.path) + args, 4 * 1024 * 1024, 15_000).orEmpty()
    }
}

/**
 * A short external program whose stdout is read whole, bounded in time: a watchdog kills it (and what it started) at [run]'s
 * deadline, which closes its stdout, so a hung `ffprobe` / `ffmpeg` (a damaged file, a stuck driver) never blocks the reader forever.
 */
object BoundedProcess {
    /** stdout (at most [maxBytes]); null when it did not end within [timeoutMs] or could not start. */
    fun run(command: List<String>, maxBytes: Int, timeoutMs: Long): String? {
        val p = runCatching { ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start() }.getOrNull() ?: return null
        runCatching { p.outputStream.close() }
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = Thread {
            try {
                if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    timedOut.set(true)
                    p.descendants().forEach { it.destroyForcibly() }
                    p.destroyForcibly()
                }
            } catch (_: InterruptedException) {
            }
        }.apply {
            isDaemon = true
            name = "bounded-process"
            start()
        }
        val out = try {
            p.inputStream.use { it.readNBytes(maxBytes) }
        } catch (_: java.io.IOException) {
            ByteArray(0)
        }
        // more output than wanted, or done reading: the program has nothing more to give
        if (p.isAlive && !p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            timedOut.set(true)
            p.destroyForcibly()
        }
        watchdog.interrupt()
        return if (timedOut.get()) null else out.decodeToString()
    }
}

data class Detected(
    /** Encoders of this platform's preference list that FFmpeg has, in that order. */
    val encoders: List<Encoder> = emptyList(),
    val libplacebo: Boolean = false,
    val frcAmf: Boolean = false,
    /** `ffmpeg -hwaccels` lists `vaapi`. */
    val hwaccelVaapi: Boolean = false,
)

object Detection {
    /** `ffmpeg -encoders`: lines like ` V....D h264_amf   AMD AMF H.264 Encoder`; only the platform's own list is kept. */
    fun parseEncoders(platform: Platform, out: String): List<Encoder> {
        val names = out.lines().mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1) }.toSet()
        return Encoder.preference(platform).filter { it.wire in names }
    }

    /** `ffmpeg -hwaccels`: `Hardware acceleration methods:` then one name per line. */
    fun parseHasHwaccel(out: String, name: String): Boolean =
        out.lines().dropWhile { "acceleration methods" !in it }.drop(1).any { it.trim() == name }

    /** `ffmpeg -filters`: lines like ` ... libplacebo   V->V  ...`. */
    fun parseHasFilter(out: String, name: String): Boolean = out.lines().any { it.trim().split(Regex("\\s+")).getOrNull(1) == name }

    suspend fun detect(platform: Platform, ffmpeg: File, runner: CommandRunner): Detected {
        val enc = runCatching { runner.run(ffmpeg, listOf("-hide_banner", "-encoders")) }.getOrDefault("")
        val fil = runCatching { runner.run(ffmpeg, listOf("-hide_banner", "-filters")) }.getOrDefault("")
        val hw = if (platform == Platform.LINUX) runCatching { runner.run(ffmpeg, listOf("-hide_banner", "-hwaccels")) }.getOrDefault("") else ""
        return Detected(parseEncoders(platform, enc), parseHasFilter(fil, "libplacebo"), parseHasFilter(fil, "frc_amf"), parseHasHwaccel(hw, "vaapi"))
    }
}

data class GpuInfo(val vendor: Vendor, val name: String, val vulkan: Boolean) {
    companion object {
        /** Vendor guessed from the hardware encoders present when the app does not know better. */
        fun guess(d: Detected): GpuInfo {
            val vendor = when {
                Encoder.H264_AMF in d.encoders -> Vendor.AMD
                Encoder.H264_NVENC in d.encoders -> Vendor.NVIDIA
                Encoder.H264_QSV in d.encoders -> Vendor.INTEL
                else -> Vendor.UNKNOWN
            }
            return GpuInfo(vendor, "", d.libplacebo)
        }

        /** [guess], except that on Linux with `h264_vaapi` the render node's PCI vendor is the GPU's. */
        fun guessWith(d: Detected, node: RenderNode?): GpuInfo {
            val g = guess(d)
            return if (node != null && node.usable() && node.vendor != Vendor.UNKNOWN && Encoder.H264_VAAPI in d.encoders) g.copy(vendor = node.vendor) else g
        }
    }
}

enum class Sustain { YES, NO, UNKNOWN }

/** Everything that decides which profiles can run. */
data class Environment(
    val detected: Detected,
    val gpu: GpuInfo,
    val shadersOk: Boolean,
    val platform: Platform = Platform.current(),
    /** Linux VAAPI render node (null on Windows or without `/dev/dri`). */
    val renderNode: RenderNode? = null,
    val lsfg: LsfgStatus = LsfgStatus.DISABLED,
    /** The `L` (or `VL`) Anime4K networks of the 1440p / 2160p profiles are present ([shadersOk] covers only `M`). */
    val anime4kLargeOk: Boolean = shadersOk,
    /**
     * An NVIDIA driver is present (Linux: `/dev/nvidiactl`). Distribution FFmpeg builds list `h264_nvenc` on every machine; without
     * this check a PC with no VA-API node and no NVIDIA card would pick NVENC and fail every session instead of encoding in software.
     * (Difference from tk-link, which trusts the `-encoders` list.)
     */
    val nvencDevice: Boolean = true,
) {
    private fun vaapiNode(): RenderNode? = renderNode?.takeIf { platform == Platform.LINUX && it.usable() }

    /** Encoders that can run here, in preference order: `h264_vaapi` needs a usable render node, `h264_nvenc` an NVIDIA driver on Linux. */
    fun usableEncoders(): List<Encoder> = detected.encoders.filter {
        (it != Encoder.H264_VAAPI || vaapiNode() != null) && (it != Encoder.H264_NVENC || platform != Platform.LINUX || nvencDevice)
    }

    fun encoder(): Encoder? = usableEncoders().firstOrNull()

    /** D3D11 on Windows; VAAPI on Linux when the node is usable and FFmpeg can decode or encode with it; software otherwise. */
    fun backend(): Backend = when (platform) {
        Platform.WINDOWS -> Backend.D3d11
        Platform.LINUX -> {
            val n = vaapiNode()
            if (n != null && (detected.hwaccelVaapi || Encoder.H264_VAAPI in detected.encoders)) Backend.Vaapi(n.device, detected.hwaccelVaapi) else Backend.Software
        }
    }

    private fun anime4kOk(): Boolean = detected.libplacebo && gpu.vulkan && shadersOk

    fun profileAvailable(p: ProfileSpec): Boolean {
        val up = p.upscale
        if (up is Upscale.Anime4k && up.variant == Anime4kVariant.L && !anime4kLargeOk) return false
        return when (p.fg) {
            Fg.None -> if (up == Upscale.None) true else anime4kOk() && encoder() != null
            Fg.FrcAmf -> up is Upscale.Anime4k && detected.libplacebo && gpu.vulkan && shadersOk && gpu.vendor == Vendor.AMD &&
                Encoder.H264_AMF in detected.encoders && detected.frcAmf
            is Fg.Lsfg -> lsfg.isAvailable && encoder() != null && (up == Upscale.None || anime4kOk())
        }
    }

    /** Encoder to use for [p] (frame generation by AMF needs AMF). */
    fun encoderFor(p: ProfileSpec): Encoder? = if (p.fg == Fg.FrcAmf) Encoder.H264_AMF else encoder()
}

/** Stored sustain verdicts (`profile id -> sustained`, `bench.json`). */
class BenchStore(private val file: File? = null) {
    private val results = ConcurrentHashMap<String, Boolean>()

    init {
        file?.takeIf { it.isFile }?.let { f ->
            runCatching {
                (Json.parseToJsonElement(f.readText()) as JsonObject).forEach { (k, v) -> (v as? JsonPrimitive)?.booleanOrNull?.let { results[k] = it } }
            }
        }
    }

    @Synchronized
    fun record(profile: String, sustained: Boolean) {
        results[profile] = sustained
        file?.let { f ->
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(Json.encodeToString(JsonObject.serializer(), JsonObject(results.toSortedMap().mapValues { JsonPrimitive(it.value) })))
            }
        }
    }

    fun get(profile: String): Sustain = when (results[profile]) {
        true -> Sustain.YES
        false -> Sustain.NO
        null -> Sustain.UNKNOWN
    }
}

/** `GET /v1/capabilities` (contract `Capabilities`; `gpu`, `encoders`, `limits`, `lsfg` are fields the phone ignores). */
object CapabilitiesJson {
    fun build(env: Environment, bench: BenchStore, instanceId: String, serverName: String, clientFgAllowed: Boolean, serverVersion: String): JsonObject =
        buildJsonObject {
            put("contract", "v1")
            put("server_version", serverVersion)
            put("instance_id", instanceId)
            put("server_name", serverName)
            putJsonObject("gpu") {
                put("vendor", env.gpu.vendor.wire)
                put("name", env.gpu.name)
                put("vulkan", env.gpu.vulkan)
            }
            put("encoders", buildJsonArray { env.usableEncoders().forEach { add(JsonPrimitive(it.wire)) } })
            put("default_profile", Profiles.DEFAULT_PROFILE)
            put(
                "profiles",
                buildJsonArray {
                    Profiles.CATALOG.filter(env::profileAvailable).forEach { p -> add(profileJson(env, bench, p)) }
                },
            )
            put("client_fg", if (clientFgAllowed) "allowed" else "suppressed")
            putJsonObject("limits") {
                put("max_ram_bytes", MAX_RAM_BYTES)
                put("segment_wait_secs", SEGMENT_WAIT_SECS)
            }
            put("lsfg", lsfgJson(env.lsfg.capability()))
        }

    fun profileJson(env: Environment, bench: BenchStore, p: ProfileSpec): JsonObject = buildJsonObject {
        put("id", p.id)
        put("label", p.label)
        put("upscale", if (p.upscale == Upscale.None) "none" else "anime4k")
        put("fg", p.fg.wire)
        putJsonObject("output") {
            put("width", p.width)
            put("height", p.height)
        }
        val sustain = if (p.id == Profiles.DEFAULT_PROFILE) Sustain.YES else bench.get(p.id)
        put(
            "measured_sustain",
            when (sustain) {
                Sustain.YES -> JsonPrimitive(true)
                Sustain.NO -> JsonPrimitive(false)
                Sustain.UNKNOWN -> JsonPrimitive("unknown")
            },
        )
        put("encoder", env.encoderFor(p)?.wire ?: "")
        when (val m = p.fg.lsfg()) {
            is LsfgMode.Fixed -> put("fg_multiplier", m.multiplier)
            is LsfgMode.Auto -> put("fg_target_fps", m.targetFps)
            null -> Unit
        }
    }

    fun lsfgJson(c: LsfgCapability): JsonObject = buildJsonObject {
        put("available", c.available)
        put("experimental", c.experimental)
        put("multipliers", buildJsonArray { c.multipliers.forEach { add(JsonPrimitive(it)) } })
        put("auto_target_fps", c.autoTargetFps)
        c.reason?.let { put("reason", it) }
    }
}

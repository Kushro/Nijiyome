// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.util.function.BooleanSupplier

/**
 * The four JNI calls of `libnijiyome_upscale` (native/upscale/src/upscale_jni.cpp), behind an interface so the engine logic is shared:
 * desktop implements it in :core:upscale desktopMain, Android in :core:upscale-native (each next to its own `UpscaleNative` object).
 */
interface UpscaleNativeBridge {
    /** Loads the library once; false (with the reason logged) when it is missing or does not load. */
    fun load(): Boolean
    fun probe(preferredKey: String?): String
    fun init(deviceIndex: Int, modelDir: String): Int
    fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, tile: Int, cancel: BooleanSupplier?): ByteArray?
    fun release()
}

/** One Vulkan device as the probe lists it (for the log and the desktop GPU match). */
data class UpscaleProbeDevice(val index: Int, val name: String, val type: String, val uuid: String, val usable: Boolean)

/** The parsed `nProbe` JSON (fields as documented in upscale_jni.cpp). */
data class UpscaleProbe(
    val ok: Boolean,
    val index: Int,
    val device: String,
    val type: String,
    val vendorId: Int,
    val deviceId: Int,
    val uuid: String,
    val heapMb: Int,
    val fp16: Boolean,
    val driver: String,
    val preferredMatched: Boolean,
    val error: String,
    val detail: String,
    val devices: List<UpscaleProbeDevice>,
) {
    companion object {
        fun parse(raw: String): UpscaleProbe? = runCatching {
            val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return null
            fun p(o: JsonObject, k: String) = o[k] as? JsonPrimitive
            fun s(o: JsonObject, k: String) = p(o, k)?.contentOrNull.orEmpty()
            fun i(o: JsonObject, k: String, d: Int = 0) = p(o, k)?.intOrNull ?: d
            fun b(o: JsonObject, k: String) = p(o, k)?.booleanOrNull == true
            val devices = (obj["devices"] as? JsonArray).orEmpty().mapNotNull { e ->
                val d = e as? JsonObject ?: return@mapNotNull null
                UpscaleProbeDevice(i(d, "index", -1), s(d, "name"), s(d, "type"), s(d, "uuid"), b(d, "usable"))
            }
            UpscaleProbe(
                ok = b(obj, "ok"), index = i(obj, "index", -1), device = s(obj, "device"), type = s(obj, "type"),
                vendorId = i(obj, "vendorId"), deviceId = i(obj, "deviceId"), uuid = s(obj, "uuid"), heapMb = i(obj, "heapMb"),
                fp16 = b(obj, "fp16"), driver = s(obj, "driver"), preferredMatched = b(obj, "preferredMatched"),
                error = s(obj, "error"), detail = s(obj, "detail"), devices = devices,
            )
        }.getOrNull()
    }
}

/**
 * GPUs the engine refuses before touching them (plan §8, R1), in the spirit of `FgPolicy`: ncnn's Vulkan path is reported broken on
 * some Adreno drivers (RealSR-NCNN-Android notes problems on Adreno 840) and the pre-600 Adreno drivers are Vulkan 1.0 era. The list
 * is meant to grow from the phone tests; a refused GPU means the feature is off, never a CPU fallback.
 */
object UpscaleGpuPolicy {
    private val ADRENO_MODEL = Regex("adreno\\D*(\\d+)", RegexOption.IGNORE_CASE)

    /** Adreno models with a reported ncnn problem. */
    private val BLOCKED_ADRENO = setOf(840)

    /** Older Adreno than this is refused. */
    const val MIN_ADRENO_MODEL = 600

    /** The reason [deviceName] is refused, or null when it may run. */
    fun blockedReason(deviceName: String?): String? {
        val model = ADRENO_MODEL.find(deviceName ?: return null)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return when {
            model in BLOCKED_ADRENO -> "Adreno $model is on the upscale blocklist"
            model < MIN_ADRENO_MODEL -> "Adreno $model is older than $MIN_ADRENO_MODEL"
            else -> null
        }
    }
}

/**
 * [UpscaleEngine] over the JNI library. [probe] loads the library, asks the native GPU pick (the shared `gpu_pick.h` policy; on
 * desktop [preferredKey] may name the GPU that draws the app) and applies [UpscaleGpuPolicy]; the model folder and `nInit` come on
 * the first [upscale] (and again after every [release]), so a broken driver shows as ENGINE_FAILED in the queue. Every call runs on
 * the queue's single worker thread, as the native mutex expects.
 */
class NativeUpscaleEngine(
    private val bridge: UpscaleNativeBridge,
    /** The folder holding realesr-animevideov3-x{2,3,4}.param and the x4 .bin (installed on first use where needed); null = missing. */
    private val modelDir: () -> File?,
    /** Desktop: the device UUID / LUID to prefer, or null for the shared policy. */
    private val preferredKey: (UpscaleProbe) -> String? = { null },
    private val log: (String) -> Unit = { System.err.println("[nijiyome-upscale] $it") },
) : UpscaleEngine {
    private var probed: UpscaleProbe? = null
    private var initialized = false
    private var initFailed = false

    override fun probe(): String? {
        if (!bridge.load()) return null
        val first = UpscaleProbe.parse(runCatching { bridge.probe(null) }.getOrElse { log("probe threw: ${it.message}"); return null })
            ?: run {
                log("probe returned unreadable JSON")
                return null
            }
        val key = if (first.ok) runCatching { preferredKey(first) }.getOrNull()?.takeIf { it.isNotBlank() } else null
        val chosen = key?.let { k -> UpscaleProbe.parse(runCatching { bridge.probe(k) }.getOrNull().orEmpty()) } ?: first
        log(
            "devices: " + chosen.devices.joinToString { "#${it.index} ${it.name} (${it.type}${if (it.usable) "" else ", unusable"})" },
        )
        if (!chosen.ok) {
            log("no usable GPU: ${chosen.error} ${chosen.detail}".trim())
            return null
        }
        UpscaleGpuPolicy.blockedReason(chosen.device)?.let { why ->
            log("GPU refused: $why")
            return null
        }
        log(
            "GPU: ${chosen.device} (${chosen.type}, ${chosen.driver}, ${chosen.heapMb} MB, fp16=${chosen.fp16}" +
                (if (key != null) ", preferred=${chosen.preferredMatched}" else "") + ")",
        )
        probed = chosen
        return chosen.device
    }

    override fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, cancelled: () -> Boolean): ByteArray? {
        if (!ensureInit()) return null
        return runCatching {
            bridge.upscale(rgba, width, height, scale, 0, BooleanSupplier { cancelled() })
        }.onFailure { log("upscale threw: ${it.message}") }.getOrNull()
    }

    override fun release() {
        if (!initialized) return
        initialized = false
        runCatching { bridge.release() }
    }

    private fun ensureInit(): Boolean {
        if (initialized) return true
        if (initFailed) return false
        val probe = probed ?: return false
        val dir = runCatching { modelDir() }.getOrNull()
        if (dir == null) {
            log("model files missing")
            initFailed = true
            return false
        }
        val code = runCatching { bridge.init(probe.index, dir.absolutePath) }.getOrElse { log("init threw: ${it.message}"); -1 }
        if (code != 0) {
            log("init failed: $code")
            initFailed = true
            return false
        }
        initialized = true
        return true
    }

    companion object {
        /** The files ncnn needs: the three .param and the x4 .bin (x2 and x3 reuse it, upscale_engine.cpp). */
        val MODEL_FILES: List<String> = listOf(
            "realesr-animevideov3-x2.param",
            "realesr-animevideov3-x3.param",
            "realesr-animevideov3-x4.param",
            "realesr-animevideov3-x4.bin",
        )

        /** True when [dir] holds every [MODEL_FILES] entry with some content. */
        fun hasModels(dir: File): Boolean = MODEL_FILES.all { File(dir, it).let { f -> f.isFile && f.length() > 0 } }
    }
}

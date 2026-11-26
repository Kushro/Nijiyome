// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.framegen

import app.nijiyome.player.mpv.external.LsfgVk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/** Transport active inside the native desktop LSFG engine. */
enum class DesktopFgTransport(val code: Int) {
    NONE(0),
    GL_VULKAN_ZERO_COPY(1),
    D3D12_VULKAN_ZERO_COPY(2),
    STAGING(3);

    val isZeroCopy: Boolean get() = this == GL_VULKAN_ZERO_COPY || this == D3D12_VULKAN_ZERO_COPY

    companion object {
        fun fromCode(code: Int): DesktopFgTransport = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** Result of probing the Vulkan GPU and Lossless.dll SPIR-V shader set. */
data class DesktopFgProbe(
    val ok: Boolean,
    val deviceName: String = "",
    val fp16: Boolean = false,
    val zeroCopyVk: Boolean = false,
    val errorCode: String = "",
)

/** Live native telemetry snapshot. */
data class DesktopFgNativeStats(
    val active: Boolean = false,
    val transport: DesktopFgTransport = DesktopFgTransport.NONE,
    val multiplier: Int = 2,
    val overloaded: Boolean = false,
    val inFps: Double = 0.0,
    val outFps: Double = 0.0,
    val computeMs: Double = 0.0,
    val latencyMs: Double = 0.0,
    val inFrames: Long = 0L,
    val outFrames: Long = 0L,
    val generatedFrames: Long = 0L,
    val droppedLate: Long = 0L,
    val sceneCuts: Long = 0L,
    val errorCode: String = "",
)

/**
 * JNI bridge to `libnijiyome_fg_desktop.so` (Linux) / `nijiyome_fg_desktop.dll` (Windows).
 * Located from `NIJIYOME_DESKTOP_FRAMEGEN`, the Compose Desktop `compose.application.resources.dir`,
 * next to the app launcher, or `<cwd>/build/desktop-framegen/` during development.
 */
object DesktopFrameGenNative {
    const val ENV_LIB_PATH = "NIJIYOME_DESKTOP_FRAMEGEN"
    const val ENV_TRANSPORT = "NIJIYOME_FG_TRANSPORT"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: String? = null

    val isLoaded: Boolean
        get() {
            ensureLoaded()
            return loaded
        }

    val failureReason: String?
        get() {
            ensureLoaded()
            return loadError
        }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

    private fun libFileName(): String =
        if (isWindows()) "nijiyome_fg_desktop.dll" else "libnijiyome_fg_desktop.so"

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loadAttempted) return loaded
        loadAttempted = true
        val fileName = libFileName()
        val candidates = buildList {
            System.getenv(ENV_LIB_PATH)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
            System.getProperty("compose.application.resources.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(File(it, fileName))
            }
            System.getProperty("java.home")?.trim()?.takeIf { it.isNotEmpty() }?.let { home ->
                val runtime = File(home)
                // Linux jpackage: <app>/lib/runtime -> <app>/lib/app/resources/<lib>
                add(File(runtime.parentFile, "app/resources/$fileName"))
                // Windows jpackage: <app>/runtime -> <app>/app/resources/<lib>
                add(File(runtime.parentFile, "app/resources/$fileName"))
                add(File(runtime.parentFile, fileName))
            }
            val cwd = File(System.getProperty("user.dir").orEmpty())
            add(File(cwd, "build/desktop-framegen/$fileName"))
            add(File(cwd, "build/desktop-framegen/Release/$fileName"))
            add(File(cwd, "desktopApp/build/generated/appResources/${if (isWindows()) "windows-x64" else "linux-x64"}/$fileName"))
        }
        for (c in candidates) {
            if (!c.isFile) continue
            try {
                System.load(c.absolutePath)
                loaded = true
                loadError = null
                return true
            } catch (t: Throwable) {
                loadError = t.message ?: t.javaClass.simpleName
            }
        }
        try {
            System.loadLibrary("nijiyome_fg_desktop")
            loaded = true
            loadError = null
            return true
        } catch (t: Throwable) {
            if (loadError == null) loadError = t.message ?: "native library not found"
            return false
        }
    }

    /**
     * Finds `Lossless.dll` containing the precompiled LSFG 3.1 SPIR-V shader set (works on both Linux and Windows).
     * Unlike the external lsfg-vk 2.x layer (which accepts `lsfg-vk.dll`), the embedded Vulkan engine requires `Lossless.dll`.
     */
    fun findLosslessDll(
        configured: String?,
        env: Map<String, String> = System.getenv(),
        home: File = File(System.getProperty("user.home")),
        windows: Boolean = isWindows(),
    ): File? {
        val candidates = buildList {
            configured?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                val f = File(path)
                // If user configured lsfg-vk.dll, prefer a sibling Lossless.dll first.
                if (f.name.equals("lsfg-vk.dll", ignoreCase = true)) {
                    f.parentFile?.let { add(File(it, "Lossless.dll")) }
                }
                add(f)
            }
            if (!windows) {
                val configHome = env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
                val userConf = runCatching { LsfgVk.userDll(File(configHome, "lsfg-vk/conf.toml").readText()) }.getOrNull()
                userConf?.let { path ->
                    val f = File(path)
                    if (f.name.equals("lsfg-vk.dll", ignoreCase = true)) {
                        f.parentFile?.let { add(File(it, "Lossless.dll")) }
                    }
                    add(f)
                }
                listOf(".local/share/Steam", ".steam/steam", ".var/app/com.valvesoftware.Steam/.local/share/Steam").forEach { rel ->
                    add(File(home, "$rel/steamapps/common/Lossless Scaling/Lossless.dll"))
                }
                add(File(home, "Lossless.dll"))
            } else {
                listOfNotNull(
                    env["ProgramFiles(x86)"]?.let { File(it, "Steam/steamapps/common/Lossless Scaling/Lossless.dll") },
                    env["ProgramFiles"]?.let { File(it, "Steam/steamapps/common/Lossless Scaling/Lossless.dll") },
                    File("C:/Program Files (x86)/Steam/steamapps/common/Lossless Scaling/Lossless.dll"),
                    File("D:/SteamLibrary/steamapps/common/Lossless Scaling/Lossless.dll"),
                    File("E:/SteamLibrary/steamapps/common/Lossless Scaling/Lossless.dll"),
                ).forEach(::add)
            }
        }
        return candidates.firstOrNull { it.isFile }
    }

    fun probe(dllPath: String?): DesktopFgProbe {
        if (!ensureLoaded()) {
            return DesktopFgProbe(ok = false, errorCode = "no_library")
        }
        val raw = runCatching { nProbe(dllPath.orEmpty()) }.getOrElse {
            return DesktopFgProbe(ok = false, errorCode = "probe_failed")
        }
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return DesktopFgProbe(ok = false, errorCode = "probe_failed")
        fun p(k: String) = obj[k] as? JsonPrimitive
        return DesktopFgProbe(
            ok = p("ok")?.booleanOrNull == true,
            deviceName = p("device")?.contentOrNull.orEmpty(),
            fp16 = p("fp16")?.booleanOrNull == true,
            zeroCopyVk = p("zeroCopyVk")?.booleanOrNull == true,
            errorCode = p("error")?.contentOrNull.orEmpty(),
        )
    }

    fun readStats(handle: Long): DesktopFgNativeStats {
        if (handle == 0L || !isLoaded) return DesktopFgNativeStats()
        val doubles = DoubleArray(4)
        val longs = LongArray(9)
        val err = nStats(handle, doubles, longs)
        return DesktopFgNativeStats(
            active = longs[0] != 0L,
            transport = DesktopFgTransport.fromCode(longs[1].toInt()),
            multiplier = longs[2].toInt().coerceIn(2, 4),
            overloaded = longs[3] != 0L,
            inFrames = longs[4],
            outFrames = longs[5],
            generatedFrames = longs[6],
            droppedLate = longs[7],
            sceneCuts = longs[8],
            inFps = doubles[0],
            outFps = doubles[1],
            computeMs = doubles[2],
            latencyMs = doubles[3],
            errorCode = err,
        )
    }

    fun forcedTransportCode(): Int = when (System.getenv(ENV_TRANSPORT)?.trim()?.lowercase(Locale.ROOT)) {
        "staging" -> DesktopFgTransport.STAGING.code
        else -> 0
    }

    @JvmStatic external fun nProbe(dllPath: String): String
    @JvmStatic external fun nCreate(): Long
    @JvmStatic external fun nDestroy(handle: Long)
    @JvmStatic external fun nStart(
        handle: Long,
        dllPath: String,
        width: Int,
        height: Int,
        multiplier: Int,
        flowScale: Float,
        performance: Boolean,
        antiArtifacts: Boolean,
        vsyncNs: Long,
        forceTransport: Int,
        d3d12DeviceStructPtr: Long,
    ): Int
    @JvmStatic external fun nStop(handle: Long)
    @JvmStatic external fun nSetPaused(handle: Long, paused: Boolean)
    @JvmStatic external fun nFlush(handle: Long)
    @JvmStatic external fun nSetSourcePeriodNs(handle: Long, periodNs: Long)
    @JvmStatic external fun nSetVsyncNs(handle: Long, vsyncNs: Long)
    @JvmStatic external fun nSetAntiArtifacts(handle: Long, enabled: Boolean)
    @JvmStatic external fun nSubmitGlFrame(handle: Long, srcGlTexId: Int, timestampNs: Long): Boolean
    @JvmStatic external fun nSubmitD3d12Frame(handle: Long, srcResourcePtr: Long, timestampNs: Long): Boolean
    @JvmStatic external fun nSubmitRgbaFrame(handle: Long, directBuf: ByteBuffer, byteCount: Int, timestampNs: Long): Boolean
    @JvmStatic external fun nAcquirePresentSlot(handle: Long, nowNs: Long): Int
    @JvmStatic external fun nSlotCount(handle: Long): Int
    @JvmStatic external fun nSlotHandle(handle: Long, slot: Int): Long
    @JvmStatic external fun nReadSlotRgba(handle: Long, slot: Int, directBuf: ByteBuffer, byteCount: Int): Boolean
    @JvmStatic external fun nStats(handle: Long, outDoubles: DoubleArray, outLongs: LongArray): String
}

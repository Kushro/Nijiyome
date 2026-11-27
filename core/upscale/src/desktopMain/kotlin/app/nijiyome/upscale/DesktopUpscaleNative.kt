// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import java.io.File
import java.util.Locale
import java.util.function.BooleanSupplier

/** The JNI symbols of `libnijiyome_upscale` (native/upscale/src/upscale_jni.cpp). Call only through [DesktopUpscaleBridge]. */
internal object UpscaleNative {
    @JvmStatic external fun nProbe(preferredKey: String?): String

    @JvmStatic external fun nInit(deviceIndex: Int, modelDir: String): Int

    @JvmStatic external fun nUpscale(
        rgba: ByteArray,
        width: Int,
        height: Int,
        scale: Int,
        tile: Int,
        cancelToken: BooleanSupplier?,
    ): ByteArray?

    @JvmStatic external fun nRelease()
}

/**
 * Loads `libnijiyome_upscale.so` / `nijiyome_upscale.dll` the way `DesktopFrameGenNative` loads the frame generation library: from
 * `NIJIYOME_DESKTOP_UPSCALE`, the Compose Desktop `compose.application.resources.dir` (where packaging stages it), next to the
 * jpackage runtime, or `build/desktop-upscale/` (and its `Release/`) during development.
 */
object DesktopUpscaleBridge : UpscaleNativeBridge {
    const val ENV_LIB_PATH = "NIJIYOME_DESKTOP_UPSCALE"

    /** The models folder packaging stages next to the library (`<resources.dir>/upscale-models/`). */
    private const val MODELS_DIR = "upscale-models"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loaded = false

    @Volatile
    var loadError: String? = null
        private set

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

    private fun libFileName(): String = if (isWindows()) "nijiyome_upscale.dll" else "libnijiyome_upscale.so"

    /** The folders packaging may have staged resources in, most specific first. */
    private fun resourceDirs(): List<File> = buildList {
        System.getProperty("compose.application.resources.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
        System.getProperty("java.home")?.trim()?.takeIf { it.isNotEmpty() }?.let { home ->
            val runtime = File(home)
            // Linux jpackage: <app>/lib/runtime -> <app>/lib/app/resources; Windows: <app>/runtime -> <app>/app/resources
            add(File(runtime.parentFile, "app/resources"))
            runtime.parentFile?.let { add(it) }
        }
        // Development: what stageAppResources staged for `run`.
        val os = if (isWindows()) "windows-x64" else "linux-x64"
        add(File(System.getProperty("user.dir").orEmpty(), "desktopApp/build/generated/appResources/$os"))
    }

    @Synchronized
    override fun load(): Boolean {
        if (loadAttempted) return loaded
        loadAttempted = true
        val fileName = libFileName()
        val cwd = File(System.getProperty("user.dir").orEmpty())
        val candidates = buildList {
            System.getenv(ENV_LIB_PATH)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
            resourceDirs().forEach { add(File(it, fileName)) }
            add(File(cwd, "build/desktop-upscale/$fileName"))
            add(File(cwd, "build/desktop-upscale/Release/$fileName"))
            add(File(cwd, "../build/desktop-upscale/$fileName"))
            add(File(cwd, "../build/desktop-upscale/Release/$fileName"))
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
            System.loadLibrary("nijiyome_upscale")
            loaded = true
            loadError = null
        } catch (t: Throwable) {
            if (loadError == null) loadError = t.message ?: "native library not found"
        }
        if (!loaded) System.err.println("[nijiyome-upscale] library not loaded: $loadError")
        return loaded
    }

    /**
     * The staged models (`<resources.dir>/upscale-models/`, three `.param` and the x4 `.bin`), or `native/upscale/models/` of the
     * repository during development. ncnn reads them in place with fopen, so nothing is copied.
     */
    fun modelDir(): File? {
        val cwd = File(System.getProperty("user.dir").orEmpty())
        val candidates = buildList {
            System.getenv(ENV_LIB_PATH)?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it).parentFile?.let { d -> add(File(d, MODELS_DIR)) } }
            resourceDirs().forEach { add(File(it, MODELS_DIR)) }
            add(File(cwd, "build/desktop-upscale/$MODELS_DIR"))
            add(File(cwd, "native/upscale/models"))
            add(File(cwd, "../native/upscale/models"))
        }
        return candidates.firstOrNull { NativeUpscaleEngine.hasModels(it) }
    }

    override fun probe(preferredKey: String?): String = UpscaleNative.nProbe(preferredKey)

    override fun init(deviceIndex: Int, modelDir: String): Int = UpscaleNative.nInit(deviceIndex, modelDir)

    override fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, tile: Int, cancel: BooleanSupplier?): ByteArray? =
        UpscaleNative.nUpscale(rgba, width, height, scale, tile, cancel)

    override fun release() = UpscaleNative.nRelease()
}

/**
 * The desktop engine. GPU match (plan §4.6, decided for upscaling only): the GL / D3D device Skia draws with cannot be read safely
 * from the worker thread (Skiko's `renderInfo` calls glGetString without a current context), so the shared `gpu_pick.h` policy picks
 * and nothing is preferred. On Linux the probe is logged next to the PCI id of the boot VGA device (the GPU Mesa draws on by default),
 * so a mismatch on a two-GPU machine shows in the log. Framegen keeps its own pick, unchanged.
 */
fun desktopUpscaleEngine(): UpscaleEngine = NativeUpscaleEngine(
    bridge = DesktopUpscaleBridge,
    modelDir = DesktopUpscaleBridge::modelDir,
    preferredKey = { probe ->
        bootVgaPciId()?.let { (vendor, device) ->
            val same = vendor == probe.vendorId && device == probe.deviceId
            System.err.println(
                "[nijiyome-upscale] Vulkan pick ${probe.device} (%04x:%04x); boot VGA %04x:%04x%s".format(
                    probe.vendorId, probe.deviceId, vendor, device, if (same) "" else " (different GPU: the app may draw elsewhere)",
                ),
            )
        }
        null
    },
)

/** Linux: vendor and device id of the DRM card with `boot_vga = 1`, or null (other systems, no sysfs, unreadable). */
private fun bootVgaPciId(): Pair<Int, Int>? = runCatching {
    if (!System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("linux")) return null
    val cards = File("/sys/class/drm").listFiles { f -> f.name.matches(Regex("card\\d+")) } ?: return null
    for (card in cards) {
        val dev = File(card, "device")
        if (File(dev, "boot_vga").takeIf { it.isFile }?.readText()?.trim() != "1") continue
        fun hex(name: String) = File(dev, name).readText().trim().removePrefix("0x").toInt(16)
        return hex("vendor") to hex("device")
    }
    null
}.getOrNull()

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.util.function.BooleanSupplier

/** The JNI symbols of `libnijiyome_upscale.so` (native/upscale/src/upscale_jni.cpp); kept by consumer-rules.pro. */
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

private const val TAG = "NijiyomeUpscale"

/** `System.loadLibrary("nijiyome_upscale")`; the 32-bit ABIs and a missing library leave it unloaded (no GPU, feature off). */
internal object AndroidUpscaleBridge : UpscaleNativeBridge {
    @Volatile
    private var attempted = false

    @Volatile
    private var loaded = false

    @Synchronized
    override fun load(): Boolean {
        if (attempted) return loaded
        attempted = true
        loaded = runCatching { System.loadLibrary("nijiyome_upscale") }
            .onFailure { Log.w(TAG, "library not loaded: ${it.message}") }
            .isSuccess
        return loaded
    }

    override fun probe(preferredKey: String?): String = UpscaleNative.nProbe(preferredKey)

    override fun init(deviceIndex: Int, modelDir: String): Int = UpscaleNative.nInit(deviceIndex, modelDir)

    override fun upscale(rgba: ByteArray, width: Int, height: Int, scale: Int, tile: Int, cancel: BooleanSupplier?): ByteArray? =
        UpscaleNative.nUpscale(rgba, width, height, scale, tile, cancel)

    override fun release() = UpscaleNative.nRelease()
}

/**
 * Copies the model assets (`assets/upscale-models/`) to `filesDir/upscale-models/<build>/` on first use, where ncnn can fopen them;
 * as `AssetShaderInstaller` does, the folder is keyed by the APK build so an update always installs fresh copies, files are written
 * then renamed, and older folders are removed.
 */
internal class UpscaleModelInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ASSET_DIR)

    @Synchronized
    fun install(): File? = runCatching {
        val dir = File(root, buildKey())
        if (!NativeUpscaleEngine.hasModels(dir)) {
            dir.mkdirs()
            for (name in NativeUpscaleEngine.MODEL_FILES) {
                val target = File(dir, name)
                if (target.isFile && target.length() > 0) continue
                val tmp = File(dir, "$name.tmp")
                appContext.assets.open("$ASSET_DIR/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        }
        root.listFiles()?.filter { it.isDirectory && it.name != dir.name }?.forEach { runCatching { it.deleteRecursively() } }
        dir.takeIf { NativeUpscaleEngine.hasModels(it) }
    }.onFailure { Log.w(TAG, "model install failed: ${it.message}") }.getOrNull()

    /** `b<versionCode>-<lastUpdateTime>`, as `AssetShaderInstaller.buildKey`. */
    private fun buildKey(): String = runCatching {
        val pm = appContext.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appContext.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "b$code-${info.lastUpdateTime}"
    }.getOrDefault("b0")

    private companion object {
        const val ASSET_DIR = "upscale-models"
    }
}

/** The phone engine: one GPU, the shared policy plus [UpscaleGpuPolicy]'s Adreno blocklist, models installed on first use. */
fun androidUpscaleEngine(context: Context): UpscaleEngine {
    val installer = UpscaleModelInstaller(context)
    return NativeUpscaleEngine(
        bridge = AndroidUpscaleBridge,
        modelDir = installer::install,
        log = { Log.i(TAG, it) },
    )
}

/** Lowers the calling thread (the queue's worker) to background priority, below the UI and the player. */
fun androidUpscaleThreadSetup() {
    runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

// 0.4.0 (worker A): turns the planner's [ShaderSpec]s into files mpv can load. Replaces 0.3.x `ShaderPresets.install` (one folder per
// preset): files now live once in a folder keyed by the APK build, so an app update always installs fresh copies (Enigmata's E28 fix:
// a stale copy from a previous APK must never win) and old folders are removed. FP16 Anime4K copies (Enigmata's
// Anime4KShaderOptimizer) live in their own sub-folder, so switching precision changes the path and mpv reloads the shader.
// ShaderResolver and ShaderNames (pure) are in commonMain/quality/ShaderNames.kt.

/** [ShaderResolver] over the APK assets (`assets/shaders/`), installed under `filesDir/shaders/<build>/`. */
class AssetShaderInstaller(context: Context) : ShaderResolver {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "shaders")
    private val dir: File by lazy { File(root, buildKey()) }

    @Volatile
    private var cleaned = false

    @Synchronized
    override fun resolve(specs: List<ShaderSpec>, fp16: Boolean): List<String>? {
        if (specs.isEmpty()) return emptyList()
        cleanOldBuilds()
        val names = ShaderNames.relativePaths(specs, fp16)
        return runCatching {
            specs.zip(names).map { (spec, name) ->
                val target = File(dir, name)
                if (!target.isFile || target.length() == 0L) write(target, content(spec, name))
                target.absolutePath
            }
        }.onFailure { android.util.Log.w("mpv", "shader install failed: ${it.message}") }.getOrNull()
    }

    private fun content(spec: ShaderSpec, name: String): ByteArray {
        val raw = appContext.assets.open("${ShaderCatalog.ASSET_DIR}/${spec.asset}").use { it.readBytes() }
        return when {
            spec.transform == ShaderTransform.CAS ->
                ShaderSources.casVariant(raw.toString(Charsets.UTF_8), spec.casSharpening).toByteArray(Charsets.UTF_8)
            name.startsWith("${ShaderNames.FP16_DIR}/") ->
                // A rewrite that throws falls back to the verbatim text (never an empty chain).
                runCatching { Anime4KShaderOptimizer.optimize(raw.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8) }.getOrDefault(raw)
            else -> raw
        }
    }

    /** Write-then-rename, so mpv never reads a half-written shader. */
    private fun write(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    /** Removes the folders of previous builds and the 0.3.x per-preset folders (`shaders/anime4k-a/`...), once per process. */
    private fun cleanOldBuilds() {
        if (cleaned) return
        cleaned = true
        val keep = dir.name
        root.listFiles()?.filter { it.isDirectory && it.name != keep }?.forEach { runCatching { it.deleteRecursively() } }
    }

    /** `b<versionCode>-<lastUpdateTime>`: changes with every APK install, also debug reinstalls of the same version. */
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
}

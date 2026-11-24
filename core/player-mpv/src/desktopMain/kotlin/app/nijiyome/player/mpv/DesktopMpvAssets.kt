// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.mpv.quality.Anime4KShaderOptimizer
import app.nijiyome.player.mpv.quality.ShaderCatalog
import app.nijiyome.player.mpv.quality.ShaderNames
import app.nijiyome.player.mpv.quality.ShaderResolver
import app.nijiyome.player.mpv.quality.ShaderSources
import app.nijiyome.player.mpv.quality.ShaderSpec
import app.nijiyome.player.mpv.quality.ShaderTransform
import app.nijiyome.player.mpv.quality.mpvLogInfo
import java.io.File
import java.net.JarURLConnection
import java.util.Locale

// Hito 4: the bundled shaders and subtitle fonts on desktop. They are the files of src/androidMain/assets (Android reads them as APK
// assets), copied by the build into this module's resources under [DesktopMpvAssets.ROOT] (see build.gradle.kts), and extracted to the
// cache folder the module receives, because mpv only reads files.

object DesktopMpvAssets {
    /** Classpath folder of the copied assets: `<ROOT>/shaders/...`, `<ROOT>/subfonts/...`. */
    const val ROOT = "app/nijiyome/player/mpv"
    const val FONTS_DIR = "subfonts"

    private val loader: ClassLoader get() = DesktopMpvAssets::class.java.classLoader ?: ClassLoader.getSystemClassLoader()

    /**
     * v1.1 (F2): the shaders only desktop bundles (src/desktopMain/resources: the Anime4K VL networks of HQ and NVScaler) are all on
     * the class path.
     */
    val hasDesktopShaders: Boolean by lazy {
        System.getenv("NIJIYOME_DESKTOP_SHADERS") != "0" && ShaderCatalog.DESKTOP_FILES.all { loader.getResource("$ROOT/${ShaderCatalog.ASSET_DIR}/$it") != null }
    }

    /** Bytes of the bundled asset [path] (`shaders/CAS.glsl`), or null when it is not on the classpath. */
    fun read(path: String): ByteArray? = loader.getResourceAsStream("$ROOT/$path")?.use { it.readBytes() }

    /**
     * File names directly inside the bundled asset folder [dir] (a resources folder in development, a jar entry once packaged). Every
     * class path root counts: v1.1's desktop-only shaders live in src/desktopMain/resources next to the generated Android copies.
     */
    fun list(dir: String): List<String> {
        val urls = runCatching { loader.getResources("$ROOT/$dir").toList() }.getOrDefault(emptyList())
        return urls.flatMap { url -> listOne(url, dir) }.distinct().sorted()
    }

    private fun listOne(url: java.net.URL, dir: String): List<String> = runCatching {
        when (url.protocol) {
            "file" -> File(url.toURI()).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
            "jar" -> {
                val prefix = "$ROOT/$dir/"
                // Not the class loader's cached JarFile: this one is closed afterwards.
                val conn = (url.openConnection() as JarURLConnection).apply { useCaches = false }
                conn.jarFile.use { jar ->
                    jar.entries().asSequence().map { it.name }
                        .filter { it.startsWith(prefix) && it.length > prefix.length && '/' !in it.substring(prefix.length) }
                        .map { it.substring(prefix.length) }.toList()
                }
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /**
     * Copies the bundled subtitle fonts (`nijiyome_*`, the static faces libass needs for "Outfit" / "Space Grotesk") into [dir] when
     * missing or different. Returns how many files are in place.
     */
    fun installSubtitleFonts(dir: File, read: (String) -> ByteArray? = ::read, names: List<String> = list(FONTS_DIR)): Int {
        dir.mkdirs()
        var ok = 0
        for (name in names) {
            val bytes = read("$FONTS_DIR/$name") ?: continue
            if (runCatching { writeIfChanged(File(dir, name), bytes) }.isSuccess) ok++
        }
        return ok
    }

    /** Write-then-rename, so mpv never reads a half-written file; nothing is written when [target] already has [bytes]. */
    internal fun writeIfChanged(target: File, bytes: ByteArray): Boolean {
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) return false
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
        return true
    }
}

/**
 * [ShaderResolver] over the bundled shaders, installed under [dir] with the same relative paths as Android
 * ([ShaderNames.relativePaths]: FP16 Anime4K copies in their own sub-folder, generated CAS variants in `gen/`). Android keys its
 * folder by the APK build so an update never runs a stale copy; here each file is compared with what this build would write and
 * rewritten when it differs (once per file and process), which gives the same guarantee without a build key.
 */
class DesktopShaderInstaller(
    private val dir: File,
    private val read: (String) -> ByteArray? = { DesktopMpvAssets.read("${ShaderCatalog.ASSET_DIR}/$it") },
) : ShaderResolver {
    private val verified = HashSet<String>()

    @Synchronized
    override fun resolve(specs: List<ShaderSpec>, fp16: Boolean): List<String>? {
        if (specs.isEmpty()) return emptyList()
        val names = ShaderNames.relativePaths(specs, fp16)
        return runCatching {
            specs.zip(names).map { (spec, name) ->
                val target = File(dir, name)
                if (name !in verified || !target.isFile) {
                    DesktopMpvAssets.writeIfChanged(target, content(spec, name))
                    verified += name
                }
                target.absolutePath
            }
        }.onFailure { mpvLogInfo("shader install failed: ${it.message}") }.getOrNull()
    }

    private fun content(spec: ShaderSpec, name: String): ByteArray {
        val raw = read(spec.asset) ?: error("bundled shader ${spec.asset} not found")
        return when {
            spec.transform == ShaderTransform.CAS ->
                ShaderSources.casVariant(raw.toString(Charsets.UTF_8), spec.casSharpening).toByteArray(Charsets.UTF_8)
            name.startsWith("${ShaderNames.FP16_DIR}/") ->
                // A rewrite that throws falls back to the verbatim text (never an empty chain), as on Android.
                runCatching { Anime4KShaderOptimizer.optimize(raw.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8) }.getOrDefault(raw)
            else -> raw
        }
    }
}

/**
 * The common quality controller writes Android's values for two properties; this translates them for desktop mpv:
 * - `hwdec`: [app.nijiyome.model.DecoderMode] names MediaCodec decoders. Auto = on Linux the list MediaMP validates (NVDEC kept on the
 *   GPU, VAAPI copied back since Skiko's GLX context cannot take VAAPI frames directly, then mpv's safe list), on Windows mpv's safe
 *   list (D3D11VA); HW (copy) = `auto-copy`, HW+ (direct) = `auto`, SW = `no`.
 * - `glsl-shaders`: a `:`-joined list, but mpv's path-list separator is `;` on Windows and its absolute paths start with a drive letter.
 */
internal class DesktopQualityPort(
    private val write: (String, String) -> Unit,
    private val readProperty: (String) -> String?,
    private val windows: Boolean = isWindows(),
) : app.nijiyome.player.mpv.quality.QualityMpvPort {
    override fun setProperty(name: String, value: String) = write(name, desktopQualityValue(name, value, windows))

    override fun getProperty(name: String): String? = readProperty(name)
}

internal fun desktopQualityValue(name: String, value: String, windows: Boolean): String = when (name) {
    "hwdec" -> desktopHwdec(value, windows)
    "glsl-shaders" -> if (windows) windowsShaderList(value) else value
    else -> value
}

internal fun desktopHwdec(androidValue: String, windows: Boolean): String = when (androidValue) {
    app.nijiyome.model.DecoderMode.AUTO.hwdec -> if (windows) "auto-safe" else LINUX_AUTO_HWDEC
    app.nijiyome.model.DecoderMode.HW.hwdec -> "auto-copy"
    app.nijiyome.model.DecoderMode.HW_PLUS.hwdec -> "auto"
    app.nijiyome.model.DecoderMode.SW.hwdec -> "no"
    else -> androidValue
}

// PENDIENTE-ESCRITORIO: lista de shaders y hwdec de Windows sin probar (separador `;`, d3d11va)
/** `C:\a.glsl:C:\b.glsl` (joined with `:` by MpvOptions.shaderList) -> `C:\a.glsl;C:\b.glsl`: a one-letter piece is a drive letter. */
internal fun windowsShaderList(posixJoined: String): String {
    if (posixJoined.isEmpty()) return posixJoined
    val parts = posixJoined.split(':')
    val paths = mutableListOf<String>()
    var i = 0
    while (i < parts.size) {
        val p = parts[i]
        if (p.length == 1 && p[0].isLetter() && i + 1 < parts.size) {
            paths += "$p:${parts[i + 1]}"
            i += 2
        } else {
            paths += p
            i++
        }
    }
    return paths.joinToString(";")
}

/**
 * MediaMP's own Linux default (OpenGLRenderContextLifecycle.initialize). VAAPI works through the system's libva when the app could load
 * it (SystemLibva, v1.1); with the bundled libva 1.20 only NVDEC or software decode.
 */
internal const val LINUX_AUTO_HWDEC = "nvdec,vaapi-copy,auto-safe"

internal fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

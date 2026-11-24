// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.Locale
import kotlin.math.floor

// Hito 4, paso D: frame generation on the external mpv through lsfg-vk (a Vulkan layer that hooks mpv's swapchain), as Tatakai Next
// did (tk-player/src/external.rs `FrameGen`, docs/redesign/18). Nothing is installed: the app only finds the layer the user installed
// and writes a config file of its own for the mpv process (`LSFGVK_CONFIG`); the user's own lsfg-vk profiles are never touched.
//
// Detection:
// - the layer: a Vulkan layer manifest (`*.json`) whose layer name is lsfg-vk's, in the directories the Vulkan loader reads on Linux
//   (`VK_ADD_IMPLICIT_LAYER_PATH`, `$XDG_CONFIG_HOME` / `$XDG_CONFIG_DIRS` / `/etc` / `$XDG_DATA_HOME` / `$XDG_DATA_DIRS`, each with
//   `vulkan/implicit_layer.d`);
// - the shaders: lsfg-vk 2.x reads `lsfg-vk.dll` (the shader pack it ships for Lossless Scaling), 1.x the game's `Lossless.dll`.
//   Order (Tatakai Next's): an `lsfg-vk.dll` next to a configured DLL, Steam's Lossless Scaling folder, the configured DLL itself (the
//   app's setting, then `dll` of `[global]` in the user's `lsfg-vk/conf.toml`), then Steam's `Lossless.dll`.

object LsfgVk {
    /** Environment of the mpv process: the dedicated config, and the layer's own off switch. */
    const val ENV_CONFIG = "LSFGVK_CONFIG"
    const val ENV_DISABLE = "DISABLE_LSFGVK"

    /** The layer's own log (diagnostics). */
    const val ENV_LOG_FILE = "LSFGVK_LOG_FILE"
    const val ENV_LOG_LEVEL = "LSFGVK_LOG_LEVEL"

    /** Frame rate frame generation assumes for the source before playback (anime: 23.976 fps). */
    const val SOURCE_FPS = 23.976

    /** Optical-flow resolution scale of Tatakai Next's balanced profile. */
    const val DEFAULT_FLOW_SCALE = 0.85f

    /** Why frame generation cannot run here. */
    enum class Missing { NOT_LINUX, LAYER, DLL }

    /** What was found. [missing] null = it can run. */
    data class Availability(val layer: File?, val dll: File?, val missing: Missing?) {
        val available: Boolean get() = missing == null
    }

    /** Looks for the layer and the shaders ([configuredDll] = the app's setting). Reads a few small files. */
    fun detect(
        configuredDll: String?,
        env: Map<String, String> = System.getenv(),
        home: File = File(System.getProperty("user.home")),
        linux: Boolean = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("linux"),
        sysconfDir: File = File("/etc"),
    ): Availability {
        if (!linux) return Availability(null, null, Missing.NOT_LINUX)
        val layer = findLayer(env, home, sysconfDir)
        val dll = findDll(configuredDll, env, home)
        val missing = when {
            layer == null -> Missing.LAYER
            dll == null -> Missing.DLL
            else -> null
        }
        return Availability(layer, dll, missing)
    }

    /** The Vulkan loader's implicit layer directories, in its search order. */
    internal fun layerDirs(env: Map<String, String>, home: File, sysconfDir: File = File("/etc")): List<File> {
        fun list(v: String?, default: String): List<String> = (v?.takeIf { it.isNotBlank() } ?: default).split(':').filter { it.isNotBlank() }
        val configHome = env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() } ?: File(home, ".config").path
        val dataHome = env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() } ?: File(home, ".local/share").path
        val sub = "vulkan/implicit_layer.d"
        return buildList {
            env["VK_ADD_IMPLICIT_LAYER_PATH"]?.split(':')?.filter { it.isNotBlank() }?.forEach { add(File(it)) }
            add(File(configHome, sub))
            list(env["XDG_CONFIG_DIRS"], "/etc/xdg").forEach { add(File(it, sub)) }
            add(File(sysconfDir, sub))
            add(File(dataHome, sub))
            list(env["XDG_DATA_DIRS"], "/usr/local/share:/usr/share").forEach { add(File(it, sub)) }
        }.distinct()
    }

    /** The first lsfg-vk layer manifest, or null. */
    internal fun findLayer(env: Map<String, String>, home: File, sysconfDir: File = File("/etc")): File? = layerDirs(env, home, sysconfDir).asSequence()
        .flatMap { d -> (if (d.isFile) listOf(d) else d.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()).asSequence() }
        .firstOrNull { f -> runCatching { isLsfgManifest(f.readText()) }.getOrDefault(false) }

    /** Whether a layer manifest is lsfg-vk's (`VK_LAYER_LSFGVK_frame_generation` in 2.x, `VK_LAYER_LS_frame_generation` in 1.x). */
    internal fun isLsfgManifest(text: String): Boolean {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return false
        val layers = buildList {
            (root["layer"] as? JsonObject)?.let(::add)
            (root["layers"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(::add) }
        }
        return layers.any { l ->
            val name = (l["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            name.contains("LSFG", ignoreCase = true) || name == "VK_LAYER_LS_frame_generation"
        }
    }

    /** The shader DLL to use, or null (see the order above). */
    internal fun findDll(configured: String?, env: Map<String, String>, home: File): File? {
        val configHome = env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
        val userConf = runCatching { userDll(File(configHome, "lsfg-vk/conf.toml").readText()) }.getOrNull()
        val configuredFiles = listOfNotNull(configured?.trim()?.takeIf { it.isNotEmpty() }?.let(::File), userConf?.let(::File))
        val steam = listOf(".local/share/Steam", ".steam/steam", ".var/app/com.valvesoftware.Steam/.local/share/Steam")
            .map { File(home, "$it/steamapps/common/Lossless Scaling") }
        val candidates = buildList {
            configuredFiles.forEach { c -> c.parentFile?.let { add(File(it, "lsfg-vk.dll")) } }
            steam.forEach { add(File(it, "lsfg-vk.dll")) }
            addAll(configuredFiles)
            steam.forEach { add(File(it, "Lossless.dll")) }
        }
        return candidates.firstOrNull { it.isFile }
    }

    /** `dll = "..."` of the `[global]` table of an lsfg-vk config, or null. */
    internal fun userDll(toml: String): String? {
        var inGlobal = false
        for (raw in toml.lines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inGlobal = line == "[global]"
                continue
            }
            if (!inGlobal || !line.startsWith("dll")) continue
            val rest = line.removePrefix("dll").trimStart()
            if (!rest.startsWith("=")) continue
            return rest.removePrefix("=").trim().trim('"').takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * The multiplier the display can show (Tatakai Next BUG-036): [requested] (0 = automatic, the highest that fits) times the source frame
     * rate must fit the refresh rate, never below x2 (frame generation is on) nor above x4 or the request. Unknown refresh = request
     * (x4 for automatic).
     */
    fun multiplierFor(requested: Int, refreshHz: Double?): Int {
        val want = if (requested <= 0) 4 else requested.coerceIn(2, 4)
        val hz = refreshHz?.takeIf { it.isFinite() && it > 1.0 } ?: return want
        val fits = floor(hz / SOURCE_FPS + 0.01).coerceIn(2.0, 4.0).toInt()
        return minOf(want, fits)
    }

    /** The dedicated config (lsfg-vk format version 2): one profile active in `mpv`. */
    fun configToml(multiplier: Int, flowScale: Float, performance: Boolean, dll: File?): String = buildString {
        append("version = 2\n\n[global]\nallow_fp16 = true\n")
        if (dll != null) append("dll = ").append(tomlString(dll.absolutePath)).append('\n')
        append("\n[[profile]]\nname = \"Nijiyome\"\nactive_in = [ \"mpv\" ]\n")
        append("multiplier = ").append(multiplier.coerceIn(2, 4)).append('\n')
        append("flow_scale = ").append("%.2f".format(Locale.ROOT, flowScale.coerceIn(0.25f, 1f))).append('\n')
        append("performance_mode = ").append(performance).append('\n')
        append("pacing_mode = \"vsync\"\noverride_present_mode = true\npreserve_swapchain_image_count = false\n")
    }

    /**
     * Writes [configToml] as `<dir>/lsfg-vk-<random>.toml` (written whole, then renamed into place) and returns it. [dir] is meant to
     * be this user's private folder ([PrivateRuntimeDir]); the random name keeps sessions apart.
     */
    fun writeConfig(dir: File, multiplier: Int, flowScale: Float, performance: Boolean, dll: File?): LsfgLaunch {
        dir.mkdirs()
        val name = "lsfg-vk-${PrivateRuntimeDir.token()}"
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(configToml(multiplier, flowScale, performance, dll))
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        val f = File(dir, "$name.toml")
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
        f.deleteOnExit()
        return LsfgLaunch(f, multiplier.coerceIn(2, 4))
    }

    /** A TOML basic string: backslash, quote and every control character escaped (a path may hold a newline). */
    internal fun tomlString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 || c.code == 0x7f -> append("\\u%04X".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/lsfg.rs (AGPL-3.0, same author), itself from Enigmata-Temple media/lsfg.rs.
package app.nijiyome.server.link.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/** `capabilities.lsfg` (additive field of Link v1). */
data class LsfgCapability(
    val available: Boolean,
    val experimental: Boolean = true,
    val multipliers: List<Int> = Profiles.LSFG_MULTIPLIERS,
    val autoTargetFps: Int = Profiles.LSFG_AUTO_TARGET_FPS,
    val reason: String? = null,
)

/** Helper and library LSFG runs with. [toString] hides the paths. */
data class LsfgPaths(val helper: File, val dll: File) {
    override fun toString(): String = "LsfgPaths(..)"
}

data class ProbeInfo(val device: String, val fp16: Boolean)

/** A probe result: the GPU, or the reason code. */
sealed interface ProbeResult {
    data class Ok(val info: ProbeInfo) : ProbeResult
    data class Failed(val reason: String) : ProbeResult
}

/** Whether LSFG is available on this PC and with what (computed once at start). */
class LsfgStatus private constructor(val paths: LsfgPaths?, val reason: String?, val device: String?) {
    val isAvailable: Boolean get() = paths != null

    fun capability() = LsfgCapability(available = isAvailable, reason = reason)

    override fun toString(): String = "LsfgStatus(available=$isAvailable, reason=$reason)"

    companion object {
        fun unavailable(reason: String) = LsfgStatus(null, reason, null)
        fun available(paths: LsfgPaths, info: ProbeInfo) = LsfgStatus(paths, null, info.device)
        val DISABLED = unavailable(Lsfg.REASON_DISABLED)
    }
}

/** The system the helper and the library are searched in (tests use a fake). */
interface LsfgEnv {
    fun variable(name: String): String?
    /** Folders the helper may live in (next to the executable, its parent, the app's extracted copy), in order. */
    fun helperDirs(): List<File>
    fun isFile(path: File): Boolean
    fun readText(path: File): String?
    /** Default Steam folders, in preference order (they may not exist). */
    fun steamRoots(): List<File>
}

object SystemLsfgEnv : LsfgEnv {
    override fun variable(name: String): String? = System.getenv(name)

    override fun helperDirs(): List<File> = helperDirsFrom(System::getProperty)

    /**
     * The packaged app's resources folder first (`compose.application.resources.dir`: where the AppImage, the Linux tree and the
     * Windows installer / zip put `temple-lsfg[.exe]`), then the jpackage launcher's folder (`<app>/bin/Nijiyome`) or the JVM's own
     * `bin`, and the folder above it (a development layout).
     */
    internal fun helperDirsFrom(property: (String) -> String?): List<File> {
        val resources = property("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let(::File)
        val exe = property("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let(::File)?.parentFile
            ?: File(property("java.home").orEmpty(), "bin")
        return listOfNotNull(resources, exe, exe.parentFile).distinct()
    }

    override fun isFile(path: File): Boolean = path.isFile
    override fun readText(path: File): String? = runCatching { path.readText() }.getOrNull()

    override fun steamRoots(): List<File> =
        if (Platform.current() == Platform.WINDOWS) listOf(File(Lsfg.STEAM_DEFAULT_ROOT))
        else Lsfg.linuxSteamRoots(variable("XDG_DATA_HOME")?.let(::File), variable("HOME")?.let(::File))
}

/**
 * LSFG (Lossless Scaling Frame Generation) on the PC, experimental. It does not run inside FFmpeg: the `temple-lsfg` helper
 * (native/temple-lsfg, C++ / Vulkan) sits between two FFmpeg ([Args.buildLsfg]). It needs the helper and the user's own
 * `Lossless.dll` (found in Steam or set by the user). `temple-lsfg --probe --dll <path>` decides at start whether it works here.
 * Nothing logs paths: reasons are codes.
 */
object Lsfg {
    /** Absolute path of `temple-lsfg` (development override). */
    const val HELPER_ENV = "TEMPLE_LSFG"
    /** Every Vulkan process Link spawns gets it: the implicit lsfg-vk layer (when installed) stays out. */
    val VULKAN_EXTRA_ENV = mapOf("DISABLE_LSFGVK" to "1")
    val HELPER_FILE_NAME: String get() = if (Platform.current() == Platform.WINDOWS) "temple-lsfg.exe" else "temple-lsfg"
    const val DLL_FILE_NAME = "Lossless.dll"
    const val STEAM_DEFAULT_ROOT = "C:\\Program Files (x86)\\Steam"
    const val PROBE_TIMEOUT_MS = 20_000L
    private const val MAX_VDF_BYTES = 1024 * 1024

    const val REASON_DISABLED = "disabled"
    const val REASON_ENGINE = "engine_unavailable"
    const val REASON_HELPER = "helper_missing"
    const val REASON_DLL = "dll_missing"
    const val REASON_PROBE = "probe_failed"
    val HELPER_ERRORS = listOf("dll_unreadable", "missing_spirv", "no_vulkan", "device_unsupported")

    /** `$XDG_DATA_HOME/Steam` (absolute only; else `$HOME/.local/share/Steam`), `~/.steam/steam`, `~/.steam/root`, Flatpak. */
    fun linuxSteamRoots(xdgDataHome: File?, home: File?): List<File> {
        val xdg = xdgDataHome?.takeIf { it.isAbsolute }
        val h = home?.takeIf { it.isAbsolute }
        val roots = mutableListOf<File>()
        when {
            xdg != null -> roots += File(xdg, "Steam")
            h != null -> roots += File(h, ".local/share/Steam")
        }
        if (h != null) {
            roots += File(h, ".steam/steam")
            roots += File(h, ".steam/root")
            roots += File(h, ".var/app/com.valvesoftware.Steam/.local/share/Steam")
        }
        return roots.distinct()
    }

    /** `TEMPLE_LSFG` when set (and a file; a broken value does not fall back), else the first [LsfgEnv.helperDirs] with it. */
    fun findHelper(env: LsfgEnv): File? {
        env.variable(HELPER_ENV)?.takeIf { it.isNotEmpty() }?.let { v ->
            val c = File(v)
            return c.takeIf { it.isAbsolute && env.isFile(it) }
        }
        return env.helperDirs().map { File(it, HELPER_FILE_NAME) }.firstOrNull { it.isAbsolute && env.isFile(it) }
    }

    /** Absolute library paths of a `libraryfolders.vdf`, unescaped. */
    fun parseLibraryFolders(vdf: String): List<File> = vdf.lines().mapNotNull { line ->
        val strings = quotedStrings(line.trim())
        val key = strings.getOrNull(0) ?: return@mapNotNull null
        val value = strings.getOrNull(1) ?: return@mapNotNull null
        if (!key.equals("path", ignoreCase = true)) return@mapNotNull null
        File(value).takeIf { it.isAbsolute }
    }

    internal fun quotedStrings(line: String): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] != '"') {
                i++
                continue
            }
            i++
            val value = StringBuilder()
            while (i < line.length) {
                val c = line[i++]
                if (c == '"') break
                if (c == '\\' && i < line.length) {
                    when (val n = line[i++]) {
                        '\\' -> value.append('\\')
                        '"' -> value.append('"')
                        else -> value.append('\\').append(n)
                    }
                } else {
                    value.append(c)
                }
            }
            found += value.toString()
        }
        return found
    }

    /** Each default Steam folder followed by the libraries of its `libraryfolders.vdf`, without repeats. */
    fun steamDllCandidates(env: LsfgEnv): List<File> {
        val roots = mutableListOf<File>()
        for (root in env.steamRoots()) {
            if (root !in roots) roots += root
            val text = env.readText(File(root, "steamapps/libraryfolders.vdf"))?.takeIf { it.length <= MAX_VDF_BYTES } ?: continue
            parseLibraryFolders(text).forEach { if (it !in roots) roots += it }
        }
        return roots.map { File(it, "steamapps/common/Lossless Scaling/$DLL_FILE_NAME") }
    }

    /** The configured path (absolute and a file; no other is searched then) or the first existing Steam candidate. */
    fun findDll(env: LsfgEnv, configured: String?): File? {
        val c = configured?.trim()?.takeIf { it.isNotEmpty() }
        if (c != null) return File(c).takeIf { it.isAbsolute && env.isFile(it) }
        return steamDllCandidates(env).firstOrNull { env.isFile(it) }
    }

    /** One JSON line `{"ok":true,"device":…,"fp16":…}` or `{"ok":false,"error":"<code>"}`; anything else is `probe_failed`. */
    fun parseProbeOutput(stdout: String, exitOk: Boolean): ProbeResult {
        val failed = ProbeResult.Failed(REASON_PROBE)
        val line = stdout.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return failed
        val o = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return failed
        val ok = o["ok"]?.jsonPrimitive?.booleanOrNull ?: return failed
        if (ok) {
            val device = o["device"]?.jsonPrimitive?.contentOrNull ?: return failed
            if (!exitOk || device.any { it.isISOControl() }) return failed
            return ProbeResult.Ok(ProbeInfo(device, o["fp16"]?.jsonPrimitive?.booleanOrNull ?: false))
        }
        val code = o["error"]?.jsonPrimitive?.contentOrNull
        return if (code != null && code in HELPER_ERRORS) ProbeResult.Failed(code) else failed
    }

    /** The first missing piece is the reported reason. */
    fun detect(env: LsfgEnv, configuredDll: String?, probe: (File, File) -> ProbeResult): LsfgStatus {
        val helper = findHelper(env) ?: return LsfgStatus.unavailable(REASON_HELPER)
        val dll = findDll(env, configuredDll) ?: return LsfgStatus.unavailable(REASON_DLL)
        return when (val r = probe(helper, dll)) {
            is ProbeResult.Ok -> LsfgStatus.available(LsfgPaths(helper, dll), r.info)
            is ProbeResult.Failed -> LsfgStatus.unavailable(r.reason)
        }
    }

    /** The real probe: `temple-lsfg --probe --dll <path>` within [PROBE_TIMEOUT_MS], reading its single stdout line. Blocking. */
    fun runProbe(helper: File, dll: File): ProbeResult {
        val pb = ProcessBuilder(helper.path, "--probe", "--dll", dll.path).redirectError(ProcessBuilder.Redirect.DISCARD)
        pb.environment().putAll(VULKAN_EXTRA_ENV)
        val process = try {
            pb.start()
        } catch (_: Exception) {
            return ProbeResult.Failed(REASON_PROBE)
        }
        process.outputStream.close()
        if (!process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return ProbeResult.Failed(REASON_PROBE)
        }
        val out = process.inputStream.use { it.readNBytes(16 * 1024).decodeToString() }
        return parseProbeOutput(out, process.exitValue() == 0)
    }

    fun detectSystem(configuredDll: String?): LsfgStatus = detect(SystemLsfgEnv, configuredDll, ::runProbe)
}

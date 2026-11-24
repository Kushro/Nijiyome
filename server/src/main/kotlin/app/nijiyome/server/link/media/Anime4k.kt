// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/anime4k.rs (AGPL-3.0, same author), which follows Enigmata-Temple media/anime4k.rs.
package app.nijiyome.server.link.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Anime4K 4.0 as a libplacebo custom shader. The 14 pass files (`*.glsl`, MIT / Unlicense) are not embedded: the app passes the
 * directory that holds them. For each mode / variant the passes are concatenated into one `.hook` written to the FFmpeg working
 * directory and referenced RELATIVELY (no path in the filter string).
 */
object Anime4k {
    const val VERSION = "4.0"

    /** `anime4k-v4.0-a-m.hook`: ASCII, nothing that needs escaping in an FFmpeg filter string. */
    fun shaderFileName(mode: Anime4kMode, variant: Anime4kVariant): String = "anime4k-v$VERSION-${mode.wire}-${variant.wire}.hook"

    internal fun passes(mode: Anime4kMode, q: Anime4kVariant): List<String> {
        val v = q.wire.uppercase()
        val clamp = "Anime4K_Clamp_Highlights"
        val down = "Anime4K_AutoDownscalePre_x2"
        val up = "Anime4K_Upscale_CNN_x2_$v"
        return when (mode) {
            Anime4kMode.A -> listOf(clamp, "Anime4K_Restore_CNN_$v", up, down, up)
            Anime4kMode.B -> listOf(clamp, "Anime4K_Restore_CNN_Soft_$v", up, down, up)
            Anime4kMode.C -> listOf(clamp, "Anime4K_Upscale_Denoise_CNN_x2_$v", down, up)
        }
    }

    /** All 14 pass names (without extension) the shaders directory must contain. */
    fun requiredFiles(): List<String> = buildList {
        add("Anime4K_Clamp_Highlights")
        add("Anime4K_AutoDownscalePre_x2")
        for (q in listOf("S", "M", "L")) {
            add("Anime4K_Restore_CNN_$q")
            add("Anime4K_Restore_CNN_Soft_$q")
            add("Anime4K_Upscale_CNN_x2_$q")
            add("Anime4K_Upscale_Denoise_CNN_x2_$q")
        }
    }

    /** The file of pass [name]; an `_L` pass falls back to its `_VL` file (mpv-anime-build carries only `VL`). */
    private fun passFile(dir: File, name: String): File? {
        File(dir, "$name.glsl").takeIf { it.isFile }?.let { return it }
        val stem = name.removeSuffix("_L").takeIf { it != name } ?: return null
        return File(dir, "${stem}_VL.glsl").takeIf { it.isFile }
    }

    fun chainPresent(dir: File, mode: Anime4kMode, variant: Anime4kVariant): Boolean = passes(mode, variant).all { passFile(dir, it) != null }

    /** The 1080p chain (mode A, `M` networks): the minimum for any Anime4K profile. */
    fun shadersPresent(dir: File): Boolean = chainPresent(dir, Anime4kMode.A, Anime4kVariant.M)

    /** Verifies the directory against `anime4k.lock.json` (`files[].path`, `sha256`); returns the error or null. */
    fun verifyAgainstLock(dir: File, lockJson: String): String? {
        val lock = runCatching { Json.parseToJsonElement(lockJson) as JsonObject }.getOrNull() ?: return "lock is not JSON"
        val files = lock["files"] as? JsonArray ?: return "lock has no files"
        for (f in files) {
            val o = f as? JsonObject ?: return "lock entry without path"
            val name = o["path"]?.jsonPrimitive?.contentOrNull ?: return "lock entry without path"
            if ('/' in name || '\\' in name || ".." in name) return "lock entry '$name' escapes the shaders directory"
            val bytes = runCatching { File(dir, name).readBytes() }.getOrNull() ?: return "missing shader $name"
            val want = o["sha256"]?.jsonPrimitive?.contentOrNull ?: return "lock entry without sha256"
            if (sha256Hex(bytes) != want) return "shader $name does not match the lock"
        }
        return null
    }

    /** The `.hook` text: passes in order, LF line endings, separated by a blank line. */
    fun hookSource(dir: File, mode: Anime4kMode, variant: Anime4kVariant): String = buildString {
        for (p in passes(mode, variant)) {
            val file = passFile(dir, p) ?: throw IOException("$p.glsl")
            append(file.readText().replace("\r\n", "\n"))
            if (!endsWith('\n')) append('\n')
            append('\n')
        }
    }

    /** Writes the `.hook` into [workdir] when absent or different; returns its relative name. */
    fun writeShader(shaders: File, workdir: File, mode: Anime4kMode, variant: Anime4kVariant): String {
        val name = shaderFileName(mode, variant)
        val path = File(workdir, name)
        val hook = hookSource(shaders, mode, variant)
        if (!path.isFile || path.length() != hook.toByteArray().size.toLong()) path.writeText(hook)
        return name
    }
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

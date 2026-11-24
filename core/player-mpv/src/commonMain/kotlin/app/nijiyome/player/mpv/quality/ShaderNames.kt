// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

// 0.4.0 (worker A): turns the planner's [ShaderSpec]s into files mpv can load. Replaces 0.3.x `ShaderPresets.install` (one folder per
// preset): files now live once in a folder keyed by the APK build, so an app update always installs fresh copies (Enigmata's E28 fix:
// a stale copy from a previous APK must never win) and old folders are removed. FP16 Anime4K copies (Enigmata's
// Anime4KShaderOptimizer) live in their own sub-folder, so switching precision changes the path and mpv reloads the shader.

/** Resolves a chain to absolute paths (installing what is missing). Null when a file could not be installed (the chain then stays off). */
fun interface ShaderResolver {
    fun resolve(specs: List<ShaderSpec>, fp16: Boolean): List<String>?
}

object ShaderNames {
    const val FP16_DIR = "fp16-v${Anime4KShaderOptimizer.VERSION}"
    const val GENERATED_DIR = "gen"

    /** Whether [asset] gets an FP16 copy (Enigmata's allowlist: `Anime4K_*` only; FSR / FSRCNNX / SSim / CAS stay verbatim). */
    fun fp16Eligible(asset: String): Boolean = Anime4KShaderOptimizer.shouldOptimize(asset)

    /**
     * Relative path of each pass inside the build folder, in chain order. A file used twice in one chain (Anime4K FAST = S/S) gets a
     * second copy (`name__1.glsl`), as 0.3.x did with its index prefixes, so each pass is its own file whatever mpv does with a
     * repeated entry of the `glsl-shaders` list.
     */
    fun relativePaths(specs: List<ShaderSpec>, fp16: Boolean): List<String> {
        val seen = HashMap<String, Int>()
        return specs.map { spec ->
            val base = when {
                spec.transform == ShaderTransform.CAS -> "$GENERATED_DIR/${ShaderSources.casFileName(spec.casSharpening)}"
                fp16 && fp16Eligible(spec.asset) -> "$FP16_DIR/${spec.asset}"
                else -> spec.asset
            }
            val n = seen.getOrDefault(base, 0)
            seen[base] = n + 1
            if (n == 0) base else base.removeSuffix(".glsl") + "__$n.glsl"
        }
    }

    /** Whether a chain resolved with [fp16] actually uses an FP16 copy (beyond the cheap Clamp pre-pass, Enigmata's rule). */
    fun usesFp16(specs: List<ShaderSpec>, fp16: Boolean): Boolean =
        fp16 && specs.any { it.transform == ShaderTransform.NONE && fp16Eligible(it.asset) && it.asset != ShaderCatalog.CLAMP }
}

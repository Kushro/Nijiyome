// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File

// Hito 4, paso D: the desktop-only player settings of Settings › Reproductor (the external mpv, its enhanced mode and frame
// generation). They mean nothing on Android, so they live in their own file under the app's data folder instead of AppSettings
// (whose codec Android shares): `<data>/desktop-player.json`.

/**
 * @property mpvPath the mpv executable; null = the first `mpv` on PATH, else a usual location.
 * @property enhanced the external mpv runs mpv-anime-build instead of the user's own mpv config.
 * @property enhancedUosc mpv-anime-build draws its uosc interface (false = mpv's own OSC, the build's profiles stay on its keys).
 * @property frameGen lsfg-vk frame generation (Linux). It runs on the external mpv window whatever the engine setting.
 * @property frameGenMultiplier 0 = automatic (the highest the monitor shows, up to x4), else 2..4.
 * @property frameGenPerformance lsfg-vk's performance mode (lighter model).
 * @property frameGenFlowScale optical-flow resolution scale, 0.25..1.
 * @property lsfgDll the shader DLL (`lsfg-vk.dll` / `Lossless.dll`); null = found automatically.
 * @property volume the last player volume (0..100, both mpv engines); null = never set.
 * @property muted whether the player was muted.
 */
data class DesktopPlayerPrefs(
    val mpvPath: String? = null,
    val enhanced: Boolean = false,
    val enhancedUosc: Boolean = true,
    val frameGen: Boolean = false,
    val frameGenMultiplier: Int = 0,
    val frameGenPerformance: Boolean = false,
    val frameGenFlowScale: Float = LsfgVk.DEFAULT_FLOW_SCALE,
    val lsfgDll: String? = null,
    val volume: Int? = null,
    val muted: Boolean = false,
) {
    internal fun toJson(): String = JsonObject(
        buildMap {
            mpvPath?.let { put("mpv_path", JsonPrimitive(it)) }
            put("enhanced", JsonPrimitive(enhanced))
            put("enhanced_uosc", JsonPrimitive(enhancedUosc))
            put("frame_gen", JsonPrimitive(frameGen))
            put("frame_gen_multiplier", JsonPrimitive(frameGenMultiplier))
            put("frame_gen_performance", JsonPrimitive(frameGenPerformance))
            put("frame_gen_flow_scale", JsonPrimitive(frameGenFlowScale))
            lsfgDll?.let { put("lsfg_dll", JsonPrimitive(it)) }
            volume?.let { put("volume", JsonPrimitive(it)) }
            put("muted", JsonPrimitive(muted))
        },
    ).toString()

    /** Values a hand-edited file may hold out of range, brought back to the accepted ones. */
    fun sanitized(): DesktopPlayerPrefs = copy(
        mpvPath = mpvPath?.trim()?.takeIf { it.isNotEmpty() },
        frameGenMultiplier = frameGenMultiplier.takeIf { it == 0 || it in 2..4 } ?: 0,
        frameGenFlowScale = frameGenFlowScale.takeIf { it.isFinite() }?.coerceIn(0.25f, 1f) ?: LsfgVk.DEFAULT_FLOW_SCALE,
        lsfgDll = lsfgDll?.trim()?.takeIf { it.isNotEmpty() },
        volume = volume?.coerceIn(0, 100),
    )

    companion object {
        internal fun fromJson(text: String): DesktopPlayerPrefs {
            val o = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return DesktopPlayerPrefs()
            fun p(k: String) = o[k] as? JsonPrimitive
            val d = DesktopPlayerPrefs()
            return DesktopPlayerPrefs(
                mpvPath = p("mpv_path")?.contentOrNull,
                enhanced = p("enhanced")?.booleanOrNull ?: d.enhanced,
                enhancedUosc = p("enhanced_uosc")?.booleanOrNull ?: d.enhancedUosc,
                frameGen = p("frame_gen")?.booleanOrNull ?: d.frameGen,
                frameGenMultiplier = p("frame_gen_multiplier")?.intOrNull ?: d.frameGenMultiplier,
                frameGenPerformance = p("frame_gen_performance")?.booleanOrNull ?: d.frameGenPerformance,
                frameGenFlowScale = p("frame_gen_flow_scale")?.floatOrNull ?: d.frameGenFlowScale,
                lsfgDll = p("lsfg_dll")?.contentOrNull,
                volume = p("volume")?.intOrNull,
                muted = p("muted")?.booleanOrNull ?: d.muted,
            ).sanitized()
        }
    }
}

/** [DesktopPlayerPrefs] kept in [file]; every [update] is written at once (write-then-rename). */
class DesktopPlayerPrefsStore(private val file: File) {
    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<DesktopPlayerPrefs> = _prefs.asStateFlow()

    private fun load(): DesktopPlayerPrefs =
        runCatching { DesktopPlayerPrefs.fromJson(file.readText()) }.getOrDefault(DesktopPlayerPrefs())

    fun update(change: (DesktopPlayerPrefs) -> DesktopPlayerPrefs) {
        _prefs.update { change(it).sanitized() }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(_prefs.value.toJson())
            if (!tmp.renameTo(file)) {
                file.writeText(_prefs.value.toJson())
                tmp.delete()
            }
        }
    }
}

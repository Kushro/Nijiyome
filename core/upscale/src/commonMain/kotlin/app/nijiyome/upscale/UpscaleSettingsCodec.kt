// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Bounds of [ImageUpscaleSettings.cacheMb] (plan §4.5). */
const val UPSCALE_CACHE_MIN_MB = 64
const val UPSCALE_CACHE_MAX_MB = 2048

/** Only Real-ESRGAN ships in v1.3.3: a stored ANIME4K (phase 2) reads as Real-ESRGAN, and the cache size stays in range. */
fun ImageUpscaleSettings.sanitized(): ImageUpscaleSettings = copy(
    engine = UpscaleEngineId.REAL_ESRGAN,
    cacheMb = cacheMb?.coerceIn(UPSCALE_CACHE_MIN_MB, UPSCALE_CACHE_MAX_MB),
)

/** The cache cap in bytes, with the platform default when the user did not choose one. */
fun ImageUpscaleSettings.cacheBytes(limits: UpscaleLimits): Long = UpscaleResultCache.megabytes(cacheMb ?: limits.defaultCacheMb)

/**
 * JSON of [ImageUpscaleSettings] (the `image_upscale_json` value). Reading is lenient field by field: unknown keys are ignored, a
 * field with an unknown enum value or the wrong type takes its default, unknown entries of `kinds` are dropped, and anything that
 * is not a JSON object reads as the defaults. The result is always [sanitized].
 */
object UpscaleSettingsCodec {
    private val json = Json { encodeDefaults = true }

    fun encode(settings: ImageUpscaleSettings): String = json.encodeToString(ImageUpscaleSettings.serializer(), settings.sanitized())

    fun decode(raw: String?): ImageUpscaleSettings {
        val d = ImageUpscaleSettings()
        val obj = raw?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return d
        return ImageUpscaleSettings(
            enabled = obj.bool("enabled") ?: d.enabled,
            engine = obj.enumOf<UpscaleEngineId>("engine") ?: d.engine,
            quality = obj.enumOf<UpscaleQuality>("quality") ?: d.quality,
            kinds = (obj["kinds"] as? JsonArray)?.mapNotNull { enumOf<UpscaleImageKind>(it) }?.toSet() ?: d.kinds,
            cacheMb = when (val v = obj["cacheMb"]) {
                null, JsonNull -> d.cacheMb
                else -> (v as? JsonPrimitive)?.intOrNull ?: d.cacheMb
            },
            onlyWhileCharging = obj.bool("onlyWhileCharging") ?: d.onlyWhileCharging,
        ).sanitized()
    }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private inline fun <reified E : Enum<E>> JsonObject.enumOf(key: String): E? = this[key]?.let { enumOf<E>(it) }

    private inline fun <reified E : Enum<E>> enumOf(element: JsonElement): E? {
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return enumValues<E>().firstOrNull { it.name == name }
    }
}

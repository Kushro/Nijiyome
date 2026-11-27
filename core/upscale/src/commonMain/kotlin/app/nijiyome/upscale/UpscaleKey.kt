// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import java.security.MessageDigest

/**
 * Cache and state keys (plan §4.5).
 *
 * A key is `sha256(url, kind, engine, model version, quality, target rounded up to 64 px)`. The model factor is not in it on purpose:
 * [UpscaleQueue.cached] must answer before the source is decoded, and for one URL the factor follows from the quality and the target.
 * The factor actually used and the source content hash are stored with each entry ([UpscaleCacheEntry]); a changed source no longer
 * matches its entry's hash and the entry is dropped (see [UpscaleResultCache.lookup] and [UpscaleQueue.sourceRefreshed]).
 */
object UpscaleKey {
    /** Bump when a model file or the tiling changes: every key changes with it. */
    const val MODEL_VERSION = "animevideov3-1"

    const val TARGET_BUCKET = 64

    fun of(url: String, kind: UpscaleImageKind, settings: ImageUpscaleSettings, targetWidth: Int, targetHeight: Int): String {
        val raw = listOf(
            url,
            kind.name,
            settings.engine.name,
            MODEL_VERSION,
            settings.quality.name,
            bucket(targetWidth).toString(),
            bucket(targetHeight).toString(),
        ).joinToString("\u0000")
        return sha256Hex(raw.encodeToByteArray())
    }

    fun bucket(px: Int): Int = if (px <= 0) 0 else ((px + TARGET_BUCKET - 1) / TARGET_BUCKET) * TARGET_BUCKET

    /** The hash that identifies a source's bytes. */
    fun sourceHash(bytes: ByteArray): String = sha256Hex(bytes)

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}

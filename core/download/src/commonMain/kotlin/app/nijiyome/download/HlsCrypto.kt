// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-128 (CBC, PKCS#7) of HLS `#EXT-X-KEY:METHOD=AES-128` segments (RFC 8216 §5.2). Pure, JVM-tested. */
internal object HlsCrypto {
    /** Explicit `IV=0x…` or, when absent, the media sequence number as a 16-byte big-endian integer. */
    fun iv(hex: String?, sequence: Long): ByteArray {
        val out = ByteArray(16)
        if (hex != null) {
            val clean = hex.removePrefix("0x").removePrefix("0X").padStart(32, '0').takeLast(32)
            for (i in 0 until 16) out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        } else {
            for (i in 0 until 8) out[15 - i] = (sequence ushr (8 * i)).toByte()
        }
        return out
    }

    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}

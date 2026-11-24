// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import java.io.File
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * v1.1, Android libmpv's TLS options. Off (the default, `AppSettings.mpvTlsVerify`): `tls-verify=no`, as every version before.
 * On with a CA bundle: `tls-verify=yes` and `tls-ca-file` (libmpv's FFmpeg uses mbedTLS and ships no CA store, so the system's CAs
 * are handed over as a PEM file, see [pemBundle]).
 *
 * On without a usable bundle (it could not be written, or it was deleted and could not be written again): fail closed, never back to
 * `tls-verify=no` while the setting shows on. mpv keeps `tls-verify=yes` with no CA (nothing verifies, so no HTTPS stream opens), and
 * [mpvTlsBlocksLoad] stops an HTTPS load before mpv starts it, with [TLS_CA_UNAVAILABLE] as the error the player shows (a watched
 * stream moves to Media3, which verifies certificates itself).
 */
internal fun mpvTlsOptions(verify: Boolean, caFile: File?): List<Pair<String, String>> = when {
    !verify -> listOf("tls-verify" to "no")
    usableBundle(caFile) -> listOf("tls-verify" to "yes", "tls-ca-file" to caFile!!.absolutePath)
    else -> listOf("tls-verify" to "yes")
}

/** Whether a load of [url] must not start: the check is on, there is no usable bundle, and the URL is HTTPS. */
internal fun mpvTlsBlocksLoad(verify: Boolean, caFile: File?, url: String): Boolean =
    verify && !usableBundle(caFile) && url.trimStart().startsWith("https://", ignoreCase = true)

/** Error of an HTTPS load stopped by [mpvTlsBlocksLoad]. */
internal const val TLS_CA_UNAVAILABLE = "TLS: the system certificates for \"Verify certificates (libmpv)\" could not be prepared"

private fun usableBundle(file: File?): Boolean = file != null && file.isFile && file.length() > 0

/**
 * A file written once and reused while it is still there: [get] writes it again when it was deleted or emptied (Android clears the
 * cache folder on "Clear cache" or under storage pressure) instead of handing back a path that no longer exists. null = [write] failed.
 */
internal class RegeneratedFile(private val write: () -> File?) {
    @Volatile
    private var file: File? = null

    fun get(): File? {
        file?.takeIf(::usableBundle)?.let { return it }
        return synchronized(this) {
            file?.takeIf(::usableBundle) ?: runCatching(write).getOrNull()?.takeIf(::usableBundle).also { file = it }
        }
    }
}

/** [certs] as one PEM file (`-----BEGIN CERTIFICATE-----` blocks, 64 columns), what `tls-ca-file` reads. */
internal fun pemBundle(certs: Collection<X509Certificate>): String = buildString {
    val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
    certs.forEach { cert ->
        append("-----BEGIN CERTIFICATE-----\n")
        append(encoder.encodeToString(cert.encoded))
        append("\n-----END CERTIFICATE-----\n")
    }
}

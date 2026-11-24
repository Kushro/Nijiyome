// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import android.content.Context
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * v1.1: the system's CA certificates as a PEM file in the app's cache, for libmpv's `tls-ca-file` ([mpvTlsOptions]). Only the
 * `system:` entries of `AndroidCAStore`, the CAs OkHttp / Media3 trust by default (user-added CAs are not trusted by apps that do not
 * opt in). Written the first time a player with the check on starts, and written again whenever it is gone (the cache was cleared);
 * null when it cannot be written (the engine then fails closed, see [mpvTlsOptions]).
 */
internal object SystemCaBundle {
    @Volatile
    private var bundle: RegeneratedFile? = null

    fun file(context: Context): File? {
        val app = context.applicationContext
        val current = bundle ?: synchronized(this) { bundle ?: RegeneratedFile { write(app) }.also { bundle = it } }
        return current.get()
    }

    private fun write(context: Context): File? = runCatching {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val certs = store.aliases().toList().filter { it.startsWith("system:") }.mapNotNull { store.getCertificate(it) as? X509Certificate }
        if (certs.isEmpty()) return null
        val dir = File(context.cacheDir, "mpv").apply { mkdirs() }
        val target = File(dir, "system-ca.pem")
        val tmp = File(dir, "system-ca.pem.tmp")
        tmp.writeText(pemBundle(certs))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) return null
        }
        target
    }.getOrNull()
}

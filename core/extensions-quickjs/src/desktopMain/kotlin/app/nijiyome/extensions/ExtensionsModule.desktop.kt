// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import app.nijiyome.network.FlareSolverrInterceptor
import app.nijiyome.network.outbound
import app.nijiyome.extensions.browser.DesktopHiddenBrowser
import app.nijiyome.model.EmbedSniffer
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Koin bindings of the extension host on desktop: extensions live under [dataDir], downloads under [cacheDir]. The hidden browser
 * (1.1, F4) is the user's Chrome / Edge / Chromium driven over CDP ([DesktopHiddenBrowser]): while none is found bundles get no browser
 * (the Extensions screen says so on those extensions) and embed pages are not offered, as before.
 */
fun desktopExtensionsModule(dataDir: File, cacheDir: File): Module = module {
    includes(
        extensionsModule {
            ExtensionsPlatform(
                filesDir = dataDir,
                cacheDir = cacheDir,
                openUri = ::openDesktopUri,
                logWarn = { tag, msg -> System.err.println("W/$tag: $msg") },
                logDebug = { _, _ -> },
                embedPages = { get<DesktopHiddenBrowser>().sniffer.available },
                // Settings › Red: the outbound proxy and a Cloudflare challenge solved once through FlareSolverr (as Tatakai Next's ExtOutbound)
                configureHttp = {
                    outbound()
                    addInterceptor(FlareSolverrInterceptor())
                },
            )
        },
    )
    single { DesktopHiddenBrowser(File(dataDir, DesktopHiddenBrowser.PREFS_FILE), logs = get()) } onClose { b ->
        b?.let { runBlocking { it.host.shutdown() } }
    }
    single<BrowserRuntime> { get<DesktopHiddenBrowser>().runtime }
    single<EmbedSniffer> { get<DesktopHiddenBrowser>().sniffer }
}

// Two or more characters before the colon: a Windows drive ("C:\\x") is a path, not a scheme.
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+:")

/** A `.kai` picked on desktop: a plain path (Linux or Windows) or a `file:` URI. Any other scheme is refused. */
internal fun openDesktopUri(uri: String): InputStream {
    val file = when {
        uri.startsWith("file:", ignoreCase = true) -> fileOfUri(uri)
        URI_SCHEME.containsMatchIn(uri) -> throw IOException("unsupported uri scheme")
        else -> File(uri)
    }
    return FileInputStream(file)
}

/** `file:///x`, `file://localhost/x` and `file:/x`, with or without percent-encoding. A remote host is refused. */
private fun fileOfUri(uri: String): File {
    val parsed = try {
        URI(uri.replace(" ", "%20"))
    } catch (e: java.net.URISyntaxException) {
        throw IOException("malformed file uri", e)
    }
    val host = parsed.host
    if (!host.isNullOrEmpty() && !host.equals("localhost", ignoreCase = true)) throw IOException("unsupported uri host")
    val path = parsed.path?.takeIf { it.isNotEmpty() } ?: throw IOException("malformed file uri")
    return File(path)
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import android.content.Context
import android.net.Uri
import android.util.Log
import app.nijiyome.model.EmbedSniffer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Koin bindings of the extension host on Android (hidden-WebView browser and embed sniffer included). Requires `androidContext()`. */
val extensionsModule: Module = module {
    includes(
        extensionsModule {
            val context: Context = androidContext()
            ExtensionsPlatform(
                filesDir = context.filesDir,
                cacheDir = context.cacheDir,
                openUri = { uri ->
                    val parsed = Uri.parse(uri)
                    when (parsed.scheme) {
                        "content" -> context.contentResolver.openInputStream(parsed) ?: throw IOException("cannot open")
                        "file", null -> FileInputStream(File(parsed.path ?: uri))
                        else -> throw IOException("unsupported uri scheme")
                    }
                },
                logWarn = { tag, msg -> Log.w(tag, msg) },
                logDebug = { tag, msg -> Log.d(tag, msg) },
            )
        },
    )
    // android wave (12 §3.7): hidden-WebView browser runtime for bundles with permitted browserDomains.
    single<BrowserRuntime> { WebViewBrowserRuntime(androidContext(), get()) }
    // parity wave B (16 §3.3, worker t): embed sniffer (hidden WebView).
    single<EmbedSniffer> { WebViewEmbedSniffer(androidContext(), get()) }
}

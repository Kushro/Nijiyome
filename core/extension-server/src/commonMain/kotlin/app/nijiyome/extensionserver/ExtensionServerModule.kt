// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensionserver

import app.nijiyome.extensions.ExternalCandidateSource
import app.nijiyome.extensions.ExternalCustomSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * A `nijiyome://extensionserver/connect?…` link opened from outside (QR scanned by the camera app, a browser) waiting for the connect
 * screen. The screen asks the user before pairing: opening a link never connects on its own.
 */
class PendingServerLink {
    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(raw: String) {
        _link.value = raw
    }

    /** Returns and clears the pending link. */
    fun consume(): String? = _link.value.also { _link.value = null }
}

/**
 * Koin bindings of the extension server client (docs/redesign/13 §3.10) for a platform: [store] is where the sealed connections live
 * (Android: AndroidKeyStore-sealed file under `noBackupFilesDir`) and [deviceLabel] the name this device announces when pairing.
 * Requires the shared [OkHttpClient]. Android: `val extensionServerModule` (androidMain).
 */
fun extensionServerModule(store: Scope.() -> ExtensionServerStore, deviceLabel: Scope.() -> String): Module = module {
    // Derived from the shared client (User-Agent) without its HTTP cache and WITHOUT redirects: a redirect could leave the guarded
    // origin. No call timeout, so /resolve can stream; ServerCandidateSource bounds it. Short connect timeout: a PC that is off fails fast.
    single(named(HTTP)) {
        get<OkHttpClient>().newBuilder()
            .cache(null)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            // BUG-011: a provider on the PC can stay silent > 30 s between two /resolve snapshots (Toko: ~31 s on a first resolve).
            .readTimeout(75, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
    single { ExtensionServerClient(get(named(HTTP))) }
    single { store() }
    single { ExtensionServerRepositoryImpl(store = get(), client = get(), deviceLabel = deviceLabel()) }
    single<ExtensionServerRepository> { get<ExtensionServerRepositoryImpl>() }
    single { ServerCandidateSource(repository = get(), client = get()) } bind ExternalCandidateSource::class
    // parity wave B (16 §3.4, worker t; ext.ecosystems.via-server): the PC servers' custom sources as provider pages.
    single { ServerCustomSources(repository = get(), client = get()) } bind ExternalCustomSources::class
    single { PendingServerLink() }
}

private const val HTTP = "extension-server-http"

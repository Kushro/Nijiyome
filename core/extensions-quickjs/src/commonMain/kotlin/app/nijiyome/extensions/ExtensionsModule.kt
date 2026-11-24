// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.EmbedSniffer
import app.nijiyome.model.ExtLogLevel
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceStatsStore
import app.nijiyome.model.StremioAddonRepository
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.io.InputStream

/**
 * What the extension host needs from the platform: where its files live, how a `.kai` picked by the user is opened ([openUri]
 * receives a content URI on Android, a path or `file:` URI on desktop) and where the console of the extensions is logged.
 */
class ExtensionsPlatform(
    val filesDir: File,
    val cacheDir: File,
    val openUri: (String) -> InputStream,
    val logWarn: (tag: String, message: String) -> Unit,
    val logDebug: (tag: String, message: String) -> Unit,
    /**
     * Whether embed pages can be resolved here right now (an [EmbedSniffer] that can open a browser). Desktop: only while a Chrome /
     * Edge / Chromium is found; otherwise its embed candidates never reach the source selector, where they would always fail and lower
     * the source's reliability. Android: true, as always.
     */
    val embedPages: () -> Boolean = { true },
    /** Adds to the extensions' own OkHttp client (desktop: the outbound proxy and FlareSolverr of Settings › Red; Android: nothing). */
    val configureHttp: OkHttpClient.Builder.() -> Unit = {},
)

/**
 * Koin bindings of the extension host shared by Android and desktop. The platform module adds the hidden browser
 * ([BrowserRuntime]) and the [EmbedSniffer] when it has them; both are optional here.
 */
fun extensionsModule(platform: Scope.() -> ExtensionsPlatform): Module = module {
    single { platform() }
    // android wave (12 §3.7 / §3.16): per-extension log, hidden-WebView browser runtime for bundles with permitted browserDomains.
    single { ExtensionLogStore() }
    single<JsEngine> { QuickJsEngine(browserRuntime = getOrNull<BrowserRuntime>()) }
    // Deliberately a fresh client: nothing from the app's own network stack (auth headers, OAuth tokens) may reach extensions.
    single(named("extensions-http")) { OkHttpClient.Builder().apply(get<ExtensionsPlatform>().configureHttp).build() } onClose { client ->
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
    }
    single {
        val client = get<OkHttpClient>(named("extensions-http"))
        val logs = get<ExtensionLogStore>()
        val env = get<ExtensionsPlatform>()
        ExtensionHost(
            engine = get(),
            hostFor = { manifest ->
                OkHttpJsHost(
                    manifest.networkPolicy,
                    client,
                    onLog = { level, msg ->
                        if (level == "error" || level == "warn") {
                            env.logWarn("Ext:${manifest.id}", msg)
                            logs.append(manifest.id, if (level == "error") ExtLogLevel.ERR else ExtLogLevel.WARN, "console", msg)
                        }
                    },
                    onEvent = { level, op, msg -> logs.append(manifest.id, level, op, msg) },
                    onDebug = { msg -> env.logDebug("Ext:${manifest.id}", msg) },
                )
            },
        )
    }
    // parity wave B (16 §3.3 / §3.4, worker t): native Stremio addons (one more ExternalCandidateSource), their reset wiper.
    // `StremioCandidateSource` is bound BEFORE the repository so `getAll<ExternalCandidateSource>()` below sees it.
    single {
        StremioAddonRepositoryImpl(
            get(named("extensions-http")),
            File(get<ExtensionsPlatform>().filesDir, StremioAddonRepositoryImpl.DIR_NAME),
            get(),
        )
    }
    single<StremioAddonRepository> { get<StremioAddonRepositoryImpl>() }
    single { StremioCandidateSource(repository = get(), client = get(named("extensions-http"))) } bind ExternalCandidateSource::class
    single(named("stremio-wiper")) { AppDataWiper { get<StremioAddonRepositoryImpl>().wipe() } } bind AppDataWiper::class
    single {
        val env = get<ExtensionsPlatform>()
        val root = File(env.filesDir, "extensions").also { it.mkdirs() }
        ExtensionRepositoryImpl(
            rootDir = root,
            openUri = env.openUri,
            host = get(),
            settings = get<SettingsRepository>(),
            stats = get<SourceStatsStore>(),
            logStore = get(),
            // docs/redesign/13 §3.10: linked PC extension servers (:core:extension-server binds ExternalCandidateSource) + Stremio addons.
            external = getAll<ExternalCandidateSource>(),
            // parity wave B (16 §3.4, worker t): URL installs / repositories over the extensions' own client, SECRET preferences,
            // embed pages of custom/watch through the sniffer, custom sources of the PC servers (ExternalCustomSources).
            http = get(named("extensions-http")),
            secrets = getOrNull<SecretStore>(),
            downloadDir = File(env.cacheDir, "extension-downloads"),
            embedSniffer = { getOrNull<EmbedSniffer>() },
            embedPages = env.embedPages,
            externalCustom = getAll<ExternalCustomSources>(),
        )
    }
    single<ExtensionRepository> { get<ExtensionRepositoryImpl>() }
    single(named("extension-catalog-wiper")) { AppDataWiper { get<ExtensionRepositoryImpl>().wipeCatalog() } } bind AppDataWiper::class
}

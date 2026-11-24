package app.nijiyome.metadata

import app.nijiyome.metadata.providers.AniListProvider
import app.nijiyome.metadata.providers.AniZipProvider
import app.nijiyome.metadata.providers.KitsuProvider
import app.nijiyome.metadata.providers.MalProvider
import app.nijiyome.metadata.providers.ShikimoriProvider
import app.nijiyome.metadata.providers.TenraiProvider
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** Supplies the user's enabled provider ids in priority order (implemented in :core:data from AppSettings). */
fun interface ProviderPriority {
    fun current(): List<String>
}

/** Koin property name of the MAL client id (defined by :core:data as PROP_MAL_CLIENT_ID; repeated here so :core:metadata stays JVM-only). */
const val METADATA_PROP_MAL_CLIENT_ID: String = "NIJIYOME_MAL_CLIENT_ID"

/** Requires an [OkHttpClient] (networkModule) and a [ProviderPriority] (dataModule). Android: Shikimori is told "android". */
val metadataModule: Module = metadataModule(platform = ShikimoriProvider.ANDROID)

/**
 * [metadataModule] for a [platform] label ("android", "linux", "windows"): the User-Agent Shikimori requires names it
 * (`Nijiyome (<platform>)`).
 */
fun metadataModule(platform: String): Module = module {
    single { AniListProvider(get<OkHttpClient>()) }
    single { AniZipProvider(get<OkHttpClient>()) }
    single { KitsuProvider(get<OkHttpClient>()) }
    // parity wave B (16 §3.1, worker r, plan 14 §0.1 #13): MAL / Shikimori / Tenrai in the router (stubs without capabilities until r).
    single { MalProvider(get<OkHttpClient>(), clientId = { getPropertyOrNull<String>(METADATA_PROP_MAL_CLIENT_ID) }) }
    single { ShikimoriProvider(get<OkHttpClient>(), platform = platform) }
    single { TenraiProvider(get<OkHttpClient>()) }
    // parity wave A (15 §3.1, worker o): trace.moe image search.
    single { TraceMoeClient(get<OkHttpClient>()) }
    single {
        val priority = get<ProviderPriority>()
        MetadataRouter(
            providers = listOf<MetadataProvider>(
                get<AniListProvider>(), get<AniZipProvider>(), get<KitsuProvider>(),
                get<MalProvider>(), get<ShikimoriProvider>(), get<TenraiProvider>(),
            ),
            priority = { priority.current() },
            imageSearch = get<TraceMoeClient>(),
        )
    }
    single { TrendingFeed(get()) }
}

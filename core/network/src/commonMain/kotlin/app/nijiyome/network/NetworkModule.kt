package app.nijiyome.network

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.util.concurrent.TimeUnit

/** Koin property that overrides the version shown in the User-Agent (optional). */
const val PROP_APP_VERSION = "nijiyome.appVersion"

/**
 * The single shared OkHttp client for third-party public APIs (AniList, ani.zip, Kitsu, images).
 *
 * It deliberately has NO authentication interceptor: the AniList / MAL OAuth tokens live in :core:data (ListsRepositoryImpl adds
 * them per request to graphql.anilist.co / api.myanimelist.net only) and must never be attached to third-party hosts.
 * 0.3.1: AniList requests go through the shared [AniListGateway] (concurrency cap, header-driven budget, 429 handling, dedupe).
 */
object HttpClientFactory {
    const val CACHE_BYTES = 20L * 1024 * 1024

    fun create(
        cacheDir: File?,
        appVersion: String = "dev",
        aniList: AniListGateway? = null,
        platform: String = "Android",
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient =
        OkHttpClient.Builder()
            .apply(configure)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(UserAgentInterceptor("Nijiyome/$appVersion ($platform)"))
            // 0.3.1: every AniList request of the app (metadata, lists, social, airing, manga, novels) shares this limiter.
            .apply { if (aniList != null) addInterceptor(aniList) }
            .apply { if (cacheDir != null) cache(Cache(File(cacheDir, "http"), CACHE_BYTES)) }
            .build()
}

class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        return if (req.header("User-Agent") == null) {
            chain.proceed(req.newBuilder().header("User-Agent", userAgent).build())
        } else {
            chain.proceed(req)
        }
    }
}

/**
 * The network module for a platform: [cacheDir] is where the HTTP cache lives (null = no disk cache) and [platform] is the
 * label in the User-Agent ("Android", "Linux", "Windows"). [configure] adds to the client (desktop: the outbound route of Settings › Red;
 * Android passes nothing).
 */
fun networkModule(platform: String, configure: OkHttpClient.Builder.() -> Unit = {}, cacheDir: Scope.() -> File?): Module = module {
    single { AniListGateway() }
    single<OkHttpClient> {
        HttpClientFactory.create(
            cacheDir = cacheDir(),
            appVersion = getPropertyOrNull<String>(PROP_APP_VERSION) ?: "dev",
            aniList = get<AniListGateway>(),
            platform = platform,
            configure = configure,
        )
    } onClose { client ->
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        runCatching { client?.cache?.close() }
    }
}

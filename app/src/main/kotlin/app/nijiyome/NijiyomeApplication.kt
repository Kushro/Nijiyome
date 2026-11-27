package app.nijiyome

import android.app.Application
import app.nijiyome.upscale.UpscaleQueue
import app.nijiyome.upscale.UpscaleRefreshListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.koin.core.context.GlobalContext
import app.nijiyome.data.AppLogStore
import app.nijiyome.data.HistorySyncScheduler
import app.nijiyome.data.PROP_ANILIST_CLIENT_ID
import app.nijiyome.data.PROP_MAL_CLIENT_ID
import app.nijiyome.data.dataModule
import app.nijiyome.link.LinkLogShipper
import app.nijiyome.link.LinkRepositoryImpl
import app.nijiyome.model.LogPushTrigger
import app.nijiyome.model.LogRepository
import java.io.File
import app.nijiyome.database.databaseModule
import app.nijiyome.discover.discoverModule
import app.nijiyome.download.downloadModule
import app.nijiyome.extensions.extensionsModule
import app.nijiyome.extensionserver.extensionServerModule
import app.nijiyome.feature.details.detailsModule
import app.nijiyome.feature.downloads.downloadsFeatureModule
import app.nijiyome.feature.extensions.extensionsFeatureModule
import app.nijiyome.feature.home.homeModule
import app.nijiyome.feature.library.libraryModule
import app.nijiyome.feature.link.linkFeatureModule
import app.nijiyome.feature.manga.mangaFeatureModule
import app.nijiyome.feature.novel.novelFeatureModule
import app.nijiyome.feature.search.searchModule
import app.nijiyome.feature.settings.settingsModule
import app.nijiyome.feature.social.socialFeatureModule
import app.nijiyome.feature.torrents.torrentsFeatureModule
import app.nijiyome.feature.you.youFeatureModule
import app.nijiyome.framegen.frameGenModule
import app.nijiyome.link.linkModule
import app.nijiyome.manga.mangaModule
import app.nijiyome.metadata.metadataModule
import app.nijiyome.network.networkModule
import app.nijiyome.novel.novelModule
import app.nijiyome.player.HistoryFlush
import app.nijiyome.player.mpv.mpvPlayerModule
import app.nijiyome.player.playerModule
import app.nijiyome.social.socialModule
import app.nijiyome.torrent.torrentModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** Player -> data bridge: flush pending watch history on pause / exit. */
private val appModule = module {
    single<HistoryFlush> { HistoryFlush { get<HistorySyncScheduler>().requestSync(immediate = true) } }
    // 0.4.0 integration: Link "Mixto" needs the phone frame generation (frameGenModule); FG runs on the main thread.
    single<app.nijiyome.model.PhoneFrameGenGate> {
        FrameGenLinkGate(get(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main))
    }
    // Automatically ships scrubbed Android diagnostic logs & crashes to the paired PC via POST /v1/logs.
    single<LogPushTrigger>(createdAtStart = true) {
        val ctx = androidContext()
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        LinkLogShipper(
            link = get<LinkRepositoryImpl>(),
            logs = { getOrNull<LogRepository>() },
            cursorFile = File(ctx.noBackupFilesDir, "link-log-cursor.txt"),
            appVersion = version,
            platform = "android-${android.os.Build.VERSION.RELEASE}",
        ).also { it.start() }
    }
}

/** Every module of the Android app, in load order. A list of its own so tests can inspect what the app registers. */
internal val nijiyomeModules: List<Module> = listOf(
    // B3 - data (linkModule before dataModule: HistorySyncer consumes LinkHistoryRemote)
    networkModule, metadataModule, databaseModule, linkModule, dataModule,
    // B4 - media
    extensionsModule, playerModule,
    // B2 - feature ViewModels
    homeModule, searchModule, detailsModule, libraryModule, settingsModule,
    extensionsFeatureModule, linkFeatureModule,
    appModule,
    // android wave contract (docs/redesign/12 §3.16)
    torrentModule, mpvPlayerModule, torrentsFeatureModule, youFeatureModule,
    // integrated extension server client (docs/redesign/13 §3.10)
    extensionServerModule,
    // parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.8): core modules of r / s / t (all after dataModule and
    // extensionsModule, which they consume), then their feature modules. `getAll<AppDataWiper>()` of AppResetImpl and
    // `getAll<ChapterDownloadSource>()` of DownloadsViewModel resolve lazily, so the order among these does not matter.
    discoverModule, downloadModule, mangaModule, novelModule, socialModule,
    downloadsFeatureModule, mangaFeatureModule, novelFeatureModule, socialFeatureModule,
    // 0.4.0 player quality (worker B): phone frame generation (FgController, used by mpvPlayerModule's sessions)
    frameGenModule,
    // v1.3.3: GPU image upscaling (its pause gates read frameGenModule's FgActiveFlag and mpvPlayerModule's RemoteProcessingState)
    upscaleModule,
)

/**
 * Koin starts here, before WorkManager's default initializer runs (HistorySyncWorker is a KoinComponent), so the
 * default initializer must stay enabled. parity wave A, 2026-11-09 amendment: there is no own login and no hosted backend; the only
 * injected configuration is the pair of optional OAuth client ids (BuildConfig, never from committed files).
 */
class NijiyomeApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Before any activity starts, so the upscale queue's "app hidden" count is right from the first one.
        AppVisibility.register(this)
        startKoin {
            androidContext(this@NijiyomeApplication)
            properties(
                mapOf(
                    // parity wave A (15 §3.3): list provider OAuth client ids (worker q's ListsRepositoryImpl reads them with getProperty)
                    PROP_ANILIST_CLIENT_ID to BuildConfig.NIJIYOME_ANILIST_CLIENT_ID,
                    PROP_MAL_CLIENT_ID to BuildConfig.NIJIYOME_MAL_CLIENT_ID,
                ),
            )
            modules(nijiyomeModules)
        }
        val logRepo = org.koin.core.context.GlobalContext.get().getOrNull<LogRepository>()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        (logRepo as? AppLogStore)?.installCrashHandler(
            appVersion = version,
            platform = "android-${android.os.Build.VERSION.RELEASE}",
        )
    }

    /**
     * v1.3.3 (plan-upscaling §4.2, R3): the app's own image loader, with exactly Coil 3.6.3's defaults. `ImageLoader.Builder(context)`
     * builds what Coil's default singleton builds: the shared default disk cache (`coil3_disk_cache` in the temporary folder, which is
     * this app's cacheDir on Android; 2 % of the volume, 10 to 250 MB), a memory cache of Coil's default share of the app memory and
     * the service-loaded components (the OkHttp fetcher). The only addition is the listener that tells the upscale queue when an
     * image came from the network again.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .eventListener(UpscaleRefreshListener { GlobalContext.getOrNull()?.getOrNull<UpscaleQueue>() })
            .build()
}

// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.AppSettings
import app.nijiyome.model.torrent.TorrentSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.nijiyome.database.HistoryDao
import app.nijiyome.metadata.ProviderPriority
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.AppLog
import app.nijiyome.model.AppReset
import app.nijiyome.model.ListProvider
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.ReadListPuller
import app.nijiyome.model.SecretStore
import app.nijiyome.model.UpdateRepository
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.LogRepository
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceStatsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

private const val DATA_SCOPE = "data"

/** What the desktop app tells the data layer about itself (the packaged version and the platform). */
data class DesktopAppInfo(
    /** The packaged version (`gradle.properties`, or the release tag in CI); empty when unknown (a development run). */
    val version: String = "",
    val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
    /** Top-level folders of the data directory that hold the user's downloads (kept by "Restablecer la app"). */
    val downloadFoldersInData: Set<String> = emptySet(),
)

/**
 * The repositories on desktop. Needs networkModule, metadataModule and databaseModule. Settings live in
 * `<dataDir>/settings.preferences_pb` (the same DataStore format as Android). Lists and accounts use the Koin properties
 * PROP_ANILIST_CLIENT_ID / PROP_MAL_CLIENT_ID (the desktop clients, with the loopback redirect); without one, "Vincular" is disabled.
 */
/**
 * Hito 6 (Tatakai Next `model/mod.rs` / `video.rs`): a new desktop install starts with upscaling off and deband off; Anime4K, when
 * turned on, starts at the balanced tier (M/S, Tatakai Next's "fast"). Android keeps [AppSettings]' defaults. A value already
 * stored (every write stores every key) is kept.
 */
val DESKTOP_SETTINGS_DEFAULTS: AppSettings = AppSettings().let { d ->
    d.copy(
        playerPreset = QualityPresets.OFF,
        playerQuality = d.playerQuality.copy(deband = d.playerQuality.deband.copy(enabled = false)),
        torrent = TorrentSettings.desktopDefaults(),
    )
}

fun desktopDataModule(dataDir: File, cacheDir: File = File(dataDir, "cache"), app: DesktopAppInfo = DesktopAppInfo()): Module = module {
    // Background work that outlives a screen (DataStore writes, stale-while-revalidate refreshes); cancelled when the app closes.
    single(named(DATA_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.IO) } onClose { it?.cancel() }
    // The app log (`<dataDir>/logs/app.log`), installed as AppLog.sink at start like on Android.
    single<LogRepository>(createdAtStart = true) { AppLogStore(File(dataDir, "logs")).also { AppLog.sink = it } }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(scope = get(named(DATA_SCOPE))) { File(dataDir, "settings.preferences_pb") }
    }
    // Created at start: its constructor reads the settings file with runBlocking, which must not happen on the UI thread later.
    single<SettingsRepository>(createdAtStart = true) {
        SettingsRepositoryImpl(get(), scope = get(named(DATA_SCOPE)), defaults = DESKTOP_DEFAULT_SETTINGS)
    }
    single { ProviderPriority { get<SettingsRepository>().settings.value.providerPriority } }
    single<MetadataRepository> {
        val settings = get<SettingsRepository>()
        val historyDao = get<HistoryDao>()
        MetadataRepositoryImpl(
            get(),
            get(),
            ttlHours = { settings.settings.value.metadataCacheTtlHours },
            historyTitle = { ref -> historyDao.newestTitle(ref) },
            refreshScope = get(named(DATA_SCOPE)),
        )
    }
    single<SourceStatsStore> { SourceStatsStoreImpl(get()) }

    // Secrets: a sealed file whose key lives in the system keyring (Secret Service / DPAPI, a 0600 file without one).
    single<SecretStore> { DesktopSecretStore(dataDir, DesktopSecretStore.defaultVaults(dataDir, app.windows)) }
    // Lists and accounts: the shared repository, with the loopback redirect of the desktop OAuth clients.
    single { PendingOAuthRedirect() }
    single { DesktopOAuthLauncher(get()) } onClose { it?.close() }
    single<ListsRepository> {
        val scope = this
        val listSettings = get<SettingsRepository>()
        val anilistId = getPropertyOrNull<String>(PROP_ANILIST_CLIENT_ID)
        val malId = getPropertyOrNull<String>(PROP_MAL_CLIENT_ID)
        ListsRepositoryImpl(
            dao = get(),
            secrets = get(),
            anilist = AniListListClient(get()),
            mal = MalListClient(get()),
            clientIds = { p -> if (p == ListProvider.ANILIST) anilistId else malId },
            scheduler = DesktopListSyncScheduler(get(named(DATA_SCOPE)), lists = { scope.getOrNull<ListsRepository>() }),
            scope = get(named(DATA_SCOPE)),
            readLists = { scope.getAll<ReadListPuller>() },
            writeBack = { listSettings.settings.value.listsWriteBack },
            redirectUri = { LoopbackOAuth.redirectUri() },
        )
    }
    // New-version notice: the same GitHub Releases check, offering a release only when it has this platform's build.
    single<UpdateRepository> {
        val settings = get<SettingsRepository>()
        UpdateRepositoryImpl(
            client = get(),
            installedVersion = app.version,
            enabled = { settings.settings.value.updateCheck && app.version.isNotEmpty() },
            store = get<DataStore<Preferences>>(),
            scope = get(named(DATA_SCOPE)),
            hasBuild = if (app.windows) ::hasWindowsBuild else ::hasLinuxBuild,
        )
    }
    single<AppReset> {
        DesktopAppReset(
            dataDir = dataDir,
            cacheDir = cacheDir,
            settingsStore = get(),
            database = get(),
            secrets = get(),
            metadata = getOrNull(),
            extensions = getOrNull(),
            link = getOrNull(),
            logs = getOrNull(),
            wipers = getAll<AppDataWiper>(),
            keep = DesktopAppReset.DEFAULT_KEEP + app.downloadFoldersInData,
            torrents = { getKoin().getOrNull<app.nijiyome.model.torrent.TorrentRepository>() },
        )
    }
    single<HistoryRepository> {
        val settings = get<SettingsRepository>()
        HistoryRepositoryImpl(
            get(),
            // The PC is the history server, not a client: paired phones push and pull through the Link server (:server,
            // RoomLinkHistory over this same Room table), so nothing is scheduled here.
            scheduler = NoHistorySync,
            syncer = null,
            lists = getOrNull(),
            autoListProgress = { settings.settings.value.autoListProgress },
            autoListAdd = { settings.settings.value.autoListAdd },
        )
    }
}

/** Linux release asset: the AppImage. */
internal fun hasLinuxBuild(assets: List<String>): Boolean = assets.any { it.endsWith(".AppImage", ignoreCase = true) }

/** Windows release asset: the installer or the portable zip. */
internal fun hasWindowsBuild(assets: List<String>): Boolean =
    assets.any { it.endsWith("-setup.exe", ignoreCase = true) || it.endsWith("-portable.zip", ignoreCase = true) }

private object NoHistorySync : HistorySyncScheduler {
    override fun requestSync(immediate: Boolean) = Unit
}

/** First-run settings of the desktop app: the shared defaults with the desktop torrent values (Tatakai Next's desktop engine). */
internal val DESKTOP_DEFAULT_SETTINGS: AppSettings get() = DESKTOP_SETTINGS_DEFAULTS

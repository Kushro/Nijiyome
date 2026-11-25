// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.server.desktop

import app.nijiyome.model.SecretStore
import app.nijiyome.model.LocalMediaRoute
import app.nijiyome.model.NetworkSettingsController
import app.nijiyome.server.link.KeySealer
import app.nijiyome.server.link.SecretStoreSealer
import app.nijiyome.database.HistoryDao
import app.nijiyome.extensions.ExtensionHost
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PcPlayerHub
import app.nijiyome.model.PcPlayerRemote
import app.nijiyome.model.RemoteLogsRepository
import app.nijiyome.model.SendToPhone
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.server.ext.HostCatalog
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

/**
 * Koin bindings of the desktop servers: [DesktopServers] (started with the graph, stopped when it closes) behind
 * [LinkServerController] for Settings › Nijiyome Link. Its watch history is the app's own (Room), its extensions the app's own
 * host. [anime4kDir] gives the folder of the bundled Anime4K passes (the app extracts them; null = no Anime4K profiles);
 * [lsfgDll] the `Lossless.dll` chosen in Settings › Reproductor (null = search Steam), read from the graph each time Link starts.
 */
fun desktopServerModule(
    dataDir: File,
    lsfgDll: (Koin) -> String? = { null },
    anime4kDir: () -> File? = { null },
): Module = module {
    // The desktop player plugs into this hub while its screen is open (remote control from paired phones, GET /v1/player).
    single { PcPlayerHub() }
    single<PcPlayerRemote> { get<PcPlayerHub>() }
    single { DeviceLogsStore(File(dataDir, "logs/devices")) }
    single<RemoteLogsRepository> { get<DeviceLogsStore>() }
    single(createdAtStart = true) {
        val history = RoomLinkHistory(get<HistoryDao>(), File(dataDir, "link/history-origins.json"))
        val deviceLogs = get<DeviceLogsStore>()
        val repo = getOrNull<ExtensionRepository>()
        val host = getOrNull<ExtensionHost>()
        val catalog = if (repo != null && host != null) HostCatalog(repo, host, File(dataDir, "extensions")) else null
        val player = get<PcPlayerHub>()
        val koin = getKoin()
        val metadata = getOrNull<MetadataRepository>()
        DesktopServers(
            dataDir = dataDir,
            linkHost = { s ->
                DesktopLinkHost(
                    history, repo, s::libraryFolder, s::ownMediaOrigins,
                    player = player,
                    torrentEngine = { koin.getOrNull<TorrentRepository>() },
                    mediaOf = { req -> metadata?.media(MediaKind.ANIME, DesktopLinkHost.refOf(req.mediaId)) },
                    deviceLogs = deviceLogs,
                )
            },
            catalog = catalog,
            anime4kDir = anime4kDir,
            historyOrigins = history::originCounts,
            // the CA and server keys are sealed with a key kept in the app's secret store (system keychain)
            keySealer = getOrNull<SecretStore>()?.let(::SecretStoreSealer) ?: KeySealer.Plain,
            lsfgDll = { lsfgDll(koin) },
            appVersion = { koin.getProperty<String>(APP_VERSION_PROPERTY) },
        ).also { it.start() }
    } onClose { it?.close() }
    single<LinkServerController> { get<DesktopServers>() }
    // Settings › Red (1.1): the outbound proxy, FlareSolverr and the player's loopback media proxy.
    single(createdAtStart = true) { DesktopNetwork(dataDir, getOrNull<SecretStore>()) } onClose { it?.close() }
    single<NetworkSettingsController> { get<DesktopNetwork>() }
    single {
        val net = get<DesktopNetwork>()
        LoopbackMediaRoute(File(dataDir, "server/local"), enabled = { net.current.localMediaProxy })
    } onClose { it?.close() }
    single<LocalMediaRoute> { get<LoopbackMediaRoute>() }
    single<SendToPhone> { get<DesktopServers>() }
}

/** The Koin property the desktop app sets to its packaged version (`DesktopAppProperties.VERSION`). */
internal const val APP_VERSION_PROPERTY = "NIJIYOME_VERSION"

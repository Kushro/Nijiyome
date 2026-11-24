// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.DefaultDownloadFolder
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.EmbedSniffer
import app.nijiyome.model.SettingsRepository
import java.io.File
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Desktop counterpart of the Android `downloadModule`: [DesktopDownloadRepository] with its JSON stores in [storeDir] and new
 * downloads in [defaultFolder] (`<Downloads>/Nijiyome/Episodes`) unless Settings › Descargas names another folder. Needs the shared
 * `OkHttpClient` (networkModule), SettingsRepository, HistoryRepository, MetadataRepository and ExtensionRepository; ListsRepository
 * and EmbedSniffer when they are bound. Closing Koin (app exit) stops the running downloads where they are.
 */
fun desktopDownloadModule(storeDir: File, defaultFolder: File): Module = module {
    single {
        val settings = get<SettingsRepository>()
        val koin = getKoin()
        val http = get<OkHttpClient>()
        DesktopDownloadRepository(
            settings = { settings.settings.value.downloads },
            extensions = get(),
            metadata = get(),
            history = get(),
            lists = getOrNull(),
            storeDir = storeDir,
            defaultFolder = defaultFolder,
            http = http,
            sniffer = { runCatching { koin.getOrNull<EmbedSniffer>() }.getOrNull() },
            // Settings › Descargas › Velocidad, and a .ts remuxed to .mp4 when ffmpeg is on the PATH (looked up once per download).
            engine = DesktopDownloadEngine(
                http,
                speedLimitKib = { settings.settings.value.downloads.sanitized().speedLimitKib },
                ffmpeg = { DesktopDownloadEngine.onPath("ffmpeg") },
            ),
            notifier = getOrNull(),
            // 1.1 (F7): TorBox downloads renew their link on start / resume (torrent module, bound later)
            debrid = { runCatching { koin.getOrNull<app.nijiyome.model.torrent.DebridRepository>() }.getOrNull() },
        )
    } bind AppDataWiper::class onClose { (it as? DesktopDownloadRepository)?.close() }
    single<DownloadRepository> { get<DesktopDownloadRepository>() }
    single { DefaultDownloadFolder(defaultFolder.absolutePath) }
}

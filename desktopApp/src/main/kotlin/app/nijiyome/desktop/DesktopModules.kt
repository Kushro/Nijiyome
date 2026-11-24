// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import app.nijiyome.data.DesktopAppInfo
import app.nijiyome.data.desktopDataModule
import app.nijiyome.social.desktopSocialModule
import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.database.databaseModule
import app.nijiyome.database.open
import app.nijiyome.desktop.system.DesktopExtraStorage
import app.nijiyome.desktop.system.webDownloadsFolder
import app.nijiyome.discover.desktopDiscoverModule
import app.nijiyome.extensions.StremioAddonRepositoryImpl
import app.nijiyome.feature.settings.ExtraStorage
import app.nijiyome.model.SettingsRepository
import app.nijiyome.extensions.desktopExtensionsModule
import app.nijiyome.extensionserver.desktopExtensionServerModule
import app.nijiyome.feature.details.desktopDetailsModule
import app.nijiyome.feature.downloads.downloadsFeatureModule
import app.nijiyome.feature.extensions.extensionsFeatureModule
import app.nijiyome.feature.link.extensionServersFeatureModule
import app.nijiyome.feature.home.homeModule
import app.nijiyome.feature.library.libraryModule
import app.nijiyome.feature.manga.mangaFeatureModule
import app.nijiyome.feature.novel.desktopNovelFeatureModule
import app.nijiyome.feature.search.desktopSearchModule
import app.nijiyome.feature.settings.desktopSettingsModule
import app.nijiyome.feature.social.socialFeatureModule
import app.nijiyome.feature.torrents.desktopTorrentsFeatureModule
import app.nijiyome.feature.you.youFeatureModule
import app.nijiyome.manga.mangaModule
import app.nijiyome.metadata.metadataModule
import app.nijiyome.network.networkModule
import app.nijiyome.network.outbound
import app.nijiyome.novel.desktopNovelModule
import app.nijiyome.player.desktopPlayerModule
import app.nijiyome.player.mpv.desktopMpvPlayerModule
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore
import app.nijiyome.player.mpv.external.desktopExternalMpvModule
import app.nijiyome.download.desktopDownloadModule
import app.nijiyome.torrent.DesktopTorrentFolders
import app.nijiyome.torrent.desktopTorrentModule
import java.io.File
import app.nijiyome.player.mpv.DesktopMpvAssets
import app.nijiyome.server.desktop.desktopServerModule
import app.nijiyome.server.link.media.Anime4k
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop dependency graph. It grows as modules get a desktop side. What a module cannot do on desktop yet is marked
 * PENDIENTE-ESCRITORIO inside that module (see docs/patron-modulo-kmp.md).
 */
fun desktopModules(paths: AppPaths, folders: UserFolders = UserFolders.resolve(paths), appVersion: String = ""): List<Module> = listOf(
    // Settings › Red: the outbound proxy (and its HTTP credentials) on the app's shared client.
    networkModule(platform = AppPaths.platformLabel(), configure = { outbound() }) { paths.cacheDir },
    // Shikimori's required User-Agent names this platform ("linux" / "windows"); Android keeps "android".
    metadataModule(platform = AppPaths.platformLabel().lowercase()),
    databaseModule { NijiyomeDatabase.open(paths.databaseFile) },
    // Repositories, the secret store (system keyring), lists and accounts (loopback OAuth), the update check and the reset.
    desktopDataModule(paths.dataDir, paths.cacheDir, DesktopAppInfo(version = appVersion, downloadFoldersInData = folders.inDataDir(paths))),
    // Calendar and "For you": airing schedule and recommendations, stored under the data directory, and the airing reminders.
    desktopDiscoverModule(paths.dataDir),
    // The .kai extension host (QuickJS). The hidden browser some extensions need is still Android-only.
    desktopExtensionsModule(dataDir = paths.dataDir, cacheDir = paths.cacheDir),
    // This PC as a client of other PCs' extension servers (as in Tatakai Next): their sources join the source list. The tokens are
    // sealed with a key kept in the secret store (system keyring).
    desktopExtensionServerModule(paths.dataDir),
    // Manga and novels: reading, offline novel text and narration (system voice, cloud voices, local KittenTTS on request).
    mangaModule { paths.dataDir },
    desktopNovelModule(paths.dataDir, paths.cacheDir),
    // The torrent engine (libtorrent4j; its native library is unpacked under the cache directory the first time it is needed, and
    // without it the app goes on with no torrents) and its screens.
    desktopTorrentModule(
        DesktopTorrentFolders(
            stateDir = File(paths.dataDir, "torrents"),
            downloadDir = folders.torrentDownloads,
            exportDir = folders.export,
            nativeCacheDir = paths.cacheDir,
        ),
    ),
    desktopTorrentsFeatureModule(),
    // Web downloads (HLS and files, resumable) and the local library, stored under the data directory.
    desktopDownloadModule(storeDir = File(paths.dataDir, "downloads"), defaultFolder = folders.webDownloads),
    // The player: the logic that is not video (sources, subtitles, markers), its subtitle tools and the libmpv engine. Its native
    // runtime is unpacked once under the cache directory; the subtitle fonts the user imports stay under the data directory (both
    // mpv engines read that folder).
    desktopPlayerModule(cacheDir = paths.cacheDir, fontsDir = subtitleFontsDir(paths)),
    desktopMpvPlayerModule(paths.cacheDir, fontsDir = subtitleFontsDir(paths)),
    // mpv in its own window (the EXTERNAL engine, its enhanced mode and lsfg-vk frame generation) and its settings file.
    desktopExternalMpvModule(dataDir = paths.dataDir, cacheDir = paths.cacheDir, fontsDir = subtitleFontsDir(paths)),
    // Nijiyome Link (the phone processes on this PC) and the integrated extension server; both off until Settings turns them on.
    // The LSFG library of Link's profiles is the one chosen for the player (Settings › Reproductor).
    desktopServerModule(
        paths.dataDir,
        lsfgDll = { koin -> koin.getOrNull<DesktopPlayerPrefsStore>()?.prefs?.value?.lsfgDll },
    ) { linkAnime4kShaders(File(paths.cacheDir, "link-anime4k")) },
    // The screens. Each one hides what depends on a service that is not registered above; a destination that cannot open
    // without one is not offered by the shell (RouteAvailability).
    homeModule,
    desktopSearchModule(paths.dataDir),
    desktopDetailsModule(paths.dataDir),
    libraryModule,
    desktopSettingsModule(),
    // Ajustes › Datos: the web downloads folder, the logs and the extensions next to the caches and the torrents.
    module {
        single<ExtraStorage> {
            val settings = get<SettingsRepository>()
            DesktopExtraStorage(
                webDownloads = { webDownloadsFolder(settings.settings.value.downloads.directoryTreeUri, folders.webDownloads) },
                logs = File(paths.dataDir, "logs"),
                extensions = listOf(File(paths.dataDir, "extensions"), File(paths.dataDir, StremioAddonRepositoryImpl.DIR_NAME)),
            )
        }
    },
    extensionsFeatureModule,
    extensionServersFeatureModule,
    mangaFeatureModule,
    desktopNovelFeatureModule(paths.dataDir),
    downloadsFeatureModule,
    // AniList profile, activity and notifications (needs the lists repository's AniList account).
    desktopSocialModule(paths.cacheDir),
    socialFeatureModule,
    youFeatureModule,
)

/** The player's subtitle fonts: the bundled ones for libass and the user's imported TTF / OTF. */
private fun subtitleFontsDir(paths: AppPaths): File = File(paths.dataDir, "subtitle-fonts")

/**
 * The bundled Anime4K passes (the player's own `shaders/Anime4K_*.glsl`) copied to [dir] for the Link server's libplacebo graphs.
 * Explicitly enumerates the known pass names in addition to [DesktopMpvAssets.list] so packaged JARs without directory entries
 * still extract every required pass.
 */
private fun linkAnime4kShaders(dir: File): File? {
    val listed = DesktopMpvAssets.list("shaders").filter { it.startsWith("Anime4K_") && it.endsWith(".glsl") }
    val known = Anime4k.requiredFiles().map { "$it.glsl" } + listOf(
        "Anime4K_Restore_CNN_VL.glsl",
        "Anime4K_Restore_CNN_Soft_VL.glsl",
        "Anime4K_Upscale_CNN_x2_VL.glsl",
        "Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
    )
    val names = (known + listed).distinct()
    dir.mkdirs()
    var written = 0
    for (name in names) {
        val bytes = DesktopMpvAssets.read("shaders/$name") ?: continue
        val out = File(dir, name)
        if (!out.isFile || out.length() != bytes.size.toLong()) {
            val tmp = File(dir, "$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(out)) {
                out.writeBytes(bytes)
                tmp.delete()
            }
        }
        written++
    }
    return if (written > 0 || Anime4k.shadersPresent(dir)) dir else null
}

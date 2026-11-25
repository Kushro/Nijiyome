// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.MangaRepository
import app.nijiyome.model.NovelRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.MetadataRepository
import app.nijiyome.nav.Route
import app.nijiyome.nav.RouteAvailability
import app.nijiyome.nav.topLevelRoutes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.error.DefinitionParameterException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.error.NoParameterFoundException
import org.koin.dsl.koinApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop dependency graph against a temporary data directory (never a real user directory). */
class DesktopGraphTest {
    private fun withGraph(block: (org.koin.core.Koin, AppPaths) -> Unit) {
        val dir = Files.createTempDirectory("nijiyome-desktop-test").toFile()
        val paths = AppPaths(dir)
        assertNull(paths.prepare())
        // Downloads under the temporary directory too (as with NIJIYOME_DATA_DIR), never the user's Downloads folder.
        val folders = UserFolders.resolve(paths, env = { if (it == "NIJIYOME_DATA_DIR") dir.path else null })
        val app = koinApplication { modules(desktopModules(paths, folders)) }
        try {
            block(app.koin, paths)
        } finally {
            app.close()
            dir.deleteRecursively() // temp dir created above
        }
    }

    @Test
    fun theServicesOfHomeResolve() = withGraph { koin, paths ->
        assertNotNull(koin.get<OkHttpClient>())
        assertNotNull(koin.get<MetadataRepository>())
        assertNotNull(koin.get<HistoryRepository>())
        assertNotNull(koin.get<NijiyomeDatabase>())
        assertTrue(paths.cacheDir.isDirectory)
    }

    @Test
    fun theRailOffersWhatDesktopCanOpen() = withGraph { koin, _ ->
        val available = RouteAvailability(koin)
        assertEquals(
            listOf(
                Route.Home, Route.Search, Route.Calendar, Route.Library, Route.Manga(), Route.Novels(), Route.Downloads(),
                Route.Extensions, Route.Linked, Route.Settings, Route.Profile(),
            ),
            topLevelRoutes(wide = true).filter(available::offers),
        )
        // Sparkles: Link opens its server side on desktop (LinkServerController).
        assertTrue(available.offers(Route.Linked))
        // Hito 6: a client of other PCs' extension servers (from Extensiones and nijiyome://extensionserver/connect). Tú no longer needs
        // the Link client (its Link row hides).
        assertTrue(available.offers(Route.ExtensionServers))
        assertTrue(available.offers(Route.Link))
        assertTrue(available.offers(Route.You))
        // Hito 5 (W3): accounts, the AniList profile and its notifications are on desktop now.
        assertTrue(available.offers(Route.Notifications))
        assertTrue(available.offers(Route.Profile()))
        assertTrue(available.offers(Route.SettingsSection(SettingsSection.ACCOUNTS)))
        assertTrue(available.offers(Route.SettingsSection(SettingsSection.TORRENTS)))
    }

    /**
     * Every screen ViewModel registered on desktop is created against the real graph. The ones that cannot be (a service they
     * need is not on desktop yet) are listed here, and the shell must not open their screens: see `Route.needs()` and the
     * `PENDIENTE-ESCRITORIO` notes of each screen. A new ViewModel that does not resolve fails this test until that is decided.
     */
    @OptIn(KoinInternalApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun theViewModelsOfTheScreensResolve() {
        // Their coroutines are queued on a dispatcher nobody runs: creating them must not start any request.
        Dispatchers.setMain(StandardTestDispatcher())
        try {
            withGraph { koin, _ ->
                val missing = sortedMapOf<String, String>()
                var created = 0
                val viewModels = koin.instanceRegistry.instances.values.map { it.beanDefinition.primaryType }
                    .filter { ViewModel::class.java.isAssignableFrom(it.java) }.distinct()
                assertTrue(viewModels.size >= 25, "found only ${viewModels.size} ViewModels: the screen modules are not registered")
                for (type in viewModels) {
                    try {
                        koin.get<Any>(clazz = type)
                        created++
                    } catch (e: Exception) {
                        val causes = generateSequence<Throwable>(e) { it.cause }.toList()
                        val noService = causes.filterIsInstance<NoDefinitionFoundException>().lastOrNull()
                        when {
                            noService != null -> missing[type.simpleName!!] = SERVICE.find(noService.message.orEmpty())?.groupValues?.get(1) ?: noService.message.orEmpty()
                            // Needs the arguments of its screen (an id, the reader arguments): not created here.
                            causes.any { it is NoParameterFoundException || it is DefinitionParameterException || it is IndexOutOfBoundsException } -> Unit
                            else -> throw e
                        }
                    }
                }
                assertTrue(created >= 12, "only $created ViewModels were created")
                assertEquals(emptyMap<String, String>(), missing.toMap())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Hito 5 (W3): the services of accounts, the keyring, the update check and the reset resolve on desktop. */
    @Test
    fun theAccountServicesResolve() = withGraph { koin, _ ->
        assertNotNull(koin.get<app.nijiyome.model.ListsRepository>())
        assertNotNull(koin.get<app.nijiyome.model.SecretStore>())
        assertNotNull(koin.get<app.nijiyome.model.UpdateRepository>())
        assertNotNull(koin.get<app.nijiyome.model.AppReset>())
        assertNotNull(koin.get<app.nijiyome.data.DesktopOAuthLauncher>())
        assertNotNull(koin.get<app.nijiyome.model.SocialRepository>())
        assertNotNull(koin.get<app.nijiyome.model.torrent.DebridRepository>())
    }

    @Test
    fun theExtensionHostResolves() = withGraph { koin, _ ->
        assertNotNull(koin.get<ExtensionRepository>())
    }

    @Test
    fun mangaNovelsAndTorrentsResolve() = withGraph { koin, _ ->
        assertNotNull(koin.get<MangaRepository>())
        assertNotNull(koin.get<NovelRepository>())
        // The engine when libtorrent4j loads here; a repository without one (same screens, every action unavailable) when not.
        assertNotNull(koin.get<TorrentRepository>())
        assertNotNull(koin.get<DownloadRepository>())
    }

    @Test
    fun aSecondInstanceIsRefused() {
        val dir = Files.createTempDirectory("nijiyome-lock-test").toFile()
        try {
            val first = assertNotNull(InstanceLock.acquire(dir))
            // Same JVM: a second lock on the same file is refused too (OverlappingFileLockException -> null).
            assertNull(InstanceLock.acquire(dir))
            first.release()
            assertNotNull(InstanceLock.acquire(dir)).release()
        } finally {
            dir.deleteRecursively() // temp dir created above
        }
    }

    @Test
    fun dataDirectoryResolution() {
        val env = mapOf("XDG_CONFIG_HOME" to "/x/cfg")
        assertEquals(File("/x/cfg/Nijiyome"), AppPaths.resolve(env::get, osName = "Linux", home = "/h").dataDir)
        assertEquals(File("/h/.config/Nijiyome"), AppPaths.resolve({ null }, osName = "Linux", home = "/h").dataDir)
        assertEquals(File("C:/Roaming/Nijiyome"), AppPaths.resolve(mapOf("APPDATA" to "C:/Roaming")::get, osName = "Windows 11", home = "C:/u").dataDir)
        assertEquals(File("/tmp/override"), AppPaths.resolve(mapOf("NIJIYOME_DATA_DIR" to "/tmp/override")::get, osName = "Linux", home = "/h").dataDir)
        // A folder under a regular file cannot exist on any system (`/dev/null/...` can be created on Windows).
        val file = Files.createTempFile("nijiyome-not-a-dir", ".txt").toFile().apply { deleteOnExit() }
        assertNotNull(AppPaths(File(file, "not-a-dir")).prepare(), "an unusable directory must be reported")
    }
}

private val SERVICE = Regex("""model\.(?:[a-z]+\.)*([A-Z][A-Za-z]+)""")

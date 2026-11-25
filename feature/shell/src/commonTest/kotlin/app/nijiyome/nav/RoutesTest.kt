package app.nijiyome.nav

import app.nijiyome.feature.library.LibraryTab
import app.nijiyome.model.FeedKind
import app.nijiyome.model.MediaKind
import app.nijiyome.model.NavStyle
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.torrent.TorrentSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {

    @Test
    fun `compact has five destinations and wide has the Sparkles rail order`() {
        assertEquals(listOf(Route.Home, Route.Search, Route.Calendar, Route.Library, Route.You), topLevelRoutes(wide = false))
        assertEquals(
            listOf(
                Route.Home, Route.Search, Route.Calendar, Route.Library, Route.Manga(), Route.Novels(),
                Route.Downloads(), Route.Extensions, Route.Linked, Route.Settings,
                Route.Profile(),
            ),
            topLevelRoutes(wide = true),
        )
    }

    @Test
    fun `medium navigation drops manga and novels from the rail and keeps the order`() {
        val medium = railGroups(NavStyle.MEDIUM)
        assertEquals(listOf(Route.Home, Route.Search, Route.Calendar, Route.Library), medium.primary)
        assertEquals(UtilityRoutesWide, medium.utility)
        assertEquals(railGroups(NavStyle.RAIL), railGroups(NavStyle.COMMAND))
        assertEquals(TopLevelRoutesWide, railGroups(NavStyle.RAIL).ordered)
        // Number keys count the visible destinations: Ajustes is 10th with the full rail, 8th by medium.
        assertEquals(Route.Settings, railGroups(NavStyle.MEDIUM).ordered[7])
        val noLink = railGroups(NavStyle.RAIL).filter { it != Route.Linked }
        assertFalse(Route.Linked in noLink.ordered)
        assertEquals(Route.Settings, noLink.ordered[8])
    }

    @Test
    fun `routes fold into their rail destination on wide and into you on compact`() {
        assertEquals(Route.Manga(), Route.Manga("frieren").topLevel(wide = true))
        assertEquals(Route.You, Route.Manga("frieren").topLevel(wide = false))
        assertEquals(Route.Novels(), Route.NovelDetails(1).topLevel(wide = true))
        assertEquals(Route.Downloads(), Route.Downloads(app.nijiyome.model.DownloadsTab.HISTORY).topLevel(wide = true))
        assertEquals(Route.Downloads(), Route.Torrents(TorrentSection.FILES, "x").topLevel(wide = true))
        assertEquals(Route.You, Route.Downloads().topLevel(wide = false))
        assertEquals(Route.Profile(), Route.Notifications.topLevel(wide = true))
        assertEquals(Route.You, Route.Profile("someone").topLevel(wide = false))
        assertEquals(Route.Extensions, Route.Provider("ext", "src").topLevel(wide = true))
        assertEquals(Route.Extensions, Route.ExtensionServers.topLevel(wide = true))
        assertEquals(Route.Calendar, Route.Calendar.topLevel(wide = false))
        assertEquals(Route.Calendar, Route.Calendar.topLevel(wide = true))
        // Para ti is a tab of Explorar on wide windows and a row of Tú on phones.
        assertEquals(Route.Search, Route.ForYou.topLevel(wide = true))
        assertEquals(Route.You, Route.ForYou.topLevel(wide = false))
    }

    @Test
    fun `compact folds transfers and settings-like routes into you`() {
        assertEquals(Route.You, Route.Torrents(TorrentSection.PEERS, "abc").topLevel(wide = false))
        assertEquals(Route.Library, Route.LibraryTab(LibraryTab.HISTORY).topLevel(wide = false))
        listOf(Route.Extensions, Route.Link, Route.Linked, Route.Settings, Route.SettingsSection(SettingsSection.PLAYER), Route.You)
            .forEach { assertEquals(Route.You, it.topLevel(wide = false), "$it") }
        assertEquals(Route.Search, Route.SearchFor(feed = FeedKind.entries.first()).topLevel(wide = false))
        assertEquals(Route.You, Route.Extensions.topLevel())
        assertEquals(Route.Search, Route.SearchFor(studio = "MAPPA").topLevel(wide = false))
        assertEquals(Route.Search, Route.ImageSearch().topLevel(wide = false))
        assertEquals(Route.Search, Route.ImageSearch("content://x").topLevel(wide = true))
    }

    @Test
    fun `wide maps every rail destination to itself`() {
        assertEquals(Route.Extensions, Route.Extensions.topLevel(wide = true))
        assertEquals(Route.Linked, Route.Link.topLevel(wide = true))
        assertEquals(Route.Settings, Route.SettingsSection(SettingsSection.TORRENTS).topLevel(wide = true))
        topLevelRoutes(wide = true).forEach { assertEquals(it, it.topLevel(wide = true)) }
        topLevelRoutes(wide = false).forEach { assertEquals(it, it.topLevel(wide = false)) }
    }

    @Test
    fun `routes without an owner return themselves`() {
        val details = Route.Details(MediaKind.ANIME, 1)
        assertEquals(details, details.topLevel(wide = true))
        assertTrue(details.topLevel(wide = false) !in topLevelRoutes(wide = false))
    }

    @Test
    fun `the medium follows the route`() {
        assertEquals(ShellMedium.ANIME, Route.Home.medium())
        assertEquals(ShellMedium.MANGA, Route.Manga("x").medium())
        assertEquals(ShellMedium.MANGA, Route.Details(MediaKind.MANGA, 3).medium())
        assertEquals(ShellMedium.NOVEL, Route.NovelDetails(3).medium())
        assertEquals(null, Route.Settings.medium())
        assertEquals(Route.Novels(), ShellMedium.NOVEL.home())
    }
}

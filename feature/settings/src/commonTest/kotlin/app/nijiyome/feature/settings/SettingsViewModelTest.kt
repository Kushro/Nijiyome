package app.nijiyome.feature.settings

import app.nijiyome.model.AppSettings
import app.nijiyome.model.ContrastPref
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import app.nijiyome.model.Episode
import app.nijiyome.model.FeedKind
import app.nijiyome.model.FeedPage
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataCacheStats
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.Quality
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.AppLanguage
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }


    @Test
    fun `a preset keeps the chosen header composition`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)
        vm.setLook { it.copy(headerLayout = app.nijiyome.model.HeaderLayout.LINE) }
        vm.applyLookPreset(app.nijiyome.model.LookPreset.CINEMA)
        val look = repo.settings.value.look
        assertEquals(app.nijiyome.model.HeaderLayout.LINE, look.headerLayout)
        assertEquals(app.nijiyome.model.PageStyle.EDITORIAL, look.pageStyle)
        assertEquals(app.nijiyome.model.LookPreset.CINEMA, look.matchingPreset())
    }

    @Test
    fun `appearance changes are written through the settings repository`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)

        vm.setPalette("sakura")
        vm.setAmoled(true)
        vm.setDynamicColor(false)
        vm.setContrast(ContrastPref.HIGH)

        val s = repo.settings.value
        assertEquals("sakura", s.paletteId)
        assertEquals(true, s.amoled)
        assertEquals(false, s.dynamicColor)
        assertEquals(ContrastPref.HIGH, s.contrast)
        assertEquals("sakura", vm.state.first { it.settings.paletteId == "sakura" }.settings.paletteId)
    }

    @Test
    fun `dynamic color is off on a new install`() {
        assertFalse(AppSettings().dynamicColor)
    }

    @Test
    fun `a preset keeps the sparkles design and a direction starts on its own palette`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)
        vm.setDirection(app.nijiyome.model.DesignDirection.OBI)
        vm.setDirectionPalette("noche")
        vm.applyLookPreset(app.nijiyome.model.LookPreset.DENSE)
        var design = repo.settings.value.look.design
        assertEquals(app.nijiyome.model.DesignDirection.OBI, design.direction)
        assertEquals("noche", design.palette)
        vm.setDirection(app.nijiyome.model.DesignDirection.SUMI)
        design = repo.settings.value.look.design
        assertNull(design.palette)
    }

    @Test
    fun `the player structure keeps the touch style in step`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)
        vm.setPlayerStructure(app.nijiyome.model.PlayerStructure.MINIMAL)
        assertEquals(app.nijiyome.model.PlayerChromeStyle.MINIMAL, repo.settings.value.playerChromeStyle)
        assertEquals(app.nijiyome.model.PlayerStructure.MINIMAL, repo.settings.value.look.design.structures.player)
    }

    @Test
    fun `provider priority is set as a whole order`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)
        val before = repo.settings.value.providerPriority
        vm.setProviderPriority(before.reversed() + "unknown")
        assertEquals(before.reversed(), repo.settings.value.providerPriority)
    }

    @Test
    fun `provider priority moves and clamps`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)

        vm.moveProvider("kitsu", -1)
        assertEquals(listOf("anilist", "kitsu", "anizip", "tenrai"), repo.settings.value.providerPriority)
        vm.moveProvider("anilist", -1)
        assertEquals(listOf("anilist", "kitsu", "anizip", "tenrai"), repo.settings.value.providerPriority)
        assertEquals(listOf("a", "b"), listOf("a", "b").moved("zzz", 1))
    }



    private class FakeMetadata(var stats: MetadataCacheStats = MetadataCacheStats(entries = 12, bytes = 98_000_000)) : MetadataRepository {
        var cleared = false
        var failClear = false
        override suspend fun feed(kind: MediaKind, feed: FeedKind, page: Int): FeedPage = error("unused")
        override suspend fun search(kind: MediaKind, query: String, page: Int): FeedPage = error("unused")
        override suspend fun media(kind: MediaKind, ref: MediaRef): Media? = null
        override suspend fun episodes(ref: MediaRef): List<Episode> = emptyList()
        override suspend fun cacheStats(): MetadataCacheStats = stats
        override suspend fun clearCache() {
            if (failClear) throw AppException(AppError.Unknown("disk"))
            cleared = true
            stats = MetadataCacheStats(0, 0)
        }
    }

    @Test
    fun `quality defaults are written through the repository`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)
        assertEquals(PlayerEngine.MPV, repo.settings.value.playerEngine, "0.4.0: libmpv is the default engine")

        vm.setPlayerQuality { it.copy(tier = app.nijiyome.model.QualityTier.HQ, autoAdjust = false) }
        vm.setPlayerQuality { it.copy(deband = it.deband.copy(enabled = false, iterations = 99)) }

        val q = repo.settings.value.playerQuality
        assertEquals(app.nijiyome.model.QualityTier.HQ, q.tier)
        assertEquals(false, q.autoAdjust)
        assertEquals(false, q.deband.enabled)
        assertEquals(16, q.deband.iterations)
    }

    @Test
    fun `wave settings are written through the repository`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)

        vm.setThemeMode(ThemeMode.DARK)
        vm.setLanguage(AppLanguage.ES)
        vm.setReduceMotion(true)
        vm.setPlayerEngine(PlayerEngine.EXTERNAL)
        vm.setExternalPlayerPackage("is.xyz.mpv")
        vm.setPlayerPreset("anime4k-b")
        vm.setSourceMode(SourceMode.MANUAL)
        vm.setPreferredKind(SourceKind.TORRENT)
        vm.setMinQuality(Quality.P1080)
        vm.setSubLang("en")
        vm.setDubLang("es")
        vm.setAutoNext(false)
        vm.setSkipIntro(false)

        val s = repo.settings.value
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(AppLanguage.ES, s.language)
        assertEquals(true, s.reduceMotion)
        assertEquals(PlayerEngine.EXTERNAL, s.playerEngine)
        assertEquals("is.xyz.mpv", s.externalPlayerPackage)
        assertEquals("anime4k-b", s.playerPreset)
        assertEquals(SourceMode.MANUAL, s.sourcePrefs.mode)
        assertEquals(SourceKind.TORRENT, s.sourcePrefs.prefer)
        assertEquals(Quality.P1080, s.sourcePrefs.minQuality)
        assertEquals("en", s.sourcePrefs.subLang)
        assertEquals("es", s.sourcePrefs.dubLang)
        assertEquals(false, s.autoNext)
        assertEquals(false, s.skipIntro)

        vm.setExternalPlayerPackage(" ")
        assertNull(repo.settings.value.externalPlayerPackage)
    }

    @Test
    fun `ttl and subtitle style are clamped`() = runTest {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo)

        vm.setMetadataTtl(1000)
        assertEquals(168, repo.settings.value.metadataCacheTtlHours)
        vm.setMetadataTtl(0)
        assertEquals(1, repo.settings.value.metadataCacheTtlHours)

        vm.setSubtitleStyle(SubtitleStyle(scale = 5f, backgroundOpacity = -1f, font = "space_grotesk"))
        val style = repo.settings.value.subtitleStyle
        assertEquals(SubtitleStyle.MAX_SCALE, style.scale)
        assertEquals(0f, style.backgroundOpacity)
        assertEquals("space_grotesk", style.font)
    }

    @Test
    fun `switch changes announce settings saved`() = runTest {
        val vm = SettingsViewModel(FakeSettings())
        vm.events.test {
            vm.setAutoNext(false)
            assertEquals(SettingsEvent.Saved, awaitItem())
            // one save model: every change is confirmed, the palette too
            vm.setPalette("sakura")
            assertEquals(SettingsEvent.Saved, awaitItem())
            vm.setMinQuality(Quality.P480)
            assertEquals(SettingsEvent.Saved, awaitItem())
        }
    }

    @Test
    fun `storage loads the cache figures and clearing the cache reports the freed bytes`() = runTest {
        val metadata = FakeMetadata()
        val vm = SettingsViewModel(FakeSettings(), metadata = metadata)

        vm.refreshStorage()
        val ok = vm.state.first { it.storage is LoadState.Ok }.storage as LoadState.Ok
        assertEquals(98_000_000L, ok.value.cache?.bytes)
        assertNull(ok.value.torrents)

        vm.events.test {
            vm.clearCache()
            assertEquals(SettingsEvent.CacheCleared(98_000_000), awaitItem())
        }
        assertEquals(true, metadata.cleared)
    }

    @Test
    fun `cache clear failure is reported`() = runTest {
        val metadata = FakeMetadata().apply { failClear = true }
        val vm = SettingsViewModel(FakeSettings(), metadata = metadata)
        vm.events.test {
            vm.clearCache()
            assertEquals(SettingsEvent.Failed(AppError.Unknown("disk")), awaitItem())
        }
    }

    private class FakeImages(var bytes: Long? = 40_000_000) : ImageCache {
        var cleared = false
        var failClear = false
        override suspend fun sizeBytes(): Long? = bytes
        override suspend fun clear() {
            if (failClear) throw AppException(AppError.Unknown("images"))
            cleared = true
            bytes = 0
        }
    }

    @Test
    fun `clearing the cache also empties the image disk cache and reports both as freed`() = runTest {
        val metadata = FakeMetadata()
        val images = FakeImages()
        val vm = SettingsViewModel(FakeSettings(), metadata = metadata, images = images)

        vm.refreshStorage()
        val ok = vm.state.first { it.storage is LoadState.Ok }.storage as LoadState.Ok
        assertEquals(40_000_000L, ok.value.imageBytes)
        assertEquals(138_000_000L, ok.value.clearableBytes)

        vm.events.test {
            vm.clearCache()
            assertEquals(SettingsEvent.CacheCleared(138_000_000), awaitItem())
        }
        assertEquals(true, metadata.cleared)
        assertEquals(true, images.cleared)
        val after = vm.state.first { (it.storage as? LoadState.Ok)?.value?.clearableBytes == 0L }
        assertEquals(false, after.clearingCache)
    }

    @Test
    fun `image cache is cleared even when the metadata clear fails`() = runTest {
        val metadata = FakeMetadata().apply { failClear = true }
        val images = FakeImages()
        val vm = SettingsViewModel(FakeSettings(), metadata = metadata, images = images)
        vm.events.test {
            vm.clearCache()
            assertEquals(SettingsEvent.Failed(AppError.Unknown("disk")), awaitItem())
        }
        assertEquals(true, images.cleared)
    }

    @Test
    fun `image cache alone can be cleared`() = runTest {
        val images = FakeImages(bytes = 5_000)
        val vm = SettingsViewModel(FakeSettings(), images = images)
        vm.events.test {
            vm.clearCache()
            assertEquals(SettingsEvent.CacheCleared(5_000), awaitItem())
        }
    }

    @Test
    fun `freed bytes never go negative and clearable bytes need one known figure`() {
        assertEquals(0L, freedBytes(before = 10, after = 25))
        assertEquals(15L, freedBytes(before = 25, after = 10))
        assertNull(StorageInfo(cache = null, torrents = null).clearableBytes)
        assertEquals(7L, StorageInfo(cache = null, torrents = null, imageBytes = 7).clearableBytes)
    }

    // ---- Hito 6 (D1) -----------------------------------------------------------------------------------------------------------

    @Test
    fun `Vaciar empties only the metadata cache and keeps the images`() = runTest {
        val metadata = FakeMetadata()
        val images = FakeImages()
        val vm = SettingsViewModel(FakeSettings(), metadata = metadata, images = images)
        vm.refreshStorage()
        vm.state.first { it.storage is LoadState.Ok }
        vm.events.test {
            vm.clearMetadataCache()
            assertEquals(SettingsEvent.CacheCleared(98_000_000), awaitItem())
        }
        assertEquals(true, metadata.cleared)
        assertEquals(false, images.cleared)
    }

    @Test
    fun `the extra storage folders are measured with the rest`() = runTest {
        val extra = ExtraStorage { listOf(ExtraStorageItem(ExtraStorageItem.Kind.WEB_DOWNLOADS, 5_000), ExtraStorageItem(ExtraStorageItem.Kind.LOGS, 7)) }
        val vm = SettingsViewModel(FakeSettings(), metadata = FakeMetadata(), extraStorage = extra)
        vm.refreshStorage()
        val state = vm.state.first { it.storage is LoadState.Ok }
        assertEquals(listOf(ExtraStorageItem.Kind.WEB_DOWNLOADS, ExtraStorageItem.Kind.LOGS), state.extraStorage.map { it.kind })
        // Without one (Android) nothing extra is listed.
        val plain = SettingsViewModel(FakeSettings(), metadata = FakeMetadata())
        plain.refreshStorage()
        assertEquals(emptyList(), plain.state.first { it.storage is LoadState.Ok }.extraStorage)
    }

    @Test
    fun `a folder inside the torrent folder is marked so it is not counted twice`() {
        assertTrue(isInside("/home/u/Downloads/Nijiyome/Episodes", "/home/u/Downloads/Nijiyome"))
        assertTrue(isInside("/home/u/Downloads/Nijiyome", "/home/u/Downloads/Nijiyome/"))
        assertFalse(isInside("/home/u/Downloads/Nijiyome2", "/home/u/Downloads/Nijiyome"))
        assertFalse(isInside("/home/u/.config/Nijiyome/logs", "/home/u/Downloads/Nijiyome"))
    }

    private class FakeReset : app.nijiyome.model.AppReset {
        var calls = 0
        override suspend fun resetAll(): Result<Unit> {
            calls++
            return Result.success(Unit)
        }
    }

    private class FakeSelectiveReset : app.nijiyome.model.SelectiveAppReset {
        val kept = mutableListOf<Boolean>()
        override suspend fun resetAll(keepTorrents: Boolean): Result<Unit> {
            kept += keepTorrents
            return Result.success(Unit)
        }
    }

    @Test
    fun `the torrents choice reaches a selective reset and a plain reset is called as before`() = runTest {
        val selective = FakeSelectiveReset()
        val vm = SettingsViewModel(FakeSettings(), reset = selective)
        vm.events.test {
            vm.resetApp(keepTorrents = false)
            assertEquals(SettingsEvent.ResetDone, awaitItem())
        }
        assertEquals(listOf(false), selective.kept)

        val plain = FakeReset()
        val android = SettingsViewModel(FakeSettings(), reset = plain)
        android.events.test {
            android.resetApp()
            assertEquals(SettingsEvent.ResetDone, awaitItem())
        }
        assertEquals(1, plain.calls)
    }

    @Test
    fun `the typed reset word ignores case and spaces`() {
        assertTrue(resetWordMatches(" borrar ", "BORRAR"))
        assertTrue(resetWordMatches("RESET", "RESET"))
        assertFalse(resetWordMatches("", "RESET"))
        assertFalse(resetWordMatches("RESE", "RESET"))
    }
}

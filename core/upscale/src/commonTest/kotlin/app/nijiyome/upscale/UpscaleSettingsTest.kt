// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpscaleSettingsTest {
    @Test
    fun defaultsFollowTheOwnersDecisions() {
        val d = ImageUpscaleSettings()
        assertTrue(d.enabled)
        assertEquals(UpscaleEngineId.REAL_ESRGAN, d.engine)
        assertEquals(false, d.onlyWhileCharging)
        assertEquals(false, UpscaleImageKind.AVATAR in d.kinds)
    }

    @Test
    fun roundTrip() {
        val s = ImageUpscaleSettings(
            enabled = false,
            quality = UpscaleQuality.BEST,
            kinds = setOf(UpscaleImageKind.AVATAR),
            cacheMb = 512,
            onlyWhileCharging = true,
        )
        assertEquals(s, UpscaleSettingsCodec.decode(UpscaleSettingsCodec.encode(s)))
    }

    @Test
    fun missingOrBrokenJsonReadsAsDefaults() {
        assertEquals(ImageUpscaleSettings(), UpscaleSettingsCodec.decode(null))
        assertEquals(ImageUpscaleSettings(), UpscaleSettingsCodec.decode("{not json"))
        assertEquals(ImageUpscaleSettings(), UpscaleSettingsCodec.decode("[1,2]"))
    }

    @Test
    fun readingIsLenientFieldByField() {
        val raw = """{"enabled":false,"quality":"ULTRA","engine":"WAIFU2X","kinds":["POSTER","MANGA_PAGE"],"future":1,"cacheMb":"big"}"""
        val s = UpscaleSettingsCodec.decode(raw)
        assertEquals(false, s.enabled)
        assertEquals(UpscaleQuality.BALANCED, s.quality)
        assertEquals(UpscaleEngineId.REAL_ESRGAN, s.engine)
        assertEquals(setOf(UpscaleImageKind.POSTER), s.kinds)
        assertEquals(null, s.cacheMb)
    }

    @Test
    fun sanitizedClampsTheCacheAndKeepsRealEsrgan() {
        val s = UpscaleSettingsCodec.decode("""{"engine":"ANIME4K","cacheMb":10}""")
        assertEquals(UpscaleEngineId.REAL_ESRGAN, s.engine)
        assertEquals(UPSCALE_CACHE_MIN_MB, s.cacheMb)
        assertEquals(UPSCALE_CACHE_MAX_MB, ImageUpscaleSettings(cacheMb = 99_999).sanitized().cacheMb)
        assertEquals(UpscaleResultCache.megabytes(256), ImageUpscaleSettings().cacheBytes(TEST_LIMITS))
    }

    @Test
    fun storeReadsWritesAndClearsTheCache() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val persistence = InMemorySettingsPersistence("""{"quality":"FAST"}""")
        val files = InMemoryFileStore()
        val cache = UpscaleResultCache(files, Long.MAX_VALUE)
        cache.load()
        cache.put("k", UpscaleCacheEntry("u", UpscaleImageKind.POSTER, "h", 2, 10, 10), ByteArray(10))
        val store = PersistedUpscaleSettingsStore(persistence, cache, testWorkScope(), io)
        assertEquals(UpscaleQuality.FAST, store.settings.value.quality)

        store.update { it.copy(enabled = false) }
        assertEquals(false, store.settings.value.enabled)
        advanceUntilIdle()
        assertEquals(false, UpscaleSettingsCodec.decode(persistence.stored).enabled)
        assertEquals(UpscaleQuality.FAST, UpscaleSettingsCodec.decode(persistence.stored).quality)

        store.clearCache()
        assertEquals(0L, cache.bytes.value)
        assertTrue(files.files.isEmpty())
    }
}

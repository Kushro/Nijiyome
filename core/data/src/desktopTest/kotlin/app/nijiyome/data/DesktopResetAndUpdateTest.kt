// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.nijiyome.database.HistoryRow
import app.nijiyome.database.NijiyomeDatabase
import app.nijiyome.database.open
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopResetAndUpdateTest {
    private val root: File = Files.createTempDirectory("reset-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    private class CountingSecrets : SecretStore {
        var cleared = 0
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String?) = Unit
        override suspend fun clear() {
            cleared++
        }
    }

    @Test
    fun resetEmptiesTheDataDirectoryButKeepsLogsOpenFilesAndDownloads() = runBlocking {
        val data = File(root, "Nijiyome").apply { mkdirs() }
        val outside = File(root, "outside").apply { mkdirs() }
        File(outside, "keep.txt").writeText("not ours")
        File(data, "extensions/toko").mkdirs()
        File(data, "extensions/toko/main.js").writeText("x")
        File(data, "desktop-system.properties").writeText("trayIcon=false")
        File(data, "secrets.key").writeText("k")
        File(data, "logs").mkdirs()
        File(data, "logs/app.log").writeText("log")
        File(data, "downloads").mkdirs()
        File(data, "downloads/ep1.mkv").writeText("video")
        File(data, "cache/http").mkdirs()
        File(data, "cache/http/x").writeText("c")
        // A link inside the data directory must go without touching what it points to.
        runCatching { Files.createSymbolicLink(File(data, "link").toPath(), outside.toPath()) }
        val db = NijiyomeDatabase.open(File(data, "nijiyome.db"))
        db.history().upsert(HistoryRow(episodeId = "e1", animeId = "a1", title = "T", poster = null, episode = 1, progressSec = 10, durationSec = 100, completed = false, watchedAtMs = 1, anilistId = 1, malId = null))
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(data, "settings.preferences_pb") }
        prefs.edit { it[booleanPreferencesKey("onboarding_done")] = true }
        val secrets = CountingSecrets()
        var wiped = 0
        val reset = DesktopAppReset(
            dataDir = data,
            cacheDir = File(data, "cache"),
            settingsStore = prefs,
            database = db,
            secrets = secrets,
            wipers = listOf(AppDataWiper { wiped++ }),
            keep = DesktopAppReset.DEFAULT_KEEP + "downloads",
        )
        assertTrue(reset.resetAll().isSuccess)
        assertEquals(1, secrets.cleared)
        assertEquals(1, wiped)
        assertFalse(File(data, "extensions").exists())
        assertFalse(File(data, "desktop-system.properties").exists())
        assertFalse(File(data, "secrets.key").exists())
        assertFalse(File(data, "link").exists())
        assertTrue(File(outside, "keep.txt").isFile, "a link's target is never followed")
        assertTrue(File(data, "logs/app.log").isFile)
        assertTrue(File(data, "downloads/ep1.mkv").isFile)
        assertTrue(File(data, "cache").isDirectory)
        assertFalse(File(data, "cache/http").exists())
        assertTrue(File(data, "nijiyome.db").isFile)
        assertNull(db.history().get("e1"), "tables emptied")
        assertNull(prefs.data.first()[booleanPreferencesKey("onboarding_done")])
        db.close()
    }

    /** A torrent engine with [ids] in its library that records `remove(id, deleteFiles)`; nothing else is used. */
    private fun fakeTorrents(ids: List<String>, removed: MutableList<Pair<String, Boolean>>): app.nijiyome.model.torrent.TorrentRepository {
        val library = kotlinx.coroutines.flow.MutableStateFlow(ids.map { app.nijiyome.model.torrent.TorrentLibraryItem(id = it, name = it) })
        return java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(app.nijiyome.model.torrent.TorrentRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getLibrary" -> library
                "remove" -> {
                    removed += (args[0] as String) to (args[1] as Boolean)
                    Unit
                }
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "FakeTorrents"
                else -> error("not used: ${method.name}")
            }
        } as app.nijiyome.model.torrent.TorrentRepository
    }

    /** Hito 6 (D1, sistema-ajustes-5): "Conservar los torrents descargados" off removes every torrent with its files; on, none. */
    @Test
    fun theTorrentsGoWithTheirFilesOnlyWhenNotKept() = runBlocking {
        val data = File(root, "Nijiyome").apply { mkdirs() }
        val db = NijiyomeDatabase.open(File(data, "nijiyome.db"))
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(data, "settings.preferences_pb") }
        val removed = mutableListOf<Pair<String, Boolean>>()
        var asked = 0
        val reset = DesktopAppReset(
            dataDir = data,
            cacheDir = File(data, "cache"),
            settingsStore = prefs,
            database = db,
            secrets = CountingSecrets(),
            torrents = { asked++; fakeTorrents(listOf("aa", "bb"), removed) },
        )
        assertTrue(reset.resetAll().isSuccess)
        assertEquals(0, asked, "kept by default: the engine is not even started")
        assertTrue(removed.isEmpty())
        assertTrue(reset.resetAll(keepTorrents = false).isSuccess)
        assertEquals(listOf("aa" to true, "bb" to true), removed)
        db.close()
    }

    @Test
    fun whatCouldNotBeDeletedIsFinishedOnTheNextStart() {
        val data = File(root, "d").apply { mkdirs() }
        File(data, "torrents").mkdirs()
        File(data, "torrents/state.bin").writeText("x")
        File(data, DesktopAppReset.PENDING_FILE).writeText("""["torrents","../outside","logs"]""")
        File(root, "outside").mkdirs()
        File(data, "logs").mkdirs()
        DesktopAppReset.finishPending(data)
        assertFalse(File(data, "torrents").exists())
        assertTrue(File(root, "outside").isDirectory, "never outside the data directory")
        assertTrue(File(data, "logs").isDirectory, "never the logs")
        assertFalse(File(data, DesktopAppReset.PENDING_FILE).exists())
    }

    @Test
    fun theUpdateCheckOffersAReleaseOnlyWithThisPlatformsBuild() = runBlocking {
        MockWebServer().use { server ->
            val body = """
                {"tag_name":"v0.3.0","draft":false,"prerelease":false,"html_url":"https://github.com/Kushro/Nijiyome/releases/tag/v0.3.0",
                 "published_at":"2026-12-01T10:00:00Z","body":"Notes",
                 "assets":[{"name":"Nijiyome-v0.3.0-linux-x86_64.AppImage"},{"name":"Nijiyome-v0.3.0-windows-x64-setup.exe"},
                           {"name":"Nijiyome-v0.3.0-windows-x64-portable.zip"},{"name":"Nijiyome-v0.3.0-android-arm64-v8a.apk"}]}
            """.trimIndent()
            repeat(3) { server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()) }
            server.start()
            val endpoint = server.url("/repos/Kushro/Nijiyome/releases/latest").toString()
            fun repo(version: String, linux: Boolean) = UpdateRepositoryImpl(
                client = OkHttpClient(),
                installedVersion = version,
                scope = null,
                endpoint = endpoint,
                hasBuild = if (linux) ::hasLinuxBuild else ::hasWindowsBuild,
            )
            val linux = repo("0.1.0", linux = true).check(force = true).getOrThrow()
            assertEquals("0.3.0", linux?.version)
            assertEquals("https://github.com/Kushro/Nijiyome/releases/tag/v0.3.0", linux?.url)
            assertEquals("0.3.0", repo("0.2.9", linux = false).check(force = true).getOrThrow()?.version)
            assertNull(repo("0.3.0", linux = true).check(force = true).getOrThrow(), "same version: up to date")
            assertEquals("GET", server.takeRequest().method)
        }
        // A release with only the APK is not offered on desktop, and the other way round.
        val apkOnly = """{"tag_name":"v9.0.0","assets":[{"name":"Nijiyome-v9.0.0-android-universal.apk"}]}"""
        assertNull(UpdateRepositoryImpl.parseRelease(apkOnly, ::hasLinuxBuild))
        assertNull(UpdateRepositoryImpl.parseRelease(apkOnly, ::hasWindowsBuild))
        val desktopOnly = """{"tag_name":"v9.0.0","assets":[{"name":"Nijiyome-v9.0.0-linux-x86_64.AppImage"}]}"""
        assertNull(UpdateRepositoryImpl.parseRelease(desktopOnly))
        assertEquals("9.0.0", UpdateRepositoryImpl.parseRelease(desktopOnly, ::hasLinuxBuild)?.version)
    }
}

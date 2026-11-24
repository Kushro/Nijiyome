// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import app.nijiyome.model.AppSettings
import app.nijiyome.model.Narrator
import app.nijiyome.model.SettingsRepository
import app.nijiyome.novel.tts.DesktopNarrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 1.1 review #8: closing Koin (the app's exit, a reset) releases the desktop narrator and what it holds. */
class NarratorKoinTest {
    private val tmp: File = Files.createTempDirectory("nijiyome-narrator-koin").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private class Settings : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    @Test
    fun closingKoinReleasesTheNarrator() {
        val app = koinApplication {
            modules(
                module {
                    single<SettingsRepository> { Settings() }
                    single { OkHttpClient() }
                },
                desktopNovelModule(File(tmp, "data"), File(tmp, "cache")),
            )
        }
        val narrator = app.koin.get<Narrator>() as DesktopNarrator
        assertFalse(narrator.released)
        app.close()
        assertTrue(narrator.released)
    }
}

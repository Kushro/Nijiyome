// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import app.nijiyome.model.Narrator
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.TtsEngine
import app.nijiyome.novel.tts.DesktopNarrator
import app.nijiyome.novel.tts.DesktopVoiceCatalog
import app.nijiyome.novel.tts.MpvClipPlayer
import app.nijiyome.novel.tts.kitten.Kitten
import app.nijiyome.player.mpv.DesktopMpvSessionFactory
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

/**
 * Desktop: the shared novel bindings (home, details, chapters, reading progress, readlist) with the store in [filesDir], and the
 * desktop [Narrator] (1.1): the system voice (speech-dispatcher / espeak-ng on Linux, SAPI on Windows), the cloud engines of Android
 * and the local KittenTTS (downloaded on request to `<filesDir>/tts/kitten`). Clips are cached under `<cacheDir>/tts` and played with
 * an audio-only libmpv session (its runtime is the player's, under `<cacheDir>/mpv`).
 */
fun desktopNovelModule(filesDir: File, cacheDir: File = File(filesDir, "cache")): Module = module {
    includes(novelModule { filesDir })
    single<Narrator> {
        val settings = get<SettingsRepository>()
        val secrets = getOrNull<SecretStore>()
        val client = get<OkHttpClient>()
        val kitten = Kitten(File(File(filesDir, "tts"), "kitten"), client, model = { settings.settings.value.tts.model })
        val catalog = DesktopVoiceCatalog(
            cloud = CloudTts(client),
            apiKey = { engine: TtsEngine -> runCatching { secrets?.get(SecretKeys.ttsApiKey(engine.id)) }.getOrNull() },
            kitten = kitten,
        )
        val sessions = DesktopMpvSessionFactory(cacheDir = cacheDir, mainDispatcher = Dispatchers.Main, audioOnly = true)
        DesktopNarrator(
            catalog = catalog,
            player = MpvClipPlayer(sessions, Dispatchers.Main),
            cacheDir = File(cacheDir, DesktopNarrator.CACHE_DIR),
            settings = { settings.settings.value.tts },
        )
    } onClose { narrator ->
        // Closing the app (stopKoin) or resetting it: the libmpv session, ONNX Runtime and its thread, the speech-dispatcher socket.
        (narrator as? DesktopNarrator)?.release()
    }
}

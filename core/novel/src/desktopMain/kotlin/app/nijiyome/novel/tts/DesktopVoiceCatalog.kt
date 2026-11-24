// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsEngine
import app.nijiyome.novel.CloudTts
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.tts.kitten.Kitten

/**
 * The voices of this computer: the system voice is speech-dispatcher on Linux (espeak-ng to a file when there is no speech-dispatcher)
 * and SAPI on Windows; KITTEN is [kitten]; the cloud engines share [cloud] with Android.
 */
internal class DesktopVoiceCatalog(
    private val cloud: CloudTts,
    private val apiKey: suspend (TtsEngine) -> String?,
    private val kitten: Kitten?,
    private val windows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win"),
    private val speechDispatcher: () -> Boolean = { SpeechDispatcherVoice.present() },
    private val find: (String) -> java.io.File? = { Executables.find(it) },
) : VoiceCatalog {
    @Volatile
    private var spdVoice: SpeechDispatcherVoice? = null

    private val spd: SpeechDispatcherVoice
        get() = spdVoice ?: synchronized(this) { spdVoice ?: SpeechDispatcherVoice().also { spdVoice = it } }

    override fun system(): DesktopVoice? = when {
        windows -> (find("powershell") ?: find("pwsh"))?.let { SapiVoice(it) }
        speechDispatcher() -> spd
        else -> find("espeak-ng")?.let { EspeakVoice(it) }
    }

    override fun forEngine(engine: TtsEngine): DesktopVoice? = when (engine) {
        TtsEngine.SYSTEM -> system()
        TtsEngine.KITTEN -> kitten
        TtsEngine.OPENAI, TtsEngine.ELEVENLABS, TtsEngine.OPENAI_COMPATIBLE -> CloudVoice(engine, cloud) { apiKey(engine) }
    }

    override val localVoice: LocalVoiceInstaller? get() = kitten

    override fun releaseIdle() {
        spdVoice?.disconnect()
        kitten?.unloadLater()
    }

    override fun close() {
        spdVoice?.close()
        spdVoice = null
        kitten?.close()
    }
}

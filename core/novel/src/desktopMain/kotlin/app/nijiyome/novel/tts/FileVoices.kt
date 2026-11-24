// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.CloudTts
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import java.io.File
import java.util.Locale

/** Moves a finished `.part` file over [target] (replacing an older one). */
internal fun File.commitTo(target: File): File {
    if (!renameTo(target)) {
        target.delete()
        if (!renameTo(target)) throw TtsException(TtsFailure.EMPTY_AUDIO)
    }
    return target
}

private fun appLanguageFirst(all: List<TtsVoice>): List<TtsVoice> {
    val app = Locale.getDefault().language
    return all.filter { it.lang?.startsWith(app) == true }.ifEmpty { all }.sortedBy { it.label }
}

/**
 * The Linux system voice when speech-dispatcher is not there: `espeak-ng -w <clip>.wav` (the narrator plays and pauses the clip).
 * Pitch: `-p` 0..99 (50 = the voice's own); the speed is applied when playing. The voice id is an espeak-ng language (`es`, `en-us`).
 */
internal class EspeakVoice(private val exe: File) : FileVoice {
    override val extension: String = "wav"

    override fun cacheMaterial(settings: TtsSettings): String = "espeak|${settings.voice.orEmpty()}|${espeakPitch(settings.pitch)}"

    override suspend fun status(): NarratorEngineStatus {
        val version = runCatching { runProcess(listOf(exe.absolutePath, "--version"), timeoutMs = 5_000) }.getOrNull()
        return if (version?.code == 0) {
            NarratorEngineStatus(true, backend = version.stdout.lineSequence().firstOrNull()?.substringBefore(" Data at")?.trim()?.ifEmpty { null } ?: "espeak-ng")
        } else {
            NarratorEngineStatus(false, backend = "espeak-ng", problem = app.nijiyome.novel.NarratorProblem.BROKEN, detail = version?.stderr?.trim())
        }
    }

    override suspend fun voices(settings: TtsSettings): List<TtsVoice> {
        val out = runCatching { runProcess(listOf(exe.absolutePath, "--voices"), timeoutMs = 10_000) }.getOrNull()?.takeIf { it.code == 0 } ?: return emptyList()
        return appLanguageFirst(parseVoices(out.stdout))
    }

    override suspend fun synthesize(text: String, settings: TtsSettings, target: File): File {
        if (target.isFile && target.length() > 44) return target
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        val voice = settings.voice ?: Locale.getDefault().language
        val cmd = listOf(exe.absolutePath, "-w", tmp.absolutePath, "-p", espeakPitch(settings.pitch).toString(), "-v", voice, "--stdin")
        var r = runProcess(cmd, stdin = text)
        // An app language espeak-ng has no voice for: its default voice.
        if (r.code != 0 && settings.voice == null) r = runProcess(cmd.filterIndexed { i, _ -> i != 5 && i != 6 }, stdin = text)
        if (r.code != 0 || !tmp.isFile || tmp.length() <= 44) {
            tmp.delete()
            throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = IllegalStateException("espeak-ng: ${r.stderr.trim().take(200)}"))
        }
        return tmp.commitTo(target)
    }

    companion object {
        /** espeak-ng `-p` of a pitch factor: 50 at 1×, 25 at 0.5×, 99 at 2×. */
        fun espeakPitch(factor: Float): Int = (50f * factor.coerceIn(0.5f, 2f)).toInt().coerceIn(0, 99)

        /** `espeak-ng --voices` (`Pty Language Age/Gender VoiceName File Other`). */
        fun parseVoices(out: String): List<TtsVoice> = out.lineSequence().drop(1).mapNotNull { l ->
            val cols = l.trim().split(Regex("\\s+"))
            if (cols.size < 4) return@mapNotNull null
            val lang = cols[1]
            TtsVoice(lang, "${cols[3].replace('_', ' ')} ($lang)", lang)
        }.toList()
    }
}

/**
 * The Windows system voice: SAPI through PowerShell's `System.Speech`, writing a WAV (as Tatakai Next). The pitch goes in SSML
 * `<prosody>`; the speed is applied when playing.
 * PENDIENTE-ESCRITORIO: SAPI sin probar en Windows real (solo diseño y compilación; la lista de voces y el WAV dependen de PowerShell)
 */
internal class SapiVoice(private val powershell: File) : FileVoice {
    override val extension: String = "wav"

    override fun cacheMaterial(settings: TtsSettings): String = "sapi|${settings.voice.orEmpty()}|${sapiPitchPercent(settings.pitch)}"

    override suspend fun status(): NarratorEngineStatus = NarratorEngineStatus(true, backend = "Windows SAPI")

    override suspend fun voices(settings: TtsSettings): List<TtsVoice> {
        val out = runCatching { runProcess(listOf(powershell.absolutePath, "-NoProfile", "-NonInteractive", "-Command", PS_VOICES), timeoutMs = 20_000) }
            .getOrNull()?.takeIf { it.code == 0 } ?: return emptyList()
        return appLanguageFirst(parseVoices(out.stdout))
    }

    override suspend fun synthesize(text: String, settings: TtsSettings, target: File): File {
        if (target.isFile && target.length() > 44) return target
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part.wav")
        val r = runProcess(
            listOf(powershell.absolutePath, "-NoProfile", "-NonInteractive", "-Command", PS_SPEAK),
            stdin = ssml(text, sapiPitchPercent(settings.pitch)),
            env = mapOf("NIJIYOME_TTS_VOICE" to settings.voice.orEmpty(), "NIJIYOME_TTS_OUT" to tmp.absolutePath),
        )
        if (r.code != 0 || !tmp.isFile || tmp.length() <= 44) {
            tmp.delete()
            throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = IllegalStateException("SAPI: ${r.stderr.trim().take(200)}"))
        }
        return tmp.commitTo(target)
    }

    companion object {
        fun sapiPitchPercent(factor: Float): Int = ssipOffset(factor).coerceIn(-50, 50)

        /** The SSML document SAPI speaks: the text escaped, in the app language, at [pitchPercent]. */
        fun ssml(text: String, pitchPercent: Int, lang: String = Locale.getDefault().toLanguageTag()): String {
            // XML 1.0 refuses control characters other than tab / LF / CR (and U+FFFE / U+FFFF, lone surrogates): SpeakSsml would fail.
            val escaped = xmlText(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            val sign = if (pitchPercent >= 0) "+" else ""
            return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"$lang\">" +
                "<prosody pitch=\"$sign$pitchPercent%\">$escaped</prosody></speak>"
        }

        /** [text] without the characters XML 1.0 does not allow. */
        fun xmlText(text: String): String = buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                        append(c).append(text[i + 1])
                        i++
                    }
                    Character.isSurrogate(c) -> {}
                    c == '\t' || c == '\n' || c == '\r' -> append(c)
                    c < ' ' || c == '\uFFFE' || c == '\uFFFF' -> {}
                    else -> append(c)
                }
                i++
            }
        }

        /** `name|culture|gender` lines of [PS_VOICES]. */
        fun parseVoices(out: String): List<TtsVoice> = out.lineSequence().mapNotNull { l ->
            val p = l.trim().split('|')
            val name = p.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lang = p.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            TtsVoice(name, if (lang != null) "$name ($lang)" else name, lang)
        }.toList()

        const val PS_VOICES: String = "Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "\$s.GetInstalledVoices() | Where-Object { \$_.Enabled } | ForEach-Object { \$_.VoiceInfo.Name + '|' + \$_.VoiceInfo.Culture.Name + '|' + \$_.VoiceInfo.Gender }"

        const val PS_SPEAK: String = "[Console]::InputEncoding = [System.Text.Encoding]::UTF8; Add-Type -AssemblyName System.Speech; " +
            "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "if (\$env:NIJIYOME_TTS_VOICE) { try { \$s.SelectVoice(\$env:NIJIYOME_TTS_VOICE) } catch {} }; " +
            "\$s.SetOutputToWaveFile(\$env:NIJIYOME_TTS_OUT); \$s.SpeakSsml([Console]::In.ReadToEnd()); \$s.Dispose()"
    }
}

/** A cloud engine (OpenAI, ElevenLabs, OpenAI-compatible) through the shared [CloudTts]: mp3 clips, exactly the requests of Android. */
internal class CloudVoice(
    private val engine: TtsEngine,
    private val cloud: CloudTts,
    private val apiKey: suspend () -> String?,
) : FileVoice {
    override val extension: String = "mp3"

    override fun cacheMaterial(settings: TtsSettings): String = "cloud"

    override suspend fun status(): NarratorEngineStatus? = null

    override suspend fun voices(settings: TtsSettings): List<TtsVoice> = cloud.voices(engine, settings, apiKey())

    override suspend fun synthesize(text: String, settings: TtsSettings, target: File): File = cloud.synthesize(engine, text, settings, apiKey(), target)
}

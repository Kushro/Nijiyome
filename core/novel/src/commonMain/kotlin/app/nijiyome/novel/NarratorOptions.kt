// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import app.nijiyome.model.Narrator
import app.nijiyome.model.TtsEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * 1.1: what a platform's [Narrator] offers beyond the shared interface (desktop: the pitch of the system voice, a status row per engine
 * and the local KittenTTS download). A narrator that does not implement it (Android's) gets [defaultEngines], no pitch, no status and
 * no local voice, which is what the settings showed before.
 */
interface NarratorOptions {
    /** The engines the settings offer, in order. */
    val engines: List<TtsEngine>

    /** True when [engine] follows `TtsSettings.pitch`. */
    fun hasPitch(engine: TtsEngine): Boolean

    /** What [engine] runs on here and whether it can speak; null = nothing to show. */
    suspend fun status(engine: TtsEngine): NarratorEngineStatus?

    /** The downloadable local voice (KittenTTS); null where there is none. */
    val localVoice: LocalVoiceInstaller?

    companion object {
        /** The engines of a narrator without options: every engine but the desktop-only local one. */
        val defaultEngines: List<TtsEngine> = TtsEngine.entries.filter { it != TtsEngine.KITTEN }

        fun enginesOf(narrator: Narrator?): List<TtsEngine> = (narrator as? NarratorOptions)?.engines ?: defaultEngines
    }
}

/** Why an engine cannot speak on this computer (the settings map it to a localized hint). */
enum class NarratorProblem {
    /** Linux without speech-dispatcher and espeak-ng; Windows without PowerShell. */
    NO_SYSTEM_VOICE,

    /** KittenTTS: the model or ONNX Runtime is not downloaded. */
    NOT_INSTALLED,

    /** KittenTTS: `espeak-ng` is missing. */
    NO_PHONEMIZER,

    /** No build of the engine for this OS / CPU. */
    UNSUPPORTED_PLATFORM,

    /** The engine is there but failed to start ([NarratorEngineStatus.detail] says why). */
    BROKEN,
}

/**
 * The status row of one engine: [backend] is what speaks (`speech-dispatcher 0.12.0`, `espeak-ng`, `Windows SAPI`,
 * `KittenTTS nano-int8 · ONNX Runtime 1.29.0`), [detail] a raw diagnostic (paths, error text) shown small.
 */
data class NarratorEngineStatus(
    val available: Boolean,
    val backend: String? = null,
    val problem: NarratorProblem? = null,
    val detail: String? = null,
)

/** One downloadable model of the local voice. */
data class LocalVoiceModel(val id: String, val label: String, val approxMb: Int)

/** Progress of a running install: [stage] = `runtime` / `model`; [total] is null when the server sends no length. */
data class LocalVoiceProgress(val stage: String, val done: Long, val total: Long?)

/** The local voice's installer (KittenTTS: ONNX Runtime + model, each checked against a pinned SHA-256). */
interface LocalVoiceInstaller {
    val models: List<LocalVoiceModel>
    val defaultModel: String

    /** Non-null while [install] runs. */
    val progress: StateFlow<LocalVoiceProgress?>

    /** True when [model] and the runtime are on disk. */
    fun installed(model: String): Boolean

    /** Downloads what [model] still needs. */
    suspend fun install(model: String): Result<Unit>

    /** Deletes the runtime and every model. */
    suspend fun remove(): Result<Unit>
}

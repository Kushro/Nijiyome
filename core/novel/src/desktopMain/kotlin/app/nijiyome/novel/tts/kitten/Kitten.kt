// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts.kitten

import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.LocalVoiceModel
import app.nijiyome.novel.LocalVoiceProgress
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import app.nijiyome.novel.tts.Executables
import app.nijiyome.novel.tts.FileVoice
import app.nijiyome.novel.tts.Wav
import app.nijiyome.novel.tts.commitTo
import app.nijiyome.novel.tts.runProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** A file KittenTTS downloads: from [url] to [name], [size] bytes with this [sha256] (pinned; anything else is refused). */
internal data class PinnedFile(val name: String, val url: String, val sha256: String, val size: Long)

/** The runtime and the models one build downloads ([KittenRelease.PINS] in the app; tests point it at a local server). */
internal data class KittenPins(
    val runtime: PinnedFile,
    val runtimeVersion: String,
    val models: Map<String, List<PinnedFile>>,
    val modelInfo: List<LocalVoiceModel>,
    val defaultModel: String,
)

/**
 * What the local voice downloads (1.1, desktop `novel.tts.kitten`, Tatakai Next's local provider), every file pinned by SHA-256:
 * - ONNX Runtime for Java [ORT_VERSION] from Maven Central (MIT; natives for Linux x64 / arm64, Windows x64 and macOS arm64 inside);
 * - the KittenTTS `nano-int8` model (Apache-2.0) at a fixed Hugging Face revision of `KittenML/kitten-tts-nano-0.8-int8`.
 * Tatakai Next also offered `mini` (~80 MB); only the pinned `nano-int8` is offered here.
 */
internal object KittenRelease {
    const val ORT_VERSION: String = "1.29.0"
    val RUNTIME = PinnedFile(
        name = "onnxruntime-$ORT_VERSION.jar",
        url = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/$ORT_VERSION/onnxruntime-$ORT_VERSION.jar",
        sha256 = "5933bbc0c6c4d89afc04bf7c11011de23a947509c1352e5bf5936e62a84e4d10",
        size = 54_400_660,
    )

    const val DEFAULT_MODEL: String = "nano-int8"
    private const val NANO_REPO = "KittenML/kitten-tts-nano-0.8-int8"
    private const val NANO_REVISION = "84781d74e29ee25217551556398b42f80593a813"

    private fun hf(file: String) = "https://huggingface.co/$NANO_REPO/resolve/$NANO_REVISION/$file"

    /** model id → its files (config first). */
    val MODELS: Map<String, List<PinnedFile>> = mapOf(
        DEFAULT_MODEL to listOf(
            PinnedFile("config.json", hf("config.json"), "b66006ccbeccd4de5fc3c9272059c47f5725df7215fd889785c03602652fab64", 688),
            PinnedFile("kitten_tts_nano_v0_8.onnx", hf("kitten_tts_nano_v0_8.onnx"), "f7b0afcbee92870b32b8e0276d855b954dc25470c9f051b376ac7eee537c76fc", 24_369_971),
            PinnedFile("voices.npz", hf("voices.npz"), "8aa7cee235abb0739cb51e6559685f65a4dacd95568833d05699b1633f519b3f", 3_278_902),
        ),
    )

    val MODEL_INFO: List<LocalVoiceModel> = listOf(LocalVoiceModel(DEFAULT_MODEL, "KittenTTS nano 0.8 (int8)", 28))

    val PINS: KittenPins = KittenPins(RUNTIME, ORT_VERSION, MODELS, MODEL_INFO, DEFAULT_MODEL)

    /** True where the ORT jar ships a native library for this OS / CPU. */
    fun platformSupported(os: String = System.getProperty("os.name").orEmpty(), arch: String = System.getProperty("os.arch").orEmpty()): Boolean {
        val o = os.lowercase()
        val a = arch.lowercase()
        val x64 = a == "amd64" || a == "x86_64"
        val arm = a == "aarch64" || a == "arm64"
        return when {
            o.contains("linux") -> x64 || arm
            o.contains("win") -> x64
            o.contains("mac") -> arm
            else -> false
        }
    }
}

/**
 * KittenTTS on desktop: the installer ([LocalVoiceInstaller]) and the voice ([FileVoice], 24 kHz mono WAV clips). Files live in
 * [dir] (`<data>/tts/kitten/`: `runtime/<ORT jar>`, `models/<id>/…`). Text → IPA with the system `espeak-ng` ([phonemizer]; the app
 * never downloads it), then ONNX inference as in Tatakai Next: `input_ids` int64 `[1, n]`, `style` float32 `[1, cols]` (the voice
 * matrix row for the chunk length), `speed` float32 `[1]` × the voice's speed prior; the last [KittenModel.TRIM_SAMPLES] samples of
 * each chunk are dropped. The speed of the reader is applied when playing, like the other clips.
 */
internal class Kitten(
    private val dir: File,
    private val client: OkHttpClient,
    private val phonemizer: () -> File? = { Executables.find("espeak-ng") },
    private val model: () -> String = { KittenRelease.DEFAULT_MODEL },
    private val pins: KittenPins = KittenRelease.PINS,
    private val platformSupported: () -> Boolean = { KittenRelease.platformSupported() },
) : LocalVoiceInstaller, FileVoice {
    override val models: List<LocalVoiceModel> get() = pins.modelInfo
    override val defaultModel: String get() = pins.defaultModel
    private val _progress = MutableStateFlow<LocalVoiceProgress?>(null)
    override val progress: StateFlow<LocalVoiceProgress?> = _progress.asStateFlow()
    override val extension: String = "wav"

    /**
     * ONNX Runtime recurses deeply while it prepares the graph (seen as a native stack overflow, SIGSEGV, on the app's small-stack
     * threads): the model is loaded and run only on this thread, which has a 64 MB stack. Created on the first clip and shut down by
     * [unload] / [close] (guarded by [engineLock]).
     */
    private var onnx: ExecutorCoroutineDispatcher? = null

    private fun onnxDispatcher(): ExecutorCoroutineDispatcher = onnx ?: java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "kitten-onnx", ONNX_STACK_BYTES).apply { isDaemon = true }
    }.asCoroutineDispatcher().also { onnx = it }

    /** Background unloads ([unloadLater]). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // A removal that could not delete files in use (Windows keeps a loaded DLL / an open jar) finishes on the next start.
        if (pendingRemoval.isFile && dir.deleteRecursively()) pendingRemoval.delete()
    }

    private val installLock = Mutex()
    private val engineLock = Mutex()
    private var bridge: OrtBridge? = null
    private var engine: Engine? = null

    @Volatile
    private var loadError: String? = null

    private class Engine(val model: String, val session: OrtBridge.Session, val config: KittenConfig, val voices: Map<String, VoiceMatrix>)

    private val pendingRemoval: File get() = File(dir.parentFile ?: dir, dir.name + ".remove")

    private val runtimeJar get() = File(File(dir, "runtime"), pins.runtime.name)

    private fun modelDir(id: String) = File(File(dir, "models"), id)

    private fun modelId(): String = model().takeIf { it in pins.models } ?: pins.defaultModel

    override fun installed(model: String): Boolean {
        val files = pins.models[model] ?: return false
        return runtimeJar.isFile && files.all { File(modelDir(model), it.name).isFile }
    }

    override suspend fun install(model: String): Result<Unit> = installLock.withLock {
        val files = pins.models[model] ?: return Result.failure(IllegalArgumentException("unknown KittenTTS model $model"))
        try {
            withContext(Dispatchers.IO) {
                if (!runtimeJar.isFile) download(pins.runtime, runtimeJar, "runtime")
                for (f in files) {
                    val target = File(modelDir(model), f.name)
                    if (!target.isFile) download(f, target, "model")
                }
            }
            loadError = null
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _progress.value = null
        }
    }

    /**
     * Deletes the runtime and the models. ONNX Runtime stays loaded in this process if it was used (its native library cannot be
     * loaded twice): where the OS keeps those files locked (Windows), the models go now and the rest on the next start.
     */
    override suspend fun remove(): Result<Unit> = installLock.withLock {
        unload()
        loadError = null
        withContext(Dispatchers.IO) {
            if (dir.deleteRecursively() || !dir.exists()) return@withContext Result.success(Unit)
            File(dir, "models").deleteRecursively()
            if (File(dir, "models").exists()) return@withContext Result.failure(IOException("could not delete $dir"))
            runCatching { pendingRemoval.parentFile?.mkdirs(); pendingRemoval.writeText("") }
                .fold({ Result.success(Unit) }, { Result.failure(IOException("could not delete $dir", it)) })
        }
    }

    /**
     * Narration stopped: closes the model's native session and stops the 64 MB `kitten-onnx` thread (the next clip loads them
     * again). Waits for a clip being synthesised, so a native session is never closed under it.
     */
    suspend fun unload() {
        engineLock.withLock {
            engine?.session?.close()
            engine = null
            onnx?.close()
            onnx = null
        }
    }

    /** [unload] in the background (the narrator's stop is not suspending). */
    fun unloadLater() {
        scope.launch { unload() }
    }

    /** Downloads [file] to [target] through a `.part`, checking its size and SHA-256 as it streams. */
    private suspend fun download(file: PinnedFile, target: File, stage: String) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        _progress.value = LocalVoiceProgress(stage, 0, file.size)
        try {
            fetch(file, part, digest) { done = it }
        } catch (t: Throwable) {
            // Network error, too large, cancelled: no half file stays behind (the jar alone is 54 MB).
            part.delete()
            throw t
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (done != file.size || sha != file.sha256) {
            part.delete()
            throw IOException("${file.name}: checksum mismatch (got $sha, $done bytes)")
        }
        part.commitTo(target)
    }

    private suspend fun fetch(file: PinnedFile, part: File, digest: MessageDigest, onDone: (Long) -> Unit) {
        var done = 0L
        client.newCall(Request.Builder().url(file.url).get().build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for ${file.name}")
            part.outputStream().use { out ->
                r.body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (done + n > file.size) throw IOException("${file.name} is larger than expected")
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onDone(done)
                        _progress.value = _progress.value?.copy(done = done)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------- voice

    override fun cacheMaterial(settings: TtsSettings): String = "kitten|${modelId()}|${settings.voice ?: KittenModel.DEFAULT_VOICE}"

    override suspend fun status(): NarratorEngineStatus {
        val id = modelId()
        val label = pins.modelInfo.firstOrNull { it.id == id }?.label ?: "KittenTTS $id"
        return when {
            !platformSupported() -> NarratorEngineStatus(false, label, NarratorProblem.UNSUPPORTED_PLATFORM)
            !installed(id) -> NarratorEngineStatus(false, label, NarratorProblem.NOT_INSTALLED)
            phonemizer() == null -> NarratorEngineStatus(false, label, NarratorProblem.NO_PHONEMIZER)
            loadError != null -> NarratorEngineStatus(false, label, NarratorProblem.BROKEN, loadError)
            else -> NarratorEngineStatus(true, "$label · ONNX Runtime ${bridge?.version ?: pins.runtimeVersion}")
        }
    }

    override suspend fun voices(settings: TtsSettings): List<TtsVoice> = KittenModel.VOICES.map { TtsVoice(it, it, "en-US") }

    override suspend fun synthesize(text: String, settings: TtsSettings, target: File): File {
        if (target.isFile && target.length() > 44) return target
        val espeak = phonemizer() ?: throw TtsException(TtsFailure.PHONEMIZER_MISSING)
        val id = modelId()
        if (!installed(id)) throw TtsException(TtsFailure.LOCAL_NOT_INSTALLED)
        val samples = engineLock.withLock {
            val e = ensureEngine(id)
            synth(e, espeak, text, settings.voice)
        }
        if (samples.isEmpty()) throw TtsException(TtsFailure.EMPTY_AUDIO)
        return withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.writeBytes(Wav.bytes(samples, engine?.config?.sampleRate ?: KittenModel.DEFAULT_SAMPLE_RATE))
            part.commitTo(target)
        }
    }

    private suspend fun ensureEngine(id: String): Engine = withContext(onnxDispatcher()) {
        engine?.takeIf { it.model == id }?.let { return@withContext it }
        try {
            engine?.session?.close()
            engine = null
            val b = bridge ?: OrtBridge.load(runtimeJar).also { bridge = it }
            val mdir = modelDir(id)
            val config = KittenModel.config(File(mdir, "config.json").readText())
            val voices = KittenModel.npz(File(mdir, config.voicesFile).readBytes())
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            Engine(id, b.session(File(mdir, config.modelFile), threads), config, voices).also {
                engine = it
                loadError = null
            }
        } catch (t: Throwable) {
            loadError = t.message ?: t.toString()
            throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = t)
        }
    }

    private suspend fun synth(e: Engine, espeak: File, text: String, voice: String?): FloatArray = withContext(onnxDispatcher()) {
        val name = KittenModel.resolveVoice(e.config.aliases, e.voices, voice) ?: throw TtsException(TtsFailure.MISSING_VOICE)
        val matrix = e.voices.getValue(name)
        val speed = e.config.speedPriors[name] ?: 1f
        val parts = ArrayList<FloatArray>()
        for (chunk in KittenText.chunkText(KittenText.preprocess(text))) {
            ensureActive()
            val ids = KittenText.inputIds(phonemize(espeak, chunk))
            if (ids.size <= 3) continue
            val style = matrix.styleFor(chunk.length)
            val out = try {
                e.session.runFirstOutput(mapOf("input_ids" to arrayOf(ids), "style" to arrayOf(style), "speed" to floatArrayOf(speed)))
            } catch (t: Throwable) {
                throw TtsException(TtsFailure.ENGINE_UNAVAILABLE, cause = t)
            }
            parts += out.copyOf((out.size - KittenModel.TRIM_SAMPLES).coerceAtLeast(0))
        }
        val all = FloatArray(parts.sumOf { it.size })
        var i = 0
        for (p in parts) {
            p.copyInto(all, i)
            i += p.size
        }
        all
    }

    /** IPA of one chunk with its punctuation kept (`phonemizeWithPreservedPunctuation`). */
    private suspend fun phonemize(espeak: File, chunk: String): String {
        val out = StringBuilder()
        for (piece in KittenText.splitPhrases(chunk)) {
            when (piece) {
                is KittenText.Piece.Literal -> out.append(piece.text)
                is KittenText.Piece.Phrase -> {
                    val r = runProcess(listOf(espeak.absolutePath, "-q", "--ipa", "-v", "en-us", "--stdin"), stdin = piece.text, timeoutMs = 30_000)
                    if (r.code != 0) throw TtsException(TtsFailure.PHONEMIZER_MISSING, cause = IllegalStateException("espeak-ng: ${r.stderr.trim().take(200)}"))
                    out.append(KittenText.cleanIpa(r.stdout))
                }
            }
        }
        return out.toString()
    }

    companion object {
        private const val ONNX_STACK_BYTES = 64L shl 20
        private const val CLOSE_WAIT_MS = 5_000L
    }

    /**
     * The app is closing: [unload] (waiting up to [CLOSE_WAIT_MS] for a clip in progress; past that the native session is left to
     * the process exit rather than closed under the inference), then ONNX Runtime's class loader. Not usable afterwards.
     */
    fun close() {
        val unloaded = runBlocking { withTimeoutOrNull(CLOSE_WAIT_MS) { unload() } } != null
        scope.cancel()
        if (unloaded) {
            bridge?.close()
            bridge = null
        }
    }
}

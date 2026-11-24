// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts

import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.NarratorEngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * 1.1: one way of speaking on desktop. [FileVoice]s write a clip that the narrator plays (and can pause mid-sentence) with libmpv;
 * a [LiveVoice] speaks through the OS on its own (speech-dispatcher).
 */
internal interface DesktopVoice {
    suspend fun status(): NarratorEngineStatus?

    suspend fun voices(settings: TtsSettings): List<TtsVoice>
}

/** Writes the speech of a text to an audio file; the narrator applies the speed when it plays it (so the cache survives a change). */
internal interface FileVoice : DesktopVoice {
    /** File extension of the clips (`wav`, `mp3`). */
    val extension: String

    /** Everything besides the text that changes the clip (voice, pitch, model), for the cache name. */
    fun cacheMaterial(settings: TtsSettings): String

    /** Writes [text] to [target] unless it is there already; returns [target]. Throws [app.nijiyome.novel.TtsException]. */
    suspend fun synthesize(text: String, settings: TtsSettings, target: File): File
}

/** Speaks through the OS: [speak] returns when the text was spoken, and cancelling it stops the speech. */
internal interface LiveVoice : DesktopVoice {
    suspend fun speak(text: String, settings: TtsSettings, speed: Float)

    /** Pauses the speech in progress; false when that is not possible (the narrator then stops and restarts the paragraph). */
    fun pause(): Boolean

    fun resume(): Boolean

    fun close()
}

/** Speed / pitch factor (0.5..2) as the -100..100 offset of speech-dispatcher (100 % = 0). */
internal fun ssipOffset(factor: Float): Int = (((factor.coerceIn(0.5f, 2f)) - 1f) * 100f).toInt().coerceIn(-100, 100)

/** Executables on `PATH` (and the usual folders an app launched from a menu may not inherit). */
internal object Executables {
    private val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    fun find(name: String, path: String? = System.getenv("PATH")): File? {
        val dirs = path.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() } +
            if (windows) emptyList() else listOf("/usr/bin", "/usr/local/bin", "/bin")
        val names = if (windows) listOf("$name.exe", "$name.cmd", "$name.bat", name) else listOf(name)
        for (dir in dirs.distinct()) for (n in names) {
            val f = File(dir, n)
            if (f.isFile && f.canExecute()) return f
        }
        return null
    }
}

/** Result of a finished process. */
internal class ProcessOutput(val code: Int, val stdout: String, val stderr: String)

/**
 * Runs [command] with [stdin] written to it and waits for it; cancelling the caller kills it. Output is read fully (the tools used
 * print little); [env] adds variables.
 */
internal suspend fun runProcess(command: List<String>, stdin: String? = null, env: Map<String, String> = emptyMap(), timeoutMs: Long = 120_000): ProcessOutput {
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder(command).apply {
            environment().putAll(env)
            if (stdin == null) redirectInput(ProcessBuilder.Redirect.from(File(if (File.separatorChar == '\\') "NUL" else "/dev/null")))
        }.start()
    }
    try {
        val out = StringBuilder()
        val err = StringBuilder()
        val readers = listOf(
            Thread { out.append(process.inputStream.bufferedReader().readText()) },
            Thread { err.append(process.errorStream.bufferedReader().readText()) },
        ).onEach { it.isDaemon = true; it.start() }
        if (stdin != null) {
            withContext(Dispatchers.IO) { process.outputStream.use { it.write(stdin.toByteArray(Charsets.UTF_8)) } }
        }
        val finished = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                process.onExit().whenComplete { _, _ -> if (cont.isActive) cont.resume(true) }
                cont.invokeOnCancellation { process.destroyForcibly() }
            }
        } ?: false
        if (!finished) {
            process.destroyForcibly()
            return ProcessOutput(-1, out.toString(), "timed out")
        }
        withContext(Dispatchers.IO) { readers.forEach { it.join(2_000) } }
        return ProcessOutput(process.exitValue(), out.toString(), err.toString())
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

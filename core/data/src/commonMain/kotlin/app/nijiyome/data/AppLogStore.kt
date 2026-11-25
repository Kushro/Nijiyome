// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.AppLog
import app.nijiyome.model.LogLevel
import app.nijiyome.model.LogRepository
import app.nijiyome.model.LogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

// Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.4). Owner: worker q. Moved from androidMain unchanged except the
// platform mirror (hito 5).

/** The platform's own log for a formatted line (Android: logcat, tag `Nijiyome`; desktop: stderr). */
internal expect fun mirrorLogLine(level: LogLevel, line: String)

/**
 * File-backed app log under [dir] (`filesDir/logs/app.log`, rotated at [maxBytes] into `app.1.log`), installed as `AppLog.sink` by
 * `dataModule`. Settings › Acerca de › Abrir registros shows [recentLines] and shares [logFile] through the `FileProvider` declared in
 * the :core:data manifest (`${applicationId}.logs`). Lines are scrubbed ([scrub]): no query strings, no bearer tokens, no keys.
 * Writes happen on one background thread, so [log] never blocks the caller. [mirror] also sends the line to the platform log ([mirrorLogLine]: logcat on Android, stderr on desktop).
 */
class AppLogStore(
    private val dir: File,
    private val maxBytes: Long = MAX_BYTES,
    private val mirror: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : LogRepository, LogSink {
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "nijiyome-log").apply { isDaemon = true } }
    private val lock = Any()

    private val current: File get() = File(dir, FILE)
    private val rotated: File get() = File(dir, ROTATED)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val line = format(clock(), level, tag, message, throwable)
        if (mirror) runCatching { mirrorLogLine(level, line) }
        runCatching { writer.execute { append(line) } }
        AppLog.notifyObservers(level, line)
    }

    /**
     * Installs a chained [Thread.UncaughtExceptionHandler] that writes uncaught crashes synchronously before
     * delegating to the previous handler, ensuring crash traces survive process termination.
     */
    fun installCrashHandler(appVersion: String = "", platform: String = "") {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val meta = buildList {
                    if (appVersion.isNotBlank()) add("v=$appVersion")
                    if (platform.isNotBlank()) add("os=$platform")
                    add("thread=${thread.name}")
                }.joinToString(" ")
                val line = format(clock(), LogLevel.ERROR, "crash", "[FATAL] uncaught exception [$meta]", throwable)
                if (mirror) runCatching { mirrorLogLine(LogLevel.ERROR, line) }
                append(line)
                AppLog.notifyObservers(LogLevel.ERROR, line)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Synchronous append (tests and the writer thread). */
    internal fun append(line: String) {
        synchronized(lock) {
            try {
                dir.mkdirs()
                val file = current
                if (file.isFile && file.length() + line.length + 1 > maxBytes) {
                    rotated.delete()
                    file.renameTo(rotated)
                }
                current.appendText(line + "\n", Charsets.UTF_8)
            } catch (_: Exception) {
                // the log must never break the app
            }
        }
    }

    override suspend fun recentLines(max: Int): List<String> = withContext(Dispatchers.IO) {
        flush()
        synchronized(lock) {
            val lines = buildList {
                if (rotated.isFile) addAll(rotated.readLines(Charsets.UTF_8))
                if (current.isFile) addAll(current.readLines(Charsets.UTF_8))
            }.filter { it.isNotBlank() }
            lines.takeLast(max.coerceAtLeast(0))
        }
    }

    override fun logFile(): File? = current.takeIf { it.isFile && it.length() > 0 }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        flush()
        synchronized(lock) {
            current.delete()
            rotated.delete()
        }
        Unit
    }

    /** Waits for the lines queued so far (bounded). */
    private fun flush() {
        runCatching { writer.submit {}.get(FLUSH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    companion object {
        const val FILE = "app.log"
        const val ROTATED = "app.1.log"
        const val MAX_BYTES = 512L * 1024
        private const val FLUSH_TIMEOUT_MS = 2_000L
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /**
         * `2026-11-09T21:04:11.000Z E/player: message (IOException: reset <- SocketException: broken) | trace: ...`
         * Includes the full cause chain (up to 4 causes) and top 8 relevant stack frames (prioritizing `app.nijiyome.*`)
         * for WARN/ERROR entries, with [scrub] applied to the entire line.
         */
        fun format(epochMs: Long, level: LogLevel, tag: String, message: String, throwable: Throwable?): String {
            val letter = when (level) {
                LogLevel.INFO -> 'I'
                LogLevel.WARN -> 'W'
                LogLevel.ERROR -> 'E'
            }
            val cause = throwable?.let { formatThrowable(it, includeFrames = level != LogLevel.INFO) }.orEmpty()
            val text = scrub("$message$cause").replace('\n', ' ').replace('\r', ' ')
            return "${TIME.format(Instant.ofEpochMilli(epochMs))} $letter/${tag.take(32)}: $text"
        }

        private fun formatThrowable(root: Throwable, includeFrames: Boolean): String {
            val chain = buildList {
                var cur: Throwable? = root
                val seen = HashSet<Throwable>()
                while (cur != null && size < 4 && seen.add(cur)) {
                    val name = cur::class.java.simpleName.ifBlank { cur::class.java.name }
                    val msg = cur.message?.takeIf { it.isNotBlank() }?.let { ": ${it.take(240)}" }.orEmpty()
                    add("$name$msg")
                    cur = cur.cause
                }
            }.joinToString(" <- ")
            if (!includeFrames) return " ($chain)"
            val deepest = generateSequence(root) { it.cause }.take(4).last()
            val frames = collectRelevantFrames(deepest, root)
            return if (frames.isEmpty()) " ($chain)" else " ($chain) | trace: ${frames.joinToString(" <- ")}"
        }

        private fun collectRelevantFrames(deepest: Throwable, root: Throwable): List<String> {
            val out = ArrayList<String>(8)
            fun addFrame(f: StackTraceElement) {
                val cls = f.className.substringAfterLast('.')
                val loc = if (f.lineNumber > 0) "${f.fileName ?: cls}:${f.lineNumber}" else (f.fileName ?: cls)
                val entry = "$cls.${f.methodName}($loc)"
                if (entry !in out && out.size < 8) out += entry
            }
            // Always keep the top frame where the root cause was thrown
            deepest.stackTrace.firstOrNull()?.let(::addFrame)
            // Prioritize app.nijiyome.* frames from deepest, then root
            for (f in deepest.stackTrace) {
                if (out.size >= 8) break
                if (f.className.startsWith("app.nijiyome.")) addFrame(f)
            }
            if (deepest !== root) {
                for (f in root.stackTrace) {
                    if (out.size >= 8) break
                    if (f.className.startsWith("app.nijiyome.")) addFrame(f)
                }
            }
            // If no app.nijiyome.* frames were present, take up to 3 top frames from deepest
            if (out.size <= 1) {
                for (f in deepest.stackTrace.drop(1).take(3)) addFrame(f)
            }
            return out
        }

        private val QUERY = Regex("""(https?://[^\s?#]+)\?[^\s]*""")
        private val BEARER = Regex("""(?i)(bearer\s+)[A-Za-z0-9._\-~+/=]+""")
        private val SECRET_PARAM = Regex("""(?i)\b(access_token|refresh_token|token|apikey|api_key|code|code_verifier|client_secret)=([^\s&]+)""")

        /** Drops URL query strings and redacts bearer tokens / key parameters (the file is meant to be shared in bug reports). */
        fun scrub(text: String): String = text
            .replace(QUERY) { it.groupValues[1] + "?…" }
            .replace(BEARER) { it.groupValues[1] + "…" }
            .replace(SECRET_PARAM) { it.groupValues[1] + "=…" }
    }
}

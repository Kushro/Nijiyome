// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

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

        /** `2026-11-09T21:04:11.000Z I/player: message (IOException: reset)` with [scrub] applied. */
        fun format(epochMs: Long, level: LogLevel, tag: String, message: String, throwable: Throwable?): String {
            val letter = when (level) {
                LogLevel.INFO -> 'I'
                LogLevel.WARN -> 'W'
                LogLevel.ERROR -> 'E'
            }
            val cause = throwable?.let { t -> " (${t::class.java.simpleName}${t.message?.let { ": $it" }.orEmpty()})" }.orEmpty()
            val text = scrub("$message$cause").replace('\n', ' ').replace('\r', ' ')
            return "${TIME.format(Instant.ofEpochMilli(epochMs))} $letter/${tag.take(32)}: $text"
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

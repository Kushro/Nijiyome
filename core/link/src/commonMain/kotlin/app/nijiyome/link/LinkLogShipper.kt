// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import app.nijiyome.model.AppLog
import app.nijiyome.model.LinkState
import app.nijiyome.model.LogLevel
import app.nijiyome.model.LogPushTrigger
import app.nijiyome.model.LogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/**
 * Ships local diagnostic logs from a paired mobile device to the PC via Nijiyome Link (`POST /v1/logs`).
 * - Tracks a persisted SHA-256 cursor of the last shipped line so only unseen lines are sent.
 * - Automatically ships when Link becomes paired & reachable (catching up on prior crashes/sessions).
 * - Automatically schedules a debounced ship (2.5 s) whenever a [LogLevel.ERROR] is logged.
 * - Also exposes [pushNow] for manual push from Settings › Acerca de › Abrir registros.
 */
class LinkLogShipper(
    private val link: LinkRepositoryImpl,
    private val logs: () -> LogRepository?,
    private val cursorFile: File,
    private val appVersion: String = "",
    private val platform: String = "android",
    io: CoroutineDispatcher = Dispatchers.IO,
) : LogPushTrigger {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val mutex = Mutex()
    @Volatile private var debounceJob: Job? = null

    fun start() {
        scope.launch {
            link.state
                .map { it is LinkState.Paired && it.reachable }
                .distinctUntilChanged()
                .collect { online ->
                    if (online) runCatching { pushInternal(forceAll = false) }
                }
        }
        AppLog.addObserver { level, _ ->
            if (level == LogLevel.ERROR && link.state.value is LinkState.Paired) {
                scheduleErrorFlush()
            }
        }
    }

    private fun scheduleErrorFlush() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(ERROR_DEBOUNCE_MS)
            runCatching { pushInternal(forceAll = false) }
        }
    }

    override suspend fun pushNow(): Result<Int> = runCatching { pushInternal(forceAll = true) }

    private suspend fun pushInternal(forceAll: Boolean): Int = mutex.withLock {
        if (link.state.value !is LinkState.Paired) {
            throw IllegalStateException("link:not_paired")
        }
        val repo = logs() ?: return@withLock 0
        val allLines = repo.recentLines(MAX_PULL_LINES)
        if (allLines.isEmpty()) return@withLock 0

        val lastHash = if (forceAll) null else readCursor()
        val toSend = if (lastHash == null) {
            allLines.takeLast(MAX_PUSH_BATCH)
        } else {
            val idx = allLines.indexOfLast { sha256(it) == lastHash }
            if (idx >= 0) allLines.drop(idx + 1).takeLast(MAX_PUSH_BATCH) else allLines.takeLast(MAX_PUSH_BATCH)
        }
        if (toSend.isEmpty()) return@withLock 0

        val accepted = link.pushLogs(toSend, appVersion = appVersion.ifBlank { null }, platform = platform).getOrThrow()
        writeCursor(sha256(toSend.last()))
        accepted
    }

    private fun readCursor(): String? = runCatching {
        cursorFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCursor(hash: String) {
        runCatching {
            cursorFile.parentFile?.mkdirs()
            cursorFile.writeText(hash, Charsets.UTF_8)
        }
    }

    companion object {
        private const val ERROR_DEBOUNCE_MS = 2_500L
        private const val MAX_PULL_LINES = 500
        private const val MAX_PUSH_BATCH = 400

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

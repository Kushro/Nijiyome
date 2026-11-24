// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.ListsRepository
import app.nijiyome.model.inBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Desktop counterpart of `WorkManagerListSyncScheduler`: pushes the offline list queue from the app's background [scope] (there
 * is no job scheduler that survives the app on desktop; the queue lives in the database and the next start pushes it too). A new
 * request replaces a waiting one (WorkManager's REPLACE). While the push fails and changes remain, it retries with an exponential
 * backoff from 30 s, like the Android worker, up to [maxAttempts]. Like the worker, every push is background traffic for the AniList
 * gateway (`inBackground`): what the user does on screen goes first.
 */
class DesktopListSyncScheduler(
    private val scope: CoroutineScope,
    private val lists: () -> ListsRepository?,
    private val firstDelayMs: Long = 2_000,
    private val backoffMs: Long = 30_000,
    private val maxAttempts: Int = 8,
) : ListSyncScheduler {
    private var job: Job? = null

    @Synchronized
    override fun requestPush() {
        job?.cancel()
        job = scope.launch {
            delay(firstDelayMs)
            var wait = backoffMs
            repeat(maxAttempts) {
                val repo = lists() ?: return@launch
                val control = repo as? ListsSyncControl ?: return@launch
                val retry = try {
                    val report = inBackground { control.pushPending() }
                    report.failed > 0 && repo.pendingChanges.value > 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    true
                }
                if (!retry) return@launch
                delay(wait)
                wait = (wait * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private companion object {
        const val MAX_BACKOFF_MS = 30L * 60 * 1000
    }
}

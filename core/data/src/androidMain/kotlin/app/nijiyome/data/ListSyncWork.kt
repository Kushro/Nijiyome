// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.inBackground
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nijiyome.model.ListsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class WorkManagerListSyncScheduler(private val context: Context) : ListSyncScheduler {
    override fun requestPush() {
        val request = OneTimeWorkRequestBuilder<ListSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE: a new change must not wait behind a backed-off attempt; the queue in Room is the source of truth.
        WorkManager.getInstance(context).enqueueUniqueWork(ListSyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class ListSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val lists: ListsRepository by inject()

    // 0.3.1: WorkManager jobs are background traffic for the AniList gateway (interactive requests go first).
    override suspend fun doWork(): Result = inBackground { work() }

    private suspend fun work(): Result = try {
        val control = lists as? ListsSyncControl
        if (control == null) {
            Result.success()
        } else {
            val report = control.pushPending()
            // offline / provider down: keep the queue and retry with backoff
            if (report.failed > 0 && lists.pendingChanges.value > 0) Result.retry() else Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "nijiyome-lists-push"
    }
}


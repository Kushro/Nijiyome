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
import app.nijiyome.database.HistoryDao
import app.nijiyome.link.HistoryResyncMode
import app.nijiyome.link.LinkException
import app.nijiyome.link.LinkHistoryMode
import app.nijiyome.link.LinkHistoryRemote
import app.nijiyome.link.LinkHistoryUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class WorkManagerHistorySyncScheduler(private val context: Context) : HistorySyncScheduler {
    override fun requestFollowUp() = enqueue(immediate = false, name = HistorySyncWorker.UNIQUE_NAME + "-next")

    override fun requestSync(immediate: Boolean) = enqueue(immediate, HistorySyncWorker.UNIQUE_NAME)

    private fun enqueue(immediate: Boolean, name: String) {
        val request = OneTimeWorkRequestBuilder<HistorySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (!immediate) setInitialDelay(HistorySyncWorker.COALESCE_SECONDS, TimeUnit.SECONDS) }
            .build()
        // KEEP: while a delayed job is pending, further writes join it (coalescing). REPLACE: flush now.
        val policy = if (immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }
}

class HistorySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val syncer: HistorySyncer by inject()
    private val scheduler: HistorySyncScheduler by inject()

    // 0.3.1: WorkManager jobs are background traffic for the AniList gateway (interactive requests go first).
    override suspend fun doWork(): Result = inBackground { work() }

    private suspend fun work(): Result = try {
        syncer.syncOnce()
        // Rows written while this pass ran: schedule the next coalesced window.
        if (syncer.hasPending()) scheduler.requestFollowUp()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: LinkHistoryUnavailableException) {
        Result.success() // no PC: nothing to push until the user pairs one again
    } catch (_: LinkException) {
        Result.retry() // PC offline / busy: exponential backoff
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "nijiyome-history-sync"
        const val COALESCE_SECONDS = 15L
    }
}

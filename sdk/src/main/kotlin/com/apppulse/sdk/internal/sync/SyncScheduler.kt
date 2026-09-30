package com.apppulse.sdk.internal.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.apppulse.sdk.internal.logging.SdkLogger
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager scheduling for event sync operations.
 *
 * Two types of sync are managed:
 *
 * 1. **Periodic sync** — runs every 15 minutes (minimum allowed by WorkManager).
 *    Catches up on any events that failed immediate upload.
 *    Uses KEEP policy so re-initialization doesn't reset the schedule.
 *
 * 2. **One-shot sync** — enqueued after a batch of events is tracked.
 *    Uses REPLACE policy so rapid event tracking doesn't queue redundant workers.
 *
 * Both have a **CONNECTED** network constraint — WorkManager automatically
 * holds the work until the device has network connectivity.
 *
 * **Backoff strategy:** Exponential backoff starting at 30 seconds.
 * WorkManager handles the backoff automatically when the Worker returns
 * [Result.retry()].
 */
internal class SyncScheduler(
    private val context: Context
) {

    companion object {
        private const val TAG = "SyncScheduler"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        private const val BACKOFF_DELAY_SECONDS = 30L
    }

    /** Network constraints shared by both periodic and one-shot work. */
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedules periodic sync that runs every 15 minutes.
     *
     * Called once during SDK initialization. Uses [ExistingPeriodicWorkPolicy.KEEP]
     * so re-initializing the SDK doesn't restart the timer.
     */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<EventSyncWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS
            )
            .addTag(EventSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EventSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        SdkLogger.d("$TAG: Periodic sync scheduled (every ${PERIODIC_INTERVAL_MINUTES}min)")
    }

    /**
     * Enqueues an immediate one-shot sync.
     *
     * Called after events are tracked to attempt upload sooner than
     * the next periodic interval. Uses [ExistingWorkPolicy.REPLACE] so
     * rapid-fire event tracking doesn't stack up redundant workers.
     */
    fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<EventSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS
            )
            .addTag(EventSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EventSyncWorker.ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        SdkLogger.d("$TAG: Immediate sync enqueued")
    }

    /**
     * Cancels all scheduled sync work.
     * Called when the SDK is reset (testing) or shut down.
     */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(EventSyncWorker.TAG)
        SdkLogger.d("$TAG: All sync work cancelled")
    }
}

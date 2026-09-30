package com.apppulse.sdk.internal.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apppulse.sdk.AppPulse
import com.apppulse.sdk.internal.logging.SdkLogger
import com.apppulse.sdk.internal.network.RemoteDataSource
import com.apppulse.sdk.internal.network.UploadResult
import com.apppulse.sdk.internal.repository.EventRepository

/**
 * WorkManager worker that syncs pending events to the backend.
 *
 * **How it works:**
 * 1. Queries Room for PENDING events (up to batchSize)
 * 2. Marks them as SENDING (prevents double-send)
 * 3. Uploads via [RemoteDataSource.uploadBatch]
 * 4. On success: deletes from Room, increments upload counter
 * 5. On retryable failure: increments retry count or marks FAILED if limit exceeded
 * 6. On permanent failure: marks FAILED immediately
 *
 * **Retry logic:**
 * - WorkManager handles worker-level retries (exponential backoff)
 * - Per-event retry count is tracked in Room ([AnalyticsEventEntity.retryCount])
 * - Events exceeding [retryLimit] are moved to FAILED status
 *
 * **Threading:** Extends [CoroutineWorker], so [doWork] runs on a background
 * thread managed by WorkManager. Room and Retrofit calls are suspend functions.
 */
internal class EventSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "EventSyncWorker"

        /** Unique work name for periodic sync. */
        const val PERIODIC_WORK_NAME = "apppulse_periodic_sync"

        /** Unique work name for one-shot sync. */
        const val ONE_SHOT_WORK_NAME = "apppulse_one_shot_sync"
    }

    override suspend fun doWork(): Result {
        SdkLogger.d("$TAG: Starting sync...")

        // Access the SDK singleton — if not initialized, we can't sync
        val manager = AppPulse.manager
        if (manager == null) {
            SdkLogger.w("$TAG: SDK not initialized, skipping sync")
            return Result.failure()
        }

        val repository = manager.repository
        val config = manager.config

        // Create a RemoteDataSource for this sync cycle
        val remoteDataSource = com.apppulse.sdk.internal.network.NetworkClient
            .createApiService(config)
            .let { com.apppulse.sdk.internal.network.RemoteDataSource(it) }

        return try {
            syncPendingEvents(repository, remoteDataSource, config.batchSize, config.retryLimit)
        } catch (e: Exception) {
            SdkLogger.e("$TAG: Unexpected error during sync", e)
            Result.retry()
        }
    }

    /**
     * Core sync loop: fetch batches of pending events and upload them
     * until no more are pending or an error stops progress.
     */
    private suspend fun syncPendingEvents(
        repository: EventRepository,
        remoteDataSource: RemoteDataSource,
        batchSize: Int,
        retryLimit: Int
    ): Result {
        var totalUploaded = 0
        var hasMoreEvents = true

        while (hasMoreEvents) {
            // Fetch next batch
            val pendingEvents = repository.getPendingEvents(batchSize)

            if (pendingEvents.isEmpty()) {
                SdkLogger.d("$TAG: No pending events, sync complete (uploaded=$totalUploaded)")
                return Result.success()
            }

            val eventIds = pendingEvents.map { it.id }

            // Mark as SENDING to prevent double-send
            repository.markSending(eventIds)
            SdkLogger.d("$TAG: Uploading batch of ${pendingEvents.size} events...")

            // Upload batch
            when (val result = remoteDataSource.uploadBatch(pendingEvents)) {
                is UploadResult.Success -> {
                    // Remove sent events from database
                    repository.markSent(eventIds)
                    totalUploaded += eventIds.size

                    // Update session counter
                    AppPulse.manager?.let { it.uploadedCount += eventIds.size }
                    SdkLogger.d("$TAG: Batch uploaded successfully (${eventIds.size} events)")
                }

                is UploadResult.RetryableFailure -> {
                    SdkLogger.w("$TAG: Batch upload failed (retryable): ${result.error.message}")
                    handleRetryableFailure(repository, eventIds, retryLimit)

                    // Stop processing more batches — retry later
                    hasMoreEvents = false
                }

                is UploadResult.PermanentFailure -> {
                    SdkLogger.w("$TAG: Batch upload failed (permanent): ${result.error.message}")
                    // Mark all events in this batch as FAILED
                    for (id in eventIds) {
                        repository.markFailed(id)
                    }
                    hasMoreEvents = false
                }
            }

            // If this batch was smaller than batchSize, there are no more events
            if (pendingEvents.size < batchSize) {
                hasMoreEvents = false
            }
        }

        return if (totalUploaded > 0) {
            SdkLogger.d("$TAG: Sync complete, uploaded $totalUploaded events total")
            Result.success()
        } else {
            SdkLogger.d("$TAG: Sync complete, will retry remaining events later")
            Result.retry()
        }
    }

    /**
     * For each event in a failed batch:
     * - If retry count < retryLimit → increment retry count, revert to PENDING
     * - If retry count >= retryLimit → mark as FAILED (permanently)
     */
    private suspend fun handleRetryableFailure(
        repository: EventRepository,
        eventIds: List<String>,
        retryLimit: Int
    ) {
        for (id in eventIds) {
            val retryCount = repository.getRetryCount(id) ?: 0
            if (retryCount >= retryLimit) {
                SdkLogger.w("$TAG: Event $id exceeded retry limit ($retryLimit), marking FAILED")
                repository.markFailed(id)
            } else {
                repository.incrementRetry(id)
                SdkLogger.d("$TAG: Event $id retry count → ${retryCount + 1}/$retryLimit")
            }
        }
    }
}

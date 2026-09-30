package com.apppulse.sdk.internal.manager

import android.content.Context
import com.apppulse.sdk.AppPulse
import com.apppulse.sdk.AppPulseConfig
import com.apppulse.sdk.AppPulseError
import com.apppulse.sdk.EventCallback
import com.apppulse.sdk.EventCounts
import com.apppulse.sdk.internal.db.AppPulseDatabase
import com.apppulse.sdk.internal.logging.SdkLogger
import com.apppulse.sdk.internal.model.AnalyticsEvent
import com.apppulse.sdk.internal.network.NetworkClient
import com.apppulse.sdk.internal.network.RemoteDataSource
import com.apppulse.sdk.internal.network.UploadResult
import com.apppulse.sdk.internal.repository.EventRepository
import com.apppulse.sdk.internal.repository.EventRepositoryImpl
import com.apppulse.sdk.internal.sync.SyncScheduler
import com.apppulse.sdk.internal.util.DeviceInfo
import com.apppulse.sdk.internal.validation.EventValidator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Internal orchestrator for SDK operations.
 *
 * Coordinates between the public [AppPulse] API and internal components:
 * - [EventValidator] for input validation
 * - [EventRepository] for Room persistence
 * - [RemoteDataSource] for REST API communication
 * - [SyncScheduler] for WorkManager-based background sync
 * - [DeviceInfo] for device metadata
 *
 * **Threading model:**
 * - Public methods are safe to call from any thread
 * - Database and network I/O run on [Dispatchers.IO] via [sdkScope]
 * - Callbacks are delivered on [Dispatchers.Main]
 */
internal class AppPulseManager(
    private val applicationContext: Context,
    internal val config: AppPulseConfig
) {

    /** The currently identified user, or null. */
    @Volatile
    var userId: String? = null
        private set

    // ── Lazy-initialized dependencies ──

    private val database: AppPulseDatabase by lazy {
        AppPulseDatabase.getInstance(applicationContext)
    }

    internal val repository: EventRepository by lazy {
        EventRepositoryImpl(database.eventDao())
    }

    private val remoteDataSource: RemoteDataSource by lazy {
        val apiService = NetworkClient.createApiService(config)
        RemoteDataSource(apiService)
    }

    private val syncScheduler: SyncScheduler by lazy {
        SyncScheduler(applicationContext)
    }

    // ── Coroutine infrastructure ──

    private val sdkScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            SdkLogger.e("Uncaught exception in SDK coroutine", throwable)
        }
    )

    // ── Cached metadata ──

    private val deviceInfo: Map<String, String> by lazy {
        DeviceInfo.collect(applicationContext)
    }

    private val appVersion: String? by lazy {
        DeviceInfo.getAppVersion(applicationContext)
    }

    // ── Session-level counters ──

    @Volatile
    var uploadedCount: Int = 0
        internal set

    init {
        SdkLogger.d("AppPulseManager created (endpoint=${config.endpoint}, batchSize=${config.batchSize})")

        // Schedule periodic background sync via WorkManager
        syncScheduler.schedulePeriodicSync()
    }

    // ──────────────────────────────────────────────
    // User Identification
    // ──────────────────────────────────────────────

    fun setUserId(userId: String) {
        if (userId.isBlank()) {
            SdkLogger.w("setUserId called with blank userId — ignoring")
            return
        }
        this.userId = userId
        SdkLogger.d("User set: ${SdkLogger.maskValue(userId)}")
    }

    fun clearUser() {
        userId = null
        SdkLogger.d("User cleared")
    }

    // ──────────────────────────────────────────────
    // Event Tracking
    // ──────────────────────────────────────────────

    /**
     * Validates, enriches, persists, and attempts to upload an event.
     *
     * Flow:
     * 1. Validate name + properties
     * 2. Create [AnalyticsEvent] with UUID, timestamp, device info
     * 3. Save to Room (guaranteed persistence)
     * 4. Callback with success (event is safely queued)
     * 5. Attempt immediate upload (best-effort)
     * 6. Schedule WorkManager sync as backup
     */
    fun trackEvent(
        name: String,
        properties: Map<String, Any>?,
        callback: EventCallback?
    ) {
        val validationError = EventValidator.validate(name, properties)
        if (validationError != null) {
            SdkLogger.w("Event validation failed: ${validationError.message}")
            callback?.onFailure(validationError)
            return
        }

        val event = AnalyticsEvent(
            id = UUID.randomUUID().toString(),
            name = name,
            userId = userId,
            properties = properties,
            timestamp = System.currentTimeMillis(),
            sdkVersion = AppPulse.SDK_VERSION,
            appVersion = appVersion,
            deviceInfo = deviceInfo
        )

        sdkScope.launch {
            try {
                repository.saveEvent(event)
                SdkLogger.d("Event queued: ${event.name} (id=${event.id})")

                withContext(Dispatchers.Main) {
                    callback?.onSuccess()
                }

                // Best-effort immediate upload
                attemptImmediateUpload(event)

                // Schedule WorkManager sync as a safety net
                syncScheduler.scheduleImmediateSync()

            } catch (e: Exception) {
                SdkLogger.e("Failed to save event: ${e.message}", e)
                val error = AppPulseError.StorageError(
                    "Failed to store event: ${e.message}", e
                )
                withContext(Dispatchers.Main) {
                    callback?.onFailure(error)
                }
            }
        }
    }

    private suspend fun attemptImmediateUpload(event: AnalyticsEvent) {
        try {
            repository.markSending(listOf(event.id))

            when (val result = remoteDataSource.uploadEvent(event)) {
                is UploadResult.Success -> {
                    repository.markSent(listOf(event.id))
                    uploadedCount++
                    SdkLogger.d("Event uploaded immediately: ${event.name}")
                }

                is UploadResult.RetryableFailure -> {
                    repository.incrementRetry(event.id)
                    SdkLogger.d("Immediate upload failed (retryable): ${result.error.message}")
                }

                is UploadResult.PermanentFailure -> {
                    repository.markFailed(event.id)
                    SdkLogger.w("Immediate upload failed (permanent): ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            try {
                repository.incrementRetry(event.id)
            } catch (revertError: Exception) {
                SdkLogger.e("Failed to revert event status", revertError)
            }
            SdkLogger.e("Immediate upload attempt failed: ${e.message}", e)
        }
    }

    // ──────────────────────────────────────────────
    // Sync Control
    // ──────────────────────────────────────────────

    /**
     * Triggers an immediate one-shot sync via WorkManager.
     * Respects network constraints — will wait for connectivity.
     */
    fun forceSync() {
        syncScheduler.scheduleImmediateSync()
        SdkLogger.d("Force sync requested")
    }

    // ──────────────────────────────────────────────
    // Event Statistics
    // ──────────────────────────────────────────────

    suspend fun getEventCounts(): EventCounts {
        return EventCounts(
            pending = repository.getPendingCount(),
            uploaded = uploadedCount,
            failed = repository.getFailedCount()
        )
    }

    suspend fun deleteAllEvents() {
        repository.deleteAllEvents()
        uploadedCount = 0
        SdkLogger.d("All events cleared")
    }

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    fun shutdown() {
        sdkScope.cancel()
        syncScheduler.cancelAll()
        SdkLogger.d("AppPulseManager shut down")
    }
}

package com.apppulse.sdk

import android.content.Context
import com.apppulse.sdk.internal.logging.SdkLogger
import com.apppulse.sdk.internal.manager.AppPulseManager
import kotlinx.coroutines.launch

/**
 * AppPulse — Android Analytics SDK
 *
 * The main entry point for the AppPulse analytics SDK. This is the **only**
 * class that host applications interact with directly.
 *
 * ## Quick Start
 *
 * ```kotlin
 * // 1. Initialize (typically in Application.onCreate)
 * val config = AppPulseConfig(
 *     apiKey = "your-api-key",
 *     endpoint = "https://analytics.example.com/"
 * )
 * AppPulse.initialize(applicationContext, config)
 *
 * // 2. Identify the user
 * AppPulse.setUserId("user-123")
 *
 * // 3. Track events
 * AppPulse.trackEvent("login")
 * AppPulse.trackEvent("purchase", mapOf("product_id" to "abc", "price" to 499))
 * ```
 *
 * ## Design Notes
 *
 * - **Singleton pattern:** [AppPulse] is a Kotlin `object`, ensuring a single
 *   instance across the application. This is the standard pattern for Android SDKs
 *   (e.g., Firebase, Amplitude, Segment).
 *
 * - **Idempotent initialization:** Calling [initialize] more than once is safe —
 *   the second call logs a warning and returns success without re-initializing.
 *
 * - **No leaks:** The SDK stores `applicationContext`, never an Activity context.
 *
 * - **Fail-safe:** No method on this object will throw an exception that crashes the
 *   host application. Errors are returned via [Result] or [EventCallback].
 */
object AppPulse {

    /** The current SDK version, embedded in event metadata. */
    internal const val SDK_VERSION = "1.0.0"

    /**
     * The internal manager that does all the real work.
     * Null until [initialize] is called.
     * Marked @Volatile for safe reads from any thread.
     */
    @Volatile
    internal var manager: AppPulseManager? = null
        private set

    /** Whether the SDK has been initialized. */
    val isInitialized: Boolean
        get() = manager != null

    /**
     * Initializes the AppPulse SDK.
     *
     * Must be called **once** before any other SDK method, typically in
     * `Application.onCreate()`.
     *
     * @param context Any [Context]. Will be converted to `applicationContext` internally.
     * @param config  SDK configuration. Validated before use.
     * @return [Result.success] if initialization succeeded, [Result.failure] with
     *         an [IllegalArgumentException] if the config is invalid.
     */
    @Synchronized
    fun initialize(context: Context, config: AppPulseConfig): Result<Unit> {
        // Idempotent — safe to call twice
        if (isInitialized) {
            SdkLogger.w("AppPulse already initialized. Ignoring duplicate call.")
            return Result.success(Unit)
        }

        // Validate configuration before proceeding
        val validationError = validateConfig(config)
        if (validationError != null) {
            SdkLogger.e("Initialization failed: ${validationError.message}")
            return Result.failure(IllegalArgumentException(validationError.message))
        }

        // Configure logger first so subsequent log calls respect the flag
        SdkLogger.isDebugEnabled = config.debugLogging

        // Always use applicationContext to prevent Activity memory leaks
        val appContext = context.applicationContext

        // Create the internal manager
        manager = AppPulseManager(appContext, config)

        SdkLogger.i("SDK initialized (version=$SDK_VERSION, endpoint=${config.endpoint})")

        return Result.success(Unit)
    }

    /**
     * Sets the current user identifier.
     *
     * All events tracked after this call will include this user ID.
     * Call [clearUser] to disassociate subsequent events from any user.
     *
     * @param userId A non-blank user identifier string.
     */
    fun setUserId(userId: String) {
        requireInitialized()?.setUserId(userId)
    }

    /**
     * Clears the current user identifier.
     *
     * Events tracked after this call will not be associated with any user.
     */
    fun clearUser() {
        requireInitialized()?.clearUser()
    }

    /**
     * Tracks a custom analytics event.
     *
     * The event is validated, persisted locally, and queued for delivery.
     * If the network is available, delivery is attempted immediately.
     * If offline, the event is stored and retried when connectivity returns.
     *
     * @param name       The event name. Must be non-blank.
     * @param properties Optional key-value pairs of event data.
     *                   Values must be [String], [Number], or [Boolean].
     * @param callback   Optional callback to receive success/failure notification.
     */
    fun trackEvent(
        name: String,
        properties: Map<String, Any>? = null,
        callback: EventCallback? = null
    ) {
        val mgr = requireInitialized()
        if (mgr == null) {
            callback?.onFailure(
                AppPulseError.InitializationError(
                    "AppPulse SDK is not initialized. Call AppPulse.initialize() first."
                )
            )
            return
        }
        mgr.trackEvent(name, properties, callback)
    }

    // ──────────────────────────────────────────────
    // Event Statistics
    // ──────────────────────────────────────────────

    /**
     * Retrieves a snapshot of event queue statistics.
     *
     * @param callback Receives [EventCounts] on the main thread.
     */
    fun getEventCounts(callback: (EventCounts) -> Unit) {
        val mgr = requireInitialized() ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val counts = mgr.getEventCounts()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    callback(counts)
                }
            } catch (e: Exception) {
                SdkLogger.e("Failed to get event counts: ${e.message}", e)
            }
        }
    }

    /**
     * Deletes all events from the local queue and resets counters.
     * Primarily for debugging / demo use.
     */
    fun deleteAllEvents() {
        val mgr = requireInitialized() ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                mgr.deleteAllEvents()
            } catch (e: Exception) {
                SdkLogger.e("Failed to delete events: ${e.message}", e)
            }
        }
    }

    /**
     * Forces an immediate sync of pending events.
     *
     * Enqueues a one-shot WorkManager task that will upload all pending
     * events when network is available. If already connected, upload
     * starts immediately.
     */
    fun forceSync() {
        requireInitialized()?.forceSync()
    }

    // ──────────────────────────────────────────────
    // Internal / Testing
    // ──────────────────────────────────────────────

    /**
     * Resets the SDK to its uninitialized state.
     * **For testing only** — not part of the public API.
     */
    @Synchronized
    internal fun reset() {
        manager?.shutdown()
        manager = null
        SdkLogger.isDebugEnabled = false
        SdkLogger.d("SDK reset")
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * Validates the [AppPulseConfig] and returns a [ConfigurationError] if
     * any field is invalid, or null if the config is valid.
     */
    private fun validateConfig(config: AppPulseConfig): AppPulseError.ConfigurationError? {
        if (config.apiKey.isBlank()) {
            return AppPulseError.ConfigurationError("API key must not be blank")
        }
        if (config.endpoint.isBlank()) {
            return AppPulseError.ConfigurationError("Endpoint must not be blank")
        }
        if (!config.endpoint.startsWith("http://") && !config.endpoint.startsWith("https://")) {
            return AppPulseError.ConfigurationError(
                "Endpoint must start with http:// or https://"
            )
        }
        if (config.batchSize < 1) {
            return AppPulseError.ConfigurationError("Batch size must be at least 1")
        }
        if (config.retryLimit < 0) {
            return AppPulseError.ConfigurationError("Retry limit must not be negative")
        }
        return null
    }

    /**
     * Returns the [AppPulseManager] if initialized, or logs an error and returns null.
     * This is the gatekeeper — every public method goes through here.
     */
    private fun requireInitialized(): AppPulseManager? {
        val mgr = manager
        if (mgr == null) {
            SdkLogger.e("AppPulse SDK is not initialized. Call AppPulse.initialize() first.")
        }
        return mgr
    }
}

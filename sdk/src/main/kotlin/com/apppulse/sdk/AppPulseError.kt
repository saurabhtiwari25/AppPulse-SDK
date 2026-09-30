package com.apppulse.sdk

/**
 * Sealed class hierarchy representing errors from the AppPulse SDK.
 *
 * SDK methods that can fail return or callback with a specific [AppPulseError] subtype.
 * This allows the host application to handle each error case appropriately without
 * catching raw exceptions.
 *
 * ```kotlin
 * AppPulse.trackEvent("purchase", callback = object : EventCallback {
 *     override fun onSuccess() { /* ... */ }
 *     override fun onFailure(error: AppPulseError) {
 *         when (error) {
 *             is AppPulseError.ValidationError -> { /* bad input */ }
 *             is AppPulseError.NetworkError -> { /* no connection */ }
 *             else -> { /* ... */ }
 *         }
 *     }
 * })
 * ```
 *
 * @property message A human-readable description of the error.
 * @property cause The underlying throwable, if any.
 */
sealed class AppPulseError(
    val message: String,
    val cause: Throwable? = null
) {
    /** The SDK has not been initialized. Call [AppPulse.initialize] first. */
    class InitializationError(message: String, cause: Throwable? = null) :
        AppPulseError(message, cause)

    /** The provided [AppPulseConfig] contains invalid values. */
    class ConfigurationError(message: String) :
        AppPulseError(message)

    /** The event name or properties failed validation. */
    class ValidationError(message: String) :
        AppPulseError(message)

    /** A network-level failure (no connectivity, DNS resolution, etc.). */
    class NetworkError(message: String, cause: Throwable? = null) :
        AppPulseError(message, cause)

    /** The network request timed out. */
    class TimeoutError(message: String, cause: Throwable? = null) :
        AppPulseError(message, cause)

    /** The analytics API returned a non-success HTTP status. */
    class ApiError(val statusCode: Int, message: String) :
        AppPulseError(message)

    /** A local storage (Room database) operation failed. */
    class StorageError(message: String, cause: Throwable? = null) :
        AppPulseError(message, cause)

    override fun toString(): String =
        "${this::class.simpleName}(message='$message')"
}

package com.apppulse.sdk

/**
 * Callback interface for event tracking results.
 *
 * Pass an implementation to [AppPulse.trackEvent] to receive
 * success or failure notifications.
 *
 * ```kotlin
 * AppPulse.trackEvent(
 *     name = "purchase",
 *     callback = object : EventCallback {
 *         override fun onSuccess() {
 *             Log.d("Demo", "Event queued successfully")
 *         }
 *         override fun onFailure(error: AppPulseError) {
 *             Log.e("Demo", "Event failed: $error")
 *         }
 *     }
 * )
 * ```
 */
interface EventCallback {

    /** Called when the event has been successfully queued for delivery. */
    fun onSuccess()

    /** Called when the event could not be queued due to [error]. */
    fun onFailure(error: AppPulseError)
}

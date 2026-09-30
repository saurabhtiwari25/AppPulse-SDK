package com.apppulse.sdk.internal.model

/**
 * Status of an analytics event in the local queue.
 *
 * State machine:
 * ```
 * PENDING → SENDING → (deleted on success)
 *                    → FAILED (non-retryable or max retries exceeded)
 *                    → PENDING (retryable failure, retryCount incremented)
 * ```
 */
internal enum class EventStatus {
    /** Event is queued and waiting to be sent. */
    PENDING,

    /** Event is currently being transmitted to the API. */
    SENDING,

    /** Event was permanently failed (non-retryable or exceeded retry limit). */
    FAILED
}

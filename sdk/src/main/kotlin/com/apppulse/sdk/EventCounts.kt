package com.apppulse.sdk

/**
 * Snapshot of event queue statistics.
 *
 * Returned by [AppPulse.getEventCounts] to let the host application
 * display sync status without accessing SDK internals.
 *
 * @property pending  Events queued locally, waiting to be sent.
 * @property uploaded Events successfully delivered to the backend (session count).
 * @property failed   Events that permanently failed (exceeded retry limit).
 */
data class EventCounts(
    val pending: Int,
    val uploaded: Int,
    val failed: Int
)

package com.apppulse.sdk.internal.repository

import com.apppulse.sdk.internal.model.AnalyticsEvent

/**
 * Repository interface for analytics event persistence.
 *
 * This abstraction sits between the SDK manager and the Room database,
 * allowing the storage implementation to be swapped (e.g., for testing
 * with an in-memory database).
 */
internal interface EventRepository {

    /** Persists an event with PENDING status. */
    suspend fun saveEvent(event: AnalyticsEvent)

    /** Returns up to [limit] events with PENDING status, ordered oldest-first. */
    suspend fun getPendingEvents(limit: Int): List<AnalyticsEvent>

    /** Marks events as currently being transmitted. */
    suspend fun markSending(eventIds: List<String>)

    /** Removes successfully sent events from the database. */
    suspend fun markSent(eventIds: List<String>)

    /** Marks an event as permanently failed. */
    suspend fun markFailed(eventId: String)

    /** Increments retry count and returns the event to PENDING status. */
    suspend fun incrementRetry(eventId: String)

    /** Returns the current retry count for an event, or null if not found. */
    suspend fun getRetryCount(eventId: String): Int?

    /** Deletes specific events by ID. */
    suspend fun deleteEvents(eventIds: List<String>)

    /** Deletes all events from the database. */
    suspend fun deleteAllEvents()

    /** Returns the number of events with PENDING status. */
    suspend fun getPendingCount(): Int

    /** Returns the number of events with FAILED status. */
    suspend fun getFailedCount(): Int
}

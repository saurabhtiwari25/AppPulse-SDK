package com.apppulse.sdk.internal.repository

import com.apppulse.sdk.internal.db.AnalyticsEventEntity
import com.apppulse.sdk.internal.db.EventDao
import com.apppulse.sdk.internal.logging.SdkLogger
import com.apppulse.sdk.internal.model.AnalyticsEvent
import com.apppulse.sdk.internal.model.EventStatus
import com.google.gson.Gson

/**
 * Room-backed implementation of [EventRepository].
 *
 * Handles mapping between the domain model [AnalyticsEvent] and the
 * Room entity [AnalyticsEventEntity]. Complex fields (properties, deviceInfo)
 * are serialized to JSON strings using Gson.
 */
internal class EventRepositoryImpl(
    private val eventDao: EventDao,
    private val gson: Gson = Gson()
) : EventRepository {

    override suspend fun saveEvent(event: AnalyticsEvent) {
        val entity = toEntity(event)
        eventDao.insert(entity)
        SdkLogger.d("Event saved to database: ${event.name} (id=${event.id})")
    }

    override suspend fun getPendingEvents(limit: Int): List<AnalyticsEvent> {
        return eventDao.getByStatus(EventStatus.PENDING.name, limit).map { toModel(it) }
    }

    override suspend fun markSending(eventIds: List<String>) {
        if (eventIds.isNotEmpty()) {
            eventDao.updateStatusBatch(eventIds, EventStatus.SENDING.name)
        }
    }

    override suspend fun markSent(eventIds: List<String>) {
        // Successfully sent events are deleted to save storage space.
        // The uploaded count is tracked in-memory by AppPulseManager.
        if (eventIds.isNotEmpty()) {
            eventDao.deleteByIds(eventIds)
            SdkLogger.d("Deleted ${eventIds.size} sent events from database")
        }
    }

    override suspend fun markFailed(eventId: String) {
        eventDao.updateStatus(eventId, EventStatus.FAILED.name)
        SdkLogger.d("Event marked as FAILED: $eventId")
    }

    override suspend fun incrementRetry(eventId: String) {
        eventDao.incrementRetryAndUpdateStatus(eventId, EventStatus.PENDING.name)
    }

    override suspend fun getRetryCount(eventId: String): Int? {
        return eventDao.getRetryCount(eventId)
    }

    override suspend fun deleteEvents(eventIds: List<String>) {
        if (eventIds.isNotEmpty()) {
            eventDao.deleteByIds(eventIds)
        }
    }

    override suspend fun deleteAllEvents() {
        eventDao.deleteAll()
        SdkLogger.d("All events deleted from database")
    }

    override suspend fun getPendingCount(): Int {
        return eventDao.countByStatus(EventStatus.PENDING.name)
    }

    override suspend fun getFailedCount(): Int {
        return eventDao.countByStatus(EventStatus.FAILED.name)
    }

    // ──────────────────────────────────────────────
    // Entity ↔ Domain Model Mapping
    // ──────────────────────────────────────────────

    private fun toEntity(event: AnalyticsEvent): AnalyticsEventEntity {
        return AnalyticsEventEntity(
            id = event.id,
            eventName = event.name,
            userId = event.userId,
            propertiesJson = event.properties?.let { gson.toJson(it) },
            timestamp = event.timestamp,
            sdkVersion = event.sdkVersion,
            appVersion = event.appVersion,
            deviceInfoJson = gson.toJson(event.deviceInfo),
            retryCount = 0,
            status = EventStatus.PENDING.name
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toModel(entity: AnalyticsEventEntity): AnalyticsEvent {
        return AnalyticsEvent(
            id = entity.id,
            name = entity.eventName,
            userId = entity.userId,
            properties = entity.propertiesJson?.let {
                gson.fromJson(it, Map::class.java) as? Map<String, Any>
            },
            timestamp = entity.timestamp,
            sdkVersion = entity.sdkVersion,
            appVersion = entity.appVersion,
            deviceInfo = entity.deviceInfoJson?.let {
                gson.fromJson(it, Map::class.java) as? Map<String, String>
            } ?: emptyMap()
        )
    }
}

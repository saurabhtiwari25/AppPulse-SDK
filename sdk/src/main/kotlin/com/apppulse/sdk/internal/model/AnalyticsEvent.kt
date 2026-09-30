package com.apppulse.sdk.internal.model

/**
 * Internal domain model representing an analytics event.
 *
 * This is the SDK's internal representation — it is never exposed to the host app.
 * It contains all metadata that will be sent to the analytics backend.
 *
 * The mapping to/from the Room [AnalyticsEventEntity] happens in [EventRepositoryImpl].
 */
internal data class AnalyticsEvent(
    /** Unique event identifier (UUID). */
    val id: String,

    /** Event name (e.g., "purchase", "login"). */
    val name: String,

    /** The user ID at the time the event was tracked, or null. */
    val userId: String?,

    /** Optional key-value properties attached to the event. */
    val properties: Map<String, Any>?,

    /** Epoch milliseconds when the event was created. */
    val timestamp: Long,

    /** SDK version string (e.g., "1.0.0"). */
    val sdkVersion: String,

    /** Host application version, or null if unavailable. */
    val appVersion: String?,

    /** Device metadata (OS, model, locale, etc.). */
    val deviceInfo: Map<String, String>
)

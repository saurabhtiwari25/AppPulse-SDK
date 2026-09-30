package com.apppulse.sdk.internal.network

import com.google.gson.annotations.SerializedName

/**
 * JSON request body for a single analytics event.
 *
 * This DTO is what actually gets serialized to JSON and sent over the wire.
 * It maps from the internal [AnalyticsEvent] domain model.
 *
 * Example JSON:
 * ```json
 * {
 *   "eventId": "abc-123",
 *   "eventName": "purchase",
 *   "userId": "user-123",
 *   "timestamp": 1720000000000,
 *   "properties": { "product_id": "SKU-001", "price": 499 },
 *   "sdkVersion": "1.0.0",
 *   "appVersion": "1.0.0",
 *   "deviceInfo": { "os": "Android", "device_model": "Pixel 7" }
 * }
 * ```
 */
internal data class EventRequest(
    @SerializedName("eventId")
    val eventId: String,

    @SerializedName("eventName")
    val eventName: String,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("properties")
    val properties: Map<String, Any>?,

    @SerializedName("sdkVersion")
    val sdkVersion: String,

    @SerializedName("appVersion")
    val appVersion: String?,

    @SerializedName("deviceInfo")
    val deviceInfo: Map<String, String>?
)

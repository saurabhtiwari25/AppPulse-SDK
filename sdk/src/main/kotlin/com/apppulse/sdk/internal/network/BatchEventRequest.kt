package com.apppulse.sdk.internal.network

import com.google.gson.annotations.SerializedName

/**
 * JSON request body for a batch of analytics events.
 *
 * Example JSON:
 * ```json
 * {
 *   "events": [
 *     { "eventId": "abc-123", "eventName": "login", ... },
 *     { "eventId": "def-456", "eventName": "purchase", ... }
 *   ]
 * }
 * ```
 */
internal data class BatchEventRequest(
    @SerializedName("events")
    val events: List<EventRequest>
)

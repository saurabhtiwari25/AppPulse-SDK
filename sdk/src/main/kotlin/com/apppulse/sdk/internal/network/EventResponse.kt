package com.apppulse.sdk.internal.network

import com.google.gson.annotations.SerializedName

/**
 * JSON response from the analytics API.
 *
 * Example success:
 * ```json
 * { "success": true, "message": "Events received" }
 * ```
 *
 * Example error:
 * ```json
 * { "success": false, "message": "Invalid API key" }
 * ```
 */
internal data class EventResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String?
)

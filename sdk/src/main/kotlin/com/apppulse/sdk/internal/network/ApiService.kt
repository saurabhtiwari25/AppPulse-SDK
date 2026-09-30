package com.apppulse.sdk.internal.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service interface for the analytics REST API.
 *
 * All methods are suspend functions — they run on the calling coroutine's
 * dispatcher (typically [Dispatchers.IO]) and return Retrofit [Response]
 * wrappers so we can inspect HTTP status codes.
 *
 * **Important:** This interface is `internal` — the host application never
 * sees Retrofit types.
 */
internal interface ApiService {

    /**
     * Sends a single analytics event.
     *
     * ```
     * POST /events
     * Content-Type: application/json
     * X-API-Key: <apiKey>
     * ```
     */
    @POST("events")
    suspend fun postEvent(@Body event: EventRequest): Response<EventResponse>

    /**
     * Sends a batch of analytics events.
     *
     * ```
     * POST /events/batch
     * Content-Type: application/json
     * X-API-Key: <apiKey>
     * ```
     */
    @POST("events/batch")
    suspend fun postEventsBatch(@Body batch: BatchEventRequest): Response<EventResponse>
}

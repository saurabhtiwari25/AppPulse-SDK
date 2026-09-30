package com.apppulse.sdk.internal.network

import com.apppulse.sdk.AppPulseError
import com.apppulse.sdk.internal.logging.SdkLogger
import com.apppulse.sdk.internal.model.AnalyticsEvent
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Result of an upload attempt.
 *
 * The caller uses this to decide:
 * - [Success] → delete the events from Room
 * - [RetryableFailure] → keep in Room, increment retry count
 * - [PermanentFailure] → mark as FAILED, don't retry
 */
internal sealed class UploadResult {
    /** Events were accepted by the server (HTTP 2xx). */
    data object Success : UploadResult()

    /** Temporary failure — should be retried later. */
    data class RetryableFailure(val error: AppPulseError) : UploadResult()

    /** Permanent failure — should NOT be retried. */
    data class PermanentFailure(val error: AppPulseError) : UploadResult()
}

/**
 * Wraps [ApiService] and maps HTTP responses and exceptions to [UploadResult].
 *
 * Error classification:
 * ```
 * HTTP 2xx            → Success
 * HTTP 400            → PermanentFailure (bad request)
 * HTTP 401            → PermanentFailure (invalid API key)
 * HTTP 429            → RetryableFailure (rate limited)
 * HTTP 5xx            → RetryableFailure (server error)
 * SocketTimeout       → RetryableFailure (timeout)
 * UnknownHost / IO    → RetryableFailure (network error)
 * Other exception     → RetryableFailure (unknown)
 * ```
 *
 * **Important:** This class is `internal` — the host application never
 * interacts with it directly.
 */
internal class RemoteDataSource(
    private val apiService: ApiService
) {

    /**
     * Uploads a single event.
     */
    suspend fun uploadEvent(event: AnalyticsEvent): UploadResult {
        val request = toRequest(event)
        return executeWithErrorMapping {
            apiService.postEvent(request)
        }
    }

    /**
     * Uploads a batch of events in a single HTTP request.
     */
    suspend fun uploadBatch(events: List<AnalyticsEvent>): UploadResult {
        if (events.isEmpty()) return UploadResult.Success

        val batch = BatchEventRequest(
            events = events.map { toRequest(it) }
        )
        return executeWithErrorMapping {
            apiService.postEventsBatch(batch)
        }
    }

    // ──────────────────────────────────────────────
    // Error Mapping
    // ──────────────────────────────────────────────

    /**
     * Executes an API call and maps the result to [UploadResult].
     * Catches all exceptions so the SDK never crashes.
     */
    private suspend fun executeWithErrorMapping(
        call: suspend () -> Response<EventResponse>
    ): UploadResult {
        return try {
            val response = call()
            mapHttpResponse(response)
        } catch (e: SocketTimeoutException) {
            SdkLogger.w("Request timed out", e)
            UploadResult.RetryableFailure(
                AppPulseError.TimeoutError("Request timed out", e)
            )
        } catch (e: UnknownHostException) {
            SdkLogger.w("DNS resolution failed (offline?)", e)
            UploadResult.RetryableFailure(
                AppPulseError.NetworkError("Unable to resolve host — device may be offline", e)
            )
        } catch (e: IOException) {
            SdkLogger.w("Network I/O error", e)
            UploadResult.RetryableFailure(
                AppPulseError.NetworkError("Network error: ${e.message}", e)
            )
        } catch (e: Exception) {
            SdkLogger.e("Unexpected error during upload", e)
            UploadResult.RetryableFailure(
                AppPulseError.NetworkError("Unexpected error: ${e.message}", e)
            )
        }
    }

    /**
     * Maps an HTTP response to [UploadResult] based on the status code.
     */
    private fun mapHttpResponse(response: Response<EventResponse>): UploadResult {
        val code = response.code()

        return when {
            // 2xx — Success
            response.isSuccessful -> {
                SdkLogger.d("Upload successful (HTTP $code)")
                UploadResult.Success
            }

            // 400 — Bad Request (malformed payload)
            code == 400 -> {
                val msg = "Bad request (HTTP 400): ${extractErrorMessage(response)}"
                SdkLogger.w(msg)
                UploadResult.PermanentFailure(AppPulseError.ApiError(code, msg))
            }

            // 401 — Unauthorized (invalid API key)
            code == 401 -> {
                val msg = "Invalid API key (HTTP 401)"
                SdkLogger.e(msg)
                UploadResult.PermanentFailure(AppPulseError.ApiError(code, msg))
            }

            // 429 — Rate Limited (retry later)
            code == 429 -> {
                val msg = "Rate limited (HTTP 429)"
                SdkLogger.w(msg)
                UploadResult.RetryableFailure(AppPulseError.ApiError(code, msg))
            }

            // 5xx — Server Error (retry later)
            code in 500..599 -> {
                val msg = "Server error (HTTP $code)"
                SdkLogger.w(msg)
                UploadResult.RetryableFailure(AppPulseError.ApiError(code, msg))
            }

            // Other 4xx — Permanent failure
            code in 400..499 -> {
                val msg = "Client error (HTTP $code): ${extractErrorMessage(response)}"
                SdkLogger.w(msg)
                UploadResult.PermanentFailure(AppPulseError.ApiError(code, msg))
            }

            // Anything else — treat as retryable
            else -> {
                val msg = "Unexpected HTTP status: $code"
                SdkLogger.w(msg)
                UploadResult.RetryableFailure(AppPulseError.ApiError(code, msg))
            }
        }
    }

    /**
     * Attempts to extract an error message from the response body.
     */
    private fun extractErrorMessage(response: Response<EventResponse>): String {
        return try {
            response.errorBody()?.string()?.take(200) ?: "No error body"
        } catch (e: Exception) {
            "Unable to read error body"
        }
    }

    // ──────────────────────────────────────────────
    // Domain → DTO Mapping
    // ──────────────────────────────────────────────

    private fun toRequest(event: AnalyticsEvent): EventRequest {
        return EventRequest(
            eventId = event.id,
            eventName = event.name,
            userId = event.userId,
            timestamp = event.timestamp,
            properties = event.properties,
            sdkVersion = event.sdkVersion,
            appVersion = event.appVersion,
            deviceInfo = event.deviceInfo
        )
    }
}

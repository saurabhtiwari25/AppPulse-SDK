package com.apppulse.sdk.internal.network

import com.apppulse.sdk.internal.model.AnalyticsEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Unit tests for [RemoteDataSource] using OkHttp's [MockWebServer].
 *
 * MockWebServer runs a real HTTP server on localhost, so these tests
 * verify actual HTTP behavior (status codes, headers, JSON parsing)
 * without hitting a real backend.
 */
class RemoteDataSourceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var remoteDataSource: RemoteDataSource

    private val testEvent = AnalyticsEvent(
        id = "test-event-001",
        name = "purchase",
        userId = "user-123",
        properties = mapOf("price" to 499, "currency" to "USD"),
        timestamp = 1720000000000L,
        sdkVersion = "1.0.0",
        appVersion = "1.0.0",
        deviceInfo = mapOf("os" to "Android", "device_model" to "Pixel 7")
    )

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        remoteDataSource = RemoteDataSource(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ──────────────────────────────────────────────
    // Success Cases
    // ──────────────────────────────────────────────

    @Test
    fun `HTTP 200 returns Success`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "message": "Event received"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.Success::class.java)
    }

    @Test
    fun `HTTP 201 returns Success`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"success": true, "message": "Created"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.Success::class.java)
    }

    @Test
    fun `successful upload sends correct JSON payload`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true}""")
                .addHeader("Content-Type", "application/json")
        )

        remoteDataSource.uploadEvent(testEvent)

        val request = mockWebServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/events")

        val body = request.body.readUtf8()
        assertThat(body).contains("\"eventId\":\"test-event-001\"")
        assertThat(body).contains("\"eventName\":\"purchase\"")
        assertThat(body).contains("\"userId\":\"user-123\"")
    }

    // ──────────────────────────────────────────────
    // Permanent Failure Cases
    // ──────────────────────────────────────────────

    @Test
    fun `HTTP 400 returns PermanentFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"success": false, "message": "Invalid payload"}""")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.PermanentFailure::class.java)
    }

    @Test
    fun `HTTP 401 returns PermanentFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success": false, "message": "Invalid API key"}""")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.PermanentFailure::class.java)
    }

    @Test
    fun `HTTP 403 returns PermanentFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"success": false, "message": "Forbidden"}""")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.PermanentFailure::class.java)
    }

    // ──────────────────────────────────────────────
    // Retryable Failure Cases
    // ──────────────────────────────────────────────

    @Test
    fun `HTTP 429 returns RetryableFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"success": false, "message": "Too many requests"}""")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.RetryableFailure::class.java)
    }

    @Test
    fun `HTTP 500 returns RetryableFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"success": false, "message": "Internal server error"}""")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.RetryableFailure::class.java)
    }

    @Test
    fun `HTTP 502 returns RetryableFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("Bad Gateway")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.RetryableFailure::class.java)
    }

    @Test
    fun `HTTP 503 returns RetryableFailure`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable")
        )

        val result = remoteDataSource.uploadEvent(testEvent)

        assertThat(result).isInstanceOf(UploadResult.RetryableFailure::class.java)
    }

    // ──────────────────────────────────────────────
    // Batch Upload
    // ──────────────────────────────────────────────

    @Test
    fun `batch upload sends correct path`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "message": "Batch received"}""")
                .addHeader("Content-Type", "application/json")
        )

        val events = listOf(
            testEvent,
            testEvent.copy(id = "test-event-002", name = "login")
        )

        val result = remoteDataSource.uploadBatch(events)

        assertThat(result).isInstanceOf(UploadResult.Success::class.java)

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/events/batch")

        val body = request.body.readUtf8()
        assertThat(body).contains("\"events\"")
        assertThat(body).contains("test-event-001")
        assertThat(body).contains("test-event-002")
    }

    @Test
    fun `empty batch returns Success without making request`() = runTest {
        val result = remoteDataSource.uploadBatch(emptyList())

        assertThat(result).isInstanceOf(UploadResult.Success::class.java)
        assertThat(mockWebServer.requestCount).isEqualTo(0)
    }
}

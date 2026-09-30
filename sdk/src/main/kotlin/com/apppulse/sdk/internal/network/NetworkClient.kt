package com.apppulse.sdk.internal.network

import com.apppulse.sdk.AppPulseConfig
import com.apppulse.sdk.internal.logging.SdkLogger
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory that creates the OkHttp client and Retrofit service.
 *
 * Configures:
 * - **Timeouts**: connect=10s, read=30s, write=30s
 * - **API key header**: Sent via `X-API-Key` on every request (never in URL or logs)
 * - **Logging interceptor**: Enabled only when [AppPulseConfig.debugLogging] is true,
 *   and limited to HEADERS level to avoid logging event payloads
 *
 * **Security:** The API key interceptor adds the header but the logging
 * interceptor is added AFTER it, with HEADERS level (not BODY), so the
 * API key value itself is never printed to Logcat.
 */
internal object NetworkClient {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    /**
     * Creates a configured [ApiService] instance.
     *
     * @param config SDK configuration providing endpoint, API key, and debug flag.
     */
    fun createApiService(config: AppPulseConfig): ApiService {
        val client = buildOkHttpClient(config)
        val retrofit = buildRetrofit(config.endpoint, client)
        return retrofit.create(ApiService::class.java)
    }

    private fun buildOkHttpClient(config: AppPulseConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // API key interceptor — adds X-API-Key header to every request
        builder.addInterceptor(createApiKeyInterceptor(config.apiKey))

        // Logging interceptor — only when debug logging is enabled
        if (config.debugLogging) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    private fun buildRetrofit(endpoint: String, client: OkHttpClient): Retrofit {
        // Ensure endpoint ends with "/" (Retrofit requirement)
        val baseUrl = if (endpoint.endsWith("/")) endpoint else "$endpoint/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
    }

    /**
     * Interceptor that adds the API key as an HTTP header.
     * The key is never logged or included in the URL.
     */
    private fun createApiKeyInterceptor(apiKey: String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
    }

    /**
     * Logging interceptor set to HEADERS level.
     * BODY level is intentionally avoided to prevent logging event payloads
     * which may contain user data.
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            SdkLogger.d("HTTP: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }
}

package com.apppulse.sdk

/**
 * Configuration for the AppPulse Analytics SDK.
 *
 * Pass an instance to [AppPulse.initialize] to configure the SDK.
 *
 * ```kotlin
 * val config = AppPulseConfig(
 *     apiKey = "your-api-key",
 *     endpoint = "https://analytics.example.com/",
 *     debugLogging = true
 * )
 * ```
 *
 * @property apiKey The API key for authenticating with the analytics backend. Must not be blank.
 * @property endpoint The base URL of the analytics API. Must start with http:// or https://.
 * @property debugLogging Whether to enable verbose SDK logging. Defaults to false.
 *                        Should only be enabled during development.
 * @property batchSize Number of events to send per network request. Defaults to 20. Must be >= 1.
 * @property retryLimit Maximum number of retry attempts for failed events. Defaults to 3. Must be >= 0.
 */
data class AppPulseConfig(
    val apiKey: String,
    val endpoint: String,
    val debugLogging: Boolean = false,
    val batchSize: Int = 20,
    val retryLimit: Int = 3
)

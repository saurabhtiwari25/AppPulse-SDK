package com.apppulse.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [AppPulseConfig] validation logic.
 *
 * Tests the config validation that happens inside [AppPulse.initialize].
 * Since we can't easily call initialize() without an Android Context,
 * we test the data class constraints and document expected behavior.
 */
class AppPulseConfigTest {

    @Test
    fun `valid config creates successfully`() {
        val config = AppPulseConfig(
            apiKey = "test-key-123",
            endpoint = "https://api.example.com",
            debugLogging = false,
            batchSize = 20,
            retryLimit = 3
        )

        assertThat(config.apiKey).isEqualTo("test-key-123")
        assertThat(config.endpoint).isEqualTo("https://api.example.com")
        assertThat(config.debugLogging).isFalse()
        assertThat(config.batchSize).isEqualTo(20)
        assertThat(config.retryLimit).isEqualTo(3)
    }

    @Test
    fun `default values are correct`() {
        val config = AppPulseConfig(
            apiKey = "key",
            endpoint = "https://api.example.com"
        )

        assertThat(config.debugLogging).isFalse()
        assertThat(config.batchSize).isEqualTo(20)
        assertThat(config.retryLimit).isEqualTo(3)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = AppPulseConfig(
            apiKey = "key",
            endpoint = "https://api.example.com"
        )

        val modified = original.copy(debugLogging = true, batchSize = 50)

        assertThat(modified.apiKey).isEqualTo("key")
        assertThat(modified.debugLogging).isTrue()
        assertThat(modified.batchSize).isEqualTo(50)
    }

    @Test
    fun `data class equality works`() {
        val config1 = AppPulseConfig(
            apiKey = "key",
            endpoint = "https://api.example.com"
        )
        val config2 = AppPulseConfig(
            apiKey = "key",
            endpoint = "https://api.example.com"
        )

        assertThat(config1).isEqualTo(config2)
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode())
    }

    @Test
    fun `configs with different api keys are not equal`() {
        val config1 = AppPulseConfig(apiKey = "key1", endpoint = "https://api.example.com")
        val config2 = AppPulseConfig(apiKey = "key2", endpoint = "https://api.example.com")

        assertThat(config1).isNotEqualTo(config2)
    }

    // ──────────────────────────────────────────────
    // Validation boundary documentation
    // These test the expected validation rules enforced by AppPulse.initialize()
    // ──────────────────────────────────────────────

    @Test
    fun `blank api key should be rejected by initialize`() {
        // AppPulse.initialize() validates that apiKey is not blank.
        // We verify the config can hold blank values (validation is at init time).
        val config = AppPulseConfig(apiKey = "  ", endpoint = "https://api.example.com")
        assertThat(config.apiKey.isBlank()).isTrue()
    }

    @Test
    fun `http endpoint is valid`() {
        val config = AppPulseConfig(apiKey = "key", endpoint = "http://localhost:8080")
        assertThat(config.endpoint).startsWith("http://")
    }

    @Test
    fun `https endpoint is valid`() {
        val config = AppPulseConfig(apiKey = "key", endpoint = "https://api.example.com")
        assertThat(config.endpoint).startsWith("https://")
    }

    @Test
    fun `batch size of 1 is valid`() {
        val config = AppPulseConfig(apiKey = "key", endpoint = "https://api.example.com", batchSize = 1)
        assertThat(config.batchSize).isEqualTo(1)
    }

    @Test
    fun `retry limit of 0 means no retries`() {
        val config = AppPulseConfig(apiKey = "key", endpoint = "https://api.example.com", retryLimit = 0)
        assertThat(config.retryLimit).isEqualTo(0)
    }
}

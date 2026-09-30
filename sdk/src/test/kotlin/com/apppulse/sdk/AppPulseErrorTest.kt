package com.apppulse.sdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the [AppPulseError] sealed class hierarchy.
 *
 * Verifies that each error subtype:
 * - Carries the correct message
 * - Preserves the underlying cause (when provided)
 * - Is distinguishable via type checks (sealed class exhaustiveness)
 */
class AppPulseErrorTest {

    @Test
    fun `ConfigurationError carries message`() {
        val error = AppPulseError.ConfigurationError("API key is blank")
        assertThat(error.message).isEqualTo("API key is blank")
        assertThat(error.cause).isNull()
    }

    @Test
    fun `InitializationError carries message`() {
        val error = AppPulseError.InitializationError("SDK not initialized")
        assertThat(error.message).isEqualTo("SDK not initialized")
    }

    @Test
    fun `ValidationError carries message`() {
        val error = AppPulseError.ValidationError("Event name is blank")
        assertThat(error.message).isEqualTo("Event name is blank")
    }

    @Test
    fun `NetworkError carries message and cause`() {
        val cause = java.io.IOException("Connection refused")
        val error = AppPulseError.NetworkError("Network error", cause)
        assertThat(error.message).isEqualTo("Network error")
        assertThat(error.cause).isEqualTo(cause)
    }

    @Test
    fun `ApiError carries HTTP code and message`() {
        val error = AppPulseError.ApiError(401, "Unauthorized")
        assertThat(error.statusCode).isEqualTo(401)
        assertThat(error.message).isEqualTo("Unauthorized")
    }

    @Test
    fun `TimeoutError carries message and cause`() {
        val cause = java.net.SocketTimeoutException("Read timed out")
        val error = AppPulseError.TimeoutError("Request timed out", cause)
        assertThat(error.message).isEqualTo("Request timed out")
        assertThat(error.cause).isEqualTo(cause)
    }

    @Test
    fun `StorageError carries message and cause`() {
        val cause = RuntimeException("Database locked")
        val error = AppPulseError.StorageError("Failed to save", cause)
        assertThat(error.message).isEqualTo("Failed to save")
        assertThat(error.cause).isEqualTo(cause)
    }

    @Test
    fun `sealed class exhaustive when expression compiles`() {
        val error: AppPulseError = AppPulseError.NetworkError("test", null)

        // This verifies that all sealed subtypes can be matched
        val description = when (error) {
            is AppPulseError.ConfigurationError -> "config"
            is AppPulseError.InitializationError -> "init"
            is AppPulseError.ValidationError -> "validation"
            is AppPulseError.NetworkError -> "network"
            is AppPulseError.ApiError -> "api"
            is AppPulseError.TimeoutError -> "timeout"
            is AppPulseError.StorageError -> "storage"
        }

        assertThat(description).isEqualTo("network")
    }
}

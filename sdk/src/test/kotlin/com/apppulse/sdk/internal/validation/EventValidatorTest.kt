package com.apppulse.sdk.internal.validation

import com.apppulse.sdk.AppPulseError
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [EventValidator].
 *
 * Pure JUnit — no Android dependencies needed.
 * Tests cover all validation rules documented in the validator.
 */
class EventValidatorTest {

    // ──────────────────────────────────────────────
    // Event Name Validation
    // ──────────────────────────────────────────────

    @Test
    fun `valid event name returns null`() {
        val result = EventValidator.validate("purchase", null)
        assertThat(result).isNull()
    }

    @Test
    fun `valid event name with underscores returns null`() {
        val result = EventValidator.validate("user_signed_up", null)
        assertThat(result).isNull()
    }

    @Test
    fun `valid event name with numbers returns null`() {
        val result = EventValidator.validate("step3_completed", null)
        assertThat(result).isNull()
    }

    @Test
    fun `blank event name returns validation error`() {
        val result = EventValidator.validate("   ", null)
        assertThat(result).isNotNull()
        assertThat(result).isInstanceOf(AppPulseError.ValidationError::class.java)
        assertThat(result!!.message).contains("blank")
    }

    @Test
    fun `empty event name returns validation error`() {
        val result = EventValidator.validate("", null)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("blank")
    }

    @Test
    fun `event name with spaces returns validation error`() {
        val result = EventValidator.validate("user signed up", null)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("alphanumeric")
    }

    @Test
    fun `event name with special characters returns validation error`() {
        val result = EventValidator.validate("purchase!", null)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("alphanumeric")
    }

    @Test
    fun `event name with hyphens returns validation error`() {
        val result = EventValidator.validate("user-login", null)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("alphanumeric")
    }

    @Test
    fun `event name exceeding max length returns validation error`() {
        val longName = "a".repeat(257)
        val result = EventValidator.validate(longName, null)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("256")
    }

    @Test
    fun `event name at max length is valid`() {
        val maxName = "a".repeat(256)
        val result = EventValidator.validate(maxName, null)
        assertThat(result).isNull()
    }

    // ──────────────────────────────────────────────
    // Properties Validation
    // ──────────────────────────────────────────────

    @Test
    fun `null properties are valid`() {
        val result = EventValidator.validate("test", null)
        assertThat(result).isNull()
    }

    @Test
    fun `empty properties map is valid`() {
        val result = EventValidator.validate("test", emptyMap())
        assertThat(result).isNull()
    }

    @Test
    fun `string property value is valid`() {
        val result = EventValidator.validate("test", mapOf("key" to "value"))
        assertThat(result).isNull()
    }

    @Test
    fun `integer property value is valid`() {
        val result = EventValidator.validate("test", mapOf("count" to 42))
        assertThat(result).isNull()
    }

    @Test
    fun `double property value is valid`() {
        val result = EventValidator.validate("test", mapOf("price" to 9.99))
        assertThat(result).isNull()
    }

    @Test
    fun `boolean property value is valid`() {
        val result = EventValidator.validate("test", mapOf("premium" to true))
        assertThat(result).isNull()
    }

    @Test
    fun `mixed valid property types are valid`() {
        val props = mapOf(
            "name" to "John",
            "age" to 30,
            "score" to 99.5,
            "active" to true
        )
        val result = EventValidator.validate("test", props)
        assertThat(result).isNull()
    }

    @Test
    fun `list property value returns validation error`() {
        val props = mapOf("tags" to listOf("a", "b") as Any)
        val result = EventValidator.validate("test", props)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("unsupported type")
    }

    @Test
    fun `map property value returns validation error`() {
        val props = mapOf("nested" to mapOf("key" to "value") as Any)
        val result = EventValidator.validate("test", props)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("unsupported type")
    }

    @Test
    fun `blank property key returns validation error`() {
        val props = mapOf("  " to "value")
        val result = EventValidator.validate("test", props)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("key must not be blank")
    }

    @Test
    fun `too many properties returns validation error`() {
        val props = (1..51).associate { "key_$it" to "value" }
        val result = EventValidator.validate("test", props)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("50")
    }

    @Test
    fun `50 properties is valid (at the limit)`() {
        val props = (1..50).associate { "key_$it" to "value" }
        val result = EventValidator.validate("test", props)
        assertThat(result).isNull()
    }

    @Test
    fun `oversized string value returns validation error`() {
        val bigValue = "x".repeat(1025) // 1025 bytes > 1024 limit
        val props = mapOf("data" to bigValue)
        val result = EventValidator.validate("test", props)
        assertThat(result).isNotNull()
        assertThat(result!!.message).contains("1024")
    }

    @Test
    fun `string value at max size is valid`() {
        val maxValue = "x".repeat(1024)
        val props = mapOf("data" to maxValue)
        val result = EventValidator.validate("test", props)
        assertThat(result).isNull()
    }
}

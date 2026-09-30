package com.apppulse.sdk.internal.validation

import com.apppulse.sdk.AppPulseError

/**
 * Validates event names and properties before they are persisted.
 *
 * Validation rules:
 * - Event name: non-blank, max 256 chars, alphanumeric + underscores only
 * - Properties: max 50 keys, values must be String/Number/Boolean, max 1KB per string value
 * - Blank property keys are rejected
 *
 * Returns null if valid, or a [AppPulseError.ValidationError] describing the problem.
 */
internal object EventValidator {

    private const val MAX_EVENT_NAME_LENGTH = 256
    private const val MAX_PROPERTY_KEYS = 50
    private const val MAX_PROPERTY_VALUE_BYTES = 1024 // 1 KB per string value
    private val EVENT_NAME_PATTERN = Regex("^[a-zA-Z0-9_]+$")

    /**
     * Validates an event name and optional properties map.
     *
     * @return null if valid, or a [AppPulseError.ValidationError] if invalid.
     */
    fun validate(name: String, properties: Map<String, Any>?): AppPulseError.ValidationError? {
        validateName(name)?.let { return it }
        properties?.let { validateProperties(it) }?.let { return it }
        return null
    }

    private fun validateName(name: String): AppPulseError.ValidationError? {
        if (name.isBlank()) {
            return AppPulseError.ValidationError("Event name must not be blank")
        }
        if (name.length > MAX_EVENT_NAME_LENGTH) {
            return AppPulseError.ValidationError(
                "Event name exceeds maximum length of $MAX_EVENT_NAME_LENGTH characters"
            )
        }
        if (!EVENT_NAME_PATTERN.matches(name)) {
            return AppPulseError.ValidationError(
                "Event name must contain only alphanumeric characters and underscores, got: '$name'"
            )
        }
        return null
    }

    private fun validateProperties(properties: Map<String, Any>): AppPulseError.ValidationError? {
        if (properties.size > MAX_PROPERTY_KEYS) {
            return AppPulseError.ValidationError(
                "Properties exceed maximum of $MAX_PROPERTY_KEYS keys (got ${properties.size})"
            )
        }

        for ((key, value) in properties) {
            if (key.isBlank()) {
                return AppPulseError.ValidationError("Property key must not be blank")
            }

            when (value) {
                is String -> {
                    if (value.toByteArray(Charsets.UTF_8).size > MAX_PROPERTY_VALUE_BYTES) {
                        return AppPulseError.ValidationError(
                            "Property '$key' string value exceeds $MAX_PROPERTY_VALUE_BYTES bytes"
                        )
                    }
                }
                is Number, is Boolean -> { /* allowed */ }
                else -> {
                    return AppPulseError.ValidationError(
                        "Property '$key' has unsupported type: ${value::class.simpleName}. " +
                            "Supported types: String, Number, Boolean"
                    )
                }
            }
        }
        return null
    }
}

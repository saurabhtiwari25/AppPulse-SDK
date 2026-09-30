package com.apppulse.sdk.internal.logging

import android.util.Log

/**
 * Internal configurable logger for the AppPulse SDK.
 *
 * All SDK logging goes through this singleton so we can:
 * - Respect the [isDebugEnabled] flag from [AppPulseConfig.debugLogging]
 * - Use a consistent tag ("AppPulse") across all log statements
 * - Ensure sensitive data is never logged
 *
 * **Security rules:**
 * - Never pass API keys, raw user IDs, or credentials to any log method.
 * - Use [maskValue] for any user-identifiable strings.
 */
internal object SdkLogger {

    private const val TAG = "AppPulse"

    /** Set from [AppPulseConfig.debugLogging] during initialization. */
    @Volatile
    var isDebugEnabled: Boolean = false

    /** Debug-level log — only emitted when [isDebugEnabled] is true. */
    fun d(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, message)
        }
    }

    /** Info-level log — always emitted (initialization, shutdown). */
    fun i(message: String) {
        Log.i(TAG, message)
    }

    /** Warning-level log — non-fatal issues like duplicate initialization. */
    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, message, throwable)
        } else {
            Log.w(TAG, message)
        }
    }

    /** Error-level log — failures that the SDK cannot recover from silently. */
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /**
     * Masks a value for safe logging.
     * Example: "user-12345" → "us***45"
     */
    fun maskValue(value: String): String {
        return if (value.length > 4) {
            "${value.take(2)}***${value.takeLast(2)}"
        } else {
            "***"
        }
    }
}

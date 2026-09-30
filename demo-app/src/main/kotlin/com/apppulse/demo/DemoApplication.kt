package com.apppulse.demo

import android.app.Application
import android.util.Log
import com.apppulse.sdk.AppPulse
import com.apppulse.sdk.AppPulseConfig

/**
 * Custom Application class that initializes the AppPulse SDK.
 *
 * This demonstrates the recommended initialization pattern:
 * - Initialize in Application.onCreate() so the SDK is ready before any Activity starts.
 * - Use applicationContext (the Application IS the applicationContext).
 * - Handle initialization failure gracefully.
 */
class DemoApplication : Application() {

    companion object {
        private const val TAG = "AppPulseDemo"
    }

    override fun onCreate() {
        super.onCreate()

        val config = AppPulseConfig(
            apiKey = "demo-api-key-12345",
            endpoint = "https://example.com/api/v1/",
            debugLogging = true,
            batchSize = 20,
            retryLimit = 3
        )

        val result = AppPulse.initialize(
            context = this,
            config = config
        )

        result.fold(
            onSuccess = {
                Log.i(TAG, "AppPulse SDK initialized successfully")
            },
            onFailure = { error ->
                Log.e(TAG, "AppPulse SDK initialization failed: ${error.message}")
            }
        )
    }
}

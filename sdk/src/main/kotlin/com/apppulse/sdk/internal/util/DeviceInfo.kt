package com.apppulse.sdk.internal.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Collects privacy-safe device metadata for analytics events.
 *
 * Only collects non-sensitive information:
 * - OS type and version
 * - Device manufacturer and model
 * - System locale
 *
 * Does NOT collect:
 * - Device IDs (IMEI, Android ID, etc.)
 * - Location
 * - Installed apps
 * - User accounts
 */
internal object DeviceInfo {

    /**
     * Collects device metadata as a string map.
     * Results are safe to log and transmit.
     */
    fun collect(context: Context): Map<String, String> {
        return mapOf(
            "os" to "Android",
            "os_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT.toString(),
            "device_manufacturer" to Build.MANUFACTURER,
            "device_model" to Build.MODEL,
            "locale" to Locale.getDefault().toString()
        )
    }

    /**
     * Returns the host application's version name, or null if unavailable.
     */
    fun getAppVersion(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

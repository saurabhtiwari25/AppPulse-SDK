package com.apppulse.demo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apppulse.sdk.AppPulse
import com.apppulse.sdk.AppPulseError
import com.apppulse.sdk.EventCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Main demo activity that exercises all public SDK APIs.
 *
 * Uses only the SDK's public API surface — no internal classes accessed.
 * Polls event counts every 2 seconds to keep the display current.
 */
class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var tvSdkStatus: TextView
    private lateinit var tvCurrentUser: TextView
    private lateinit var etUserId: TextInputEditText
    private lateinit var btnSetUser: MaterialButton
    private lateinit var btnClearUser: MaterialButton
    private lateinit var btnTrackLogin: MaterialButton
    private lateinit var btnTrackProductView: MaterialButton
    private lateinit var btnTrackPurchase: MaterialButton
    private lateinit var tvPendingEvents: TextView
    private lateinit var tvUploadedEvents: TextView
    private lateinit var tvFailedEvents: TextView
    private lateinit var btnForceSync: MaterialButton
    private lateinit var btnClearEvents: MaterialButton

    // Periodic refresh handler
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 2000L // 2 seconds

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshEventCounts()
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        updateSdkStatus()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun bindViews() {
        tvSdkStatus = findViewById(R.id.tvSdkStatus)
        tvCurrentUser = findViewById(R.id.tvCurrentUser)
        etUserId = findViewById(R.id.etUserId)
        btnSetUser = findViewById(R.id.btnSetUser)
        btnClearUser = findViewById(R.id.btnClearUser)
        btnTrackLogin = findViewById(R.id.btnTrackLogin)
        btnTrackProductView = findViewById(R.id.btnTrackProductView)
        btnTrackPurchase = findViewById(R.id.btnTrackPurchase)
        tvPendingEvents = findViewById(R.id.tvPendingEvents)
        tvUploadedEvents = findViewById(R.id.tvUploadedEvents)
        tvFailedEvents = findViewById(R.id.tvFailedEvents)
        btnForceSync = findViewById(R.id.btnForceSync)
        btnClearEvents = findViewById(R.id.btnClearEvents)
    }

    private fun updateSdkStatus() {
        tvSdkStatus.text = if (AppPulse.isInitialized) {
            "SDK Status: ✅ Initialized"
        } else {
            "SDK Status: ❌ Not Initialized"
        }
    }

    private fun updateUserDisplay(userId: String?) {
        tvCurrentUser.text = if (userId != null) {
            "Current User: $userId"
        } else {
            "Current User: None"
        }
    }

    private fun refreshEventCounts() {
        if (!AppPulse.isInitialized) return

        AppPulse.getEventCounts { counts ->
            tvPendingEvents.text = "Pending Events: ${counts.pending}"
            tvUploadedEvents.text = "Uploaded Events: ${counts.uploaded}"
            tvFailedEvents.text = "Failed Events: ${counts.failed}"
        }
    }

    private fun setupListeners() {
        btnSetUser.setOnClickListener {
            val userId = etUserId.text?.toString()?.trim()
            if (userId.isNullOrBlank()) {
                Toast.makeText(this, "Enter a user ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPulse.setUserId(userId)
            updateUserDisplay(userId)
            Toast.makeText(this, "User set: $userId", Toast.LENGTH_SHORT).show()
        }

        btnClearUser.setOnClickListener {
            AppPulse.clearUser()
            updateUserDisplay(null)
            Toast.makeText(this, "User cleared", Toast.LENGTH_SHORT).show()
        }

        btnTrackLogin.setOnClickListener {
            trackEvent("login")
        }

        btnTrackProductView.setOnClickListener {
            trackEvent(
                "product_view",
                mapOf(
                    "product_id" to "SKU-001",
                    "category" to "electronics"
                )
            )
        }

        btnTrackPurchase.setOnClickListener {
            trackEvent(
                "purchase",
                mapOf(
                    "product_id" to "SKU-001",
                    "price" to 499,
                    "currency" to "USD"
                )
            )
        }

        // Force Sync button — triggers WorkManager one-shot sync
        btnForceSync.isEnabled = true
        btnForceSync.setOnClickListener {
            AppPulse.forceSync()
            Toast.makeText(this, "⚡ Sync triggered", Toast.LENGTH_SHORT).show()
        }

        // Clear Events button
        btnClearEvents.isEnabled = true
        btnClearEvents.setOnClickListener {
            AppPulse.deleteAllEvents()
            Toast.makeText(this, "Events cleared", Toast.LENGTH_SHORT).show()
            refreshEventCounts()
        }
    }

    /**
     * Tracks an event using the SDK's public API and shows the result.
     */
    private fun trackEvent(name: String, properties: Map<String, Any>? = null) {
        AppPulse.trackEvent(
            name = name,
            properties = properties,
            callback = object : EventCallback {
                override fun onSuccess() {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Event queued: $name",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshEventCounts()
                }

                override fun onFailure(error: AppPulseError) {
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Event failed: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

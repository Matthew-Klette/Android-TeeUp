package com.teeup.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.teeup.android.data.ApiException
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Profile → Offline Data & Sync Status. There's no offline cache/queue in this app (see
 * SyncStatus doc comment), so this reports what's real — last successful sync and current
 * connectivity — rather than a fabricated "N rounds pending" counter.
 */
class OfflineSyncStatusActivity : LocaleActivity() {
    private lateinit var lastSyncedText: TextView
    private lateinit var connectionText: TextView
    private lateinit var checkNowButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_sync_status)

        lastSyncedText = findViewById(R.id.text_last_synced)
        connectionText = findViewById(R.id.text_connection)
        checkNowButton = findViewById(R.id.button_check_now)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        checkNowButton.setOnClickListener { onCheckNowClicked() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val lastSyncMillis = SyncStatus.lastSyncMillisOrNull(this)
        lastSyncedText.text = if (lastSyncMillis != null) {
            SyncStatus.formatRelative(this, lastSyncMillis)
        } else {
            getString(R.string.offline_sync_never)
        }
        connectionText.text = describeConnection()
    }

    private fun describeConnection(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return getString(R.string.offline_sync_no_connection)
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return getString(R.string.offline_sync_no_connection)

        return when {
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> getString(R.string.offline_sync_no_connection)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.offline_sync_connected_wifi)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.offline_sync_connected_mobile)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getString(R.string.offline_sync_connected_ethernet)
            else -> getString(R.string.offline_sync_connected_generic)
        }
    }

    private fun onCheckNowClicked() {
        checkNowButton.isEnabled = false
        checkNowButton.setText(R.string.offline_sync_checking)

        Thread {
            try {
                // A real, lightweight call against the live API — this is what "reachable" means here.
                TeeUpApiClient.fetchCourses()
                SyncStatus.recordSuccess(this)
                runOnUiThread {
                    checkNowButton.isEnabled = true
                    checkNowButton.setText(R.string.offline_sync_check_now)
                    refreshStatus()
                    TeeUpBanner.show(this, getString(R.string.offline_sync_reachable))
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    checkNowButton.isEnabled = true
                    checkNowButton.setText(R.string.offline_sync_check_now)
                    refreshStatus()
                    TeeUpBanner.show(this, e.message ?: getString(R.string.offline_sync_request_failed_fallback), isError = true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    checkNowButton.isEnabled = true
                    checkNowButton.setText(R.string.offline_sync_check_now)
                    refreshStatus()
                    TeeUpBanner.show(this, getString(R.string.offline_sync_unreachable), isError = true)
                }
            }
        }.start()
    }
}

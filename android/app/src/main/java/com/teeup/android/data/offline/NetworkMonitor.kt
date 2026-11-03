package com.teeup.android.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide online/offline state from ConnectivityManager's default-network callback
 * (Android Developers, 2026b). Screens collect [isOnline] to show the "You're offline" banner,
 * and the Scorecard checks it before trying to upload. Started once from TeeUpApplication.
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _isOnline.value = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        Log.i(TAG, "Initial connectivity: online=${_isOnline.value}")
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) = update(false)
        })
    }

    private fun update(online: Boolean) {
        if (_isOnline.value == online) return
        _isOnline.value = online
        Log.i(TAG, if (online) "Connection restored" else "Connection lost")
    }
}

/*
References:

Android Developers (2026b). Monitor connectivity status and connection metering. [online] Available at: <https://developer.android.com/training/basics/network-ops/reading-network-state> [Accessed 3 Nov. 2026].
*/

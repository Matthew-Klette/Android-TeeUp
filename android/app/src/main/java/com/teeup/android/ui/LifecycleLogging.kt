package com.teeup.android.ui

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Logs every Activity lifecycle transition (created → started → resumed → paused → stopped →
 * destroyed) under the screen's class name, so execution flow and state changes can be traced in
 * Logcat by filtering on "Lifecycle" (Android Developers, 2026e). Attached by both screen base
 * classes, so every Activity in the app reports its lifecycle.
 */
class LifecycleLogging(private val screen: String) : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) = log("onCreate")
    override fun onStart(owner: LifecycleOwner) = log("onStart")
    override fun onResume(owner: LifecycleOwner) = log("onResume")
    override fun onPause(owner: LifecycleOwner) = log("onPause")
    override fun onStop(owner: LifecycleOwner) = log("onStop")
    override fun onDestroy(owner: LifecycleOwner) = log("onDestroy")

    private fun log(event: String) {
        Log.d(TAG, "$screen.$event")
    }

    private companion object {
        const val TAG = "Lifecycle"
    }
}

/*
References:

Android Developers (2026e). The activity lifecycle. [online] Available at: <https://developer.android.com/guide/components/activities/activity-lifecycle> [Accessed 3 Nov. 2026].
*/

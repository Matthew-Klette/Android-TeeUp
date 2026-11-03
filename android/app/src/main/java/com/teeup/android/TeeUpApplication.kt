package com.teeup.android

import android.app.Application
import android.util.Log
import com.teeup.android.data.offline.NetworkMonitor
import com.teeup.android.data.offline.ScorecardOutbox
import com.teeup.android.notifications.PushNotifications

/** Holds an app-wide Context so singletons like DevIdentity can reach SharedPreferences
 *  without every call site threading one through. Also starts the app-wide services: the
 *  connectivity monitor (offline mode), the notification channels (FCM push) and a sync pass
 *  for any scorecards still queued from a previous session. */
class TeeUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        Log.i(TAG, "Application created (version ${BuildConfig.VERSION_NAME})")
        NetworkMonitor.start(this)
        PushNotifications.createChannels(this)
        // WorkManager persists queued work across restarts on its own; this just makes sure a
        // round queued by an older build (or before a crash) is picked up too. Cheap: it only
        // runs once a connection exists, and does nothing with an empty queue.
        ScorecardOutbox.scheduleSync(this)
    }

    companion object {
        private const val TAG = "TeeUpApplication"

        lateinit var appContext: Application
            private set

        /** Same as [appContext], but null instead of crashing when unset — real app usage
         *  always has it set (Application.onCreate() runs before any Activity), but plain
         *  JVM unit tests (Formatting/InputValidation/RoundModels) never create the
         *  Application at all, so callers there need a way to fall back gracefully. */
        val appContextOrNull: Application?
            get() = if (::appContext.isInitialized) appContext else null
    }
}

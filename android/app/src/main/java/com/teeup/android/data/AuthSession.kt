package com.teeup.android.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.SignInActivity
import com.teeup.android.data.offline.ResponseCache
import com.teeup.android.notifications.DeviceTokenRegistrar
import kotlin.concurrent.thread

/** The only place in the app that clears the Firebase session and the cached
 *  backend identity. Used by both PrivacyDataActivity and ProfileActivity. */
object AuthSession {
    private const val TAG = "AuthSession"

    fun signOut(context: Context) {
        val appContext = context.applicationContext
        // Stop pushes to this phone first (needs the session to still exist), then sign out.
        DeviceTokenRegistrar.unregister {
            FirebaseAuth.getInstance().signOut()
            LocalIdentity.clearCache(appContext)
            com.teeup.android.notifications.TeeTimeReminders.cancelAll(appContext)
            // Room disallows main-thread queries. Queued scorecards are kept: they're filed under
            // their owner and upload when that golfer signs back in.
            thread(name = "clear-offline-cache") { ResponseCache.clear() }
            Log.i(TAG, "Signed out")

            val intent = Intent(appContext, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            appContext.startActivity(intent)
        }
    }
}

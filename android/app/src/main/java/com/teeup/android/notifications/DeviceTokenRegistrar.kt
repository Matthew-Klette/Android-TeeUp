package com.teeup.android.notifications

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Stores this phone's FCM registration token in Firebase Realtime Database, the app's NoSQL
 * store, at deviceTokens/{firebaseUid}/{token} = server timestamp (Firebase, 2026b). The API
 * reads that node to address pushes, and its security rules (firebase/database.rules.json)
 * let each signed-in user write only their own tokens.
 */
object DeviceTokenRegistrar {
    private const val TAG = "DeviceTokenRegistrar"
    private const val NODE = "deviceTokens"

    /** Fetches the current token and registers it; call once a user is signed in. */
    fun syncCurrentToken() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::register)
            .addOnFailureListener { Log.w(TAG, "Couldn't get an FCM token", it) }
    }

    fun register(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.d(TAG, "No signed-in user; token will be registered after sign-in")
            return
        }
        FirebaseDatabase.getInstance().reference.child(NODE).child(uid).child(token)
            .setValue(ServerValue.TIMESTAMP)
            .addOnSuccessListener { Log.i(TAG, "Device token registered for push") }
            .addOnFailureListener { Log.w(TAG, "Device token write failed (check RTDB rules)", it) }
    }

    /** Removes this phone's token before sign-out, so the next account's pushes don't come here.
     *  [onDone] always runs, even if the delete fails, so sign-out is never blocked. */
    fun unregister(onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone()
        // Offline, an RTDB delete only completes once the connection returns, so sign-out waits
        // at most UNREGISTER_TIMEOUT_MS; the queued delete still goes through later.
        val finished = AtomicBoolean(false)
        val finishOnce = { if (finished.compareAndSet(false, true)) onDone() }
        Handler(Looper.getMainLooper()).postDelayed(finishOnce, UNREGISTER_TIMEOUT_MS)
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseDatabase.getInstance().reference.child(NODE).child(uid).child(token).removeValue()
                    .addOnCompleteListener {
                        Log.i(TAG, "Device token removed: ${it.isSuccessful}")
                        finishOnce()
                    }
            }
            .addOnFailureListener { finishOnce() }
    }

    private const val UNREGISTER_TIMEOUT_MS = 2_000L
}

/*
References:

Firebase (2026b). Read and write data on Android. [online] Available at: <https://firebase.google.com/docs/database/android/read-and-write> [Accessed 3 Nov. 2026].
*/

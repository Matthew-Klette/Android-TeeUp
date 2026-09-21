package com.teeup.android.data

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.SignInActivity

/** The only place in the app that clears the Firebase session and the cached
 *  backend identity. Used by both PrivacyDataActivity and ProfileActivity. */
object AuthSession {
    fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        LocalIdentity.clearCache(context)

        val intent = Intent(context, SignInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}

package com.teeup.android.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Registers the signed-in Firebase user (EME-295's Google SSO) against
 * POST /api/auth/register (idempotent server-side — see AuthService.RegisterAsync)
 * and caches the backend user id.
 */
object LocalIdentity {
    private const val PREFS = "teeup_local_identity"
    private const val KEY_USER_ID = "user_id"

    /** Non-blocking: returns the cached backend user id, or null if [ensureRegistered] hasn't run yet. */
    fun cachedUserIdOrNull(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)

    /** Blocks on network calls the first time; must be called off the main thread, after a Firebase sign-in. */
    fun ensureRegistered(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("ensureRegistered called with no signed-in Firebase user")

        val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
        prefs.edit().putString(KEY_USER_ID, user.id).apply()
        return user.id
    }
}

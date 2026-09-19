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

    data class BackendIdentity(val userId: String, val profileComplete: Boolean)

    /** Non-blocking: returns the cached backend user id, or null if [ensureRegistered] hasn't run yet. */
    fun cachedUserIdOrNull(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)

    /** Blocks on network calls the first time; must be called off the main thread, after a Firebase sign-in. */
    fun ensureRegistered(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }
        return register(context).userId
    }

    /**
     * Always hits the network (registration is idempotent server-side), so the
     * caller gets an up-to-date [BackendIdentity.profileComplete] right after a
     * sign-in — unlike [ensureRegistered], which may return a stale local cache.
     */
    fun registerFresh(context: Context): BackendIdentity = register(context)

    private fun register(context: Context): BackendIdentity {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("register called with no signed-in Firebase user")

        val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USER_ID, user.id).apply()
        return BackendIdentity(user.id, user.profileComplete)
    }
}

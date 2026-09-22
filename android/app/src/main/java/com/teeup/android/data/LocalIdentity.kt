package com.teeup.android.data

import android.content.Context
import com.teeup.android.BuildConfig
import com.google.firebase.auth.FirebaseAuth

/**
 * Registers the signed-in Firebase user against POST /api/auth/register
 * (idempotent server-side) and caches the backend user id. Falls back to
 * [DevIdentity] in debug builds when there's no real Firebase user, using
 * the same endpoint but keyed by the dev id instead.
 */
object LocalIdentity {
    private const val PREFS = "teeup_local_identity"
    private const val KEY_USER_ID = "user_id"

    data class BackendIdentity(val userId: String, val profileComplete: Boolean)

    /** Non-blocking: returns the cached backend user id, or null if [ensureRegistered] hasn't run yet. */
    fun cachedUserIdOrNull(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)

    /** Called on sign-out (AuthSession) so the next sign-in doesn't reuse a stale cached id. */
    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_USER_ID).apply()
    }

    /** Blocks on network calls the first time; must be called off the main thread, after a Firebase sign-in. */
    fun ensureRegistered(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }
        return register(context).userId
    }

    /**
     * Always hits the network, so the caller gets an up-to-date
     * [BackendIdentity.profileComplete] right after sign-in. Unlike
     * [ensureRegistered], this never returns a stale local cache.
     */
    fun registerFresh(context: Context): BackendIdentity = register(context)

    private fun register(context: Context): BackendIdentity {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val (uid, displayName) = when {
            firebaseUser != null -> firebaseUser.uid to (firebaseUser.displayName ?: "TeeUp Golfer")
            BuildConfig.DEBUG -> DevIdentity.deviceId to "TeeUp Golfer (Dev)"
            else -> throw IllegalStateException("register called with no signed-in Firebase user")
        }

        val user = TeeUpApiClient.register(uid, displayName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_USER_ID, user.id).apply()
        return BackendIdentity(user.id, user.profileComplete)
    }
}

package com.teeup.android.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth

/**
 * Stand-in for the real signed-in user until EME-295's Google SSO flow lands.
 * Signs in anonymously with Firebase — a genuine Firebase-issued identity and
 * ID token, not a fake client-generated UUID — then registers that UID against
 * POST /api/auth/register (idempotent server-side — see AuthService.RegisterAsync)
 * and caches the backend user id. Anonymous auth is what lets EME-291's JWT
 * bearer middleware validate a real token for these calls today, instead of
 * every request going out unauthenticated and 401ing.
 *
 * Replace this whole file with the real authenticated user id once EME-295
 * lands — nothing downstream should need to change beyond where the Firebase
 * user comes from.
 */
object LocalIdentity {
    private const val PREFS = "teeup_local_identity"
    private const val KEY_USER_ID = "user_id"

    /** Non-blocking: returns the cached backend user id, or null if [ensureRegistered] hasn't run yet. */
    fun cachedUserIdOrNull(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)

    /** Blocks on network calls the first time; must be called off the main thread. */
    fun ensureRegistered(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }

        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: Tasks.await(FirebaseAuth.getInstance().signInAnonymously()).user
            ?: throw IllegalStateException("Firebase anonymous sign-in returned no user")

        val user = TeeUpApiClient.register(firebaseUser.uid, "Guest")
        prefs.edit().putString(KEY_USER_ID, user.id).apply()
        return user.id
    }
}

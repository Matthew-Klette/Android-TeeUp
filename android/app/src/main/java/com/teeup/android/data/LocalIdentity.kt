package com.teeup.android.data

import android.content.Context
import java.util.UUID

/**
 * Stand-in for the real signed-in user until Firebase sign-in + JWT
 * verification land (EME-295/296/291). Generates a random UUID once per
 * device install, registers it against POST /api/auth/register (idempotent
 * server-side — see AuthService.RegisterAsync), and caches the backend
 * user id. This is what lets EME-298's join-request flow genuinely round
 * -trip through the real API/DB today instead of using a fake user id
 * that would fail the FK constraint on JoinRequest.GuestUserId.
 *
 * Replace this whole file with the real authenticated user id once
 * Firebase auth is wired in — nothing downstream should need to change
 * beyond where the user id comes from.
 */
object LocalIdentity {
    private const val PREFS = "teeup_local_identity"
    private const val KEY_FIREBASE_UID = "firebase_uid"
    private const val KEY_USER_ID = "user_id"

    /** Non-blocking: returns the cached backend user id, or null if [ensureRegistered] hasn't run yet. */
    fun cachedUserIdOrNull(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)

    /** Blocks on a network call the first time; must be called off the main thread. */
    fun ensureRegistered(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }

        val firebaseUid = prefs.getString(KEY_FIREBASE_UID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_FIREBASE_UID, it).apply()
        }

        val user = TeeUpApiClient.register(firebaseUid, "Guest")
        prefs.edit().putString(KEY_USER_ID, user.id).apply()
        return user.id
    }
}

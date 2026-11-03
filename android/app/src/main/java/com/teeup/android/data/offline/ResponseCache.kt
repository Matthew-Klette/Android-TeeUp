package com.teeup.android.data.offline

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.TeeUpApplication

/**
 * Read-through cache for TeeUpApiClient's GET requests. Every successful GET is saved to Room;
 * when the network is unreachable the last saved body is served instead, so Home, Rounds, the
 * Scorecard and Profile keep working offline. Blocking calls: TeeUpApiClient only ever runs off
 * the main thread, which is also what Room requires.
 */
object ResponseCache {
    private const val TAG = "ResponseCache"

    private fun dao() = TeeUpApplication.appContextOrNull?.let { TeeUpDatabase.get(it).cachedResponses() }

    /** Null when nobody is signed in: there's no owner to file the response under. */
    private fun keyFor(path: String): String? =
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid -> "$uid|$path" }

    fun store(path: String, body: String) {
        val key = keyFor(path) ?: return
        try {
            dao()?.upsert(CachedResponseEntity(key, body, System.currentTimeMillis()))
        } catch (e: Exception) {
            // A full disk or locked database must never fail the live request that just worked.
            Log.w(TAG, "Couldn't cache $path", e)
        }
    }

    /** The saved body for [path], or null when it was never loaded online on this device. */
    fun load(path: String): CachedResponseEntity? {
        val key = keyFor(path) ?: return null
        return try {
            dao()?.find(key)?.also { Log.i(TAG, "Offline: serving cached $path from ${it.cachedAtMillis}") }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read cached $path", e)
            null
        }
    }

    /** Called on sign-out so the next account starts clean. */
    fun clear() {
        try {
            dao()?.clear()
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear the cache", e)
        }
    }
}

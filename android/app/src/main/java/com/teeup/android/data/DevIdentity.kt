package com.teeup.android.data

import android.content.Context
import com.teeup.android.TeeUpApplication
import java.util.UUID

/**
 * Dev-only fallback identity for a backend running with DevAuth:Enabled (see the
 * API's Program.cs) — a stable per-install fake id, sent as X-Dev-User-Id whenever
 * there's no real Firebase session (see TeeUpApiClient, LocalIdentity). Exists
 * because this repo's google-services.json is per-developer/gitignored, and a
 * fresh clone without a real one can't complete any real Firebase Auth flow.
 * Only ever read/attached from debug builds — see BuildConfig.DEBUG checks at
 * every call site — so it's a no-op in a release build regardless.
 */
object DevIdentity {
    private const val PREFS = "teeup_dev_identity"
    private const val KEY_DEV_USER_ID = "dev_user_id"

    val deviceId: String by lazy {
        val prefs = TeeUpApplication.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEV_USER_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEV_USER_ID, generated).apply()
        }
    }
}

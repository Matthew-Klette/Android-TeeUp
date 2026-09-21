package com.teeup.android.data

import android.content.Context
import com.teeup.android.R

/**
 * Tracks the last time any real API call succeeded. There's no offline cache/queue in
 * this app yet (every screen is a live network call — see HomeActivity/TeeTimeDetailActivity),
 * so this is genuinely all there is to report; OfflineSyncStatusActivity shows this rather
 * than inventing fake queued-rounds data.
 */
object SyncStatus {
    private const val PREFS = "teeup_sync_status"
    private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"

    fun recordSuccess(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNC_MILLIS, System.currentTimeMillis())
            .apply()
    }

    fun lastSyncMillisOrNull(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_LAST_SYNC_MILLIS)) prefs.getLong(KEY_LAST_SYNC_MILLIS, 0L) else null
    }

    /** "Just now" / "5 minute(s) ago" / "3 hour(s) ago" / "2 day(s) ago". Plain arithmetic on
     *  purpose — same reasoning as Formatting.kt's substring date parsing: no java.time
     *  without desugaring at minSdk 24, and this doesn't need timezone-aware formatting. */
    fun formatRelative(context: Context, millis: Long): String {
        val diffSeconds = (System.currentTimeMillis() - millis) / 1000
        return when {
            diffSeconds < 60 -> context.getString(R.string.sync_relative_just_now)
            diffSeconds < 3600 -> context.getString(R.string.sync_relative_minutes_ago, diffSeconds / 60)
            diffSeconds < 86400 -> context.getString(R.string.sync_relative_hours_ago, diffSeconds / 3600)
            else -> context.getString(R.string.sync_relative_days_ago, diffSeconds / 86400)
        }
    }
}

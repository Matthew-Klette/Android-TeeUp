package com.teeup.android.data

import android.content.Context
import com.teeup.android.R

/**
 * Tracks the last time a real API call (or an offline-queue upload) succeeded, for the Offline
 * Data & Sync Status screen. The offline data itself lives in Room (see data/offline).
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

    /** "Just now" / "1 minute ago" / "5 minutes ago" / "3 hours ago" / "2 days ago" (plurals, so
     *  each language gets its own singular). Plain arithmetic on
     *  purpose — same reasoning as Formatting.kt's substring date parsing: no java.time
     *  without desugaring at minSdk 24, and this doesn't need timezone-aware formatting. */
    fun formatRelative(context: Context, millis: Long): String {
        val diffSeconds = (System.currentTimeMillis() - millis) / 1000
        return when {
            diffSeconds < 60 -> context.getString(R.string.sync_relative_just_now)
            diffSeconds < 3600 -> (diffSeconds / 60).toInt().let { context.resources.getQuantityString(R.plurals.sync_relative_minutes_ago, it, it) }
            diffSeconds < 86400 -> (diffSeconds / 3600).toInt().let { context.resources.getQuantityString(R.plurals.sync_relative_hours_ago, it, it) }
            else -> (diffSeconds / 86400).toInt().let { context.resources.getQuantityString(R.plurals.sync_relative_days_ago, it, it) }
        }
    }
}

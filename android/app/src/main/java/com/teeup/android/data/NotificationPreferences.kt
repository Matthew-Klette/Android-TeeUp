package com.teeup.android.data

import android.content.Context
import com.teeup.android.R

/**
 * Mirrors the API's NotificationType enum (api/TeeUp.Api/Models/Enums.cs), in the same
 * ordinal order, so a raw AppNotification.type int can be labeled in the UI without a
 * separate lookup table.
 */
enum class NotificationCategory(val labelRes: Int) {
    JOIN_REQUEST_RECEIVED(R.string.notification_category_join_request),
    REQUEST_ACCEPTED(R.string.notification_category_accepted),
    REQUEST_DECLINED(R.string.notification_category_declined),
    TEE_TIME_REMINDER(R.string.notification_category_reminder),
    SYNC_PENDING(R.string.notification_category_sync),
    WEATHER_ALERT(R.string.notification_category_weather);

    companion object {
        fun fromType(type: Int): NotificationCategory? = entries.getOrNull(type)
    }
}

/**
 * Five of these six categories are now real, server-persisted preferences, read and written
 * by NotificationPreferencesActivity via TeeUpApiClient. Only SYNC_PENDING stays here in
 * SharedPreferences, since offline-sync reminders are a device-local concept the backend has
 * no field for.
 */
object NotificationPreferences {
    private const val PREFS = "teeup_notification_prefs"
    private const val KEY_SYNC_PENDING = "sync_pending"

    /** Opt-out model: on until the user turns it off. */
    fun isSyncPendingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_PENDING, true)

    fun setSyncPendingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_PENDING, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

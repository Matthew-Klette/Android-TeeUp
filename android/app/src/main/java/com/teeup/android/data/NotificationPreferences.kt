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
    WEATHER_ALERT(R.string.notification_category_weather),
    TEE_TIME_CANCELLED(R.string.notification_category_cancelled);

    companion object {
        fun fromType(type: Int): NotificationCategory? = entries.getOrNull(type)
    }
}

/**
 * EME-318: five of these six categories are real, persisted preferences now, not on-device
 * only — PATCH /api/profiles/me's `JoinRequestNotifications` field covers JOIN_REQUEST_RECEIVED,
 * REQUEST_ACCEPTED and REQUEST_DECLINED as a single toggle (the backend only has one field for
 * all three; splitting them into their own fields would need a migration this ticket didn't
 * call for), `TeeTimeReminders` covers TEE_TIME_REMINDER, and `WeatherAlerts` covers
 * WEATHER_ALERT. NotificationPreferencesActivity reads/writes those three straight through
 * TeeUpApiClient.fetchProfile()/updateNotificationPreference() — this object doesn't hold them.
 *
 * SYNC_PENDING is the one category with no backing field on User (api/TeeUp.Api/Models/User.cs):
 * offline-sync reminders are a device-local concept the server has no notion of, so it alone
 * still lives here, in SharedPreferences.
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

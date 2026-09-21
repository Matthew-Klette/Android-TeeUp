package com.teeup.android.data

import android.content.Context
import com.teeup.android.R

/**
 * On-device only — there's no user-preference storage for this on the backend
 * (NotificationsController just returns every NotificationDto for the user), so
 * these categories filter what NotificationsActivity shows locally. Matches the
 * real NotificationType enum (api/TeeUp.Api/Models/Enums.cs) rather than inventing
 * categories that don't correspond to anything the backend can actually send.
 */
enum class NotificationCategory(val key: String, val labelRes: Int) {
    JOIN_REQUEST_RECEIVED("join_request_received", R.string.notification_category_join_request),
    REQUEST_ACCEPTED("request_accepted", R.string.notification_category_accepted),
    REQUEST_DECLINED("request_declined", R.string.notification_category_declined),
    TEE_TIME_REMINDER("tee_time_reminder", R.string.notification_category_reminder),
    SYNC_PENDING("sync_pending", R.string.notification_category_sync),
    WEATHER_ALERT("weather_alert", R.string.notification_category_weather)
}

object NotificationPreferences {
    private const val PREFS = "teeup_notification_prefs"

    /** Opt-out model: everything is on until the user turns it off. */
    fun isEnabled(context: Context, category: NotificationCategory): Boolean =
        prefs(context).getBoolean(category.key, true)

    fun setEnabled(context: Context, category: NotificationCategory, enabled: Boolean) {
        prefs(context).edit().putBoolean(category.key, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package com.teeup.android

import android.os.Bundle
import android.view.View
import com.teeup.android.data.NotificationCategory
import com.teeup.android.data.NotificationPreferences
import com.teeup.android.ui.LocaleActivity

/**
 * Screen 6 · Notifications. Reached from Home's bell icon; maps to GET /api/notifications
 * (sample data for now, see notifications_preview_notice). The four sample rows are each
 * gated by NotificationPreferences (set on Profile → Notification Preferences) so that
 * screen actually does something rather than being an inert settings page.
 */
class NotificationsActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        applyNotificationPreferences()
    }

    private fun applyNotificationPreferences() {
        fun setVisible(rowId: Int, category: NotificationCategory) {
            findViewById<View>(rowId).visibility =
                if (NotificationPreferences.isEnabled(this, category)) View.VISIBLE else View.GONE
        }
        setVisible(R.id.row_notification_join, NotificationCategory.JOIN_REQUEST_RECEIVED)
        setVisible(R.id.row_notification_accepted, NotificationCategory.REQUEST_ACCEPTED)
        setVisible(R.id.row_notification_sync, NotificationCategory.SYNC_PENDING)
        setVisible(R.id.row_notification_weather, NotificationCategory.WEATHER_ALERT)
    }
}

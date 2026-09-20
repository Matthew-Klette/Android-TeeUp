package com.teeup.android

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.ApiException
import com.teeup.android.data.AppNotification
import com.teeup.android.data.NotificationType
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime

/** Screen 6 · Notifications. Reached from Home's bell icon; maps to GET /api/notifications. */
class NotificationsActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        statusText = findViewById(R.id.text_status)
        container = findViewById(R.id.notifications_container)

        findViewById<View>(R.id.button_back).setOnClickListener { finish() }

        loadNotifications()
    }

    private fun loadNotifications() {
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.notifications_loading)
        container.removeAllViews()

        Thread {
            try {
                val notifications = TeeUpApiClient.fetchNotifications()
                runOnUiThread { render(notifications) }
            } catch (e: ApiException) {
                runOnUiThread { showError(e.message ?: "Couldn't load notifications") }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.notifications_load_failed)) }
            }
        }.start()
    }

    private fun render(notifications: List<AppNotification>) {
        if (notifications.isEmpty()) {
            statusText.visibility = View.VISIBLE
            statusText.setText(R.string.notifications_empty)
            return
        }

        statusText.visibility = View.GONE
        container.removeAllViews()
        notifications.forEach { container.addView(buildNotificationCard(it)) }
    }

    private fun showError(message: String) {
        container.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun buildNotificationCard(notification: AppNotification): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = space(R.dimen.space_sm) }

            setBackgroundResource(if (notification.isRead) R.drawable.bg_card else R.drawable.bg_card_outline)
            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)
        }

        card.addView(TextView(this).apply {
            text = titleFor(notification.type)
            setTextColor(colorOf(if (notification.isRead) R.color.teeup_text_primary else R.color.teeup_accent))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = notification.message
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body))
            setPadding(0, space(R.dimen.space_xs), 0, 0)
        })

        card.addView(TextView(this).apply {
            text = formatTeeTime(notification.createdAt)
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_caption))
            setPadding(0, space(R.dimen.space_xs), 0, 0)
        })

        return card
    }

    private fun titleFor(type: Int): String = when (type) {
        NotificationType.JOIN_REQUEST_RECEIVED -> "New Join Request"
        NotificationType.REQUEST_ACCEPTED -> "Request Accepted"
        NotificationType.REQUEST_DECLINED -> "Request Declined"
        NotificationType.TEE_TIME_REMINDER -> "Tee Time Reminder"
        NotificationType.SYNC_PENDING -> "Sync Reminder"
        NotificationType.WEATHER_ALERT -> "Weather Alert"
        else -> "Notification"
    }

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)

    private fun space(dimensionRes: Int): Int = resources.getDimensionPixelSize(dimensionRes)
}

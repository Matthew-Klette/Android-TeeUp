package com.teeup.android

import android.app.Activity
import android.content.Intent
import com.teeup.android.ui.runWhenActive
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.teeup.android.nav.NotificationDestination
import com.teeup.android.nav.notificationDestination
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
        findViewById<View>(R.id.qa_retry).setOnClickListener { loadNotifications() }

        loadNotifications()
    }

    private fun loadNotifications() {
        findViewById<View>(R.id.qa_retry).visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.notifications_loading)
        container.removeAllViews()

        Thread {
            try {
                val notifications = TeeUpApiClient.fetchNotifications()
                runWhenActive { render(notifications) }
            } catch (e: ApiException) {
                runWhenActive { showError(e.message ?: "Couldn't load notifications") }
            } catch (e: Exception) {
                runWhenActive { showError(getString(R.string.notifications_load_failed)) }
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
        findViewById<View>(R.id.qa_retry).visibility = View.VISIBLE
        container.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun buildNotificationCard(notification: AppNotification): View {
        val card = LinearLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "${titleFor(notification.type)}. ${notification.message}"
            setOnClickListener { openNotification(notification) }
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

    private fun openNotification(notification: AppNotification) {
        when (notificationDestination(notification.type, notification.relatedEntityId)) {
            NotificationDestination.TEE_TIME -> startActivity(
                Intent(this, TeeTimeDetailActivity::class.java)
                    .putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, notification.relatedEntityId))
            NotificationDestination.ROUNDS -> {
                Toast.makeText(this, R.string.notification_round_missing, Toast.LENGTH_LONG).show()
                openTab(RoundsActivity::class.java)
            }
            NotificationDestination.SCORECARD -> openTab(ScorecardActivity::class.java)
            NotificationDestination.UNAVAILABLE -> Toast.makeText(
                this, R.string.notification_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun openTab(destination: Class<*>) {
        startActivity(Intent(this, destination).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
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

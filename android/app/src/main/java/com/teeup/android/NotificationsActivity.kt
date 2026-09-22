package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.AppNotification
import com.teeup.android.data.NotificationCategory
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 6: Notifications, reached from Home's bell icon. Loads real notifications from
 * GET /api/notifications and is deliberately not filtered by NotificationPreferences, since
 * those toggles control creation, not history visibility.
 */
class NotificationsActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        findViewById<Button>(R.id.notifications_retry).setOnClickListener { loadNotifications() }

        loadNotifications()
    }

    private fun loadNotifications() {
        findViewById<View>(R.id.notifications_progress).visibility = View.VISIBLE
        findViewById<View>(R.id.notifications_retry).visibility = View.GONE
        findViewById<View>(R.id.text_notifications_status).visibility = View.GONE

        Thread {
            try {
                val notifications = TeeUpApiClient.fetchNotifications()
                runOnUiThread {
                    findViewById<View>(R.id.notifications_progress).visibility = View.GONE
                    render(notifications)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<View>(R.id.notifications_progress).visibility = View.GONE
                    findViewById<View>(R.id.notifications_retry).visibility = View.VISIBLE
                    findViewById<LinearLayout>(R.id.notifications_container).removeAllViews()
                    findViewById<TextView>(R.id.text_notifications_status).apply {
                        text = getString(R.string.notifications_load_failed)
                        visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun render(notifications: List<AppNotification>) {
        val container = findViewById<LinearLayout>(R.id.notifications_container)
        container.removeAllViews()

        val statusText = findViewById<TextView>(R.id.text_notifications_status)
        if (notifications.isEmpty()) {
            statusText.text = getString(R.string.notifications_empty)
            statusText.visibility = View.VISIBLE
            return
        }
        statusText.visibility = View.GONE

        // Already newest-first server-side (NotificationService.GetForUserAsync).
        notifications.forEach { notification -> container.addView(buildNotificationRow(notification)) }
    }

    private fun buildNotificationRow(notification: AppNotification): View {
        val category = NotificationCategory.fromType(notification.type)
        val hasTarget = notification.relatedEntityId != null

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                when {
                    hasTarget && !notification.isRead -> R.drawable.bg_card_outline_interactive
                    hasTarget -> R.drawable.bg_card_interactive
                    !notification.isRead -> R.drawable.bg_card_outline
                    else -> R.drawable.bg_card
                }
            )
            elevation = resources.getDimension(R.dimen.elevation_card)
            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = space(R.dimen.space_sm)
            }
            if (hasTarget) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onNotificationClicked(notification) }
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (!notification.isRead) {
            titleRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    marginEnd = space(R.dimen.space_sm)
                }
                setBackgroundResource(R.drawable.bg_dot_accent)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        titleRow.addView(TextView(this).apply {
            text = category?.let { getString(it.labelRes) } ?: getString(R.string.notifications_title)
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        card.addView(titleRow)

        card.addView(TextView(this).apply {
            text = notification.message
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = space(R.dimen.space_xs)
            }
        })

        card.addView(TextView(this).apply {
            text = formatTeeTime(notification.createdAt)
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_caption))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = space(R.dimen.space_xs)
            }
        })

        return card
    }

    /** relatedEntityId is the tee time to open. Tee Time Detail already handles a
     *  deleted/unavailable id with its own load-failed state, so there's nothing to check
     *  first. */
    private fun onNotificationClicked(notification: AppNotification) {
        val teeTimeId = notification.relatedEntityId
        if (teeTimeId == null) {
            TeeUpBanner.show(this, getString(R.string.notification_unavailable), isError = true)
            return
        }
        startActivity(Intent(this, TeeTimeDetailActivity::class.java).apply {
            putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, teeTimeId)
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)
    private fun space(dimensionRes: Int): Int = resources.getDimensionPixelSize(dimensionRes)
}

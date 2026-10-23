package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.teeup.android.data.AppNotification
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.notifications.NotificationsScreen
import com.teeup.android.notifications.NotificationsViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 6: Notifications, reached from Home's bell icon. The UI is [NotificationsScreen]; the
 * list (GET /api/notifications, not filtered by NotificationPreferences, since those toggles
 * control creation, not history) lives in [NotificationsViewModel].
 */
class NotificationsActivity : LocaleComponentActivity() {
    private val viewModel: NotificationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            TeeUpTheme {
                NotificationsScreen(
                    viewModel,
                    onBack = {
                        finish()
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    },
                    onOpen = ::onNotificationClicked
                )
            }
        }
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
}

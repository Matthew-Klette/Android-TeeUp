package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.NotificationPreferencesScreen
import com.teeup.android.profile.NotificationPreferencesViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.collectWhileStarted
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import com.teeup.android.ui.showNotice

/**
 * Profile → Notification Preferences. Two of the three switches persist server-side via PATCH
 * /api/profiles/me (see NotificationPreferences for why Sync Reminders doesn't), loading the
 * current values on open so a reinstall shows the real saved state.
 */
class NotificationPreferencesActivity : LocaleComponentActivity() {
    private val viewModel: NotificationPreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TeeUpTheme { NotificationPreferencesScreen(viewModel, onBack = ::finishWithSlideBack) } }
        collectWhileStarted(viewModel.events, ::showNotice)
    }
}

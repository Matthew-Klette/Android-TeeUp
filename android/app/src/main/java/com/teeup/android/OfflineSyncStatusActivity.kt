package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.OfflineSyncScreen
import com.teeup.android.profile.OfflineSyncViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.collectWhileStarted
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import com.teeup.android.ui.showNotice

/**
 * Profile → Offline Data & Sync Status: rounds queued offline in the Room outbox, the last
 * successful sync, the live connection, and Sync Now. UI: [OfflineSyncScreen].
 */
class OfflineSyncStatusActivity : LocaleComponentActivity() {
    private val viewModel: OfflineSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TeeUpTheme { OfflineSyncScreen(viewModel, onBack = ::finishWithSlideBack, onCheckNow = { viewModel.checkNow(this) }) }
        }
        collectWhileStarted(viewModel.events, ::showNotice)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(this)
    }
}

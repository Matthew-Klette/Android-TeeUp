package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.EditEvent
import com.teeup.android.profile.PlayingDetailsScreen
import com.teeup.android.profile.PlayingDetailsViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.collectWhileStarted
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import com.teeup.android.ui.showNotice

/**
 * Profile → Playing Details. Same PATCH /api/profiles/me as PersonalDetailsActivity, editing
 * handicap / home course / pace of play this time; displayName carries through unchanged. The
 * course field is live search (GET /api/courses?search=), as on Register.
 */
class PlayingDetailsActivity : LocaleComponentActivity() {
    private val viewModel: PlayingDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TeeUpTheme { PlayingDetailsScreen(viewModel, onBack = ::finishWithSlideBack) } }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is EditEvent.Banner -> showNotice(event.notice)
                EditEvent.Saved -> finishWithSlideBack()
            }
        }
    }
}

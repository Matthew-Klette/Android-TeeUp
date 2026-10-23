package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.EditEvent
import com.teeup.android.profile.PersonalDetailsScreen
import com.teeup.android.profile.PersonalDetailsViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.collectWhileStarted
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import com.teeup.android.ui.showNotice

/**
 * Profile → Personal Details. Real PATCH /api/profiles/me — there's no separate GET, so the
 * current profile is read via the idempotent POST /api/auth/register (see RegisteredUser's doc
 * comment), then only displayName is edited here; the other fields carry through unchanged.
 */
class PersonalDetailsActivity : LocaleComponentActivity() {
    private val viewModel: PersonalDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TeeUpTheme { PersonalDetailsScreen(viewModel, onBack = ::finishWithSlideBack) } }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is EditEvent.Banner -> showNotice(event.notice)
                EditEvent.Saved -> finishWithSlideBack()
            }
        }
    }
}

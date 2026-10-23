package com.teeup.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.creategroup.CreateGroupEvent
import com.teeup.android.creategroup.CreateGroupScreen
import com.teeup.android.creategroup.CreateGroupViewModel
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.showNotice
import kotlinx.coroutines.launch

/**
 * EME-311: create a real group looking for players — course, date/time, holes, guests wanted,
 * wanted handicap range, wanted pace. Posts to POST /api/teetimes/groups. Reachable from Home's
 * Create a Group button. EME-321: also the edit screen for an existing group —
 * TeeTimeDetailActivity's Edit starts it with [EXTRA_EDIT_TEE_TIME_ID] and the group's current
 * values as the other EXTRA_EDIT_* extras, which pre-fills the form, locks the course (editing
 * never sends it) and PATCHes instead of POSTing. The UI is [CreateGroupScreen]; the form and
 * API calls live in [CreateGroupViewModel].
 */
class CreateGroupActivity : LocaleComponentActivity() {
    companion object {
        const val EXTRA_EDIT_TEE_TIME_ID = "com.teeup.android.extra.EDIT_TEE_TIME_ID"
        const val EXTRA_EDIT_COURSE_ID = "com.teeup.android.extra.EDIT_COURSE_ID"
        const val EXTRA_EDIT_DATE_TIME_ISO = "com.teeup.android.extra.EDIT_DATE_TIME_ISO"
        const val EXTRA_EDIT_HOLES = "com.teeup.android.extra.EDIT_HOLES"
        const val EXTRA_EDIT_OPEN_SPOTS = "com.teeup.android.extra.EDIT_OPEN_SPOTS"
        const val EXTRA_EDIT_HANDICAP_MIN = "com.teeup.android.extra.EDIT_HANDICAP_MIN"
        const val EXTRA_EDIT_HANDICAP_MAX = "com.teeup.android.extra.EDIT_HANDICAP_MAX"
        const val EXTRA_EDIT_PACE = "com.teeup.android.extra.EDIT_PACE"
    }

    private val viewModel: CreateGroupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            TeeUpTheme { CreateGroupScreen(viewModel, onBack = ::close) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is CreateGroupEvent.Banner -> showNotice(event.notice)
                        is CreateGroupEvent.Done -> {
                            showNotice(event.notice)
                            close()
                        }
                    }
                }
            }
        }
    }

    private fun close() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}

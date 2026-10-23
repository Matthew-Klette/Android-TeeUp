package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.nav.BottomNav
import com.teeup.android.rounds.RoundsActions
import com.teeup.android.rounds.RoundsEvent
import com.teeup.android.rounds.RoundsScreen
import com.teeup.android.rounds.RoundsViewModel
import com.teeup.android.ui.DeviceLocation
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.showNotice
import kotlinx.coroutines.launch

/** Rounds tab: choose an upcoming round to start scoring, or review a past one. The UI is
 *  [RoundsScreen]; the schedule and solo-round creation live in [RoundsViewModel]. */
class RoundsActivity : LocaleComponentActivity() {
    private val viewModel: RoundsViewModel by viewModels()
    private var hasResumedOnce = false
    private var askedForLocation = false

    /** Nearby courses are a nicety: a "no" leaves Start a Round's search exactly as it was. */
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) DeviceLocation.request(this, viewModel::updateLocation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val actions = RoundsActions(
            openScorecard = ::openScorecard,
            openSummary = { teeTimeId ->
                startActivity(Intent(this, RoundSummaryActivity::class.java).putExtra(RoundSummaryActivity.EXTRA_TEE_TIME_ID, teeTimeId))
            },
            openTab = { tab -> BottomNav.open(this, tab) },
            findNearby = ::findNearby
        )
        setContent {
            TeeUpTheme { RoundsScreen(viewModel, actions) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is RoundsEvent.Banner -> showNotice(event.notice)
                        is RoundsEvent.OpenScorecard -> openScorecard(event.teeTimeId, event.holes)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up scores just saved by ScorecardActivity/RoundSummaryActivity without a stale
        // list. The ViewModel's own first load covers the first resume.
        if (hasResumedOnce) viewModel.refresh()
        hasResumedOnce = true
    }

    /** Asks for location once per visit; after that only uses it if it was granted. */
    private fun findNearby() {
        when {
            DeviceLocation.hasPermission(this) -> DeviceLocation.request(this, viewModel::updateLocation)
            !askedForLocation -> {
                askedForLocation = true
                locationPermission.launch(DeviceLocation.PERMISSIONS)
            }
        }
    }

    private fun openScorecard(teeTimeId: String, holes: Int?) {
        startActivity(Intent(this, ScorecardActivity::class.java).apply {
            putExtra(ScorecardActivity.EXTRA_TEE_TIME_ID, teeTimeId)
            holes?.let { putExtra(ScorecardActivity.EXTRA_HOLE_COUNT, it) }
        })
    }
}

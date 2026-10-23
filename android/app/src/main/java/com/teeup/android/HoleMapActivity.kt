package com.teeup.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.scorecard.HoleMapActions
import com.teeup.android.scorecard.HoleMapHost
import com.teeup.android.scorecard.HoleMapScreen
import com.teeup.android.scorecard.HoleMapUiState
import com.teeup.android.scorecard.HoleStep
import com.teeup.android.ui.HoleMapController
import com.teeup.android.ui.LocaleComponentActivity

/**
 * Live Hole Map opened from a course preview (Part 1 custom feature: real-time GPS scorecard).
 * The map itself — satellite hole view, GPS and live distances — is [HoleMapController], shared
 * with the in-round ScorecardActivity; this screen adds ‹ › through the course's mapped holes and
 * Follow GPS. The UI is [HoleMapScreen].
 */
class HoleMapActivity : LocaleComponentActivity(), HoleMapController.Listener {
    companion object {
        const val EXTRA_COURSE_ID = "com.teeup.android.extra.COURSE_ID"
        const val EXTRA_COURSE_NAME = "com.teeup.android.extra.COURSE_NAME"
        /** Hole to open on. Defaults to the first mapped hole. */
        const val EXTRA_START_HOLE = "com.teeup.android.extra.START_HOLE"
    }

    private lateinit var host: HoleMapHost
    private lateinit var ui: HoleMapUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        ui = HoleMapUiState(courseName)
        host = HoleMapHost(this, this)
        val actions = HoleMapActions(
            back = {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            previous = { stepHole(-1) },
            next = { stepHole(+1) },
            followGps = { host.withController { it.resumeFollowingGps() } },
            resetMarkers = { host.withController { it.resetMarkers() } }
        )
        setContent { TeeUpTheme { HoleMapScreen(ui, host, actions) } }
        host.withController { map ->
            if (courseId == null) map.showUnavailable() else map.start(courseId, courseName, intent.getIntExtra(EXTRA_START_HOLE, 0))
        }
    }

    override fun onResume() {
        super.onResume()
        host.onResume()
    }

    override fun onPause() {
        super.onPause()
        host.onPause()
    }

    override fun onDestroy() {
        host.onDestroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        host.onRequestPermissionsResult(requestCode)
    }

    override fun onHoleShown(hole: Int) {
        val map = host.controller ?: return
        if (hole !in map.mappedHoles) return
        val (canPrevious, canNext) = HoleStep.canStep(map.mappedHoles, hole)
        ui.hole = hole
        ui.canPrevious = canPrevious
        ui.canNext = canNext
        ui.showFollowGps = !map.isFollowingGps
    }

    override fun onMarkersMovedChanged(moved: Boolean) {
        ui.showResetMarkers = moved
    }

    override fun onFollowGpsChanged(following: Boolean) {
        ui.showFollowGps = !following && host.controller?.mappedHoles?.isNotEmpty() == true
    }

    private fun stepHole(delta: Int) {
        val map = host.controller ?: return
        HoleStep.next(map.mappedHoles, map.shownHoleNumber, delta)?.let { map.showHole(it) }
    }
}

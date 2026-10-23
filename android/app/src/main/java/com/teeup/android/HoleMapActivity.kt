package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.teeup.android.ui.HoleMapController
import com.teeup.android.ui.LocaleActivity

/**
 * Live Hole Map opened from a course preview (Part 1 custom feature: real-time GPS scorecard).
 * The map itself — satellite hole view, GPS and live distances — is [HoleMapController], shared
 * with the in-round ScorecardActivity; this screen adds plain Previous / Follow GPS / Next
 * controls over the course's mapped holes.
 */
class HoleMapActivity : LocaleActivity(), HoleMapController.Listener {
    companion object {
        const val EXTRA_COURSE_ID = "com.teeup.android.extra.COURSE_ID"
        const val EXTRA_COURSE_NAME = "com.teeup.android.extra.COURSE_NAME"
        /** Hole to open on. Defaults to the first mapped hole. */
        const val EXTRA_START_HOLE = "com.teeup.android.extra.START_HOLE"
    }

    private lateinit var map: HoleMapController
    private lateinit var holeTitleText: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var followButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hole_map)

        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        holeTitleText = findViewById(R.id.text_hole_title)
        previousButton = findViewById(R.id.button_previous_hole)
        nextButton = findViewById(R.id.button_next_hole)
        followButton = findViewById(R.id.button_follow_gps)
        findViewById<TextView>(R.id.text_course_name).text = courseName
        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        previousButton.setOnClickListener { stepHole(-1) }
        nextButton.setOnClickListener { stepHole(+1) }
        followButton.setOnClickListener { map.resumeFollowingGps() }
        listOf(previousButton, nextButton, followButton).forEach { it.isEnabled = false }

        map = HoleMapController(this, this)
        if (courseId == null) {
            map.showUnavailable()
        } else {
            map.start(courseId, courseName, intent.getIntExtra(EXTRA_START_HOLE, 0))
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        map.onDestroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        map.onRequestPermissionsResult(requestCode)
    }

    override fun onHoleShown(hole: Int) {
        val mapped = map.mappedHoles
        val index = mapped.indexOf(hole)
        if (index < 0) return
        holeTitleText.text = getString(R.string.hole_map_hole_format, hole)
        previousButton.isEnabled = index > 0
        nextButton.isEnabled = index < mapped.lastIndex
        followButton.isEnabled = !map.isFollowingGps
    }

    override fun onFollowGpsChanged(following: Boolean) {
        followButton.isEnabled = !following && map.mappedHoles.isNotEmpty()
    }

    private fun stepHole(delta: Int) {
        val mapped = map.mappedHoles
        val index = mapped.indexOf(map.shownHoleNumber)
        if (index < 0) return
        val next = (index + delta).coerceIn(0, mapped.lastIndex)
        if (next != index) map.showHole(mapped[next])
    }
}

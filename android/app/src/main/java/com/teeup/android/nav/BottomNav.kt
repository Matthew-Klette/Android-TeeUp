package com.teeup.android.nav

import android.app.Activity
import android.content.Intent
import com.teeup.android.HomeActivity
import com.teeup.android.ProfileActivity
import com.teeup.android.R
import com.teeup.android.RoundsActivity
import com.teeup.android.ScorecardActivity

enum class BottomNavTab {
    HOME, ROUNDS, SCORECARD, PROFILE
}

/**
 * Wires the shared `bottom_nav.xml` include into a tab-root Activity.
 * No fragments/AndroidX BottomNavigationView (team decision: plain Kotlin,
 * no Jetpack) — each tab is its own Activity, switched with CLEAR_TOP so the
 * back stack doesn't pile up across tabs.
 */
object BottomNav {
    fun wire(activity: Activity, current: BottomNavTab) {
        activity.findViewById<android.view.View>(R.id.nav_home).setOnClickListener {
            if (current != BottomNavTab.HOME) navigateTo(activity, HomeActivity::class.java)
        }
        activity.findViewById<android.view.View>(R.id.nav_rounds).setOnClickListener {
            if (current != BottomNavTab.ROUNDS) navigateTo(activity, RoundsActivity::class.java)
        }
        activity.findViewById<android.view.View>(R.id.nav_scorecard).setOnClickListener {
            if (current != BottomNavTab.SCORECARD) navigateTo(activity, ScorecardActivity::class.java)
        }
        activity.findViewById<android.view.View>(R.id.nav_profile).setOnClickListener {
            if (current != BottomNavTab.PROFILE) navigateTo(activity, ProfileActivity::class.java)
        }
    }

    private fun navigateTo(activity: Activity, destination: Class<*>) {
        val intent = Intent(activity, destination)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        activity.startActivity(intent)
    }
}

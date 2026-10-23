package com.teeup.android.nav

import android.app.Activity
import android.content.Intent
import com.teeup.android.HomeActivity
import com.teeup.android.ProfileActivity
import com.teeup.android.R
import com.teeup.android.RoundsActivity
import com.teeup.android.ScorecardActivity

enum class BottomNavTab {
    HOME,
    ROUNDS,
    SCORECARD,
    PROFILE
}

object BottomNav {

    /** Switches to [tab]'s screen from the GlassTabBar, reusing it if it's already open. */
    fun open(activity: Activity, tab: BottomNavTab) {
        val destination = when (tab) {
            BottomNavTab.HOME -> HomeActivity::class.java
            BottomNavTab.ROUNDS -> RoundsActivity::class.java
            BottomNavTab.SCORECARD -> ScorecardActivity::class.java
            BottomNavTab.PROFILE -> ProfileActivity::class.java
        }
        navigateTo(activity, destination)
    }

    private fun navigateTo(
        activity: Activity,
        destination: Class<*>
    ) {
        val intent = Intent(activity, destination).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}
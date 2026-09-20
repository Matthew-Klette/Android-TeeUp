package com.teeup.android.nav

import android.app.Activity
import android.content.Intent
import android.view.View
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

    fun wire(activity: Activity, current: BottomNavTab) {
        val home = activity.findViewById<View>(R.id.nav_home)
        val rounds = activity.findViewById<View>(R.id.nav_rounds)
        val scorecard = activity.findViewById<View>(R.id.nav_scorecard)
        val profile = activity.findViewById<View>(R.id.nav_profile)

        // Mark exactly one tab as selected.
        home.isSelected = current == BottomNavTab.HOME
        rounds.isSelected = current == BottomNavTab.ROUNDS
        scorecard.isSelected = current == BottomNavTab.SCORECARD
        profile.isSelected = current == BottomNavTab.PROFILE

        home.setOnClickListener {
            if (current != BottomNavTab.HOME) {
                navigateTo(activity, HomeActivity::class.java)
            }
        }

        rounds.setOnClickListener {
            if (current != BottomNavTab.ROUNDS) {
                navigateTo(activity, RoundsActivity::class.java)
            }
        }

        scorecard.setOnClickListener {
            if (current != BottomNavTab.SCORECARD) {
                navigateTo(activity, ScorecardActivity::class.java)
            }
        }

        profile.setOnClickListener {
            if (current != BottomNavTab.PROFILE) {
                navigateTo(activity, ProfileActivity::class.java)
            }
        }
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
        // Home is the stable root. Switching tabs must not accumulate tab history.
        if (activity !is HomeActivity) activity.finish()
    }
}

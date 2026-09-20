package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

/** Stable Scorecard tab; a selected round opens the separate live preview. */
class ScorecardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scorecard_landing)
        BottomNav.wire(this, BottomNavTab.SCORECARD)
        findViewById<View>(R.id.button_choose_round).setOnClickListener {
            startActivity(Intent(this, RoundsActivity::class.java).putExtra("history", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }
}

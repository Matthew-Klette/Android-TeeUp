package com.teeup.android

import android.os.Bundle
import android.widget.Button
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 5 · Live Scorecard (EME-303 owns the real GPS + offline sync).
 * Note: the Figma frame for this screen has no bottom nav, since it's
 * meant to be an immersive in-round view — kept it as the tab landing
 * page for scaffolding simplicity. Revisit if EME-303 wants a separate
 * "active rounds" list before drilling into hole-by-hole scoring.
 */
class ScorecardActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scorecard)
        BottomNav.wire(this, BottomNavTab.SCORECARD)

        // The hole/scores shown here are static sample data (scorecard_sample_*),
        // so advancing a hole has nothing real to advance — honest placeholder
        // rather than faking hole progression, same pattern as Group Chat.
        findViewById<Button>(R.id.button_next_hole).setOnClickListener {
            TeeUpBanner.show(this, "Live hole-by-hole scoring isn't built yet — see EME-303.")
        }
    }
}

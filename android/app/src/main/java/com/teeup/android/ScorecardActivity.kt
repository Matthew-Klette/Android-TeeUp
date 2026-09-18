package com.teeup.android

import android.app.Activity
import android.os.Bundle
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

/**
 * Screen 5 · Live Scorecard (EME-303 owns the real GPS + offline sync).
 * Note: the Figma frame for this screen has no bottom nav, since it's
 * meant to be an immersive in-round view — kept it as the tab landing
 * page for scaffolding simplicity. Revisit if EME-303 wants a separate
 * "active rounds" list before drilling into hole-by-hole scoring.
 */
class ScorecardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scorecard)
        BottomNav.wire(this, BottomNavTab.SCORECARD)
    }
}

package com.teeup.android

import android.os.Bundle
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity

/** Rounds tab. No dedicated Figma frame — added because bottom nav needs a 4th destination. */
class RoundsActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rounds)
        BottomNav.wire(this, BottomNavTab.ROUNDS)
    }
}

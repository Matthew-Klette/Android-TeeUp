package com.teeup.android

import android.app.Activity
import android.os.Bundle
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

/** Screen 4 · Profile & Settings (EME-301 owns the real screen; EME-296 owns first-time setup). */
class ProfileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        BottomNav.wire(this, BottomNavTab.PROFILE)
        // Each row (row_personal_details, row_playing_details, row_notification_preferences,
        // row_language, row_biometric_login, row_offline_sync, row_privacy) is stubbed for EME-301.
    }
}

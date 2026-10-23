package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import com.teeup.android.data.AuthSession
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.PrivacyDataScreen
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack

/** Profile → Privacy & Data (POPIA). The UI is [PrivacyDataScreen]. */
class PrivacyDataActivity : LocaleComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TeeUpTheme { PrivacyDataScreen(onBack = ::finishWithSlideBack, onSignOut = { AuthSession.signOut(this) }) }
        }
    }
}

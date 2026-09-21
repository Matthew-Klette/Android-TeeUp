package com.teeup.android.ui

import android.content.Context
import androidx.fragment.app.FragmentActivity

/** Base for the two BiometricPrompt-hosting screens (BiometricLoginActivity,
 *  BiometricUnlockActivity) — BiometricPrompt requires a FragmentActivity, unlike every
 *  other screen in this app. See [LocaleActivity]/[LocaleManager] for the locale part. */
abstract class LocaleFragmentActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

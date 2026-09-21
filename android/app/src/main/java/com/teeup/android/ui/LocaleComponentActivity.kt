package com.teeup.android.ui

import android.content.Context
import androidx.activity.ComponentActivity

/** Base class for ComponentActivity screens (SplashActivity) — see [LocaleActivity]/[LocaleManager]. */
abstract class LocaleComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

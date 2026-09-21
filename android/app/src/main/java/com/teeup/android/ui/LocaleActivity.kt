package com.teeup.android.ui

import android.app.Activity
import android.content.Context

/** Base class for every plain Activity screen so language changes (see [LocaleManager]) take effect. */
abstract class LocaleActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }
}

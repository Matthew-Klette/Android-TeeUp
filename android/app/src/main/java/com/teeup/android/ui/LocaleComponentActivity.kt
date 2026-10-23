package com.teeup.android.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Base class for every screen except the two biometric ones, so language changes (see [LocaleManager]) take effect. */
abstract class LocaleComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Compose screens draw edge to edge and move their own content clear of the keyboard
        // through the IME insets (LargeTitleScreen, TeeUpSheet). Without this the system pans the
        // whole window up as well, pushing the top of the screen off the display.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

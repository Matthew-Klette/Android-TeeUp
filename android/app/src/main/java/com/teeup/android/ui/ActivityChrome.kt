package com.teeup.android.ui

import android.app.Activity
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Every Compose screen draws under transparent system bars, with dark icons on the light page.
 *  Call before super.onCreate. */
fun ComponentActivity.enableTeeUpEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
}

/** Back out of a pushed screen with the slide the rest of the app uses. */
@Suppress("DEPRECATION")
fun Activity.finishWithSlideBack() {
    finish()
    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
}

/** Collects a ViewModel's one-off [events] while the screen is started. */
fun <T> ComponentActivity.collectWhileStarted(events: Flow<T>, handle: (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) { events.collect(handle) }
    }
}

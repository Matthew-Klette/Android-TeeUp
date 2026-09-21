package com.teeup.android.ui

import android.app.Activity

/** Legacy blocking requests may finish after Back or rotation destroys the Activity. */
fun Activity.runWhenActive(action: () -> Unit) {
    runOnUiThread { if (!isFinishing && !isDestroyed) action() }
}

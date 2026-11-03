package com.teeup.android

import android.app.Activity
import android.content.Intent
import com.teeup.android.data.LocalIdentity
import com.teeup.android.data.offline.ScorecardOutbox
import com.teeup.android.notifications.TeeTimeReminders

/** Shared by every sign-in path (Google, email). Call off the main thread, right
 *  after a Firebase sign-in succeeds. */
fun Activity.completeSignIn() {
    val identity = LocalIdentity.registerFresh(this)
    // A round this golfer queued offline before signing out uploads now, and their tee time
    // reminders are planned for this phone.
    ScorecardOutbox.scheduleSync(this)
    TeeTimeReminders.refresh(this)
    runOnUiThread {
        val destination = if (identity.profileComplete) HomeActivity::class.java else RegisterActivity::class.java
        startActivity(Intent(this, destination))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}

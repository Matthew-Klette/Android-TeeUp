package com.teeup.android

import android.app.Activity
import android.content.Intent
import com.teeup.android.data.LocalIdentity

/** Shared by every sign-in path (Google, email). Call off the main thread, right
 *  after a Firebase sign-in succeeds. */
fun Activity.completeSignIn() {
    val identity = LocalIdentity.registerFresh(this)
    runOnUiThread {
        val destination = if (identity.profileComplete) HomeActivity::class.java else RegisterActivity::class.java
        startActivity(Intent(this, destination))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}

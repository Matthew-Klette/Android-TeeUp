package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.LocalIdentity
import com.teeup.android.ui.LocaleActivity

/** Profile → Privacy & Data (POPIA). Real, working Sign Out — the only place in the app
 *  that clears the Firebase session and the cached backend identity. */
class PrivacyDataActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_data)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<Button>(R.id.button_sign_out).setOnClickListener { onSignOutClicked() }
    }

    private fun onSignOutClicked() {
        FirebaseAuth.getInstance().signOut()
        LocalIdentity.clearCache(this)

        val intent = Intent(this, SignInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}

package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.teeup.android.data.AuthSession
import com.teeup.android.ui.LocaleActivity

/** Profile → Privacy & Data (POPIA). */
class PrivacyDataActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_data)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<Button>(R.id.button_sign_out).setOnClickListener { AuthSession.signOut(this) }
    }
}

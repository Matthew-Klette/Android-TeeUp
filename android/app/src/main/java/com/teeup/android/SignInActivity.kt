package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Screen 1 · Sign In (EME-295 owns the real Firebase Google SSO wiring).
 * Every "continue" path here is a stub that just moves on to Home.
 */
class SignInActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        val goToHome = { startActivity(Intent(this, HomeActivity::class.java)); finish() }

        findViewById<android.view.View>(R.id.button_continue_google).setOnClickListener { goToHome() }
        findViewById<android.view.View>(R.id.button_continue_apple).setOnClickListener { goToHome() }
        findViewById<android.view.View>(R.id.button_continue_email).setOnClickListener { goToHome() }
        findViewById<android.view.View>(R.id.button_use_biometric).setOnClickListener { goToHome() }

        findViewById<android.view.View>(R.id.text_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}

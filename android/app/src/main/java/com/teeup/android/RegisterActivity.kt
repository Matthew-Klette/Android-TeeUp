package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Register / profile setup (EME-296). Not a dedicated Figma frame — implied by Sign In's "New here? Register" link. */
class RegisterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        findViewById<android.view.View>(R.id.button_continue).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}

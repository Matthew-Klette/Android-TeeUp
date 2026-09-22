package com.teeup.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Real Firebase email/password sign-in and sign-up (EME-326). Reached from SignInActivity's
 * "Continue with Email" button (sign-in mode) and "New here? Register" link (sign-up mode);
 * the toggle link on this screen switches between the two either way.
 */
class EmailSignInActivity : LocaleActivity() {
    private val tag = "EmailSignInActivity"

    private lateinit var titleText: TextView
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordLabel: TextView
    private lateinit var confirmPasswordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var toggleModeText: TextView

    private var isSignUpMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_sign_in)

        titleText = findViewById(R.id.text_title)
        emailInput = findViewById(R.id.input_email)
        passwordInput = findViewById(R.id.input_password)
        confirmPasswordLabel = findViewById(R.id.label_confirm_password)
        confirmPasswordInput = findViewById(R.id.input_confirm_password)
        submitButton = findViewById(R.id.button_submit)
        toggleModeText = findViewById(R.id.text_toggle_mode)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        isSignUpMode = intent.getBooleanExtra(EXTRA_START_IN_SIGNUP_MODE, false)
        updateModeUi()

        toggleModeText.setOnClickListener {
            isSignUpMode = !isSignUpMode
            updateModeUi()
        }

        submitButton.setOnClickListener { onSubmitClicked() }
    }

    private fun updateModeUi() {
        if (isSignUpMode) {
            titleText.text = getString(R.string.email_auth_title_signup)
            confirmPasswordLabel.visibility = View.VISIBLE
            confirmPasswordInput.visibility = View.VISIBLE
            submitButton.text = getString(R.string.email_auth_submit_signup)
            toggleModeText.text = getString(R.string.email_auth_toggle_to_signin)
        } else {
            titleText.text = getString(R.string.email_auth_title_signin)
            confirmPasswordLabel.visibility = View.GONE
            confirmPasswordInput.visibility = View.GONE
            submitButton.text = getString(R.string.email_auth_submit_signin)
            toggleModeText.text = getString(R.string.email_auth_toggle_to_signup)
        }
    }

    private fun onSubmitClicked() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            TeeUpBanner.show(this, getString(R.string.email_auth_error_email_invalid), isError = true)
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            TeeUpBanner.show(this, getString(R.string.email_auth_error_password_too_short), isError = true)
            return
        }
        if (isSignUpMode && password != confirmPasswordInput.text.toString()) {
            TeeUpBanner.show(this, getString(R.string.email_auth_error_confirm_mismatch), isError = true)
            return
        }

        submitButton.isEnabled = false
        submitButton.text = getString(
            if (isSignUpMode) R.string.email_auth_progress_signup else R.string.email_auth_progress_signin
        )

        Thread {
            try {
                val auth = FirebaseAuth.getInstance()
                if (isSignUpMode) {
                    Tasks.await(auth.createUserWithEmailAndPassword(email, password))
                } else {
                    Tasks.await(auth.signInWithEmailAndPassword(email, password))
                }
                completeSignIn()
            } catch (e: Exception) {
                Log.w(tag, "Email ${if (isSignUpMode) "sign-up" else "sign-in"} failed", e)
                runOnUiThread {
                    submitButton.isEnabled = true
                    updateModeUi()
                    TeeUpBanner.show(this, e.message ?: getString(R.string.email_auth_failed_generic), isError = true)
                }
            }
        }.start()
    }

    companion object {
        private const val EXTRA_START_IN_SIGNUP_MODE = "com.teeup.android.extra.START_IN_SIGNUP_MODE"
        private const val MIN_PASSWORD_LENGTH = 6

        fun intent(context: Context, startInSignUpMode: Boolean): Intent =
            Intent(context, EmailSignInActivity::class.java)
                .putExtra(EXTRA_START_IN_SIGNUP_MODE, startInSignUpMode)
    }
}

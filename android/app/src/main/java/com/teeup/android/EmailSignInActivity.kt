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
import com.google.firebase.auth.FirebaseAuthException
import com.teeup.android.data.ApiException
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

    // Set once Firebase auth itself has succeeded (account created or signed in), so a
    // later failure while registering with our own backend doesn't get retried as if
    // nothing had happened yet: retrying must not call Firebase auth again, since the
    // account already exists / the session is already live.
    private var awaitingBackendRetry = false

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

        isSignUpMode = savedInstanceState?.getBoolean(KEY_IS_SIGN_UP_MODE)
            ?: intent.getBooleanExtra(EXTRA_START_IN_SIGNUP_MODE, false)
        awaitingBackendRetry = savedInstanceState?.getBoolean(KEY_AWAITING_BACKEND_RETRY) ?: false
        updateModeUi()

        toggleModeText.setOnClickListener {
            isSignUpMode = !isSignUpMode
            updateModeUi()
        }

        submitButton.setOnClickListener { onSubmitClicked() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_SIGN_UP_MODE, isSignUpMode)
        outState.putBoolean(KEY_AWAITING_BACKEND_RETRY, awaitingBackendRetry)
    }

    private fun updateModeUi() {
        if (awaitingBackendRetry) {
            // Firebase auth already succeeded, only our own backend step is left. Mode
            // no longer means anything here, so hide the choice instead of showing a
            // stale one.
            toggleModeText.visibility = View.GONE
            emailInput.isEnabled = false
            passwordInput.isEnabled = false
            confirmPasswordInput.isEnabled = false
            submitButton.text = getString(R.string.email_auth_retry)
            return
        }

        toggleModeText.visibility = View.VISIBLE
        emailInput.isEnabled = true
        passwordInput.isEnabled = true
        confirmPasswordInput.isEnabled = true

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
        if (awaitingBackendRetry) {
            submitButton.isEnabled = false
            submitButton.text = getString(R.string.email_auth_progress_retry)
            Thread { registerWithBackend() }.start()
            return
        }

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
        // Captured now, not read again from the field inside the worker thread below: the
        // confirm-password field can only be edited while this Activity is on screen and
        // interactive, same lifetime as this function call.
        val signUpMode = isSignUpMode
        if (signUpMode && password != confirmPasswordInput.text.toString()) {
            TeeUpBanner.show(this, getString(R.string.email_auth_error_confirm_mismatch), isError = true)
            return
        }

        submitButton.isEnabled = false
        submitButton.text = getString(
            if (signUpMode) R.string.email_auth_progress_signup else R.string.email_auth_progress_signin
        )
        // Switching mode mid-request would leave the in-flight Firebase call mismatched
        // with what's on screen, so lock it until this attempt resolves one way or another.
        toggleModeText.isEnabled = false

        Thread {
            val firebaseAuthSucceeded = try {
                val auth = FirebaseAuth.getInstance()
                if (signUpMode) {
                    Tasks.await(auth.createUserWithEmailAndPassword(email, password))
                } else {
                    Tasks.await(auth.signInWithEmailAndPassword(email, password))
                }
                true
            } catch (e: Exception) {
                // Firebase auth itself failed: no account was created and no session
                // started, so it's safe to let the user retry sign-in/sign-up as normal.
                Log.w(tag, "Email ${if (signUpMode) "sign-up" else "sign-in"} failed", e)
                runOnUiThread {
                    submitButton.isEnabled = true
                    toggleModeText.isEnabled = true
                    updateModeUi()
                    TeeUpBanner.show(this, friendlyAuthErrorMessage(e), isError = true)
                }
                false
            }

            // Firebase auth succeeded past this point: the account exists and this device
            // is signed in. Any failure below is our own backend, not Firebase, so a retry
            // must never call createUserWithEmailAndPassword/signInWithEmailAndPassword again.
            if (firebaseAuthSucceeded) {
                registerWithBackend()
            }
        }.start()
    }

    /** Runs off the main thread. Registers the already-signed-in Firebase user with our own
     *  backend; safe to call repeatedly since POST /api/auth/register is idempotent. */
    private fun registerWithBackend() {
        try {
            completeSignIn()
        } catch (e: Exception) {
            Log.w(tag, "Backend registration failed after Firebase auth succeeded", e)
            runOnUiThread {
                awaitingBackendRetry = true
                submitButton.isEnabled = true
                updateModeUi()
                val fallback = getString(R.string.email_auth_registration_failed)
                val message = if (e is ApiException) e.message ?: fallback else fallback
                TeeUpBanner.show(this, message, isError = true)
            }
        }
    }

    /** Firebase's own exception message is a raw Java class name, not something to show a
     *  user. Map known error codes to a plain sentence, falling back to the generic message. */
    private fun friendlyAuthErrorMessage(e: Exception): String {
        val errorCode = (e as? FirebaseAuthException)?.errorCode
        return when (errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.email_auth_error_email_in_use)
            "ERROR_INVALID_EMAIL" -> getString(R.string.email_auth_error_email_invalid)
            "ERROR_WRONG_PASSWORD" -> getString(R.string.email_auth_error_wrong_password)
            "ERROR_USER_NOT_FOUND" -> getString(R.string.email_auth_error_user_not_found)
            "ERROR_USER_DISABLED" -> getString(R.string.email_auth_error_user_disabled)
            "ERROR_WEAK_PASSWORD" -> getString(R.string.email_auth_error_weak_password)
            "ERROR_TOO_MANY_REQUESTS" -> getString(R.string.email_auth_error_too_many_requests)
            "ERROR_NETWORK_REQUEST_FAILED" -> getString(R.string.email_auth_error_network)
            "ERROR_INVALID_CREDENTIAL" -> getString(R.string.email_auth_error_invalid_credential)
            else -> getString(R.string.email_auth_failed_generic)
        }
    }

    companion object {
        private const val EXTRA_START_IN_SIGNUP_MODE = "com.teeup.android.extra.START_IN_SIGNUP_MODE"
        private const val KEY_IS_SIGN_UP_MODE = "isSignUpMode"
        private const val KEY_AWAITING_BACKEND_RETRY = "awaitingBackendRetry"
        private const val MIN_PASSWORD_LENGTH = 6

        fun intent(context: Context, startInSignUpMode: Boolean): Intent =
            Intent(context, EmailSignInActivity::class.java)
                .putExtra(EXTRA_START_IN_SIGNUP_MODE, startInSignUpMode)
    }
}

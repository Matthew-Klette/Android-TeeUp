package com.teeup.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.teeup.android.auth.EmailAuthForm
import com.teeup.android.auth.EmailAuthScreen
import com.teeup.android.auth.EmailAuthUiState
import com.teeup.android.auth.FirebaseAuthErrors
import com.teeup.android.data.ApiException
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real Firebase email/password sign-in and sign-up (EME-326). Reached from SignInActivity's
 * "Continue with Email" button (sign-in mode) and "New here? Register" link (sign-up mode);
 * the toggle link on this screen switches between the two either way. The UI is
 * [EmailAuthScreen].
 */
class EmailSignInActivity : LocaleComponentActivity() {
    private val tag = "EmailSignInActivity"
    private lateinit var ui: EmailAuthUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        // awaitingBackendRetry is set once Firebase auth itself has succeeded (account created or
        // signed in), so a later failure registering with our own backend isn't retried as if
        // nothing had happened: retrying must not call Firebase auth again.
        ui = EmailAuthUiState(
            signUp = savedInstanceState?.getBoolean(KEY_IS_SIGN_UP_MODE) ?: intent.getBooleanExtra(EXTRA_START_IN_SIGNUP_MODE, false),
            awaitingBackendRetry = savedInstanceState?.getBoolean(KEY_AWAITING_BACKEND_RETRY) ?: false
        )
        setContent { TeeUpTheme { EmailAuthScreen(ui, onBack = ::finishWithSlideBack, onSubmit = ::onSubmitClicked) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_SIGN_UP_MODE, ui.signUp)
        outState.putBoolean(KEY_AWAITING_BACKEND_RETRY, ui.awaitingBackendRetry)
    }

    private fun onSubmitClicked() {
        if (ui.busy) return
        if (ui.awaitingBackendRetry) {
            ui.busy = true
            lifecycleScope.launch { registerWithBackend() }
            return
        }

        // Captured now: the fields can't change once the request is in flight (they're locked).
        val email = ui.email.trim()
        val password = ui.password
        val signUpMode = ui.signUp
        EmailAuthForm.validate(email, password, ui.confirm, signUpMode) { Patterns.EMAIL_ADDRESS.matcher(it).matches() }?.let { error ->
            TeeUpBanner.show(this, getString(error), isError = true)
            return
        }

        ui.busy = true
        lifecycleScope.launch {
            val firebaseAuthSucceeded = try {
                withContext(Dispatchers.IO) {
                    val auth = FirebaseAuth.getInstance()
                    if (signUpMode) {
                        Tasks.await(auth.createUserWithEmailAndPassword(email, password))
                    } else {
                        Tasks.await(auth.signInWithEmailAndPassword(email, password))
                    }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Firebase auth itself failed: no account was created and no session
                // started, so it's safe to let the user retry sign-in/sign-up as normal.
                Log.w(tag, "Email ${if (signUpMode) "sign-up" else "sign-in"} failed", e)
                ui.busy = false
                TeeUpBanner.show(this@EmailSignInActivity, friendlyAuthErrorMessage(e), isError = true)
                false
            }

            // Firebase auth succeeded past this point: the account exists and this device
            // is signed in. Any failure below is our own backend, not Firebase, so a retry
            // must never call createUserWithEmailAndPassword/signInWithEmailAndPassword again.
            if (firebaseAuthSucceeded) registerWithBackend()
        }
    }

    /** Registers the already-signed-in Firebase user with our own backend; safe to call
     *  repeatedly since POST /api/auth/register is idempotent. */
    private suspend fun registerWithBackend() {
        try {
            withContext(Dispatchers.IO) { completeSignIn() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Backend registration failed after Firebase auth succeeded", e)
            ui.awaitingBackendRetry = true
            ui.busy = false
            val fallback = getString(R.string.email_auth_registration_failed)
            val message = if (e is ApiException) e.message ?: fallback else fallback
            TeeUpBanner.show(this, message, isError = true)
        }
    }

    /** Tasks.await wraps a failed task's exception in ExecutionException, so the real
     *  FirebaseAuthException is the cause, not e itself. */
    private fun friendlyAuthErrorMessage(e: Exception): String {
        val errorCode = ((e as? FirebaseAuthException) ?: (e.cause as? FirebaseAuthException))?.errorCode
        return getString(FirebaseAuthErrors.messageFor(errorCode))
    }

    companion object {
        private const val EXTRA_START_IN_SIGNUP_MODE = "com.teeup.android.extra.START_IN_SIGNUP_MODE"
        private const val KEY_IS_SIGN_UP_MODE = "isSignUpMode"
        private const val KEY_AWAITING_BACKEND_RETRY = "awaitingBackendRetry"

        fun intent(context: Context, startInSignUpMode: Boolean): Intent =
            Intent(context, EmailSignInActivity::class.java)
                .putExtra(EXTRA_START_IN_SIGNUP_MODE, startInSignUpMode)
    }
}

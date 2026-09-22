package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException as GoogleSignInException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.teeup.android.data.LocalIdentity
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 1: Sign In. "Continue with Google" is the real Firebase Google SSO flow.
 * Email and biometric buttons have no credential UI yet, so they route to the
 * same Google flow for now instead of a dead end.
 */
class SignInActivity : LocaleActivity() {
    private val tag = "SignInActivity"
    private lateinit var googleButton: Button

    // Firebase Google Sign-In setup (Google, n.d.)
    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        googleButton = findViewById(R.id.button_continue_google)
        googleButton.setOnClickListener { onGoogleSignInClicked() }

        // Neither has its own credential UI yet, so both route to Google
        // SSO like the main button instead of a dead end.
        findViewById<Button>(R.id.button_continue_email).setOnClickListener { onGoogleSignInClicked() }
        findViewById<Button>(R.id.button_use_biometric).setOnClickListener { onGoogleSignInClicked() }

        // No separate sign-up flow. Google SSO doubles as registration for a
        // first-time user, so this link starts the same flow as the button.
        findViewById<View>(R.id.text_register).setOnClickListener { onGoogleSignInClicked() }
    }

    private fun onGoogleSignInClicked() {
        googleButton.isEnabled = false
        googleButton.setText(R.string.sign_in_google_progress)
        startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(GoogleSignInException::class.java)
            val idToken = account.idToken
                ?: throw IllegalStateException("Google sign-in returned no ID token")
            authenticateWithFirebase(idToken)
        } catch (e: GoogleSignInException) {
            resetGoogleButton()
            // The user backing out of the Google chooser isn't a failure worth surfacing.
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                Log.w(tag, "Google sign-in failed: ${e.statusCode}", e)
                TeeUpBanner.show(this, getString(R.string.signin_google_failed), isError = true)
            }
        }
    }

    private fun authenticateWithFirebase(googleIdToken: String) {
        Thread {
            try {
                val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Tasks.await(FirebaseAuth.getInstance().signInWithCredential(credential))
                completeSignIn()
            } catch (e: Exception) {
                Log.w(tag, "Firebase sign-in failed", e)
                runOnUiThread {
                    resetGoogleButton()
                    TeeUpBanner.show(this, getString(R.string.signin_failed_generic), isError = true)
                }
            }
        }.start()
    }

    /** Runs off the main thread, right after a Firebase sign-in succeeds. */
    private fun completeSignIn() {
        val identity = LocalIdentity.registerFresh(this)
        runOnUiThread {
            val destination = if (identity.profileComplete) HomeActivity::class.java else RegisterActivity::class.java
            startActivity(Intent(this, destination))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    private fun resetGoogleButton() {
        googleButton.isEnabled = true
        googleButton.setText(R.string.sign_in_google)
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}

/*
References:

Google. Firebase (n.d.). Authenticate with Google on Android. [online] Available at: <https://firebase.google.com/docs/auth/android/google-signin> [Accessed 19 Sep. 2026].
*/

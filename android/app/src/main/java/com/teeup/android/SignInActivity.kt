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
 * Screen 1 · Sign In. "Continue with Google" is the real Firebase Google SSO
 * flow (EME-295); Email/biometric remain stubs for their own tickets. Android
 * only — no Apple sign-in. No guest/anonymous option — every session is a
 * real Google-backed identity.
 */
class SignInActivity : LocaleActivity() {
    private val tag = "SignInActivity"
    private lateinit var googleButton: Button

    /** Restricted to the owner's account: the picker skips straight to it (still one
     *  real Google consent tap — that's a Google OAuth requirement, not something
     *  any app code can skip) instead of showing every account on the device. */
    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .setAccountName(OWNER_ACCOUNT_EMAIL)
            .build()
        GoogleSignIn.getClient(this, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        val goToHome = {
            startActivity(Intent(this, HomeActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }

        googleButton = findViewById(R.id.button_continue_google)
        googleButton.setOnClickListener { onGoogleSignInClicked() }

        findViewById<View>(R.id.button_continue_email).setOnClickListener { goToHome() }
        findViewById<View>(R.id.button_use_biometric).setOnClickListener { goToHome() }

        // No separate sign-up credential flow — Google SSO doubles as registration
        // for a first-time user, so this link starts the same flow as the button.
        findViewById<View>(R.id.text_register).setOnClickListener { onGoogleSignInClicked() }

        // If this account already granted consent on this device, this resolves
        // without showing any UI at all and lands straight on Home/Register.
        attemptSilentSignIn()
    }

    /** Silent Google sign-in — succeeds without UI only if [OWNER_ACCOUNT_EMAIL] already
     *  granted this app consent on this device before. First-ever run on a device still
     *  needs the one real interactive consent tap; nothing can skip that. */
    private fun attemptSilentSignIn() {
        googleSignInClient.silentSignIn().addOnCompleteListener(this) { task ->
            // Failure here is the normal case (no cached credential yet, e.g. a fresh
            // install) — task.result throws on failure, so it must never be touched
            // without checking isSuccessful first.
            if (!task.isSuccessful) {
                Log.d(tag, "Silent Google sign-in not available yet: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            val idToken = task.result?.idToken ?: return@addOnCompleteListener
            Log.d(tag, "Silent Google sign-in succeeded for ${task.result?.email}")
            authenticateWithFirebase(idToken)
        }
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
                TeeUpBanner.show(this, "Google sign-in failed — please try again", isError = true)
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
                    TeeUpBanner.show(this, "Sign-in failed — check your connection and try again", isError = true)
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

        /** The only account this build's sign-in flow targets — see the
         *  googleSignInClient/attemptSilentSignIn doc comments above. */
        private const val OWNER_ACCOUNT_EMAIL = "matthewklette14@gmail.com"
    }
}

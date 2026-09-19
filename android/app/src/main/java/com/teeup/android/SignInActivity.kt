package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException as GoogleSignInException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.teeup.android.data.LocalIdentity

/**
 * Screen 1 · Sign In. "Continue with Google" is the real Firebase Google SSO
 * flow (EME-295); Apple/Email/biometric remain stubs for their own tickets.
 */
class SignInActivity : Activity() {
    private val tag = "SignInActivity"
    private lateinit var googleButton: Button

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

        val goToHome = { startActivity(Intent(this, HomeActivity::class.java)); finish() }

        googleButton = findViewById(R.id.button_continue_google)
        googleButton.setOnClickListener { onGoogleSignInClicked() }

        findViewById<View>(R.id.button_continue_apple).setOnClickListener { goToHome() }
        findViewById<View>(R.id.button_continue_email).setOnClickListener { goToHome() }
        findViewById<View>(R.id.button_use_biometric).setOnClickListener { goToHome() }

        findViewById<View>(R.id.text_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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
                Toast.makeText(this, "Google sign-in failed — please try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun authenticateWithFirebase(googleIdToken: String) {
        Thread {
            try {
                val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Tasks.await(FirebaseAuth.getInstance().signInWithCredential(credential))
                LocalIdentity.ensureRegistered(this)
                runOnUiThread {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Log.w(tag, "Firebase sign-in failed", e)
                runOnUiThread {
                    resetGoogleButton()
                    Toast.makeText(this, "Sign-in failed — check your connection and try again", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }

    private fun resetGoogleButton() {
        googleButton.isEnabled = true
        googleButton.setText(R.string.sign_in_google)
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}

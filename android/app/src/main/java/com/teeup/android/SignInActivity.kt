package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException as GoogleSignInException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.teeup.android.auth.SignInActions
import com.teeup.android.auth.SignInScreen
import com.teeup.android.auth.SignInUiState
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 1: Sign In. "Continue with Google" is the real Firebase Google SSO flow;
 * "Continue with Email" and "Register" open EmailSignInActivity's real email/password
 * flow. Biometric has no credential UI of its own yet, so it still routes to Google.
 * The UI is [SignInScreen].
 */
class SignInActivity : LocaleComponentActivity() {
    private val tag = "SignInActivity"
    private val ui = SignInUiState()

    // Firebase Google Sign-In setup (Google, n.d.)
    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        val actions = SignInActions(
            google = ::onGoogleSignInClicked,
            email = { startActivity(EmailSignInActivity.intent(this, startInSignUpMode = false)) },
            // Biometric has no credential UI of its own yet, so it still routes to
            // Google rather than a dead end.
            biometric = ::onGoogleSignInClicked,
            register = { startActivity(EmailSignInActivity.intent(this, startInSignUpMode = true)) }
        )
        setContent { TeeUpTheme { SignInScreen(ui, actions) } }
    }

    @Suppress("DEPRECATION")
    private fun onGoogleSignInClicked() {
        if (ui.googleBusy) return
        ui.googleBusy = true
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
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Tasks.await(FirebaseAuth.getInstance().signInWithCredential(credential))
                    completeSignIn()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Firebase sign-in failed", e)
                resetGoogleButton()
                TeeUpBanner.show(this@SignInActivity, getString(R.string.signin_failed_generic), isError = true)
            }
        }
    }

    private fun resetGoogleButton() {
        ui.googleBusy = false
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}

/*
References:

Google. Firebase (n.d.). Authenticate with Google on Android. [online] Available at: <https://firebase.google.com/docs/auth/android/google-signin> [Accessed 19 Sep. 2026].
*/

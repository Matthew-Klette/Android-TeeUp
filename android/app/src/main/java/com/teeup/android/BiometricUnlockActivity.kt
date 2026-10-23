package com.teeup.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.auth.BiometricUnlockScreen
import com.teeup.android.auth.BiometricUnlockUiState
import com.teeup.android.data.LocalIdentity
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleFragmentActivity
import com.teeup.android.ui.enableTeeUpEdgeToEdge

/**
 * The real enforcement side of Profile → Biometric Login: SplashActivity routes here
 * instead of straight to HomeActivity whenever BiometricPreference.isEnabled() is true.
 * A passed prompt is required before Home opens; there's no way around it from here
 * short of signing out. The UI is [BiometricUnlockScreen].
 */
class BiometricUnlockActivity : LocaleFragmentActivity() {
    private lateinit var ui: BiometricUnlockUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        ui = BiometricUnlockUiState(getString(R.string.biometric_unlock_prompt_subtitle))
        setContent { TeeUpTheme { BiometricUnlockScreen(ui, onTryAgain = ::promptForUnlock, onSignOut = ::signOutInstead) } }
        promptForUnlock()
    }

    private fun signOutInstead() {
        FirebaseAuth.getInstance().signOut()
        LocalIdentity.clearCache(this)
        val intent = Intent(this, SignInActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun promptForUnlock() {
        ui.showOptions = false
        ui.status = getString(R.string.biometric_unlock_prompt_subtitle)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_unlock_prompt_title))
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                startActivity(Intent(this@BiometricUnlockActivity, HomeActivity::class.java))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                ui.status = errString.toString()
                ui.showOptions = true
            }

            override fun onAuthenticationFailed() {
                // A single failed scan — the prompt stays open for another attempt.
            }
        })

        prompt.authenticate(promptInfo)
    }
}

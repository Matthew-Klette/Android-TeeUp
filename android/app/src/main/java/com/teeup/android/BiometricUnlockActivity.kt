package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.LocalIdentity
import com.teeup.android.ui.LocaleFragmentActivity

/**
 * The real enforcement side of Profile → Biometric Login: SplashActivity routes here
 * instead of straight to HomeActivity whenever BiometricPreference.isEnabled() is true.
 * A passed prompt is required before Home opens; there's no way around it from here
 * short of signing out.
 */
class BiometricUnlockActivity : LocaleFragmentActivity() {
    private lateinit var statusText: TextView
    private lateinit var tryAgainButton: Button
    private lateinit var signOutInstead: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_unlock)

        statusText = findViewById(R.id.text_unlock_status)
        tryAgainButton = findViewById(R.id.button_try_again)
        signOutInstead = findViewById(R.id.text_sign_out_instead)

        statusText.text = getString(R.string.biometric_unlock_prompt_subtitle)
        tryAgainButton.setOnClickListener { promptForUnlock() }
        signOutInstead.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            LocalIdentity.clearCache(this)
            val intent = Intent(this, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }

        promptForUnlock()
    }

    private fun promptForUnlock() {
        tryAgainButton.visibility = View.GONE
        signOutInstead.visibility = View.GONE
        statusText.text = getString(R.string.biometric_unlock_prompt_subtitle)

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
                statusText.text = errString.toString()
                tryAgainButton.visibility = View.VISIBLE
                signOutInstead.visibility = View.VISIBLE
            }

            override fun onAuthenticationFailed() {
                // A single failed scan — the prompt stays open for another attempt.
            }
        })

        prompt.authenticate(promptInfo)
    }
}

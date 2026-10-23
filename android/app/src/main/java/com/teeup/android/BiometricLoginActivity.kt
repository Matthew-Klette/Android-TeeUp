package com.teeup.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.teeup.android.auth.BiometricLoginScreen
import com.teeup.android.auth.BiometricLoginUiState
import com.teeup.android.auth.BiometricReasons
import com.teeup.android.data.BiometricPreference
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleFragmentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import com.teeup.android.ui.finishWithSlideBack

/**
 * Profile → Biometric Login. Real androidx.biometric check + real BiometricPrompt —
 * turning this on requires actually passing a live Face/Fingerprint prompt, not just
 * flipping a switch. See BiometricUnlockActivity for where this gets enforced
 * (SplashActivity routes there instead of straight to Home when this is on).
 */
class BiometricLoginActivity : LocaleFragmentActivity() {
    private lateinit var ui: BiometricLoginUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        ui = BiometricLoginUiState(getString(R.string.biometric_checking))
        setContent { TeeUpTheme { BiometricLoginScreen(ui, onBack = ::finishWithSlideBack, onToggle = ::onToggled) } }

        val canAuthenticate = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            ui.status = getString(R.string.biometric_unavailable_format, getString(BiometricReasons.reasonFor(canAuthenticate)))
            ui.enabled = false
            return
        }
        ui.status = getString(R.string.biometric_available)
        ui.enabled = true
        ui.checked = BiometricPreference.isEnabled(this)
    }

    private fun onToggled(on: Boolean) {
        ui.checked = on
        if (on) {
            confirmThenEnable()
        } else {
            BiometricPreference.setEnabled(this, false)
            TeeUpBanner.show(this, getString(R.string.biometric_turned_off))
        }
    }

    /** Enabling requires actually passing a live prompt first — otherwise a user could
     *  flip this on for a fingerprint that was never actually verified against this device. */
    private fun confirmThenEnable() {
        ui.enabled = false
        ui.status = getString(R.string.biometric_confirming)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                BiometricPreference.setEnabled(this@BiometricLoginActivity, true)
                ui.status = getString(R.string.biometric_available)
                ui.enabled = true
                TeeUpBanner.show(this@BiometricLoginActivity, getString(R.string.biometric_turned_on))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                ui.checked = false
                ui.enabled = true
                ui.status = getString(R.string.biometric_available)
                // Cancelling is a normal user choice, not a failure worth flagging red.
                val isCancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                if (!isCancel) {
                    TeeUpBanner.show(this@BiometricLoginActivity, getString(R.string.biometric_confirm_error_format, errString), isError = true)
                }
            }

            override fun onAuthenticationFailed() {
                // A single failed scan — the prompt stays open for another attempt, nothing to do here.
            }
        })

        prompt.authenticate(promptInfo)
    }
}

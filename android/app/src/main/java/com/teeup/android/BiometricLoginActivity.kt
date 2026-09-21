package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.teeup.android.data.BiometricPreference
import com.teeup.android.ui.LocaleFragmentActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Profile → Biometric Login. Real androidx.biometric check + real BiometricPrompt —
 * turning this on requires actually passing a live Face/Fingerprint prompt, not just
 * flipping a switch. See BiometricUnlockActivity for where this gets enforced
 * (SplashActivity routes there instead of straight to Home when this is on).
 */
class BiometricLoginActivity : LocaleFragmentActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggle: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_login)

        statusText = findViewById(R.id.text_status)
        toggle = findViewById(R.id.switch_biometric)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val canAuthenticate = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            statusText.text = getString(R.string.biometric_unavailable_format, unavailableReason(canAuthenticate))
            toggle.isEnabled = false
            return
        }

        statusText.text = getString(R.string.biometric_available)
        toggle.isEnabled = true
        toggle.isChecked = BiometricPreference.isEnabled(this)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                confirmThenEnable()
            } else {
                BiometricPreference.setEnabled(this, false)
                TeeUpBanner.show(this, getString(R.string.biometric_turned_off))
            }
        }
    }

    /** Enabling requires actually passing a live prompt first — otherwise a user could
     *  flip this on for a fingerprint that was never actually verified against this device. */
    private fun confirmThenEnable() {
        toggle.isEnabled = false
        statusText.text = getString(R.string.biometric_confirming)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_cancel))
            .build()

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                BiometricPreference.setEnabled(this@BiometricLoginActivity, true)
                statusText.text = getString(R.string.biometric_available)
                toggle.isEnabled = true
                TeeUpBanner.show(this@BiometricLoginActivity, getString(R.string.biometric_turned_on))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                toggle.isChecked = false
                toggle.isEnabled = true
                statusText.text = getString(R.string.biometric_available)
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

    private fun unavailableReason(code: Int): String = when (code) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> getString(R.string.biometric_reason_no_hardware)
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> getString(R.string.biometric_reason_hw_unavailable)
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> getString(R.string.biometric_reason_none_enrolled)
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> getString(R.string.biometric_reason_security_update)
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> getString(R.string.biometric_reason_unsupported)
        else -> getString(R.string.biometric_reason_unknown)
    }
}

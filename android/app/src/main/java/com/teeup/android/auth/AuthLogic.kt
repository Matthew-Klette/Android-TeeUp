package com.teeup.android.auth

import androidx.annotation.StringRes
import com.teeup.android.R

private const val MIN_PASSWORD_LENGTH = 6

object EmailAuthForm {
    /**
     * The first problem with the form, as a message, or null when it can be sent. [isEmail] is
     * Android's email pattern in the app (Patterns.EMAIL_ADDRESS); the confirm field only counts
     * when signing up.
     */
    @StringRes
    fun validate(email: String, password: String, confirm: String, signUp: Boolean, isEmail: (String) -> Boolean): Int? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() || !isEmail(trimmed) -> R.string.email_auth_error_email_invalid
            password.length < MIN_PASSWORD_LENGTH -> R.string.email_auth_error_password_too_short
            signUp && password != confirm -> R.string.email_auth_error_confirm_mismatch
            else -> null
        }
    }
}

/** Firebase's own exception message is a raw Java class name, not something to show a user. */
object FirebaseAuthErrors {
    @StringRes
    fun messageFor(errorCode: String?): Int = when (errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.email_auth_error_email_in_use
        "ERROR_INVALID_EMAIL" -> R.string.email_auth_error_email_invalid
        "ERROR_WRONG_PASSWORD" -> R.string.email_auth_error_wrong_password
        "ERROR_USER_NOT_FOUND" -> R.string.email_auth_error_user_not_found
        "ERROR_USER_DISABLED" -> R.string.email_auth_error_user_disabled
        "ERROR_WEAK_PASSWORD" -> R.string.email_auth_error_weak_password
        "ERROR_TOO_MANY_REQUESTS" -> R.string.email_auth_error_too_many_requests
        "ERROR_NETWORK_REQUEST_FAILED" -> R.string.email_auth_error_network
        "ERROR_INVALID_CREDENTIAL" -> R.string.email_auth_error_invalid_credential
        else -> R.string.email_auth_failed_generic
    }
}

/** Why biometric login can't be turned on, from BiometricManager.canAuthenticate. */
object BiometricReasons {
    private const val ERROR_HW_UNAVAILABLE = 1
    private const val ERROR_NONE_ENROLLED = 11
    private const val ERROR_NO_HARDWARE = 12
    private const val ERROR_SECURITY_UPDATE_REQUIRED = 15
    private const val ERROR_UNSUPPORTED = -2

    @StringRes
    fun reasonFor(code: Int): Int = when (code) {
        ERROR_NO_HARDWARE -> R.string.biometric_reason_no_hardware
        ERROR_HW_UNAVAILABLE -> R.string.biometric_reason_hw_unavailable
        ERROR_NONE_ENROLLED -> R.string.biometric_reason_none_enrolled
        ERROR_SECURITY_UPDATE_REQUIRED -> R.string.biometric_reason_security_update
        ERROR_UNSUPPORTED -> R.string.biometric_reason_unsupported
        else -> R.string.biometric_reason_unknown
    }
}

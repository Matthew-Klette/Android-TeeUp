package com.teeup.android.auth

import com.teeup.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthLogicTest {
    private val looksLikeEmail: (String) -> Boolean = { it.contains('@') && it.contains('.') }

    private fun validate(email: String, password: String, confirm: String = "", signUp: Boolean = false) =
        EmailAuthForm.validate(email, password, confirm, signUp, looksLikeEmail)

    @Test
    fun email_trimmedAndRequired() {
        assertEquals(R.string.email_auth_error_email_invalid, validate("   ", "secret1"))
        assertEquals(R.string.email_auth_error_email_invalid, validate("not-an-email", "secret1"))
        assertNull(validate("  me@example.com ", "secret1"))
    }

    @Test
    fun password_atLeastSixCharacters() {
        assertEquals(R.string.email_auth_error_password_too_short, validate("me@example.com", "12345"))
        assertNull(validate("me@example.com", "123456"))
    }

    @Test
    fun confirm_onlyCheckedWhenSigningUp() {
        assertNull(validate("me@example.com", "secret1", confirm = "other", signUp = false))
        assertEquals(R.string.email_auth_error_confirm_mismatch, validate("me@example.com", "secret1", confirm = "other", signUp = true))
        assertNull(validate("me@example.com", "secret1", confirm = "secret1", signUp = true))
    }

    @Test
    fun checksRunInOrder_emailFirst() {
        assertEquals(R.string.email_auth_error_email_invalid, validate("bad", "1", confirm = "2", signUp = true))
    }

    @Test
    fun firebaseErrors_mapToPlainSentences() {
        assertEquals(R.string.email_auth_error_email_in_use, FirebaseAuthErrors.messageFor("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(R.string.email_auth_error_wrong_password, FirebaseAuthErrors.messageFor("ERROR_WRONG_PASSWORD"))
        assertEquals(R.string.email_auth_error_invalid_credential, FirebaseAuthErrors.messageFor("ERROR_INVALID_CREDENTIAL"))
        assertEquals(R.string.email_auth_error_network, FirebaseAuthErrors.messageFor("ERROR_NETWORK_REQUEST_FAILED"))
    }

    @Test
    fun firebaseErrors_unknownOrMissingCodeIsGeneric() {
        assertEquals(R.string.email_auth_failed_generic, FirebaseAuthErrors.messageFor("ERROR_SOMETHING_NEW"))
        assertEquals(R.string.email_auth_failed_generic, FirebaseAuthErrors.messageFor(null))
    }

    @Test
    fun biometricReasons_matchBiometricManagerCodes() {
        // BiometricManager.BIOMETRIC_ERROR_* values.
        assertEquals(R.string.biometric_reason_no_hardware, BiometricReasons.reasonFor(12))
        assertEquals(R.string.biometric_reason_hw_unavailable, BiometricReasons.reasonFor(1))
        assertEquals(R.string.biometric_reason_none_enrolled, BiometricReasons.reasonFor(11))
        assertEquals(R.string.biometric_reason_security_update, BiometricReasons.reasonFor(15))
        assertEquals(R.string.biometric_reason_unsupported, BiometricReasons.reasonFor(-2))
        assertEquals(R.string.biometric_reason_unknown, BiometricReasons.reasonFor(999))
    }
}

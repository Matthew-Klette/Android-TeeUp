package com.teeup.android

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SplashDestination { SIGN_IN, HOME }

class SplashViewModel : BaseViewModel() {
    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination.asStateFlow()

    init {
        val signedIn = FirebaseAuth.getInstance().currentUser != null
        Log.d(tag, "Firebase auth check: signedIn=$signedIn")
        _destination.value = if (signedIn) SplashDestination.HOME else SplashDestination.SIGN_IN
    }
}

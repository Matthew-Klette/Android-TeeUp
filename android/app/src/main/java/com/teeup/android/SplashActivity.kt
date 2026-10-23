package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.teeup.android.auth.SplashScreen
import com.teeup.android.data.BiometricPreference
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Screen 0 · Splash. Checks Firebase auth state before routing to Sign In or
 * Home. No interactive UI, so it should be on/off screen almost instantly.
 */
class SplashActivity : LocaleComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private val tag = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        setContent { TeeUpTheme { SplashScreen() } }

        lifecycleScope.launch {
            viewModel.destination.filterNotNull().collect { destination ->
                Log.d(tag, "routing to $destination")
                val target = when (destination) {
                    // Biometric Login (Profile) gates Home behind a real BiometricPrompt
                    // when turned on. See BiometricUnlockActivity.
                    SplashDestination.HOME ->
                        if (BiometricPreference.isEnabled(this@SplashActivity)) {
                            BiometricUnlockActivity::class.java
                        } else {
                            HomeActivity::class.java
                        }
                    SplashDestination.SIGN_IN -> SignInActivity::class.java
                }
                startActivity(Intent(this@SplashActivity, target))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(tag, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "onDestroy")
    }
}

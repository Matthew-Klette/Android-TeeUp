package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Screen 0 · Splash. Checks Firebase auth state before routing to Sign In or
 * Home. No interactive UI, so it should be on/off screen almost instantly.
 */
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private val tag = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            viewModel.destination.filterNotNull().collect { destination ->
                Log.d(tag, "routing to $destination")
                val target = when (destination) {
                    SplashDestination.HOME -> HomeActivity::class.java
                    SplashDestination.SIGN_IN -> SignInActivity::class.java
                }
                startActivity(Intent(this@SplashActivity, target))
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

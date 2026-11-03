package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.teeup.android.auth.SignInActions
import com.teeup.android.auth.SignInScreen
import com.teeup.android.auth.SignInUiState
import com.teeup.android.data.BiometricPreference
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.splash.SplashShatterView
import com.teeup.android.ui.splash.SplashSound
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Screen 0 · Splash. Checks Firebase auth state, plays the golf intro (tee and
 * ball assemble, the driver snaps the tee, the ball smashes the screen) with its
 * sound, and lets the glass shatter into the next screen before routing to it.
 * Signed-out users see the shards land as the real Sign In screen, so the
 * hand-off to SignInActivity is seamless; signed-in users get the same intro
 * into the app background, then Home fades in.
 */
class SplashActivity : LocaleComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private val tag = "SplashActivity"
    private val sound by lazy { SplashSound(this) }

    /** Set when the intro ends while we're in the background; routed from [onStart]. */
    private var pendingDestination: SplashDestination? = null

    /** True once we've handed off to the next screen, which the sound's tail may play over. */
    private var routed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light icons on the dark intro; useAppSystemBars flips them as the light screen lands.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val destination = viewModel.destination.filterNotNull().first()
            Log.d(tag, "playing intro into $destination")
            playIntro(destination)
        }
    }

    private fun playIntro(destination: SplashDestination) {
        val stage = findViewById<FrameLayout>(R.id.splash_stage)
        val reveal = when (destination) {
            SplashDestination.SIGN_IN -> ComposeView(this).apply {
                // Preview only: the intro view on top swallows every touch until routing.
                setContent { TeeUpTheme { SignInScreen(SignInUiState(), SignInActions({}, {}, {}, {})) } }
            }
            SplashDestination.HOME -> View(this).apply { setBackgroundColor(getColor(R.color.teeup_background)) }
        }
        reveal.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        stage.addView(reveal, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        findViewById<SplashShatterView>(R.id.splash_intro).apply {
            // The first frame can land after onStop (Home pressed during launch); stay quiet then.
            onPlaybackStarted = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) sound.start() }
            onRevealStarting = ::useAppSystemBars
            onFinished = { routeWhenVisible(destination) }
            play(reveal)
        }
    }

    /** Back to the app's dark icons on transparent bars, as the light screen lands. */
    private fun useAppSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * Android 10+ blocks activity starts from the background, so if the user left during
     * the intro, hold the route until they come back instead of finishing into nothing.
     */
    private fun routeWhenVisible(destination: SplashDestination) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            route(destination)
        } else {
            pendingDestination = destination
        }
    }

    @Suppress("DEPRECATION")
    private fun route(destination: SplashDestination) {
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
        if (destination == SplashDestination.SIGN_IN) {
            // The shards already landed as this exact screen; any transition would show a seam.
            overridePendingTransition(0, 0)
        } else {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        // The sound's last ~0.6 s (reverb and breeze tail) carries on over the next screen.
        routed = true
        finish()
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "onStart")
        pendingDestination?.let {
            pendingDestination = null
            route(it)
        }
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
        // Leaving mid-intro (home, back, recents) cuts the sound; routing onward lets it finish.
        if (!routed) sound.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "onDestroy")
    }
}

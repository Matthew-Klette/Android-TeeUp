package com.teeup.android.design

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kotlin.math.PI
import kotlin.math.pow

/**
 * Apple's springs, in Apple's terms: damping ratio (1 = no overshoot) and response (seconds to
 * reach the target). Compose wants stiffness, which for unit mass is (2π / response)².
 * Defaults are critically damped; bounce is only for motion a gesture threw.
 */
object TeeUpMotion {
    fun <T> spring(dampingRatio: Float = 1f, response: Float = 0.35f): AnimationSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffnessFor(response))

    fun stiffnessFor(response: Float): Float = (2 * PI / response).pow(2).toFloat()

    /** Moves and reveals (Apple: reposition 1.0 / 0.4). */
    fun <T> smooth(): AnimationSpec<T> = spring(dampingRatio = 1f, response = 0.4f)

    /** Selection indicators and small state changes. */
    fun <T> snappy(): AnimationSpec<T> = spring(dampingRatio = 1f, response = 0.28f)

    /** Sheets and anything released with momentum (Apple: drawer 0.8 / 0.3). */
    fun <T> momentum(): AnimationSpec<T> = spring(dampingRatio = 0.8f, response = 0.32f)

    /** Press-in: near-instant so feedback lands on touch-down, not on release. */
    fun <T> pressIn(): AnimationSpec<T> = spring(dampingRatio = 1f, response = 0.12f)

    /** Release: slightly soft so the control settles back rather than snapping. */
    fun <T> pressOut(): AnimationSpec<T> = spring(dampingRatio = 0.75f, response = 0.3f)
}

/** Off when the system's animator duration scale is 0 (Settings → Accessibility → Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** [spec], or an instant change when the user has asked for reduced motion. */
@Composable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (rememberReducedMotion()) snap() else spec

/**
 * Tap target with iOS press feedback: shrinks and dims on touch-down (springing back on
 * release, interruptible both ways), commits on touch-up. No ripple — iOS never ripples.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    pressedAlpha: Float = 0.85f,
    haptic: Boolean = false,
    role: Role = Role.Button,
    onClickLabel: String? = null
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = if (pressed) TeeUpMotion.pressIn() else TeeUpMotion.pressOut(),
        label = "pressScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        animationSpec = if (pressed) TeeUpMotion.pressIn() else TeeUpMotion.pressOut(),
        label = "pressAlpha"
    )
    Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel
        ) {
            if (haptic) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        }
}

package com.teeup.android.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

private const val SHIMMER_PERIOD_MS = 1400
private const val SHIMMER_WIDTH_PX = 900f

/** Loading placeholder: a soft sheen sweeping across. Static when reduced motion is on. */
fun Modifier.skeleton(shape: Shape): Modifier = composed {
    val colors = TeeUpTheme.colors
    if (rememberReducedMotion()) return@composed Modifier.background(colors.skeleton, shape)
    val sweep by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = -SHIMMER_WIDTH_PX,
        targetValue = SHIMMER_WIDTH_PX * 2,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    Modifier.background(
        Brush.linearGradient(
            colors = listOf(colors.skeleton, colors.skeletonSheen, colors.skeleton),
            start = Offset(sweep, 0f),
            end = Offset(sweep + SHIMMER_WIDTH_PX, SHIMMER_WIDTH_PX / 4)
        ),
        shape
    )
}

package com.teeup.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Blur a downscaled copy of what's underneath. A blur throws away fine detail anyway, so the
 * difference is invisible, but it's a fraction of the pixels to process every frame.
 */
@OptIn(ExperimentalHazeApi::class)
private val BlurAtReducedResolution: HazeEffectScope.() -> Unit = { inputScale = HazeInputScale.Auto }

/** The screen's blur source: scrolling content registers with `hazeSource`, chrome reads it. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * iOS 26 Liquid Glass, approximated: a real backdrop blur of whatever scrolls underneath
 * (Android 12+; a near-opaque frost on older phones), a faint white tint so labels stay legible,
 * a bright rim where light catches the top edge, and a soft floating shadow. Bigger surfaces
 * read thicker: pass a larger [blur] and [elevation] for bars than for small buttons.
 */
fun Modifier.liquidGlass(
    shape: Shape = CircleShape,
    blur: Dp = 22.dp,
    elevation: Dp = 14.dp
): Modifier = composed {
    val colors = TeeUpTheme.colors
    val state = LocalHazeState.current
    val shadowed = Modifier
        .shadow(elevation, shape, clip = false, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
        .clip(shape)
    // With no blur source (e.g. over a WebView, which can't be recorded into one), a hazeEffect
    // would blur the button's own content instead, so it's the frosted fill on its own.
    val material = if (state == null) {
        Modifier.background(FROST_FALLBACK)
    } else {
        Modifier.hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(colors.glass),
                blurRadius = blur,
                noiseFactor = 0.03f,
                fallbackTint = HazeTint(FROST_FALLBACK)
            ),
            block = BlurAtReducedResolution
        )
    }
    shadowed
        .then(material)
        .border(
            width = 0.8.dp,
            brush = Brush.verticalGradient(listOf(colors.glassRim, Color.White.copy(alpha = 0.15f))),
            shape = shape
        )
}

/** Near-opaque frost for glass that has nothing to blur (or a phone that can't blur). */
private val FROST_FALLBACK = Color(0xF0FFFFFF)

/** The scroll-edge effect under a floating top bar: content blurs progressively as it slides
 *  beneath, instead of meeting a hard 1px divider. */
@Composable
fun Modifier.scrollEdgeGlass(): Modifier {
    val colors = TeeUpTheme.colors
    return this.hazeEffect(
        state = LocalHazeState.current,
        style = HazeStyle(
            backgroundColor = colors.background,
            // Heavier at the top where the status bar sits, lighter where content emerges.
            tint = HazeTint(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(colors.background.copy(alpha = 0.94f), colors.background.copy(alpha = 0.7f))
                )
            ),
            blurRadius = 18.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(colors.background.copy(alpha = 0.97f))
        ),
        block = BlurAtReducedResolution
    )
}

/**
 * The scroll-edge effect behind the floating tab bar: content blurs and fades into the page
 * progressively as it nears the bottom — clear above, fully frosted under the bar — so busy
 * content never competes with the bar. On phones without real blur it's a plain fade.
 */
@Composable
fun Modifier.bottomEdgeGlass(): Modifier {
    val colors = TeeUpTheme.colors
    val fade = Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.88f)))
    return this.hazeEffect(state = LocalHazeState.current) {
        backgroundColor = colors.background
        blurRadius = 20.dp
        noiseFactor = 0f
        tints = listOf(HazeTint(fade))
        fallbackTint = HazeTint(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.97f))))
        // preferPerformance: a single blur under a gradient mask where per-step blurs are slow.
        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f, preferPerformance = true)
        BlurAtReducedResolution(this)
    }
}

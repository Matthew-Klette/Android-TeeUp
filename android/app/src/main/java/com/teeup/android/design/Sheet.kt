package com.teeup.android.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SHEET_INSET = 8.dp
private const val MAX_HEIGHT_FRACTION = 0.88f

/** Apple's momentum projection: where a flick released at [velocity] px/s would come to rest. */
internal fun projectedDistance(velocity: Float, decelerationRate: Float = 0.998f): Float =
    (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

/** Progressive resistance past a boundary, so an over-drag slows rather than hitting a wall. */
internal fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
    (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))

/**
 * iOS bottom sheet. It rises on a critically damped spring, follows the finger 1:1, resists when
 * pulled up past its height, and on release projects the flick's momentum to decide between
 * settling and dismissing, carrying the finger's velocity into the spring so there's no seam.
 * Grabbable mid-flight. The scrim's opacity tracks the sheet's position. Like iOS 26's
 * partial-height sheets it floats inset from the screen edges with every corner rounded.
 *
 * Shown while composed. [content] gets `dismiss(then)`: it animates the sheet away, runs `then`,
 * and reports [onDismissed] so the caller can drop the sheet from composition.
 */
@Composable
fun TeeUpSheet(
    onDismissed: () -> Unit,
    content: @Composable ColumnScope.(dismiss: (then: () -> Unit) -> Unit) -> Unit
) {
    val colors = TeeUpTheme.colors
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val screenHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val offset = remember { Animatable(screenHeight) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val insetPx = with(LocalDensity.current) { SHEET_INSET.toPx() }

    fun dismiss(velocity: Float = 0f, then: () -> Unit = {}) {
        if (dismissing) return
        dismissing = true
        scope.launch {
            // Whole screen height, so the floating sheet clears the navigation bar too.
            val target = screenHeight
            if (reduced) offset.snapTo(target) else offset.animateTo(target, TeeUpMotion.smooth(), initialVelocity = velocity)
            then()
            onDismissed()
        }
    }

    LaunchedEffect(Unit) {
        if (reduced) offset.snapTo(0f) else offset.animateTo(0f, TeeUpMotion.smooth())
    }
    BackHandler { dismiss() }

    /** Follows the finger 1:1 from [dragPosition], rubber-banding above the resting position. */
    fun dragBy(delta: Float) {
        dragPosition += delta
        val shown = if (dragPosition < 0f) -rubberBand(-dragPosition, sheetHeight.coerceAtLeast(1f)) else dragPosition
        scope.launch { offset.snapTo(shown) }
    }

    /** Release: project the flick's momentum, then dismiss or settle, carrying its velocity. */
    suspend fun settle(velocity: Float) {
        if (offset.value + projectedDistance(velocity) > sheetHeight * 0.5f) {
            dismiss(velocity)
        } else {
            dragPosition = 0f
            offset.animateTo(0f, TeeUpMotion.momentum(), initialVelocity = velocity)
        }
    }

    val dragState = rememberDraggableState { delta -> dragBy(delta) }

    // A scrolling list inside the sheet hands the drag over once it's at its top, as on iOS:
    // pulling down past the first row moves the sheet, and pushing a lowered sheet back up
    // raises the sheet before the list starts scrolling.
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPosition <= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -dragPosition)
                dragBy(consumed)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                dragBy(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragPosition <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val progress = if (sheetHeight > 0f) (1f - offset.value / sheetHeight).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() }
        )
        val shape = RoundedCornerShape(TeeUpMetrics.sheetRadius)
        // Laid out in the space above the keyboard and below the status bar, so a sheet with a
        // text field rises with the keyboard and never grows off the top of the screen.
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().statusBarsPadding()) {
            val maxSheetHeight = maxHeight * MAX_HEIGHT_FRACTION
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = SHEET_INSET, vertical = SHEET_INSET)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    // Plus the bottom inset: the distance the sheet travels before its top leaves view.
                    .onSizeChanged { sheetHeight = it.height.toFloat() + insetPx }
                    .graphicsLayer { translationY = offset.value }
                    .shadow(28.dp, shape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
                    .background(colors.surface, shape)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            // Grabbed mid-flight: carry on from wherever it is on screen right now.
                            offset.stop()
                            dragPosition = offset.value
                        },
                        onDragStopped = { velocity -> settle(velocity) }
                    )
                    .nestedScroll(nestedScroll)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).background(colors.tertiaryLabel.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
                }
                content { then -> dismiss(then = then) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

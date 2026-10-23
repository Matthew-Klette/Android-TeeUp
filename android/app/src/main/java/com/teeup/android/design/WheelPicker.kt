package com.teeup.android.design

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val ROW_HEIGHT = 36.dp
private const val VISIBLE_ROWS = 5
private const val PADDING_ROWS = VISIBLE_ROWS / 2

/**
 * iOS wheel picker: rows snap into a highlighted band in the middle, fading and shrinking with
 * distance from it, with a haptic tick as each row crosses the band. [onSelect] reports the
 * centred row whenever it changes. Blank rows above and below let the first and last values
 * reach the middle.
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = TeeUpTheme.colors
    val view = LocalView.current
    // List index i is padding when i < PADDING_ROWS; the top row sits PADDING_ROWS above the band.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start)
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val latestOnSelect by rememberUpdatedState(onSelect)

    LaunchedEffect(state, count) {
        var first = true
        snapshotFlow { centredRow(state, rowPx, count) }
            .distinctUntilChanged()
            .collect { row ->
                // No tick for the starting row, only for rows the finger moves through.
                if (!first) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                first = false
                latestOnSelect(row)
            }
    }

    Box(modifier.height(ROW_HEIGHT * VISIBLE_ROWS).semantics { this.contentDescription = contentDescription }) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .background(colors.fill, RoundedCornerShape(10.dp))
        )
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxWidth()) {
            items(count + PADDING_ROWS * 2, contentType = { "row" }) { index ->
                val value = index - PADDING_ROWS
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .graphicsLayer {
                            // Read while drawing only: scrolling never recomposes the rows.
                            val top = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: 0
                            val distance = abs(top - PADDING_ROWS * rowPx) / rowPx
                            alpha = (1f - distance * 0.3f).coerceIn(0.15f, 1f)
                            val scale = (1f - distance * 0.06f).coerceIn(0.8f, 1f)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (value in 0 until count) {
                        Label(label(value), TeeUpTheme.type.title3, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The value index in the band: the top visible row, plus one once it's more than half gone. */
private fun centredRow(state: LazyListState, rowPx: Float, count: Int): Int {
    val past = if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
    return (state.firstVisibleItemIndex + past).coerceIn(0, count - 1)
}

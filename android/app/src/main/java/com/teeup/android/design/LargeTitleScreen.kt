package com.teeup.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.nav.BottomNavTab
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val TOP_BAR_HEIGHT = 56.dp
private val COLLAPSE_DISTANCE = 52.dp
private val WASH_HEIGHT = 340.dp
private val BAR_CLEARANCE = 32.dp
private val EDGE_FADE = 64.dp

/**
 * The iOS 26 screen frame Home established, for every other screen: a green wash and a large
 * title that hands over to an inline title in a glass bar as the list scrolls beneath it,
 * floating glass toolbar buttons ([navigation] on the left, [actions] on the right), and either
 * the floating tab bar ([selectedTab]) or a floating [bottomBar] over a progressive blur.
 * [overlay] draws above everything, for sheets.
 *
 * Scroll position is only read while drawing, so scrolling never recomposes the screen.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    selectedTab: BottomNavTab? = null,
    onSelectTab: (BottomNavTab) -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    header: @Composable () -> Unit = { LargeTitle(title) },
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val colors = TeeUpTheme.colors
    val density = LocalDensity.current
    val hazeState = rememberHazeState()
    val collapseDistance = with(density) { COLLAPSE_DISTANCE.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val floatingHeight = when {
        selectedTab != null -> TeeUpMetrics.tabBarHeight
        bottomBar != null -> bottomBarHeight
        else -> 0.dp
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier.fillMaxSize().background(colors.background)) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + WASH_HEIGHT)
                        .background(Brush.verticalGradient(listOf(colors.accentWash, colors.background.copy(alpha = 0f))))
                )
                LazyColumn(
                    state = listState,
                    // A focused field scrolls up above the keyboard.
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(top = statusTop + TOP_BAR_HEIGHT, bottom = navBottom + floatingHeight + BAR_CLEARANCE)
                ) {
                    // Gone by half-way, before the inline title starts to appear.
                    item(key = "largeTitle", contentType = "largeTitle") {
                        Box(Modifier.graphicsLayer { alpha = (1f - collapse * 2f).coerceIn(0f, 1f) }) { header() }
                    }
                    content()
                }
            }

            TopBar(title, { collapse }, statusTop, navigation, actions)

            if (selectedTab != null || bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBottom + floatingHeight + EDGE_FADE)
                        .bottomEdgeGlass()
                )
            }
            if (selectedTab != null) {
                GlassTabBar(
                    selected = selectedTab,
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 10.dp)
                )
            } else if (bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = TeeUpMetrics.gutter)
                        .padding(bottom = 10.dp)
                        .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                ) { bottomBar() }
            }
            overlay()
        }
    }
}

/** The large title: an optional accent eyebrow above, an optional secondary line below. */
@Composable
fun LargeTitle(title: String, eyebrow: String? = null, subtitle: String? = null) {
    val type = TeeUpTheme.type
    val colors = TeeUpTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = TeeUpMetrics.gutter)) {
        if (eyebrow != null) {
            Label(eyebrow, type.eyebrow, color = colors.accent, maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Label(title, type.largeTitle, maxLines = 2, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Label(subtitle, type.subheadline, color = colors.secondaryLabel, maxLines = 3)
        }
    }
}

/** The glass back button at the top left of a pushed screen. */
@Composable
fun BackButton(onBack: () -> Unit, contentDescription: String) {
    GlassIconButton(R.drawable.ic_glyph_chevron_left, contentDescription, onBack)
}

@Composable
private fun TopBar(
    title: String,
    collapse: () -> Float,
    statusTop: Dp,
    navigation: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit
) {
    // The blur only exists once there's something under the bar; at rest it costs nothing.
    val showEdge by remember { derivedStateOf { collapse() > 0f } }
    Box(Modifier.fillMaxWidth()) {
        if (showEdge) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + TOP_BAR_HEIGHT)
                    .graphicsLayer { alpha = collapse() }
                    .scrollEdgeGlass()
            )
        }
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT)
                // Clear of the 44dp toolbar buttons on either side.
                .padding(horizontal = TeeUpMetrics.gutter + TeeUpMetrics.minTouch + 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Label(
                title,
                TeeUpTheme.type.headline,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val progress = ((collapse() - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    alpha = progress
                    // Rises into place as it appears, hinting where the large title went.
                    translationY = (1f - progress) * 6.dp.toPx()
                }
            )
        }
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT)
                .padding(horizontal = TeeUpMetrics.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigation?.invoke()
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}

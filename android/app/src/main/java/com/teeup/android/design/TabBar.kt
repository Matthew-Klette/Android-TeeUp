package com.teeup.android.design

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.nav.BottomNavTab

private data class TabItem(val tab: BottomNavTab, @DrawableRes val icon: Int, @StringRes val label: Int)

private val TABS = listOf(
    TabItem(BottomNavTab.HOME, R.drawable.ic_glyph_house, R.string.nav_home),
    TabItem(BottomNavTab.ROUNDS, R.drawable.ic_glyph_calendar, R.string.nav_rounds),
    TabItem(BottomNavTab.SCORECARD, R.drawable.ic_glyph_flag, R.string.nav_scorecard),
    TabItem(BottomNavTab.PROFILE, R.drawable.ic_glyph_person, R.string.nav_profile)
)

/**
 * iOS 26 floating tab bar: a glass capsule over the content (which keeps scrolling underneath)
 * with a tinted lens behind the current tab that springs to a newly tapped one. The lens moves on
 * touch so the response is immediate even though the next tab is its own Activity.
 */
@Composable
fun GlassTabBar(selected: BottomNavTab, onSelect: (BottomNavTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = TeeUpTheme.colors
    val view = LocalView.current
    val selectedIndex = TABS.indexOfFirst { it.tab == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TeeUpMetrics.tabBarHeight)
            .liquidGlass(CircleShape, blur = 26.dp, elevation = 22.dp)
            .padding(5.dp)
    ) {
        val itemWidth = maxWidth / TABS.size
        val lensOffset by animateDpAsState(itemWidth * selectedIndex, motionSpec(TeeUpMotion.snappy()), label = "lens")
        Box(
            Modifier
                .offset { IntOffset(lensOffset.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .background(colors.accentTint, CircleShape)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TABS.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val tint by animateColorAsState(if (isSelected) colors.accent else colors.label, TeeUpMotion.snappy(), label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(
                            onClick = {
                                if (!isSelected) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelect(item.tab)
                                }
                            },
                            pressedScale = 0.9f,
                            role = Role.Tab
                        )
                        .semantics { this.selected = isSelected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Glyph(item.icon, tint, size = 23.dp)
                    Spacer(Modifier.height(2.dp))
                    Label(stringResource(item.label), TeeUpTheme.type.caption2, color = tint, maxLines = 1)
                }
            }
        }
    }
}

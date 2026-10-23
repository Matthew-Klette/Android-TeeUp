package com.teeup.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One figure in a [StatStrip]: a small caps label over the value, optionally in [valueColor]. */
data class Stat(val label: String, val value: String, val valueColor: Color? = null)

/** Key numbers side by side on a white card, split by hairlines, like the App Store's info strip. */
@Composable
fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Row(
        modifier
            .padding(horizontal = TeeUpMetrics.gutter)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .shadow(18.dp, shape, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(colors.surface)
            .padding(vertical = 14.dp)
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(Modifier.width(0.5.dp).fillMaxHeight().padding(vertical = 6.dp).background(colors.separator))
            }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(stat.label.uppercase(), type.caption2.copy(textAlign = TextAlign.Center), color = colors.secondaryLabel, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Label(stat.value, type.title2.copy(textAlign = TextAlign.Center), color = stat.valueColor ?: colors.label, maxLines = 1)
            }
        }
    }
}

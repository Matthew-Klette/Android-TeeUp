package com.teeup.android.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teeup.android.R

private val ROW_MIN_HEIGHT = 52.dp
private val ROW_PADDING = 16.dp
private val ICON_TILE = 30.dp
private val ICON_GAP = 14.dp

/** Where a divider starts when the rows above and below lead with an icon tile. */
val IconRowDividerInset: Dp = ROW_PADDING + ICON_TILE + ICON_GAP

/**
 * iOS inset grouped section: an optional small-caps [header] above a white rounded card of rows,
 * and an optional [footer] note below. Settings, forms and detail screens are built from these.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = TeeUpTheme.colors
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Column(modifier.fillMaxWidth().padding(horizontal = TeeUpMetrics.gutter)) {
        if (header != null) {
            Label(
                header.uppercase(),
                TeeUpTheme.type.eyebrow,
                color = colors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp).semantics { heading() }
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.12f))
                .clip(shape)
                .background(colors.surface),
            content = content
        )
        if (footer != null) {
            Label(
                footer,
                TeeUpTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )
        }
    }
}

/** Hairline between rows, inset from the leading edge as on iOS. */
@Composable
fun ListDivider(inset: Dp = ROW_PADDING) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(TeeUpTheme.colors.separator))
}

/**
 * One row of a [GroupedSection]: an optional coloured icon tile (Settings-style) or custom
 * [leading] content, a title with an optional subtitle, and a trailing [value], custom
 * [trailing] content or a disclosure chevron. Tappable when [onClick] is set.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = TeeUpTheme.colors.accent,
    titleColor: Color = TeeUpTheme.colors.label,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val tappable = if (onClick != null) {
        Modifier.pressable(onClick = onClick, enabled = enabled, pressedScale = 1f, pressedAlpha = 0.5f)
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .then(tappable)
            .padding(horizontal = ROW_PADDING, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(ICON_GAP))
            }
            icon != null -> {
                Box(
                    Modifier.size(ICON_TILE).clip(RoundedCornerShape(8.dp)).background(iconTint),
                    contentAlignment = Alignment.Center
                ) { Glyph(icon, colors.onAccent, size = 18.dp) }
                Spacer(Modifier.width(ICON_GAP))
            }
        }
        Column(Modifier.weight(1f)) {
            Label(title, type.body, color = if (enabled) titleColor else colors.tertiaryLabel, maxLines = 3)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Label(subtitle, type.footnote, color = colors.secondaryLabel, maxLines = 3)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Label(value, type.body, color = colors.secondaryLabel, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Glyph(R.drawable.ic_glyph_chevron_right, colors.tertiaryLabel, size = 16.dp)
        }
    }
}

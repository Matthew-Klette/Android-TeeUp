package com.teeup.android.design

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText

/** A tinted stroke glyph from res/drawable/ic_glyph_*. */
@Composable
fun Glyph(@DrawableRes icon: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}

/** Plain text in a theme style; colour defaults to the primary label. */
@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = TeeUpTheme.colors.label,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** A 44dp round glass button floating over content (iOS 26 toolbar item). */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false
) {
    val colors = TeeUpTheme.colors
    Box(
        modifier = modifier
            .size(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, pressedScale = 0.9f, onClickLabel = contentDescription)
            .liquidGlass(CircleShape, blur = 16.dp, elevation = 10.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, colors.label, size = 21.dp)
        if (showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-9).dp, y = 9.dp)
                    .size(9.dp)
                    .background(colors.destructive, CircleShape)
            )
        }
    }
}

enum class ButtonKind { Primary, Secondary, Tinted, Destructive, DestructiveTinted }

/** Capsule button. Primary is the one strong action per card or screen. */
@Composable
fun TeeUpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    compact: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val (container, content) = when (kind) {
        ButtonKind.Primary -> colors.accent to colors.onAccent
        ButtonKind.Secondary -> colors.fill to colors.label
        ButtonKind.Tinted -> colors.accentTint to colors.accent
        ButtonKind.Destructive -> colors.destructive to colors.onAccent
        ButtonKind.DestructiveTinted -> colors.destructive.copy(alpha = 0.1f) to colors.destructive
    }
    Row(
        modifier = modifier
            .height(if (compact) TeeUpMetrics.compactButtonHeight else TeeUpMetrics.buttonHeight)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.96f)
            .clip(CircleShape)
            .background(if (enabled) container else colors.fill)
            .padding(horizontal = if (compact) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val tint = if (enabled) content else colors.tertiaryLabel
        if (leadingIcon != null) {
            Glyph(leadingIcon, tint, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Label(text, if (compact) type.subheadline.copy(fontWeight = type.headline.fontWeight) else type.headline, color = tint, maxLines = 1)
    }
}

/** Filter pill: a quiet fill when off, solid ink when on, with an optional chevron for menus. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    @DrawableRes leadingIcon: Int? = null
) {
    val colors = TeeUpTheme.colors
    val container by animateColorAsState(if (selected) colors.label else colors.surface, TeeUpMotion.snappy(), label = "chipBg")
    val content by animateColorAsState(if (selected) colors.surface else colors.label, TeeUpMotion.snappy(), label = "chipFg")
    Row(
        modifier = modifier
            // 44dp to touch (the minimum target), 36dp to see.
            .height(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, pressedScale = 0.94f, haptic = true)
            .padding(vertical = 4.dp)
            .shadow(if (selected) 0.dp else 1.dp, CircleShape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
            .clip(CircleShape)
            .background(container)
            .semantics { this.selected = selected }
            .padding(start = 14.dp, end = if (showChevron) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Glyph(leadingIcon, content, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Label(label, TeeUpTheme.type.subheadline.copy(fontWeight = TeeUpTheme.type.headline.fontWeight), color = content, maxLines = 1)
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Glyph(com.teeup.android.R.drawable.ic_glyph_chevron_down, content.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}

/** iOS search field: grey fill, magnifier, clear button once there's text. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val focus = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(1.dp, RoundedCornerShape(TeeUpMetrics.controlRadius), ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
            .clip(RoundedCornerShape(TeeUpMetrics.controlRadius))
            .background(colors.surface)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph(com.teeup.android.R.drawable.ic_glyph_magnifier, colors.tertiaryLabel, size = 19.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Label(placeholder, type.body, color = colors.tertiaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            Box(
                Modifier
                    .size(32.dp)
                    .pressable(onClick = { onValueChange(""); focus.clearFocus() }, pressedScale = 0.85f, onClickLabel = clearDescription)
                    .semantics { contentDescription = clearDescription },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(18.dp).background(colors.tertiaryLabel, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(com.teeup.android.R.drawable.ic_glyph_xmark, colors.surface, size = 12.dp)
                }
            }
        }
    }
}

/** iOS segmented control: a white thumb that springs between equal segments on a grey track. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TeeUpTheme.colors
    val view = LocalView.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.fill)
            .padding(2.dp)
    ) {
        val segment = maxWidth / options.size
        val thumbOffset by animateDpAsState(segment * selectedIndex, motionSpec(TeeUpMotion.snappy()), label = "thumb")
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
                .background(colors.surface, RoundedCornerShape(8.dp))
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, option ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(
                            onClick = {
                                if (index != selectedIndex) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelect(index)
                            },
                            pressedScale = 1f,
                            pressedAlpha = 0.6f,
                            role = Role.Tab
                        )
                        .semantics { selected = index == selectedIndex },
                    contentAlignment = Alignment.Center
                ) {
                    Label(
                        option,
                        TeeUpTheme.type.footnote.copy(fontWeight = TeeUpTheme.type.headline.fontWeight),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Spacing helper so screens read in the 4-pt grid. */
val ScreenPadding = PaddingValues(horizontal = TeeUpMetrics.gutter)

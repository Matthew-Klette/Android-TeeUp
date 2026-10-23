package com.teeup.android.design

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teeup.android.R

private val FIELD_ROW_MIN_HEIGHT = 52.dp
private val STEPPER_WIDTH = 94.dp
private val STEPPER_HEIGHT = 32.dp

/**
 * A text field as one row of a [GroupedSection], the iOS form idiom. With a [label] the label
 * sits on the left and the entry right-aligned beside it ("Min      12"); without one the entry
 * fills the row. [error] shows in red under the row and is announced to accessibility.
 */
@Composable
fun FieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    error: String? = null,
    /** Focus (and raise the keyboard) as soon as the row appears. */
    autoFocus: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val align = if (label != null) TextAlign.End else TextAlign.Start
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FIELD_ROW_MIN_HEIGHT)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Glyph(leadingIcon, colors.tertiaryLabel, size = 19.dp)
                Spacer(Modifier.width(10.dp))
            }
            if (label != null) {
                Label(label, type.body, maxLines = 1)
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = if (label != null) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Label(placeholder, type.body.copy(textAlign = align), color = colors.tertiaryLabel, maxLines = 1, modifier = Modifier.fillMaxWidth())
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = type.body.copy(color = if (enabled) colors.label else colors.secondaryLabel, textAlign = align),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    // Without a handler, Next/Done do Compose's default (move focus / close keyboard).
                    keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() ?: defaultKeyboardAction(imeAction) }),
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics {
                            contentDescription = label ?: placeholder
                            if (error != null) this.error(error)
                        }
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (error != null) {
            Label(
                error,
                type.footnote,
                color = colors.destructive,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }
    }
}

/** iOS stepper: − and + halves of one grey capsule. Each step ticks the haptics. */
@Composable
fun Stepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementDescription: String,
    incrementDescription: String,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true
) {
    val colors = TeeUpTheme.colors
    val view = LocalView.current
    Row(
        Modifier
            .size(STEPPER_WIDTH, STEPPER_HEIGHT)
            .clip(CircleShape)
            .background(colors.fill),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperHalf(R.drawable.ic_glyph_minus, decrementDescription, canDecrement) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onDecrement()
        }
        Box(Modifier.width(0.5.dp).fillMaxHeight().padding(vertical = 8.dp).background(colors.separator))
        StepperHalf(R.drawable.ic_glyph_plus, incrementDescription, canIncrement) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onIncrement()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StepperHalf(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = TeeUpTheme.colors
    Box(
        Modifier
            .weight(1f)
            .height(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 1f, pressedAlpha = 0.5f, onClickLabel = description)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, if (enabled) colors.label else colors.tertiaryLabel, size = 18.dp)
    }
}

private val TOGGLE_WIDTH = 51.dp
private val TOGGLE_HEIGHT = 31.dp
private val TOGGLE_THUMB = 27.dp

/** iOS switch: the thumb springs across and the track fills green when on. */
@Composable
fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = TeeUpTheme.colors
    val view = LocalView.current
    val travel = TOGGLE_WIDTH - TOGGLE_THUMB - 4.dp
    val thumbOffset by animateDpAsState(if (checked) travel else 0.dp, motionSpec(TeeUpMotion.momentum()), label = "toggleThumb")
    val track by animateColorAsState(if (checked) colors.accent else colors.fillPressed, TeeUpMotion.snappy(), label = "toggleTrack")
    Box(
        modifier
            .size(TOGGLE_WIDTH, TOGGLE_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onCheckedChange(it)
            }
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(TOGGLE_THUMB)
                .shadow(3.dp, CircleShape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
                .background(colors.surface, CircleShape)
        )
    }
}

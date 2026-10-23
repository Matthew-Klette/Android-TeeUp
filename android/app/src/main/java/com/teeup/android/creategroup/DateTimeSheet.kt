package com.teeup.android.creategroup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.Glyph
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.Label
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.WheelPicker
import com.teeup.android.design.pressable
import java.text.DateFormatSymbols
import java.util.Calendar

private const val HOURS = 24
private const val MINUTES = 60

/**
 * The date and time picker, in a sheet: a month calendar (today onwards, as the old date
 * picker's minimum) above hour and minute wheels. Starts on the current choice, or today at the
 * current time. Apply hands back the local wall-clock values.
 */
@Composable
fun ColumnScope.DateTimeSheetContent(
    initial: PickedDateTime?,
    onApply: (PickedDateTime) -> Unit,
    dismiss: (then: () -> Unit) -> Unit
) {
    val start = remember { initial ?: PickedDateTime.from(Calendar.getInstance()) }
    val today = remember { Calendar.getInstance().let { Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH)) } }
    var month by remember { mutableStateOf(start.year to start.monthIndex) }
    var day by remember { mutableStateOf(Triple(start.year, start.monthIndex, start.day)) }
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }

    Label(stringResource(R.string.create_group_when), TeeUpTheme.type.title3, modifier = Modifier.padding(bottom = 6.dp).semantics { heading() })
    MonthHeader(
        year = month.first,
        monthIndex = month.second,
        // Nothing before this month can be picked, so there's no going back past it.
        canGoBack = month.first * 12 + month.second > today.first * 12 + today.second,
        onShift = { by -> month = CalendarMonth.shift(month.first, month.second, by) }
    )
    MonthGrid(month.first, month.second, selected = day, today = today, onPick = { day = it })
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            count = HOURS,
            selected = hour,
            onSelect = { hour = it },
            label = { "%02d".format(it) },
            contentDescription = stringResource(R.string.create_group_pick_time_title),
            modifier = Modifier.weight(1f)
        )
        Label(":", TeeUpTheme.type.title3, modifier = Modifier.padding(horizontal = 8.dp))
        WheelPicker(
            count = MINUTES,
            selected = minute,
            onSelect = { minute = it },
            label = { "%02d".format(it) },
            contentDescription = stringResource(R.string.create_group_pick_time_title),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeeUpButton(stringResource(R.string.filter_cancel), onClick = { dismiss {} }, modifier = Modifier.weight(1f), kind = ButtonKind.Secondary)
        TeeUpButton(
            stringResource(R.string.filter_apply),
            onClick = {
                onApply(PickedDateTime(day.first, day.second, day.third, hour, minute))
                dismiss {}
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MonthHeader(year: Int, monthIndex: Int, canGoBack: Boolean, onShift: (Int) -> Unit) {
    val monthName = remember(monthIndex) { DateFormatSymbols.getInstance().months[monthIndex] }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label("$monthName $year", TeeUpTheme.type.headline, modifier = Modifier.weight(1f), maxLines = 1)
        if (canGoBack) {
            MonthButton(R.drawable.ic_glyph_chevron_left, stringResource(R.string.create_group_previous_month)) { onShift(-1) }
            Spacer(Modifier.width(8.dp))
        }
        MonthButton(R.drawable.ic_glyph_chevron_right, stringResource(R.string.create_group_next_month)) { onShift(1) }
    }
}

/** A round grey button: glass would vanish against the white sheet. */
@Composable
private fun MonthButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, pressedScale = 0.9f, onClickLabel = description)
            .padding(4.dp)
            .background(TeeUpTheme.colors.fill, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, TeeUpTheme.colors.accent, size = 18.dp)
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    monthIndex: Int,
    selected: Triple<Int, Int, Int>,
    today: Triple<Int, Int, Int>,
    onPick: (Triple<Int, Int, Int>) -> Unit
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val firstDay = remember { Calendar.getInstance().firstDayOfWeek }
    val weekdays = remember(firstDay) {
        val short = DateFormatSymbols.getInstance().shortWeekdays
        (0 until 7).map { short[(firstDay - 1 + it) % 7 + 1].take(1).uppercase() }
    }
    val cells = remember(year, monthIndex, firstDay) { CalendarMonth.cells(year, monthIndex, firstDay) }

    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach { Label(it, type.caption2.copy(textAlign = TextAlign.Center), color = colors.tertiaryLabel, modifier = Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(4.dp))
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val date = Triple(year, monthIndex, day)
                        val enabled = CalendarMonth.isSelectable(year, monthIndex, day, today)
                        val isSelected = date == selected
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(if (isSelected) colors.accent else colors.surface, CircleShape)
                                .pressable(onClick = { onPick(date) }, enabled = enabled, pressedScale = 0.9f)
                                .semantics { this.selected = isSelected },
                            contentAlignment = Alignment.Center
                        ) {
                            Label(
                                day.toString(),
                                if (isSelected || date == today) type.headline else type.body,
                                color = when {
                                    isSelected -> colors.onAccent
                                    !enabled -> colors.tertiaryLabel.copy(alpha = 0.5f)
                                    date == today -> colors.accent
                                    else -> colors.label
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

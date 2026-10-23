package com.teeup.android.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.data.formatHandicapOrEmpty
import com.teeup.android.data.handicapValue
import com.teeup.android.data.validHandicap
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.SegmentedControl
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable

@Composable
internal fun HomeSheetContent(
    sheet: HomeSheet,
    state: HomeUiState,
    viewModel: HomeViewModel,
    dismiss: (then: () -> Unit) -> Unit
) {
    val filters = state.filters
    when (sheet) {
        HomeSheet.DATE -> OptionsSheet(
            title = stringResource(R.string.home_filter_datetime),
            options = stringArrayResource(R.array.home_date_filter_options).toList(),
            selected = filters.date.ordinal,
            dismiss = dismiss
        ) { index -> viewModel.updateFilters { it.copy(date = DateFilter.entries[index]) } }
        HomeSheet.SPOTS -> OptionsSheet(
            title = stringResource(R.string.home_filter_players),
            options = stringArrayResource(R.array.home_players_filter_options).toList(),
            selected = filters.minOpenSpots,
            dismiss = dismiss
        ) { index -> viewModel.updateFilters { it.copy(minOpenSpots = index) } }
        HomeSheet.COURSE -> {
            val sorted = remember(state.courses) { state.courses.values.sortedBy { it.name } }
            OptionsSheet(
                title = stringResource(R.string.home_filter_course),
                options = listOf(stringResource(R.string.home_filter_course_any)) + sorted.map { it.name },
                selected = filters.courseId?.let { id -> sorted.indexOfFirst { it.id == id } + 1 } ?: 0,
                dismiss = dismiss
            ) { index -> viewModel.updateFilters { it.copy(courseId = if (index == 0) null else sorted[index - 1].id) } }
        }
        HomeSheet.HOLES -> OptionsSheet(
            title = stringResource(R.string.home_filter_holes),
            options = stringArrayResource(R.array.home_holes_filter_options).toList(),
            selected = when (filters.holes) { 9 -> 1; 18 -> 2; else -> 0 },
            dismiss = dismiss
        ) { index -> viewModel.updateFilters { it.copy(holes = when (index) { 1 -> 9; 2 -> 18; else -> null }) } }
        HomeSheet.SKILL -> SkillSheet(filters, dismiss) { maxHandicap, pace ->
            viewModel.updateFilters { it.copy(maxHandicap = maxHandicap, pace = pace) }
        }
    }
}

/** A titled list with a checkmark on the current choice. Picking applies at once — the list
 *  behind updates while the sheet slides away. */
@Composable
private fun OptionsSheet(
    title: String,
    options: List<String>,
    selected: Int,
    dismiss: (then: () -> Unit) -> Unit,
    onPick: (Int) -> Unit
) {
    val colors = TeeUpTheme.colors
    Label(title, TeeUpTheme.type.title3, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
    LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        itemsIndexed(options) { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(colors.background)
                    .pressable(onClick = { onPick(index); dismiss {} }, pressedScale = 1f, pressedAlpha = 0.5f, haptic = true)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label(option, TeeUpTheme.type.body, modifier = Modifier.weight(1f), maxLines = 1)
                AnimatedVisibility(index == selected, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Glyph(R.drawable.ic_glyph_check, colors.accent, size = 20.dp)
                }
            }
            if (index < options.lastIndex) {
                Box(Modifier.fillMaxWidth().background(colors.background).padding(start = 16.dp).height(0.5.dp).background(colors.separator))
            }
        }
    }
}

/** Host skill and pace. Unlike the other sheets this one re-queries the API on Apply. */
@Composable
private fun SkillSheet(
    filters: HomeFilters,
    dismiss: (then: () -> Unit) -> Unit,
    onApply: (maxHandicap: Double?, pace: Int?) -> Unit
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    var handicapText by remember { mutableStateOf(formatHandicapOrEmpty(filters.maxHandicap)) }
    var invalid by remember { mutableStateOf(false) }
    // Segment 0 is "Any pace"; 1..3 are the PaceOfPlay ordinals 0..2.
    var paceIndex by remember { mutableIntStateOf((filters.pace ?: -1) + 1) }
    val paceOptions = listOf(stringResource(R.string.filter_pace_any)) + stringArrayResource(R.array.pace_of_play_options)

    Label(stringResource(R.string.filter_skill_title), type.title3, modifier = Modifier.padding(bottom = 14.dp).semantics { heading() })
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(TeeUpMetrics.controlRadius))
            .background(colors.background)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (handicapText.isEmpty()) Label(stringResource(R.string.filter_max_handicap_hint), type.body, color = colors.tertiaryLabel, maxLines = 1)
        BasicTextField(
            value = handicapText,
            onValueChange = { handicapText = it; invalid = false },
            singleLine = true,
            textStyle = type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (invalid) {
        Label(
            stringResource(R.string.playing_details_handicap_invalid),
            type.footnote,
            color = colors.destructive,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    SegmentedControl(paceOptions, paceIndex, onSelect = { paceIndex = it })
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeeUpButton(
            stringResource(R.string.filter_clear),
            onClick = { onApply(null, null); dismiss {} },
            modifier = Modifier.weight(1f),
            kind = ButtonKind.Secondary
        )
        TeeUpButton(
            stringResource(R.string.filter_apply),
            onClick = {
                if (!validHandicap(handicapText)) {
                    invalid = true
                } else {
                    onApply(handicapValue(handicapText), (paceIndex - 1).takeIf { it in 0..2 })
                    dismiss {}
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

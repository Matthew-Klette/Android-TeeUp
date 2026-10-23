package com.teeup.android.creategroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.design.BackButton
import com.teeup.android.design.FieldRow
import com.teeup.android.design.Glyph
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.SegmentedControl
import com.teeup.android.design.Stepper
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme

/**
 * Create a Group (and Edit Group) as an iOS inset grouped form: course search with suggestions
 * inline, date and time in a sheet, segmented controls for holes and pace, a stepper for the
 * players needed, and the save button floating at the bottom.
 */
@Composable
fun CreateGroupScreen(viewModel: CreateGroupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDateSheet by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val title = stringResource(if (state.isEdit) R.string.edit_group_title else R.string.create_group_title)

    LargeTitleScreen(
        title = title,
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) },
        bottomBar = {
            TeeUpButton(
                stringResource(
                    when {
                        state.submitting && state.isEdit -> R.string.edit_group_saving
                        state.submitting -> R.string.create_group_creating
                        state.isEdit -> R.string.edit_group_save
                        else -> R.string.create_group_create
                    }
                ),
                onClick = { focus.clearFocus(); viewModel.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.submitting
            )
        },
        overlay = {
            if (showDateSheet) {
                TeeUpSheet(onDismissed = { showDateSheet = false }) { dismiss ->
                    DateTimeSheetContent(state.picked, onApply = viewModel::pickDateTime, dismiss = dismiss)
                }
            }
        }
    ) {
        item(key = "course", contentType = "section") { CourseSection(state, viewModel) }
        item(key = "when", contentType = "section") {
            GroupedSection {
                val months = stringArrayResource(R.array.month_abbreviations)
                ListRow(
                    title = state.picked?.label(months) ?: stringResource(R.string.create_group_pick_date_time),
                    titleColor = if (state.picked == null) TeeUpTheme.colors.accent else TeeUpTheme.colors.label,
                    icon = R.drawable.ic_glyph_calendar,
                    onClick = { focus.clearFocus(); showDateSheet = true }
                )
            }
        }
        item(key = "holes", contentType = "section") {
            GroupedSection(header = stringResource(R.string.create_group_holes)) {
                SegmentedControl(
                    options = stringArrayResource(R.array.create_group_holes_options).toList(),
                    selectedIndex = state.holesIndex,
                    onSelect = viewModel::selectHoles,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        item(key = "players", contentType = "section") {
            GroupedSection(header = stringResource(R.string.create_group_players_needed)) {
                ListRow(
                    title = stringResource(R.string.detail_stat_spots),
                    icon = R.drawable.ic_glyph_people,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label(state.playersNeeded.toString(), TeeUpTheme.type.headline, maxLines = 1)
                            Spacer(Modifier.width(12.dp))
                            Stepper(
                                onDecrement = { viewModel.changePlayers(-1) },
                                onIncrement = { viewModel.changePlayers(1) },
                                decrementDescription = stringResource(R.string.create_group_minus),
                                incrementDescription = stringResource(R.string.create_group_plus),
                                canDecrement = state.playersNeeded > 1,
                                canIncrement = state.playersNeeded < 20
                            )
                        }
                    }
                )
            }
        }
        item(key = "handicap", contentType = "section") { HandicapSection(state, viewModel) }
        item(key = "pace", contentType = "section") {
            GroupedSection(header = stringResource(R.string.register_pace)) {
                SegmentedControl(
                    options = listOf(stringResource(R.string.filter_pace_any)) + stringArrayResource(R.array.pace_of_play_options),
                    selectedIndex = state.paceIndex,
                    onSelect = viewModel::selectPace,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/** Search-as-you-type course field with matches listed underneath; locked when editing. */
@Composable
private fun CourseSection(state: CreateGroupUiState, viewModel: CreateGroupViewModel) {
    val colors = TeeUpTheme.colors
    val focus = LocalFocusManager.current
    GroupedSection(header = stringResource(R.string.home_filter_course)) {
        FieldRow(
            value = state.courseQuery,
            onValueChange = viewModel::updateCourseQuery,
            placeholder = stringResource(R.string.course_search_hint),
            leadingIcon = if (state.isEdit) null else R.drawable.ic_glyph_magnifier,
            imeAction = ImeAction.Search,
            onImeAction = { focus.clearFocus() },
            enabled = !state.isEdit,
            trailing = if (state.selectedCourse != null && !state.isEdit) {
                { Glyph(R.drawable.ic_glyph_check, colors.accent, size = 20.dp) }
            } else null
        )
        if (state.selectedCourse == null) {
            state.suggestions.forEach { course ->
                ListDivider()
                ListRow(
                    title = course.name,
                    titleColor = colors.accent,
                    showChevron = false,
                    onClick = { viewModel.selectCourse(course); focus.clearFocus() }
                )
            }
        }
    }
}

@Composable
private fun HandicapSection(state: CreateGroupUiState, viewModel: CreateGroupViewModel) {
    val invalid = stringResource(R.string.playing_details_handicap_invalid)
    val focus = LocalFocusManager.current
    GroupedSection(header = stringResource(R.string.create_group_wanted_handicap)) {
        Column {
            FieldRow(
                value = state.handicapMin,
                onValueChange = viewModel::updateHandicapMin,
                placeholder = stringResource(R.string.group_no_handicap),
                label = stringResource(R.string.create_group_handicap_min_hint),
                keyboardType = KeyboardType.Decimal,
                error = if (state.minInvalid) invalid else null
            )
            ListDivider()
            FieldRow(
                value = state.handicapMax,
                onValueChange = viewModel::updateHandicapMax,
                placeholder = stringResource(R.string.group_no_handicap),
                label = stringResource(R.string.create_group_handicap_max_hint),
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                onImeAction = { focus.clearFocus() },
                error = if (state.maxInvalid) invalid else null
            )
        }
    }
}

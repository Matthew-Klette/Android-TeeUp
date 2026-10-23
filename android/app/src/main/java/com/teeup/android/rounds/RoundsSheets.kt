package com.teeup.android.rounds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.FieldRow
import com.teeup.android.design.Label
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme

private const val METERS_PER_KM = 1000.0

/** Start a Round: search for a course, and tapping a match moves straight on to the hole count
 *  (there's nothing else to confirm), then creates the solo round. Before anything's typed, the
 *  two closest courses sit at the top under "Nearby" when the phone knows where it is. */
@Composable
internal fun ColumnScope.StartSoloSheetContent(
    state: RoundsUiState,
    viewModel: RoundsViewModel,
    dismiss: (then: () -> Unit) -> Unit
) {
    var course by remember { mutableStateOf<Course?>(null) }
    val picked = course
    if (picked != null) {
        HoleCountContent(busy = state.startingSolo) { holes ->
            viewModel.startSoloRound(picked.id, holes)
            dismiss {}
        }
        return
    }
    val colors = TeeUpTheme.colors
    val focus = LocalFocusManager.current
    Label(stringResource(R.string.rounds_pick_course), TeeUpTheme.type.title3, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
    // Takes what's left of the sheet, so the title, field and Back always stay in view.
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .clip(RoundedCornerShape(TeeUpMetrics.controlRadius))
            .background(colors.background)
    ) {
        FieldRow(
            value = state.courseQuery,
            onValueChange = viewModel::updateCourseQuery,
            placeholder = stringResource(R.string.course_search_hint),
            leadingIcon = R.drawable.ic_glyph_magnifier,
            imeAction = ImeAction.Search,
            onImeAction = { focus.clearFocus() }
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (state.courseQuery.isBlank() && state.nearby.isNotEmpty()) {
                item(key = "nearby-header") { NearbyHeader() }
                items(state.nearby, key = { "nearby-${it.course.id}" }) { nearby ->
                    ListDivider()
                    ListRow(
                        title = nearby.course.name,
                        subtitle = stringResource(R.string.rounds_nearby_distance_format, nearby.distanceMeters / METERS_PER_KM),
                        icon = R.drawable.ic_glyph_target,
                        titleColor = colors.accent,
                        showChevron = true,
                        onClick = { focus.clearFocus(); course = nearby.course }
                    )
                }
            }
            itemsIndexed(state.suggestions, key = { _, it -> it.id }) { _, suggestion ->
                ListDivider()
                ListRow(
                    title = suggestion.name,
                    titleColor = colors.accent,
                    showChevron = true,
                    onClick = { focus.clearFocus(); course = suggestion }
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    TeeUpButton(stringResource(R.string.wireflow_back), onClick = { dismiss {} }, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
}

/** Labels the up-to-two closest courses, so they don't read as search results. */
@Composable
private fun NearbyHeader() {
    ListDivider()
    Label(
        stringResource(R.string.rounds_nearby).uppercase(),
        TeeUpTheme.type.eyebrow,
        color = TeeUpTheme.colors.secondaryLabel,
        maxLines = 1,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp).semantics { heading() }
    )
}

/** "How many holes?" with 9 and 18 as two big choices. */
@Composable
internal fun ColumnScope.HoleCountContent(busy: Boolean = false, onChosen: (Int) -> Unit) {
    Label(stringResource(R.string.rounds_choose_holes), TeeUpTheme.type.title3, modifier = Modifier.padding(bottom = 16.dp).semantics { heading() })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeeUpButton(stringResource(R.string.rounds_nine_holes), onClick = { onChosen(9) }, modifier = Modifier.weight(1f), kind = ButtonKind.Tinted, enabled = !busy)
        TeeUpButton(stringResource(R.string.rounds_eighteen_holes), onClick = { onChosen(18) }, modifier = Modifier.weight(1f), kind = ButtonKind.Tinted, enabled = !busy)
    }
}

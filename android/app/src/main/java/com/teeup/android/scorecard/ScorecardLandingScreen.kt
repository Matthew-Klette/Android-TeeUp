package com.teeup.android.scorecard

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.LargeTitle
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.skeleton
import com.teeup.android.nav.BottomNavTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScorecardLandingUiState(
    val isLoading: Boolean = true,
    /** The API's message (possibly blank) when the last load failed. */
    val loadError: String? = null,
    val items: List<LandingItem> = emptyList(),
    val courses: Map<String, Course> = emptyMap()
)

/** The Scorecard tab with no round open: every round with scoring started, so the tab isn't
 *  blank until a round is picked on My Rounds. */
class ScorecardLandingViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ScorecardLandingUiState())
    val state: StateFlow<ScorecardLandingUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val (items, courses) = withContext(Dispatchers.IO) {
                    val schedule = TeeUpApiClient.fetchSchedule()
                    val courses = try {
                        TeeUpApiClient.fetchCourses().associateBy { it.id }
                    } catch (e: Exception) {
                        emptyMap()
                    }
                    ScorecardLanding.items(schedule) to courses
                }
                _state.update { it.copy(isLoading = false, items = items, courses = courses) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.message.orEmpty(), items = emptyList()) }
            }
        }
    }
}

class ScorecardLandingActions(
    val openScorecard: (teeTimeId: String, holes: Int) -> Unit,
    val openSummary: (teeTimeId: String) -> Unit,
    val chooseRound: () -> Unit,
    val openTab: (BottomNavTab) -> Unit
)

@Composable
fun ScorecardLandingScreen(viewModel: ScorecardLandingViewModel, actions: ScorecardLandingActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = stringResource(R.string.scorecard_title),
        selectedTab = BottomNavTab.SCORECARD,
        onSelectTab = actions.openTab,
        header = { LargeTitle(stringResource(R.string.scorecard_title), subtitle = stringResource(R.string.scorecard_landing)) }
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> item(key = "skeleton", contentType = "skeleton") {
                Box(
                    Modifier
                        .padding(horizontal = TeeUpMetrics.gutter)
                        .padding(top = 52.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .skeleton(RoundedCornerShape(TeeUpMetrics.cardRadius))
                )
            }
            state.loadError != null -> item(key = "message") {
                StateMessage(
                    state.loadError!!.ifBlank { stringResource(R.string.scorecard_landing_load_failed) },
                    buttonLabel = stringResource(R.string.profile_retry),
                    onButton = viewModel::load
                )
            }
            state.items.isEmpty() -> item(key = "message") {
                StateMessage(
                    stringResource(R.string.scorecard_landing_empty),
                    buttonLabel = stringResource(R.string.rounds_browse),
                    onButton = actions.chooseRound
                )
            }
            else -> item(key = "list", contentType = "section") {
                GroupedSection(header = stringResource(R.string.scorecard_landing_heading)) {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) ListDivider()
                        ListRow(
                            title = state.courses[item.round.courseId]?.name ?: stringResource(R.string.rounds_unknown_course),
                            subtitle = formatTeeTime(item.round.dateTime),
                            trailing = { ProgressRing(item) },
                            onClick = {
                                if (item.isComplete) actions.openSummary(item.round.teeTimeId)
                                else actions.openScorecard(item.round.teeTimeId, item.totalHoles)
                            }
                        )
                    }
                }
            }
        }
        if (state.items.isNotEmpty()) {
            item(key = "choose", contentType = "section") {
                GroupedSection {
                    ListRow(stringResource(R.string.rounds_browse), icon = R.drawable.ic_glyph_calendar, onClick = actions.chooseRound)
                }
            }
        }
    }
}

/** "9/18" with a thin bar, green once every hole is in. */
@Composable
private fun ProgressRing(item: LandingItem) {
    val colors = TeeUpTheme.colors
    val fraction = if (item.totalHoles > 0) (item.holesScored.toFloat() / item.totalHoles).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.teeup.android.design.Label(
                "${item.holesScored}/${item.totalHoles}",
                TeeUpTheme.type.subheadline.copy(fontWeight = TeeUpTheme.type.headline.fontWeight),
                color = if (item.isComplete) colors.accent else colors.label,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(52.dp).height(4.dp).clip(CircleShape).background(colors.fill)) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(colors.accent))
        }
    }
}

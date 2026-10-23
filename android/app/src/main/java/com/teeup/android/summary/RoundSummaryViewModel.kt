package com.teeup.android.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.RoundSummaryActivity
import com.teeup.android.data.Course
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoundSummaryUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val round: ScheduledRound? = null,
    val course: Course? = null,
    val stats: SummaryStats? = null
)

sealed interface SummaryEvent {
    data class Banner(val notice: Notice) : SummaryEvent
    /** The round isn't there: say so and close. */
    data object Missing : SummaryEvent
    /** The round was deleted: back to My Rounds. */
    data object RoundDeleted : SummaryEvent
}

/** A finished (or History) round's totals and holes, with deleting a hole or the whole round. */
class RoundSummaryViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val teeTimeId: String? = savedState[RoundSummaryActivity.EXTRA_TEE_TIME_ID]
    private val _state = MutableStateFlow(RoundSummaryUiState())
    val state: StateFlow<RoundSummaryUiState> = _state.asStateFlow()
    private val _events = Channel<SummaryEvent>(Channel.BUFFERED)
    val events: Flow<SummaryEvent> = _events.receiveAsFlow()
    private var loadJob: Job? = null

    init {
        if (teeTimeId == null) viewModelScope.launch { _events.send(SummaryEvent.Missing) } else load()
    }

    fun load() {
        val id = teeTimeId ?: return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val (match, course) = withContext(Dispatchers.IO) {
                    val match = TeeUpApiClient.fetchSchedule().firstOrNull { it.teeTimeId == id }
                    val course = match?.let { round ->
                        try {
                            TeeUpApiClient.fetchCourses().firstOrNull { it.id == round.courseId }
                        } catch (e: Exception) {
                            null
                        } ?: MockCatalog.courseById(round.courseId)
                    }
                    match to course
                }
                if (match == null) {
                    _events.send(SummaryEvent.Missing)
                    return@launch
                }
                _state.update { it.copy(isLoading = false, round = match, course = course, stats = SummaryStats.from(match.round)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                _events.send(SummaryEvent.Banner(Notice(e.message, R.string.summary_load_failed, isError = true)))
            }
        }
    }

    fun deleteHole(holeNumber: Int) {
        val roundId = _state.value.stats?.roundId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { TeeUpApiClient.deleteScorecardEntry(roundId, holeNumber) }
                _events.send(SummaryEvent.Banner(Notice(null, R.string.summary_hole_deleted)))
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(SummaryEvent.Banner(Notice(e.message, R.string.summary_delete_failed_fallback, isError = true)))
            }
        }
    }

    /** The round is gone once this succeeds, so there's nothing to reload (unlike [deleteHole]). */
    fun deleteRound() {
        val roundId = _state.value.stats?.roundId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { TeeUpApiClient.deleteRound(roundId) }
                _events.send(SummaryEvent.Banner(Notice(null, R.string.summary_round_deleted)))
                _events.send(SummaryEvent.RoundDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(SummaryEvent.Banner(Notice(e.message, R.string.summary_delete_round_failed_fallback, isError = true)))
            }
        }
    }
}

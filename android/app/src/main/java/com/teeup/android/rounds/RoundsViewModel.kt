package com.teeup.android.rounds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.GeoPoint
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.selectRounds
import com.teeup.android.ui.CourseSearch
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

/** One round's card: everything it shows, worked out once per load or tab switch. */
data class RoundItem(
    val round: ScheduledRound,
    val course: Course?,
    val action: RoundAction,
    val holesScored: Int,
    val totalHoles: Int
)

data class RoundsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val hasLoaded: Boolean = false,
    val showHistory: Boolean = false,
    val items: List<RoundItem> = emptyList(),
    val courseQuery: String = "",
    val suggestions: List<Course> = emptyList(),
    /** Start a Round's "Nearby" courses, shown above the search while it's empty. */
    val nearby: List<NearbyCourse> = emptyList(),
    val startingSolo: Boolean = false
)

sealed interface RoundsEvent {
    data class Banner(val notice: Notice) : RoundsEvent
    data class OpenScorecard(val teeTimeId: String, val holes: Int?) : RoundsEvent
}

/** Rounds tab: the schedule (GET /api/rounds/schedule) split into upcoming and history, and
 *  starting a solo round on any course (POST a solo tee time, then score it). */
class RoundsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(RoundsUiState())
    val state: StateFlow<RoundsUiState> = _state.asStateFlow()
    private val _events = Channel<RoundsEvent>(Channel.BUFFERED)
    val events: Flow<RoundsEvent> = _events.receiveAsFlow()
    private val courseSearch = CourseSearch(viewModelScope)
    private var schedule: List<ScheduledRound> = emptyList()
    private var coursesById: Map<String, Course> = emptyMap()
    private var location: GeoPoint? = null
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { courseSearch.results.collect { results -> _state.update { it.copy(suggestions = results) } } }
    }

    /** Picks up scores just saved by Scorecard/RoundSummary without a stale list. */
    fun refresh() {
        if (schedule.isNotEmpty()) load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val (fetchedSchedule, courses) = withContext(Dispatchers.IO) {
                    val fetched = TeeUpApiClient.fetchSchedule()
                    val courses = try {
                        TeeUpApiClient.fetchCourses()
                    } catch (e: Exception) {
                        MockCatalog.courses
                    }
                    fetched to courses
                }
                schedule = fetchedSchedule
                coursesById = courses.associateBy { it.id }
                _state.update {
                    it.copy(isLoading = false, hasLoaded = true, items = buildItems(it.showHistory), nearby = nearbyCourses())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, hasLoaded = true, loadFailed = true, items = emptyList()) }
            }
        }
    }

    fun showHistory(history: Boolean) {
        if (_state.value.showHistory == history) return
        _state.update { it.copy(showHistory = history, items = buildItems(history)) }
    }

    /** A round with an unreadable date makes the whole tab empty, with a banner, as before. */
    private fun buildItems(history: Boolean): List<RoundItem> = try {
        val now = System.currentTimeMillis()
        selectRounds(schedule, history, now).map { round ->
            val (scored, total) = RoundAction.progress(round)
            RoundItem(
                round = round,
                course = coursesById[round.courseId] ?: MockCatalog.courseById(round.courseId),
                action = RoundAction.of(round, now),
                holesScored = scored,
                totalHoles = total
            )
        }
    } catch (e: IllegalArgumentException) {
        viewModelScope.launch { _events.send(RoundsEvent.Banner(Notice(e.message, R.string.rounds_date_error_fallback, isError = true))) }
        emptyList()
    }

    /** The phone's position, for Start a Round's Nearby courses. */
    fun updateLocation(position: GeoPoint) {
        location = position
        _state.update { it.copy(nearby = nearbyCourses()) }
    }

    private fun nearbyCourses(): List<NearbyCourse> = NearbyCourses.pick(coursesById.values.toList(), location)

    fun updateCourseQuery(text: String) {
        _state.update { it.copy(courseQuery = text) }
        courseSearch.search(text)
        // Don't leave the last search's matches under Nearby while the debounce catches up.
        if (text.isBlank()) courseSearch.clear()
    }

    fun resetCourseSearch() {
        _state.update { it.copy(courseQuery = "") }
        courseSearch.search("")
        courseSearch.clear()
    }

    /** Creates a solo tee time on [courseId] and opens its scorecard. */
    fun startSoloRound(courseId: String, holes: Int) {
        if (_state.value.startingSolo) return
        _state.update { it.copy(startingSolo = true) }
        viewModelScope.launch {
            try {
                val teeTime = withContext(Dispatchers.IO) { TeeUpApiClient.createSoloTeeTime(courseId, holes) }
                _events.send(RoundsEvent.OpenScorecard(teeTime.id, holes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(RoundsEvent.Banner(Notice(e.message, R.string.rounds_start_solo_failed, isError = true)))
            } finally {
                _state.update { it.copy(startingSolo = false) }
            }
        }
    }
}

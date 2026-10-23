package com.teeup.android.creategroup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.teeup.android.CreateGroupActivity
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatHandicapValue
import com.teeup.android.ui.CourseSearch
import com.teeup.android.ui.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_HOLES_INDEX = 1 // 18 holes

data class CreateGroupUiState(
    val isEdit: Boolean = false,
    val courseQuery: String = "",
    /** Set when a suggestion is tapped (or, editing, resolved from the group's course id);
     *  cleared as soon as the typed text no longer matches it. */
    val selectedCourse: Course? = null,
    val suggestions: List<Course> = emptyList(),
    val picked: PickedDateTime? = null,
    /** What's sent: the picked time in UTC, or the group's own timestamp when editing. */
    val pickedIso: String? = null,
    /** 0 = 9 holes, 1 = 18. */
    val holesIndex: Int = DEFAULT_HOLES_INDEX,
    val playersNeeded: Int = 1,
    val handicapMin: String = "",
    val handicapMax: String = "",
    val minInvalid: Boolean = false,
    val maxInvalid: Boolean = false,
    /** 0 = any pace, 1..3 = PaceOfPlay 0..2. */
    val paceIndex: Int = 0,
    val submitting: Boolean = false
)

sealed interface CreateGroupEvent {
    data class Banner(val notice: Notice) : CreateGroupEvent
    /** Saved: show [notice] and close the screen. */
    data class Done(val notice: Notice) : CreateGroupEvent
}

/**
 * EME-311 Create a Group (POST /api/teetimes/groups) and EME-321 Edit Group (PATCH), one form.
 * Course search is live (GET /api/courses?search=), debounced; in edit mode the course is
 * locked and only resolved to a name for display, since editing never sends it.
 */
class CreateGroupViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val editTeeTimeId: String? = savedState[CreateGroupActivity.EXTRA_EDIT_TEE_TIME_ID]
    private val _state = MutableStateFlow(initialState(savedState))
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()
    private val _events = Channel<CreateGroupEvent>(Channel.BUFFERED)
    val events: Flow<CreateGroupEvent> = _events.receiveAsFlow()
    private val courseSearch = CourseSearch(viewModelScope, skip = { it == _state.value.selectedCourse?.name })

    init {
        if (editTeeTimeId != null) {
            savedState.get<String>(CreateGroupActivity.EXTRA_EDIT_COURSE_ID)?.let(::resolveLockedCourse)
        } else {
            courseSearch.results.onEach { results -> _state.update { it.copy(suggestions = results) } }.launchIn(viewModelScope)
        }
    }

    private fun initialState(savedState: SavedStateHandle): CreateGroupUiState {
        if (editTeeTimeId == null) return CreateGroupUiState()
        val iso = savedState.get<String>(CreateGroupActivity.EXTRA_EDIT_DATE_TIME_ISO)
        // A malformed timestamp leaves the time unset; the "pick a date/time" check on save covers it.
        val picked = iso?.let { runCatching { PickedDateTime.fromIso(it) }.getOrNull() }
        return CreateGroupUiState(
            isEdit = true,
            picked = picked,
            pickedIso = iso.takeIf { picked != null },
            holesIndex = if (savedState.get<Int>(CreateGroupActivity.EXTRA_EDIT_HOLES) == 9) 0 else 1,
            playersNeeded = PlayersNeeded.clamp(savedState.get<Int>(CreateGroupActivity.EXTRA_EDIT_OPEN_SPOTS) ?: 1),
            handicapMin = savedState.get<Double>(CreateGroupActivity.EXTRA_EDIT_HANDICAP_MIN)?.let(::formatHandicapValue).orEmpty(),
            handicapMax = savedState.get<Double>(CreateGroupActivity.EXTRA_EDIT_HANDICAP_MAX)?.let(::formatHandicapValue).orEmpty(),
            paceIndex = savedState.get<Int>(CreateGroupActivity.EXTRA_EDIT_PACE)?.plus(1) ?: 0
        )
    }

    /** Shows the locked course's name; a failed lookup just leaves the field blank. */
    private fun resolveLockedCourse(courseId: String) {
        viewModelScope.launch {
            val course = withContext(Dispatchers.IO) {
                runCatching { TeeUpApiClient.fetchCourses().firstOrNull { it.id == courseId } }.getOrNull()
            }
            _state.update { it.copy(selectedCourse = course, courseQuery = course?.name.orEmpty()) }
        }
    }

    fun updateCourseQuery(text: String) {
        _state.update {
            it.copy(courseQuery = text, selectedCourse = it.selectedCourse?.takeIf { course -> course.name == text })
        }
        courseSearch.search(text)
    }

    fun selectCourse(course: Course) {
        _state.update { it.copy(selectedCourse = course, courseQuery = course.name, suggestions = emptyList()) }
        courseSearch.search(course.name)
    }

    fun pickDateTime(picked: PickedDateTime) = _state.update { it.copy(picked = picked, pickedIso = picked.toUtcIso()) }
    fun selectHoles(index: Int) = _state.update { it.copy(holesIndex = index) }
    fun changePlayers(delta: Int) = _state.update { it.copy(playersNeeded = PlayersNeeded.change(it.playersNeeded, delta)) }
    fun updateHandicapMin(text: String) = _state.update { it.copy(handicapMin = text, minInvalid = false) }
    fun updateHandicapMax(text: String) = _state.update { it.copy(handicapMax = text, maxInvalid = false) }
    fun selectPace(index: Int) = _state.update { it.copy(paceIndex = index) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val form = GroupForm(
            isEdit = editTeeTimeId != null,
            courseId = current.selectedCourse?.id,
            dateTimeIso = current.pickedIso,
            holes = if (current.holesIndex == 0) 9 else 18,
            openSpots = current.playersNeeded,
            handicapMinText = current.handicapMin,
            handicapMaxText = current.handicapMax,
            paceIndex = current.paceIndex
        )
        when (val result = GroupFormValidator.validate(form, System.currentTimeMillis())) {
            is GroupFormResult.Invalid -> reject(result.error)
            is GroupFormResult.Valid -> save(result.group)
        }
    }

    private fun reject(error: FormError) {
        when (error) {
            FormError.HANDICAP_MIN_INVALID -> _state.update { it.copy(minInvalid = true) }
            FormError.HANDICAP_MAX_INVALID -> _state.update { it.copy(maxInvalid = true) }
            else -> viewModelScope.launch {
                val message = when (error) {
                    FormError.COURSE_REQUIRED -> R.string.create_group_course_required
                    FormError.DATE_REQUIRED -> R.string.create_group_pick_date_time_error
                    FormError.DATE_IN_PAST -> R.string.create_group_date_time_in_past
                    else -> R.string.create_group_handicap_range_invalid
                }
                _events.send(CreateGroupEvent.Banner(Notice(null, message, isError = true)))
            }
        }
    }

    private fun save(group: ValidGroup) {
        val editId = editTeeTimeId
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (editId != null) {
                        TeeUpApiClient.editGroup(
                            teeTimeId = editId,
                            dateTimeIso = group.dateTimeIso,
                            holes = group.holes,
                            openSpots = group.openSpots,
                            wantedHandicapMin = group.handicapMin,
                            wantedHandicapMax = group.handicapMax,
                            wantedPace = group.pace
                        )
                    } else {
                        // Validation guarantees a course when creating.
                        TeeUpApiClient.createGroup(
                            courseId = requireNotNull(group.courseId),
                            dateTimeIso = group.dateTimeIso,
                            holes = group.holes,
                            openSpots = group.openSpots,
                            wantedHandicapMin = group.handicapMin,
                            wantedHandicapMax = group.handicapMax,
                            wantedPace = group.pace
                        )
                    }
                }
                _events.send(CreateGroupEvent.Done(Notice(null, if (editId != null) R.string.edit_group_saved else R.string.create_group_created)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fallback = if (editId != null) R.string.edit_group_failed_fallback else R.string.create_group_failed_fallback
                _events.send(CreateGroupEvent.Banner(Notice(e.message, fallback, isError = true)))
                _state.update { it.copy(submitting = false) }
            }
        }
    }
}

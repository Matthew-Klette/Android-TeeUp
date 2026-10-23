package com.teeup.android.profile

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatHandicapOrEmpty
import com.teeup.android.design.BackButton
import com.teeup.android.design.FieldRow
import com.teeup.android.design.Glyph
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.SegmentedControl
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.CourseSearch
import com.teeup.android.ui.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayingDetailsUiState(
    val load: ProfileLoad = ProfileLoad.Loading,
    val user: RegisteredUser? = null,
    val handicap: String = "",
    val courseQuery: String = "",
    /** Pre-filled from the profile's home course, or a tapped suggestion; cleared as soon as the
     *  typed text no longer matches it (and then saving clears the home course). */
    val selectedCourse: Course? = null,
    val suggestions: List<Course> = emptyList(),
    /** PaceOfPlay ordinal: 0 Relaxed, 1 Standard, 2 Brisk. */
    val pace: Int = 0,
    val saving: Boolean = false
)

/** Profile → Playing Details: handicap, home course and pace of play, with the same PATCH
 *  /api/profiles/me as Personal Details (display name carried through unchanged). */
class PlayingDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PlayingDetailsUiState())
    val state: StateFlow<PlayingDetailsUiState> = _state.asStateFlow()
    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()
    private val courseSearch = CourseSearch(viewModelScope, skip = { it == _state.value.selectedCourse?.name })

    init {
        load()
        viewModelScope.launch { courseSearch.results.collect { results -> _state.update { it.copy(suggestions = results) } } }
    }

    fun load() {
        _state.update { it.copy(load = ProfileLoad.Loading) }
        viewModelScope.launch {
            try {
                val (user, _) = loadRegisteredProfile() ?: run {
                    _state.update { it.copy(load = ProfileLoad.SignedOut) }
                    return@launch
                }
                val courses = withContext(Dispatchers.IO) { TeeUpApiClient.fetchCourses() }
                val home = courses.firstOrNull { it.id == user.homeCourseId }
                _state.update {
                    it.copy(
                        load = ProfileLoad.Loaded,
                        user = user,
                        handicap = formatHandicapOrEmpty(user.handicapIndex),
                        selectedCourse = home,
                        courseQuery = home?.name.orEmpty(),
                        pace = user.paceOfPlay.coerceIn(0, 2)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = ProfileLoad.Failed(e.message.orEmpty())) }
            }
        }
    }

    fun updateHandicap(text: String) = _state.update { it.copy(handicap = text) }
    fun selectPace(index: Int) = _state.update { it.copy(pace = index) }

    fun updateCourseQuery(text: String) {
        _state.update { it.copy(courseQuery = text, selectedCourse = it.selectedCourse?.takeIf { course -> course.name == text }) }
        courseSearch.search(text)
    }

    fun selectCourse(course: Course) {
        _state.update { it.copy(selectedCourse = course, courseQuery = course.name, suggestions = emptyList()) }
        courseSearch.search(course.name)
    }

    fun save() {
        val current = _state.value
        val existing = current.user ?: return
        if (current.saving) return
        viewModelScope.launch {
            val handicap = when (val entry = PlayingHandicap.parse(current.handicap)) {
                HandicapEntry.Invalid -> {
                    _events.send(EditEvent.Banner(Notice(null, R.string.playing_details_handicap_invalid, isError = true)))
                    return@launch
                }
                is HandicapEntry.Value -> entry.handicap
            }
            _state.update { it.copy(saving = true) }
            try {
                withContext(Dispatchers.IO) {
                    TeeUpApiClient.updateProfile(
                        displayName = existing.displayName,
                        handicapIndex = handicap,
                        homeCourseId = current.selectedCourse?.id,
                        paceOfPlay = current.pace,
                        profileComplete = true
                    )
                }
                _events.send(EditEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.send(EditEvent.Banner(Notice(e.message, R.string.profile_save_error_fallback, isError = true)))
            }
        }
    }
}

@Composable
fun PlayingDetailsScreen(viewModel: PlayingDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = TeeUpTheme.colors
    val focus = LocalFocusManager.current
    LargeTitleScreen(
        title = stringResource(R.string.playing_details_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) },
        bottomBar = if (state.load == ProfileLoad.Loaded) {
            {
                TeeUpButton(
                    stringResource(if (state.saving) R.string.playing_details_saving else R.string.playing_details_save),
                    onClick = { focus.clearFocus(); viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving
                )
            }
        } else null
    ) {
        profileLoadStates(state.load, onRetry = viewModel::load) {
            item(key = "handicap", contentType = "section") {
                GroupedSection(header = stringResource(R.string.register_handicap)) {
                    FieldRow(
                        value = state.handicap,
                        onValueChange = viewModel::updateHandicap,
                        placeholder = stringResource(R.string.register_handicap_hint),
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        onImeAction = { focus.clearFocus() }
                    )
                }
            }
            item(key = "course", contentType = "section") {
                GroupedSection(header = stringResource(R.string.register_home_course)) {
                    FieldRow(
                        value = state.courseQuery,
                        onValueChange = viewModel::updateCourseQuery,
                        placeholder = stringResource(R.string.course_search_hint),
                        leadingIcon = R.drawable.ic_glyph_magnifier,
                        imeAction = ImeAction.Search,
                        onImeAction = { focus.clearFocus() },
                        trailing = if (state.selectedCourse != null) {
                            { Glyph(R.drawable.ic_glyph_check, colors.accent, size = 20.dp) }
                        } else null
                    )
                    if (state.selectedCourse == null) {
                        state.suggestions.forEach { course ->
                            ListDivider()
                            ListRow(
                                course.name,
                                titleColor = colors.accent,
                                showChevron = false,
                                onClick = { viewModel.selectCourse(course); focus.clearFocus() }
                            )
                        }
                    }
                }
            }
            item(key = "pace", contentType = "section") {
                GroupedSection(header = stringResource(R.string.register_pace)) {
                    SegmentedControl(
                        options = stringArrayResource(R.array.pace_of_play_options).toList(),
                        selectedIndex = state.pace,
                        onSelect = viewModel::selectPace,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

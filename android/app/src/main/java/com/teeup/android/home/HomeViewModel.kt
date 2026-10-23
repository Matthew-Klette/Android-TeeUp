package com.teeup.android.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teeup.android.data.Course
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything Home shows. [visible] is the loaded list after [filters]: derived, never stored,
 *  and worked out once per state rather than on every read. */
data class HomeUiState(
    val filters: HomeFilters = HomeFilters(),
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    /** Null when the last load worked; the API's message (possibly blank) when it didn't. */
    val loadError: String? = null,
    val teeTimes: List<TeeTime> = emptyList(),
    val courses: Map<String, Course> = emptyMap()
) {
    val visible: List<TeeTime> by lazy(LazyThreadSafetyMode.NONE) {
        HomeFiltering.apply(teeTimes, courses, filters, HomeFiltering.isoDate(0), HomeFiltering.isoDate(1))
    }
}

/**
 * Loads joinable groups (or the user's own, with My groups on) and holds Home's filters. Only
 * real API data: a failure is a visible error with retry, never silently swapped for mock groups.
 * Survives rotation, so the list and filters don't reload or reset when the phone turns.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        val filters = _state.value.filters
        _state.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val (courses, teeTimes) = withContext(Dispatchers.IO) {
                    val courses = TeeUpApiClient.fetchCourses().associateBy { it.id }
                    val teeTimes = if (filters.mineOnly) {
                        TeeUpApiClient.fetchMyTeeTimes()
                    } else {
                        TeeUpApiClient.fetchTeeTimes(filters.maxHandicap, filters.pace, joinableOnly = true)
                    }
                    SyncStatus.recordSuccess(getApplication())
                    courses to teeTimes
                }
                _state.update { it.copy(isLoading = false, hasLoaded = true, teeTimes = teeTimes, courses = courses) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, hasLoaded = true, loadError = e.message.orEmpty(), teeTimes = emptyList()) }
            }
        }
    }

    /** Applies [transform] to the filters. Skill and My groups change what the server returns,
     *  so those reload; everything else just re-filters what's already here. */
    fun updateFilters(transform: (HomeFilters) -> HomeFilters) {
        val before = _state.value.filters
        val after = transform(before)
        if (after == before) return
        _state.update { it.copy(filters = after) }
        val refetch = after.mineOnly != before.mineOnly || after.maxHandicap != before.maxHandicap || after.pace != before.pace
        if (refetch) load()
    }

    /** Clears every refinement but keeps the search text and the My groups switch. */
    fun clearRefinements() = updateFilters { HomeFilters(search = it.search, mineOnly = it.mineOnly) }
}

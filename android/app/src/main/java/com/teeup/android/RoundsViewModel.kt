package com.teeup.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpRepository
import com.teeup.android.data.roundTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoundsState(val rounds: List<ScheduledRound> = emptyList(), val courses: Map<String, String> = emptyMap(),
                       val loading: Boolean = false, val error: String? = null)

class RoundsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(RoundsState())
    val state = mutableState.asStateFlow()

    fun load() {
        if (state.value.loading) return
        mutableState.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val rounds = TeeUpRepository.getRoundSchedule()
                // Validate server timestamps before rendering; malformed data shows retry.
                rounds.forEach { roundTimestamp(it.dateTime) }
                val courses = TeeUpRepository.getCourses().associate { it.id to it.name }
                mutableState.value = RoundsState(rounds, courses)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(loading = false, error = e.message ?: "Unavailable")
            }
        }
    }
}

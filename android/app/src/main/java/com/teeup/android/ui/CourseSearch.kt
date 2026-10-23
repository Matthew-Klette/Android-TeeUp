package com.teeup.android.ui

import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

private const val DEBOUNCE_MS = 250L

/**
 * Server-side course search as you type (GET /api/courses?search=), shared by every course
 * picker (Register, Playing Details, Create/Edit Group, Start a Round). Debounced, with a newer query
 * cancelling an older one in flight; a failed search just shows no suggestions. [skip] says
 * when text needs no search (e.g. it's the course already picked).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CourseSearch(scope: CoroutineScope, private val skip: (String) -> Boolean = { false }) {
    private val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<Course>>(emptyList())
    val results: StateFlow<List<Course>> = _results.asStateFlow()

    init {
        query.debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .mapLatest { text -> if (text.isBlank() || skip(text)) emptyList() else fetch(text) }
            .onEach { _results.value = it }
            .launchIn(scope)
    }

    fun search(text: String) {
        query.value = text
    }

    fun clear() {
        _results.value = emptyList()
    }

    private suspend fun fetch(text: String): List<Course> = withContext(Dispatchers.IO) {
        try {
            TeeUpApiClient.fetchCourses(search = text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}

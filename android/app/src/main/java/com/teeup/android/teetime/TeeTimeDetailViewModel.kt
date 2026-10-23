package com.teeup.android.teetime

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.TeeTimeDetailActivity
import com.teeup.android.data.Course
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.LocalIdentity
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeTime
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

/** One-off things the Activity does: show a banner, send the user to sign in, close the screen. */
sealed interface DetailEvent {
    data class Banner(val notice: Notice) : DetailEvent
    data object SignInRequired : DetailEvent
    data object Deleted : DetailEvent
}

/** A loaded tee time, plus what the screen derives from it (worked out once, here). */
data class LoadedDetail(
    val teeTime: TeeTime,
    val course: Course?,
    val requests: List<JoinRequest>,
    val presentation: TeeTimeDetailPresentation
)

data class TeeTimeDetailUiState(
    val isLoading: Boolean = true,
    val error: Notice? = null,
    /** False when there's nothing to retry (no tee time id was passed in). */
    val canRetry: Boolean = true,
    val detail: LoadedDetail? = null,
    val sendingRequest: Boolean = false
)

/**
 * Tee Time Detail / Join Request: loads the tee time, its course and join requests, and runs
 * Request to Join (POST /api/teetimes/{id}/joinrequests), approve/decline, withdraw, and the
 * host's cancel and delete. All network work is on [Dispatchers.IO].
 */
class TeeTimeDetailViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val teeTimeId: String? = savedState[TeeTimeDetailActivity.EXTRA_TEE_TIME_ID]
    private val _state = MutableStateFlow(
        if (teeTimeId == null) TeeTimeDetailUiState(isLoading = false, error = Notice(null, R.string.teetime_no_id), canRetry = false)
        else TeeTimeDetailUiState()
    )
    val state: StateFlow<TeeTimeDetailUiState> = _state.asStateFlow()
    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()
    private var loadJob: Job? = null

    /** Called on every resume, so an edit, cancel, delete or withdraw elsewhere shows up. While a
     *  tee time is already on screen it refreshes in place rather than blanking to a spinner. */
    fun load() {
        val id = teeTimeId ?: return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val (loaded, usedMockFallback) = withContext(Dispatchers.IO) { fetchDetail(id) }
                if (loaded == null) {
                    _state.update { it.copy(isLoading = false, detail = null, error = Notice(null, R.string.teetime_not_found)) }
                    return@launch
                }
                _state.update { it.copy(isLoading = false, detail = loaded, error = null) }
                if (usedMockFallback) send(Notice(null, R.string.teetime_mock_fallback_notice, isError = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, detail = null, error = Notice(e.message, R.string.teetime_load_failed)) }
            }
        }
    }

    /**
     * Falls back to the sample catalog only when a request actually fails, not when a successful
     * call simply doesn't include this id — that's a real "not found" (EME-319). Only a genuine
     * API round trip counts as a sync.
     */
    private fun fetchDetail(id: String): Pair<LoadedDetail?, Boolean> {
        var reachedApi = false
        var usedMockFallback = false
        val teeTime = try {
            TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == id }.also { reachedApi = true }
        } catch (e: Exception) {
            usedMockFallback = true
            null
        } ?: MockCatalog.teeTimeById(id) ?: return null to false
        val course = try {
            TeeUpApiClient.fetchCourses().firstOrNull { it.id == teeTime.courseId }
        } catch (e: Exception) {
            usedMockFallback = true
            null
        } ?: MockCatalog.courseById(teeTime.courseId)
        val requests = try {
            TeeUpApiClient.fetchJoinRequestsForTeeTime(id)
        } catch (e: Exception) {
            emptyList()
        }
        if (reachedApi) SyncStatus.recordSuccess(getApplication())
        return loadedDetail(teeTime, course, requests) to usedMockFallback
    }

    private fun loadedDetail(teeTime: TeeTime, course: Course?, requests: List<JoinRequest>) = LoadedDetail(
        teeTime, course, requests,
        TeeTimeDetailPresentation.from(teeTime, requests, LocalIdentity.cachedUserIdOrNull(getApplication()))
    )

    /** The tee time, course and requests straight from the API after a change; null if it's gone. */
    private fun refetch(id: String): LoadedDetail? {
        val requests = TeeUpApiClient.fetchJoinRequestsForTeeTime(id)
        val teeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == id } ?: return null
        val course = TeeUpApiClient.fetchCourses().firstOrNull { it.id == teeTime.courseId }
        return loadedDetail(teeTime, course, requests)
    }

    fun requestToJoin() {
        val id = teeTimeId ?: return
        if (_state.value.sendingRequest) return
        _state.update { it.copy(sendingRequest = true) }
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    val userId = LocalIdentity.ensureRegistered(getApplication())
                    TeeUpApiClient.createJoinRequest(id, userId)
                    refetch(id)
                }
                send(Notice(null, R.string.teetime_request_sent))
                if (refreshed != null) _state.update { it.copy(detail = refreshed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // No signed-in Firebase user: a friendly prompt, not the raw internal message.
                send(Notice(null, R.string.teetime_signin_again, isError = true))
                _events.send(DetailEvent.SignInRequired)
            } catch (e: Exception) {
                send(Notice(e.message, R.string.teetime_request_failed_fallback, isError = true))
            } finally {
                _state.update { it.copy(sendingRequest = false) }
            }
        }
    }

    /** Host approving or declining a guest (status is a [com.teeup.android.data.JoinRequestStatus]). */
    fun respond(joinRequestId: String, status: Int) {
        val id = teeTimeId ?: return
        runAction(R.string.teetime_update_failed_fallback) {
            val refreshed = withContext(Dispatchers.IO) {
                TeeUpApiClient.updateJoinRequestStatus(joinRequestId, status)
                refetch(id)
            }
            if (refreshed != null) _state.update { it.copy(detail = refreshed) }
            send(Notice(null, R.string.teetime_updated))
        }
    }

    fun withdraw(joinRequestId: String) = runAction(R.string.teetime_withdraw_failed_fallback) {
        withContext(Dispatchers.IO) { TeeUpApiClient.withdrawJoinRequest(joinRequestId) }
        send(Notice(null, R.string.teetime_request_withdrawn))
        load()
    }

    fun cancelGroup() {
        val id = teeTimeId ?: return
        runAction(R.string.teetime_cancel_failed_fallback) {
            withContext(Dispatchers.IO) { TeeUpApiClient.cancelGroup(id) }
            send(Notice(null, R.string.teetime_group_cancelled))
            load()
        }
    }

    fun deleteGroup() {
        val id = teeTimeId ?: return
        runAction(R.string.teetime_delete_failed_fallback) {
            withContext(Dispatchers.IO) { TeeUpApiClient.deleteGroup(id) }
            send(Notice(null, R.string.teetime_group_deleted))
            _events.send(DetailEvent.Deleted)
        }
    }

    /** Runs [block], turning a failure into an error banner with the API's message or [fallback]. */
    private fun runAction(@StringRes fallback: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(Notice(e.message, fallback, isError = true))
            }
        }
    }

    private suspend fun send(notice: Notice) = _events.send(DetailEvent.Banner(notice))
}

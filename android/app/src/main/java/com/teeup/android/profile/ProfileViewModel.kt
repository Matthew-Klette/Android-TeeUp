package com.teeup.android.profile

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.R
import com.teeup.android.data.LocalProfilePhoto
import com.teeup.android.data.RegisteredUser
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

data class ProfileUiState(
    val isLoading: Boolean = true,
    val signedOut: Boolean = false,
    /** Set when the last load failed: the API's message (possibly blank). */
    val loadError: String? = null,
    val user: RegisteredUser? = null,
    val homeCourseName: String? = null,
    /** Null when the schedule couldn't be read: shown as "…", not 0. */
    val roundsPlayed: Int? = null,
    val photo: ImageBitmap? = null,
    val savingPhoto: Boolean = false
)

/**
 * Profile & Settings header: name, handicap, home course, rounds played, and the profile photo
 * (kept on this device only, keyed by the signed-in uid — see LocalProfilePhoto). There's no
 * separate GET, so the idempotent POST /api/auth/register doubles as "fetch current profile",
 * as Personal/Playing Details do.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private val _events = Channel<Notice>(Channel.BUFFERED)
    val events: Flow<Notice> = _events.receiveAsFlow()
    private var loadJob: Job? = null

    /** Called on every resume, so edits made on the detail screens show up straight away. */
    fun load() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            _state.update { it.copy(isLoading = false, signedOut = true, photo = null) }
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, signedOut = false, loadError = null) }
        loadJob = viewModelScope.launch {
            // Re-read on every resume, so switching accounts never shows the last one's photo.
            refreshPhoto(firebaseUser.uid)
            try {
                val (user, homeCourse, rounds) = withContext(Dispatchers.IO) {
                    val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
                    val courses = try {
                        TeeUpApiClient.fetchCourses()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val rounds = try {
                        TeeUpApiClient.fetchSchedule().count { it.round != null }
                    } catch (e: Exception) {
                        null
                    }
                    Triple(user, courses.firstOrNull { it.id == user.homeCourseId }?.name, rounds)
                }
                _state.update { it.copy(isLoading = false, user = user, homeCourseName = homeCourse, roundsPlayed = rounds) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.message.orEmpty()) }
            }
        }
    }

    private suspend fun refreshPhoto(uid: String) {
        val photo = withContext(Dispatchers.IO) { LocalProfilePhoto.loadOrNull(getApplication(), uid)?.asImageBitmap() }
        _state.update { it.copy(photo = photo) }
    }

    fun savePhoto(imageUri: Uri) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        viewModelScope.launch {
            if (uid == null) {
                _events.send(Notice(null, R.string.profile_load_signed_out, isError = true))
                return@launch
            }
            _state.update { it.copy(savingPhoto = true) }
            _events.send(Notice(null, R.string.profile_photo_uploading))
            try {
                withContext(Dispatchers.IO) { LocalProfilePhoto.save(getApplication(), uid, imageUri) }
                refreshPhoto(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(Notice(e.message, R.string.profile_photo_upload_failed, isError = true))
            } finally {
                _state.update { it.copy(savingPhoto = false) }
            }
        }
    }
}

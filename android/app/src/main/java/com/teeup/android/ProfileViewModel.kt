package com.teeup.android

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.teeup.android.data.ApiException
import com.teeup.android.data.ProfileUpdateRequest
import com.teeup.android.data.TeeUpRepository
import com.teeup.android.data.UserProfile
import com.teeup.android.ui.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val saveSucceeded: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel : BaseViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    // Retained across rotation so a failed save can be retried.
    var pendingUpdate: ProfileUpdateRequest? = null
        private set

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (_state.value.isLoading || _state.value.isSaving) return

        pendingUpdate = null
        _state.value = _state.value.copy(
            isLoading = true,
            loadFailed = false,
            saveFailed = false,
            saveSucceeded = false,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val profile = TeeUpRepository.getProfile()
                _state.value = ProfileUiState(profile = profile)
                Log.d(tag, "Profile loaded")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Profile load failed: ${e.javaClass.simpleName}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    loadFailed = true,
                    errorMessage = (e as? ApiException)?.message
                )
            }
        }
    }

    fun saveProfile(request: ProfileUpdateRequest) {
        val current = _state.value
        if (current.profile == null || current.isLoading || current.isSaving) return

        pendingUpdate = request
        _state.value = current.copy(
            isSaving = true,
            loadFailed = false,
            saveFailed = false,
            saveSucceeded = false,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val saved = TeeUpRepository.updateProfile(request)
                pendingUpdate = null

                // Display the server response only after PATCH succeeds.
                _state.value = ProfileUiState(
                    profile = saved,
                    saveSucceeded = true
                )
                Log.d(tag, "Profile saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Profile save failed: ${e.javaClass.simpleName}")
                _state.value = _state.value.copy(
                    isSaving = false,
                    saveFailed = true,
                    errorMessage = (e as? ApiException)?.message
                )
            }
        }
    }

    fun retryLastAction() {
        val request = pendingUpdate
        if (_state.value.saveFailed && request != null) {
            saveProfile(request)
        } else {
            loadProfile()
        }
    }
}
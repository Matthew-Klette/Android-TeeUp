package com.teeup.android.profile

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.R
import com.teeup.android.data.NotificationPreferences
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.design.BackButton
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.Toggle
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

/** A switch saved on the server: what it shows, and whether its save is still in flight. */
data class ServerToggle(val on: Boolean = false, val saving: Boolean = false)

data class NotificationPreferencesUiState(
    val load: ProfileLoad = ProfileLoad.Loading,
    val joinUpdates: ServerToggle = ServerToggle(),
    val reminders: ServerToggle = ServerToggle(),
    /** Device-only: offline-sync reminders have no server field (see NotificationPreferences). */
    val syncReminders: Boolean = true
)

/**
 * Profile → Notification Preferences. Join request updates and tee time reminders persist
 * server-side (PATCH /api/profiles/me) and load on open, so a reinstall shows the real saved
 * state; a failed save flips the switch back with an error, so a flaky connection can't leave the
 * screen claiming a save that didn't happen.
 */
class NotificationPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(NotificationPreferencesUiState(syncReminders = NotificationPreferences.isSyncPendingEnabled(application)))
    val state: StateFlow<NotificationPreferencesUiState> = _state.asStateFlow()
    private val _events = Channel<Notice>(Channel.BUFFERED)
    val events: Flow<Notice> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            _state.update { it.copy(load = ProfileLoad.SignedOut) }
            return
        }
        _state.update { it.copy(load = ProfileLoad.Loading) }
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { TeeUpApiClient.fetchProfile() }
                _state.update {
                    it.copy(load = ProfileLoad.Loaded, joinUpdates = ServerToggle(user.joinRequestNotifications), reminders = ServerToggle(user.teeTimeReminders))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = ProfileLoad.Failed(e.message.orEmpty())) }
            }
        }
    }

    fun setJoinUpdates(enabled: Boolean) = saveServerToggle(
        enabled,
        read = { it.joinUpdates },
        write = { state, toggle -> state.copy(joinUpdates = toggle) }
    ) { TeeUpApiClient.updateNotificationPreference(joinRequestNotifications = enabled) }

    fun setReminders(enabled: Boolean) = saveServerToggle(
        enabled,
        read = { it.reminders },
        write = { state, toggle -> state.copy(reminders = toggle) }
    ) { TeeUpApiClient.updateNotificationPreference(teeTimeReminders = enabled) }

    fun setSyncReminders(enabled: Boolean) {
        NotificationPreferences.setSyncPendingEnabled(getApplication(), enabled)
        _state.update { it.copy(syncReminders = enabled) }
    }

    /** Shows [enabled] at once and saves it; on failure puts the switch back and says why. */
    private fun saveServerToggle(
        enabled: Boolean,
        read: (NotificationPreferencesUiState) -> ServerToggle,
        write: (NotificationPreferencesUiState, ServerToggle) -> NotificationPreferencesUiState,
        patch: () -> Unit
    ) {
        if (read(_state.value).saving) return
        _state.update { write(it, ServerToggle(on = enabled, saving = true)) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { patch() }
                _state.update { write(it, ServerToggle(on = enabled)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { write(it, ServerToggle(on = !enabled)) }
                _events.send(Notice(e.message, R.string.profile_save_error_fallback, isError = true))
            }
        }
    }
}

@Composable
fun NotificationPreferencesScreen(viewModel: NotificationPreferencesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = stringResource(R.string.notification_prefs_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) }
    ) {
        profileLoadStates(state.load, onRetry = viewModel::load) {
            item(key = "toggles", contentType = "section") {
                // The explanation is long, so it's the section's footnote rather than a subtitle.
                GroupedSection(footer = stringResource(R.string.notification_prefs_subtitle)) {
                    ListRow(
                        stringResource(R.string.notification_pref_join_updates),
                        icon = R.drawable.ic_glyph_person_badge,
                        trailing = { Toggle(state.joinUpdates.on, viewModel::setJoinUpdates, enabled = !state.joinUpdates.saving) }
                    )
                    ListDivider(com.teeup.android.design.IconRowDividerInset)
                    ListRow(
                        stringResource(R.string.notification_category_reminder),
                        icon = R.drawable.ic_glyph_clock,
                        trailing = { Toggle(state.reminders.on, viewModel::setReminders, enabled = !state.reminders.saving) }
                    )
                    ListDivider(com.teeup.android.design.IconRowDividerInset)
                    ListRow(
                        stringResource(R.string.notification_category_sync),
                        icon = R.drawable.ic_glyph_sync,
                        trailing = { Toggle(state.syncReminders, viewModel::setSyncReminders) }
                    )
                }
            }
        }
    }
}

package com.teeup.android.profile

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.ApiException
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.design.BackButton
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListRow
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme
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

/** Profile → Privacy & Data (POPIA): what's collected, how it's used, the user's controls, and
 *  Sign Out. */
@Composable
fun PrivacyDataScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    LargeTitleScreen(
        title = stringResource(R.string.privacy_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) }
    ) {
        listOf(
            R.string.privacy_section_collect to R.string.privacy_body_collect,
            R.string.privacy_section_usage to R.string.privacy_body_usage,
            R.string.privacy_section_controls to R.string.privacy_body_controls
        ).forEach { (header, body) ->
            item(key = header, contentType = "section") {
                GroupedSection(header = stringResource(header)) {
                    Label(stringResource(body), TeeUpTheme.type.body, modifier = Modifier.padding(16.dp))
                }
            }
        }
        item(key = "signOut", contentType = "section") {
            GroupedSection {
                ListRow(stringResource(R.string.privacy_sign_out), titleColor = TeeUpTheme.colors.destructive, showChevron = false, onClick = onSignOut)
            }
        }
    }
}

data class OfflineSyncUiState(
    /** Null: nothing has loaded from the API on this device yet. */
    val lastSynced: String? = null,
    val connection: ConnectionKind = ConnectionKind.NONE,
    val checking: Boolean = false
)

/**
 * Profile → Offline Data & Sync Status. There's no offline cache or queue in this app (see
 * SyncStatus), so this reports what's real — the last successful sync and the current
 * connection — rather than a fabricated "N rounds pending" counter.
 */
class OfflineSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(OfflineSyncUiState())
    val state: StateFlow<OfflineSyncUiState> = _state.asStateFlow()
    private val _events = Channel<Notice>(Channel.BUFFERED)
    val events: Flow<Notice> = _events.receiveAsFlow()

    /** [context] formats the relative time in the app's language. */
    fun refresh(context: Context) {
        val app = getApplication<Application>()
        val last = SyncStatus.lastSyncMillisOrNull(app)?.let { SyncStatus.formatRelative(context, it) }
        _state.update { it.copy(lastSynced = last, connection = currentConnection(app)) }
    }

    /** A real, lightweight call against the live API — this is what "reachable" means here. */
    fun checkNow(context: Context) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val notice = try {
                withContext(Dispatchers.IO) {
                    TeeUpApiClient.fetchCourses()
                    SyncStatus.recordSuccess(getApplication())
                }
                Notice(null, R.string.offline_sync_reachable)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                Notice(e.message, R.string.offline_sync_request_failed_fallback, isError = true)
            } catch (e: Exception) {
                Notice(null, R.string.offline_sync_unreachable, isError = true)
            }
            _state.update { it.copy(checking = false) }
            refresh(context)
            _events.send(notice)
        }
    }

    private fun currentConnection(context: Context): ConnectionKind {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        return ConnectionKind.of(
            hasNetwork = capabilities != null,
            internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            ethernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        )
    }
}

@Composable
fun OfflineSyncScreen(viewModel: OfflineSyncViewModel, onBack: () -> Unit, onCheckNow: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = stringResource(R.string.offline_sync_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) },
        bottomBar = {
            TeeUpButton(
                stringResource(if (state.checking) R.string.offline_sync_checking else R.string.offline_sync_check_now),
                onClick = onCheckNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.checking
            )
        }
    ) {
        item(key = "status", contentType = "section") {
            GroupedSection(header = stringResource(R.string.offline_sync_last_synced_label)) {
                ListRow(state.lastSynced ?: stringResource(R.string.offline_sync_never), icon = R.drawable.ic_glyph_sync)
            }
        }
        item(key = "connection", contentType = "section") {
            GroupedSection(header = stringResource(R.string.offline_sync_connection_label), footer = stringResource(R.string.offline_sync_no_cache_notice)) {
                ListRow(
                    stringResource(
                        when (state.connection) {
                            ConnectionKind.NONE -> R.string.offline_sync_no_connection
                            ConnectionKind.WIFI -> R.string.offline_sync_connected_wifi
                            ConnectionKind.MOBILE -> R.string.offline_sync_connected_mobile
                            ConnectionKind.ETHERNET -> R.string.offline_sync_connected_ethernet
                            ConnectionKind.OTHER -> R.string.offline_sync_connected_generic
                        }
                    ),
                    icon = R.drawable.ic_glyph_globe,
                    iconTint = if (state.connection == ConnectionKind.NONE) TeeUpTheme.colors.destructive else TeeUpTheme.colors.accent
                )
            }
        }
    }
}

package com.teeup.android.partners

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.Partner
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatHandicap
import com.teeup.android.data.paceLabel
import com.teeup.android.design.Avatar
import com.teeup.android.design.BackButton
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.SearchField
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleManager
import com.teeup.android.ui.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AVATAR = 36.dp

data class PartnersUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val partners: List<Partner> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Partner> = emptyList(),
    /** User ids with an add/remove call in flight, so their buttons can't be double-tapped. */
    val busy: Set<String> = emptySet()
)

/**
 * Playing Partners, the "private groups" feature from the Part 1 design: golfers a user plays with
 * regularly. A host can mark a group partners-only so only these golfers see and can join it, and
 * each gets a push invite when it's created. Backed by GET/POST/DELETE /api/friends and
 * GET /api/users/search.
 */
class PartnersViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "PartnersViewModel"
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val _state = MutableStateFlow(PartnersUiState())
    val state: StateFlow<PartnersUiState> = _state.asStateFlow()
    private val _events = Channel<Notice>(Channel.BUFFERED)
    val events: Flow<Notice> = _events.receiveAsFlow()
    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val partners = withContext(Dispatchers.IO) { TeeUpApiClient.fetchPartners() }
                Log.d(TAG, "Loaded ${partners.size} partner(s)")
                _state.update { it.copy(isLoading = false, partners = partners) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Partners load failed: ${e.message}")
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    /** Debounced search; under 2 characters clears the results without a request. */
    fun updateQuery(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        if (text.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            try {
                val results = withContext(Dispatchers.IO) { TeeUpApiClient.searchGolfers(text) }
                _state.update { it.copy(results = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList(), searching = false) }
                _events.send(Notice(e.message, R.string.partners_action_failed, isError = true))
            }
        }
    }

    fun add(golfer: Partner) = mutate(golfer.userId) {
        val added = withContext(Dispatchers.IO) { TeeUpApiClient.addPartner(golfer.userId) }
        Log.i(TAG, "Partner added: ${golfer.userId}")
        _state.update { state ->
            state.copy(
                partners = (state.partners.filterNot { it.userId == added.userId } + added).sortedBy { it.displayName.lowercase() },
                results = state.results.map { if (it.userId == golfer.userId) it.copy(isPartner = true) else it }
            )
        }
        _events.send(Notice(text(R.string.partners_added, golfer.displayName), R.string.partners_added))
    }

    fun remove(partner: Partner) = mutate(partner.userId) {
        withContext(Dispatchers.IO) { TeeUpApiClient.removePartner(partner.userId) }
        Log.i(TAG, "Partner removed: ${partner.userId}")
        _state.update { state ->
            state.copy(
                partners = state.partners.filterNot { it.userId == partner.userId },
                results = state.results.map { if (it.userId == partner.userId) it.copy(isPartner = false) else it }
            )
        }
        _events.send(Notice(text(R.string.partners_removed, partner.displayName), R.string.partners_removed))
    }

    private fun mutate(userId: String, block: suspend () -> Unit) {
        if (userId in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + userId) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(Notice(e.message, R.string.partners_action_failed, isError = true))
            } finally {
                _state.update { it.copy(busy = it.busy - userId) }
            }
        }
    }

    /** Formats in the in-app language (the Application context alone isn't locale-wrapped). */
    private fun text(res: Int, arg: String) = LocaleManager.wrap(getApplication()).getString(res, arg)
}

@Composable
fun PartnersScreen(viewModel: PartnersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LargeTitleScreen(
        title = stringResource(R.string.partners_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) }
    ) {
        item(key = "saved", contentType = "section") {
            GroupedSection(header = stringResource(R.string.partners_section_saved), footer = stringResource(R.string.partners_section_saved_footer)) {
                when {
                    state.isLoading && state.partners.isEmpty() -> ListRow(stringResource(R.string.offline_sync_checking))
                    state.loadFailed -> ListRow(
                        stringResource(R.string.partners_load_failed),
                        value = stringResource(R.string.profile_retry),
                        onClick = viewModel::load
                    )
                    state.partners.isEmpty() -> Label(
                        stringResource(R.string.partners_empty),
                        TeeUpTheme.type.subheadline,
                        color = TeeUpTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> state.partners.forEachIndexed { index, partner ->
                        if (index > 0) ListDivider(16.dp + AVATAR + 14.dp)
                        GolferRow(partner, context, busy = partner.userId in state.busy, onRemove = { viewModel.remove(partner) })
                    }
                }
            }
        }
        item(key = "search", contentType = "search") {
            GroupedSection(header = stringResource(R.string.partners_section_find)) {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = stringResource(R.string.partners_search_hint),
                    clearDescription = stringResource(R.string.home_clear_search),
                    modifier = Modifier.padding(12.dp)
                )
                val trimmed = state.query.trim()
                when {
                    trimmed.isEmpty() -> Unit
                    trimmed.length < 2 -> Hint(stringResource(R.string.partners_search_min))
                    state.searching -> Hint(stringResource(R.string.offline_sync_checking))
                    state.results.isEmpty() -> Hint(stringResource(R.string.partners_search_none, trimmed))
                    else -> state.results.forEach { golfer ->
                        ListDivider(16.dp + AVATAR + 14.dp)
                        GolferRow(golfer, context, busy = golfer.userId in state.busy, onAdd = { viewModel.add(golfer) })
                    }
                }
            }
        }
        if (state.loadFailed && state.partners.isEmpty() && state.query.isEmpty()) {
            item(key = "error") {
                StateMessage(stringResource(R.string.partners_load_failed), icon = R.drawable.ic_glyph_people)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Label(text, TeeUpTheme.type.footnote, color = TeeUpTheme.colors.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
}

/** A golfer with handicap and pace. Partners get Remove; search results get Add (or a Partner tag). */
@Composable
private fun GolferRow(
    golfer: Partner,
    context: android.content.Context,
    busy: Boolean,
    onRemove: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null
) {
    ListRow(
        title = golfer.displayName,
        subtitle = stringResource(R.string.partners_subtitle, formatHandicap(golfer.handicapIndex), paceLabel(context, golfer.paceOfPlay)),
        leading = { Avatar(golfer.displayName, golfer.userId, size = AVATAR) },
        trailing = {
            when {
                onRemove != null -> TeeUpButton(stringResource(R.string.partners_remove), onClick = onRemove, kind = ButtonKind.DestructiveTinted, compact = true, enabled = !busy)
                golfer.isPartner -> Label(stringResource(R.string.partners_added_badge), TeeUpTheme.type.footnote, color = TeeUpTheme.colors.accent)
                onAdd != null -> TeeUpButton(stringResource(R.string.partners_add), onClick = onAdd, kind = ButtonKind.Tinted, compact = true, enabled = !busy)
            }
        }
    )
}

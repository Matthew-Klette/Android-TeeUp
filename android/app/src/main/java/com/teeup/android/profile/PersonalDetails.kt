package com.teeup.android.profile

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.R
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.design.BackButton
import com.teeup.android.design.FieldRow
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListRow
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.skeleton
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

/** Loading, signed out, or the API's message (possibly blank) when the profile couldn't load. */
sealed interface ProfileLoad {
    data object Loading : ProfileLoad
    data object SignedOut : ProfileLoad
    data class Failed(val message: String) : ProfileLoad
    data object Loaded : ProfileLoad
}

/** Saved: the screen closes. Or a banner. */
sealed interface EditEvent {
    data class Banner(val notice: Notice) : EditEvent
    data object Saved : EditEvent
}

/** There's no separate GET for the profile: the idempotent POST /api/auth/register returns the
 *  current one (see RegisteredUser's doc comment). Null when nobody is signed in. */
internal suspend fun loadRegisteredProfile(): Pair<RegisteredUser, String?>? {
    val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return null
    val user = withContext(Dispatchers.IO) { TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer") }
    return user to firebaseUser.email
}

data class PersonalDetailsUiState(
    val load: ProfileLoad = ProfileLoad.Loading,
    val user: RegisteredUser? = null,
    val email: String? = null,
    val name: String = "",
    val saving: Boolean = false
)

/** Profile → Personal Details: only the display name is edited (PATCH /api/profiles/me); the
 *  other fields are carried through unchanged on save. */
class PersonalDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PersonalDetailsUiState())
    val state: StateFlow<PersonalDetailsUiState> = _state.asStateFlow()
    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(load = ProfileLoad.Loading) }
        viewModelScope.launch {
            try {
                val (user, email) = loadRegisteredProfile() ?: run {
                    _state.update { it.copy(load = ProfileLoad.SignedOut) }
                    return@launch
                }
                _state.update { it.copy(load = ProfileLoad.Loaded, user = user, email = email, name = user.displayName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(load = ProfileLoad.Failed(e.message.orEmpty())) }
            }
        }
    }

    fun updateName(text: String) = _state.update { it.copy(name = text) }

    fun save() {
        val current = _state.value
        val existing = current.user ?: return
        if (current.saving) return
        val name = DisplayName.validOrNull(current.name)
        viewModelScope.launch {
            if (name == null) {
                _events.send(EditEvent.Banner(Notice(null, R.string.personal_details_name_required, isError = true)))
                return@launch
            }
            _state.update { it.copy(saving = true) }
            try {
                withContext(Dispatchers.IO) {
                    TeeUpApiClient.updateProfile(
                        displayName = name,
                        handicapIndex = existing.handicapIndex,
                        homeCourseId = existing.homeCourseId,
                        paceOfPlay = existing.paceOfPlay,
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
fun PersonalDetailsScreen(viewModel: PersonalDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = stringResource(R.string.personal_details_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) },
        bottomBar = if (state.load == ProfileLoad.Loaded) {
            {
                TeeUpButton(
                    stringResource(if (state.saving) R.string.personal_details_saving else R.string.personal_details_save),
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving
                )
            }
        } else null
    ) {
        profileLoadStates(state.load, onRetry = viewModel::load) {
            item(key = "name", contentType = "section") {
                GroupedSection(header = stringResource(R.string.personal_details_display_name)) {
                    FieldRow(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        placeholder = stringResource(R.string.personal_details_display_name),
                        imeAction = ImeAction.Done
                    )
                }
            }
            item(key = "email", contentType = "section") {
                GroupedSection(header = stringResource(R.string.personal_details_email_label)) {
                    val email = state.email
                    ListRow(
                        email ?: stringResource(R.string.personal_details_email_none),
                        titleColor = if (email == null) TeeUpTheme.colors.secondaryLabel else TeeUpTheme.colors.label
                    )
                }
            }
        }
    }
}

/** The loading, signed-out and failed states every profile screen shares; [loaded] otherwise. */
internal fun LazyListScope.profileLoadStates(
    load: ProfileLoad,
    onRetry: () -> Unit,
    loaded: LazyListScope.() -> Unit
) {
    when (load) {
        ProfileLoad.Loading -> item(key = "loading", contentType = "skeleton") { SectionSkeleton() }
        ProfileLoad.SignedOut -> item(key = "message") { StateMessage(stringResource(R.string.profile_load_signed_out), icon = R.drawable.ic_glyph_person) }
        is ProfileLoad.Failed -> item(key = "message") {
            StateMessage(
                load.message.ifBlank { stringResource(R.string.profile_load_error_fallback) },
                buttonLabel = stringResource(R.string.profile_retry),
                onButton = onRetry,
                icon = R.drawable.ic_glyph_person
            )
        }
        ProfileLoad.Loaded -> loaded()
    }
}

@Composable
internal fun SectionSkeleton() {
    Box(
        Modifier
            .padding(horizontal = TeeUpMetrics.gutter)
            .padding(top = 26.dp)
            .fillMaxWidth()
            .height(140.dp)
            .skeleton(RoundedCornerShape(TeeUpMetrics.cardRadius))
    )
}

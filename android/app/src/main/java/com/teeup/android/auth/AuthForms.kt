package com.teeup.android.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.design.BackButton
import com.teeup.android.design.FieldRow
import com.teeup.android.design.Glyph
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.LargeTitle
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.SegmentedControl
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.Toggle

@Stable
class EmailAuthUiState(signUp: Boolean, awaitingBackendRetry: Boolean) {
    var signUp by mutableStateOf(signUp)
    /** Firebase auth already worked; only our own backend registration is left to retry, so
     *  the fields and the mode no longer apply. */
    var awaitingBackendRetry by mutableStateOf(awaitingBackendRetry)
    var busy by mutableStateOf(false)
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
}

/** Firebase email/password sign-in and sign-up as one form; the link at the foot switches mode. */
@Composable
fun EmailAuthScreen(state: EmailAuthUiState, onBack: () -> Unit, onSubmit: () -> Unit) {
    val focus = LocalFocusManager.current
    val title = stringResource(if (state.signUp) R.string.email_auth_title_signup else R.string.email_auth_title_signin)
    val editable = !state.awaitingBackendRetry && !state.busy
    val submitLabel = stringResource(
        when {
            state.awaitingBackendRetry && state.busy -> R.string.email_auth_progress_retry
            state.awaitingBackendRetry -> R.string.email_auth_retry
            state.busy && state.signUp -> R.string.email_auth_progress_signup
            state.busy -> R.string.email_auth_progress_signin
            state.signUp -> R.string.email_auth_submit_signup
            else -> R.string.email_auth_submit_signin
        }
    )
    LargeTitleScreen(
        title = title,
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) },
        bottomBar = {
            TeeUpButton(submitLabel, onClick = { focus.clearFocus(); onSubmit() }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
        }
    ) {
        item(key = "fields", contentType = "section") {
            GroupedSection {
                FieldRow(
                    value = state.email,
                    onValueChange = { state.email = it },
                    placeholder = stringResource(R.string.email_auth_email_hint),
                    label = stringResource(R.string.email_auth_email_label),
                    keyboardType = KeyboardType.Email,
                    enabled = editable
                )
                ListDivider()
                FieldRow(
                    value = state.password,
                    onValueChange = { state.password = it },
                    placeholder = stringResource(R.string.email_auth_password_hint),
                    label = stringResource(R.string.email_auth_password_label),
                    keyboardType = KeyboardType.Password,
                    imeAction = if (state.signUp) ImeAction.Next else ImeAction.Done,
                    onImeAction = if (state.signUp) null else ({ focus.clearFocus(); onSubmit() }),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = editable
                )
                if (state.signUp) {
                    ListDivider()
                    FieldRow(
                        value = state.confirm,
                        onValueChange = { state.confirm = it },
                        placeholder = stringResource(R.string.email_auth_password_hint),
                        label = stringResource(R.string.email_auth_confirm_password_label),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = { focus.clearFocus(); onSubmit() },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = editable
                    )
                }
            }
        }
        if (!state.awaitingBackendRetry) {
            item(key = "toggle", contentType = "link") {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Switching mode mid-request would mismatch the call in flight, so it's locked meanwhile.
                    TextLink(
                        stringResource(if (state.signUp) R.string.email_auth_toggle_to_signin else R.string.email_auth_toggle_to_signup),
                        onClick = { state.signUp = !state.signUp },
                        enabled = !state.busy
                    )
                }
            }
        }
    }
}

@Stable
class RegisterUiState(initialName: String) {
    var name by mutableStateOf(initialName)
    var handicap by mutableStateOf("")
    var courseQuery by mutableStateOf("")
    /** Set when a suggestion is tapped; cleared once the typed text no longer matches it, so an
     *  empty or edited field means "no home course". */
    var selectedCourse by mutableStateOf<Course?>(null)
    var suggestions by mutableStateOf<List<Course>>(emptyList())
    var pace by mutableIntStateOf(PACE_STANDARD)
    var saving by mutableStateOf(false)

    companion object {
        const val PACE_STANDARD = 1
    }
}

class RegisterActions(
    val updateCourseQuery: (String) -> Unit,
    val selectCourse: (Course) -> Unit,
    val submit: () -> Unit
)

/** Profile setup after a new user's first sign-in: name, handicap, home course and pace. */
@Composable
fun RegisterScreen(state: RegisterUiState, actions: RegisterActions) {
    val colors = TeeUpTheme.colors
    val focus = LocalFocusManager.current
    val title = stringResource(R.string.register_title)
    LargeTitleScreen(
        title = title,
        header = { LargeTitle(title, subtitle = stringResource(R.string.register_subtitle)) },
        bottomBar = {
            TeeUpButton(
                stringResource(if (state.saving) R.string.register_saving else R.string.register_continue),
                onClick = { focus.clearFocus(); actions.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving
            )
        }
    ) {
        item(key = "name", contentType = "section") {
            GroupedSection(header = stringResource(R.string.register_display_name)) {
                FieldRow(
                    value = state.name,
                    onValueChange = { state.name = it },
                    placeholder = stringResource(R.string.register_display_name_hint)
                )
            }
        }
        item(key = "handicap", contentType = "section") {
            GroupedSection(header = stringResource(R.string.register_handicap)) {
                FieldRow(
                    value = state.handicap,
                    onValueChange = { state.handicap = it },
                    placeholder = stringResource(R.string.register_handicap_hint),
                    keyboardType = KeyboardType.Decimal
                )
            }
        }
        item(key = "course", contentType = "section") {
            GroupedSection(header = stringResource(R.string.register_home_course)) {
                FieldRow(
                    value = state.courseQuery,
                    onValueChange = actions.updateCourseQuery,
                    placeholder = stringResource(R.string.course_search_hint),
                    leadingIcon = R.drawable.ic_glyph_magnifier,
                    imeAction = ImeAction.Search,
                    onImeAction = { focus.clearFocus() },
                    trailing = if (state.selectedCourse != null) {
                        { Glyph(R.drawable.ic_glyph_check, colors.accent, size = 20.dp) }
                    } else null
                )
                if (state.selectedCourse == null) {
                    state.suggestions.forEach { course ->
                        ListDivider()
                        ListRow(course.name, titleColor = colors.accent, showChevron = false, onClick = { actions.selectCourse(course); focus.clearFocus() })
                    }
                }
            }
        }
        item(key = "pace", contentType = "section") {
            GroupedSection(header = stringResource(R.string.register_pace)) {
                SegmentedControl(
                    options = stringArrayResource(R.array.pace_of_play_options).toList(),
                    selectedIndex = state.pace,
                    onSelect = { state.pace = it },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Stable
class BiometricLoginUiState(initialStatus: String) {
    var status by mutableStateOf(initialStatus)
    var checked by mutableStateOf(false)
    var enabled by mutableStateOf(false)
}

/** Profile → Biometric Login: one switch, which only turns on after a live prompt passes. */
@Composable
fun BiometricLoginScreen(state: BiometricLoginUiState, onBack: () -> Unit, onToggle: (Boolean) -> Unit) {
    LargeTitleScreen(
        title = stringResource(R.string.biometric_title),
        navigation = { BackButton(onBack, stringResource(R.string.detail_back)) }
    ) {
        item(key = "toggle", contentType = "section") {
            // What the switch does, then whether this device can do it.
            GroupedSection(footer = stringResource(R.string.biometric_enforcement_notice) + "\n\n" + state.status) {
                ListRow(
                    stringResource(R.string.biometric_toggle_label),
                    icon = R.drawable.ic_glyph_lock,
                    trailing = { Toggle(state.checked, onToggle, enabled = state.enabled) }
                )
            }
        }
    }
}

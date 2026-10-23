package com.teeup.android.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.formatHandicapValue
import com.teeup.android.design.Avatar
import com.teeup.android.design.Glyph
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.IconRowDividerInset
import com.teeup.android.design.Stat
import com.teeup.android.design.StatStrip
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable
import com.teeup.android.design.skeleton
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleManager

/** Where each settings row goes; the Activity owns the Intents. */
enum class ProfileDestination { PERSONAL, PLAYING, NOTIFICATIONS, BIOMETRIC, OFFLINE_SYNC, PRIVACY }

class ProfileActions(
    val open: (ProfileDestination) -> Unit,
    val takePhoto: () -> Unit,
    val choosePhoto: () -> Unit,
    val setLanguage: (tag: String) -> Unit,
    val signOut: () -> Unit,
    val openTab: (BottomNavTab) -> Unit
)

private enum class ProfileSheet { PHOTO, LANGUAGE }

private val PHOTO_SIZE = 96.dp

/**
 * Profile & Settings, laid out like the Settings app: the photo, name and key numbers up top,
 * then grouped rows into each settings screen, Language and Sign Out. Tapping the photo offers
 * Take Photo / Choose from Gallery in a sheet.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, currentLanguageTag: String, actions: ProfileActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ProfileSheet?>(null) }
    val title = stringResource(R.string.profile_title)
    LargeTitleScreen(
        title = title,
        selectedTab = BottomNavTab.PROFILE,
        onSelectTab = actions.openTab,
        overlay = {
            sheet?.let { current ->
                TeeUpSheet(onDismissed = { sheet = null }) { dismiss ->
                    when (current) {
                        ProfileSheet.PHOTO -> {
                            SheetTitle(stringResource(R.string.profile_photo_change))
                            SheetOptions(
                                listOf(stringResource(R.string.profile_photo_take), stringResource(R.string.profile_photo_gallery)),
                                selected = null
                            ) { index -> dismiss { if (index == 0) actions.takePhoto() else actions.choosePhoto() } }
                        }
                        ProfileSheet.LANGUAGE -> {
                            SheetTitle(stringResource(R.string.profile_language_dialog_title))
                            val languages = LocaleManager.SUPPORTED_LANGUAGES
                            SheetOptions(languages.map { it.label }, selected = languages.indexOfFirst { it.tag == currentLanguageTag }) { index ->
                                val language = languages[index]
                                // Choosing the current language does nothing, as before.
                                if (language.tag == currentLanguageTag) dismiss {} else dismiss { actions.setLanguage(language.tag) }
                            }
                        }
                    }
                }
            }
        }
    ) {
        item(key = "header", contentType = "header") {
            ProfileHeader(state, onPhoto = { sheet = ProfileSheet.PHOTO })
        }
        when {
            state.signedOut -> item(key = "status") { StatusMessage(stringResource(R.string.profile_load_signed_out), onRetry = null) }
            state.loadError != null -> item(key = "status") {
                StatusMessage(state.loadError!!.ifBlank { stringResource(R.string.profile_load_error_fallback) }, onRetry = viewModel::load)
            }
            else -> item(key = "stats", contentType = "stats") {
                val handicap = state.user?.handicapIndex?.let(::formatHandicapValue)
                StatStrip(
                    listOf(
                        Stat(stringResource(R.string.profile_stat_rounds), state.roundsPlayed?.toString() ?: stringResource(R.string.profile_stat_value_placeholder)),
                        Stat(
                            stringResource(R.string.profile_stat_handicap),
                            when {
                                state.user == null -> stringResource(R.string.profile_stat_value_placeholder)
                                handicap != null -> handicap
                                else -> stringResource(R.string.profile_stat_handicap_none)
                            }
                        )
                    ),
                    Modifier.padding(top = 20.dp)
                )
            }
        }
        item(key = "account", contentType = "section") {
            val language = LocaleManager.SUPPORTED_LANGUAGES.firstOrNull { it.tag == currentLanguageTag }?.label
            GroupedSection(header = stringResource(R.string.profile_section_account)) {
                SettingsRow(R.string.profile_personal_details, R.drawable.ic_glyph_person) { actions.open(ProfileDestination.PERSONAL) }
                ListDivider(IconRowDividerInset)
                SettingsRow(R.string.profile_playing_details, R.drawable.ic_glyph_flag) { actions.open(ProfileDestination.PLAYING) }
                ListDivider(IconRowDividerInset)
                SettingsRow(R.string.profile_notification_preferences, R.drawable.ic_glyph_bell) { actions.open(ProfileDestination.NOTIFICATIONS) }
                ListDivider(IconRowDividerInset)
                ListRow(stringResource(R.string.profile_language), icon = R.drawable.ic_glyph_globe, value = language, onClick = { sheet = ProfileSheet.LANGUAGE })
            }
        }
        item(key = "device", contentType = "section") {
            GroupedSection {
                SettingsRow(R.string.profile_biometric_login, R.drawable.ic_glyph_lock) { actions.open(ProfileDestination.BIOMETRIC) }
                ListDivider(IconRowDividerInset)
                SettingsRow(R.string.profile_offline_sync, R.drawable.ic_glyph_sync) { actions.open(ProfileDestination.OFFLINE_SYNC) }
                ListDivider(IconRowDividerInset)
                SettingsRow(R.string.profile_privacy, R.drawable.ic_glyph_shield) { actions.open(ProfileDestination.PRIVACY) }
            }
        }
        item(key = "signOut", contentType = "section") {
            GroupedSection {
                ListRow(stringResource(R.string.privacy_sign_out), titleColor = TeeUpTheme.colors.destructive, showChevron = false, onClick = actions.signOut)
            }
        }
    }
}

@Composable
private fun SettingsRow(title: Int, icon: Int, onClick: () -> Unit) {
    ListRow(stringResource(title), icon = icon, onClick = onClick)
}

/** The photo (or initials, or the logo before the profile loads) with a camera badge, the
 *  name, and "handicap · home course". */
@Composable
private fun ProfileHeader(state: ProfileUiState, onPhoto: () -> Unit) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val user = state.user
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .size(PHOTO_SIZE)
                .pressable(onClick = onPhoto, enabled = !state.savingPhoto && !state.signedOut, pressedScale = 0.95f, role = Role.Button, onClickLabel = stringResource(R.string.profile_photo_change))
        ) {
            val photoModifier = Modifier
                .size(PHOTO_SIZE)
                .shadow(12.dp, CircleShape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
                .clip(CircleShape)
                .background(colors.surface)
                .border(3.dp, colors.surface, CircleShape)
            when {
                state.photo != null -> Image(state.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = photoModifier)
                user != null -> Box(photoModifier) {
                    Avatar(user.displayName, user.id, size = PHOTO_SIZE, textStyle = type.title1)
                }
                else -> Image(painterResource(R.drawable.ic_logo), contentDescription = null, contentScale = ContentScale.Crop, modifier = photoModifier)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(32.dp)
                    .border(2.5.dp, colors.background, CircleShape)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Glyph(R.drawable.ic_glyph_camera, colors.onAccent, size = 16.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            user != null -> {
                Label(user.displayName, type.title2.copy(textAlign = TextAlign.Center), maxLines = 2, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(2.dp))
                Label(
                    stringResource(
                        R.string.profile_details_summary,
                        user.handicapIndex?.let(::formatHandicapValue) ?: stringResource(R.string.profile_handicap_unset),
                        state.homeCourseName ?: stringResource(R.string.register_home_course_none)
                    ),
                    type.subheadline.copy(textAlign = TextAlign.Center),
                    color = colors.secondaryLabel,
                    maxLines = 2
                )
            }
            state.isLoading -> {
                Box(Modifier.size(160.dp, 22.dp).skeleton(RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(200.dp, 14.dp).skeleton(RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(message, TeeUpTheme.type.subheadline.copy(textAlign = TextAlign.Center), color = TeeUpTheme.colors.secondaryLabel)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TeeUpButton(stringResource(R.string.profile_retry), onClick = onRetry, compact = true)
        }
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Label(text, TeeUpTheme.type.title3, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
}

/** A list of choices in a sheet, with a check on [selected]; picking one reports its index. */
@Composable
internal fun SheetOptions(options: List<String>, selected: Int?, onPick: (Int) -> Unit) {
    val colors = TeeUpTheme.colors
    LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.background)) {
        itemsIndexed(options) { index, option ->
            if (index > 0) ListDivider()
            ListRow(
                option,
                showChevron = false,
                trailing = if (index == selected) {
                    { Glyph(R.drawable.ic_glyph_check, colors.accent, size = 20.dp) }
                } else null,
                onClick = { onPick(index) }
            )
        }
    }
}

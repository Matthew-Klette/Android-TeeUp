package com.teeup.android.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.Label
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable

private val LOGO_SIZE = 96.dp

/** The green wash every signed-out screen sits on, deeper than the in-app header wash. */
@Composable
fun AuthBackground(content: @Composable ColumnScope.() -> Unit) {
    val colors = TeeUpTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(Brush.verticalGradient(listOf(colors.accentWash, colors.accentWash.copy(alpha = 0.05f), colors.background)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = TeeUpMetrics.gutter + 8.dp),
        content = content
    )
}

/** The app icon, name and tagline, centred. */
@Composable
fun BrandMark(logoSize: Dp = LOGO_SIZE, showTagline: Boolean = true) {
    val colors = TeeUpTheme.colors
    val shape = RoundedCornerShape(logoSize * 0.24f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(logoSize)
                .shadow(20.dp, shape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
                .clip(shape)
        )
        Spacer(Modifier.height(18.dp))
        Label(stringResource(R.string.app_name), TeeUpTheme.type.largeTitle, maxLines = 1, modifier = Modifier.semantics { heading() })
        if (showTagline) {
            Spacer(Modifier.height(4.dp))
            Label(stringResource(R.string.sign_in_tagline), TeeUpTheme.type.eyebrow, color = colors.accent, maxLines = 1)
        }
    }
}

/** Screen 0: just the brand while Firebase auth state is checked; routing is instant. */
@Composable
fun SplashScreen() {
    AuthBackground {
        Spacer(Modifier.weight(1f))
        BrandMark()
        Spacer(Modifier.weight(1.2f))
    }
}

@Stable
class SignInUiState {
    var googleBusy by mutableStateOf(false)
}

class SignInActions(val google: () -> Unit, val email: () -> Unit, val biometric: () -> Unit, val register: () -> Unit)

/** Screen 1: the brand, then Google (the real Firebase SSO), Email, Face / Fingerprint, and
 *  the Register link, with the terms note at the foot. */
@Composable
fun SignInScreen(state: SignInUiState, actions: SignInActions) {
    val colors = TeeUpTheme.colors
    AuthBackground {
        Spacer(Modifier.weight(1f))
        BrandMark()
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TeeUpButton(
                stringResource(if (state.googleBusy) R.string.sign_in_google_progress else R.string.sign_in_google),
                onClick = actions.google,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.googleBusy
            )
            TeeUpButton(stringResource(R.string.sign_in_email), onClick = actions.email, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            TeeUpButton(
                stringResource(R.string.sign_in_biometric),
                onClick = actions.biometric,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Tinted,
                leadingIcon = R.drawable.ic_glyph_lock
            )
        }
        Spacer(Modifier.height(18.dp))
        TextLink(stringResource(R.string.sign_in_register), actions.register, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        Label(
            stringResource(R.string.sign_in_terms),
            TeeUpTheme.type.footnote.copy(textAlign = TextAlign.Center),
            color = colors.secondaryLabel,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}

/** An accent-coloured text action with a full-size touch target. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.96f, pressedAlpha = 0.6f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Label(
            text,
            TeeUpTheme.type.subheadline.copy(fontWeight = TeeUpTheme.type.headline.fontWeight),
            color = if (enabled) TeeUpTheme.colors.accent else TeeUpTheme.colors.tertiaryLabel,
            maxLines = 2
        )
    }
}

@Stable
class BiometricUnlockUiState(initialStatus: String) {
    var status by mutableStateOf(initialStatus)
    /** Try Again and Sign Out appear once the prompt has been dismissed or has failed. */
    var showOptions by mutableStateOf(false)
}

/** The real enforcement of Biometric Login: Home only opens after a passed prompt. */
@Composable
fun BiometricUnlockScreen(state: BiometricUnlockUiState, onTryAgain: () -> Unit, onSignOut: () -> Unit) {
    AuthBackground {
        Spacer(Modifier.weight(1f))
        BrandMark(showTagline = false)
        Spacer(Modifier.height(20.dp))
        Label(
            state.status,
            TeeUpTheme.type.body.copy(textAlign = TextAlign.Center),
            color = TeeUpTheme.colors.secondaryLabel,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.weight(1.2f))
        AnimatedVisibility(state.showOptions, enter = fadeIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TeeUpButton(
                    stringResource(R.string.biometric_unlock_try_again),
                    onClick = onTryAgain,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = R.drawable.ic_glyph_lock
                )
                Spacer(Modifier.height(8.dp))
                TextLink(stringResource(R.string.biometric_unlock_sign_out_instead), onSignOut)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

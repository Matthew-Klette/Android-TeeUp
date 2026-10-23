package com.teeup.android.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teeup.android.R

/** A centred empty or error state: an icon in a tinted circle, a message and an optional action. */
@Composable
fun StateMessage(
    message: String,
    modifier: Modifier = Modifier,
    buttonLabel: String? = null,
    onButton: () -> Unit = {},
    @DrawableRes icon: Int = R.drawable.ic_glyph_flag
) {
    val colors = TeeUpTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).background(colors.accentTint, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(icon, colors.accent, size = 30.dp)
        }
        Spacer(Modifier.height(18.dp))
        Label(message, TeeUpTheme.type.title3.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        if (buttonLabel != null) {
            Spacer(Modifier.height(20.dp))
            TeeUpButton(buttonLabel, onButton, compact = true)
        }
    }
}

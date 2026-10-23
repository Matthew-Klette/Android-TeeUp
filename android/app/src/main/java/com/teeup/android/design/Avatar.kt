package com.teeup.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AVATAR_PALETTE = listOf(
    Color(0xFFD6EBE2) to Color(0xFF006747),
    Color(0xFFE3E8F8) to Color(0xFF3A55B4),
    Color(0xFFF8E7DA) to Color(0xFFB0581A),
    Color(0xFFF1E1F2) to Color(0xFF8E3A93),
    Color(0xFFE0F0F4) to Color(0xFF1F7189)
)

/** Same person, same colour everywhere: picked from a stable hash of their id. */
fun avatarColors(key: String): Pair<Color, Color> =
    AVATAR_PALETTE[(key.hashCode() and Int.MAX_VALUE) % AVATAR_PALETTE.size]

fun avatarInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** A person's initials in their colour, like Contacts without a photo. [key] is their user id. */
@Composable
fun Avatar(
    name: String,
    key: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    textStyle: TextStyle = TeeUpTheme.type.footnote.copy(fontWeight = TeeUpTheme.type.headline.fontWeight)
) {
    val (background, foreground) = avatarColors(key.ifBlank { name })
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Label(avatarInitials(name), textStyle, color = foreground, maxLines = 1)
    }
}

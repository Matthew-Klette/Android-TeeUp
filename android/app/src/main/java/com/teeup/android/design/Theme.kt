package com.teeup.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teeup.android.R

/**
 * TeeUp's iOS 26-style design system (light appearance only). Apple.com-style neutrals — a
 * soft page faintly tinted toward green, white cards, #1D1D1F ink — with a deep royal golf green
 * (#006747) as the single "tap" colour.
 * Everything visual in the Compose screens reads from here; nothing hard-codes a colour or size.
 */
@Immutable
data class TeeUpColors(
    val background: Color = Color(0xFFF2F5F3),
    val surface: Color = Color.White,
    val label: Color = Color(0xFF1D1D1F),
    val secondaryLabel: Color = Color(0xFF6E6E73),
    val tertiaryLabel: Color = Color(0xFF8E8E93),
    val separator: Color = Color(0x1F3C3C43),
    /** iOS "fill": search fields, unselected chips and secondary buttons. */
    val fill: Color = Color(0x14767680),
    val fillPressed: Color = Color(0x26767680),
    val accent: Color = Color(0xFF006747),
    val accentPressed: Color = Color(0xFF004E36),
    val accentTint: Color = Color(0x1F006747),
    /** The soft wash of green behind a screen's header. */
    val accentWash: Color = Color(0x26006747),
    val onAccent: Color = Color.White,
    val destructive: Color = Color(0xFFD70015),
    /** Light glass: white at low alpha over a blur, with a bright rim catching the light. */
    val glass: Color = Color(0xB8FFFFFF),
    val glassRim: Color = Color(0x99FFFFFF),
    val glassShadow: Color = Color(0x24000000),
    val scrim: Color = Color(0x52000000),
    val skeleton: Color = Color(0xFFE4E9E6),
    val skeletonSheen: Color = Color(0xFFF2F5F3)
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Inter's display cut: tighter spacing and finer detail, drawn for 20pt and up (optical sizing). */
private val InterDisplay = FontFamily(
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold)
)

/**
 * The iOS text styles. Tracking tightens as size grows and leading loosens as it shrinks,
 * per Apple's typography guidance; never one letter-spacing for every size.
 */
@Immutable
data class TeeUpType(
    val largeTitle: TextStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.9).sp),
    val title1: TextStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp),
    val title2: TextStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.45).sp),
    val title3: TextStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.4).sp),
    val headline: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.35).sp),
    val body: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.35).sp),
    val callout: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp),
    val subheadline: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    val footnote: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.05).sp),
    val caption: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    val caption2: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.1.sp),
    /** Small caps-style section label ("NEARBY"), the one place tracking goes clearly positive. */
    val eyebrow: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp)
)

/** 4-pt spacing grid and the corner radii. Cards and sheets use large continuous-feeling corners. */
object TeeUpMetrics {
    /**
     * Everything in the Compose screens is drawn this much larger than Android's default dp/sp,
     * applied once as a density multiplier at the theme root, so every size, gap, touch target
     * and font grows together and the proportions stay exactly as designed.
     */
    const val UI_SCALE = 1.15f

    val gutter = 20.dp
    val cardRadius = 26.dp
    val controlRadius = 14.dp
    val sheetRadius = 34.dp
    val minTouch = 44.dp
    val buttonHeight = 50.dp
    val compactButtonHeight = 40.dp
    val tabBarHeight = 64.dp
}

private val LocalColors = staticCompositionLocalOf { TeeUpColors() }
private val LocalType = staticCompositionLocalOf { TeeUpType() }

object TeeUpTheme {
    val colors: TeeUpColors @Composable get() = LocalColors.current
    val type: TeeUpType @Composable get() = LocalType.current
}

@Composable
fun TeeUpTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * TeeUpMetrics.UI_SCALE, base.fontScale),
        LocalColors provides TeeUpColors(),
        LocalType provides TeeUpType(),
        content = content
    )
}

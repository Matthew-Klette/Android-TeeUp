package com.teeup.android.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.GroupMember
import com.teeup.android.data.TeeTime
import com.teeup.android.data.formatHandicap
import com.teeup.android.data.paceLabel
import com.teeup.android.design.Avatar
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpMotion
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable
import com.teeup.android.design.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.text.DateFormatSymbols

private const val ENTRANCE_STAGGER_MS = 45L
private const val MAX_STAGGERED = 6
private const val MAX_AVATARS = 4

/**
 * One open group: a calendar-style date tile, the course and tee time, who's playing, what the
 * host is looking for, and the two actions. The whole card opens the group and presses like a
 * physical card. With [animateIn] it springs into place, staggered by [index] — only the first
 * time a group appears, not every time the list recycles it back on screen.
 */
@Composable
fun TeeTimeCard(
    teeTime: TeeTime,
    course: Course?,
    index: Int,
    animateIn: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    val parts = remember(teeTime.dateTime) { TeeTimeParts.parse(teeTime.dateTime) }
    val courseName = course?.name ?: stringResource(R.string.home_unknown_course)
    val host = teeTime.members.firstOrNull { it.isHost }

    Column(
        modifier
            .fillMaxWidth()
            .cardEntrance(index, animateIn)
            .pressable(onClick = onOpen, pressedScale = 0.98f, pressedAlpha = 1f, onClickLabel = stringResource(R.string.home_card_view_group))
            .shadow(18.dp, shape, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(colors.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTile(parts)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(courseName, type.headline, maxLines = 2, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(R.drawable.ic_glyph_clock, colors.secondaryLabel, size = 15.dp)
                    Spacer(Modifier.width(5.dp))
                    Label(
                        listOfNotNull(parts?.time, stringResource(R.string.home_chip_holes_format, teeTime.holes ?: 18)).joinToString("  ·  "),
                        type.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarStack(teeTime.members)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (host != null) {
                    Label(stringResource(R.string.home_card_hosted_by_format, host.displayName), type.footnote.copy(fontWeight = type.headline.fontWeight), maxLines = 1)
                    Label(
                        "HCP ${formatHandicap(host.handicapIndex)}  ·  ${paceLabel(LocalContext.current, host.paceOfPlay)}",
                        type.footnote,
                        color = colors.secondaryLabel,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            SpotsPill(teeTime.spotsRemaining)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(R.drawable.ic_glyph_target, colors.accent, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Label(
                stringResource(R.string.home_card_wanted_format, wantedSummary(teeTime)),
                type.footnote,
                color = colors.label,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TeeUpButton(
                stringResource(R.string.home_card_preview),
                onPreview,
                Modifier.weight(1f),
                kind = ButtonKind.Secondary,
                compact = true
            )
            TeeUpButton(
                stringResource(R.string.home_card_view_group),
                onOpen,
                Modifier.weight(1f),
                compact = true
            )
        }
    }
}

/** Springs the card up into place the first time it's shown, staggered down the list. */
@Composable
private fun Modifier.cardEntrance(index: Int, animateIn: Boolean): Modifier {
    val reduced = rememberReducedMotion() || !animateIn
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    val translation = remember { Animatable(if (reduced) 0f else rise) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(ENTRANCE_STAGGER_MS * index.coerceAtMost(MAX_STAGGERED))
        coroutineScope {
            launch { alpha.animateTo(1f, TeeUpMotion.smooth()) }
            launch { translation.animateTo(0f, TeeUpMotion.smooth()) }
        }
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        translationY = translation.value
    }
}

/** Calendar-style tile: weekday over the day of the month, in the accent. Rounds uses it too. */
@Composable
fun DateTile(parts: TeeTimeParts?) {
    val colors = TeeUpTheme.colors
    val weekdays = remember { DateFormatSymbols.getInstance().shortWeekdays }
    Column(
        Modifier
            .size(width = 54.dp, height = 58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.accent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Label(
            parts?.let { weekdays.getOrNull(it.weekdayIndex + 1)?.uppercase() }.orEmpty(),
            TeeUpTheme.type.caption2,
            color = colors.onAccent.copy(alpha = 0.8f),
            maxLines = 1
        )
        Label(parts?.dayOfMonth?.toString() ?: "–", TeeUpTheme.type.title2, color = colors.onAccent, maxLines = 1)
    }
}

@Composable
private fun SpotsPill(spotsRemaining: Int) {
    val colors = TeeUpTheme.colors
    val full = spotsRemaining <= 0
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (full) colors.fill else colors.accentTint)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Label(
            if (full) stringResource(R.string.home_card_full) else pluralStringResource(R.plurals.home_card_spots_left, spotsRemaining, spotsRemaining),
            TeeUpTheme.type.caption.copy(fontWeight = TeeUpTheme.type.headline.fontWeight),
            color = if (full) colors.secondaryLabel else colors.accent,
            maxLines = 1
        )
    }
}

/** Overlapping initials, like Messages' group avatars, with a "+n" when the group is bigger. */
@Composable
private fun AvatarStack(members: List<GroupMember>) {
    val colors = TeeUpTheme.colors
    val shown = members.take(MAX_AVATARS)
    Box {
        shown.forEachIndexed { i, member ->
            Box(
                Modifier
                    .offset(x = (i * 20).dp)
                    .size(32.dp)
                    .border(2.dp, colors.surface, CircleShape)
                    .padding(2.dp)
            ) {
                Avatar(member.displayName, member.userId, size = 28.dp, textStyle = TeeUpTheme.type.caption2)
            }
        }
        val extra = members.size - shown.size
        if (extra > 0) {
            Box(
                Modifier
                    .offset(x = (shown.size * 20).dp)
                    .size(32.dp)
                    .border(2.dp, colors.surface, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.fill),
                contentAlignment = Alignment.Center
            ) {
                Label("+$extra", TeeUpTheme.type.caption2, color = colors.secondaryLabel, maxLines = 1)
            }
        }
        // Reserve the stack's full width so the text beside it doesn't sit under the avatars.
        Spacer(Modifier.width((32 + 20 * (shown.size - 1 + if (extra > 0) 1 else 0)).coerceAtLeast(32).dp))
    }
}

/** "HCP up to 20 · Relaxed", "HCP 5–20 · Any pace", "Any handicap · Standard". */
@Composable
fun wantedSummary(teeTime: TeeTime): String {
    val min = teeTime.wantedHandicapMin
    val max = teeTime.wantedHandicapMax
    val handicapPart = when {
        min != null && max != null -> stringResource(R.string.home_card_wanted_handicap_range_format, formatHandicap(min), formatHandicap(max))
        max != null -> stringResource(R.string.home_card_wanted_handicap_max_format, formatHandicap(max))
        min != null -> stringResource(R.string.home_card_wanted_handicap_min_format, formatHandicap(min))
        else -> stringResource(R.string.home_card_wanted_handicap_any)
    }
    val pacePart = teeTime.wantedPace?.let { paceLabel(LocalContext.current, it) } ?: stringResource(R.string.filter_pace_any)
    return "$handicapPart · $pacePart"
}

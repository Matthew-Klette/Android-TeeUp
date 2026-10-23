package com.teeup.android.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.HoleScore
import com.teeup.android.data.formatTeeTime
import com.teeup.android.design.BackButton
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.ConfirmSheetContent
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitle
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.Stat
import com.teeup.android.design.StatStrip
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable
import com.teeup.android.design.skeleton

class RoundSummaryActions(val back: () -> Unit, val done: () -> Unit)

private sealed interface SummarySheet {
    data class DeleteHole(val holeNumber: Int) : SummarySheet
    data object DeleteRound : SummarySheet
}

private val HOLE_BADGE = 36.dp

/**
 * The round summary: the course and date as the title, strokes / putts / holes at a glance, the
 * finer numbers as a grouped list, then every scored hole with Delete. Done floats at the bottom.
 */
@Composable
fun RoundSummaryScreen(viewModel: RoundSummaryViewModel, actions: RoundSummaryActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<SummarySheet?>(null) }
    val stats = state.stats
    val courseName = state.course?.name ?: stringResource(R.string.rounds_unknown_course)
    val unavailable = stringResource(R.string.summary_stat_unavailable)

    LargeTitleScreen(
        title = stringResource(R.string.summary_title),
        navigation = { BackButton(actions.back, stringResource(R.string.detail_back)) },
        bottomBar = { TeeUpButton(stringResource(R.string.summary_done), onClick = actions.done, modifier = Modifier.fillMaxWidth()) },
        header = {
            val round = state.round
            if (round != null) {
                LargeTitle(stringResource(R.string.summary_title), eyebrow = courseName.uppercase(), subtitle = formatTeeTime(round.dateTime))
            } else {
                LargeTitle(stringResource(R.string.summary_title), subtitle = stringResource(R.string.summary_preview))
            }
        },
        overlay = {
            sheet?.let { current ->
                TeeUpSheet(onDismissed = { sheet = null }) { dismiss ->
                    when (current) {
                        is SummarySheet.DeleteHole -> ConfirmSheetContent(
                            title = stringResource(R.string.summary_delete_confirm_title),
                            message = stringResource(R.string.summary_delete_confirm_message),
                            confirmLabel = stringResource(R.string.summary_delete_hole),
                            cancelLabel = stringResource(R.string.dialog_cancel),
                            destructive = true,
                            onConfirm = { viewModel.deleteHole(current.holeNumber) },
                            dismiss = dismiss
                        )
                        SummarySheet.DeleteRound -> ConfirmSheetContent(
                            title = stringResource(R.string.summary_delete_round_confirm_title),
                            message = stringResource(R.string.summary_delete_round_confirm_message),
                            confirmLabel = stringResource(R.string.summary_delete_round),
                            cancelLabel = stringResource(R.string.dialog_cancel),
                            destructive = true,
                            onConfirm = viewModel::deleteRound,
                            dismiss = dismiss
                        )
                    }
                }
            }
        }
    ) {
        when {
            stats == null && state.loadFailed -> item(key = "message") {
                StateMessage(stringResource(R.string.summary_load_failed), buttonLabel = stringResource(R.string.profile_retry), onButton = viewModel::load)
            }
            stats == null -> item(key = "skeleton", contentType = "skeleton") {
                Column(Modifier.padding(horizontal = TeeUpMetrics.gutter).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(78.dp).skeleton(RoundedCornerShape(TeeUpMetrics.cardRadius)))
                    Box(Modifier.fillMaxWidth().height(160.dp).skeleton(RoundedCornerShape(TeeUpMetrics.cardRadius)))
                }
            }
            else -> {
                item(key = "totals", contentType = "stats") {
                    StatStrip(
                        listOf(
                            Stat(stringResource(R.string.summary_total_strokes), stats.totalStrokes.toString(), TeeUpTheme.colors.accent),
                            Stat(stringResource(R.string.summary_total_putts), stats.totalPutts.toString()),
                            Stat(stringResource(R.string.summary_holes_played), stats.holesPlayed.toString())
                        ),
                        Modifier.padding(top = 20.dp)
                    )
                }
                item(key = "details", contentType = "section") {
                    GroupedSection(footer = if (stats.showHandicapHint) stringResource(R.string.summary_handicap_hint) else null) {
                        ListRow(stringResource(R.string.summary_avg_putts), value = stats.averagePutts ?: unavailable)
                        ListDivider()
                        ListRow(stringResource(R.string.summary_net_score), value = stats.netScore ?: unavailable)
                        ListDivider()
                        ListRow(stringResource(R.string.summary_stableford_score), value = stats.stableford ?: unavailable)
                    }
                }
                item(key = "holes", contentType = "section") {
                    GroupedSection(header = stringResource(R.string.scorecard_title)) {
                        if (stats.holes.isEmpty()) {
                            ListRow(stringResource(R.string.summary_no_holes), titleColor = TeeUpTheme.colors.secondaryLabel)
                        }
                        stats.holes.forEachIndexed { index, hole ->
                            if (index > 0) ListDivider(16.dp + HOLE_BADGE + 14.dp)
                            HoleRow(
                                hole,
                                onDelete = if (stats.roundId != null) {
                                    { sheet = SummarySheet.DeleteHole(hole.holeNumber) }
                                } else null
                            )
                        }
                    }
                }
                if (stats.roundId != null) {
                    item(key = "deleteRound", contentType = "section") {
                        GroupedSection {
                            ListRow(
                                stringResource(R.string.summary_delete_round),
                                titleColor = TeeUpTheme.colors.destructive,
                                showChevron = false,
                                onClick = { sheet = SummarySheet.DeleteRound }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The hole number, its strokes and putts as two small figures, and Delete. Read aloud as the
 *  one sentence ("Hole 4 — 5 strokes, 2 putts"). */
@Composable
private fun HoleRow(hole: HoleScore, onDelete: (() -> Unit)?) {
    val description = stringResource(R.string.summary_hole_row_format, hole.holeNumber, hole.strokes, hole.putts)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
            HoleBadge(hole.holeNumber)
            Spacer(Modifier.width(14.dp))
            Figure(hole.strokes, stringResource(R.string.scorecard_strokes))
            Spacer(Modifier.width(26.dp))
            Figure(hole.putts, stringResource(R.string.scorecard_putts))
        }
        if (onDelete != null) DeleteText(onDelete)
    }
}

@Composable
private fun Figure(value: Int, label: String) {
    Column {
        Label(value.toString(), TeeUpTheme.type.title3, maxLines = 1)
        Label(label, TeeUpTheme.type.caption, color = TeeUpTheme.colors.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun HoleBadge(number: Int) {
    Box(Modifier.size(HOLE_BADGE).background(TeeUpTheme.colors.accentTint, CircleShape), contentAlignment = Alignment.Center) {
        Label(number.toString(), TeeUpTheme.type.headline, color = TeeUpTheme.colors.accent, maxLines = 1)
    }
}

@Composable
private fun DeleteText(onClick: () -> Unit) {
    Box(Modifier.pressable(onClick = onClick, pressedScale = 0.94f).padding(horizontal = 4.dp, vertical = 8.dp)) {
        Label(stringResource(R.string.summary_delete_hole), TeeUpTheme.type.subheadline, color = TeeUpTheme.colors.destructive, maxLines = 1)
    }
}

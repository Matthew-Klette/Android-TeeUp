package com.teeup.android.rounds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.formatTeeTime
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.GlassIconButton
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitle
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.SegmentedControl
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.skeleton
import com.teeup.android.home.DateTile
import com.teeup.android.home.TeeTimeParts
import com.teeup.android.nav.BottomNavTab

class RoundsActions(
    val openScorecard: (teeTimeId: String, holes: Int?) -> Unit,
    val openSummary: (teeTimeId: String) -> Unit,
    val openTab: (BottomNavTab) -> Unit,
    /** Start a Round opened: find the phone (asking for location if needed) for Nearby courses. */
    val findNearby: () -> Unit
)

/** Which sheet is up: starting a solo round (course, then holes), or just the hole count for a
 *  legacy round that doesn't know its own length. */
internal sealed interface RoundsSheet {
    data object StartSolo : RoundsSheet
    data class ChooseHoles(val teeTimeId: String) : RoundsSheet
}

/**
 * The Rounds tab: a segmented Upcoming / History switch over round cards, each with its one
 * action (start, continue, summary). Starting a solo round is the + button, shown wherever no
 * card already offers a way to start, and the empty state's button.
 */
@Composable
fun RoundsScreen(viewModel: RoundsViewModel, actions: RoundsActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<RoundsSheet?>(null) }
    LaunchedEffect(sheet) { if (sheet == RoundsSheet.StartSolo) actions.findNearby() }
    // A solo round needs no upcoming tee time, so it's always offered on History; on Upcoming
    // only once nothing else on the tab offers a Start/Continue of its own.
    val canStartSolo = state.hasLoaded && !state.loadFailed && (state.showHistory || state.items.isEmpty())

    LargeTitleScreen(
        title = stringResource(R.string.rounds_title),
        selectedTab = BottomNavTab.ROUNDS,
        onSelectTab = actions.openTab,
        actions = {
            if (canStartSolo) {
                GlassIconButton(R.drawable.ic_glyph_plus, stringResource(R.string.rounds_start_a_round), { sheet = RoundsSheet.StartSolo })
            }
        },
        header = { LargeTitle(stringResource(R.string.rounds_title), subtitle = stringResource(R.string.rounds_subtitle)) },
        overlay = {
            sheet?.let { current ->
                TeeUpSheet(onDismissed = { sheet = null; viewModel.resetCourseSearch() }) { dismiss ->
                    when (current) {
                        RoundsSheet.StartSolo -> StartSoloSheetContent(state, viewModel, dismiss)
                        is RoundsSheet.ChooseHoles -> HoleCountContent { holes -> dismiss { actions.openScorecard(current.teeTimeId, holes) } }
                    }
                }
            }
        }
    ) {
        item(key = "tabs", contentType = "tabs") {
            SegmentedControl(
                options = listOf(stringResource(R.string.rounds_upcoming), stringResource(R.string.rounds_history)),
                selectedIndex = if (state.showHistory) 1 else 0,
                onSelect = { viewModel.showHistory(it == 1) },
                modifier = Modifier.padding(horizontal = TeeUpMetrics.gutter).padding(top = 18.dp, bottom = 6.dp)
            )
        }
        roundsContent(state, viewModel, actions, onStartSolo = { sheet = RoundsSheet.StartSolo }, onChooseHoles = { sheet = RoundsSheet.ChooseHoles(it) })
    }
}

private fun LazyListScope.roundsContent(
    state: RoundsUiState,
    viewModel: RoundsViewModel,
    actions: RoundsActions,
    onStartSolo: () -> Unit,
    onChooseHoles: (teeTimeId: String) -> Unit
) {
    when {
        state.isLoading && !state.hasLoaded -> items(2, key = { "skeleton$it" }, contentType = { "skeleton" }) { SkeletonRound() }
        state.loadFailed -> item(key = "message") {
            StateMessage(stringResource(R.string.rounds_load_failed), buttonLabel = stringResource(R.string.profile_retry), onButton = viewModel::load)
        }
        state.items.isEmpty() -> item(key = "message") {
            StateMessage(
                stringResource(if (state.showHistory) R.string.rounds_history_empty else R.string.rounds_upcoming_empty),
                buttonLabel = stringResource(R.string.rounds_start_a_round),
                onButton = onStartSolo,
                icon = R.drawable.ic_glyph_calendar
            )
        }
        else -> items(state.items, key = { it.round.teeTimeId }, contentType = { "round" }) { item ->
            RoundCard(
                item,
                onAction = {
                    when (val action = item.action) {
                        is RoundAction.Start -> action.fixedHoles?.let { actions.openScorecard(item.round.teeTimeId, it) }
                            ?: onChooseHoles(item.round.teeTimeId)
                        is RoundAction.Continue -> actions.openScorecard(item.round.teeTimeId, action.totalHoles)
                        RoundAction.ViewSummary -> actions.openSummary(item.round.teeTimeId)
                        RoundAction.NotYetStartable -> Unit
                    }
                },
                modifier = Modifier.animateItem().padding(horizontal = TeeUpMetrics.gutter).padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun RoundCard(item: RoundItem, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    val parts = remember(item.round.dateTime) { TeeTimeParts.parse(item.round.dateTime) }
    val started = item.round.round != null
    Column(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(colors.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTile(parts)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(item.course?.name ?: stringResource(R.string.rounds_unknown_course), type.headline, maxLines = 2, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(R.drawable.ic_glyph_clock, colors.secondaryLabel, size = 15.dp)
                    Spacer(Modifier.width(5.dp))
                    Label(formatTeeTime(item.round.dateTime), type.subheadline, color = colors.secondaryLabel, maxLines = 1)
                }
            }
        }
        if (started) {
            Spacer(Modifier.height(14.dp))
            HoleProgress(item.holesScored, item.totalHoles)
        }
        Spacer(Modifier.height(14.dp))
        val (label, kind) = when (item.action) {
            RoundAction.NotYetStartable -> stringResource(R.string.rounds_not_started_yet) to ButtonKind.Secondary
            is RoundAction.Start -> stringResource(R.string.rounds_start_round) to ButtonKind.Primary
            is RoundAction.Continue -> stringResource(R.string.rounds_continue_round) to ButtonKind.Primary
            RoundAction.ViewSummary -> stringResource(R.string.rounds_view_summary) to ButtonKind.Tinted
        }
        TeeUpButton(
            label,
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            kind = kind,
            compact = true,
            enabled = item.action != RoundAction.NotYetStartable
        )
    }
}

/** A thin bar of holes scored so far, with "9 of 18 holes" beside it. */
@Composable
private fun HoleProgress(scored: Int, total: Int) {
    val colors = TeeUpTheme.colors
    val fraction = if (total > 0) (scored.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(colors.fill)) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(colors.accent))
        }
        Spacer(Modifier.width(12.dp))
        Label(stringResource(R.string.rounds_progress_format, scored, total), TeeUpTheme.type.footnote, color = colors.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun SkeletonRound() {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Column(
        Modifier
            .padding(horizontal = TeeUpMetrics.gutter)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(TeeUpTheme.colors.surface, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp, 58.dp).skeleton(RoundedCornerShape(15.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Box(Modifier.size(170.dp, 16.dp).skeleton(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(110.dp, 12.dp).skeleton(RoundedCornerShape(6.dp)))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(40.dp).skeleton(CircleShape))
    }
}

package com.teeup.android.scorecard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.teeup.android.R
import com.teeup.android.data.HoleScoreInput
import com.teeup.android.data.RoundScoring
import com.teeup.android.design.BackButton
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.ConfirmSheetContent
import com.teeup.android.design.FieldRow
import com.teeup.android.design.GlassTabBar
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.ListDivider
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.liquidGlass
import com.teeup.android.design.pressable
import com.teeup.android.design.skeleton
import com.teeup.android.nav.BottomNavTab

/** What the current hole's score button shows. */
sealed interface ScoreLabel {
    data object Add : ScoreLabel
    /** Entered this round but not sent yet: tap to change it. */
    data class Pending(val strokes: Int, val putts: Int) : ScoreLabel
    /** Already on the server, which rejects a repeated hole, so it's read-only here. */
    data class Saved(val strokes: Int, val putts: Int) : ScoreLabel
}

/** The in-round screen's state. The Activity runs the round (it's bound to the map controller)
 *  and writes here; the screen only reads, so scrolling and the map never recompose it. */
@Stable
class RoundUiState {
    var loading by mutableStateOf(true)
    var courseName by mutableStateOf("")
    var dateText by mutableStateOf("")
    var hole by mutableIntStateOf(1)
    var canPrevious by mutableStateOf(false)
    var canNext by mutableStateOf(false)
    var scoreLabel by mutableStateOf<ScoreLabel>(ScoreLabel.Add)
    var scored by mutableIntStateOf(0)
    var totalHoles by mutableIntStateOf(18)
    var isComplete by mutableStateOf(false)
    var canDelete by mutableStateOf(false)
    var showFollowGps by mutableStateOf(false)
    var showResetMarkers by mutableStateOf(false)
    /** The score entry sheet: the hole, what's already entered, and whether saving finishes the round. */
    var scoreSheet by mutableStateOf<ScoreSheet?>(null)
    var confirmDelete by mutableStateOf(false)
}

data class ScoreSheet(val hole: Int, val existing: HoleScoreInput?, val finishes: Boolean)

class RoundActions(
    val back: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val followGps: () -> Unit,
    val resetMarkers: () -> Unit,
    val addScore: () -> Unit,
    val saveScore: (HoleScoreInput) -> Unit,
    val finish: () -> Unit,
    val deleteRound: () -> Unit,
    val openTab: (BottomNavTab) -> Unit
)

/**
 * The live scorecard during a round: the hole and its arrows up top, the satellite hole map (the
 * shared [com.teeup.android.ui.HoleMapController]) with live distances, the hole's score, and
 * finishing. Score entry and deleting the round are sheets.
 */
@Composable
fun RoundScreen(state: RoundUiState, host: HoleMapHost, actions: RoundActions) {
    val colors = TeeUpTheme.colors
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            RoundHeader(state, actions)
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = TeeUpMetrics.gutter)
                    .padding(bottom = TeeUpMetrics.tabBarHeight + 24.dp)
                    .navigationBarsPadding()
            ) {
                // Always composed, so the controller finds its views; hidden behind skeletons while loading.
                MapAndPanel(
                    landscape = landscape,
                    map = {
                        HoleMapCard(host::onViewAttached, Modifier.fillMaxSize()) {
                            if (state.showResetMarkers) ResetMarkersPill(actions.resetMarkers)
                            if (state.showFollowGps) FollowGpsPill(actions.followGps)
                        }
                    },
                    panel = { RoundPanel(state, host, actions, scrollable = landscape) },
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (state.loading) 0f else 1f }
                )
                if (state.loading) LoadingSkeleton()
            }
        }
        GlassTabBar(
            selected = BottomNavTab.SCORECARD,
            onSelect = actions.openTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 10.dp)
        )
        state.scoreSheet?.let { sheet ->
            TeeUpSheet(onDismissed = { state.scoreSheet = null }) { dismiss ->
                ScoreSheetContent(sheet, onSave = { entry -> dismiss { actions.saveScore(entry) } }, dismiss = dismiss)
            }
        }
        if (state.confirmDelete) {
            TeeUpSheet(onDismissed = { state.confirmDelete = false }) { dismiss ->
                ConfirmSheetContent(
                    title = stringResource(R.string.summary_delete_round_confirm_title),
                    message = stringResource(R.string.summary_delete_round_confirm_message),
                    confirmLabel = stringResource(R.string.summary_delete_round),
                    cancelLabel = stringResource(R.string.dialog_cancel),
                    destructive = true,
                    onConfirm = actions.deleteRound,
                    dismiss = dismiss
                )
            }
        }
    }
}

@Composable
private fun RoundHeader(state: RoundUiState, actions: RoundActions) {
    val type = TeeUpTheme.type
    val colors = TeeUpTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = TeeUpMetrics.gutter)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions.back, stringResource(R.string.detail_back))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Label(state.courseName, type.headline, maxLines = 1)
                Label(state.dateText, type.footnote, color = colors.secondaryLabel, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HoleArrow(R.drawable.ic_glyph_chevron_left, stringResource(R.string.hole_map_previous), state.canPrevious, actions.previous)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(
                    stringResource(R.string.hole_map_hole_format, state.hole),
                    type.title1.copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() }
                )
                Label(
                    stringResource(R.string.scorecard_progress_format, state.scored, state.totalHoles),
                    type.footnote.copy(textAlign = TextAlign.Center),
                    color = colors.secondaryLabel,
                    maxLines = 1
                )
            }
            HoleArrow(R.drawable.ic_glyph_chevron_right, stringResource(R.string.hole_map_next), state.canNext, actions.next)
        }
    }
}

/** A round grey ‹ or › beside the hole number. */
@Composable
fun HoleArrow(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TeeUpTheme.colors
    Box(
        Modifier
            .size(TeeUpMetrics.minTouch)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.9f, haptic = true, onClickLabel = description)
            .background(colors.surface, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, if (enabled) colors.accent else colors.tertiaryLabel, size = 20.dp)
    }
}

/** Floats over the map once the golfer has moved it off their GPS hole. */
@Composable
fun FollowGpsPill(onClick: () -> Unit) {
    MapPill(R.drawable.ic_glyph_target, stringResource(R.string.hole_map_follow_gps), Alignment.BottomEnd, onClick)
}

/** Floats over the map once the golfer has dragged their dot or the flag on this hole. */
@Composable
fun ResetMarkersPill(onClick: () -> Unit) {
    val description = stringResource(R.string.hole_map_reset_markers_description)
    MapPill(
        R.drawable.ic_glyph_sync,
        stringResource(R.string.hole_map_reset_markers),
        Alignment.BottomStart,
        onClick,
        Modifier.semantics { contentDescription = description }
    )
}

@Composable
private fun MapPill(@DrawableRes icon: Int, label: String, alignment: Alignment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = alignment) {
        Row(
            modifier
                .height(TeeUpMetrics.compactButtonHeight)
                .pressable(onClick = onClick, pressedScale = 0.94f)
                .liquidGlass(CircleShape, blur = 16.dp, elevation = 10.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(icon, TeeUpTheme.colors.accent, size = 17.dp)
            Spacer(Modifier.width(6.dp))
            Label(label, TeeUpTheme.type.subheadline.copy(fontWeight = TeeUpTheme.type.headline.fontWeight), color = TeeUpTheme.colors.accent, maxLines = 1)
        }
    }
}

@Composable
private fun RoundPanel(state: RoundUiState, host: HoleMapHost, actions: RoundActions, scrollable: Boolean) {
    val scroll = rememberScrollState()
    Column(if (scrollable) Modifier.verticalScroll(scroll) else Modifier) {
        DistancesCard(host::onViewAttached)
        Spacer(Modifier.height(12.dp))
        val label = when (val score = state.scoreLabel) {
            ScoreLabel.Add -> stringResource(R.string.scorecard_add_score)
            is ScoreLabel.Pending -> stringResource(R.string.scorecard_edit_score_format, score.strokes, score.putts)
            is ScoreLabel.Saved -> stringResource(R.string.scorecard_saved_score_format, score.strokes, score.putts)
        }
        TeeUpButton(
            label,
            onClick = actions.addScore,
            modifier = Modifier.fillMaxWidth(),
            kind = if (state.scoreLabel is ScoreLabel.Pending) ButtonKind.Tinted else ButtonKind.Primary,
            enabled = state.scoreLabel !is ScoreLabel.Saved,
            leadingIcon = if (state.scoreLabel == ScoreLabel.Add) R.drawable.ic_glyph_plus else null
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TeeUpButton(
                stringResource(if (state.isComplete) R.string.scorecard_finish_round else R.string.scorecard_finish_early),
                onClick = actions.finish,
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Secondary,
                compact = true
            )
            if (state.canDelete) {
                TeeUpButton(
                    stringResource(R.string.summary_delete_round),
                    onClick = { state.confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.DestructiveTinted,
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f).skeleton(shape))
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(120.dp).skeleton(shape))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(TeeUpMetrics.buttonHeight).skeleton(CircleShape))
    }
}

/** Strokes and putts for one hole. A bad entry keeps the sheet open with the reason. */
@Composable
private fun ColumnScope.ScoreSheetContent(sheet: ScoreSheet, onSave: (HoleScoreInput) -> Unit, dismiss: (then: () -> Unit) -> Unit) {
    val colors = TeeUpTheme.colors
    var strokes by remember { mutableStateOf(sheet.existing?.strokes?.toString().orEmpty()) }
    var putts by remember { mutableStateOf(sheet.existing?.putts?.toString().orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    val save = {
        val entry = RoundScoring.entryOrNull(sheet.hole, strokes.trim().toIntOrNull(), putts.trim().toIntOrNull())
        if (entry == null) invalid = true else onSave(entry)
    }
    Label(
        stringResource(R.string.scorecard_score_dialog_title, sheet.hole),
        TeeUpTheme.type.title3,
        modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }
    )
    Column(Modifier.fillMaxWidth().background(colors.background, RoundedCornerShape(TeeUpMetrics.controlRadius))) {
        FieldRow(
            value = strokes,
            onValueChange = { strokes = it; invalid = false },
            placeholder = stringResource(R.string.scorecard_strokes_hint),
            label = stringResource(R.string.scorecard_strokes),
            keyboardType = KeyboardType.Number,
            autoFocus = true
        )
        ListDivider()
        FieldRow(
            value = putts,
            onValueChange = { putts = it; invalid = false },
            placeholder = stringResource(R.string.scorecard_putts_hint),
            label = stringResource(R.string.scorecard_putts),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            onImeAction = save
        )
    }
    if (invalid) {
        Label(
            stringResource(R.string.scorecard_validation_error),
            TeeUpTheme.type.footnote,
            color = colors.destructive,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeeUpButton(stringResource(R.string.dialog_cancel), onClick = { dismiss {} }, modifier = Modifier.weight(1f), kind = ButtonKind.Secondary)
        TeeUpButton(
            stringResource(if (sheet.finishes) R.string.scorecard_save_and_finish else R.string.scorecard_save_score),
            onClick = save,
            modifier = Modifier.weight(1f)
        )
    }
}

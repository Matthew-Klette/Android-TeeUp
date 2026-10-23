package com.teeup.android.scorecard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.design.BackButton
import com.teeup.android.design.Label
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme

/** The Hole Map screen's state, written by the Activity from the map controller's callbacks. */
@Stable
class HoleMapUiState(val courseName: String) {
    /** Null until the map shows a hole: the title reads "Hole Map" until then. */
    var hole by mutableStateOf<Int?>(null)
    var canPrevious by mutableStateOf(false)
    var canNext by mutableStateOf(false)
    var showFollowGps by mutableStateOf(false)
    var showResetMarkers by mutableStateOf(false)
}

class HoleMapActions(
    val back: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val followGps: () -> Unit,
    val resetMarkers: () -> Unit
)

/** Live Hole Map from a course preview: the course's satellite hole view with GPS distances, and
 *  ‹ › through its mapped holes. */
@Composable
fun HoleMapScreen(state: HoleMapUiState, host: HoleMapHost, actions: HoleMapActions) {
    val type = TeeUpTheme.type
    val colors = TeeUpTheme.colors
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = TeeUpMetrics.gutter), verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions.back, stringResource(R.string.detail_back))
            Label(
                state.courseName,
                type.headline,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TeeUpMetrics.gutter).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoleArrow(R.drawable.ic_glyph_chevron_left, stringResource(R.string.hole_map_previous), state.canPrevious, actions.previous)
            Label(
                state.hole?.let { stringResource(R.string.hole_map_hole_format, it) } ?: stringResource(R.string.hole_map_title),
                type.title1.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            HoleArrow(R.drawable.ic_glyph_chevron_right, stringResource(R.string.hole_map_next), state.canNext, actions.next)
        }
        MapAndPanel(
            landscape = landscape,
            map = {
                HoleMapCard(host::onViewAttached, Modifier.fillMaxSize()) {
                    if (state.showResetMarkers) ResetMarkersPill(actions.resetMarkers)
                    if (state.showFollowGps) FollowGpsPill(actions.followGps)
                }
            },
            panel = { Box { DistancesCard(host::onViewAttached) } },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = TeeUpMetrics.gutter)
                .padding(bottom = 16.dp)
        )
    }
}

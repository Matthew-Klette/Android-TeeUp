package com.teeup.android.scorecard

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import com.teeup.android.R
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.HoleMapController

private const val HOST_VIEW_COUNT = 2
private val MapBackground = androidx.compose.ui.graphics.Color(0xFF161C18)

/**
 * Builds the shared [HoleMapController] once its views exist. The controller finds the map and
 * distance views by id on the Activity, but in a Compose screen those views (view_hole_map,
 * view_hole_distances) only join the window after the first frame — so the host waits for both,
 * then creates it, replays the resumed state, and runs anything asked of it before then.
 */
class HoleMapHost(private val activity: Activity, private val listener: HoleMapController.Listener) {
    var controller: HoleMapController? = null
        private set
    private var attachedViews = 0
    private var resumed = false
    private val pending = mutableListOf<(HoleMapController) -> Unit>()

    fun onViewAttached() {
        attachedViews++
        if (attachedViews < HOST_VIEW_COUNT || controller != null) return
        val created = HoleMapController(activity, listener)
        controller = created
        if (resumed) created.onResume()
        pending.forEach { it(created) }
        pending.clear()
    }

    /** Runs [block] now, or as soon as the controller exists. */
    fun withController(block: (HoleMapController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    fun onResume() {
        resumed = true
        controller?.onResume()
    }

    fun onPause() {
        resumed = false
        controller?.onPause()
    }

    fun onDestroy() {
        controller?.onDestroy()
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        controller?.onRequestPermissionsResult(requestCode)
    }
}

/** The satellite hole map (the controller's WebView) in a rounded card. */
@Composable
fun HoleMapCard(onAttached: () -> Unit, modifier: Modifier = Modifier, overlay: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Box(
        modifier
            .shadow(18.dp, shape, ambientColor = TeeUpTheme.colors.glassShadow, spotColor = TeeUpTheme.colors.glassShadow)
            .clip(shape)
            // Behind the satellite tiles while they load, so the white status text stays legible.
            .background(MapBackground)
    ) {
        AndroidView(
            factory = { context -> inflateAttached(context, R.layout.view_hole_map, ViewGroup.LayoutParams.MATCH_PARENT, onAttached) },
            modifier = Modifier.fillMaxSize()
        )
        overlay()
    }
}

/** Front / middle / back distances (the controller's TextViews) on a white card. */
@Composable
fun DistancesCard(onAttached: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = TeeUpTheme.colors.glassShadow.copy(alpha = 0.08f), spotColor = TeeUpTheme.colors.glassShadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(TeeUpTheme.colors.surface)
    ) {
        AndroidView(
            factory = { context -> inflateAttached(context, R.layout.view_hole_distances, ViewGroup.LayoutParams.WRAP_CONTENT, onAttached) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Inflated without a parent, so the root's size is set here: without explicit params a WebView
 *  inside Compose never draws. */
private fun inflateAttached(context: Context, layout: Int, height: Int, onAttached: () -> Unit): View =
    LayoutInflater.from(context).inflate(layout, null, false).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        doOnAttach { onAttached() }
    }

/**
 * The map above the [panel] in portrait; side by side in landscape, where there's no height for
 * both. One layout for both orientations, so rotating never recreates the map's WebView (the
 * controller keeps the one it found).
 */
@Composable
fun MapAndPanel(
    landscape: Boolean,
    map: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 14.dp,
    landscapePanelWidth: Dp = 360.dp
) {
    Layout(contents = listOf(map, panel), modifier = modifier) { (mapMeasurables, panelMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        if (landscape) {
            val panelWidth = landscapePanelWidth.roundToPx().coerceAtMost(constraints.maxWidth / 2)
            val panels = panelMeasurables.map { it.measure(Constraints(maxWidth = panelWidth, maxHeight = constraints.maxHeight)) }
            val mapWidth = (constraints.maxWidth - panelWidth - gapPx).coerceAtLeast(0)
            val maps = mapMeasurables.map { it.measure(Constraints.fixed(mapWidth, constraints.maxHeight)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                maps.forEach { it.place(0, 0) }
                panels.forEach { it.place(mapWidth + gapPx, 0) }
            }
        } else {
            val panels = panelMeasurables.map { it.measure(constraints.copy(minHeight = 0)) }
            val panelHeight = panels.maxOfOrNull { it.height } ?: 0
            val mapHeight = (constraints.maxHeight - panelHeight - gapPx).coerceAtLeast(0)
            val maps = mapMeasurables.map { it.measure(Constraints.fixed(constraints.maxWidth, mapHeight)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                maps.forEach { it.place(0, 0) }
                panels.forEach { it.place(0, mapHeight + gapPx) }
            }
        }
    }
}

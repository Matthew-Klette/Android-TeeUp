package com.teeup.android.course

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.teeup.android.R
import com.teeup.android.design.BackButton
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.liquidGlass
import com.teeup.android.design.pressable
import org.json.JSONObject

/** What the screen was opened with. No coordinates means there's no map to show. */
data class CoursePreviewArgs(
    val courseId: String?,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val rating: Double?
)

class CoursePreviewActions(
    val back: () -> Unit,
    val openHoleMap: (courseId: String) -> Unit,
    val openInMaps: (lat: Double, lng: Double) -> Unit
)

/** The page behind the satellite tiles while they load (matches the map's own background). */
private val MapBackground = Color(0xFF161C18)
private const val MAP_BASE_URL = "https://appassets.teeup.local/"
private val ACTION_TILE_HEIGHT = 64.dp

/**
 * Course Preview, laid out like a place in Apple Maps: the real satellite view of the course
 * runs edge to edge, a glass back button floats over it, and a card at the bottom holds the
 * name, rating and the actions (Open in Maps, and Hole Map when the course is known).
 */
@Composable
fun CoursePreviewScreen(args: CoursePreviewArgs, actions: CoursePreviewActions) {
    var mapLoaded by remember { mutableStateOf(false) }

    // No blur source here: recording the WebView into a blur layer stops its tiles painting, so
    // the glass over the map is the frosted tint alone.
    Box(Modifier.fillMaxSize().background(MapBackground)) {
        if (args.lat != null && args.lng != null) {
            CourseMapView(args.lat, args.lng, args.name, onLoaded = { mapLoaded = true }, Modifier.fillMaxSize())
        }
        AnimatedVisibility(visible = !mapLoaded, exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            val hasLocation = args.lat != null && args.lng != null
            MapStatus(stringResource(if (hasLocation) R.string.course_preview_loading else R.string.course_preview_error))
        }
        Box(Modifier.statusBarsPadding().padding(horizontal = TeeUpMetrics.gutter).padding(top = 6.dp)) {
            BackButton(actions.back, stringResource(R.string.detail_back))
        }
        CourseCard(args, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CourseMapView(lat: Double, lng: Double, name: String, onLoaded: () -> Unit, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Without explicit MATCH_PARENT params a WebView inside Compose never draws.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // CSS pixels are the system's dp: the WebView isn't drawn at the theme's UI scale.
                        val statusBar = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
                        view.evaluateJavascript(CourseMap.topInsetScript((statusBar / view.resources.displayMetrics.density).toInt()), null)
                        onLoaded()
                    }
                }
                loadDataWithBaseURL(MAP_BASE_URL, CourseMap.html(lat, lng, JSONObject.quote(name)), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
private fun MapStatus(text: String) {
    Box(
        Modifier
            .liquidGlass(CircleShape, blur = 16.dp, elevation = 10.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Label(text, TeeUpTheme.type.subheadline, color = TeeUpTheme.colors.secondaryLabel, maxLines = 2)
    }
}

/** The floating place card: inset from the screen edges with every corner rounded, like iOS 26. */
@Composable
private fun CourseCard(args: CoursePreviewArgs, actions: CoursePreviewActions, modifier: Modifier) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val shape = RoundedCornerShape(TeeUpMetrics.sheetRadius)
    val hasLocation = args.lat != null && args.lng != null
    Column(
        modifier
            .navigationBarsPadding()
            .padding(8.dp)
            .fillMaxWidth()
            .shadow(28.dp, shape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 18.dp)
    ) {
        Label(stringResource(R.string.course_preview_title).uppercase(), type.eyebrow, color = colors.accent, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Label(args.name, type.title2, maxLines = 2, modifier = Modifier.semantics { heading() })
        if (args.rating != null) {
            Spacer(Modifier.height(4.dp))
            Label(stringResource(R.string.course_preview_rating_format, args.rating), type.subheadline, color = colors.secondaryLabel, maxLines = 1)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            args.courseId?.let { courseId ->
                ActionTile(
                    stringResource(R.string.hole_map_title),
                    R.drawable.ic_glyph_flag,
                    prominent = true,
                    onClick = { actions.openHoleMap(courseId) },
                    modifier = Modifier.weight(1f)
                )
            }
            ActionTile(
                stringResource(R.string.course_preview_open_maps),
                R.drawable.ic_glyph_map,
                prominent = args.courseId == null,
                enabled = hasLocation,
                onClick = { if (args.lat != null && args.lng != null) actions.openInMaps(args.lat, args.lng) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Apple Maps' place-card action: an icon over a short label in a rounded tile. */
@Composable
private fun ActionTile(
    label: String,
    @DrawableRes icon: Int,
    prominent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = TeeUpTheme.colors
    val content = when {
        !enabled -> colors.tertiaryLabel
        prominent -> colors.onAccent
        else -> colors.accent
    }
    Column(
        modifier
            .height(ACTION_TILE_HEIGHT)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.96f, role = Role.Button)
            .clip(RoundedCornerShape(TeeUpMetrics.controlRadius))
            .background(if (prominent && enabled) colors.accent else colors.fill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Glyph(icon, content, size = 22.dp)
        Spacer(Modifier.height(4.dp))
        Label(label, TeeUpTheme.type.footnote.copy(fontWeight = TeeUpTheme.type.headline.fontWeight), color = content, maxLines = 1)
    }
}

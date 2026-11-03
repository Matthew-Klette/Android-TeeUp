package com.teeup.android.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.offline.NetworkMonitor

/**
 * "You're offline" strip under every screen's large title (it's drawn by [LargeTitleScreen]).
 * Appears the moment the connection drops and slides away when it comes back; screens keep
 * showing their last saved data underneath (offline mode). Announced politely by TalkBack.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val online by NetworkMonitor.isOnline.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !online,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val colors = TeeUpTheme.colors
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TeeUpMetrics.gutter, vertical = 8.dp)
                .background(colors.label, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Glyph(R.drawable.ic_glyph_sync, colors.surface, size = 18.dp)
            Column {
                Label(stringResource(R.string.offline_banner_title), TeeUpTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.surface)
                Label(stringResource(R.string.offline_banner_body), TeeUpTheme.type.caption, color = colors.surface.copy(alpha = 0.8f))
            }
        }
    }
}

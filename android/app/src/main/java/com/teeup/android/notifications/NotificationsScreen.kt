package com.teeup.android.notifications

import android.app.Application
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.teeup.android.R
import com.teeup.android.data.AppNotification
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.design.BackButton
import com.teeup.android.design.Glyph
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.pressable
import com.teeup.android.design.skeleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotificationItem(val notification: AppNotification, val style: NotificationStyle)

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val items: List<NotificationItem> = emptyList()
)

/**
 * Real notifications from GET /api/notifications, newest first (the server's order). Not
 * filtered by NotificationPreferences: those toggles control creation, not history.
 */
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    TeeUpApiClient.fetchNotifications().map { NotificationItem(it, NotificationStyle.of(it)) }
                }
                _state.update { it.copy(isLoading = false, items = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true, items = emptyList()) }
            }
        }
    }
}

/** Notifications as iOS-style cards: a tinted category icon, the category in bold while unread,
 *  the message and when it came. Ones linked to a tee time open it. */
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onOpen: (AppNotification) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LargeTitleScreen(
        title = stringResource(R.string.notifications_title),
        navigation = { BackButton(onBack, stringResource(R.string.notifications_back)) }
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> items(3, key = { "skeleton$it" }, contentType = { "skeleton" }) {
                Box(
                    Modifier
                        .padding(horizontal = TeeUpMetrics.gutter)
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(92.dp)
                        .skeleton(RoundedCornerShape(22.dp))
                )
            }
            state.loadFailed -> item(key = "message") {
                StateMessage(stringResource(R.string.notifications_load_failed), buttonLabel = stringResource(R.string.profile_retry), onButton = viewModel::load, icon = R.drawable.ic_glyph_bell)
            }
            state.items.isEmpty() -> item(key = "message") {
                StateMessage(stringResource(R.string.notifications_empty), icon = R.drawable.ic_glyph_bell)
            }
            else -> {
                item(key = "top", contentType = "spacer") { Spacer(Modifier.height(8.dp)) }
                items(state.items, key = { it.notification.id }, contentType = { "notification" }) { item ->
                    NotificationCard(
                        item,
                        onClick = { onOpen(item.notification) },
                        modifier = Modifier.animateItem().padding(horizontal = TeeUpMetrics.gutter).padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val style = item.style
    val shape = RoundedCornerShape(22.dp)
    val tint = when (style.tone) {
        Tone.ACCENT -> colors.accent
        Tone.DESTRUCTIVE -> colors.destructive
        Tone.NEUTRAL -> colors.tertiaryLabel
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (style.opens) Modifier.pressable(onClick = onClick, pressedScale = 0.98f, pressedAlpha = 1f) else Modifier)
            .shadow(14.dp, shape, ambientColor = colors.glassShadow.copy(alpha = 0.08f), spotColor = colors.glassShadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(colors.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint), contentAlignment = Alignment.Center) {
            Glyph(style.icon, colors.onAccent, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(
                    stringResource(style.title),
                    if (style.unread) type.headline else type.headline.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Label(formatTeeTime(item.notification.createdAt), type.caption, color = colors.tertiaryLabel, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Label(item.notification.message, type.subheadline, color = colors.secondaryLabel, maxLines = 4)
        }
        if (style.unread || style.opens) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (style.unread) Box(Modifier.padding(top = 6.dp).size(9.dp).background(colors.accent, CircleShape))
                if (style.opens) {
                    Spacer(Modifier.height(10.dp))
                    Glyph(R.drawable.ic_glyph_chevron_right, colors.tertiaryLabel, size = 16.dp)
                }
            }
        }
    }
}

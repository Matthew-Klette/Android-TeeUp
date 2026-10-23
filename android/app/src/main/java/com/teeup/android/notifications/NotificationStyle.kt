package com.teeup.android.notifications

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.teeup.android.R
import com.teeup.android.data.AppNotification
import com.teeup.android.data.NotificationCategory

/** The colour of a notification's icon tile: green for good news, red for bad, grey otherwise. */
enum class Tone { ACCENT, DESTRUCTIVE, NEUTRAL }

/** How one notification is drawn. [opens] when it has a tee time to go to. */
data class NotificationStyle(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val tone: Tone,
    val unread: Boolean,
    val opens: Boolean
) {
    companion object {
        fun of(notification: AppNotification): NotificationStyle {
            val category = NotificationCategory.fromType(notification.type)
            val (icon, tone) = when (category) {
                NotificationCategory.JOIN_REQUEST_RECEIVED -> R.drawable.ic_glyph_person_badge to Tone.ACCENT
                NotificationCategory.REQUEST_ACCEPTED -> R.drawable.ic_glyph_check to Tone.ACCENT
                NotificationCategory.REQUEST_DECLINED -> R.drawable.ic_glyph_xmark to Tone.DESTRUCTIVE
                NotificationCategory.TEE_TIME_REMINDER -> R.drawable.ic_glyph_clock to Tone.ACCENT
                NotificationCategory.TEE_TIME_CANCELLED -> R.drawable.ic_glyph_xmark to Tone.DESTRUCTIVE
                NotificationCategory.SYNC_PENDING, NotificationCategory.WEATHER_ALERT, null -> R.drawable.ic_glyph_bell to Tone.NEUTRAL
            }
            return NotificationStyle(
                title = category?.labelRes ?: R.string.notifications_title,
                icon = icon,
                tone = tone,
                unread = !notification.isRead,
                opens = notification.relatedEntityId != null
            )
        }
    }
}

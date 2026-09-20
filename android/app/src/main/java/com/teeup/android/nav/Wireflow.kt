package com.teeup.android.nav

import com.teeup.android.data.NotificationType
import java.util.UUID

enum class NotificationDestination { TEE_TIME, ROUNDS, SCORECARD, UNAVAILABLE }

/** RelatedEntityId is a tee-time ID for booking notifications, not for sync notices. */
fun notificationDestination(type: Int, relatedId: String?): NotificationDestination {
    if (type == NotificationType.SYNC_PENDING) return NotificationDestination.SCORECARD
    if (type !in setOf(NotificationType.JOIN_REQUEST_RECEIVED, NotificationType.REQUEST_ACCEPTED,
            NotificationType.REQUEST_DECLINED, NotificationType.TEE_TIME_REMINDER,
            NotificationType.WEATHER_ALERT)) return NotificationDestination.UNAVAILABLE
    val validId = relatedId != null && runCatching {
        UUID.fromString(relatedId).toString().equals(relatedId, ignoreCase = true)
    }.getOrDefault(false)
    return if (validId) NotificationDestination.TEE_TIME else NotificationDestination.ROUNDS
}

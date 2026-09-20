package com.teeup.android

import com.teeup.android.data.NotificationType
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.selectRounds
import com.teeup.android.data.roundTimestamp
import com.teeup.android.nav.NotificationDestination
import com.teeup.android.nav.notificationDestination
import org.junit.Assert.*
import org.junit.Test

class WireflowTest {
    @Test fun bookingNotificationsUseTeeTimeAndHandleMissingIds() {
        val id = "8ddcc975-8f59-4812-bad3-cd39a20af8e2"
        listOf(NotificationType.JOIN_REQUEST_RECEIVED, NotificationType.REQUEST_ACCEPTED,
            NotificationType.REQUEST_DECLINED, NotificationType.TEE_TIME_REMINDER,
            NotificationType.WEATHER_ALERT).forEach {
            assertEquals(NotificationDestination.TEE_TIME, notificationDestination(it, id))
            assertEquals(NotificationDestination.ROUNDS, notificationDestination(it, null))
            assertEquals(NotificationDestination.ROUNDS, notificationDestination(it, "invalid"))
        }
        assertEquals(NotificationDestination.SCORECARD, notificationDestination(NotificationType.SYNC_PENDING, id))
        assertEquals(NotificationDestination.UNAVAILABLE, notificationDestination(100, id))
    }

    @Test fun scheduleSplitHandlesBoundaryUtcAndOrdering() {
        val now = roundTimestamp("2026-09-21T12:00:00Z")
        fun row(id: String, date: String) = ScheduledRound(id, "course", date, null)
        val rounds = listOf(row("later", "2026-09-22T12:00:00Z"),
            row("old", "2026-09-19T12:00:00"), row("now", "2026-09-21T12:00:00Z"),
            row("recent", "2026-09-20T12:00:00Z"))
        assertEquals(listOf("now", "later"), selectRounds(rounds, false, now).map { it.teeTimeId })
        assertEquals(listOf("recent", "old"), selectRounds(rounds, true, now).map { it.teeTimeId })
    }
}

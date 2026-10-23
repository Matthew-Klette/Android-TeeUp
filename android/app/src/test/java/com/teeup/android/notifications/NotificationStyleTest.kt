package com.teeup.android.notifications

import com.teeup.android.R
import com.teeup.android.data.AppNotification
import com.teeup.android.data.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStyleTest {
    private fun notification(type: Int, related: String? = "t1", read: Boolean = false) =
        AppNotification("n1", type, "Message", related, read, "2026-10-23T08:00:00Z")

    @Test
    fun titleIsTheCategoryLabel() {
        assertEquals(R.string.notification_category_join_request, NotificationStyle.of(notification(NotificationType.JOIN_REQUEST_RECEIVED)).title)
        assertEquals(R.string.notification_category_cancelled, NotificationStyle.of(notification(NotificationType.TEE_TIME_CANCELLED)).title)
    }

    @Test
    fun unknownTypeFallsBackToTheScreenTitle() {
        assertEquals(R.string.notifications_title, NotificationStyle.of(notification(99)).title)
    }

    @Test
    fun goodNewsIsAccent_badNewsIsDestructive() {
        assertEquals(Tone.ACCENT, NotificationStyle.of(notification(NotificationType.REQUEST_ACCEPTED)).tone)
        assertEquals(Tone.DESTRUCTIVE, NotificationStyle.of(notification(NotificationType.REQUEST_DECLINED)).tone)
        assertEquals(Tone.DESTRUCTIVE, NotificationStyle.of(notification(NotificationType.TEE_TIME_CANCELLED)).tone)
        assertEquals(Tone.NEUTRAL, NotificationStyle.of(notification(NotificationType.SYNC_PENDING)).tone)
    }

    @Test
    fun opensOnlyWithARelatedTeeTime() {
        assertTrue(NotificationStyle.of(notification(NotificationType.TEE_TIME_REMINDER)).opens)
        assertFalse(NotificationStyle.of(notification(NotificationType.TEE_TIME_REMINDER, related = null)).opens)
    }

    @Test
    fun unreadFollowsTheServer() {
        assertTrue(NotificationStyle.of(notification(NotificationType.TEE_TIME_REMINDER)).unread)
        assertFalse(NotificationStyle.of(notification(NotificationType.TEE_TIME_REMINDER, read = true)).unread)
    }
}

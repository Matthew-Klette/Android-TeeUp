package com.teeup.android.teetime

import com.teeup.android.data.GroupMember
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.JoinRequestStatus
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeTimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeeTimeDetailPresentationTest {
    private val host = "host-1"
    private val me = "me-1"

    private fun teeTime(
        hostUserId: String? = host,
        type: Int = 1,
        status: Int = TeeTimeStatus.OPEN,
        spotsRemaining: Int = 2
    ) = TeeTime(
        id = "t1", hostUserId = hostUserId, courseId = "c1", dateTime = "2026-10-28T14:34:00Z",
        openSpots = 2, spotsRemaining = spotsRemaining, price = 0.0, type = type, holes = 18,
        wantedHandicapMin = null, wantedHandicapMax = null, wantedPace = null, status = status,
        members = listOf(GroupMember(host, "Brandon", 14.5, 1, isHost = true))
    )

    private fun request(id: String, guest: String, status: Int) = JoinRequest(id, "t1", guest, "Guest $guest", status)

    private fun present(teeTime: TeeTime, requests: List<JoinRequest> = emptyList(), myUserId: String? = me) =
        TeeTimeDetailPresentation.from(teeTime, requests, myUserId)

    @Test
    fun hostKind_bookingWithoutHost() {
        assertEquals(HostKind.BOOKING_NO_HOST, present(teeTime(hostUserId = null, type = 0)).hostKind)
    }

    @Test
    fun hostKind_openRoundWithoutHost() {
        assertEquals(HostKind.OPEN_NO_HOST, present(teeTime(hostUserId = null, type = 1)).hostKind)
    }

    @Test
    fun hostKind_hosted() {
        assertEquals(HostKind.HOSTED, present(teeTime()).hostKind)
    }

    @Test
    fun counts_acceptedAndPendingOnly() {
        val requests = listOf(
            request("r1", "a", JoinRequestStatus.ACCEPTED),
            request("r2", "b", JoinRequestStatus.PENDING),
            request("r3", "c", JoinRequestStatus.PENDING),
            request("r4", "d", JoinRequestStatus.DECLINED)
        )
        val result = present(teeTime(), requests)
        assertEquals(1, result.acceptedCount)
        assertEquals(2, result.pendingCount)
    }

    @Test
    fun joinButton_availableForGuestWithNoRequest() {
        assertEquals(JoinButton.AVAILABLE, present(teeTime()).joinButton)
    }

    @Test
    fun joinButton_hiddenForHost() {
        val result = present(teeTime(), myUserId = host)
        assertEquals(JoinButton.HIDDEN, result.joinButton)
        assertTrue(result.isHost)
    }

    @Test
    fun joinButton_sentWhenPendingOrAccepted() {
        assertEquals(JoinButton.SENT, present(teeTime(), listOf(request("r1", me, JoinRequestStatus.PENDING))).joinButton)
        assertEquals(JoinButton.SENT, present(teeTime(), listOf(request("r1", me, JoinRequestStatus.ACCEPTED))).joinButton)
    }

    @Test
    fun joinButton_declinedRequestCanAskAgain() {
        assertEquals(JoinButton.AVAILABLE, present(teeTime(), listOf(request("r1", me, JoinRequestStatus.DECLINED))).joinButton)
    }

    @Test
    fun joinButton_fullByStatusOrSpots() {
        assertEquals(JoinButton.FULL, present(teeTime(status = TeeTimeStatus.FULL)).joinButton)
        assertEquals(JoinButton.FULL, present(teeTime(spotsRemaining = 0)).joinButton)
    }

    @Test
    fun joinButton_sentWinsOverFull() {
        val result = present(teeTime(spotsRemaining = 0), listOf(request("r1", me, JoinRequestStatus.ACCEPTED)))
        assertEquals(JoinButton.SENT, result.joinButton)
    }

    @Test
    fun joinButton_unknownUserCanStillRequest() {
        assertEquals(JoinButton.AVAILABLE, present(teeTime(), myUserId = null).joinButton)
    }

    @Test
    fun withdraw_onlyForMyPendingRequest() {
        assertEquals("r1", present(teeTime(), listOf(request("r1", me, JoinRequestStatus.PENDING))).withdrawRequestId)
        assertNull(present(teeTime(), listOf(request("r1", me, JoinRequestStatus.ACCEPTED))).withdrawRequestId)
        assertNull(present(teeTime(), listOf(request("r1", "someone", JoinRequestStatus.PENDING))).withdrawRequestId)
        assertNull(present(teeTime(), listOf(request("r1", me, JoinRequestStatus.PENDING)), myUserId = null).withdrawRequestId)
    }

    @Test
    fun hostActions_nullForGuests() {
        assertNull(present(teeTime()).hostActions)
    }

    @Test
    fun hostActions_editAndCancelHiddenOnceCancelled() {
        val open = present(teeTime(), myUserId = host).hostActions!!
        assertTrue(open.canEditOrCancel)
        val cancelled = present(teeTime(status = TeeTimeStatus.CANCELLED), myUserId = host).hostActions!!
        assertFalse(cancelled.canEditOrCancel)
    }
}

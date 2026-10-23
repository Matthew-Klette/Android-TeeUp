package com.teeup.android.teetime

import com.teeup.android.data.JoinRequest
import com.teeup.android.data.JoinRequestStatus
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeTimeStatus
import com.teeup.android.data.canRequestToJoin

/** Who, if anyone, runs this tee time. type 0 is a Booking, 1 an Open Round. */
enum class HostKind { BOOKING_NO_HOST, OPEN_NO_HOST, HOSTED }

/** The Request to Join button. The host never sees it: the server blocks joining your own group. */
enum class JoinButton { HIDDEN, AVAILABLE, SENT, FULL }

/** Host-only actions (EME-321). Delete always applies; edit and cancel don't once it's cancelled. */
data class HostActions(val canEditOrCancel: Boolean)

/**
 * What the detail screen shows for a tee time, its join requests and the signed-in user, worked
 * out once per load. [myUserId] is the cached backend id, or null before the first registration.
 */
data class TeeTimeDetailPresentation(
    val hostKind: HostKind,
    val acceptedCount: Int,
    val pendingCount: Int,
    val isHost: Boolean,
    val joinButton: JoinButton,
    /** The user's own still-pending request, which they can withdraw (EME-323). */
    val withdrawRequestId: String?,
    val hostActions: HostActions?
) {
    companion object {
        fun from(teeTime: TeeTime, requests: List<JoinRequest>, myUserId: String?): TeeTimeDetailPresentation {
            val isHost = myUserId != null && teeTime.hostUserId == myUserId
            val alreadyRequested = myUserId != null && !canRequestToJoin(requests, myUserId)
            val isFull = teeTime.status == TeeTimeStatus.FULL || teeTime.spotsRemaining <= 0
            val myRequest = myUserId?.let { uid -> requests.firstOrNull { it.guestUserId == uid } }
            return TeeTimeDetailPresentation(
                hostKind = when {
                    teeTime.hostUserId == null && teeTime.type == 0 -> HostKind.BOOKING_NO_HOST
                    teeTime.hostUserId == null -> HostKind.OPEN_NO_HOST
                    else -> HostKind.HOSTED
                },
                acceptedCount = requests.count { it.status == JoinRequestStatus.ACCEPTED },
                pendingCount = requests.count { it.status == JoinRequestStatus.PENDING },
                isHost = isHost,
                joinButton = when {
                    isHost -> JoinButton.HIDDEN
                    alreadyRequested -> JoinButton.SENT
                    isFull -> JoinButton.FULL
                    else -> JoinButton.AVAILABLE
                },
                withdrawRequestId = myRequest?.takeIf { it.status == JoinRequestStatus.PENDING }?.id,
                hostActions = if (isHost) HostActions(canEditOrCancel = teeTime.status != TeeTimeStatus.CANCELLED) else null
            )
        }
    }
}

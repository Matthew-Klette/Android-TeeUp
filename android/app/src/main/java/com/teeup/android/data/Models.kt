package com.teeup.android.data

import android.content.Context
import com.teeup.android.R

/** Mirrors the API's CourseDto (api/TeeUp.Api/Dtos/CourseDtos.cs). */
data class Course(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double?
)

/**
 * Mirrors the API's TeeTimeDto (api/TeeUp.Api/Dtos/TeeTimeDtos.cs). type: 0=Booking, 1=OpenRound.
 * status: 0=Open, 1=Full, 2=Cancelled (Models/Enums.cs TeeTimeStatus). holes/wanted* are null for
 * legacy/solo rows created before EME-311's group fields existed. openSpots is the group's fixed
 * guest capacity (not counting the host) and never changes; spotsRemaining is that capacity minus
 * accepted guests, so it's the field to show/filter on for "how many spots are actually open".
 */
data class TeeTime(
    val id: String,
    val hostUserId: String?,
    val courseId: String,
    val dateTime: String,
    val openSpots: Int,
    val spotsRemaining: Int,
    val price: Double,
    val type: Int,
    val holes: Int?,
    val wantedHandicapMin: Double?,
    val wantedHandicapMax: Double?,
    val wantedPace: Int?,
    val status: Int,
    val members: List<GroupMember>
)

/** Mirrors the API's TeeTimeStatus enum (api/TeeUp.Api/Models/Enums.cs). */
object TeeTimeStatus {
    const val OPEN = 0
    const val FULL = 1
    const val CANCELLED = 2
}

/** Mirrors the API's GroupMemberDto (api/TeeUp.Api/Dtos/TeeTimeDtos.cs). */
data class GroupMember(
    val userId: String,
    val displayName: String,
    val handicapIndex: Double?,
    val paceOfPlay: Int,
    val isHost: Boolean
)

/** Mirrors the API's JoinRequestDto (api/TeeUp.Api/Dtos/JoinRequestDtos.cs). status: 0=Pending, 1=Accepted, 2=Declined. */
data class JoinRequest(
    val id: String,
    val teeTimeId: String,
    val guestUserId: String,
    val status: Int
)

object JoinRequestStatus {
    const val PENDING = 0
    const val ACCEPTED = 1
    const val DECLINED = 2

    fun label(context: Context, status: Int): String = context.getString(when (status) {
        PENDING -> R.string.join_request_status_pending
        ACCEPTED -> R.string.join_request_status_accepted
        DECLINED -> R.string.join_request_status_declined
        else -> R.string.join_request_status_unknown
    })
}

/**
 * Mirrors the API's UserDto (api/TeeUp.Api/Dtos/AuthDtos.cs). paceOfPlay: 0=Relaxed,
 * 1=Standard, 2=Brisk (api/TeeUp.Api/Models/Enums.cs PaceOfPlay). POST /api/auth/register
 * is idempotent and returns this full record for an already-registered user, which is how
 * PersonalDetailsActivity/PlayingDetailsActivity read the current profile before editing
 * it. GET /api/profiles/me (TeeUpApiClient.fetchProfile) returns the same shape without
 * the register side effect — that's what NotificationPreferencesActivity reads from
 * (EME-318); an earlier version of this comment claimed that GET endpoint didn't exist.
 */
data class RegisteredUser(
    val id: String,
    val displayName: String,
    val handicapIndex: Double?,
    val homeCourseId: String?,
    val paceOfPlay: Int,
    val profileComplete: Boolean,
    val joinRequestNotifications: Boolean,
    val teeTimeReminders: Boolean,
    val weatherAlerts: Boolean
)

/** Mirrors the API's NotificationDto (api/TeeUp.Api/Dtos/NotificationDtos.cs). */
data class AppNotification(
    val id: String,
    val type: Int,
    val message: String,
    val relatedEntityId: String?,
    val isRead: Boolean,
    val createdAt: String
)

/** Mirrors the API's NotificationType enum (api/TeeUp.Api/Models/Enums.cs). */
object NotificationType {
    const val JOIN_REQUEST_RECEIVED = 0
    const val REQUEST_ACCEPTED = 1
    const val REQUEST_DECLINED = 2
    const val TEE_TIME_REMINDER = 3
    const val SYNC_PENDING = 4
    const val WEATHER_ALERT = 5
}

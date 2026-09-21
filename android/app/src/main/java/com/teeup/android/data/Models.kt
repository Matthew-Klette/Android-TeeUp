package com.teeup.android.data

/** Mirrors the API's CourseDto (api/TeeUp.Api/Dtos/CourseDtos.cs). */
data class Course(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double?
)

/** Mirrors the API's TeeTimeDto (api/TeeUp.Api/Dtos/TeeTimeDtos.cs). type: 0=Booking, 1=OpenRound. */
data class TeeTime(
    val id: String,
    val hostUserId: String?,
    val courseId: String,
    val dateTime: String,
    val openSpots: Int,
    val price: Double,
    val type: Int
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

    fun label(status: Int): String = when (status) {
        PENDING -> "Pending"
        ACCEPTED -> "Accepted"
        DECLINED -> "Declined"
        else -> "Unknown"
    }
}

/**
 * Mirrors the API's UserDto (api/TeeUp.Api/Dtos/AuthDtos.cs). paceOfPlay: 0=Relaxed,
 * 1=Standard, 2=Brisk (api/TeeUp.Api/Models/Enums.cs PaceOfPlay). POST /api/auth/register
 * is idempotent and returns this full record for an already-registered user, which is
 * how PersonalDetailsActivity/PlayingDetailsActivity read the current profile before
 * editing it — there's no separate GET /api/profiles/me endpoint.
 */
data class RegisteredUser(
    val id: String,
    val displayName: String,
    val handicapIndex: Double?,
    val homeCourseId: String?,
    val paceOfPlay: Int,
    val profileComplete: Boolean
)

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

/** Mirrors the API's UserDto (api/TeeUp.Api/Dtos/AuthDtos.cs) — only the fields this client needs. */
data class RegisteredUser(val id: String, val displayName: String)

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

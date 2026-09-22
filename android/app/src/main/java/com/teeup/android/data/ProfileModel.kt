package com.teeup.android.data

data class UserProfile(
    val id: String,
    val displayName: String,
    val handicapIndex: Double?,
    val homeCourseId: String?,
    val paceOfPlay: Int,
    val language: Int,
    val profileComplete: Boolean,
    val joinRequestNotifications: Boolean,
    val teeTimeReminders: Boolean
)

/**
 * Only supplied values are updated.
 * Explicit clear flags remove optional playing details.
 */
data class ProfileUpdateRequest(
    val displayName: String? = null,
    val handicapIndex: Double? = null,
    val homeCourseId: String? = null,
    val paceOfPlay: Int? = null,
    val joinRequestNotifications: Boolean? = null,
    val teeTimeReminders: Boolean? = null,
    val clearHandicapIndex: Boolean = false,
    val clearHomeCourse: Boolean = false
)
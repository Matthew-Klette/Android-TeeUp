package com.teeup.android.data.network

import com.teeup.android.data.Course
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeTime
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import com.teeup.android.data.ProfileUpdateRequest
import com.teeup.android.data.UserProfile
import retrofit2.http.Header

/** Retrofit mirror of the endpoints TeeUpApiClient calls by hand. See RetrofitClient for wiring. */
interface TeeUpApiService {
    @GET("api/rounds/me/schedule")
    suspend fun getRoundSchedule(@Header("Authorization") authorization: String): List<com.teeup.android.data.ScheduledRound>
    @GET("api/profiles/me")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): UserProfile

    @PATCH("api/profiles/me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body body: ProfileUpdateRequest
    ): UserProfile
    @GET("api/courses")
    suspend fun getCourses(): List<Course>

    @GET("api/teetimes")
    suspend fun getTeeTimes(): List<TeeTime>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisteredUser

    @POST("api/teetimes/{teeTimeId}/joinrequests")
    suspend fun createJoinRequest(
        @Path("teeTimeId") teeTimeId: String,
        @Body body: JoinRequestBody
    ): JoinRequest

    @GET("api/teetimes/{teeTimeId}/joinrequests")
    suspend fun getJoinRequests(@Path("teeTimeId") teeTimeId: String): List<JoinRequest>

    @PATCH("api/join-requests/{joinRequestId}")
    suspend fun updateJoinRequestStatus(
        @Path("joinRequestId") joinRequestId: String,
        @Body body: JoinRequestStatusBody
    ): JoinRequest
}

data class RegisterRequest(val firebaseUid: String, val displayName: String)
data class JoinRequestBody(val guestUserId: String)
data class JoinRequestStatusBody(val status: Int)

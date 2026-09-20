package com.teeup.android.data

import android.util.Log
import com.teeup.android.data.network.JoinRequestBody
import com.teeup.android.data.network.JoinRequestStatusBody
import com.teeup.android.data.network.RegisterRequest
import com.teeup.android.data.network.RetrofitClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Retrofit-backed repository (EME-294 scaffold). New screens should call through
 * here on a ViewModel's coroutine scope; existing screens still use TeeUpApiClient
 * pending their own migration.
 */
object TeeUpRepository {
    private const val TAG = "TeeUpRepository"
    private val api = RetrofitClient.apiService
    suspend fun getRoundSchedule(): List<ScheduledRound> = profileCall { api.getRoundSchedule(it) }
    suspend fun getProfile(): UserProfile = profileCall {
        api.getProfile(it)
    }

    suspend fun updateProfile(
        request: ProfileUpdateRequest
    ): UserProfile = profileCall {
        api.updateProfile(it, request)
    }

    /**
     * Profile endpoints require a Firebase ID token.
     * Runs token retrieval off the main thread and never logs the token.
     */
    private suspend fun <T> profileCall(
        block: suspend (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw ApiException("Please sign in to access your profile.")

        val token = try {
            Tasks.await(
                user.getIdToken(false),
                15,
                TimeUnit.SECONDS
            ).token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(
                "Could not verify your sign-in. Check your connection and try again."
            )
        }

        if (token.isNullOrBlank()) {
            throw ApiException("Your session is unavailable. Please sign in again.")
        }

        try {
            Log.d(TAG, "Profile request started")
            block("Bearer $token").also {
                Log.d(TAG, "Profile request succeeded")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Profile request failed: network unavailable")
            throw ApiException(
                "Could not connect. Check your connection and try again."
            )
        } catch (e: HttpException) {
            Log.w(TAG, "Profile request failed: HTTP ${e.code()}")

            val message = when (e.code()) {
                400 -> "Some profile details are invalid. Check them and try again."
                401 -> "Your session has expired. Please sign in again."
                403 -> "You do not have permission to access this profile."
                404 -> "Your profile could not be found. Complete registration first."
                in 500..599 -> "The profile service is unavailable. Please try again later."
                else -> "Could not complete the request. Please try again."
            }

            throw ApiException(message)
        }
    }

    suspend fun getCourses(): List<Course> = call("getCourses") { api.getCourses() }

    suspend fun getTeeTimes(): List<TeeTime> = call("getTeeTimes") { api.getTeeTimes() }

    suspend fun register(firebaseUid: String, displayName: String): RegisteredUser =
        call("register") { api.register(RegisterRequest(firebaseUid, displayName)) }

    suspend fun createJoinRequest(teeTimeId: String, guestUserId: String): JoinRequest =
        call("createJoinRequest") { api.createJoinRequest(teeTimeId, JoinRequestBody(guestUserId)) }

    suspend fun getJoinRequests(teeTimeId: String): List<JoinRequest> =
        call("getJoinRequests") { api.getJoinRequests(teeTimeId) }

    suspend fun updateJoinRequestStatus(joinRequestId: String, status: Int): JoinRequest =
        call("updateJoinRequestStatus") { api.updateJoinRequestStatus(joinRequestId, JoinRequestStatusBody(status)) }

    private suspend fun <T> call(label: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "$label: request started")
            block().also { Log.d(TAG, "$label: request succeeded") }
        } catch (e: IOException) {
            Log.e(TAG, "$label: network error", e)
            throw ApiException("Couldn't reach the API at ${ApiConfig.BASE_URL} — is it running?")
        } catch (e: HttpException) {
            Log.e(TAG, "$label: HTTP ${e.code()}", e)
            throw ApiException("$label failed: HTTP ${e.code()}")
        }
    }
}

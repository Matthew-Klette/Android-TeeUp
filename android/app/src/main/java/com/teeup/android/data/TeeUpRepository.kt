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

/**
 * Retrofit-backed repository (EME-294 scaffold). New screens should call through
 * here on a ViewModel's coroutine scope; existing screens still use TeeUpApiClient
 * pending their own migration.
 */
object TeeUpRepository {
    private const val TAG = "TeeUpRepository"
    private val api = RetrofitClient.apiService

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

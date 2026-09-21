package com.teeup.android.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Plain `HttpURLConnection` + `org.json` client — no Retrofit/OkHttp yet
 * (that's EME-294's base networking scaffold; this ticket only needed the
 * calls below, so it doesn't wait on that landing first).
 * Every function here blocks and must be called off the main thread.
 */
object TeeUpApiClient {
    fun fetchCourses(): List<Course> {
        val array = JSONArray(request("GET", "api/courses"))
        return (0 until array.length()).map { i -> parseCourse(array.getJSONObject(i)) }
    }

    /** `maxHandicap`/`pace` filter by the tee time's host (EME-299); null means "no filter". */
    fun fetchTeeTimes(maxHandicap: Double? = null, pace: Int? = null): List<TeeTime> {
        val query = buildList {
            maxHandicap?.let { add("maxHandicap=$it") }
            pace?.let { add("pace=$it") }
        }.joinToString("&")
        val path = if (query.isEmpty()) "api/teetimes" else "api/teetimes?$query"

        val array = JSONArray(request("GET", path))
        return (0 until array.length()).map { i -> parseTeeTime(array.getJSONObject(i)) }
    }

    fun fetchNotifications(): List<AppNotification> {
        val array = JSONArray(request("GET", "api/notifications"))
        return (0 until array.length()).map { i -> parseNotification(array.getJSONObject(i)) }
    }

    fun register(firebaseUid: String, displayName: String): RegisteredUser {
        val body = JSONObject().put("firebaseUid", firebaseUid).put("displayName", displayName)
        return parseRegisteredUser(JSONObject(request("POST", "api/auth/register", body)))
    }

    /**
     * PATCH /api/profiles/me. `homeCourseId`/`handicapIndex` null means "leave unset";
     * `paceOfPlay` is the PaceOfPlay enum ordinal (0=Relaxed, 1=Standard, 2=Brisk).
     */
    fun updateProfile(
        displayName: String,
        handicapIndex: Double?,
        homeCourseId: String?,
        paceOfPlay: Int,
        profileComplete: Boolean
    ): RegisteredUser {
        val body = JSONObject()
            .put("displayName", displayName)
            .put("handicapIndex", handicapIndex)
            .put("homeCourseId", homeCourseId)
            .put("paceOfPlay", paceOfPlay)
            .put("profileComplete", profileComplete)
        return parseRegisteredUser(JSONObject(request("PATCH", "api/profiles/me", body)))
    }

    fun createJoinRequest(teeTimeId: String, guestUserId: String): JoinRequest {
        val body = JSONObject().put("guestUserId", guestUserId)
        return parseJoinRequest(JSONObject(request("POST", "api/teetimes/$teeTimeId/joinrequests", body)))
    }

    fun fetchJoinRequestsForTeeTime(teeTimeId: String): List<JoinRequest> {
        val array = JSONArray(request("GET", "api/teetimes/$teeTimeId/joinrequests"))
        return (0 until array.length()).map { i -> parseJoinRequest(array.getJSONObject(i)) }
    }

    fun updateJoinRequestStatus(joinRequestId: String, status: Int): JoinRequest {
        val body = JSONObject().put("status", status)
        return parseJoinRequest(JSONObject(request("PATCH", "api/join-requests/$joinRequestId", body)))
    }

    private fun parseRegisteredUser(o: JSONObject) = RegisteredUser(
        id = o.getString("id"),
        displayName = o.getString("displayName"),
        handicapIndex = if (o.isNull("handicapIndex")) null else o.getDouble("handicapIndex"),
        homeCourseId = if (o.isNull("homeCourseId")) null else o.getString("homeCourseId"),
        paceOfPlay = o.getInt("paceOfPlay"),
        profileComplete = o.getBoolean("profileComplete")
    )

    private fun parseCourse(o: JSONObject) = Course(
        id = o.getString("id"),
        name = o.getString("name"),
        latitude = o.getDouble("latitude"),
        longitude = o.getDouble("longitude"),
        rating = if (o.isNull("rating")) null else o.getDouble("rating")
    )

    private fun parseTeeTime(o: JSONObject) = TeeTime(
        id = o.getString("id"),
        hostUserId = if (o.isNull("hostUserId")) null else o.getString("hostUserId"),
        courseId = o.getString("courseId"),
        dateTime = o.getString("dateTime"),
        openSpots = o.getInt("openSpots"),
        price = o.getDouble("price"),
        type = o.getInt("type")
    )

    private fun parseNotification(o: JSONObject) = AppNotification(
        id = o.getString("id"),
        type = o.getInt("type"),
        message = o.getString("message"),
        relatedEntityId = if (o.isNull("relatedEntityId")) null else o.getString("relatedEntityId"),
        isRead = o.getBoolean("isRead"),
        createdAt = o.getString("createdAt")
    )

    private fun parseJoinRequest(o: JSONObject) = JoinRequest(
        id = o.getString("id"),
        teeTimeId = o.getString("teeTimeId"),
        guestUserId = o.getString("guestUserId"),
        status = o.getInt("status")
    )

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        val connection = URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        authorizationHeaderOrNull()?.let { connection.setRequestProperty("Authorization", it) }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw ApiException(httpFailureMessage(code))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw ApiException("Could not connect. Check your connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    /** Null if no Firebase user is signed in, or the token fetch fails (request then goes out unauthenticated). */
    private fun authorizationHeaderOrNull(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS).token
                ?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        } catch (e: Exception) {
            null
        }
    }
}

class ApiException(message: String) : Exception(message)

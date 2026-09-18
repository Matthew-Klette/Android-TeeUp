package com.teeup.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

    fun fetchTeeTimes(): List<TeeTime> {
        val array = JSONArray(request("GET", "api/teetimes"))
        return (0 until array.length()).map { i -> parseTeeTime(array.getJSONObject(i)) }
    }

    fun register(firebaseUid: String, displayName: String): RegisteredUser {
        val body = JSONObject().put("firebaseUid", firebaseUid).put("displayName", displayName)
        val response = JSONObject(request("POST", "api/auth/register", body))
        return RegisteredUser(id = response.getString("id"), displayName = response.getString("displayName"))
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
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw ApiException("$method $path failed: HTTP $code${if (detail != null) " — $detail" else ""}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class ApiException(message: String) : Exception(message)

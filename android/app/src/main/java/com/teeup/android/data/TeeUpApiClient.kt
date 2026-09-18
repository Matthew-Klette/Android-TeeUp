package com.teeup.android.data

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plain `HttpURLConnection` + `org.json` client — no Retrofit/OkHttp yet
 * (that's EME-294's base networking scaffold; this ticket only needed the
 * two GET calls below, so it doesn't wait on that landing first).
 * Every function here blocks and must be called off the main thread.
 */
object TeeUpApiClient {
    fun fetchCourses(): List<Course> {
        val array = JSONArray(getJson("api/courses"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Course(
                id = o.getString("id"),
                name = o.getString("name"),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                rating = if (o.isNull("rating")) null else o.getDouble("rating")
            )
        }
    }

    fun fetchTeeTimes(): List<TeeTime> {
        val array = JSONArray(getJson("api/teetimes"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TeeTime(
                id = o.getString("id"),
                hostUserId = if (o.isNull("hostUserId")) null else o.getString("hostUserId"),
                courseId = o.getString("courseId"),
                dateTime = o.getString("dateTime"),
                openSpots = o.getInt("openSpots"),
                price = o.getDouble("price"),
                type = o.getInt("type")
            )
        }
    }

    private fun getJson(path: String): String {
        val connection = URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode !in 200..299) {
                throw ApiException("GET $path failed: HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class ApiException(message: String) : Exception(message)

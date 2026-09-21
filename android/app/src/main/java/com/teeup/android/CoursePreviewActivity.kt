package com.teeup.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import com.teeup.android.ui.LocaleActivity
import org.json.JSONObject

/**
 * Course Preview — an actual satellite view of the course location (Leaflet +
 * Esri World Imagery tiles, no API key needed) so a player can see the real
 * course before booking, the way apps like Wedge show a course map rather
 * than just a name and address.
 */
class CoursePreviewActivity : LocaleActivity() {
    companion object {
        const val EXTRA_COURSE_NAME = "com.teeup.android.extra.COURSE_NAME"
        const val EXTRA_COURSE_LAT = "com.teeup.android.extra.COURSE_LAT"
        const val EXTRA_COURSE_LNG = "com.teeup.android.extra.COURSE_LNG"
        const val EXTRA_COURSE_RATING = "com.teeup.android.extra.COURSE_RATING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_preview)

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: getString(R.string.course_preview_title)
        val lat = if (intent.hasExtra(EXTRA_COURSE_LAT)) intent.getDoubleExtra(EXTRA_COURSE_LAT, 0.0) else null
        val lng = if (intent.hasExtra(EXTRA_COURSE_LNG)) intent.getDoubleExtra(EXTRA_COURSE_LNG, 0.0) else null

        findViewById<TextView>(R.id.text_course_preview_title).text = courseName
        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val ratingText = findViewById<TextView>(R.id.text_course_preview_rating)
        if (intent.hasExtra(EXTRA_COURSE_RATING)) {
            val rating = intent.getDoubleExtra(EXTRA_COURSE_RATING, 0.0)
            ratingText.text = getString(R.string.course_preview_rating_format, rating)
            ratingText.visibility = View.VISIBLE
        }

        val webView = findViewById<WebView>(R.id.webview_course_map)
        val statusText = findViewById<TextView>(R.id.text_course_preview_status)
        val openInMapsButton = findViewById<Button>(R.id.button_open_in_maps)

        if (lat == null || lng == null) {
            statusText.text = getString(R.string.course_preview_error)
            statusText.visibility = View.VISIBLE
            openInMapsButton.isEnabled = false
            return
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                statusText.visibility = View.GONE
            }
        }
        webView.loadDataWithBaseURL(
            "https://appassets.teeup.local/",
            buildMapHtml(lat, lng, courseName),
            "text/html",
            "utf-8",
            null
        )

        openInMapsButton.setOnClickListener {
            val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(courseName)})")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        }
    }

    /** Satellite basemap centered/zoomed on the course, with a marker and an
     *  approximate footprint circle — there's no hole-by-hole boundary data
     *  from the API yet, so this is the closest honest approximation of a
     *  course flyover rather than a fake hole layout. */
    private fun buildMapHtml(lat: Double, lng: Double, courseName: String): String {
        val safeName = JSONObject.quote(courseName)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <style>
                    html, body, #map { height: 100%; margin: 0; padding: 0; background: #161C18; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <script>
                    var map = L.map('map', { zoomControl: true }).setView([$lat, $lng], 17);
                    L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                        maxZoom: 19,
                        attribution: 'Esri, Maxar, Earthstar Geographics'
                    }).addTo(map);
                    L.marker([$lat, $lng]).addTo(map).bindPopup($safeName).openPopup();
                    L.circle([$lat, $lng], {
                        radius: 300,
                        color: '#4EA65C',
                        weight: 2,
                        fillColor: '#4EA65C',
                        fillOpacity: 0.08
                    }).addTo(map);
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

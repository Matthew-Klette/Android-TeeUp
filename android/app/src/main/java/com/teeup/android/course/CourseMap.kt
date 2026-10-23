package com.teeup.android.course

/**
 * The course preview's satellite map (Leaflet + Esri World Imagery tiles, no API key needed) and
 * the links that open the course in a maps app. Pure strings, so they're unit-tested.
 */
object CourseMap {
    /** A `geo:` URI centred on the course and labelled with its name; [encodedName] is already
     *  URI-encoded (Uri.encode). */
    fun geoUri(lat: Double, lng: Double, encodedName: String): String = "geo:$lat,$lng?q=$lat,$lng($encodedName)"

    /** Google Maps in the browser, for phones with no app that handles `geo:`. */
    fun webUrl(lat: Double, lng: Double): String = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"

    /**
     * Satellite basemap centred and zoomed on the course, with a marker and an approximate
     * footprint circle. There's no hole-by-hole boundary data from the API yet, so this is the
     * closest honest approximation of a course flyover rather than a fake hole layout.
     *
     * [quotedName] is a JavaScript string literal (JSONObject.quote). The map runs full-bleed
     * under the status bar, so the zoom buttons and attribution sit top-right, pushed down by
     * [topInsetScript]: clear of the back button on the left and the course card at the bottom.
     */
    fun html(lat: Double, lng: Double, quotedName: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>
                html, body, #map { height: 100%; margin: 0; padding: 0; background: #161C18; }
                .leaflet-top { margin-top: var(--top-inset, 0px); }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
                var map = L.map('map', { zoomControl: true }).setView([$lat, $lng], 17);
                map.zoomControl.setPosition('topright');
                map.attributionControl.setPosition('topright');
                L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                    maxZoom: 19,
                    attribution: 'Esri, Maxar, Earthstar Geographics'
                }).addTo(map);
                L.marker([$lat, $lng]).addTo(map).bindPopup($quotedName).openPopup();
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

    /** Moves the top map controls [px] CSS pixels down, below the status bar. Applied once the
     *  page has loaded, when the window's insets are known. */
    fun topInsetScript(px: Int): String = "document.documentElement.style.setProperty('--top-inset', '${px}px')"
}

package com.teeup.android.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMapTest {
    @Test
    fun geoUri_centresOnCourseAndLabelsIt() {
        assertEquals("geo:-33.9,18.4?q=-33.9,18.4(Royal%20Cape)", CourseMap.geoUri(-33.9, 18.4, "Royal%20Cape"))
    }

    @Test
    fun webUrl_isGoogleMapsSearchFallback() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=-33.9,18.4", CourseMap.webUrl(-33.9, 18.4))
    }

    @Test
    fun html_centresSatelliteMapOnCourse() {
        val html = CourseMap.html(-33.9, 18.4, "\"Royal Cape\"")
        assertTrue(html.contains("setView([-33.9, 18.4], 17)"))
        assertTrue(html.contains("World_Imagery"))
        assertTrue(html.contains("L.circle([-33.9, 18.4]"))
    }

    @Test
    fun html_usesTheAlreadyQuotedNameVerbatim() {
        val html = CourseMap.html(0.0, 0.0, "\"O'Neil \\\"Links\\\"\"")
        assertTrue(html.contains("bindPopup(\"O'Neil \\\"Links\\\"\")"))
    }

    @Test
    fun html_keepsZoomAndAttribution_topRight_offsetByTheTopInset() {
        val html = CourseMap.html(0.0, 0.0, "\"x\"")
        assertTrue(html.contains("map.zoomControl.setPosition('topright')"))
        assertTrue(html.contains("map.attributionControl.setPosition('topright')"))
        assertTrue(html.contains(".leaflet-top { margin-top: var(--top-inset, 0px); }"))
    }

    @Test
    fun topInsetScript_setsTheCssVariableInPixels() {
        assertEquals(
            "document.documentElement.style.setProperty('--top-inset', '48px')",
            CourseMap.topInsetScript(48)
        )
    }
}

package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.teeup.android.course.CourseMap
import com.teeup.android.course.CoursePreviewActions
import com.teeup.android.course.CoursePreviewArgs
import com.teeup.android.course.CoursePreviewScreen
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.ui.LocaleComponentActivity

/**
 * Course Preview — an actual satellite view of the course location so a player can see the real
 * course before booking, the way apps like Wedge show a course map rather than just a name and
 * address. The UI is [CoursePreviewScreen]; this Activity reads the extras and navigates.
 */
class CoursePreviewActivity : LocaleComponentActivity() {
    companion object {
        /** Optional; when set, the Hole Map button is shown. */
        const val EXTRA_COURSE_ID = "com.teeup.android.extra.COURSE_ID"
        const val EXTRA_COURSE_NAME = "com.teeup.android.extra.COURSE_NAME"
        const val EXTRA_COURSE_LAT = "com.teeup.android.extra.COURSE_LAT"
        const val EXTRA_COURSE_LNG = "com.teeup.android.extra.COURSE_LNG"
        const val EXTRA_COURSE_RATING = "com.teeup.android.extra.COURSE_RATING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons: the satellite imagery runs underneath and it's dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val args = CoursePreviewArgs(
            courseId = intent.getStringExtra(EXTRA_COURSE_ID),
            name = intent.getStringExtra(EXTRA_COURSE_NAME) ?: getString(R.string.course_preview_title),
            lat = doubleExtraOrNull(EXTRA_COURSE_LAT),
            lng = doubleExtraOrNull(EXTRA_COURSE_LNG),
            rating = doubleExtraOrNull(EXTRA_COURSE_RATING)
        )
        val actions = CoursePreviewActions(
            back = {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            openHoleMap = { courseId ->
                startActivity(Intent(this, HoleMapActivity::class.java)
                    .putExtra(HoleMapActivity.EXTRA_COURSE_ID, courseId)
                    .putExtra(HoleMapActivity.EXTRA_COURSE_NAME, args.name))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
            },
            openInMaps = { lat, lng -> openInMaps(lat, lng, args.name) }
        )
        setContent {
            TeeUpTheme { CoursePreviewScreen(args, actions) }
        }
    }

    private fun doubleExtraOrNull(key: String): Double? =
        if (intent.hasExtra(key)) intent.getDoubleExtra(key, 0.0) else null

    private fun openInMaps(lat: Double, lng: Double, name: String) {
        val mapIntent = Intent(Intent.ACTION_VIEW, CourseMap.geoUri(lat, lng, Uri.encode(name)).toUri())
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, CourseMap.webUrl(lat, lng).toUri()))
        }
    }
}

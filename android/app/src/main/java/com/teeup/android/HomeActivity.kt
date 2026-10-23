package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.teeup.android.data.Course
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.home.HomeActions
import com.teeup.android.home.HomeScreen
import com.teeup.android.home.HomeViewModel
import com.teeup.android.nav.BottomNav
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Home: open groups looking for players. The first screen on the iOS-style design system
 * (com.teeup.android.design); the UI is [HomeScreen] and the state lives in [HomeViewModel].
 * This Activity only draws edge to edge and turns the screen's actions into navigation.
 */
class HomeActivity : LocaleComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Content runs under transparent system bars, with dark icons on the light page.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val actions = HomeActions(
            openGroup = ::openTeeTimeDetail,
            previewCourse = ::openCoursePreview,
            openNotifications = { open(Intent(this, NotificationsActivity::class.java)) },
            createGroup = { open(Intent(this, CreateGroupActivity::class.java)) },
            openTab = { tab -> BottomNav.open(this, tab) }
        )
        setContent {
            TeeUpTheme { HomeScreen(viewModel, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up a group just created or joined elsewhere. The ViewModel's own first load
        // already covers the first resume.
        if (hasResumedOnce) viewModel.load()
        hasResumedOnce = true
    }

    private fun open(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun openTeeTimeDetail(teeTimeId: String) {
        open(Intent(this, TeeTimeDetailActivity::class.java).putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, teeTimeId))
    }

    private fun openCoursePreview(course: Course?) {
        if (course == null) {
            TeeUpBanner.show(this, getString(R.string.teetime_course_unavailable))
            return
        }
        open(Intent(this, CoursePreviewActivity::class.java).apply {
            putExtra(CoursePreviewActivity.EXTRA_COURSE_ID, course.id)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_NAME, course.name)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LAT, course.latitude)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LNG, course.longitude)
            course.rating?.let { putExtra(CoursePreviewActivity.EXTRA_COURSE_RATING, it) }
        })
    }
}

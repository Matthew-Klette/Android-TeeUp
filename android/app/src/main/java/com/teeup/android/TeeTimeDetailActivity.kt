package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.teetime.DetailEvent
import com.teeup.android.teetime.TeeTimeDetailActions
import com.teeup.android.teetime.TeeTimeDetailScreen
import com.teeup.android.teetime.TeeTimeDetailViewModel
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.showNotice
import kotlinx.coroutines.launch

/**
 * Screen 3: Tee Time Detail / Join Request. The UI is [TeeTimeDetailScreen] and the state and
 * API calls live in [TeeTimeDetailViewModel]; this Activity draws edge to edge, shows the
 * ViewModel's banners and turns the screen's actions into navigation. Group Chat is out of scope.
 */
class TeeTimeDetailActivity : LocaleComponentActivity() {
    companion object {
        /** Set by HomeActivity when opening a specific tee time. */
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

    private val viewModel: TeeTimeDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val actions = TeeTimeDetailActions(
            back = {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            previewCourse = ::openCoursePreview,
            editGroup = ::openEditGroup
        )
        setContent {
            TeeUpTheme { TeeTimeDetailScreen(viewModel, actions) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handle)
            }
        }
    }

    /** Reloads on every return to this screen (not just the first open) so an edit made via
     *  CreateGroupActivity's edit mode, or a cancel/delete/withdraw here, is reflected without
     *  a manual refresh. */
    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun handle(event: DetailEvent) {
        when (event) {
            is DetailEvent.Banner -> showNotice(event.notice)
            DetailEvent.SignInRequired -> startActivity(Intent(this, SignInActivity::class.java))
            DetailEvent.Deleted -> finish()
        }
    }

    private fun openEditGroup(teeTime: TeeTime) {
        val intent = Intent(this, CreateGroupActivity::class.java).apply {
            putExtra(CreateGroupActivity.EXTRA_EDIT_TEE_TIME_ID, teeTime.id)
            putExtra(CreateGroupActivity.EXTRA_EDIT_COURSE_ID, teeTime.courseId)
            putExtra(CreateGroupActivity.EXTRA_EDIT_DATE_TIME_ISO, teeTime.dateTime)
            putExtra(CreateGroupActivity.EXTRA_EDIT_HOLES, teeTime.holes ?: 18)
            putExtra(CreateGroupActivity.EXTRA_EDIT_OPEN_SPOTS, teeTime.openSpots)
            teeTime.wantedHandicapMin?.let { putExtra(CreateGroupActivity.EXTRA_EDIT_HANDICAP_MIN, it) }
            teeTime.wantedHandicapMax?.let { putExtra(CreateGroupActivity.EXTRA_EDIT_HANDICAP_MAX, it) }
            teeTime.wantedPace?.let { putExtra(CreateGroupActivity.EXTRA_EDIT_PACE, it) }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun openCoursePreview(course: Course?) {
        if (course == null) {
            TeeUpBanner.show(this, getString(R.string.teetime_course_unavailable))
            return
        }
        val intent = Intent(this, CoursePreviewActivity::class.java).apply {
            putExtra(CoursePreviewActivity.EXTRA_COURSE_ID, course.id)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_NAME, course.name)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LAT, course.latitude)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LNG, course.longitude)
            course.rating?.let { putExtra(CoursePreviewActivity.EXTRA_COURSE_RATING, it) }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }
}

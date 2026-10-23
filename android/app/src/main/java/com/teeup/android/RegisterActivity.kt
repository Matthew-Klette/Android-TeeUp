package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.auth.RegisterActions
import com.teeup.android.auth.RegisterScreen
import com.teeup.android.auth.RegisterUiState
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.profile.DisplayName
import com.teeup.android.profile.HandicapEntry
import com.teeup.android.profile.PlayingHandicap
import com.teeup.android.ui.CourseSearch
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.enableTeeUpEdgeToEdge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Register / profile setup, shown right after a new user's first sign-in (EME-296). Captures
 * the fields sign-in didn't ask for — handicap, home course, pace of play — then marks the
 * profile complete. Home course is live search (GET /api/courses?search=) rather than a fixed
 * dropdown, so it scales to however many courses exist. The UI is [RegisterScreen].
 */
class RegisterActivity : LocaleComponentActivity() {
    private val tag = "RegisterActivity"
    private val ui = RegisterUiState(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty())
    private val courseSearch by lazy { CourseSearch(lifecycleScope, skip = { it == ui.selectedCourse?.name }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableTeeUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { courseSearch.results.collect { ui.suggestions = it } }
        val actions = RegisterActions(
            updateCourseQuery = { text ->
                ui.courseQuery = text
                if (text != ui.selectedCourse?.name) ui.selectedCourse = null
                courseSearch.search(text)
            },
            selectCourse = { course ->
                ui.selectedCourse = course
                ui.courseQuery = course.name
                ui.suggestions = emptyList()
                courseSearch.search(course.name)
            },
            submit = ::onContinueClicked
        )
        setContent { TeeUpTheme { RegisterScreen(ui, actions) } }
    }

    private fun onContinueClicked() {
        if (ui.saving) return
        val displayName = DisplayName.validOrNull(ui.name) ?: run {
            TeeUpBanner.show(this, getString(R.string.register_error_name_required), isError = true)
            return
        }
        val handicapIndex = when (val entry = PlayingHandicap.parse(ui.handicap)) {
            HandicapEntry.Invalid -> {
                TeeUpBanner.show(this, getString(R.string.register_error_handicap_invalid), isError = true)
                return
            }
            is HandicapEntry.Value -> entry.handicap
        }
        val homeCourseId = ui.selectedCourse?.id
        val pace = ui.pace

        ui.saving = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    TeeUpApiClient.updateProfile(
                        displayName = displayName,
                        handicapIndex = handicapIndex,
                        homeCourseId = homeCourseId,
                        paceOfPlay = pace,
                        profileComplete = true
                    )
                }
                startActivity(Intent(this@RegisterActivity, HomeActivity::class.java))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Profile save failed", e)
                ui.saving = false
                TeeUpBanner.show(this@RegisterActivity, e.message ?: getString(R.string.register_error_save_failed), isError = true)
            }
        }
    }
}

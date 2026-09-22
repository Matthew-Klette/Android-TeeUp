package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.Course
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatHandicapOrEmpty
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Profile → Playing Details. Same PATCH /api/profiles/me as PersonalDetailsActivity,
 * editing handicap/home course/pace of play this time; displayName carries through
 * unchanged. Course spinner mirrors RegisterActivity's exact pattern.
 */
class PlayingDetailsActivity : LocaleActivity() {
    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var handicapInput: EditText
    private lateinit var courseSpinner: Spinner
    private lateinit var paceSpinner: Spinner
    private lateinit var saveButton: Button

    private var current: RegisteredUser? = null
    private var courses: List<Course> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playing_details)

        statusText = findViewById(R.id.text_status)
        contentGroup = findViewById(R.id.group_content)
        handicapInput = findViewById(R.id.input_handicap)
        courseSpinner = findViewById(R.id.input_home_course)
        paceSpinner = findViewById(R.id.input_pace)
        saveButton = findViewById(R.id.button_save)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        paceSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.pace_of_play_options)
        ).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }

        saveButton.setOnClickListener { onSaveClicked() }

        loadProfile()
    }

    private fun loadProfile() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            statusText.text = getString(R.string.profile_load_signed_out)
            return
        }

        Thread {
            try {
                val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
                val fetchedCourses = TeeUpApiClient.fetchCourses()
                runOnUiThread { render(user, fetchedCourses) }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = e.message ?: getString(R.string.profile_load_error_fallback) }
            }
        }.start()
    }

    private fun render(user: RegisteredUser, fetchedCourses: List<Course>) {
        current = user
        courses = fetchedCourses
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE

        handicapInput.setText(formatHandicapOrEmpty(user.handicapIndex))

        val labels = listOf(getString(R.string.register_home_course_none)) + fetchedCourses.map { it.name }
        courseSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, labels).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        val selectedCourseIndex = fetchedCourses.indexOfFirst { it.id == user.homeCourseId }
        courseSpinner.setSelection(if (selectedCourseIndex >= 0) selectedCourseIndex + 1 else 0)

        paceSpinner.setSelection(user.paceOfPlay.coerceIn(0, 2))
    }

    private fun onSaveClicked() {
        val existing = current ?: return

        val handicapText = handicapInput.text.toString().trim()
        val handicapIndex: Double? = when {
            handicapText.isEmpty() -> null
            else -> handicapText.toDoubleOrNull()?.takeIf { it in 0.0..54.0 }
                ?: run {
                    TeeUpBanner.show(this, getString(R.string.playing_details_handicap_invalid), isError = true)
                    return
                }
        }

        // Index 0 is "No home course yet" (courseSpinner.selectedItemPosition - 1 into courses).
        val homeCourseId = (courseSpinner.selectedItemPosition - 1)
            .takeIf { it in courses.indices }
            ?.let { courses[it].id }

        saveButton.isEnabled = false
        saveButton.setText(R.string.playing_details_saving)

        Thread {
            try {
                TeeUpApiClient.updateProfile(
                    displayName = existing.displayName,
                    handicapIndex = handicapIndex,
                    homeCourseId = homeCourseId,
                    paceOfPlay = paceSpinner.selectedItemPosition,
                    profileComplete = true
                )
                runOnUiThread {
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    saveButton.isEnabled = true
                    saveButton.setText(R.string.playing_details_save)
                    TeeUpBanner.show(this, e.message ?: getString(R.string.profile_save_error_fallback), isError = true)
                }
            }
        }.start()
    }
}

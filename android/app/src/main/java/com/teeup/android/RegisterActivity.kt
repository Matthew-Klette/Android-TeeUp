package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Register / profile setup, shown right after a new user's first Google
 * sign-in (EME-296). Captures the fields EME-291/295 didn't ask for yet —
 * handicap, home course, pace of play — then marks the profile complete.
 */
class RegisterActivity : LocaleActivity() {
    private val tag = "RegisterActivity"

    private lateinit var nameInput: EditText
    private lateinit var handicapInput: EditText
    private lateinit var courseSpinner: Spinner
    private lateinit var paceSpinner: Spinner
    private lateinit var continueButton: Button

    private var courses: List<Course> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameInput = findViewById(R.id.input_display_name)
        handicapInput = findViewById(R.id.input_handicap)
        courseSpinner = findViewById(R.id.input_home_course)
        paceSpinner = findViewById(R.id.input_pace)
        continueButton = findViewById(R.id.button_continue)

        nameInput.setText(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty())

        paceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.pace_of_play_options)
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        paceSpinner.setSelection(PACE_STANDARD_INDEX)

        populateCourseSpinner(emptyList())
        loadCourses()

        continueButton.setOnClickListener { onContinueClicked() }
    }

    private fun loadCourses() {
        Thread {
            try {
                val fetched = TeeUpApiClient.fetchCourses()
                runOnUiThread {
                    courses = fetched
                    populateCourseSpinner(fetched)
                }
            } catch (e: Exception) {
                // Home course is optional — a failed fetch just leaves the "No home
                // course yet" option, it shouldn't block registration.
                Log.w(tag, "Couldn't load courses for home course picker", e)
            }
        }.start()
    }

    private fun populateCourseSpinner(courseList: List<Course>) {
        val labels = listOf(getString(R.string.register_home_course_none)) + courseList.map { it.name }
        courseSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun onContinueClicked() {
        val displayName = nameInput.text.toString().trim()
        if (displayName.isEmpty()) {
            TeeUpBanner.show(this, "Enter a display name", isError = true)
            return
        }

        val handicapText = handicapInput.text.toString().trim()
        val handicapIndex: Double? = when {
            handicapText.isEmpty() -> null
            else -> handicapText.toDoubleOrNull()?.takeIf { it in 0.0..54.0 }
                ?: run {
                    TeeUpBanner.show(this, "Enter a valid handicap between 0 and 54, or leave it blank", isError = true)
                    return
                }
        }

        // Index 0 is "No home course yet" (courseSpinner.selectedItemPosition - 1 into courses).
        val homeCourseId = (courseSpinner.selectedItemPosition - 1)
            .takeIf { it in courses.indices }
            ?.let { courses[it].id }

        continueButton.isEnabled = false
        continueButton.setText(R.string.register_saving)

        Thread {
            try {
                TeeUpApiClient.updateProfile(
                    displayName = displayName,
                    handicapIndex = handicapIndex,
                    homeCourseId = homeCourseId,
                    paceOfPlay = paceSpinner.selectedItemPosition,
                    profileComplete = true
                )
                runOnUiThread {
                    startActivity(Intent(this, HomeActivity::class.java))
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    finish()
                }
            } catch (e: Exception) {
                Log.w(tag, "Profile save failed", e)
                runOnUiThread {
                    continueButton.isEnabled = true
                    continueButton.setText(R.string.register_continue)
                    TeeUpBanner.show(this, e.message ?: "Couldn't save your profile — try again", isError = true)
                }
            }
        }.start()
    }

    companion object {
        private const val PACE_STANDARD_INDEX = 1
    }
}

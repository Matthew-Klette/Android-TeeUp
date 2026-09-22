package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.CourseSearchAdapter
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Register / profile setup, shown right after a new user's first Google
 * sign-in (EME-296). Captures the fields EME-291/295 didn't ask for yet —
 * handicap, home course, pace of play — then marks the profile complete.
 *
 * Home course is a search-as-you-type field (CourseSearchAdapter, GET
 * /api/courses?search=) rather than a fixed dropdown — with a growing course
 * catalog, a Spinner meant scrolling through every course to find one, and
 * previously the picker only ever showed whatever the initial unfiltered
 * fetch happened to return.
 */
class RegisterActivity : LocaleActivity() {
    private val tag = "RegisterActivity"

    private lateinit var nameInput: EditText
    private lateinit var handicapInput: EditText
    private lateinit var courseInput: AutoCompleteTextView
    private lateinit var paceSpinner: Spinner
    private lateinit var continueButton: Button

    /** Set when a suggestion is tapped; cleared as soon as the typed text no longer matches
     *  it (see the TextWatcher below) — an empty/edited field means "no home course". */
    private var selectedCourse: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameInput = findViewById(R.id.input_display_name)
        handicapInput = findViewById(R.id.input_handicap)
        courseInput = findViewById(R.id.input_home_course)
        paceSpinner = findViewById(R.id.input_pace)
        continueButton = findViewById(R.id.button_continue)

        nameInput.setText(FirebaseAuth.getInstance().currentUser?.displayName.orEmpty())

        paceSpinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.pace_of_play_options)
        ).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
        paceSpinner.setSelection(PACE_STANDARD_INDEX)

        wireCourseSearch()

        continueButton.setOnClickListener { onContinueClicked() }
    }

    private fun wireCourseSearch() {
        val adapter = CourseSearchAdapter(this)
        courseInput.setAdapter(adapter)
        courseInput.setOnItemClickListener { _, _, position, _ ->
            selectedCourse = adapter.getItem(position)
        }
        courseInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.toString() != selectedCourse?.name) selectedCourse = null
            }
        })
    }

    private fun onContinueClicked() {
        val displayName = nameInput.text.toString().trim()
        if (displayName.isEmpty()) {
            TeeUpBanner.show(this, getString(R.string.register_error_name_required), isError = true)
            return
        }

        val handicapText = handicapInput.text.toString().trim()
        val handicapIndex: Double? = when {
            handicapText.isEmpty() -> null
            else -> handicapText.toDoubleOrNull()?.takeIf { it in 0.0..54.0 }
                ?: run {
                    TeeUpBanner.show(this, getString(R.string.register_error_handicap_invalid), isError = true)
                    return
                }
        }

        val homeCourseId = selectedCourse?.id

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
                    TeeUpBanner.show(this, e.message ?: getString(R.string.register_error_save_failed), isError = true)
                }
            }
        }.start()
    }

    companion object {
        private const val PACE_STANDARD_INDEX = 1
    }
}

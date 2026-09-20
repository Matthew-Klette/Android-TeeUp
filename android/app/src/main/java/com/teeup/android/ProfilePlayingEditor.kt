package com.teeup.android

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.teeup.android.data.Course
import com.teeup.android.data.ProfileUpdateRequest
import com.teeup.android.data.TeeUpRepository
import com.teeup.android.data.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Playing-details dialog used by the existing ProfileActivity. */
object ProfilePlayingEditor {
    fun show(
        activity: ComponentActivity,
        profile: UserProfile,
        onSave: (ProfileUpdateRequest) -> Unit
    ) {
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)
        }

        fun addField(labelRes: Int, field: View) {
            field.id = View.generateViewId()

            form.addView(TextView(activity).apply {
                setText(labelRes)
                labelFor = field.id
            })

            form.addView(
                field,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val handicapInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
            setText(profile.handicapIndex?.toString().orEmpty())
            setHint(R.string.register_handicap_hint)
        }

        addField(R.string.register_handicap, handicapInput)

        form.addView(TextView(activity).apply {
            setText(R.string.profile_handicap_help)
        })

        val courseSpinner = Spinner(activity)
        addField(R.string.register_home_course, courseSpinner)

        val courseStatus = TextView(activity).apply {
            setText(R.string.profile_courses_loading)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        form.addView(courseStatus)

        val paceSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_item,
                activity.resources.getStringArray(R.array.pace_of_play_options)
            ).apply {
                setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }

            setSelection(
                profile.paceOfPlay.takeIf { it in 0..2 } ?: -1
            )
        }
        addField(R.string.register_pace, paceSpinner)

        var courses = emptyList<Course>()

        fun populateCourses() {
            val existingName = courses
                .firstOrNull { it.id == profile.homeCourseId }
                ?.name

            val keepLabel = if (existingName == null) {
                activity.getString(R.string.profile_course_keep)
            } else {
                activity.getString(
                    R.string.profile_course_keep_named,
                    existingName
                )
            }

            val labels = listOf(
                keepLabel,
                activity.getString(R.string.profile_course_remove)
            ) + courses.map { it.name }

            courseSpinner.adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_item,
                labels
            ).apply {
                setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
                )
            }
        }

        populateCourses()

        val scroll = ScrollView(activity).apply {
            addView(form)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.profile_playing_details)
            .setView(scroll)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = handicapInput.text.toString().trim()
                    .replace(',', '.')

                val handicap = text.toBigDecimalOrNull()

                if (text.isNotEmpty() &&
                    (handicap == null ||
                            handicap < BigDecimal.ZERO ||
                            handicap > BigDecimal("54") ||
                            handicap.stripTrailingZeros().scale() > 1)
                ) {
                    handicapInput.error =
                        activity.getString(R.string.profile_handicap_invalid)
                    return@setOnClickListener
                }

                val pace = paceSpinner.selectedItemPosition
                if (pace !in 0..2) {
                    Toast.makeText(
                        activity,
                        R.string.profile_pace_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val coursePosition = courseSpinner.selectedItemPosition
                val selectedCourse = courses.getOrNull(coursePosition - 2)

                onSave(
                    ProfileUpdateRequest(
                        handicapIndex = handicap?.toDouble(),
                        clearHandicapIndex = text.isEmpty(),
                        paceOfPlay = pace,
                        homeCourseId = selectedCourse?.id,
                        clearHomeCourse = coursePosition == 1
                    )
                )

                dialog.dismiss()
            }
        }

        dialog.show()

        // Fetching courses does not prevent editing handicap or pace.
        val courseJob = activity.lifecycleScope.launch {
            try {
                val fetched = TeeUpRepository.getCourses()
                if (!dialog.isShowing) return@launch

                // Preserve an explicit "No home course" selection while loading.
                val previousSelection = courseSpinner.selectedItemPosition
                courses = fetched
                populateCourses()
                courseSpinner.setSelection(
                    if (previousSelection == 1) 1 else 0
                )

                courseStatus.setText(R.string.profile_courses_empty)
                courseStatus.visibility =
                    if (courses.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (dialog.isShowing) {
                    courseStatus.setText(R.string.profile_courses_failed)
                    courseStatus.visibility = View.VISIBLE
                }
            }
        }

        dialog.setOnDismissListener {
            courseJob.cancel()
        }
    }
}
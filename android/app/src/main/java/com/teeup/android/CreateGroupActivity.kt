package com.teeup.android

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.handicapValue
import com.teeup.android.data.roundTimestamp
import com.teeup.android.data.validHandicap
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner
import java.util.Calendar
import java.util.TimeZone

/**
 * EME-311: create a real group looking for players — course, date/time, holes, guests wanted,
 * wanted handicap range, wanted pace. Posts to POST /api/teetimes/groups. Reachable from Home
 * (see HomeActivity's "Create a Group" button); the groups-list UI itself is EME-312's job, kept
 * deliberately separate from this screen.
 */
class CreateGroupActivity : LocaleActivity() {
    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var courseSpinner: Spinner
    private lateinit var pickDateTimeButton: Button
    private lateinit var holesSpinner: Spinner
    private lateinit var playersNeededText: TextView
    private lateinit var handicapMinInput: EditText
    private lateinit var handicapMaxInput: EditText
    private lateinit var paceSpinner: Spinner
    private lateinit var createButton: Button

    private var courses: List<Course> = emptyList()
    private var pickedDateTimeIso: String? = null
    private var playersNeeded = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        statusText = findViewById(R.id.text_status)
        contentGroup = findViewById(R.id.group_content)
        courseSpinner = findViewById(R.id.input_course)
        pickDateTimeButton = findViewById(R.id.button_pick_date_time)
        holesSpinner = findViewById(R.id.input_holes)
        playersNeededText = findViewById(R.id.text_players_needed)
        handicapMinInput = findViewById(R.id.input_handicap_min)
        handicapMaxInput = findViewById(R.id.input_handicap_max)
        paceSpinner = findViewById(R.id.input_pace)
        createButton = findViewById(R.id.button_create)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        holesSpinner.adapter = ArrayAdapter(
            this, R.layout.spinner_item, resources.getStringArray(R.array.create_group_holes_options)
        ).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
        holesSpinner.setSelection(1) // default to 18 holes

        val paceLabels = listOf(getString(R.string.filter_pace_any)) + resources.getStringArray(R.array.pace_of_play_options)
        paceSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, paceLabels).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }

        pickDateTimeButton.setOnClickListener { showDateTimePicker() }
        findViewById<Button>(R.id.button_players_minus).setOnClickListener { changePlayersNeeded(-1) }
        findViewById<Button>(R.id.button_players_plus).setOnClickListener { changePlayersNeeded(1) }
        createButton.setOnClickListener { onCreateClicked() }

        updatePlayersNeededDisplay()
        loadCourses()
    }

    private fun loadCourses() {
        Thread {
            try {
                val fetched = TeeUpApiClient.fetchCourses()
                runOnUiThread { render(fetched) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = e.message ?: getString(R.string.create_group_no_courses)
                }
            }
        }.start()
    }

    private fun render(fetchedCourses: List<Course>) {
        courses = fetchedCourses
        if (fetchedCourses.isEmpty()) {
            statusText.text = getString(R.string.create_group_no_courses)
            return
        }
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE
        courseSpinner.adapter = ArrayAdapter(
            this, R.layout.spinner_item, fetchedCourses.map { it.name }
        ).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
    }

    private fun showDateTimePicker() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                showTimePicker(now) { hourOfDay, minute ->
                    pickedDateTimeIso = "%04d-%02d-%02dT%02d:%02d:00Z".format(year, month + 1, dayOfMonth, hourOfDay, minute)
                    pickDateTimeButton.text = formatTeeTime(pickedDateTimeIso!!)
                }
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = now.timeInMillis }.show()
    }

    /**
     * A custom scrollable hour/minute picker rather than the platform TimePickerDialog: the
     * "spinner" number-wheel mode (`android:timePickerMode`) that used to look like this was
     * effectively deprecated by the OS around API 29+, and most devices silently ignore it and
     * always render the analog clock-face dial regardless — this sidesteps that entirely.
     */
    private fun showTimePicker(defaultTime: Calendar, onPicked: (hourOfDay: Int, minute: Int) -> Unit) {
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 23
            value = defaultTime.get(Calendar.HOUR_OF_DAY)
            setFormatter { "%02d".format(it) }
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            value = defaultTime.get(Calendar.MINUTE)
            setFormatter { "%02d".format(it) }
        }

        val separator = TextView(this).apply {
            text = ":"
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(dp(8), 0, dp(8), 0)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(hourPicker)
            addView(separator)
            addView(minutePicker)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_group_pick_time_title)
            .setView(container)
            .setPositiveButton(R.string.filter_apply) { _, _ -> onPicked(hourPicker.value, minutePicker.value) }
            .setNegativeButton(R.string.filter_cancel, null)
            .show()
    }

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun changePlayersNeeded(delta: Int) {
        playersNeeded = (playersNeeded + delta).coerceIn(1, 20)
        updatePlayersNeededDisplay()
    }

    private fun updatePlayersNeededDisplay() {
        playersNeededText.text = playersNeeded.toString()
    }

    private fun onCreateClicked() {
        val courseIndex = courseSpinner.selectedItemPosition
        val course = courses.getOrNull(courseIndex)
        if (course == null) {
            TeeUpBanner.show(this, getString(R.string.create_group_no_courses), isError = true)
            return
        }

        val dateTimeIso = pickedDateTimeIso
        if (dateTimeIso == null) {
            TeeUpBanner.show(this, getString(R.string.create_group_pick_date_time_error), isError = true)
            return
        }
        if (roundTimestamp(dateTimeIso) <= System.currentTimeMillis()) {
            TeeUpBanner.show(this, getString(R.string.create_group_date_time_in_past), isError = true)
            return
        }

        val minText = handicapMinInput.text.toString()
        val maxText = handicapMaxInput.text.toString()
        if (!validHandicap(minText)) {
            handicapMinInput.error = getString(R.string.playing_details_handicap_invalid)
            return
        }
        if (!validHandicap(maxText)) {
            handicapMaxInput.error = getString(R.string.playing_details_handicap_invalid)
            return
        }
        val handicapMin = handicapValue(minText)
        val handicapMax = handicapValue(maxText)
        if (handicapMin != null && handicapMax != null && handicapMin > handicapMax) {
            TeeUpBanner.show(this, getString(R.string.create_group_handicap_range_invalid), isError = true)
            return
        }

        val holes = if (holesSpinner.selectedItemPosition == 0) 9 else 18
        val pace = (paceSpinner.selectedItemPosition - 1).takeIf { it in 0..2 }

        createButton.isEnabled = false
        createButton.text = getString(R.string.create_group_creating)

        Thread {
            try {
                TeeUpApiClient.createGroup(
                    courseId = course.id,
                    dateTimeIso = dateTimeIso,
                    holes = holes,
                    openSpots = playersNeeded,
                    wantedHandicapMin = handicapMin,
                    wantedHandicapMax = handicapMax,
                    wantedPace = pace
                )
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.create_group_created))
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    createButton.isEnabled = true
                    createButton.text = getString(R.string.create_group_create)
                    TeeUpBanner.show(this, e.message ?: getString(R.string.create_group_failed_fallback), isError = true)
                }
            }
        }.start()
    }
}

package com.teeup.android

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.handicapValue
import com.teeup.android.data.roundTimestamp
import com.teeup.android.data.validHandicap
import com.teeup.android.ui.CourseSearchAdapter
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * EME-311: create a real group looking for players — course, date/time, holes, guests wanted,
 * wanted handicap range, wanted pace. Posts to POST /api/teetimes/groups. Reachable from Home
 * (see HomeActivity's "Create a Group" button); the groups-list UI itself is EME-312's job, kept
 * deliberately separate from this screen. Course is a search-as-you-type field
 * (CourseSearchAdapter, GET /api/courses?search=), not a fixed dropdown — matches Register/
 * Playing Details' home course picker.
 *
 * EME-321: also doubles as the edit screen for an existing group — TeeTimeDetailActivity's Edit
 * button starts this Activity with [EXTRA_EDIT_TEE_TIME_ID] set and the group's current values
 * as the other EXTRA_EDIT_* extras, which switches the form to pre-filled/edit mode and PATCHes
 * instead of POSTing on save. The course isn't editable in that mode (see EditAsync's doc
 * comment), so the course field is locked (disabled) and pre-filled by resolving the group's
 * existing course id to a name — it isn't sent in the edit request at all.
 */
class CreateGroupActivity : LocaleActivity() {
    companion object {
        const val EXTRA_EDIT_TEE_TIME_ID = "com.teeup.android.extra.EDIT_TEE_TIME_ID"
        const val EXTRA_EDIT_COURSE_ID = "com.teeup.android.extra.EDIT_COURSE_ID"
        const val EXTRA_EDIT_DATE_TIME_ISO = "com.teeup.android.extra.EDIT_DATE_TIME_ISO"
        const val EXTRA_EDIT_HOLES = "com.teeup.android.extra.EDIT_HOLES"
        const val EXTRA_EDIT_OPEN_SPOTS = "com.teeup.android.extra.EDIT_OPEN_SPOTS"
        const val EXTRA_EDIT_HANDICAP_MIN = "com.teeup.android.extra.EDIT_HANDICAP_MIN"
        const val EXTRA_EDIT_HANDICAP_MAX = "com.teeup.android.extra.EDIT_HANDICAP_MAX"
        const val EXTRA_EDIT_PACE = "com.teeup.android.extra.EDIT_PACE"
    }

    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var courseInput: AutoCompleteTextView
    private lateinit var pickDateTimeButton: Button
    private lateinit var holesSpinner: Spinner
    private lateinit var playersNeededText: TextView
    private lateinit var handicapMinInput: EditText
    private lateinit var handicapMaxInput: EditText
    private lateinit var paceSpinner: Spinner
    private lateinit var createButton: Button

    /** Set when a suggestion is tapped, or (edit mode only) resolved from the group's existing
     *  course id; cleared as soon as the typed text no longer matches it. Unused for the edit
     *  request itself (the course isn't editable) — only for display in the locked field. */
    private var selectedCourse: Course? = null
    private var pickedDateTimeIso: String? = null
    private var playersNeeded = 1
    private var editTeeTimeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        statusText = findViewById(R.id.text_status)
        contentGroup = findViewById(R.id.group_content)
        courseInput = findViewById(R.id.input_course)
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

        wireCourseSearch()
        pickDateTimeButton.setOnClickListener { showDateTimePicker() }
        findViewById<Button>(R.id.button_players_minus).setOnClickListener { changePlayersNeeded(-1) }
        findViewById<Button>(R.id.button_players_plus).setOnClickListener { changePlayersNeeded(1) }
        createButton.setOnClickListener { onCreateClicked() }

        updatePlayersNeededDisplay()
        // No upfront course fetch needed anymore (search is live), so the form is ready
        // immediately — the "loading courses" status view is no longer used at all here.
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE
        applyEditModeIfRequested()
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

    /** Pre-fills every field an edit carries. The course field is locked (disabled) and its
     *  name resolved asynchronously from [EXTRA_EDIT_COURSE_ID], since only the id is passed —
     *  editing doesn't need it, this is purely so the host can see which course the group is on. */
    private fun applyEditModeIfRequested() {
        val id = intent.getStringExtra(EXTRA_EDIT_TEE_TIME_ID) ?: return
        editTeeTimeId = id

        findViewById<TextView>(R.id.text_screen_title).text = getString(R.string.edit_group_title)
        createButton.text = getString(R.string.edit_group_save)

        courseInput.isEnabled = false
        intent.getStringExtra(EXTRA_EDIT_COURSE_ID)?.let { resolveLockedCourseName(it) }

        intent.getStringExtra(EXTRA_EDIT_DATE_TIME_ISO)?.let { iso ->
            try {
                val local = Calendar.getInstance().apply { timeInMillis = roundTimestamp(iso) }
                pickedDateTimeIso = iso
                pickDateTimeButton.text = formatLocalDateTime(local)
            } catch (e: IllegalArgumentException) {
                // Leave unset — the existing "pick a date/time first" validation on save covers it.
            }
        }

        holesSpinner.setSelection(if (intent.getIntExtra(EXTRA_EDIT_HOLES, 18) == 9) 0 else 1)

        playersNeeded = intent.getIntExtra(EXTRA_EDIT_OPEN_SPOTS, 1).coerceIn(1, 20)
        updatePlayersNeededDisplay()

        if (intent.hasExtra(EXTRA_EDIT_HANDICAP_MIN)) {
            handicapMinInput.setText(formatHandicapForInput(intent.getDoubleExtra(EXTRA_EDIT_HANDICAP_MIN, 0.0)))
        }
        if (intent.hasExtra(EXTRA_EDIT_HANDICAP_MAX)) {
            handicapMaxInput.setText(formatHandicapForInput(intent.getDoubleExtra(EXTRA_EDIT_HANDICAP_MAX, 0.0)))
        }
        if (intent.hasExtra(EXTRA_EDIT_PACE)) {
            paceSpinner.setSelection(intent.getIntExtra(EXTRA_EDIT_PACE, 0) + 1)
        }
    }

    private fun resolveLockedCourseName(courseId: String) {
        Thread {
            val course = try {
                TeeUpApiClient.fetchCourses().firstOrNull { it.id == courseId }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                selectedCourse = course
                // `false` skips triggering the (disabled, so irrelevant anyway) search dropdown.
                courseInput.setText(course?.name.orEmpty(), false)
            }
        }.start()
    }

    private fun formatHandicapForInput(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

    /**
     * Picker widgets return plain local wall-clock numbers, no time zone attached.
     * Build the picked time in the device's local zone, then convert to real UTC
     * for the API. Just appending "Z" would send the wrong hour.
     */
    private fun showDateTimePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                showTimePicker(now) { hourOfDay, minute ->
                    val local = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, hourOfDay, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    pickedDateTimeIso = toUtcIso(local)
                    pickDateTimeButton.text = formatLocalDateTime(local)
                }
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = now.timeInMillis }.show()
    }

    private fun toUtcIso(local: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(local.timeInMillis))

    /** Same style as formatTeeTime, but from local wall-clock fields.
     *  The button must show what the user picked, not a UTC-shifted value. */
    private fun formatLocalDateTime(local: Calendar): String {
        val day = local.get(Calendar.DAY_OF_MONTH)
        val month = local.get(Calendar.MONTH)
        val hour = local.get(Calendar.HOUR_OF_DAY)
        val minute = local.get(Calendar.MINUTE)
        val monthName = resources.getStringArray(R.array.month_abbreviations).getOrNull(month) ?: ""
        return "%d %s · %02d:%02d".format(day, monthName, hour, minute)
    }

    /**
     * Custom scrollable hour/minute picker instead of the platform TimePickerDialog.
     * The spinner style got deprecated around API 29, so most devices ignore it
     * and just show the clock dial. This avoids that.
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
        val editId = editTeeTimeId
        // Edit mode never sends the course, so no selection is required there.
        if (editId == null && selectedCourse == null) {
            TeeUpBanner.show(this, getString(R.string.create_group_course_required), isError = true)
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
        // Snapshotted now, not read from the field inside Thread{} below — the field is mutable
        // and the course input stays enabled/editable while the request is in flight.
        val courseId = selectedCourse?.id

        createButton.isEnabled = false
        createButton.text = getString(if (editId != null) R.string.edit_group_saving else R.string.create_group_creating)

        Thread {
            try {
                if (editId != null) {
                    TeeUpApiClient.editGroup(
                        teeTimeId = editId,
                        dateTimeIso = dateTimeIso,
                        holes = holes,
                        openSpots = playersNeeded,
                        wantedHandicapMin = handicapMin,
                        wantedHandicapMax = handicapMax,
                        wantedPace = pace
                    )
                } else {
                    // Guarded above: editId == null implies courseId != null.
                    TeeUpApiClient.createGroup(
                        courseId = courseId!!,
                        dateTimeIso = dateTimeIso,
                        holes = holes,
                        openSpots = playersNeeded,
                        wantedHandicapMin = handicapMin,
                        wantedHandicapMax = handicapMax,
                        wantedPace = pace
                    )
                }
                runOnUiThread {
                    TeeUpBanner.show(this, getString(if (editId != null) R.string.edit_group_saved else R.string.create_group_created))
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    createButton.isEnabled = true
                    createButton.text = getString(if (editId != null) R.string.edit_group_save else R.string.create_group_create)
                    val fallback = if (editId != null) R.string.edit_group_failed_fallback else R.string.create_group_failed_fallback
                    TeeUpBanner.show(this, e.message ?: getString(fallback), isError = true)
                }
            }
        }.start()
    }
}

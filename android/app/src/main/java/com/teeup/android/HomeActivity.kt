package com.teeup.android

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.formatHandicap
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.handicapValue
import com.teeup.android.data.paceLabel
import com.teeup.android.data.validHandicap
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner
import java.util.Calendar
import java.util.TimeZone

class HomeActivity : LocaleActivity() {

    private enum class DateFilter { ALL, TODAY, TOMORROW }

    private lateinit var statusText: TextView
    private lateinit var retryButton: View
    private lateinit var teeTimesContainer: LinearLayout

    // Loaded once from the API; filters below are applied client-side against these.
    private var allTeeTimes: List<TeeTime> = emptyList()
    private var coursesById: Map<String, Course> = emptyMap()

    private var searchQuery: String = ""
    private var dateFilter: DateFilter = DateFilter.ALL
    private var minOpenSpots: Int = 0

    /** EME-312: course/holes filters — both fields already sit on TeeTimeDto, so these are
     *  applied client-side against [allTeeTimes] same as date/players, no new API params needed. */
    private var courseFilterId: String? = null
    private var holesFilter: Int? = null

    /** EME-299: discover a group filtered by the host's handicap/pace of play. Null = no filter.
     *  Unlike the other filters, this isn't applied client-side — the tee time payload has no
     *  host handicap/pace on it, so it re-queries the API instead (see loadNearbyTeeTimes). */
    private var filterMaxHandicap: Double? = null
    private var filterPace: Int? = null
    private var hasLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        BottomNav.wire(this, BottomNavTab.HOME)

        statusText = findViewById(R.id.text_status)
        retryButton = findViewById(R.id.button_retry)
        teeTimesContainer = findViewById(R.id.tee_times_container)
        retryButton.setOnClickListener { loadNearbyTeeTimes() }

        findViewById<View>(R.id.button_notifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
        }

        findViewById<EditText>(R.id.input_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilters()
            }
        })

        findViewById<Button>(R.id.button_filter_datetime).setOnClickListener {
            showOptionsDialog(
                getString(R.string.home_filter_datetime),
                resources.getStringArray(R.array.home_date_filter_options).toList()
            ) { index ->
                dateFilter = DateFilter.entries[index]
                updateFilterLabels()
                applyFilters()
            }
        }

        findViewById<Button>(R.id.button_filter_players).setOnClickListener {
            showOptionsDialog(
                getString(R.string.home_filter_players),
                resources.getStringArray(R.array.home_players_filter_options).toList()
            ) { index ->
                minOpenSpots = index
                updateFilterLabels()
                applyFilters()
            }
        }

        findViewById<Button>(R.id.button_filter_skill).setOnClickListener { showSkillFilterDialog() }
        findViewById<Button>(R.id.button_filter_course).setOnClickListener { showCourseFilterDialog() }
        findViewById<Button>(R.id.button_filter_holes).setOnClickListener { showHolesFilterDialog() }

        findViewById<Button>(R.id.button_create_group).setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
        }

        loadNearbyTeeTimes()
    }

    override fun onResume() {
        super.onResume()
        // Picks up a group just created by CreateGroupActivity without a stale list —
        // same pattern as RoundsActivity's onResume. Skip the very first call since
        // onCreate's loadNearbyTeeTimes() already covers it.
        if (hasLoadedOnce) loadNearbyTeeTimes()
        hasLoadedOnce = true
    }

    /** EME-312: only real API data now — no more silent MockCatalog substitution on an error
     *  or an empty result. A failure shows a distinct, visible error state with a retry button
     *  instead of quietly swapping in fake groups the user has no way of knowing aren't real. */
    private fun loadNearbyTeeTimes() {
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        teeTimesContainer.removeAllViews()
        repeat(2) { teeTimesContainer.addView(buildShimmerCard()) }

        Thread {
            try {
                val courses = TeeUpApiClient.fetchCourses().associateBy { it.id }
                val teeTimes = TeeUpApiClient.fetchTeeTimes(filterMaxHandicap, filterPace, joinableOnly = true)
                SyncStatus.recordSuccess(this)

                runOnUiThread {
                    allTeeTimes = teeTimes
                    coursesById = courses
                    applyFilters()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    allTeeTimes = emptyList()
                    showError(e.message ?: getString(R.string.home_load_failed))
                }
            }
        }.start()
    }

    /** Re-filters [allTeeTimes] by search text / date / players / course / holes and re-renders. */
    private fun applyFilters() {
        if (allTeeTimes.isEmpty()) {
            showError(getString(R.string.home_empty_no_teetimes))
            return
        }

        val filtered = allTeeTimes.filter { teeTime ->
            val courseName = coursesById[teeTime.courseId]?.name.orEmpty()
            val matchesSearch = searchQuery.isBlank() || courseName.contains(searchQuery, ignoreCase = true)
            val matchesDate = when (dateFilter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> isoDateOnly(teeTime.dateTime) == isoDate(0)
                DateFilter.TOMORROW -> isoDateOnly(teeTime.dateTime) == isoDate(1)
            }
            val matchesPlayers = teeTime.spotsRemaining >= minOpenSpots
            val matchesCourse = courseFilterId == null || teeTime.courseId == courseFilterId
            val matchesHoles = holesFilter == null || teeTime.holes == holesFilter
            matchesSearch && matchesDate && matchesPlayers && matchesCourse && matchesHoles
        }

        if (filtered.isEmpty()) {
            showError(getString(R.string.home_empty_no_matches))
        } else {
            renderTeeTimes(filtered)
        }
    }

    private fun isoDateOnly(isoDateTime: String): String = isoDateTime.substring(0, 10)

    /** Today (daysFromToday=0) or a day offset from it, in UTC to match how tee time timestamps are displayed (see Formatting.kt). */
    private fun isoDate(daysFromToday: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, daysFromToday)
        }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun updateFilterLabels() {
        findViewById<Button>(R.id.button_filter_datetime).text = when (dateFilter) {
            DateFilter.ALL -> getString(R.string.home_filter_datetime)
            DateFilter.TODAY -> getString(R.string.home_filter_date_today)
            DateFilter.TOMORROW -> getString(R.string.home_filter_date_tomorrow)
        }
        findViewById<Button>(R.id.button_filter_players).text =
            if (minOpenSpots == 0) getString(R.string.home_filter_players) else getString(R.string.home_filter_players_min_format, minOpenSpots)
        findViewById<Button>(R.id.button_filter_skill).setText(
            if (filterMaxHandicap != null || filterPace != null) {
                R.string.home_filter_active
            } else {
                R.string.home_filter_skill
            }
        )
        findViewById<Button>(R.id.button_filter_course).text =
            courseFilterId?.let { coursesById[it]?.name } ?: getString(R.string.home_filter_course)
        findViewById<Button>(R.id.button_filter_holes).text =
            holesFilter?.let { getString(R.string.home_filter_holes_active_format, it) } ?: getString(R.string.home_filter_holes)
    }

    /** Unlike the other filter dialogs, this one re-queries the API (see loadNearbyTeeTimes)
     *  rather than re-filtering [allTeeTimes] client-side. */
    private fun showSkillFilterDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val handicapInput = EditText(this).apply {
            hint = getString(R.string.filter_max_handicap_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            filterMaxHandicap?.let { setText(it.toString()) }
        }
        container.addView(handicapInput)

        val paceLabels = listOf(getString(R.string.filter_pace_any)) +
            resources.getStringArray(R.array.pace_of_play_options)
        val paceSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            adapter = ArrayAdapter(this@HomeActivity, R.layout.spinner_item, paceLabels)
                .apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
            setSelection((filterPace ?: -1) + 1)
        }
        container.addView(paceSpinner)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.filter_skill_title)
            .setView(container)
            .setPositiveButton(R.string.filter_apply, null)
            .setNeutralButton(R.string.filter_clear) { _, _ ->
                filterMaxHandicap = null
                filterPace = null
                updateFilterLabels()
                loadNearbyTeeTimes()
            }
            .setNegativeButton(R.string.filter_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = handicapInput.text.toString()
            if (!validHandicap(text)) {
                handicapInput.error = getString(R.string.playing_details_handicap_invalid)
                return@setOnClickListener
            }
            filterMaxHandicap = handicapValue(text)
            filterPace = (paceSpinner.selectedItemPosition - 1).takeIf { it in 0..2 }
            updateFilterLabels()
            loadNearbyTeeTimes()
            dialog.dismiss()
        }
    }

    /** Course/holes are already on every loaded [TeeTime], so both filter client-side against
     *  [allTeeTimes]/[coursesById] like date/players — no new query params needed (EME-312). */
    private fun showCourseFilterDialog() {
        val sortedCourses = coursesById.values.sortedBy { it.name }
        val options = listOf(getString(R.string.home_filter_course_any)) + sortedCourses.map { it.name }
        showOptionsDialog(getString(R.string.home_filter_course), options) { index ->
            courseFilterId = if (index == 0) null else sortedCourses[index - 1].id
            updateFilterLabels()
            applyFilters()
        }
    }

    private fun showHolesFilterDialog() {
        val options = resources.getStringArray(R.array.home_holes_filter_options).toList()
        showOptionsDialog(getString(R.string.home_filter_holes), options) { index ->
            holesFilter = when (index) { 1 -> 9; 2 -> 18; else -> null }
            updateFilterLabels()
            applyFilters()
        }
    }

    /** Same pattern as ProfileActivity's language picker: a titled dialog of plain buttons, one tap picks and closes it. */
    private fun showOptionsDialog(title: String, options: List<String>, onSelected: (Int) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        lateinit var dialog: AlertDialog

        options.forEachIndexed { index, label ->
            container.addView(Button(this).apply {
                text = label
                setBackgroundResource(R.drawable.bg_button_outline)
                setTextColor(colorOf(R.color.teeup_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                setOnClickListener {
                    onSelected(index)
                    dialog.dismiss()
                }
            })
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.show()
    }

    private fun renderTeeTimes(teeTimes: List<TeeTime>) {
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        teeTimesContainer.removeAllViews()

        teeTimes.forEachIndexed { index, teeTime ->
            val course = coursesById[teeTime.courseId]

            val card = buildTeeTimeCard(teeTime, course)
            teeTimesContainer.addView(card)

            // Small staggered fade + rise so the list feels alive rather than
            // just popping in once the network call resolves.
            card.alpha = 0f
            card.translationY = dp(12).toFloat()
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 60L)
                .setDuration(260)
                .start()
        }
    }

    private fun showError(message: String) {
        teeTimesContainer.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
        retryButton.visibility = View.VISIBLE
    }

    /** EME-312: a group looking for players — course/date/holes/spots, the host, every current
     *  member with their handicap+pace, and the wanted range. Home only ever loads OpenRound
     *  groups now (joinableOnly=true, see loadNearbyTeeTimes), so there's no Booking-card path
     *  to branch on any more. */
    private fun buildTeeTimeCard(
        teeTime: TeeTime,
        course: Course?
    ): View {
        val courseName = course?.name ?: getString(R.string.home_unknown_course)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = space(R.dimen.space_md)
            }

            setBackgroundResource(R.drawable.bg_card_outline_interactive)
            elevation = resources.getDimension(R.dimen.elevation_card)

            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)

            isClickable = true
            isFocusable = true

            setOnClickListener {
                openTeeTimeDetail(teeTime.id)
            }
        }

        fun addLine(text: String, colorRes: Int, sizeRes: Int, bold: Boolean = false, bottomMarginRes: Int = R.dimen.space_xs) {
            card.addView(TextView(this).apply {
                this.text = text
                setTextColor(colorOf(colorRes))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = space(bottomMarginRes)
                }
            })
        }

        addLine(
            getString(R.string.home_card_heading, courseName.uppercase(), getString(R.string.home_card_open_round)),
            R.color.teeup_text_secondary, R.dimen.text_caption
        )
        addLine(formatTeeTime(teeTime.dateTime), R.color.teeup_text_primary, R.dimen.text_body_large, bold = true)
        addLine(
            getString(R.string.home_card_details_format, teeTime.holes ?: 18, teeTime.spotsRemaining, teeTime.openSpots),
            R.color.teeup_text_secondary, R.dimen.text_body
        )

        val hostName = teeTime.members.firstOrNull { it.isHost }?.displayName
        if (hostName != null) {
            addLine(getString(R.string.home_card_hosted_by_format, hostName), R.color.teeup_text_secondary, R.dimen.text_body)
        }

        if (teeTime.members.isNotEmpty()) {
            addLine(getString(R.string.home_card_members_heading), R.color.teeup_text_secondary, R.dimen.text_caption, bottomMarginRes = R.dimen.space_xs)
            teeTime.members.forEach { member ->
                val name = if (member.isHost) getString(R.string.home_card_host_member_format, member.displayName) else member.displayName
                addLine(
                    getString(R.string.home_card_member_row_format, name, formatHandicap(member.handicapIndex), paceLabel(this, member.paceOfPlay)),
                    R.color.teeup_text_secondary, R.dimen.text_caption
                )
            }
        }

        addLine(
            getString(R.string.home_card_wanted_format, wantedSummary(teeTime)),
            R.color.teeup_text_secondary, R.dimen.text_body, bottomMarginRes = R.dimen.space_lg
        )

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        // Preview inherits the theme's default (secondary/outline) button style;
        // View Group is the primary action for this card, so it gets the solid-fill style.
        buttonRow.addView(Button(this).apply {
            setText(R.string.home_card_preview)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginEnd = space(R.dimen.space_sm)
            }
            setOnClickListener { openCoursePreview(course) }
        })

        buttonRow.addView(Button(this).apply {
            setText(R.string.home_card_view_group)
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(colorOf(R.color.teeup_text_on_primary))
            elevation = resources.getDimension(R.dimen.elevation_button)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            setOnClickListener { openTeeTimeDetail(teeTime.id) }
        })

        card.addView(buttonRow)
        return card
    }

    /** "HCP up to 20 · Relaxed pace", "HCP 5–20 · Any pace", "Any handicap · Standard pace", etc. */
    private fun wantedSummary(teeTime: TeeTime): String {
        val handicapPart = when {
            teeTime.wantedHandicapMin != null && teeTime.wantedHandicapMax != null ->
                getString(R.string.home_card_wanted_handicap_range_format, formatHandicap(teeTime.wantedHandicapMin), formatHandicap(teeTime.wantedHandicapMax))
            teeTime.wantedHandicapMax != null ->
                getString(R.string.home_card_wanted_handicap_max_format, formatHandicap(teeTime.wantedHandicapMax))
            teeTime.wantedHandicapMin != null ->
                getString(R.string.home_card_wanted_handicap_min_format, formatHandicap(teeTime.wantedHandicapMin))
            else -> getString(R.string.home_card_wanted_handicap_any)
        }
        val pacePart = teeTime.wantedPace?.let { paceLabel(this, it) } ?: getString(R.string.filter_pace_any)
        return "$handicapPart · $pacePart"
    }

    /** A skeleton placeholder shown in place of a real card while the network call is in flight. */
    private fun buildShimmerCard(): View {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = space(R.dimen.space_md)
            }
            setBackgroundResource(R.drawable.bg_card)
            clipToOutline = true
        }

        fun block(widthDp: Int, heightDp: Int, marginBottomDimen: Int?) = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
                marginBottomDimen?.let { bottomMargin = space(it) }
            }
            setBackgroundResource(R.drawable.bg_shimmer_block)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)

            addView(block(110, 10, R.dimen.space_sm))
            addView(block(170, 18, R.dimen.space_sm))
            addView(block(130, 12, R.dimen.space_lg))
            addView(View(this@HomeActivity).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40))
                setBackgroundResource(R.drawable.bg_shimmer_block)
            })
        }
        card.addView(content)

        val sheen = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(90), MATCH_PARENT)
            setBackgroundResource(R.drawable.bg_shimmer_sheen)
            rotation = 14f
            translationX = -dp(160).toFloat()
        }
        card.addView(sheen)

        ObjectAnimator.ofFloat(sheen, "translationX", -dp(160).toFloat(), dp(420).toFloat()).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }.start()

        return card
    }

    private fun openTeeTimeDetail(teeTimeId: String) {
        val intent = Intent(this, TeeTimeDetailActivity::class.java)
        intent.putExtra(
            TeeTimeDetailActivity.EXTRA_TEE_TIME_ID,
            teeTimeId
        )
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun openCoursePreview(course: Course?) {
        if (course == null) {
            TeeUpBanner.show(this, getString(R.string.teetime_course_unavailable))
            return
        }
        val intent = Intent(this, CoursePreviewActivity::class.java).apply {
            putExtra(CoursePreviewActivity.EXTRA_COURSE_NAME, course.name)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LAT, course.latitude)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LNG, course.longitude)
            course.rating?.let { putExtra(CoursePreviewActivity.EXTRA_COURSE_RATING, it) }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun colorOf(colorRes: Int): Int =
        resources.getColor(colorRes, theme)

    private fun space(dimensionRes: Int): Int =
        resources.getDimensionPixelSize(dimensionRes)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
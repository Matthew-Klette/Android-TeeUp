package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.roundTimestamp
import com.teeup.android.data.selectRounds
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.CourseSearchAdapter
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/** Rounds tab: choose an upcoming round to start scoring, or review a past one. */
class RoundsActivity : LocaleActivity() {
    private var showHistory = false
    private var schedule: List<ScheduledRound> = emptyList()
    private var coursesById: Map<String, Course> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rounds)
        BottomNav.wire(this, BottomNavTab.ROUNDS)

        findViewById<Button>(R.id.rounds_upcoming).setOnClickListener { setTab(history = false) }
        findViewById<Button>(R.id.rounds_history).setOnClickListener { setTab(history = true) }
        findViewById<Button>(R.id.rounds_retry).setOnClickListener { loadRounds() }
        findViewById<Button>(R.id.button_start_a_round).setOnClickListener { onStartARoundClicked() }

        updateTabSelection()
        loadRounds()
    }

    override fun onResume() {
        super.onResume()
        // Picks up scores just saved by ScorecardActivity/RoundSummaryActivity without a stale list.
        if (schedule.isNotEmpty()) loadRounds()
    }

    private fun setTab(history: Boolean) {
        if (showHistory == history) return
        showHistory = history
        updateTabSelection()
        render()
    }

    private fun updateTabSelection() {
        findViewById<Button>(R.id.rounds_upcoming).isSelected = !showHistory
        findViewById<Button>(R.id.rounds_history).isSelected = showHistory
    }

    private fun loadRounds() {
        findViewById<View>(R.id.rounds_progress).visibility = View.VISIBLE
        findViewById<View>(R.id.rounds_retry).visibility = View.GONE
        findViewById<View>(R.id.text_empty_state).visibility = View.GONE
        findViewById<View>(R.id.button_start_a_round).visibility = View.GONE

        Thread {
            try {
                val fetchedSchedule = TeeUpApiClient.fetchSchedule()
                val fetchedCourses = try {
                    TeeUpApiClient.fetchCourses()
                } catch (e: Exception) {
                    MockCatalog.courses
                }
                runOnUiThread {
                    schedule = fetchedSchedule
                    coursesById = fetchedCourses.associateBy { it.id }
                    findViewById<View>(R.id.rounds_progress).visibility = View.GONE
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<View>(R.id.rounds_progress).visibility = View.GONE
                    findViewById<View>(R.id.rounds_retry).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.text_empty_state).apply {
                        text = getString(R.string.rounds_load_failed)
                        visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.rounds_container)
        container.removeAllViews()

        val rounds = try {
            selectRounds(schedule, showHistory, System.currentTimeMillis())
        } catch (e: IllegalArgumentException) {
            TeeUpBanner.show(this, e.message ?: getString(R.string.rounds_date_error_fallback), isError = true)
            emptyList()
        }

        // A solo round needs no upcoming tee time to start, so this generic entry point
        // is always available on History; on Upcoming it only shows once the tab has
        // nothing else offering a "Start Round"/"Continue Round" action of its own.
        findViewById<Button>(R.id.button_start_a_round).visibility =
            if (showHistory || rounds.isEmpty()) View.VISIBLE else View.GONE

        val emptyState = findViewById<TextView>(R.id.text_empty_state)
        if (rounds.isEmpty()) {
            emptyState.text = getString(if (showHistory) R.string.rounds_history_empty else R.string.rounds_upcoming_empty)
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE

        rounds.forEach { round -> container.addView(buildRoundRow(round)) }
    }

    private fun buildRoundRow(round: ScheduledRound): View {
        val course = coursesById[round.courseId] ?: MockCatalog.courseById(round.courseId)
        val holesScored = round.round?.scorecard?.size ?: 0
        // The tee time's own intended length, not a hardcoded 18 — a 9-hole round with all 9
        // holes scored must count as finished, not "9 of 18, keep going" (round.holes is null
        // only for a legacy/solo row from before the API persisted this; 18 matches the same
        // fallback ScorecardActivity itself uses when EXTRA_HOLE_COUNT is absent).
        val totalHoles = round.holes ?: 18

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            elevation = resources.getDimension(R.dimen.elevation_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }

        card.addView(TextView(this).apply {
            text = course?.name ?: getString(R.string.rounds_unknown_course)
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = formatTeeTime(round.dateTime)
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4); bottomMargin = dp(12) }
        })

        // The API rejects PostScorecard before the tee time's scheduled moment (RoundService),
        // so don't let a player walk into entering 18 holes just to be rejected at the end.
        val notYetStartable = round.round == null && roundTimestamp(round.dateTime) > System.currentTimeMillis()

        val notStarted = round.round == null

        val (label, onClick) = when {
            notYetStartable -> getString(R.string.rounds_not_started_yet) to null
            // A group's hole count (round.holes) is already fixed at creation — only prompt
            // when it's genuinely unset (a legacy/solo row, see totalHoles' own comment above).
            // Prompting always, regardless, let a 9-hole group's Start Round answer "18" and post
            // holes the API's own holeLimit check (RoundService.PostScorecardAsync) then rejects.
            notStarted -> getString(R.string.rounds_start_round) to
                (round.holes?.let { fixedHoles -> { openScorecard(round.teeTimeId, fixedHoles) } }
                    ?: { promptHoleCount { holes -> openScorecard(round.teeTimeId, holes) } })
            holesScored < totalHoles -> getString(R.string.rounds_continue_round) to
                { openScorecard(round.teeTimeId, holes = totalHoles) }
            else -> getString(R.string.rounds_view_summary) to { openSummary(round.teeTimeId) }
        }

        card.addView(Button(this).apply {
            text = label
            isEnabled = onClick != null
            setBackgroundResource(if (onClick != null) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            setTextColor(colorOf(if (onClick != null) R.color.teeup_text_on_primary else R.color.teeup_text_disabled))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            onClick?.let { action -> setOnClickListener { action() } }
        })

        return card
    }

    /** Search-as-you-type (CourseSearchAdapter, GET /api/courses?search=) rather than a fixed
     *  list of whatever [coursesById] happened to have cached — that list exists only to label
     *  already-known rounds/schedule rows, not to enumerate every course that could be picked
     *  here, so it's not reused for this dialog. Tapping a suggestion both fills the field and
     *  immediately advances to the hole-count prompt — there's nothing else to confirm on this
     *  screen, unlike a form field that still needs a Save tap. */
    private fun onStartARoundClicked() {
        lateinit var dialog: AlertDialog
        val searchInput = AutoCompleteTextView(this).apply {
            hint = getString(R.string.course_search_hint)
            threshold = 1
            val adapter = CourseSearchAdapter(this@RoundsActivity)
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                val course = adapter.getItem(position)
                dialog.dismiss()
                promptHoleCount { holes -> createSoloRoundAndOpen(course.id, holes) }
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(0))
            addView(searchInput)
        }
        dialog = AlertDialog.Builder(this, R.style.TeeUpDialogTheme)
            .setTitle(getString(R.string.rounds_pick_course))
            .setView(container)
            .setNegativeButton(getString(R.string.wireflow_back), null)
            .show()
    }

    private fun promptHoleCount(onChosen: (Int) -> Unit) {
        val options = arrayOf(getString(R.string.rounds_nine_holes), getString(R.string.rounds_eighteen_holes))
        AlertDialog.Builder(this, R.style.TeeUpDialogTheme)
            .setTitle(getString(R.string.rounds_choose_holes))
            .setItems(options) { _, index -> onChosen(if (index == 0) 9 else 18) }
            .setNegativeButton(getString(R.string.wireflow_back), null)
            .show()
    }

    private fun createSoloRoundAndOpen(courseId: String, holes: Int) {
        Thread {
            try {
                val teeTime = TeeUpApiClient.createSoloTeeTime(courseId, holes)
                runOnUiThread { openScorecard(teeTime.id, holes) }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.rounds_start_solo_failed), isError = true)
                }
            }
        }.start()
    }

    private fun openScorecard(teeTimeId: String, holes: Int?) {
        startActivity(Intent(this, ScorecardActivity::class.java).apply {
            putExtra(ScorecardActivity.EXTRA_TEE_TIME_ID, teeTimeId)
            holes?.let { putExtra(ScorecardActivity.EXTRA_HOLE_COUNT, it) }
        })
    }

    private fun openSummary(teeTimeId: String) {
        startActivity(Intent(this, RoundSummaryActivity::class.java).apply {
            putExtra(RoundSummaryActivity.EXTRA_TEE_TIME_ID, teeTimeId)
        })
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)
}

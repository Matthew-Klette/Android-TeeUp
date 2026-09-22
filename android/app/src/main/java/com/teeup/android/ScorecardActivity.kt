package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.HoleScoreInput
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 5 · Live Scorecard. With [EXTRA_TEE_TIME_ID] set, this is the real
 * hole-by-hole score entry screen for that round (choose the round on
 * RoundsActivity first, which also prompts for [EXTRA_HOLE_COUNT] — 9 or 18).
 * Without a tee time id — e.g. tapped from the bottom nav — it falls back to
 * the "choose a round" landing (activity_scorecard_landing), since there's
 * nothing to score yet.
 */
class ScorecardActivity : LocaleActivity() {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
        /** 9 or 18 (see RoundsActivity's hole-count prompt). Defaults to 18 when absent —
         *  e.g. resuming a round via "Continue Round", which doesn't re-prompt. */
        const val EXTRA_HOLE_COUNT = "com.teeup.android.extra.HOLE_COUNT"
    }

    /** True only for the landing screen (no [EXTRA_TEE_TIME_ID]) — controls whether
     *  [onResume] reloads the scorecard list. */
    private var isLandingMode = false
    /** onResume always fires right after onCreate too, so without this guard
     *  loadScorecardsLanding() ran twice on every open, showing every round twice. Same guard
     *  shape as HomeActivity's hasLoadedOnce. */
    private var hasLoadedScorecardsOnce = false
    private var teeTimeId: String? = null
    private var totalHoles = 18
    private var currentHole = 1
    /** First hole this session can enter/revisit — anything before it is already permanently
     *  saved on the server (a prior "Continue Round" session), so Previous Hole stops here. */
    private var startHole = 1
    private var alreadySavedHoles = 0
    /** Hole numbers already scored and saved on the server for this round. Holes aren't always
     *  a contiguous 1..N prefix — a middle hole can be deleted from RoundSummaryActivity and
     *  reopened — so resuming and Next/Previous navigation must skip over these rather than
     *  assume everything below the highest saved hole number is done. */
    private var persistedHoles: Set<Int> = emptySet()
    private val newEntries = mutableListOf<HoleScoreInput>()
    /** Set once a Round row actually exists server-side (at least one hole already posted);
     *  null means nothing's been saved yet, so there's nothing to delete. */
    private var currentRoundId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        if (id == null) {
            isLandingMode = true
            setContentView(R.layout.activity_scorecard_landing)
            BottomNav.wire(this, BottomNavTab.SCORECARD)
            findViewById<Button>(R.id.button_choose_round).setOnClickListener {
                startActivity(Intent(this, RoundsActivity::class.java))
            }
            loadScorecardsLanding()
            return
        }

        teeTimeId = id
        totalHoles = if (intent.getIntExtra(EXTRA_HOLE_COUNT, 18) == 9) 9 else 18
        setContentView(R.layout.activity_scorecard)
        BottomNav.wire(this, BottomNavTab.SCORECARD)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        loadRound(id)
    }

    override fun onResume() {
        super.onResume()
        // Picks up a round just finished/continued elsewhere (RoundsActivity, RoundSummaryActivity)
        // without a stale list, same pattern as RoundsActivity's own onResume. Skips the first
        // call since onCreate's own loadScorecardsLanding() already covers it.
        if (isLandingMode && hasLoadedScorecardsOnce) loadScorecardsLanding()
        hasLoadedScorecardsOnce = true
    }

    /** Landing screen (no tee time id): lists every round that already has scoring started —
     *  in progress or complete — instead of leaving the tab blank until you go pick one from
     *  My Rounds. Most-recently-dated round first. */
    private fun loadScorecardsLanding() {
        val progress = findViewById<View>(R.id.scorecard_landing_progress)
        val heading = findViewById<View>(R.id.text_scorecard_landing_heading)
        val container = findViewById<LinearLayout>(R.id.scorecard_landing_container)
        val emptyText = findViewById<TextView>(R.id.text_scorecard_landing_empty)

        progress.visibility = View.VISIBLE
        heading.visibility = View.GONE
        container.visibility = View.GONE
        container.removeAllViews()
        emptyText.visibility = View.GONE

        Thread {
            try {
                val schedule = TeeUpApiClient.fetchSchedule()
                val courses = try {
                    TeeUpApiClient.fetchCourses().associateBy { it.id }
                } catch (e: Exception) {
                    emptyMap()
                }

                val started = schedule
                    .filter { it.round != null }
                    .sortedByDescending { it.dateTime }

                runOnUiThread {
                    progress.visibility = View.GONE
                    if (started.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                    } else {
                        heading.visibility = View.VISIBLE
                        container.visibility = View.VISIBLE
                        started.forEach { round -> container.addView(buildScorecardRow(round, courses[round.courseId])) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = e.message ?: getString(R.string.scorecard_landing_load_failed)
                }
            }
        }.start()
    }

    private fun buildScorecardRow(round: ScheduledRound, course: Course?): View {
        val holesScored = round.round?.scorecard?.size ?: 0
        val totalHoles = round.holes ?: 18
        val isComplete = holesScored >= totalHoles

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_interactive)
            elevation = resources.getDimension(R.dimen.elevation_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
            isClickable = true
            isFocusable = true
        }

        card.addView(TextView(this).apply {
            text = course?.name ?: getString(R.string.rounds_unknown_course)
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = getString(R.string.scorecard_progress_format, holesScored, totalHoles)
            setTextColor(colorOf(R.color.teeup_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) }
        })

        card.setOnClickListener {
            if (isComplete) openSummary(round.teeTimeId) else openScorecard(round.teeTimeId, totalHoles)
        }

        return card
    }

    private fun openScorecard(teeTimeId: String, holes: Int) {
        startActivity(Intent(this, ScorecardActivity::class.java).apply {
            putExtra(EXTRA_TEE_TIME_ID, teeTimeId)
            putExtra(EXTRA_HOLE_COUNT, holes)
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

    private fun loadRound(id: String) {
        setLoading(true)
        Thread {
            try {
                val schedule = TeeUpApiClient.fetchSchedule()
                val match = schedule.firstOrNull { it.teeTimeId == id }
                if (match == null) {
                    runOnUiThread {
                        TeeUpBanner.show(this, getString(R.string.scorecard_missing_round), isError = true)
                        finish()
                    }
                    return@Thread
                }

                val course = try {
                    TeeUpApiClient.fetchCourses().firstOrNull { it.id == match.courseId }
                } catch (e: Exception) {
                    null
                } ?: MockCatalog.courseById(match.courseId)

                val existingHoles = match.round?.scorecard?.map { it.holeNumber }?.toSet() ?: emptySet()
                val startHole = (1..totalHoles).firstOrNull { it !in existingHoles } ?: (totalHoles + 1)

                runOnUiThread { onRoundLoaded(match, course, startHole, existingHoles) }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.scorecard_load_failed), isError = true)
                    finish()
                }
            }
        }.start()
    }

    private fun onRoundLoaded(round: ScheduledRound, course: Course?, startHole: Int, existingHoles: Set<Int>) {
        setLoading(false)

        findViewById<TextView>(R.id.round_reference).text = getString(
            R.string.round_reference,
            "${course?.name ?: getString(R.string.rounds_unknown_course)} · ${formatTeeTime(round.dateTime)}"
        )

        if (startHole > totalHoles) {
            TeeUpBanner.show(this, getString(R.string.scorecard_already_complete))
            openSummary()
            return
        }

        this.startHole = startHole
        this.persistedHoles = existingHoles
        alreadySavedHoles = existingHoles.size
        currentHole = startHole
        currentRoundId = round.round?.id
        updateHoleUi()

        findViewById<Button>(R.id.button_previous_hole).setOnClickListener { onPreviousHoleClicked() }
        findViewById<Button>(R.id.button_next_hole).setOnClickListener { onNextHoleClicked() }
        findViewById<Button>(R.id.button_finish_early).setOnClickListener { onFinishEarlyClicked() }
        findViewById<Button>(R.id.button_delete_round).apply {
            visibility = if (currentRoundId != null) View.VISIBLE else View.GONE
            setOnClickListener { confirmDeleteRound() }
        }
    }

    private fun confirmDeleteRound() {
        val roundId = currentRoundId ?: return
        AlertDialog.Builder(this, R.style.TeeUpDialogTheme)
            .setTitle(R.string.summary_delete_round_confirm_title)
            .setMessage(R.string.summary_delete_round_confirm_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> deleteRound(roundId) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteRound(roundId: String) {
        Thread {
            try {
                TeeUpApiClient.deleteRound(roundId)
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.summary_round_deleted))
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.summary_delete_round_failed_fallback), isError = true)
                }
            }
        }.start()
    }

    private fun onPreviousHoleClicked() {
        if (currentHole <= startHole) return
        // Whatever's currently typed for the hole being left is discarded, not saved — Previous
        // is for backing out of a hole to go fix an earlier mistake, not for confirming this one.
        currentHole = previousEntryHole(currentHole)
        updateHoleUi()
    }

    private fun onNextHoleClicked() {
        val entry = readValidatedEntry() ?: run {
            TeeUpBanner.show(this, getString(R.string.scorecard_validation_error), isError = true)
            return
        }
        replaceEntry(entry)

        val next = nextEntryHole(currentHole)
        if (next > totalHoles) {
            finishRound()
        } else {
            currentHole = next
            updateHoleUi()
        }
    }

    /** Nearest higher hole number, up to [totalHoles], that still needs an entry — skips holes
     *  already persisted on the server so Next never re-submits one and hits the API's
     *  duplicate-hole rejection. Returns totalHoles + 1 once nothing is left to enter. */
    private fun nextEntryHole(from: Int): Int {
        var hole = from + 1
        while (hole <= totalHoles && hole in persistedHoles) hole++
        return hole
    }

    /** Nearest lower hole number, no lower than [startHole], that still needs an entry — mirrors
     *  [nextEntryHole] so Previous never lands back on an already-persisted hole either. */
    private fun previousEntryHole(from: Int): Int {
        var hole = from - 1
        while (hole > startHole && hole in persistedHoles) hole--
        return hole
    }

    private fun onFinishEarlyClicked() {
        val strokesText = findViewById<EditText>(R.id.input_strokes).text.toString().trim()
        val puttsText = findViewById<EditText>(R.id.input_putts).text.toString().trim()
        if (strokesText.isNotEmpty() || puttsText.isNotEmpty()) {
            val entry = readValidatedEntry() ?: run {
                TeeUpBanner.show(this, getString(R.string.scorecard_validation_error), isError = true)
                return
            }
            replaceEntry(entry)
        }

        if (newEntries.isEmpty()) {
            TeeUpBanner.show(this, getString(R.string.scorecard_finish_empty_error), isError = true)
            return
        }
        finishRound()
    }

    /** Replaces this session's existing entry for the hole, if any, instead of appending a
     *  second one — needed once Previous Hole makes re-confirming an already-entered hole
     *  possible (the API also rejects a duplicate hole number within one PostScorecard call). */
    private fun replaceEntry(entry: HoleScoreInput) {
        newEntries.removeAll { it.holeNumber == entry.holeNumber }
        newEntries.add(entry)
    }

    private fun readValidatedEntry(): HoleScoreInput? {
        val strokes = findViewById<EditText>(R.id.input_strokes).text.toString().trim().toIntOrNull()
        val putts = findViewById<EditText>(R.id.input_putts).text.toString().trim().toIntOrNull()
        if (strokes == null || strokes < 1 || putts == null || putts < 0 || putts > strokes) return null
        return HoleScoreInput(currentHole, strokes, putts)
    }

    private fun updateHoleUi() {
        findViewById<TextView>(R.id.text_hole_heading).text = getString(R.string.scorecard_hole_heading, currentHole)

        // Revisiting a hole already entered this session (via Previous Hole) pre-fills what
        // was there instead of showing blank fields — otherwise saving it again would erase it.
        val existing = newEntries.firstOrNull { it.holeNumber == currentHole }
        findViewById<EditText>(R.id.input_strokes).setText(existing?.strokes?.toString().orEmpty())
        findViewById<EditText>(R.id.input_putts).setText(existing?.putts?.toString().orEmpty())

        findViewById<TextView>(R.id.text_progress).text = getString(
            R.string.scorecard_progress_format, alreadySavedHoles + newEntries.size, totalHoles
        )
        findViewById<Button>(R.id.button_previous_hole).visibility =
            if (currentHole > startHole) View.VISIBLE else View.GONE
        // The last hole still needing an entry, not just hole number == totalHoles — a gap left
        // by deleting a middle hole can put the final entry before the numeric last hole.
        val isLastEntryHole = nextEntryHole(currentHole) > totalHoles
        findViewById<Button>(R.id.button_next_hole).text =
            if (isLastEntryHole) getString(R.string.scorecard_finish_round) else getString(R.string.scorecard_next_hole)
        findViewById<Button>(R.id.button_finish_early).visibility =
            if (isLastEntryHole) View.GONE else View.VISIBLE
    }

    private fun finishRound() {
        val id = teeTimeId ?: return
        setLoading(true)
        findViewById<View>(R.id.group_entry_content).visibility = View.GONE

        Thread {
            try {
                TeeUpApiClient.postScorecard(id, newEntries)
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.scorecard_finish_success))
                    openSummary()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    TeeUpBanner.show(this, e.message ?: getString(R.string.scorecard_save_failed), isError = true)
                }
            }
        }.start()
    }

    private fun openSummary() {
        val id = teeTimeId ?: return
        startActivity(Intent(this, RoundSummaryActivity::class.java).apply {
            putExtra(RoundSummaryActivity.EXTRA_TEE_TIME_ID, id)
        })
        finish()
    }

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.scorecard_progress).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.group_entry_content).visibility = if (loading) View.GONE else View.VISIBLE
    }
}

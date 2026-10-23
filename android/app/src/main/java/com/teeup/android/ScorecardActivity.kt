package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.ActiveRound
import com.teeup.android.data.Course
import com.teeup.android.data.GeoPoint
import com.teeup.android.data.HoleScoreInput
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.RoundProgress
import com.teeup.android.data.RoundProgressStore
import com.teeup.android.data.RoundScoring
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.HoleMapController
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 5 · Live Scorecard. With [EXTRA_TEE_TIME_ID] set, this is the in-round screen for
 * that round (choose the round on RoundsActivity first, which also prompts for
 * [EXTRA_HOLE_COUNT] — 9 or 18): the live hole map stays open for the whole round, with the
 * hole's distances, Add score, and ‹ › arrows to change hole. The map follows the golfer by GPS
 * and the scorecard follows the map.
 *
 * Where the golfer is in the round (hole, layup target, unsent scores) is kept by
 * [RoundProgressStore], so leaving for another tab and coming back — or the Scorecard tab, which
 * reopens an unfinished round — lands on the same hole. Without a tee time id or an unfinished
 * round it falls back to the "choose a round" landing (activity_scorecard_landing).
 */
class ScorecardActivity : LocaleActivity(), HoleMapController.Listener {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
        /** 9 or 18 (see RoundsActivity's hole-count prompt). Defaults to the unfinished round's
         *  count, then 18 — e.g. resuming a round via "Continue Round", which doesn't re-prompt. */
        const val EXTRA_HOLE_COUNT = "com.teeup.android.extra.HOLE_COUNT"
        /** Shows the scorecards list even while a round is unfinished — the fallback when that
         *  round can't be loaded, so the Scorecard tab doesn't bounce straight back out. */
        private const val EXTRA_SHOW_LIST = "com.teeup.android.extra.SHOW_LIST"
        /** Width of the distances/score panel beside the map in landscape. */
        private const val LANDSCAPE_PANEL_WIDTH_DP = 360
    }

    /** True only for the landing screen (no round to open) — controls whether
     *  [onResume] reloads the scorecard list. */
    private var isLandingMode = false
    /** onResume always fires right after onCreate too, so without this guard
     *  loadScorecardsLanding() ran twice on every open, showing every round twice. Same guard
     *  shape as HomeActivity's hasLoadedOnce. */
    private var hasLoadedScorecardsOnce = false
    private lateinit var progressStore: RoundProgressStore
    private var map: HoleMapController? = null
    private var teeTimeId: String? = null
    private var totalHoles = 18
    private var currentHole = 1
    private var scoring = RoundScoring(totalHoles, emptyMap(), emptyMap())
    /** Where the golfer dragged the layup target on [currentHole]; null means the default spot. */
    private var currentTarget: GeoPoint? = null
    /** True from the round loading until it's finished or deleted; progress is only saved meanwhile. */
    private var isRoundOpen = false
    /** Set once a Round row actually exists server-side (at least one hole already posted);
     *  null means nothing's been saved yet, so there's nothing to delete. */
    private var currentRoundId: String? = null
    /** Opened by the Scorecard tab reopening the unfinished round, rather than for a chosen round. */
    private var isResumingActiveRound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressStore = RoundProgressStore(this)

        val active = if (intent.getBooleanExtra(EXTRA_SHOW_LIST, false)) null else progressStore.activeRound()
        val chosenId = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        isResumingActiveRound = chosenId == null && active != null
        val id = chosenId ?: active?.teeTimeId
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
        val holeCount = when {
            intent.hasExtra(EXTRA_HOLE_COUNT) -> intent.getIntExtra(EXTRA_HOLE_COUNT, 18)
            active?.teeTimeId == id -> active.holes
            else -> 18
        }
        totalHoles = normalizedHoles(holeCount)
        setContentView(R.layout.activity_scorecard)
        BottomNav.wire(this, BottomNavTab.SCORECARD)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        map = HoleMapController(this, this)
        arrangeForOrientation(resources.configuration.orientation)

        loadRound(id)
    }

    /** The bottom nav reuses an existing instance (CLEAR_TOP | SINGLE_TOP). A scorecards list
     *  left in the back stack before a round started must turn into that round when reused. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isLandingMode && !intent.getBooleanExtra(EXTRA_SHOW_LIST, false) && progressStore.activeRound() != null) {
            setIntent(intent)
            recreate()
        }
    }

    /** Rotation is handled here (see the manifest) so the map page isn't reloaded mid-round. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!isLandingMode) arrangeForOrientation(newConfig.orientation)
    }

    /** Landscape has no height for the map above the distances, so they sit side by side. */
    private fun arrangeForOrientation(orientation: Int) {
        val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val gutter = resources.getDimensionPixelSize(R.dimen.space_lg)
        findViewById<LinearLayout>(R.id.group_entry_content).orientation =
            if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        findViewById<View>(R.id.frame_hole_map).layoutParams =
            (if (landscape) LinearLayout.LayoutParams(0, MATCH_PARENT, 1f) else LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
                .apply {
                    marginStart = gutter
                    marginEnd = if (landscape) 0 else gutter
                    bottomMargin = if (landscape) gutter else 0
                }
        findViewById<View>(R.id.panel_round_details).layoutParams =
            if (landscape) LinearLayout.LayoutParams(dp(LANDSCAPE_PANEL_WIDTH_DP), MATCH_PARENT)
            else LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
        // Picks up a round just finished/continued elsewhere (RoundsActivity, RoundSummaryActivity)
        // without a stale list, same pattern as RoundsActivity's own onResume. Skips the first
        // call since onCreate's own loadScorecardsLanding() already covers it.
        if (isLandingMode && hasLoadedScorecardsOnce) loadScorecardsLanding()
        hasLoadedScorecardsOnce = true
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
    }

    override fun onDestroy() {
        map?.onDestroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        map?.onRequestPermissionsResult(requestCode)
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
                        progressStore.clear(id)
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

                runOnUiThread { if (!isFinishing && !isDestroyed) onRoundLoaded(id, match, course) }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.scorecard_load_failed), isError = true)
                    // Finishing back to the Scorecard tab would only reopen this same round and fail again.
                    if (isResumingActiveRound) {
                        startActivity(Intent(this, ScorecardActivity::class.java).putExtra(EXTRA_SHOW_LIST, true))
                    }
                    finish()
                }
            }
        }.start()
    }

    private fun onRoundLoaded(id: String, round: ScheduledRound, course: Course?) {
        // The tee time's own length wins over the intent's, which falls back to 18 when unknown.
        round.holes?.let { totalHoles = normalizedHoles(it) }
        val saved = round.round?.scorecard.orEmpty()
            .associate { it.holeNumber to HoleScoreInput(it.holeNumber, it.strokes, it.putts) }
        if ((1..totalHoles).all { it in saved }) {
            progressStore.clear(id)
            TeeUpBanner.show(this, getString(R.string.scorecard_already_complete))
            openSummary()
            return
        }

        val stored = progressStore.load(id)
        scoring = RoundScoring(totalHoles, saved, stored?.pending.orEmpty().associateBy { it.holeNumber })
            .withoutStalePending()
        currentHole = stored?.hole?.takeIf { it in scoring.holes } ?: scoring.firstUnscored() ?: 1
        currentTarget = stored?.target?.takeIf { stored.hole == currentHole }
        currentRoundId = round.round?.id
        isRoundOpen = true
        progressStore.setActive(ActiveRound(id, totalHoles))

        findViewById<TextView>(R.id.text_course_name).text = getString(
            R.string.scorecard_round_header_format,
            course?.name ?: getString(R.string.rounds_unknown_course),
            formatTeeTime(round.dateTime)
        )
        findViewById<Button>(R.id.button_previous_hole).setOnClickListener { moveToHole(currentHole - 1) }
        findViewById<Button>(R.id.button_next_hole).setOnClickListener { moveToHole(currentHole + 1) }
        findViewById<Button>(R.id.button_follow_gps).setOnClickListener { map?.resumeFollowingGps() }
        findViewById<Button>(R.id.button_add_score).setOnClickListener { showScoreDialog() }
        findViewById<Button>(R.id.button_finish_early).setOnClickListener { onFinishClicked() }
        findViewById<Button>(R.id.button_delete_round).setOnClickListener { confirmDeleteRound() }

        setLoading(false)
        if (course != null) {
            map?.start(course.id, course.name, currentHole, currentTarget, scoring.holes)
        } else {
            map?.showUnavailable()
        }
        updateHoleUi()
        persistProgress()
    }

    /** A hole picked with the ‹ › arrows, or the next one to score after saving. Unless
     *  [keepFollowing], this stops the map following GPS so it stays on the chosen hole. */
    private fun moveToHole(hole: Int, keepFollowing: Boolean = false) {
        if (hole !in scoring.holes || hole == currentHole) return
        currentHole = hole
        currentTarget = null
        map?.showHole(hole, keepFollowing)
        updateHoleUi()
        persistProgress()
    }

    override fun onHoleShown(hole: Int) {
        updateFollowButton()
        // Only GPS moves the map to a hole the scorecard isn't already on.
        if (!isRoundOpen || hole == currentHole || hole !in scoring.holes) return
        currentHole = hole
        currentTarget = null
        updateHoleUi()
        persistProgress()
    }

    override fun onFollowGpsChanged(following: Boolean) = updateFollowButton()

    override fun onTargetMoved(hole: Int, target: GeoPoint) {
        if (hole != currentHole) return
        currentTarget = target
        persistProgress()
    }

    private fun updateFollowButton() {
        val map = map ?: return
        findViewById<Button>(R.id.button_follow_gps).visibility =
            if (!map.isFollowingGps && map.mappedHoles.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showScoreDialog() {
        val hole = currentHole
        if (scoring.isSaved(hole)) return
        val body = layoutInflater.inflate(R.layout.dialog_hole_score, null)
        val strokesInput = body.findViewById<EditText>(R.id.input_strokes)
        val puttsInput = body.findViewById<EditText>(R.id.input_putts)
        // Editing a hole already entered this round starts from what was there.
        scoring.pending[hole]?.let {
            strokesInput.setText(it.strokes.toString())
            puttsInput.setText(it.putts.toString())
        }

        val saveLabel = if (scoring.isLastToScore(hole)) R.string.scorecard_save_and_finish else R.string.scorecard_save_score
        val dialog = AlertDialog.Builder(this, R.style.TeeUpDialogTheme)
            .setTitle(getString(R.string.scorecard_score_dialog_title, hole))
            .setView(body)
            .setPositiveButton(saveLabel, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        // Set after show() so a bad entry keeps the dialog open instead of dismissing it.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entry = RoundScoring.entryOrNull(
                    hole,
                    strokesInput.text.toString().trim().toIntOrNull(),
                    puttsInput.text.toString().trim().toIntOrNull()
                )
                if (entry == null) {
                    strokesInput.error = getString(R.string.scorecard_validation_error)
                    strokesInput.requestFocus()
                    return@setOnClickListener
                }
                dialog.dismiss()
                onScoreEntered(entry)
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        strokesInput.requestFocus()
    }

    private fun onScoreEntered(entry: HoleScoreInput) {
        scoring = scoring.withEntry(entry)
        persistProgress()
        if (scoring.isComplete) {
            finishRound()
            return
        }
        // On the course with GPS following, the map moves on by itself as the golfer walks to the
        // next tee. Otherwise step straight to the next hole still needing a score, so off-GPS
        // scoring stays one tap per hole like the old Save & Next Hole.
        val next = scoring.holeAfter(entry.holeNumber)
        if (map?.isFollowingOnCourse != true && next != null) moveToHole(next, keepFollowing = true)
        updateHoleUi()
    }

    private fun onFinishClicked() {
        if (scoring.pending.isEmpty()) {
            TeeUpBanner.show(this, getString(R.string.scorecard_finish_empty_error), isError = true)
            return
        }
        finishRound()
    }

    private fun updateHoleUi() {
        findViewById<TextView>(R.id.text_hole_title).text = getString(R.string.hole_map_hole_format, currentHole)
        findViewById<Button>(R.id.button_previous_hole).isEnabled = currentHole > scoring.holes.first
        findViewById<Button>(R.id.button_next_hole).isEnabled = currentHole < scoring.holes.last

        val score = scoring.scoreFor(currentHole)
        val isSaved = scoring.isSaved(currentHole)
        findViewById<Button>(R.id.button_add_score).apply {
            // Holes already on the server can't be re-sent (the API rejects a repeated hole), so
            // they're shown read-only; fix one by deleting it on the round summary.
            isEnabled = !isSaved
            text = when {
                score == null -> getString(R.string.scorecard_add_score)
                isSaved -> getString(R.string.scorecard_saved_score_format, score.strokes, score.putts)
                else -> getString(R.string.scorecard_edit_score_format, score.strokes, score.putts)
            }
        }

        findViewById<TextView>(R.id.text_progress).text =
            getString(R.string.scorecard_progress_format, scoring.scoredCount, totalHoles)
        findViewById<Button>(R.id.button_finish_early).setText(
            if (scoring.isComplete) R.string.scorecard_finish_round else R.string.scorecard_finish_early
        )
        findViewById<Button>(R.id.button_delete_round).visibility =
            if (currentRoundId != null) View.VISIBLE else View.GONE
        updateFollowButton()
    }

    private fun persistProgress() {
        val id = teeTimeId ?: return
        if (!isRoundOpen) return
        progressStore.save(id, RoundProgress(currentHole, currentTarget, scoring.pending.values.toList()))
    }

    /** Ends the round for good: nothing more is saved, and the Scorecard tab stops reopening it. */
    private fun closeRound() {
        isRoundOpen = false
        teeTimeId?.let(progressStore::clear)
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
                    closeRound()
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

    private fun finishRound() {
        val id = teeTimeId ?: return
        val entries = scoring.pending.values.sortedBy { it.holeNumber }
        setLoading(true)

        Thread {
            try {
                TeeUpApiClient.postScorecard(id, entries)
                runOnUiThread {
                    closeRound()
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

    private fun normalizedHoles(count: Int) = if (count == 9) 9 else 18

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.scorecard_progress).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.group_entry_content).visibility = if (loading) View.GONE else View.VISIBLE
    }
}

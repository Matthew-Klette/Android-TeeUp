package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
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
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.nav.BottomNav
import com.teeup.android.scorecard.HoleMapHost
import com.teeup.android.scorecard.RoundActions
import com.teeup.android.scorecard.RoundScreen
import com.teeup.android.scorecard.RoundUiState
import com.teeup.android.scorecard.ScoreLabel
import com.teeup.android.scorecard.ScoreSheet
import com.teeup.android.scorecard.ScorecardLandingActions
import com.teeup.android.scorecard.ScorecardLandingScreen
import com.teeup.android.scorecard.ScorecardLandingViewModel
import com.teeup.android.ui.HoleMapController
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * round it falls back to the "choose a round" landing ([ScorecardLandingScreen]).
 *
 * The round runs here rather than in a ViewModel because it's bound to the map controller, which
 * lives with the Activity's views (rotation is handled in place, see the manifest); the Compose
 * [RoundScreen] only reads [ui].
 */
class ScorecardActivity : LocaleComponentActivity(), HoleMapController.Listener {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
        /** 9 or 18 (see RoundsActivity's hole-count prompt). Defaults to the unfinished round's
         *  count, then 18 — e.g. resuming a round via "Continue Round", which doesn't re-prompt. */
        const val EXTRA_HOLE_COUNT = "com.teeup.android.extra.HOLE_COUNT"
        /** Shows the scorecards list even while a round is unfinished — the fallback when that
         *  round can't be loaded, so the Scorecard tab doesn't bounce straight back out. */
        private const val EXTRA_SHOW_LIST = "com.teeup.android.extra.SHOW_LIST"
    }

    /** True only for the landing screen (no round to open) — controls whether
     *  [onResume] reloads the scorecard list. */
    private var isLandingMode = false
    /** onResume always fires right after onCreate too; the ViewModel's own first load covers
     *  that one, so only later resumes reload. */
    private var hasResumedOnce = false
    private val landingViewModel: ScorecardLandingViewModel by viewModels()
    private lateinit var progressStore: RoundProgressStore
    private var mapHost: HoleMapHost? = null
    private val ui = RoundUiState()
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        progressStore = RoundProgressStore(this)

        val active = if (intent.getBooleanExtra(EXTRA_SHOW_LIST, false)) null else progressStore.activeRound()
        val chosenId = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        isResumingActiveRound = chosenId == null && active != null
        val id = chosenId ?: active?.teeTimeId
        if (id == null) {
            isLandingMode = true
            val actions = ScorecardLandingActions(
                openScorecard = ::openScorecard,
                openSummary = ::openSummaryFor,
                chooseRound = { startActivity(Intent(this, RoundsActivity::class.java)) },
                openTab = { tab -> BottomNav.open(this, tab) }
            )
            setContent { TeeUpTheme { ScorecardLandingScreen(landingViewModel, actions) } }
            return
        }

        teeTimeId = id
        val holeCount = when {
            intent.hasExtra(EXTRA_HOLE_COUNT) -> intent.getIntExtra(EXTRA_HOLE_COUNT, 18)
            active?.teeTimeId == id -> active.holes
            else -> 18
        }
        totalHoles = normalizedHoles(holeCount)
        val host = HoleMapHost(this, this)
        mapHost = host
        val actions = RoundActions(
            back = {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            previous = { moveToHole(currentHole - 1) },
            next = { moveToHole(currentHole + 1) },
            followGps = { host.withController { it.resumeFollowingGps() } },
            resetMarkers = { host.withController { it.resetMarkers() } },
            addScore = ::showScoreSheet,
            saveScore = ::onScoreEntered,
            finish = ::onFinishClicked,
            deleteRound = ::deleteRound,
            openTab = { tab -> BottomNav.open(this, tab) }
        )
        setContent { TeeUpTheme { RoundScreen(ui, host, actions) } }

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

    override fun onResume() {
        super.onResume()
        mapHost?.onResume()
        // Picks up a round just finished/continued elsewhere (RoundsActivity, RoundSummaryActivity)
        // without a stale list, same pattern as RoundsActivity's own onResume.
        if (isLandingMode && hasResumedOnce) landingViewModel.load()
        hasResumedOnce = true
    }

    override fun onPause() {
        super.onPause()
        mapHost?.onPause()
    }

    override fun onDestroy() {
        mapHost?.onDestroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mapHost?.onRequestPermissionsResult(requestCode)
    }

    private fun openScorecard(teeTimeId: String, holes: Int) {
        startActivity(Intent(this, ScorecardActivity::class.java).apply {
            putExtra(EXTRA_TEE_TIME_ID, teeTimeId)
            putExtra(EXTRA_HOLE_COUNT, holes)
        })
    }

    private fun openSummaryFor(teeTimeId: String) {
        startActivity(Intent(this, RoundSummaryActivity::class.java).apply {
            putExtra(RoundSummaryActivity.EXTRA_TEE_TIME_ID, teeTimeId)
        })
    }

    private fun loadRound(id: String) {
        ui.loading = true
        lifecycleScope.launch {
            try {
                val (match, course) = withContext(Dispatchers.IO) {
                    val match = TeeUpApiClient.fetchSchedule().firstOrNull { it.teeTimeId == id }
                    val course = match?.let { round ->
                        try {
                            TeeUpApiClient.fetchCourses().firstOrNull { it.id == round.courseId }
                        } catch (e: Exception) {
                            null
                        } ?: MockCatalog.courseById(round.courseId)
                    }
                    match to course
                }
                if (match == null) {
                    progressStore.clear(id)
                    TeeUpBanner.show(this@ScorecardActivity, getString(R.string.scorecard_missing_round), isError = true)
                    finish()
                    return@launch
                }
                if (!isFinishing && !isDestroyed) onRoundLoaded(id, match, course)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                TeeUpBanner.show(this@ScorecardActivity, e.message ?: getString(R.string.scorecard_load_failed), isError = true)
                // Finishing back to the Scorecard tab would only reopen this same round and fail again.
                if (isResumingActiveRound) {
                    startActivity(Intent(this@ScorecardActivity, ScorecardActivity::class.java).putExtra(EXTRA_SHOW_LIST, true))
                }
                finish()
            }
        }
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

        ui.courseName = course?.name ?: getString(R.string.rounds_unknown_course)
        ui.dateText = formatTeeTime(round.dateTime)
        ui.loading = false
        val startHole = currentHole
        val startTarget = currentTarget
        val holes = scoring.holes
        mapHost?.withController { map ->
            if (course != null) map.start(course.id, course.name, startHole, startTarget, holes) else map.showUnavailable()
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
        mapHost?.withController { it.showHole(hole, keepFollowing) }
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

    override fun onMarkersMovedChanged(moved: Boolean) {
        ui.showResetMarkers = moved
    }

    override fun onTargetMoved(hole: Int, target: GeoPoint) {
        if (hole != currentHole) return
        currentTarget = target
        persistProgress()
    }

    private fun updateFollowButton() {
        val map = mapHost?.controller
        ui.showFollowGps = map != null && !map.isFollowingGps && map.mappedHoles.isNotEmpty()
    }

    private fun showScoreSheet() {
        val hole = currentHole
        if (scoring.isSaved(hole)) return
        // Editing a hole already entered this round starts from what was there.
        ui.scoreSheet = ScoreSheet(hole, scoring.pending[hole], finishes = scoring.isLastToScore(hole))
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
        if (mapHost?.controller?.isFollowingOnCourse != true && next != null) moveToHole(next, keepFollowing = true)
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
        ui.hole = currentHole
        ui.canPrevious = currentHole > scoring.holes.first
        ui.canNext = currentHole < scoring.holes.last
        val score = scoring.scoreFor(currentHole)
        // Holes already on the server can't be re-sent (the API rejects a repeated hole), so
        // they're shown read-only; fix one by deleting it on the round summary.
        ui.scoreLabel = when {
            score == null -> ScoreLabel.Add
            scoring.isSaved(currentHole) -> ScoreLabel.Saved(score.strokes, score.putts)
            else -> ScoreLabel.Pending(score.strokes, score.putts)
        }
        ui.scored = scoring.scoredCount
        ui.totalHoles = totalHoles
        ui.isComplete = scoring.isComplete
        ui.canDelete = currentRoundId != null
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

    private fun deleteRound() {
        val roundId = currentRoundId ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { TeeUpApiClient.deleteRound(roundId) }
                closeRound()
                TeeUpBanner.show(this@ScorecardActivity, getString(R.string.summary_round_deleted))
                finish()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                TeeUpBanner.show(this@ScorecardActivity, e.message ?: getString(R.string.summary_delete_round_failed_fallback), isError = true)
            }
        }
    }

    private fun finishRound() {
        val id = teeTimeId ?: return
        val entries = scoring.pending.values.sortedBy { it.holeNumber }
        ui.loading = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { TeeUpApiClient.postScorecard(id, entries) }
                closeRound()
                TeeUpBanner.show(this@ScorecardActivity, getString(R.string.scorecard_finish_success))
                openSummary()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.loading = false
                TeeUpBanner.show(this@ScorecardActivity, e.message ?: getString(R.string.scorecard_save_failed), isError = true)
            }
        }
    }

    private fun openSummary() {
        val id = teeTimeId ?: return
        openSummaryFor(id)
        finish()
    }

    private fun normalizedHoles(count: Int) = if (count == 9) 9 else 18
}

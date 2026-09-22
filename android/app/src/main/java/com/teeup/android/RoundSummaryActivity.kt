package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.HoleScore
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.PlayedRound
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner
import java.util.Locale

/** Screen shown after a round is finished (or reopened from My Rounds → History) with its final totals. */
class RoundSummaryActivity : LocaleActivity() {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

    private var teeTimeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_round_summary)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        findViewById<Button>(R.id.button_done).setOnClickListener {
            startActivity(Intent(this, RoundsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        val id = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        if (id == null) {
            TeeUpBanner.show(this, getString(R.string.round_missing), isError = true)
            finish()
            return
        }
        teeTimeId = id
        loadRound(id)
    }

    private fun loadRound(teeTimeId: String) {
        findViewById<View>(R.id.summary_progress).visibility = View.VISIBLE

        Thread {
            try {
                val match = TeeUpApiClient.fetchSchedule().firstOrNull { it.teeTimeId == teeTimeId }
                if (match == null) {
                    runOnUiThread {
                        TeeUpBanner.show(this, getString(R.string.round_missing), isError = true)
                        finish()
                    }
                    return@Thread
                }

                val course = try {
                    TeeUpApiClient.fetchCourses().firstOrNull { it.id == match.courseId }
                } catch (e: Exception) {
                    null
                } ?: MockCatalog.courseById(match.courseId)

                runOnUiThread { render(match, course, match.round) }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<View>(R.id.summary_progress).visibility = View.GONE
                    TeeUpBanner.show(this, e.message ?: getString(R.string.summary_load_failed), isError = true)
                }
            }
        }.start()
    }

    private fun render(round: ScheduledRound, course: Course?, playedRound: PlayedRound?) {
        findViewById<View>(R.id.summary_progress).visibility = View.GONE

        findViewById<TextView>(R.id.round_reference).text = getString(
            R.string.round_reference,
            "${course?.name ?: getString(R.string.rounds_unknown_course)} · ${formatTeeTime(round.dateTime)}"
        )

        val holes = playedRound?.scorecard?.sortedBy { it.holeNumber } ?: emptyList()
        val unavailable = getString(R.string.summary_stat_unavailable)

        // Totals/averages/net/Stableford all come from the server (RoundDto, EME-304) — SUM/AVG
        // aggregated over the scorecard rows there, not recomputed on-device.
        findViewById<TextView>(R.id.text_holes_played).text = holes.size.toString()
        findViewById<TextView>(R.id.text_total_strokes).text = (playedRound?.totalStrokes ?: 0).toString()
        findViewById<TextView>(R.id.text_total_putts).text = (playedRound?.totalPutts ?: 0).toString()
        findViewById<TextView>(R.id.text_avg_putts).text =
            playedRound?.let { String.format(Locale.getDefault(), "%.1f", it.averagePutts) } ?: unavailable
        findViewById<TextView>(R.id.text_net_score).text =
            playedRound?.netScore?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: unavailable
        findViewById<TextView>(R.id.text_stableford_score).text =
            playedRound?.stablefordScore?.toString() ?: unavailable
        findViewById<View>(R.id.text_handicap_hint).visibility =
            if (holes.isNotEmpty() && playedRound?.netScore == null) View.VISIBLE else View.GONE

        val container = findViewById<LinearLayout>(R.id.holes_container)
        container.removeAllViews()
        if (holes.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.summary_no_holes)
                setTextColor(resources.getColor(R.color.teeup_text_secondary, theme))
            })
        } else {
            holes.forEach { hole -> container.addView(buildHoleRow(hole, round.round?.id)) }
        }
    }

    /** [roundId] is null right after a round is created with no scorecard yet posted — shouldn't
     *  happen alongside a non-empty [holes] list, but the delete action is simply omitted then. */
    private fun buildHoleRow(hole: HoleScore, roundId: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            elevation = resources.getDimension(R.dimen.elevation_card)
            val padding = resources.getDimension(R.dimen.space_lg).toInt()
            setPadding(padding, padding, padding, padding)
            val marginBottom = resources.getDimension(R.dimen.space_sm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = marginBottom }
        }

        row.addView(TextView(this).apply {
            text = getString(R.string.summary_hole_row_format, hole.holeNumber, hole.strokes, hole.putts)
            setTextColor(resources.getColor(R.color.teeup_text_primary, theme))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (roundId != null) {
            row.addView(Button(this).apply {
                text = getString(R.string.summary_delete_hole)
                setBackgroundResource(R.drawable.bg_button_danger_outline)
                setTextColor(resources.getColor(R.color.teeup_danger, theme))
                setOnClickListener { confirmDeleteHole(roundId, hole.holeNumber) }
            })
        }

        return row
    }

    private fun confirmDeleteHole(roundId: String, holeNumber: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.summary_delete_confirm_title)
            .setMessage(R.string.summary_delete_confirm_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> deleteHole(roundId, holeNumber) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteHole(roundId: String, holeNumber: Int) {
        val id = teeTimeId ?: return
        Thread {
            try {
                TeeUpApiClient.deleteScorecardEntry(roundId, holeNumber)
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.summary_hole_deleted))
                    loadRound(id)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.summary_delete_failed_fallback), isError = true)
                }
            }
        }.start()
    }
}

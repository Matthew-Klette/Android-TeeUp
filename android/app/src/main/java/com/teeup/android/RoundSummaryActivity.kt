package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.Course
import com.teeup.android.data.HoleScore
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatTeeTime
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/** Screen shown after a round is finished (or reopened from My Rounds → History) with its final totals. */
class RoundSummaryActivity : LocaleActivity() {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

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

        val teeTimeId = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        if (teeTimeId == null) {
            TeeUpBanner.show(this, getString(R.string.round_missing), isError = true)
            finish()
            return
        }
        loadRound(teeTimeId)
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

                val holes = match.round?.scorecard?.sortedBy { it.holeNumber } ?: emptyList()
                runOnUiThread { render(match, course, holes) }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<View>(R.id.summary_progress).visibility = View.GONE
                    TeeUpBanner.show(this, e.message ?: "Couldn't load this round", isError = true)
                }
            }
        }.start()
    }

    private fun render(round: ScheduledRound, course: Course?, holes: List<HoleScore>) {
        findViewById<View>(R.id.summary_progress).visibility = View.GONE

        findViewById<TextView>(R.id.round_reference).text = getString(
            R.string.round_reference,
            "${course?.name ?: getString(R.string.rounds_unknown_course)} · ${formatTeeTime(round.dateTime)}"
        )

        findViewById<TextView>(R.id.text_holes_played).text = holes.size.toString()
        findViewById<TextView>(R.id.text_total_strokes).text = holes.sumOf { it.strokes }.toString()
        findViewById<TextView>(R.id.text_total_putts).text = holes.sumOf { it.putts }.toString()

        val container = findViewById<LinearLayout>(R.id.holes_container)
        container.removeAllViews()
        if (holes.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.summary_no_holes)
                setTextColor(resources.getColor(R.color.teeup_text_secondary, theme))
            })
        } else {
            holes.forEach { hole -> container.addView(buildHoleRow(hole)) }
        }
    }

    private fun buildHoleRow(hole: HoleScore): View = TextView(this).apply {
        text = getString(R.string.summary_hole_row_format, hole.holeNumber, hole.strokes, hole.putts)
        setTextColor(resources.getColor(R.color.teeup_text_primary, theme))
        setBackgroundResource(R.drawable.bg_card)
        elevation = resources.getDimension(R.dimen.elevation_card)
        val padding = resources.getDimension(R.dimen.space_lg).toInt()
        setPadding(padding, padding, padding, padding)
        val marginBottom = resources.getDimension(R.dimen.space_sm).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = marginBottom }
    }
}

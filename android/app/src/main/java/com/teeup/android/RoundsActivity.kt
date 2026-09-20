package com.teeup.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.selectRounds
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import kotlinx.coroutines.launch

class RoundsActivity : ComponentActivity() {
    private val model: RoundsViewModel by viewModels()
    private var history = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rounds)
        BottomNav.wire(this, BottomNavTab.ROUNDS)
        history = savedInstanceState?.getBoolean("history") ?: intent.getBooleanExtra("history", false)
        findViewById<View>(R.id.rounds_upcoming).setOnClickListener { history = false; render() }
        findViewById<View>(R.id.rounds_history).setOnClickListener { history = true; render() }
        findViewById<View>(R.id.rounds_retry).setOnClickListener { model.load() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render() } }
        }
    }

    override fun onResume() { super.onResume(); model.load() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("history", history)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val state = model.state.value
        findViewById<View>(R.id.rounds_upcoming).isSelected = !history
        findViewById<View>(R.id.rounds_history).isSelected = history
        findViewById<View>(R.id.rounds_progress).visibility = if (state.loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rounds_retry).visibility = if (state.error != null) View.VISIBLE else View.GONE
        val container = findViewById<LinearLayout>(R.id.rounds_container)
        container.removeAllViews()
        val rows = selectRounds(state.rounds, history, System.currentTimeMillis())
        findViewById<TextView>(R.id.text_empty_state).apply {
            text = when {
                state.error != null -> getString(R.string.rounds_load_failed)
                state.loading -> getString(R.string.rounds_loading)
                rows.isEmpty() -> getString(if (history) R.string.rounds_history_empty else R.string.rounds_upcoming_empty)
                else -> ""
            }
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        if (state.loading || state.error != null) return
        rows.forEach { row ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = padding }
            }
            card.addView(TextView(this).apply {
                text = state.courses[row.courseId] ?: getString(R.string.rounds_unknown_course)
                setTextColor(getColor(R.color.teeup_text_primary)); textSize = 18f
            })
            card.addView(TextView(this).apply {
                text = formatTeeTime(row.dateTime)
                setTextColor(getColor(R.color.teeup_text_secondary))
            })
            fun action(label: Int, callback: () -> Unit) {
                card.addView(Button(this).apply { setText(label); setOnClickListener { callback() } })
            }
            action(R.string.rounds_view_details) {
                startActivity(Intent(this, TeeTimeDetailActivity::class.java)
                    .putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, row.teeTimeId))
            }
            if (history) {
                action(R.string.rounds_open_scorecard) { openRound(row, LiveScorecardActivity::class.java) }
                action(R.string.rounds_view_summary) { openRound(row, PostRoundSummaryActivity::class.java) }
            }
            container.addView(card)
        }
    }

    private fun openRound(round: ScheduledRound, destination: Class<*>) {
        startActivity(Intent(this, destination)
            .putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, round.teeTimeId))
    }
}

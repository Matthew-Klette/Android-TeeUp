package com.teeup.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.summary.RoundSummaryActions
import com.teeup.android.summary.RoundSummaryScreen
import com.teeup.android.summary.RoundSummaryViewModel
import com.teeup.android.summary.SummaryEvent
import com.teeup.android.ui.LocaleComponentActivity
import com.teeup.android.ui.TeeUpBanner
import com.teeup.android.ui.showNotice
import kotlinx.coroutines.launch

/** Screen shown after a round is finished (or reopened from My Rounds → History) with its final
 *  totals. The UI is [RoundSummaryScreen]; loading and deleting live in [RoundSummaryViewModel]. */
class RoundSummaryActivity : LocaleComponentActivity() {
    companion object {
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

    private val viewModel: RoundSummaryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val actions = RoundSummaryActions(
            back = {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            done = ::backToRounds
        )
        setContent { TeeUpTheme { RoundSummaryScreen(viewModel, actions) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SummaryEvent.Banner -> showNotice(event.notice)
                        SummaryEvent.Missing -> {
                            TeeUpBanner.show(this@RoundSummaryActivity, getString(R.string.round_missing), isError = true)
                            finish()
                        }
                        SummaryEvent.RoundDeleted -> backToRounds()
                    }
                }
            }
        }
    }

    /** Done, and after deleting the round: back to My Rounds rather than whatever opened this. */
    private fun backToRounds() {
        startActivity(Intent(this, RoundsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}

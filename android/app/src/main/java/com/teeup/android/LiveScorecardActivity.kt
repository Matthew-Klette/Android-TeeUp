package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Final POE owns scoring/GPS/sync. This destination only previews the wireflow. */
class LiveScorecardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scorecard)
        findViewById<View>(R.id.button_back).setOnClickListener { finish() }
        val teeTimeId = intent.getStringExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID)
        findViewById<TextView>(R.id.round_reference).text = if (teeTimeId == null)
            getString(R.string.round_missing) else getString(R.string.round_reference, teeTimeId)
        findViewById<View>(R.id.button_next_hole).apply {
            isEnabled = teeTimeId != null
            setOnClickListener {
                startActivity(Intent(this@LiveScorecardActivity, PostRoundSummaryActivity::class.java)
                    .putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, teeTimeId))
            }
        }
    }
}

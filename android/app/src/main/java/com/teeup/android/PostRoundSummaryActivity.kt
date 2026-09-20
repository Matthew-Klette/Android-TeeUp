package com.teeup.android

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Navigation destination only; no completion or score write is implied. */
class PostRoundSummaryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_round_summary)
        findViewById<View>(R.id.button_back).setOnClickListener { finish() }
        val id = intent.getStringExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID)
        findViewById<TextView>(R.id.round_reference).text = if (id == null)
            getString(R.string.round_missing) else getString(R.string.round_reference, id)
    }
}

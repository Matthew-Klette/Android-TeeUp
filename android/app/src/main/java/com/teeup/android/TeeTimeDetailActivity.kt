package com.teeup.android

import android.app.Activity
import android.os.Bundle

/** Screen 3 · Tee Time Detail / Join Request (EME-298 owns the real REST integration). */
class TeeTimeDetailActivity : Activity() {
    companion object {
        /** Set by HomeActivity (EME-297) when opening a specific tee time; EME-298 reads it to fetch details. */
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tee_time_detail)

        findViewById<android.view.View>(R.id.button_back).setOnClickListener { finish() }
        // button_request_to_join, row_pending_requests, row_group_chat: stubbed for EME-298/EME-299.
        // EXTRA_TEE_TIME_ID (intent.getStringExtra) is available for EME-298 to load the real tee time.
    }
}

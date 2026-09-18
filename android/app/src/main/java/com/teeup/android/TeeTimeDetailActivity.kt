package com.teeup.android

import android.app.Activity
import android.os.Bundle

/** Screen 3 · Tee Time Detail / Join Request (EME-298 owns the real REST integration). */
class TeeTimeDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tee_time_detail)

        findViewById<android.view.View>(R.id.button_back).setOnClickListener { finish() }
        // button_request_to_join, row_pending_requests, row_group_chat: stubbed for EME-298/EME-299.
    }
}

package com.teeup.android

import android.app.Activity
import android.os.Bundle

/** Screen 6 · Notifications. Reached from Home's bell icon; maps to GET /api/notifications. */
class NotificationsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        findViewById<android.view.View>(R.id.button_back).setOnClickListener { finish() }
    }
}

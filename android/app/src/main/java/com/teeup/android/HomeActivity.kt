package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

/** Screen 2 · Home / Find a Tee Time (EME-297 owns the real REST integration). */
class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        BottomNav.wire(this, BottomNavTab.HOME)

        findViewById<android.view.View>(R.id.button_notifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        val openTeeTimeDetail = { startActivity(Intent(this, TeeTimeDetailActivity::class.java)) }
        findViewById<android.view.View>(R.id.card_teetime_1).setOnClickListener { openTeeTimeDetail() }
        findViewById<android.view.View>(R.id.button_preview_1).setOnClickListener { openTeeTimeDetail() }
        findViewById<android.view.View>(R.id.button_book_1).setOnClickListener { openTeeTimeDetail() }
        findViewById<android.view.View>(R.id.card_teetime_2).setOnClickListener { openTeeTimeDetail() }
        findViewById<android.view.View>(R.id.button_preview_2).setOnClickListener { openTeeTimeDetail() }
        findViewById<android.view.View>(R.id.button_book_2).setOnClickListener { openTeeTimeDetail() }
    }
}

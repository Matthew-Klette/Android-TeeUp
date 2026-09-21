package com.teeup.android

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.NotificationPreferences
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Profile → Notification Preferences. EME-318: three of these four toggles now call
 * PATCH /api/profiles/me and persist server-side (see NotificationPreferences' doc
 * comment for exactly which category maps to which field, and why one — Sync Reminders —
 * still doesn't). Loads current values from GET /api/profiles/me on open, so a fresh
 * install/reinstall shows what's actually saved rather than this device's own cache.
 */
class NotificationPreferencesActivity : LocaleActivity() {
    private lateinit var statusText: TextView
    private lateinit var retryButton: View
    private lateinit var contentGroup: View
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_preferences)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        statusText = findViewById(R.id.text_status)
        retryButton = findViewById(R.id.button_retry)
        contentGroup = findViewById(R.id.group_content)
        container = findViewById(R.id.toggle_container)
        retryButton.setOnClickListener { loadProfile() }

        loadProfile()
    }

    private fun loadProfile() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            contentGroup.visibility = View.GONE
            retryButton.visibility = View.GONE
            statusText.text = getString(R.string.profile_load_signed_out)
            statusText.visibility = View.VISIBLE
            return
        }

        contentGroup.visibility = View.GONE
        retryButton.visibility = View.GONE
        statusText.text = getString(R.string.personal_details_loading)
        statusText.visibility = View.VISIBLE

        Thread {
            try {
                val user = TeeUpApiClient.fetchProfile()
                runOnUiThread { render(user) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = e.message ?: getString(R.string.profile_load_error_fallback)
                    statusText.visibility = View.VISIBLE
                    retryButton.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun render(user: RegisteredUser) {
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE

        container.removeAllViews()

        container.addView(buildServerToggleRow(
            labelRes = R.string.notification_pref_join_updates,
            initiallyChecked = user.joinRequestNotifications
        ) { enabled -> TeeUpApiClient.updateNotificationPreference(joinRequestNotifications = enabled) })

        container.addView(buildServerToggleRow(
            labelRes = R.string.notification_category_reminder,
            initiallyChecked = user.teeTimeReminders
        ) { enabled -> TeeUpApiClient.updateNotificationPreference(teeTimeReminders = enabled) })

        container.addView(buildServerToggleRow(
            labelRes = R.string.notification_category_weather,
            initiallyChecked = user.weatherAlerts
        ) { enabled -> TeeUpApiClient.updateNotificationPreference(weatherAlerts = enabled) })

        container.addView(buildLocalToggleRow(
            labelRes = R.string.notification_category_sync,
            initiallyChecked = NotificationPreferences.isSyncPendingEnabled(this)
        ) { enabled -> NotificationPreferences.setSyncPendingEnabled(this, enabled) })
    }

    /** Calls the backend on toggle; on failure, reverts the switch (without re-firing this
     *  same callback — see [buildToggleRow]) and shows a banner, so a flaky connection can't
     *  leave the UI claiming a preference that didn't actually save. */
    private fun buildServerToggleRow(labelRes: Int, initiallyChecked: Boolean, patch: (Boolean) -> Unit): View {
        lateinit var toggle: ToggleRow
        toggle = buildToggleRow(labelRes, initiallyChecked) { enabled ->
            toggle.switchView.isEnabled = false
            Thread {
                try {
                    patch(enabled)
                    runOnUiThread { toggle.switchView.isEnabled = true }
                } catch (e: Exception) {
                    runOnUiThread {
                        toggle.switchView.isEnabled = true
                        toggle.revert(!enabled)
                        TeeUpBanner.show(this, e.message ?: getString(R.string.profile_save_error_fallback), isError = true)
                    }
                }
            }.start()
        }
        return toggle.view
    }

    private fun buildLocalToggleRow(labelRes: Int, initiallyChecked: Boolean, onToggled: (Boolean) -> Unit): View =
        buildToggleRow(labelRes, initiallyChecked) { enabled -> onToggled(enabled) }.view

    private class ToggleRow(val view: View, val switchView: Switch, val revert: (Boolean) -> Unit)

    private fun buildToggleRow(labelRes: Int, initiallyChecked: Boolean, onToggled: (Boolean) -> Unit): ToggleRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            elevation = resources.getDimension(R.dimen.elevation_card)
            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = space(R.dimen.space_sm) }
        }

        row.addView(TextView(this).apply {
            text = getString(labelRes)
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Programmatic reverts go through this flag rather than detaching/reattaching the
        // listener, so a failed PATCH can flip the switch back without re-triggering onToggled
        // (which would otherwise fire another PATCH call attempting to "revert" the revert).
        var suppressCallback = false
        val switchView = Switch(this).apply {
            isChecked = initiallyChecked
            setOnCheckedChangeListener { _, isChecked -> if (!suppressCallback) onToggled(isChecked) }
        }
        row.addView(switchView)

        fun revert(checked: Boolean) {
            suppressCallback = true
            switchView.isChecked = checked
            suppressCallback = false
        }

        return ToggleRow(row, switchView, ::revert)
    }

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)
    private fun space(dimensionRes: Int): Int = resources.getDimensionPixelSize(dimensionRes)
}

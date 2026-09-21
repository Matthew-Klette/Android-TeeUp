package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.teeup.android.data.NotificationCategory
import com.teeup.android.data.NotificationPreferences
import com.teeup.android.ui.LocaleActivity

/**
 * Profile → Notification Preferences. On-device only (see NotificationPreferences doc
 * comment); each toggle here is read by NotificationsActivity to actually show/hide its
 * sample rows, so this isn't an inert settings screen.
 */
class NotificationPreferencesActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_preferences)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val container = findViewById<LinearLayout>(R.id.toggle_container)
        NotificationCategory.entries.forEach { category ->
            container.addView(buildToggleRow(category))
        }
    }

    private fun buildToggleRow(category: NotificationCategory): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
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
            text = getString(category.labelRes)
            setTextColor(colorOf(R.color.teeup_text_primary))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body_large))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(Switch(this).apply {
            isChecked = NotificationPreferences.isEnabled(this@NotificationPreferencesActivity, category)
            setOnCheckedChangeListener { _, isChecked ->
                NotificationPreferences.setEnabled(this@NotificationPreferencesActivity, category, isChecked)
            }
        })

        return row
    }

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)
    private fun space(dimensionRes: Int): Int = resources.getDimensionPixelSize(dimensionRes)
}

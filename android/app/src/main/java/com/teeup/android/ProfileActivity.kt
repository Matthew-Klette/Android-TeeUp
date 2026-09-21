package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.LocaleManager

/** Screen 4 · Profile & Settings. Every row here is a real screen — see each Activity's
 *  own doc comment for what's genuinely backed by the API vs. on-device only. */
class ProfileActivity : LocaleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        BottomNav.wire(this, BottomNavTab.PROFILE)

        findViewById<android.view.View>(R.id.row_language).setOnClickListener { showLanguageDialog() }

        val rowDestinations = mapOf(
            R.id.row_personal_details to PersonalDetailsActivity::class.java,
            R.id.row_playing_details to PlayingDetailsActivity::class.java,
            R.id.row_notification_preferences to NotificationPreferencesActivity::class.java,
            R.id.row_biometric_login to BiometricLoginActivity::class.java,
            R.id.row_offline_sync to OfflineSyncStatusActivity::class.java,
            R.id.row_privacy to PrivacyDataActivity::class.java
        )
        rowDestinations.forEach { (rowId, destination) ->
            findViewById<android.view.View>(rowId).setOnClickListener {
                startActivity(Intent(this, destination))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
            }
        }
    }

    private fun showLanguageDialog() {
        val currentTag = LocaleManager.getLanguageTag(this)
        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        lateinit var dialog: AlertDialog

        LocaleManager.SUPPORTED_LANGUAGES.forEach { language ->
            container.addView(Button(this).apply {
                text = if (language.tag == currentTag) "✓ ${language.label}" else language.label
                isEnabled = language.tag != currentTag
                setBackgroundResource(
                    if (language.tag == currentTag) R.drawable.bg_button_primary else R.drawable.bg_button_outline
                )
                setTextColor(
                    resources.getColor(
                        if (language.tag == currentTag) R.color.teeup_text_on_primary else R.color.teeup_text_primary,
                        theme
                    )
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }

                // The literal click of this button is what changes the app's language.
                setOnClickListener {
                    LocaleManager.setLanguageTag(this@ProfileActivity, language.tag)
                    dialog.dismiss()
                    restartAppAtHome()
                }
            })
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_language_dialog_title))
            .setView(container)
            .setNegativeButton(getString(R.string.detail_back), null)
            .create()
        dialog.show()
    }

    /** Every open Activity was created under the old Locale, so the whole task is
     *  relaunched fresh at Home rather than just recreating this one screen. */
    private fun restartAppAtHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}

package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.AuthSession
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.LocaleManager
import java.text.NumberFormat

/** Screen 4 · Profile & Settings. Every row here is a real screen — see each Activity's
 *  own doc comment for what's genuinely backed by the API vs. on-device only. */
class ProfileActivity : LocaleActivity() {
    private lateinit var nameText: TextView
    private lateinit var detailsText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: View
    private lateinit var retryButton: View
    private lateinit var roundsStatText: TextView
    private lateinit var handicapStatText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        BottomNav.wire(this, BottomNavTab.PROFILE)

        nameText = findViewById(R.id.text_profile_name)
        detailsText = findViewById(R.id.text_profile_details)
        statusText = findViewById(R.id.text_profile_status)
        progress = findViewById(R.id.profile_progress)
        retryButton = findViewById(R.id.button_profile_retry)
        roundsStatText = findViewById(R.id.text_stat_rounds)
        handicapStatText = findViewById(R.id.text_stat_handicap)
        retryButton.setOnClickListener { loadProfile() }

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

        findViewById<Button>(R.id.button_sign_out).setOnClickListener { AuthSession.signOut(this) }
    }

    /** Personal/Playing/Notification Details are separate Activities on the back stack, not
     *  dialogs — this Activity is only resumed, not recreated, when the user backs out of one
     *  after saving, so onCreate alone left the header showing whatever was true when the
     *  screen first opened. Refreshing on every resume picks up edits made on those screens. */
    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    /** Refreshes the name/handicap summary at the top of the screen. The 2026-09 UI revamp
     *  (#15) split personal/playing details out into their own screens and, in the process,
     *  dropped the code that kept this header populated — it was left showing the layout's
     *  static "Display Name" / "Handicap · Home course" placeholders forever. Personal/Playing
     *  Details already load the same data this way (there's no separate GET, so the idempotent
     *  POST /api/auth/register doubles as "fetch current profile"), so this mirrors that. */
    private fun loadProfile() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            showLoading(false)
            showStatus(getString(R.string.profile_load_signed_out), showRetry = false)
            return
        }

        showLoading(true)
        showStatus(null, showRetry = false)

        Thread {
            try {
                val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
                val courses = try {
                    TeeUpApiClient.fetchCourses()
                } catch (e: Exception) {
                    emptyList()
                }
                val roundsPlayed = try {
                    TeeUpApiClient.fetchSchedule().count { it.round != null }
                } catch (e: Exception) {
                    null
                }
                runOnUiThread { render(user, courses.firstOrNull { it.id == user.homeCourseId }?.name, roundsPlayed) }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoading(false)
                    showStatus(e.message ?: getString(R.string.profile_load_error_fallback), showRetry = true)
                }
            }
        }.start()
    }

    private fun render(user: RegisteredUser, homeCourseName: String?, roundsPlayed: Int?) {
        showLoading(false)
        showStatus(null, showRetry = false)

        nameText.text = user.displayName

        val handicap = user.handicapIndex?.let {
            NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(it)
        }
        detailsText.text = getString(
            R.string.profile_details_summary,
            handicap ?: getString(R.string.profile_handicap_unset),
            homeCourseName ?: getString(R.string.register_home_course_none)
        )

        roundsStatText.text = roundsPlayed?.toString() ?: getString(R.string.profile_stat_value_placeholder)
        handicapStatText.text = handicap ?: getString(R.string.profile_stat_handicap_none)
    }

    private fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showStatus(message: String?, showRetry: Boolean) {
        statusText.text = message.orEmpty()
        statusText.visibility = if (message == null) View.GONE else View.VISIBLE
        retryButton.visibility = if (showRetry) View.VISIBLE else View.GONE
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

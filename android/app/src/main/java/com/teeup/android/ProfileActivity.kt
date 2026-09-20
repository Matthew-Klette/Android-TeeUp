package com.teeup.android

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import kotlinx.coroutines.launch
import java.text.NumberFormat
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.teeup.android.data.ProfileUpdateRequest
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch

class ProfileActivity : ComponentActivity() {
    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var nameText: TextView
    private lateinit var detailsText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: View
    private lateinit var retryButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ProfileActivity", "onCreate")
        setContentView(R.layout.activity_profile)

        BottomNav.wire(this, BottomNavTab.PROFILE)

        nameText = findViewById(R.id.text_profile_name)
        detailsText = findViewById(R.id.text_profile_details)
        statusText = findViewById(R.id.text_profile_status)
        progress = findViewById(R.id.profile_progress)
        retryButton = findViewById(R.id.button_profile_retry)

        retryButton.setOnClickListener {
            viewModel.retryLastAction()
        }

        findViewById<View>(R.id.row_personal_details).setOnClickListener {
            showPersonalDetails()
        }

        findViewById<View>(R.id.row_notification_preferences).setOnClickListener {
            showNotificationPreferences()
        }

        findViewById<View>(R.id.row_playing_details).setOnClickListener {
            val state = viewModel.state.value
            val profile = state.profile

            if (profile != null && !state.isLoading && !state.isSaving) {
                ProfilePlayingEditor.show(this, profile) { request ->
                    viewModel.saveProfile(request)
                }
            }
        }

        // Final POE: connect language, biometric authentication and offline sync.
        // Preview controls remain disabled so they cannot imply a saved change.
        findViewById<View>(R.id.row_language).setOnClickListener {
            val preview = Spinner(this).apply {
                contentDescription = getString(R.string.profile_language)
                adapter = ArrayAdapter(
                    this@ProfileActivity,
                    android.R.layout.simple_spinner_item,
                    resources.getStringArray(
                        R.array.profile_language_preview_options
                    )
                ).apply {
                    setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )
                }
                setSelection(0)
                isEnabled = false
            }

            showInformation(
                R.string.profile_language,
                R.string.profile_language_notice,
                preview
            )
        }

        findViewById<View>(R.id.row_biometric_login).setOnClickListener {
            val preview = Switch(this).apply {
                setText(R.string.profile_biometric_toggle)
                isChecked = false
                isEnabled = false
            }

            showInformation(
                R.string.profile_biometric_login,
                R.string.profile_biometric_notice,
                preview
            )
        }

        findViewById<View>(R.id.row_offline_sync).setOnClickListener {
            val preview = Switch(this).apply {
                setText(R.string.profile_offline_toggle)
                isChecked = false
                isEnabled = false
            }

            showInformation(
                R.string.profile_offline_sync,
                R.string.profile_offline_notice,
                preview
            )
        }

        findViewById<View>(R.id.row_privacy).setOnClickListener {
            showInformation(
                R.string.profile_privacy,
                R.string.profile_privacy_notice
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        val busy = state.isLoading || state.isSaving

        progress.visibility =
            if (busy) View.VISIBLE else View.GONE

        retryButton.visibility =
            if (!busy && (state.loadFailed || state.saveFailed)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        (retryButton as TextView).setText(
            if (state.saveFailed) R.string.profile_retry_save
            else R.string.profile_retry
        )

        val canEdit = state.profile != null && !busy

        listOf(
            R.id.row_personal_details,
            R.id.row_playing_details,
            R.id.row_notification_preferences
        ).forEach { id ->
            findViewById<View>(id).apply {
                isEnabled = canEdit
                alpha = if (canEdit) 1f else 0.5f
            }
        }

        val status = when {
            state.isLoading -> getString(R.string.profile_loading)
            state.isSaving -> getString(R.string.profile_saving)
            state.loadFailed ->
                state.errorMessage ?: getString(R.string.profile_load_failed)
            state.saveFailed -> getString(
                R.string.profile_save_error,
                state.errorMessage ?: getString(R.string.profile_save_failed)
            )
            state.saveSucceeded -> getString(R.string.profile_saved)
            else -> null
        }

        statusText.text = status.orEmpty()
        statusText.visibility =
            if (status == null) View.GONE else View.VISIBLE

        val profile = state.profile
        if (profile == null) {
            nameText.setText(
                if (state.isLoading) R.string.profile_loading
                else R.string.profile_not_loaded
            )
            detailsText.text = ""
            return
        }

        nameText.text = profile.displayName

        val handicap = profile.handicapIndex?.let {
            NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = 1
            }.format(it)
        } ?: getString(R.string.profile_handicap_unset)

        val pace = resources.getStringArray(R.array.pace_of_play_options)
            .getOrNull(profile.paceOfPlay)
            ?: getString(R.string.profile_pace_unknown)

        detailsText.text = getString(
            R.string.profile_summary,
            handicap,
            pace
        )
    }

    private fun showPersonalDetails() {
        val state = viewModel.state.value
        val profile = state.profile ?: return
        if (state.isLoading || state.isSaving) return

        val input = EditText(this).apply {
            hint = getString(R.string.register_display_name)
            contentDescription = getString(R.string.register_display_name)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true

            val previousAttempt = viewModel.pendingUpdate?.displayName
            setText(previousAttempt ?: profile.displayName)
            setSelection(text.length)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.profile_personal_details)
            .setView(container)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()

                if (name.isEmpty() || name.length > 100) {
                    input.error = getString(R.string.profile_name_invalid)
                    return@setOnClickListener
                }

                viewModel.saveProfile(
                    ProfileUpdateRequest(displayName = name)
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showNotificationPreferences() {
        val state = viewModel.state.value
        val profile = state.profile ?: return
        if (state.isLoading || state.isSaving) return

        val pending = viewModel.pendingUpdate
        val checked = booleanArrayOf(
            pending?.joinRequestNotifications ?: profile.joinRequestNotifications,
            pending?.teeTimeReminders ?: profile.teeTimeReminders,
            pending?.weatherAlerts ?: profile.weatherAlerts
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.profile_notification_preferences)
            .setMultiChoiceItems(
                R.array.profile_notification_options,
                checked
            ) { _, index, enabled ->
                checked[index] = enabled
            }
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_save) { _, _ ->
                viewModel.saveProfile(
                    ProfileUpdateRequest(
                        joinRequestNotifications = checked[0],
                        teeTimeReminders = checked[1],
                        weatherAlerts = checked[2]
                    )
                )
            }
            .show()
    }

    private fun showInformation(
        titleRes: Int,
        messageRes: Int,
        preview: View? = null
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)

            addView(TextView(this@ProfileActivity).apply {
                setText(messageRes)
                setPadding(0, 0, 0, padding)
            })

            preview?.let {
                addView(
                    it,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        val scroll = ScrollView(this).apply {
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
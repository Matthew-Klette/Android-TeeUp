package com.teeup.android

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.RegisteredUser
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Profile → Personal Details. Real PATCH /api/profiles/me — there's no separate GET, so
 * the current profile is read via the idempotent POST /api/auth/register (see
 * RegisteredUser's doc comment), then only displayName is edited here; the other fields
 * are carried through unchanged on save.
 */
class PersonalDetailsActivity : LocaleActivity() {
    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var nameInput: EditText
    private lateinit var saveButton: Button
    private var current: RegisteredUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_details)

        statusText = findViewById(R.id.text_status)
        contentGroup = findViewById(R.id.group_content)
        nameInput = findViewById(R.id.input_display_name)
        saveButton = findViewById(R.id.button_save)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        saveButton.setOnClickListener { onSaveClicked() }

        loadProfile()
    }

    private fun loadProfile() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            statusText.text = getString(R.string.profile_load_signed_out)
            return
        }

        Thread {
            try {
                val user = TeeUpApiClient.register(firebaseUser.uid, firebaseUser.displayName ?: "TeeUp Golfer")
                runOnUiThread { render(user, firebaseUser.email) }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = e.message ?: getString(R.string.profile_load_error_fallback) }
            }
        }.start()
    }

    private fun render(user: RegisteredUser, email: String?) {
        current = user
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE
        nameInput.setText(user.displayName)
        findViewById<TextView>(R.id.text_email).text = email ?: getString(R.string.personal_details_email_none)
    }

    private fun onSaveClicked() {
        val existing = current ?: return
        val newName = nameInput.text.toString().trim()
        if (newName.isEmpty()) {
            TeeUpBanner.show(this, getString(R.string.personal_details_name_required), isError = true)
            return
        }

        saveButton.isEnabled = false
        saveButton.setText(R.string.personal_details_saving)

        Thread {
            try {
                TeeUpApiClient.updateProfile(
                    displayName = newName,
                    handicapIndex = existing.handicapIndex,
                    homeCourseId = existing.homeCourseId,
                    paceOfPlay = existing.paceOfPlay,
                    profileComplete = true
                )
                runOnUiThread {
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    saveButton.isEnabled = true
                    saveButton.setText(R.string.personal_details_save)
                    TeeUpBanner.show(this, e.message ?: getString(R.string.profile_save_error_fallback), isError = true)
                }
            }
        }.start()
    }
}

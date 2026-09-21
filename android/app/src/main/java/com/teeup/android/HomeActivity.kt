package com.teeup.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.teeup.android.ui.runWhenActive
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.teeup.android.data.ApiException
import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatPrice
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.validHandicap
import com.teeup.android.data.handicapValue
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

class HomeActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var teeTimesContainer: LinearLayout
    private lateinit var skillFilterButton: Button

    /** EME-299: discover a group filtered by the host's handicap/pace of play. Null = no filter. */
    private var filterMaxHandicap: Double? = null
    private var filterPace: Int? = null
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        BottomNav.wire(this, BottomNavTab.HOME)

        statusText = findViewById(R.id.text_status)
        teeTimesContainer = findViewById(R.id.tee_times_container)
        skillFilterButton = findViewById(R.id.button_filter_skill)

        findViewById<View>(R.id.button_notifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        skillFilterButton.setOnClickListener { showSkillFilterDialog() }
        findViewById<View>(R.id.qa_retry).setOnClickListener { loadNearbyTeeTimes() }

        loadNearbyTeeTimes()
    }

    private fun showSkillFilterDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val handicapInput = EditText(this).apply {
            hint = getString(R.string.filter_max_handicap_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            filterMaxHandicap?.let { setText(it.toString()) }
        }
        container.addView(handicapInput)

        val paceLabels = listOf(getString(R.string.filter_pace_any)) +
            resources.getStringArray(R.array.pace_of_play_options)
        val paceSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            adapter = ArrayAdapter(this@HomeActivity, android.R.layout.simple_spinner_item, paceLabels)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection((filterPace ?: -1) + 1)
        }
        container.addView(paceSpinner)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.filter_skill_title)
            .setView(container)
            .setPositiveButton(R.string.filter_apply, null)
            .setNeutralButton(R.string.filter_clear) { _, _ ->
                filterMaxHandicap = null
                filterPace = null
                applyFilterState()
            }
            .setNegativeButton(R.string.filter_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = handicapInput.text.toString()
            if (!validHandicap(text)) {
                handicapInput.error = getString(R.string.profile_handicap_invalid)
                return@setOnClickListener
            }
            filterMaxHandicap = handicapValue(text)
            filterPace = (paceSpinner.selectedItemPosition - 1).takeIf { it in 0..2 }
            applyFilterState()
            dialog.dismiss()
        }
    }

    private fun applyFilterState() {
        skillFilterButton.setText(
            if (filterMaxHandicap != null || filterPace != null) {
                R.string.home_filter_active
            } else {
                R.string.home_filter_skill
            }
        )
        loadNearbyTeeTimes()
    }

    private fun loadNearbyTeeTimes() {
        val generation = ++loadGeneration
        val handicap = filterMaxHandicap
        val pace = filterPace
        findViewById<View>(R.id.qa_retry).visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.home_loading)
        teeTimesContainer.removeAllViews()

        Thread {
            try {
                val courses = TeeUpApiClient.fetchCourses()
                    .associateBy { it.id }

                val teeTimes = TeeUpApiClient.fetchTeeTimes(handicap, pace)

                runWhenActive {
                    if (generation == loadGeneration) renderTeeTimes(teeTimes, courses)
                }
            } catch (e: ApiException) {
                runWhenActive {
                    if (generation == loadGeneration) showError(e.message ?: getString(R.string.qa_load_failed))
                }
            } catch (e: Exception) {
                runWhenActive {
                    if (generation == loadGeneration) showError(getString(R.string.qa_load_failed))
                }
            }
        }.start()
    }

    private fun renderTeeTimes(
        teeTimes: List<TeeTime>,
        courses: Map<String, Course>
    ) {
        if (teeTimes.isEmpty()) {
            showError(getString(R.string.home_empty_no_teetimes))
            findViewById<View>(R.id.qa_retry).visibility = View.GONE
            return
        }

        statusText.visibility = View.GONE
        teeTimesContainer.removeAllViews()

        teeTimes.forEach { teeTime ->
            val courseName = courses[teeTime.courseId]?.name
                ?: "Unknown course"

            teeTimesContainer.addView(
                buildTeeTimeCard(teeTime, courseName)
            )
        }
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.qa_retry).visibility = View.VISIBLE
        teeTimesContainer.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun buildTeeTimeCard(
        teeTime: TeeTime,
        courseName: String
    ): View {
        val isOpenRound = teeTime.type == 1

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            ).apply {
                bottomMargin = space(R.dimen.space_md)
            }

            setBackgroundResource(
                if (isOpenRound) {
                    R.drawable.bg_card_outline_interactive
                } else {
                    R.drawable.bg_card_interactive
                }
            )

            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)

            isClickable = true
            isFocusable = true

            setOnClickListener {
                openTeeTimeDetail(teeTime.id)
            }
        }

        val roundType = getString(
            if (isOpenRound) {
                R.string.home_card_open_round
            } else {
                R.string.home_card_booking
            }
        )

        card.addView(TextView(this).apply {
            text = getString(
                R.string.home_card_heading,
                courseName.uppercase(),
                roundType
            )

            setTextColor(colorOf(R.color.teeup_text_secondary))

            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_caption)
            )

            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            ).apply {
                bottomMargin = space(R.dimen.space_xs)
            }
        })

        card.addView(TextView(this).apply {
            text = formatTeeTime(teeTime.dateTime)
            setTextColor(colorOf(R.color.teeup_text_primary))

            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_body_large)
            )

            setTypeface(typeface, android.graphics.Typeface.BOLD)

            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            ).apply {
                bottomMargin = space(R.dimen.space_xs)
            }
        })

        card.addView(TextView(this).apply {
            text = getString(
                R.string.home_card_availability,
                teeTime.openSpots,
                formatPrice(teeTime.price)
            )

            setTextColor(colorOf(R.color.teeup_text_secondary))

            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_body)
            )

            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            ).apply {
                bottomMargin = space(R.dimen.space_lg)
            }
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false

            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                WRAP_CONTENT
            )
        }

        // Both buttons inherit TeeUpButton from the application theme.
        buttonRow.addView(Button(this).apply {
            setText(R.string.home_card_preview)

            layoutParams = LinearLayout.LayoutParams(
                0,
                WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = space(R.dimen.space_sm)
            }

            setOnClickListener {
                openTeeTimeDetail(teeTime.id)
            }
        })

        buttonRow.addView(Button(this).apply {
            setText(R.string.home_card_book)

            layoutParams = LinearLayout.LayoutParams(
                0,
                WRAP_CONTENT,
                1f
            )

            setOnClickListener {
                openTeeTimeDetail(teeTime.id)
            }
        })

        card.addView(buttonRow)
        return card
    }

    private fun openTeeTimeDetail(teeTimeId: String) {
        val intent = Intent(this, TeeTimeDetailActivity::class.java)
        intent.putExtra(
            TeeTimeDetailActivity.EXTRA_TEE_TIME_ID,
            teeTimeId
        )
        startActivity(intent)
    }

    private fun colorOf(colorRes: Int): Int =
        resources.getColor(colorRes, theme)

    private fun space(dimensionRes: Int): Int =
        resources.getDimensionPixelSize(dimensionRes)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}

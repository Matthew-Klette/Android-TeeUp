package com.teeup.android

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.ApiConfig
import com.teeup.android.data.ApiException
import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatPrice
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.formatTeeTime
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner
import java.util.Calendar
import java.util.TimeZone

class HomeActivity : LocaleActivity() {

    private enum class DateFilter { ALL, TODAY, TOMORROW }

    private lateinit var statusText: TextView
    private lateinit var teeTimesContainer: LinearLayout

    // Loaded once from the API; filters below are applied client-side against these.
    private var allTeeTimes: List<TeeTime> = emptyList()
    private var coursesById: Map<String, Course> = emptyMap()

    private var searchQuery: String = ""
    private var dateFilter: DateFilter = DateFilter.ALL
    private var minOpenSpots: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        BottomNav.wire(this, BottomNavTab.HOME)

        statusText = findViewById(R.id.text_status)
        teeTimesContainer = findViewById(R.id.tee_times_container)

        findViewById<View>(R.id.button_notifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
        }

        findViewById<EditText>(R.id.input_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilters()
            }
        })

        findViewById<Button>(R.id.button_filter_datetime).setOnClickListener {
            showOptionsDialog(getString(R.string.home_filter_datetime), listOf("All dates", "Today", "Tomorrow")) { index ->
                dateFilter = DateFilter.entries[index]
                updateFilterLabels()
                applyFilters()
            }
        }

        findViewById<Button>(R.id.button_filter_players).setOnClickListener {
            showOptionsDialog(
                getString(R.string.home_filter_players),
                listOf("Any", "1+ open spot", "2+ open spots", "3+ open spots", "4+ open spots")
            ) { index ->
                minOpenSpots = index
                updateFilterLabels()
                applyFilters()
            }
        }

        findViewById<Button>(R.id.button_filter_skill).setOnClickListener {
            TeeUpBanner.show(this, "Skill / Pace filtering isn't built yet — tee times have no skill or pace field to filter on.")
        }

        loadNearbyTeeTimes()
    }

    private fun loadNearbyTeeTimes() {
        statusText.visibility = View.GONE
        teeTimesContainer.removeAllViews()
        repeat(2) { teeTimesContainer.addView(buildShimmerCard()) }

        Thread {
            try {
                val courses = TeeUpApiClient.fetchCourses()
                    .associateBy { it.id }

                val teeTimes = TeeUpApiClient.fetchTeeTimes()
                SyncStatus.recordSuccess(this)

                runOnUiThread {
                    allTeeTimes = teeTimes
                    coursesById = courses
                    applyFilters()
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    showError(e.message ?: "Request failed")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError(
                        "Couldn't reach the API at ${ApiConfig.BASE_URL} — is it running?"
                    )
                }
            }
        }.start()
    }

    /** Re-filters [allTeeTimes] by search text / date / min open spots and re-renders. */
    private fun applyFilters() {
        val filtered = allTeeTimes.filter { teeTime ->
            val courseName = coursesById[teeTime.courseId]?.name.orEmpty()
            val matchesSearch = searchQuery.isBlank() || courseName.contains(searchQuery, ignoreCase = true)
            val matchesDate = when (dateFilter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> isoDateOnly(teeTime.dateTime) == isoDate(0)
                DateFilter.TOMORROW -> isoDateOnly(teeTime.dateTime) == isoDate(1)
            }
            val matchesPlayers = teeTime.openSpots >= minOpenSpots
            matchesSearch && matchesDate && matchesPlayers
        }

        if (filtered.isEmpty()) {
            showError(if (allTeeTimes.isEmpty()) "No tee times nearby right now" else "No tee times match your filters")
        } else {
            renderTeeTimes(filtered)
        }
    }

    private fun isoDateOnly(isoDateTime: String): String = isoDateTime.substring(0, 10)

    /** Today (daysFromToday=0) or a day offset from it, in UTC to match how tee time timestamps are displayed (see Formatting.kt). */
    private fun isoDate(daysFromToday: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, daysFromToday)
        }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun updateFilterLabels() {
        findViewById<Button>(R.id.button_filter_datetime).text = when (dateFilter) {
            DateFilter.ALL -> getString(R.string.home_filter_datetime)
            DateFilter.TODAY -> "Date: Today"
            DateFilter.TOMORROW -> "Date: Tomorrow"
        }
        findViewById<Button>(R.id.button_filter_players).text =
            if (minOpenSpots == 0) getString(R.string.home_filter_players) else "Players: $minOpenSpots+"
    }

    /** Same pattern as ProfileActivity's language picker: a titled dialog of plain buttons, one tap picks and closes it. */
    private fun showOptionsDialog(title: String, options: List<String>, onSelected: (Int) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        lateinit var dialog: AlertDialog

        options.forEachIndexed { index, label ->
            container.addView(Button(this).apply {
                text = label
                setBackgroundResource(R.drawable.bg_button_outline)
                setTextColor(colorOf(R.color.teeup_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                setOnClickListener {
                    onSelected(index)
                    dialog.dismiss()
                }
            })
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun renderTeeTimes(teeTimes: List<TeeTime>) {
        statusText.visibility = View.GONE
        teeTimesContainer.removeAllViews()

        teeTimes.forEachIndexed { index, teeTime ->
            val course = coursesById[teeTime.courseId]

            val card = buildTeeTimeCard(teeTime, course)
            teeTimesContainer.addView(card)

            // Small staggered fade + rise so the list feels alive rather than
            // just popping in once the network call resolves.
            card.alpha = 0f
            card.translationY = dp(12).toFloat()
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 60L)
                .setDuration(260)
                .start()
        }
    }

    private fun showError(message: String) {
        teeTimesContainer.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun buildTeeTimeCard(
        teeTime: TeeTime,
        course: Course?
    ): View {
        val courseName = course?.name ?: "Unknown course"
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
            elevation = resources.getDimension(R.dimen.elevation_card)

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

        // Preview inherits the theme's default (secondary/outline) button style;
        // Book is the primary action for this card, so it gets the solid-fill style.
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
                openCoursePreview(course)
            }
        })

        buttonRow.addView(Button(this).apply {
            setText(R.string.home_card_book)
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(colorOf(R.color.teeup_text_on_primary))
            elevation = resources.getDimension(R.dimen.elevation_button)
            stateListAnimator = null

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

    /** A skeleton placeholder shown in place of a real card while the network call is in flight. */
    private fun buildShimmerCard(): View {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = space(R.dimen.space_md)
            }
            setBackgroundResource(R.drawable.bg_card)
            clipToOutline = true
        }

        fun block(widthDp: Int, heightDp: Int, marginBottomDimen: Int?) = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
                marginBottomDimen?.let { bottomMargin = space(it) }
            }
            setBackgroundResource(R.drawable.bg_shimmer_block)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = space(R.dimen.space_lg)
            setPadding(padding, padding, padding, padding)

            addView(block(110, 10, R.dimen.space_sm))
            addView(block(170, 18, R.dimen.space_sm))
            addView(block(130, 12, R.dimen.space_lg))
            addView(View(this@HomeActivity).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40))
                setBackgroundResource(R.drawable.bg_shimmer_block)
            })
        }
        card.addView(content)

        val sheen = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(90), MATCH_PARENT)
            setBackgroundResource(R.drawable.bg_shimmer_sheen)
            rotation = 14f
            translationX = -dp(160).toFloat()
        }
        card.addView(sheen)

        ObjectAnimator.ofFloat(sheen, "translationX", -dp(160).toFloat(), dp(420).toFloat()).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }.start()

        return card
    }

    private fun openTeeTimeDetail(teeTimeId: String) {
        val intent = Intent(this, TeeTimeDetailActivity::class.java)
        intent.putExtra(
            TeeTimeDetailActivity.EXTRA_TEE_TIME_ID,
            teeTimeId
        )
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun openCoursePreview(course: Course?) {
        if (course == null) {
            TeeUpBanner.show(this, "Course details aren't available yet")
            return
        }
        val intent = Intent(this, CoursePreviewActivity::class.java).apply {
            putExtra(CoursePreviewActivity.EXTRA_COURSE_NAME, course.name)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LAT, course.latitude)
            putExtra(CoursePreviewActivity.EXTRA_COURSE_LNG, course.longitude)
            course.rating?.let { putExtra(CoursePreviewActivity.EXTRA_COURSE_RATING, it) }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun colorOf(colorRes: Int): Int =
        resources.getColor(colorRes, theme)

    private fun space(dimensionRes: Int): Int =
        resources.getDimensionPixelSize(dimensionRes)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
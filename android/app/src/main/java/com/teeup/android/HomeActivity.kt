package com.teeup.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.ApiConfig
import com.teeup.android.data.ApiException
import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.formatPrice
import com.teeup.android.data.formatTeeTime
import com.teeup.android.nav.BottomNav
import com.teeup.android.nav.BottomNavTab

/**
 * Screen 2 · Home / Find a Tee Time.
 * Loads real data from GET /api/teetimes + GET /api/courses (EME-297) —
 * the two static sample cards from the EME-308 scaffold are gone; cards
 * are now built at runtime from whatever the API returns.
 */
class HomeActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var teeTimesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        BottomNav.wire(this, BottomNavTab.HOME)

        statusText = findViewById(R.id.text_status)
        teeTimesContainer = findViewById(R.id.tee_times_container)

        findViewById<View>(R.id.button_notifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        loadNearbyTeeTimes()
    }

    private fun loadNearbyTeeTimes() {
        statusText.visibility = View.VISIBLE
        statusText.text = "Loading nearby tee times..."
        teeTimesContainer.removeAllViews()

        Thread {
            try {
                val courses = TeeUpApiClient.fetchCourses().associateBy { it.id }
                val teeTimes = TeeUpApiClient.fetchTeeTimes()
                runOnUiThread { renderTeeTimes(teeTimes, courses) }
            } catch (e: ApiException) {
                runOnUiThread { showError(e.message ?: "Request failed") }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Couldn't reach the API at ${ApiConfig.BASE_URL} — is it running?")
                }
            }
        }.start()
    }

    private fun renderTeeTimes(teeTimes: List<TeeTime>, courses: Map<String, Course>) {
        if (teeTimes.isEmpty()) {
            showError("No tee times nearby right now")
            return
        }

        statusText.visibility = View.GONE
        teeTimesContainer.removeAllViews()
        teeTimes.forEach { teeTime ->
            val course = courses[teeTime.courseId]
            teeTimesContainer.addView(buildTeeTimeCard(teeTime, course?.name ?: "Unknown course"))
        }
    }

    private fun showError(message: String) {
        teeTimesContainer.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun buildTeeTimeCard(teeTime: TeeTime, courseName: String): View {
        val isOpenRound = teeTime.type == 1

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            setBackgroundResource(if (isOpenRound) R.drawable.bg_card_outline else R.drawable.bg_card)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openTeeTimeDetail(teeTime.id) }
        }

        card.addView(TextView(this).apply {
            text = "${courseName.uppercase()} · ${if (isOpenRound) "OPEN ROUND" else "BOOKING"}"
            setTextColor(colorOf(R.color.teeup_text_secondary))
            textSize = 11f
        })

        card.addView(TextView(this).apply {
            text = formatTeeTime(teeTime.dateTime)
            setTextColor(colorOf(R.color.teeup_text_primary))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = "${teeTime.openSpots} spot(s) open · ${formatPrice(teeTime.price)}"
            setTextColor(colorOf(R.color.teeup_text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        buttonRow.addView(Button(this).apply {
            text = "Preview"
            setBackgroundResource(R.drawable.bg_card_outline)
            setTextColor(colorOf(R.color.teeup_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            setOnClickListener { openTeeTimeDetail(teeTime.id) }
        })

        buttonRow.addView(Button(this).apply {
            text = "Book"
            setBackgroundResource(R.drawable.bg_button_outline)
            setTextColor(colorOf(R.color.teeup_accent))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            setOnClickListener { openTeeTimeDetail(teeTime.id) }
        })

        card.addView(buttonRow)
        return card
    }

    private fun openTeeTimeDetail(teeTimeId: String) {
        val intent = Intent(this, TeeTimeDetailActivity::class.java)
        intent.putExtra(TeeTimeDetailActivity.EXTRA_TEE_TIME_ID, teeTimeId)
        startActivity(intent)
    }

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}

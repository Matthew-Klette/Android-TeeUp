package com.teeup.android

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.teeup.android.data.ApiException
import com.teeup.android.data.Course
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.JoinRequestStatus
import com.teeup.android.data.LocalIdentity
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.canRequestToJoin
import com.teeup.android.data.formatPrice
import com.teeup.android.data.formatTeeTime
import com.teeup.android.ui.LocaleActivity
import com.teeup.android.ui.TeeUpBanner

/**
 * Screen 3 · Tee Time Detail / Join Request.
 * Loads the real tee time + course from the API, wires Request to Join
 * (POST /api/teetimes/{id}/joinrequests) and a Pending Requests
 * approve/decline dialog (GET/PATCH added alongside this ticket — see
 * TeeTimesController.GetJoinRequests). Group Chat has no backend/ticket
 * yet, so it's gated with an honest placeholder message, not a fake screen.
 */
class TeeTimeDetailActivity : LocaleActivity() {
    companion object {
        /** Set by HomeActivity (EME-297) when opening a specific tee time. */
        const val EXTRA_TEE_TIME_ID = "com.teeup.android.extra.TEE_TIME_ID"
    }

    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var teeTimeId: String
    private var joinRequests: List<JoinRequest> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tee_time_detail)

        statusText = findViewById(R.id.text_status)
        contentGroup = findViewById(R.id.group_detail_content)

        findViewById<View>(R.id.button_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val id = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        if (id == null) {
            showError(getString(R.string.teetime_no_id))
            return
        }
        teeTimeId = id
        loadDetail()
    }

    private fun loadDetail() {
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.teetime_loading)
        contentGroup.visibility = View.GONE

        Thread {
            try {
                var reachedApi = false

                val teeTime = try {
                    val result = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                    reachedApi = true
                    result
                } catch (e: Exception) {
                    null
                } ?: MockCatalog.teeTimeById(teeTimeId) ?: throw ApiException(getString(R.string.teetime_not_found))

                val course = try {
                    TeeUpApiClient.fetchCourses().firstOrNull { it.id == teeTime.courseId }
                } catch (e: Exception) {
                    null
                } ?: MockCatalog.courseById(teeTime.courseId)

                val requests = try {
                    TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                } catch (e: Exception) {
                    emptyList()
                }

                // Only a genuine API round trip counts as a sync (see SyncStatus's doc
                // comment) — a tee time resolved purely from MockCatalog isn't one.
                if (reachedApi) SyncStatus.recordSuccess(this)
                runOnUiThread { render(teeTime, course, requests) }
            } catch (e: Exception) {
                runOnUiThread { showError(e.message ?: getString(R.string.teetime_load_failed)) }
            }
        }.start()
    }

    private fun render(teeTime: TeeTime, course: Course?, requests: List<JoinRequest>) {
        joinRequests = requests
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE

        findViewById<TextView>(R.id.text_teetime_title).text =
            "${course?.name ?: getString(R.string.teetime_unknown_course)} · ${formatTeeTime(teeTime.dateTime)}"
        findViewById<TextView>(R.id.text_teetime_subtitle).text =
            getString(R.string.teetime_subtitle_format, teeTime.openSpots, formatPrice(teeTime.price))

        findViewById<Button>(R.id.button_preview_course).setOnClickListener {
            openCoursePreview(course)
        }

        val acceptedCount = requests.count { it.status == JoinRequestStatus.ACCEPTED }
        val pendingCount = requests.count { it.status == JoinRequestStatus.PENDING }

        findViewById<TextView>(R.id.text_host_info).text = when {
            teeTime.hostUserId == null && teeTime.type == 0 -> getString(R.string.teetime_host_booking_no_host)
            teeTime.hostUserId == null -> getString(R.string.teetime_host_open_no_host)
            else -> getString(R.string.teetime_host_hosted)
        }
        // Host handicap/pace/home-course can't be shown yet: there's no public-profile-by-id
        // endpoint (ProfilesController only exposes PATCH /me for the caller's own profile),
        // and adding one raises the same privacy question flagged elsewhere in this POE
        // (Privacy & Data / POPIA). Worth a follow-up ticket, not solved here.
        findViewById<TextView>(R.id.text_host_subtitle).text =
            getString(R.string.teetime_host_subtitle_format, acceptedCount, pendingCount)

        val requestButton = findViewById<Button>(R.id.button_request_to_join)
        val myUserIdIfKnown = LocalIdentity.cachedUserIdOrNull(this)
        val alreadyRequested = myUserIdIfKnown != null && !canRequestToJoin(requests, myUserIdIfKnown)
        requestButton.isEnabled = !alreadyRequested
        requestButton.text = if (alreadyRequested) getString(R.string.teetime_request_sent) else getString(R.string.teetime_request_to_join)
        requestButton.setOnClickListener { onRequestToJoinClicked() }

        findViewById<TextView>(R.id.text_pending_requests_subtitle).text =
            if (pendingCount > 0) getString(R.string.teetime_pending_waiting_format, pendingCount) else getString(R.string.teetime_pending_none)
        findViewById<View>(R.id.row_pending_requests).setOnClickListener { showPendingRequestsDialog() }

        findViewById<TextView>(R.id.text_group_chat_subtitle).text =
            if (acceptedCount > 0) getString(R.string.teetime_group_chat_tap_open) else getString(R.string.teetime_group_chat_locked_subtitle)
        findViewById<View>(R.id.row_group_chat).setOnClickListener {
            if (acceptedCount > 0) {
                TeeUpBanner.show(this, getString(R.string.teetime_group_chat_not_built))
            } else {
                TeeUpBanner.show(this, getString(R.string.teetime_group_chat_locked_banner))
            }
        }
    }

    private fun onRequestToJoinClicked() {
        val button = findViewById<Button>(R.id.button_request_to_join)
        button.isEnabled = false
        button.text = getString(R.string.teetime_sending)

        Thread {
            try {
                val userId = LocalIdentity.ensureRegistered(this)
                TeeUpApiClient.createJoinRequest(teeTimeId, userId)
                val refreshedRequests = TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                val refreshedTeeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                val refreshedCourse = refreshedTeeTime?.let { tt ->
                    TeeUpApiClient.fetchCourses().firstOrNull { it.id == tt.courseId }
                }
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.teetime_request_sent))
                    if (refreshedTeeTime != null) {
                        render(refreshedTeeTime, refreshedCourse, refreshedRequests)
                    }
                }
            } catch (e: IllegalStateException) {
                // LocalIdentity.ensureRegistered() throws this when there's no signed-in
                // Firebase user — surface a friendly prompt instead of the raw internal message.
                runOnUiThread {
                    TeeUpBanner.show(this, getString(R.string.teetime_signin_again), isError = true)
                    startActivity(Intent(this, SignInActivity::class.java))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.teetime_request_failed_fallback), isError = true)
                    button.isEnabled = true
                    button.text = getString(R.string.teetime_request_to_join)
                }
            }
        }.start()
    }

    private fun showPendingRequestsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        if (joinRequests.isEmpty()) {
            container.addView(TextView(this).apply { text = getString(R.string.teetime_no_join_requests) })
        } else {
            joinRequests.forEach { request -> container.addView(buildJoinRequestRow(request)) }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.teetime_join_requests_title)
            .setView(container)
            .setNegativeButton(R.string.teetime_close, null)
            .show()
    }

    private fun buildJoinRequestRow(request: JoinRequest): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }

        row.addView(TextView(this).apply {
            text = getString(R.string.teetime_guest_status_format, request.guestUserId.take(8), JoinRequestStatus.label(this@TeeTimeDetailActivity, request.status))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        if (request.status == JoinRequestStatus.PENDING) {
            row.addView(Button(this).apply {
                text = getString(R.string.teetime_accept)
                setBackgroundResource(R.drawable.bg_button_primary)
                setTextColor(colorOf(R.color.teeup_text_on_primary))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener { respondToJoinRequest(request.id, JoinRequestStatus.ACCEPTED) }
            })
            row.addView(Button(this).apply {
                text = getString(R.string.teetime_decline)
                setBackgroundResource(R.drawable.bg_button_danger_outline)
                setTextColor(colorOf(R.color.teeup_danger))
                setOnClickListener { respondToJoinRequest(request.id, JoinRequestStatus.DECLINED) }
            })
        }

        return row
    }

    private fun respondToJoinRequest(joinRequestId: String, status: Int) {
        Thread {
            try {
                TeeUpApiClient.updateJoinRequestStatus(joinRequestId, status)
                val requests = TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                val teeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                val course = teeTime?.let { tt -> TeeUpApiClient.fetchCourses().firstOrNull { it.id == tt.courseId } }
                runOnUiThread {
                    if (teeTime != null) {
                        render(teeTime, course, requests)
                    }
                    TeeUpBanner.show(this, getString(R.string.teetime_updated))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    TeeUpBanner.show(this, e.message ?: getString(R.string.teetime_update_failed_fallback), isError = true)
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        contentGroup.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun openCoursePreview(course: Course?) {
        if (course == null) {
            TeeUpBanner.show(this, getString(R.string.teetime_course_unavailable))
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

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun colorOf(colorRes: Int): Int = resources.getColor(colorRes, theme)
}

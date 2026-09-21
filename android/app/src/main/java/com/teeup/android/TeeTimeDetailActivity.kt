package com.teeup.android

import android.app.Activity
import android.app.AlertDialog
import com.teeup.android.ui.runWhenActive
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.teeup.android.data.ApiException
import com.teeup.android.data.Course
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.JoinRequestStatus
import com.teeup.android.data.LocalIdentity
import com.teeup.android.data.TeeTime
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.canRequestToJoin
import com.teeup.android.data.formatPrice
import com.teeup.android.data.formatTeeTime

/**
 * Screen 3 · Tee Time Detail / Join Request.
 * Loads the real tee time + course from the API, wires Request to Join
 * (POST /api/teetimes/{id}/joinrequests) and a Pending Requests
 * approve/decline dialog (GET/PATCH added alongside this ticket — see
 * TeeTimesController.GetJoinRequests). Group Chat has no backend/ticket
 * yet, so it's gated with an honest placeholder message, not a fake screen.
 */
class TeeTimeDetailActivity : Activity() {
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

        findViewById<View>(R.id.button_back).setOnClickListener { finish() }

        val id = intent.getStringExtra(EXTRA_TEE_TIME_ID)
        if (id == null) {
            showError("No tee time was specified")
            return
        }
        teeTimeId = id
        findViewById<View>(R.id.qa_retry).setOnClickListener { loadDetail() }
        loadDetail()
    }

    private fun loadDetail() {
        findViewById<View>(R.id.qa_retry).visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = "Loading tee time..."
        contentGroup.visibility = View.GONE

        Thread {
            try {
                val teeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                    ?: throw ApiException("Tee time not found")
                val course = TeeUpApiClient.fetchCourses().firstOrNull { it.id == teeTime.courseId }
                val requests = TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                runWhenActive { render(teeTime, course, requests) }
            } catch (e: Exception) {
                runWhenActive { showError(e.message ?: "Couldn't load this tee time") }
            }
        }.start()
    }

    private fun render(teeTime: TeeTime, course: Course?, requests: List<JoinRequest>) {
        joinRequests = requests
        statusText.visibility = View.GONE
        contentGroup.visibility = View.VISIBLE

        findViewById<TextView>(R.id.text_teetime_title).text =
            "${course?.name ?: "Unknown course"} · ${formatTeeTime(teeTime.dateTime)}"
        findViewById<TextView>(R.id.text_teetime_subtitle).text =
            "${teeTime.openSpots} spot(s) open · ${formatPrice(teeTime.price)}"

        val acceptedCount = requests.count { it.status == JoinRequestStatus.ACCEPTED }
        val pendingCount = requests.count { it.status == JoinRequestStatus.PENDING }

        findViewById<TextView>(R.id.text_host_info).text = when {
            teeTime.hostUserId == null && teeTime.type == 0 -> "Booking — reserved slot, no host required"
            teeTime.hostUserId == null -> "Open round — no host assigned yet"
            else -> "Hosted round"
        }
        // Host handicap/pace/home-course can't be shown yet: there's no public-profile-by-id
        // endpoint (ProfilesController only exposes PATCH /me for the caller's own profile),
        // and adding one raises the same privacy question flagged elsewhere in this POE
        // (Privacy & Data / POPIA). Worth a follow-up ticket, not solved here.
        findViewById<TextView>(R.id.text_host_subtitle).text =
            "$acceptedCount accepted · $pendingCount pending"

        val requestButton = findViewById<Button>(R.id.button_request_to_join)
        val myUserIdIfKnown = LocalIdentity.cachedUserIdOrNull(this)
        val alreadyRequested = myUserIdIfKnown != null && !canRequestToJoin(requests, myUserIdIfKnown)
        requestButton.isEnabled = !alreadyRequested
        requestButton.text = if (alreadyRequested) "Request sent" else "Request to Join"
        requestButton.setOnClickListener { onRequestToJoinClicked() }

        findViewById<TextView>(R.id.text_pending_requests_subtitle).text =
            if (pendingCount > 0) "$pendingCount waiting — tap to approve or decline" else "None pending"
        findViewById<View>(R.id.row_pending_requests).setOnClickListener { showPendingRequestsDialog() }

        findViewById<TextView>(R.id.text_group_chat_subtitle).text =
            if (acceptedCount > 0) "Tap to open" else "Opens once a request is accepted"
        findViewById<View>(R.id.row_group_chat).setOnClickListener {
            if (acceptedCount > 0) {
                Toast.makeText(this, "Group chat isn't built yet — no ticket/backend for it.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Opens once a join request is accepted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onRequestToJoinClicked() {
        val button = findViewById<Button>(R.id.button_request_to_join)
        button.isEnabled = false
        button.text = "Sending..."

        Thread {
            try {
                val userId = LocalIdentity.ensureRegistered(this)
                TeeUpApiClient.createJoinRequest(teeTimeId, userId)
                val refreshedRequests = TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                val refreshedTeeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                val refreshedCourse = refreshedTeeTime?.let { tt ->
                    TeeUpApiClient.fetchCourses().firstOrNull { it.id == tt.courseId }
                }
                runWhenActive {
                    Toast.makeText(this, "Request sent", Toast.LENGTH_SHORT).show()
                    if (refreshedTeeTime != null) {
                        render(refreshedTeeTime, refreshedCourse, refreshedRequests)
                    }
                }
            } catch (e: Exception) {
                runWhenActive {
                    Toast.makeText(this, e.message ?: "Couldn't send the request", Toast.LENGTH_LONG).show()
                    button.isEnabled = true
                    button.text = "Request to Join"
                }
            }
        }.start()
    }

    private fun showPendingRequestsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // EME-305 QA pass: the dialog is built from a snapshot (joinRequests). Once any
        // row's Accept/Decline succeeds, the snapshot is stale — rather than editing rows
        // in place, dismiss so the caller re-renders from the refreshed data, and the user
        // reopens the dialog (via the always-current "N pending" subtitle) to act again.
        lateinit var dialog: AlertDialog

        if (joinRequests.isEmpty()) {
            container.addView(TextView(this).apply { text = "No join requests yet." })
        } else {
            joinRequests.forEach { request ->
                container.addView(buildJoinRequestRow(request) { dialog.dismiss() })
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Join requests")
            .setView(container)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun buildJoinRequestRow(request: JoinRequest, onResponded: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }

        row.addView(TextView(this).apply {
            text = "Guest ${request.guestUserId.take(8)} · ${JoinRequestStatus.label(request.status)}"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        if (request.status == JoinRequestStatus.PENDING) {
            val acceptButton = Button(this).apply { text = "Accept" }
            val declineButton = Button(this).apply { text = "Decline" }

            // EME-305 QA pass: a fast double-tap (Accept then Decline, or a double-tap on
            // one button) fired two concurrent PATCH requests against the same join request —
            // not a crash (the API's status guard rejects the second one), but it produced a
            // confusing error toast on a request that had, in fact, already succeeded.
            // Disabling both buttons the instant either is tapped removes the race entirely.
            fun respond(status: Int) {
                acceptButton.isEnabled = false
                declineButton.isEnabled = false
                respondToJoinRequest(request.id, status, onSuccess = onResponded) {
                    acceptButton.isEnabled = true
                    declineButton.isEnabled = true
                }
            }

            acceptButton.setOnClickListener { respond(JoinRequestStatus.ACCEPTED) }
            declineButton.setOnClickListener { respond(JoinRequestStatus.DECLINED) }
            row.addView(acceptButton)
            row.addView(declineButton)
        }

        return row
    }

    private fun respondToJoinRequest(
        joinRequestId: String,
        status: Int,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        Thread {
            try {
                TeeUpApiClient.updateJoinRequestStatus(joinRequestId, status)
                val requests = TeeUpApiClient.fetchJoinRequestsForTeeTime(teeTimeId)
                val teeTime = TeeUpApiClient.fetchTeeTimes().firstOrNull { it.id == teeTimeId }
                val course = teeTime?.let { tt -> TeeUpApiClient.fetchCourses().firstOrNull { it.id == tt.courseId } }
                runWhenActive {
                    onSuccess()
                    if (teeTime != null) {
                        render(teeTime, course, requests)
                    }
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runWhenActive {
                    onFailure()
                    Toast.makeText(this, e.message ?: "Couldn't update the request", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        findViewById<View>(R.id.qa_retry).visibility = if (::teeTimeId.isInitialized) View.VISIBLE else View.GONE
        contentGroup.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = message
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}

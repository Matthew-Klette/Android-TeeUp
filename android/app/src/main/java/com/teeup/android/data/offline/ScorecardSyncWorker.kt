package com.teeup.android.data.offline

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.R
import com.teeup.android.data.ApiException
import com.teeup.android.data.HoleScoreInput
import com.teeup.android.data.RoundProgressCodec
import com.teeup.android.data.SyncStatus
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.NotificationPreferences
import com.teeup.android.notifications.PushNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The sync rules, kept free of Android types so they're unit tested (OutboxSyncRulesTest). */
object OutboxSyncRules {
    /** What to do with a queued round after one upload attempt. */
    enum class Outcome { SYNCED, RETRY_LATER, REJECTED }

    /** Holes the server already has are skipped, so an upload whose response was lost (the
     *  scores arrived but the phone never heard back) is safe to repeat. */
    fun holesToSend(queued: List<HoleScoreInput>, alreadyOnServer: Set<Int>): List<HoleScoreInput> =
        queued.filter { it.holeNumber !in alreadyOnServer }.sortedBy { it.holeNumber }

    /** No status code means the server was never reached, and 401/408/429/5xx are temporary:
     *  all retry. Any other 4xx is the server refusing these scores, which retrying can't fix. */
    fun classify(error: ApiException): Outcome = when (val code = error.statusCode) {
        null, 401, 408, 429 -> Outcome.RETRY_LATER
        in 500..599 -> Outcome.RETRY_LATER
        else -> if (code in 400..499) Outcome.REJECTED else Outcome.RETRY_LATER
    }
}

/**
 * Uploads every queued scorecard for the signed-in golfer (see [ScorecardOutbox]). Runs only
 * with a network connection; temporary failures return [Result.retry] so WorkManager backs off
 * and tries again, while a scorecard the server rejects is kept with its error for the Offline
 * Sync screen instead of retrying forever.
 */
class ScorecardSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private companion object {
        const val TAG = "ScorecardSyncWorker"
    }

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Rows stay queued for when this golfer signs back in.
            Log.i(TAG, "No signed-in user; nothing to sync")
            return Result.success()
        }
        val dao = TeeUpDatabase.get(applicationContext).pendingScorecards()
        val queue = dao.forOwner(uid).filter { it.lastError == null }
        Log.i(TAG, "Sync started: ${queue.size} queued scorecard(s), attempt ${runAttemptCount + 1}")
        var synced = 0
        var retry = false
        for (row in queue) {
            when (val outcome = upload(row)) {
                OutboxSyncRules.Outcome.SYNCED -> {
                    dao.delete(row.teeTimeId)
                    synced++
                }
                OutboxSyncRules.Outcome.RETRY_LATER -> {
                    dao.recordAttempt(row.teeTimeId, null)
                    retry = true
                }
                OutboxSyncRules.Outcome.REJECTED -> Log.w(TAG, "Server rejected ${row.teeTimeId}; kept for review ($outcome)")
            }
        }
        if (synced > 0) SyncStatus.recordSuccess(applicationContext)
        // The "Sync reminders" switch in Notification Preferences.
        if (synced > 0 && NotificationPreferences.isSyncPendingEnabled(applicationContext)) {
            PushNotifications.show(
                applicationContext,
                PushNotifications.CHANNEL_SYNC,
                applicationContext.getString(R.string.sync_done_title),
                applicationContext.resources.getQuantityString(R.plurals.sync_done_body, synced, synced)
            )
        }
        Log.i(TAG, "Sync finished: $synced uploaded, retry=$retry")
        return if (retry) Result.retry() else Result.success()
    }

    private suspend fun upload(row: PendingScorecardEntity): OutboxSyncRules.Outcome = withContext(Dispatchers.IO) {
        try {
            val queued = RoundProgressCodec.decodeEntries(row.entries)
            val onServer = TeeUpApiClient.fetchScheduleLive()
                .firstOrNull { it.teeTimeId == row.teeTimeId }
                ?.round?.scorecard.orEmpty()
                .map { it.holeNumber }
                .toSet()
            val toSend = OutboxSyncRules.holesToSend(queued, onServer)
            if (toSend.isNotEmpty()) TeeUpApiClient.postScorecard(row.teeTimeId, toSend)
            Log.i(TAG, "Uploaded ${toSend.size} hole(s) for ${row.teeTimeId}")
            OutboxSyncRules.Outcome.SYNCED
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val outcome = OutboxSyncRules.classify(e)
            if (outcome == OutboxSyncRules.Outcome.REJECTED) {
                TeeUpDatabase.get(applicationContext).pendingScorecards().recordAttempt(row.teeTimeId, e.message ?: "Rejected")
            }
            Log.w(TAG, "Upload of ${row.teeTimeId} failed (HTTP ${e.statusCode}): $outcome")
            outcome
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error uploading ${row.teeTimeId}", e)
            OutboxSyncRules.Outcome.RETRY_LATER
        }
    }
}

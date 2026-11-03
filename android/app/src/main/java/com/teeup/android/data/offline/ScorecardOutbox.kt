package com.teeup.android.data.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.data.HoleScoreInput
import com.teeup.android.data.RoundProgressCodec
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Offline mode's write side: a finished round that can't reach the API is queued in Room
 * ([PendingScorecardEntity]) and uploaded by [ScorecardSyncWorker] under a "network connected"
 * WorkManager constraint, so it syncs on its own as soon as the phone is back online, even if
 * the app was closed in between (Komilov, 2025; Android Developers, 2026d).
 */
object ScorecardOutbox {
    private const val TAG = "ScorecardOutbox"
    const val WORK_NAME = "teeup-scorecard-sync"

    private fun dao(context: Context) = TeeUpDatabase.get(context).pendingScorecards()

    /** Queues a round's scores and schedules the sync. Returns false when nobody is signed in. */
    suspend fun enqueue(context: Context, teeTimeId: String, courseName: String, entries: List<HoleScoreInput>, totalHoles: Int): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        dao(context).upsert(
            PendingScorecardEntity(
                teeTimeId = teeTimeId,
                ownerUid = uid,
                courseName = courseName,
                entries = RoundProgressCodec.encodeEntries(entries),
                totalHoles = totalHoles,
                queuedAtMillis = System.currentTimeMillis()
            )
        )
        Log.i(TAG, "Queued ${entries.size} hole(s) for $teeTimeId while offline")
        scheduleSync(context)
        return true
    }

    /** Enqueues the sync job; it waits for a connection, then retries with exponential backoff. */
    /** [restartIfQueued] false keeps a sync that's already queued or mid-upload (app start), so
     *  its backoff isn't reset every time the app opens; true replaces it (a new round queued,
     *  the golfer tapping Check Now), so it runs again as soon as there's a connection. */
    fun scheduleSync(context: Context, restartIfQueued: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<ScorecardSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // One job is enough either way: the worker always processes the whole queue.
        val policy = if (restartIfQueued) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        Log.d(TAG, "Sync scheduled (runs when connected)")
    }

    fun observePending(context: Context): Flow<List<PendingScorecardEntity>> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyFlow()
        return dao(context).observeForOwner(uid)
    }

    suspend fun pending(context: Context): List<PendingScorecardEntity> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
        return dao(context).forOwner(uid)
    }

    /** Blocking; call off the main thread. Tee times whose scorecard is still waiting to upload. */
    fun pendingTeeTimeIds(context: Context): Set<String> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return emptySet()
        return try {
            dao(context).teeTimeIdsForOwner(uid).toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the outbox", e)
            emptySet()
        }
    }

    /** Drops a queued round the server permanently rejected, once the golfer has seen why. */
    suspend fun discard(context: Context, teeTimeId: String) {
        dao(context).delete(teeTimeId)
        Log.i(TAG, "Discarded queued scorecard for $teeTimeId")
    }
}

/*
References:

Android Developers (2026d). Define work requests: work constraints. [online] Available at: <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work> [Accessed 3 Nov. 2026].

Komilov, Y. (2025). Offline-first Android app design: Room, WorkManager, and data sync. [online] Medium. Available at: <https://medium.com/@YodgorbekKomilo/offline-first-android-app-design-room-workmanagerand-data-sync-day-9-of-system-design-76b48349d286> [Accessed 3 Nov. 2026].
*/

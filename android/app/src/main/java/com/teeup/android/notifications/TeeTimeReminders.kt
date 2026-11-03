package com.teeup.android.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.teeup.android.R
import com.teeup.android.data.MockCatalog
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.TeeUpApiClient
import com.teeup.android.data.teeTimeMillis
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One reminder to post: [fireAtMillis] is when, [teeAtMillis] the tee time it's for. */
data class ReminderPlan(val teeTimeId: String, val courseName: String, val teeAtMillis: Long, val fireAtMillis: Long)

/**
 * Tee time reminders: a notification an hour before each round the golfer is booked into,
 * honouring the "Tee time reminders" switch in Notification Preferences. Scheduled on the phone
 * with WorkManager, so a reminder still arrives with no signal at the course and survives the app
 * being closed or the phone restarting (Android Developers, 2026).
 *
 * [ReminderRefreshWorker] re-plans every reminder from the golfer's schedule; it runs on app
 * start, every [REFRESH_HOURS] hours, after the Rounds tab loads and when the switch changes.
 * [TeeTimeReminderWorker] posts one reminder.
 */
object TeeTimeReminders {
    private const val TAG = "TeeTimeReminders"
    const val LEAD_MINUTES = 60L
    private val LEAD_MS = TimeUnit.MINUTES.toMillis(LEAD_MINUTES)
    /** Reminders further ahead than this are planned on a later refresh instead. */
    private val HORIZON_MS = TimeUnit.DAYS.toMillis(30)
    private const val REFRESH_HOURS = 12L

    internal const val WORK_TAG = "teetime-reminder"
    private const val REFRESH_NOW = "teetime-reminder-refresh"
    private const val REFRESH_PERIODIC = "teetime-reminder-refresh-periodic"
    private const val PREFS = "teeup_reminders"
    private const val KEY_ENABLED = "enabled"

    /**
     * Which reminders to schedule at [nowMillis]: one per round still to be played, [LEAD_MINUTES]
     * before it tees off. A round that's already fully scored, has an unreadable time, or whose
     * reminder time has passed gets none (a reminder after the tee time would only be noise).
     */
    fun plan(rounds: List<ScheduledRound>, courseNames: Map<String, String>, fallbackName: String, nowMillis: Long): List<ReminderPlan> =
        rounds.mapNotNull { round ->
            val teeAt = teeTimeMillis(round.dateTime) ?: return@mapNotNull null
            val scored = round.round?.scorecard?.size ?: 0
            val finished = round.holes != null && scored >= round.holes
            val fireAt = teeAt - LEAD_MS
            if (finished || fireAt <= nowMillis || fireAt - nowMillis > HORIZON_MS) return@mapNotNull null
            ReminderPlan(round.teeTimeId, courseNames[round.courseId] ?: fallbackName, teeAt, fireAt)
        }

    fun refresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderRefreshWorker>().build()
        )
    }

    /** Keeps reminders current for rounds booked while the app isn't opened. */
    fun schedulePeriodicRefresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REFRESH_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderRefreshWorker>(REFRESH_HOURS, TimeUnit.HOURS).build()
        )
    }

    /** On sign-out: the next golfer on this phone mustn't get the last one's reminders. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        Log.i(TAG, "All tee time reminders cancelled")
    }

    internal fun schedule(context: Context, plans: List<ReminderPlan>, nowMillis: Long) {
        val work = WorkManager.getInstance(context)
        // WorkManager runs these in order, so the cancel can't wipe the new ones.
        work.cancelAllWorkByTag(WORK_TAG)
        plans.forEach { plan ->
            val request = OneTimeWorkRequestBuilder<TeeTimeReminderWorker>()
                .setInitialDelay(plan.fireAtMillis - nowMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(TeeTimeReminderWorker.KEY_TEE_TIME_ID, plan.teeTimeId)
                        .putString(TeeTimeReminderWorker.KEY_COURSE, plan.courseName)
                        .putLong(TeeTimeReminderWorker.KEY_TEE_AT, plan.teeAtMillis)
                        .build()
                )
                .addTag(WORK_TAG)
                .build()
            work.enqueueUniqueWork("$WORK_TAG-${plan.teeTimeId}", ExistingWorkPolicy.REPLACE, request)
        }
        Log.i(TAG, "${plans.size} tee time reminder(s) scheduled")
    }

    /** Last known value of the server-side switch, for when the profile can't be fetched. */
    internal fun storedEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    internal fun storeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

/** Re-plans every tee time reminder from the golfer's profile switch and schedule. */
class ReminderRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private companion object {
        const val TAG = "ReminderRefreshWorker"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (FirebaseAuth.getInstance().currentUser == null) {
            TeeTimeReminders.cancelAll(context)
            return Result.success()
        }
        val enabled = try {
            TeeUpApiClient.fetchProfile().teeTimeReminders.also { TeeTimeReminders.storeEnabled(context, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Profile unavailable; using the last known reminder setting", e)
            TeeTimeReminders.storedEnabled(context)
        }
        if (!enabled) {
            TeeTimeReminders.cancelAll(context)
            return Result.success()
        }
        return try {
            // Both reads fall back to the offline cache, so this works without signal too.
            val rounds = TeeUpApiClient.fetchSchedule()
            val names = try {
                TeeUpApiClient.fetchCourses()
            } catch (e: Exception) {
                MockCatalog.courses
            }.associate { it.id to it.name }
            val now = System.currentTimeMillis()
            val plans = TeeTimeReminders.plan(rounds, names, context.getString(R.string.rounds_unknown_course), now)
            TeeTimeReminders.schedule(context, plans, now)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Schedule unavailable; reminders left as they were", e)
            Result.retry()
        }
    }
}

/** Posts one tee time reminder. */
class TeeTimeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_TEE_TIME_ID = "teeTimeId"
        const val KEY_COURSE = "course"
        const val KEY_TEE_AT = "teeAt"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val teeAt = inputData.getLong(KEY_TEE_AT, 0L)
        // Signed out since it was scheduled, or the phone was off past the tee time: stay quiet.
        if (FirebaseAuth.getInstance().currentUser == null || teeAt <= System.currentTimeMillis()) return Result.success()
        val local = Calendar.getInstance().apply { timeInMillis = teeAt }
        val time = String.format(Locale.US, "%02d:%02d", local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE))
        PushNotifications.show(
            context,
            PushNotifications.CHANNEL_ALERTS,
            context.getString(R.string.reminder_title),
            context.getString(R.string.reminder_body_format, inputData.getString(KEY_COURSE).orEmpty(), time),
            inputData.getString(KEY_TEE_TIME_ID)
        )
        return Result.success()
    }
}

/*
References:

Android Developers (2026). Schedule tasks with WorkManager. [online] Available at: <https://developer.android.com/develop/background-work/background-tasks/persistent> [Accessed 3 Nov. 2026].
*/

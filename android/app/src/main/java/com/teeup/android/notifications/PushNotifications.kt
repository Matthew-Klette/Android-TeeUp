package com.teeup.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.teeup.android.NotificationsActivity
import com.teeup.android.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * System notifications: the channels, showing one, and an in-app event stream so an open
 * Notifications screen refreshes the moment a push lands (Android Developers, 2026c).
 */
object PushNotifications {
    private const val TAG = "PushNotifications"

    /** Must match the channel id the API puts on its FCM messages (PushSender on the server). */
    const val CHANNEL_ALERTS = "teeup_alerts"
    const val CHANNEL_SYNC = "teeup_sync"

    private val _received = MutableSharedFlow<String?>(extraBufferCapacity = 8)
    /** Emits the related tee time id (or null) for every push received while the app is running. */
    val received: SharedFlow<String?> = _received.asSharedFlow()

    /** Safe to call repeatedly; creating an existing channel is a no-op. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.push_channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.push_channel_alerts_description)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SYNC, context.getString(R.string.push_channel_sync), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.push_channel_sync_description)
            }
        )
    }

    /** Android 13+ needs the runtime permission; older versions only honour the app-level switch. */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, channel: String, title: String, body: String, relatedEntityId: String? = null) {
        _received.tryEmit(relatedEntityId)
        if (!canPost(context)) {
            Log.w(TAG, "Notification not shown: permission denied or notifications disabled")
            return
        }
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, NotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_teeup)
            .setColor(ContextCompat.getColor(context, R.color.teeup_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(if (channel == CHANNEL_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            Log.d(TAG, "Notification shown on $channel")
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked mid-show", e)
        }
    }
}

/*
References:

Android Developers (2026c). Create and manage notification channels. [online] Available at: <https://developer.android.com/develop/ui/views/notifications/channels> [Accessed 3 Nov. 2026].
*/

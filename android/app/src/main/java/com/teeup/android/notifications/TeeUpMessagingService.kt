package com.teeup.android.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.teeup.android.R

/**
 * Receives Firebase Cloud Messaging pushes (Firebase, 2026a). The API sends one whenever it
 * stores a notification for this golfer (join request received/accepted/declined, a group
 * cancelled, a partners-only invite).
 *
 * In the background Android shows the message's notification block itself, on the channel the
 * server names; in the foreground FCM hands it here instead, so it's shown with [PushNotifications]
 * and the open Notifications screen refreshes live.
 */
class TeeUpMessagingService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "TeeUpMessaging"
    }

    /** A new or rotated registration token: store it where the API can find it. */
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        DeviceTokenRegistrar.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "Push received: type=${message.data["type"]}, id=${message.messageId}")
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["message"] ?: return
        val related = message.data["relatedEntityId"]?.takeIf { it.isNotBlank() }
        PushNotifications.show(this, PushNotifications.CHANNEL_ALERTS, title, body, related)
    }
}

/*
References:

Firebase (2026a). Receive messages in an Android app. [online] Available at: <https://firebase.google.com/docs/cloud-messaging/android/receive> [Accessed 3 Nov. 2026].
*/

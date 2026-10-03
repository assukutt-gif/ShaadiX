package com.assukutt.shaadix.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ShaadiXMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // TODO: route data messages into the in-app notification center and Android notification channel.
    }
    override fun onNewToken(token: String) {
        // TODO: save the FCM token to the signed-in user's private Firestore profile.
    }
}

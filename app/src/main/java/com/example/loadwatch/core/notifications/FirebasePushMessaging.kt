package com.example.loadwatch.core.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Firebase keeps topic changes made offline and applies them once the phone is back online
@Singleton
class FirebasePushMessaging @Inject constructor() : PushMessaging {

    private val messaging: FirebaseMessaging get() = FirebaseMessaging.getInstance()

    override suspend fun token(): String = messaging.token.await()

    override suspend fun subscribe(topic: String) {
        messaging.subscribeToTopic(topic).await()
    }

    override suspend fun unsubscribe(topic: String) {
        messaging.unsubscribeFromTopic(topic).await()
    }
}

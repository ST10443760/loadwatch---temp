package com.example.loadwatch.core.notifications

// The parts of Firebase Cloud Messaging the app uses, behind an interface so the topic logic can be unit tested
interface PushMessaging {
    suspend fun token(): String

    suspend fun subscribe(topic: String)

    suspend fun unsubscribe(topic: String)
}

// Each area has its own FCM topic; the API sends stage-change alerts to the same name (StageAlertMessage.TopicFor)
fun areaTopic(areaId: String): String = "area-$areaId"

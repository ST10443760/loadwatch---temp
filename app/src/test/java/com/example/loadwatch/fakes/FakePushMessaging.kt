package com.example.loadwatch.fakes

import com.example.loadwatch.core.notifications.PushMessaging
import com.example.loadwatch.core.notifications.PushPreferences
import java.io.IOException

// Records topic changes instead of calling Firebase; offline makes every call fail as it would without a connection
class FakePushMessaging(var token: String = "fcm-token") : PushMessaging {
    val subscribed = mutableSetOf<String>()
    val calls = mutableListOf<String>()
    var offline = false

    override suspend fun token(): String {
        if (offline) throw IOException("Offline (fake)")
        return token
    }

    override suspend fun subscribe(topic: String) {
        if (offline) throw IOException("Offline (fake)")
        calls += "subscribe $topic"
        subscribed += topic
    }

    override suspend fun unsubscribe(topic: String) {
        if (offline) throw IOException("Offline (fake)")
        calls += "unsubscribe $topic"
        subscribed -= topic
    }
}

class FakePushPreferences(
    override var subscribedTopic: String? = null,
    override var permissionPromptShown: Boolean = false,
) : PushPreferences

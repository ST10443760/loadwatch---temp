package com.example.loadwatch.core.notifications

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Small device-only facts about push notifications that must survive a restart
interface PushPreferences {
    // The area topic this phone is subscribed to, so the old one can be left when the area changes
    var subscribedTopic: String?

    // Whether the friendly explanation before the Android 13 permission dialog has been shown
    var permissionPromptShown: Boolean
}

/**
 * [PushPreferences] stored in a private SharedPreferences file. Device-only state that never syncs to the
 * server.
 */
@Singleton
class SharedPushPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : PushPreferences {

    private val prefs = context.getSharedPreferences("loadwatch_push", Context.MODE_PRIVATE)

    override var subscribedTopic: String?
        get() = prefs.getString(KEY_TOPIC, null)
        set(value) = prefs.edit { putString(KEY_TOPIC, value) }

    override var permissionPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PROMPT_SHOWN, value) }

    private companion object {
        const val KEY_TOPIC = "subscribed_topic"
        const val KEY_PROMPT_SHOWN = "permission_prompt_shown"
    }
}

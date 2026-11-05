package com.example.loadwatch.core.locale

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.loadwatch.core.common.LogTags

// Switches the app's language without touching the phone's. On Android 13+ this is the system's per-app language;
// on older versions AppCompat recreates the activity in the new locale and stores the choice itself. Setting the
// language the app already uses is skipped, so this never causes a needless recreation. Call on the main thread.
fun applyAppLanguage(languageTag: String) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (current == languageTag) return
    Log.i(LogTags.LIFECYCLE, "App language changing from ${current.ifEmpty { "the phone's" }} to $languageTag")
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
}

package com.teeup.android.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * App language switcher. There's no AppCompat/Material in this project (see
 * app/build.gradle.kts — plain ComponentActivity/Activity is deliberate), so
 * AppCompatDelegate's per-app language API isn't available here. This wraps
 * each Activity's base Context with the chosen Locale instead (the classic
 * pre-AppCompat `attachBaseContext` pattern) and persists the choice in
 * SharedPreferences so it survives process restarts.
 */
object LocaleManager {
    data class AppLanguage(val tag: String, val label: String)

    /** Labels are each language's own name for itself, not translated by the current
     *  locale, so a user can always find their language regardless of what's selected now. */
    val SUPPORTED_LANGUAGES = listOf(
        AppLanguage("en", "English"),
        AppLanguage("af", "Afrikaans"),
        AppLanguage("xh", "isiXhosa")
    )

    private const val PREFS_NAME = "teeup_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun getLanguageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE_TAG, SUPPORTED_LANGUAGES.first().tag)
            ?: SUPPORTED_LANGUAGES.first().tag

    fun setLanguageTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, tag).apply()
    }

    /** Called from every Activity's attachBaseContext so its resources resolve in the chosen language. */
    fun wrap(context: Context): Context {
        val locale = Locale(getLanguageTag(context))
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

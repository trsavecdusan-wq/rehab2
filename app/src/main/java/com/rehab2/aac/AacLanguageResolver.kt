package com.rehab2.aac

import android.content.Context
import java.util.Locale

object AacLanguageResolver {
    const val PREFS_FILE = "rehab2_prefs"
    const val PREF_ACTIVE_SPEECH_LANGUAGE = "active_speech_language"
    const val DEFAULT_LANGUAGE_CODE = "sl"

    fun readSelectedLanguageCode(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(PREF_ACTIVE_SPEECH_LANGUAGE, DEFAULT_LANGUAGE_CODE)
        return normalize(raw)
    }

    fun normalize(value: String?): String {
        val code = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return code.ifBlank { DEFAULT_LANGUAGE_CODE }
    }
}

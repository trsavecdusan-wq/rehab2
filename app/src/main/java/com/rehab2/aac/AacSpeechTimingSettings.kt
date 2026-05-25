package com.rehab2.aac

import android.content.Context

data class AacSpeechTimingSettings(
    val speakSingleIconEnabled: Boolean = true,
    val autoSpeakSentenceEnabled: Boolean = false,
    val autoSpeakSentenceDelayMs: Long = 3000L
) {
    companion object {
        const val PREFS_FILE = "rehab2_prefs"
        const val PREF_SPEAK_SINGLE_ICON_ENABLED = "aac_speak_single_icon_enabled"
        const val PREF_AUTO_SPEAK_SENTENCE_ENABLED = "aac_auto_speak_sentence_enabled"
        const val PREF_AUTO_SPEAK_SENTENCE_DELAY_MS = "aac_auto_speak_sentence_delay_ms"
        const val DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS = 3000L

        private val ALLOWED_DELAYS_MS = setOf(2000L, 3000L, 5000L, 8000L)

        fun read(context: Context): AacSpeechTimingSettings {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return AacSpeechTimingSettings(
                speakSingleIconEnabled = prefs.getBoolean(PREF_SPEAK_SINGLE_ICON_ENABLED, true),
                autoSpeakSentenceEnabled = prefs.getBoolean(PREF_AUTO_SPEAK_SENTENCE_ENABLED, false),
                autoSpeakSentenceDelayMs = normalizeDelay(
                    prefs.getLong(
                        PREF_AUTO_SPEAK_SENTENCE_DELAY_MS,
                        DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS
                    )
                )
            )
        }

        private fun normalizeDelay(value: Long): Long {
            return if (value in ALLOWED_DELAYS_MS) value else DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS
        }
    }
}

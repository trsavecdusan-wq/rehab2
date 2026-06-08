package com.rehab2.aac

import android.content.Context

data class AacSpeechTimingSettings(
    val speakSingleIconEnabled: Boolean = true,
    val delayedSingleIconSpeakEnabled: Boolean = true,
    val singleIconSpeakDelayMs: Long = 700L,
    val mainIconSpeakDelayMs: Long = 250L,
    val subIconSpeakDelayMs: Long = 700L,
    val fastCompositionSkipLastIconEnabled: Boolean = true,
    val autoSpeakSentenceEnabled: Boolean = true,
    val autoSpeakSentenceDelayMs: Long = 3000L,
    val returnToRootAfterSentenceEnabled: Boolean = true,
    val clearSentenceAfterSentenceEnabled: Boolean = true,
    val partialSentenceAutoReturnEnabled: Boolean = true,
    val partialSentenceAutoReturnMs: Long = 10000L
) {
    companion object {
        const val PREFS_FILE = "rehab2_prefs"
        const val PREF_SPEAK_SINGLE_ICON_ENABLED = "aac_speak_single_icon_enabled"
        const val PREF_DELAYED_SINGLE_ICON_SPEAK_ENABLED = "aac_delayed_single_icon_speak_enabled"
        const val PREF_SINGLE_ICON_SPEAK_DELAY_MS = "aac_single_icon_speak_delay_ms"
        const val PREF_MAIN_ICON_SPEAK_DELAY_MS = "aac_main_icon_speak_delay_ms"
        const val PREF_SUB_ICON_SPEAK_DELAY_MS = "aac_sub_icon_speak_delay_ms"
        const val PREF_FAST_COMPOSITION_SKIP_LAST_ICON_ENABLED = "aac_fast_composition_skip_last_icon_enabled"
        const val PREF_AUTO_SPEAK_SENTENCE_ENABLED = "aac_auto_speak_sentence_enabled"
        const val PREF_AUTO_SPEAK_SENTENCE_DELAY_MS = "aac_auto_speak_sentence_delay_ms"
        const val PREF_RETURN_TO_ROOT_AFTER_SENTENCE_ENABLED = "aac_return_to_root_after_sentence_enabled"
        const val PREF_CLEAR_SENTENCE_AFTER_SENTENCE_ENABLED = "aac_clear_sentence_after_sentence_enabled"
        const val PREF_PARTIAL_SENTENCE_AUTO_RETURN_ENABLED = "aac_partial_sentence_auto_return_enabled"
        const val PREF_PARTIAL_SENTENCE_AUTO_RETURN_MS = "aac_partial_sentence_auto_return_ms"
        const val DEFAULT_SINGLE_ICON_SPEAK_DELAY_MS = 700L
        const val DEFAULT_MAIN_ICON_SPEAK_DELAY_MS = 250L
        const val DEFAULT_SUB_ICON_SPEAK_DELAY_MS = 700L
        const val DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS = 3000L
        const val DEFAULT_PARTIAL_SENTENCE_AUTO_RETURN_MS = 10000L

        private val ALLOWED_MAIN_ICON_DELAYS_MS = setOf(0L, 100L, 200L, 300L, 500L, 700L, 1000L)
        private val ALLOWED_SUB_ICON_DELAYS_MS = setOf(0L, 100L, 200L, 300L, 500L, 700L, 1000L, 1500L, 2000L)
        private val ALLOWED_DELAYS_MS = setOf(1000L, 1500L, 2000L, 3000L, 4000L, 5000L)
        private val ALLOWED_PARTIAL_SENTENCE_AUTO_RETURN_MS = setOf(5000L, 10000L, 15000L, 20000L, 30000L)

        fun read(context: Context): AacSpeechTimingSettings {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val subIconDelay = normalizeSubIconDelay(
                if (prefs.contains(PREF_SUB_ICON_SPEAK_DELAY_MS)) {
                    prefs.getLong(PREF_SUB_ICON_SPEAK_DELAY_MS, DEFAULT_SUB_ICON_SPEAK_DELAY_MS)
                } else {
                    prefs.getLong(PREF_SINGLE_ICON_SPEAK_DELAY_MS, DEFAULT_SUB_ICON_SPEAK_DELAY_MS)
                }
            )
            return AacSpeechTimingSettings(
                speakSingleIconEnabled = prefs.getBoolean(PREF_SPEAK_SINGLE_ICON_ENABLED, true),
                delayedSingleIconSpeakEnabled = prefs.getBoolean(PREF_DELAYED_SINGLE_ICON_SPEAK_ENABLED, true),
                singleIconSpeakDelayMs = subIconDelay,
                mainIconSpeakDelayMs = normalizeMainIconDelay(
                    prefs.getLong(PREF_MAIN_ICON_SPEAK_DELAY_MS, DEFAULT_MAIN_ICON_SPEAK_DELAY_MS)
                ),
                subIconSpeakDelayMs = subIconDelay,
                fastCompositionSkipLastIconEnabled = prefs.getBoolean(
                    PREF_FAST_COMPOSITION_SKIP_LAST_ICON_ENABLED,
                    true
                ),
                autoSpeakSentenceEnabled = prefs.getBoolean(PREF_AUTO_SPEAK_SENTENCE_ENABLED, true),
                autoSpeakSentenceDelayMs = normalizeDelay(
                    prefs.getLong(
                        PREF_AUTO_SPEAK_SENTENCE_DELAY_MS,
                        DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS
                    )
                ),
                returnToRootAfterSentenceEnabled = prefs.getBoolean(
                    PREF_RETURN_TO_ROOT_AFTER_SENTENCE_ENABLED,
                    true
                ),
                clearSentenceAfterSentenceEnabled = prefs.getBoolean(
                    PREF_CLEAR_SENTENCE_AFTER_SENTENCE_ENABLED,
                    true
                ),
                partialSentenceAutoReturnEnabled = prefs.getBoolean(
                    PREF_PARTIAL_SENTENCE_AUTO_RETURN_ENABLED,
                    true
                ),
                partialSentenceAutoReturnMs = normalizePartialSentenceAutoReturnMs(
                    prefs.getLong(
                        PREF_PARTIAL_SENTENCE_AUTO_RETURN_MS,
                        DEFAULT_PARTIAL_SENTENCE_AUTO_RETURN_MS
                    )
                )
            )
        }

        private fun normalizeMainIconDelay(value: Long): Long {
            return if (value == DEFAULT_MAIN_ICON_SPEAK_DELAY_MS || value in ALLOWED_MAIN_ICON_DELAYS_MS) {
                value
            } else {
                DEFAULT_MAIN_ICON_SPEAK_DELAY_MS
            }
        }

        private fun normalizeSubIconDelay(value: Long): Long {
            return if (value in ALLOWED_SUB_ICON_DELAYS_MS) value else DEFAULT_SUB_ICON_SPEAK_DELAY_MS
        }

        private fun normalizeDelay(value: Long): Long {
            return if (value in ALLOWED_DELAYS_MS) value else DEFAULT_AUTO_SPEAK_SENTENCE_DELAY_MS
        }

        private fun normalizePartialSentenceAutoReturnMs(value: Long): Long {
            return if (value in ALLOWED_PARTIAL_SENTENCE_AUTO_RETURN_MS) {
                value
            } else {
                DEFAULT_PARTIAL_SENTENCE_AUTO_RETURN_MS
            }
        }
    }
}

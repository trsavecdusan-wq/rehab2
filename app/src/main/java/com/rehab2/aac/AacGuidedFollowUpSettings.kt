package com.rehab2.aac

import android.content.Context

data class AacGuidedFollowUpSettings(
    val guidedFollowUpEnabled: Boolean = true,
    val vendingNumberDisplayEnabled: Boolean = true,
    val speakDigitsSeparatelyEnabled: Boolean = true
) {
    companion object {
        const val PREFS_FILE = "rehab2_prefs"
        const val PREF_GUIDED_FOLLOW_UP_ENABLED = "aac_guided_follow_up_enabled"
        const val PREF_VENDING_NUMBER_DISPLAY_ENABLED = "aac_vending_number_display_enabled"
        const val PREF_SPEAK_DIGITS_SEPARATELY_ENABLED = "aac_speak_digits_separately_enabled"

        fun read(context: Context): AacGuidedFollowUpSettings {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return AacGuidedFollowUpSettings(
                guidedFollowUpEnabled = prefs.getBoolean(PREF_GUIDED_FOLLOW_UP_ENABLED, true),
                vendingNumberDisplayEnabled = prefs.getBoolean(PREF_VENDING_NUMBER_DISPLAY_ENABLED, true),
                speakDigitsSeparatelyEnabled = prefs.getBoolean(PREF_SPEAK_DIGITS_SEPARATELY_ENABLED, true)
            )
        }
    }
}

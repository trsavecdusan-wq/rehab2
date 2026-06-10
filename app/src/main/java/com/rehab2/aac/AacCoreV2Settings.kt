package com.rehab2.aac

import android.content.Context

enum class AacTextDisplayMode {
    ICON_AND_TEXT,
    ICON_ONLY,
    TEXT_ONLY;

    companion object {
        fun fromPreference(value: String?): AacTextDisplayMode {
            return values().firstOrNull { it.name == value } ?: ICON_AND_TEXT
        }
    }
}

data class AacCoreV2Settings(
    val guidedLearningModeEnabled: Boolean = true,
    val perMainIconGuidedLearningEnabled: Map<String, Boolean> = emptyMap(),
    val learningStatisticsEnabled: Boolean = false,
    val textDisplayMode: AacTextDisplayMode = AacTextDisplayMode.ICON_AND_TEXT
) {
    fun isGuidedLearningEnabledFor(mainIconId: String): Boolean {
        return guidedLearningModeEnabled &&
            (perMainIconGuidedLearningEnabled[mainIconId.trim()] ?: true)
    }

    companion object {
        const val PREFS_FILE = "rehab2_prefs"
        const val PREF_GUIDED_LEARNING_MODE_ENABLED = "aac_core_v2_guided_learning_mode_enabled"
        const val PREF_MAIN_ICON_GUIDED_LEARNING_PREFIX = "aac_core_v2_main_icon_guided_learning_"
        const val PREF_LEARNING_STATISTICS_ENABLED = "aac_core_v2_learning_statistics_enabled"
        const val PREF_TEXT_DISPLAY_MODE = "aac_core_v2_text_display_mode"

        val MAIN_ICON_IDS = listOf(
            "wc",
            "pain",
            "thirsty",
            "hungry",
            "tired",
            "i_want",
            "need",
            "people",
            "miss_someone",
            "call",
            "feeling",
            "place_group",
            "care",
            "health",
            "dont_want",
            "please",
            "wait",
            "repeat",
            "pogovor",
            "activity_group"
        )

        fun read(context: Context): AacCoreV2Settings {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return AacCoreV2Settings(
                guidedLearningModeEnabled = prefs.getBoolean(PREF_GUIDED_LEARNING_MODE_ENABLED, true),
                perMainIconGuidedLearningEnabled = MAIN_ICON_IDS.associateWith { iconId ->
                    prefs.getBoolean(PREF_MAIN_ICON_GUIDED_LEARNING_PREFIX + iconId, true)
                },
                learningStatisticsEnabled = prefs.getBoolean(PREF_LEARNING_STATISTICS_ENABLED, false),
                textDisplayMode = AacTextDisplayMode.fromPreference(prefs.getString(PREF_TEXT_DISPLAY_MODE, null))
            )
        }
    }
}

package com.rehab2.aac

enum class AacLabelMode {
    HIDDEN,
    SMALL,
    NORMAL,
    LARGE;

    companion object {
        const val PREFS_FILE = "rehab2_prefs"
        const val PREF_AAC_LABEL_MODE = "aac_label_mode"
        val DEFAULT = SMALL

        fun fromPreference(value: String?): AacLabelMode {
            return values().firstOrNull { it.name == value } ?: DEFAULT
        }
    }
}

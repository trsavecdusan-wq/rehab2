package com.rehab2.aac

import android.content.Context

enum class AacCommunicationContext {
    NORMAL_COMMUNICATION,
    VIDEO_CALL_COMMUNICATION,
    REAL_WORLD_ASSISTANT;

    companion object {
        fun fromPreference(value: String?): AacCommunicationContext {
            return entries.firstOrNull { it.name == value } ?: NORMAL_COMMUNICATION
        }
    }
}

object AacCommunicationContextPrefs {
    const val PREFS_FILE = "rehab2_prefs"
    const val PREF_AAC_COMMUNICATION_CONTEXT = "aac_communication_context"
    const val PREF_AAC_REAL_WORLD_HELPERS_ENABLED = "aac_real_world_helpers_enabled"

    fun readContext(context: Context): AacCommunicationContext {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        return AacCommunicationContext.fromPreference(
            prefs.getString(PREF_AAC_COMMUNICATION_CONTEXT, AacCommunicationContext.NORMAL_COMMUNICATION.name)
        )
    }

    fun areRealWorldHelpersEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_AAC_REAL_WORLD_HELPERS_ENABLED, true)
    }
}

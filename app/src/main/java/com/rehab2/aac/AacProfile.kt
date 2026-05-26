package com.rehab2.aac

import android.content.Context

data class AacProfile(
    val id: String,
    val displayName: String,
    val icon: String?,
    val context: AacCommunicationContext,
    val itemIds: List<String>,
    val enabled: Boolean = true
)

object AacProfileStore {
    const val PREFS_FILE = "rehab2_prefs"
    const val PREF_ACTIVE_PROFILE_ID = "aac_active_profile_id"
    const val DEFAULT_PROFILE_ID = "dom"

    private val fallbackProfiles = listOf(
        AacProfile(
            id = "dom",
            displayName = "DOM",
            icon = null,
            context = AacCommunicationContext.NORMAL_COMMUNICATION,
            itemIds = listOf("yes", "no", "help", "pain", "thirsty")
        ),
        AacProfile(
            id = "video_call",
            displayName = "VIDEO CALL",
            icon = null,
            context = AacCommunicationContext.VIDEO_CALL_COMMUNICATION,
            itemIds = listOf("yes", "no", "help", "pain")
        ),
        AacProfile(
            id = "real_world",
            displayName = "REAL WORLD",
            icon = null,
            context = AacCommunicationContext.REAL_WORLD_ASSISTANT,
            itemIds = listOf("thirsty", "drink", "help", "wc", "stop")
        ),
        AacProfile(
            id = "trgovina",
            displayName = "TRGOVINA",
            icon = null,
            context = AacCommunicationContext.REAL_WORLD_ASSISTANT,
            itemIds = listOf("yes", "no", "help", "stop", "wc")
        ),
        AacProfile(
            id = "avtomat",
            displayName = "AVTOMAT",
            icon = null,
            context = AacCommunicationContext.REAL_WORLD_ASSISTANT,
            itemIds = listOf("thirsty", "drink", "help", "stop", "wc")
        )
    )

    fun loadProfilesFromStorage(context: Context): List<AacProfile> {
        return fallbackProfiles.filter { it.enabled }
    }

    fun getActiveAacProfile(context: Context): AacProfile {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val profileId = prefs.getString(PREF_ACTIVE_PROFILE_ID, DEFAULT_PROFILE_ID)
        return loadProfilesFromStorage(context).firstOrNull { it.id == profileId } ?: fallbackDomProfile()
    }

    fun getActiveAacContext(context: Context): AacCommunicationContext {
        return getActiveAacProfile(context).context
    }

    fun applyProfileDefaultsIfNeeded(context: Context) {
        val activeProfile = getActiveAacProfile(context)
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val expectedHelpersEnabled = activeProfile.context != AacCommunicationContext.VIDEO_CALL_COMMUNICATION
        val currentContext = AacCommunicationContextPrefs.readContext(context)
        val currentHelpersEnabled = AacCommunicationContextPrefs.areRealWorldHelpersEnabled(context)
        if (currentContext != activeProfile.context || currentHelpersEnabled != expectedHelpersEnabled) {
            prefs.edit()
                .putString(AacCommunicationContextPrefs.PREF_AAC_COMMUNICATION_CONTEXT, activeProfile.context.name)
                .putBoolean(AacCommunicationContextPrefs.PREF_AAC_REAL_WORLD_HELPERS_ENABLED, expectedHelpersEnabled)
                .apply()
        }
    }

    fun loadItemsForProfile(context: Context, itemsById: Map<String, AacItem>): List<AacItem> {
        return getActiveAacProfile(context).itemIds.mapNotNull { itemsById[it] }
    }

    fun fallbackDomProfile(): AacProfile {
        return fallbackProfiles.first { it.id == DEFAULT_PROFILE_ID }
    }
}

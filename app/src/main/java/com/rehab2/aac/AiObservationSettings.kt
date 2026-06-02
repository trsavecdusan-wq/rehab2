package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

object AiObservationSettings {
    private const val SETTINGS_FILE = "NovaRehab/data/ai_observation_settings.json"

    data class Settings(
        val allowMicrophoneAnalysis: Boolean = false,
        val allowCameraAnalysis: Boolean = false,
        val allowMimicLearning: Boolean = false,
        val allowDailyLearning: Boolean = false,
        val allowCompanionSuggestions: Boolean = false,
        val requireYesNoConfirmation: Boolean = true,
        val lastUpdatedAt: Long = 0L
    )

    fun load(context: Context): Settings {
        val file = settingsFile(context) ?: return Settings()
        if (!file.exists()) {
            val defaults = Settings()
            save(context, defaults)
            return defaults
        }
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            sanitize(
                Settings(
                    allowMicrophoneAnalysis = json.optBoolean("allowMicrophoneAnalysis", false),
                    allowCameraAnalysis = json.optBoolean("allowCameraAnalysis", false),
                    allowMimicLearning = json.optBoolean("allowMimicLearning", false),
                    allowDailyLearning = json.optBoolean("allowDailyLearning", false),
                    allowCompanionSuggestions = json.optBoolean("allowCompanionSuggestions", false),
                    requireYesNoConfirmation = json.optBoolean("requireYesNoConfirmation", true),
                    lastUpdatedAt = json.optLong("lastUpdatedAt", 0L).coerceAtLeast(0L)
                )
            )
        } catch (_: Exception) {
            Settings()
        }
    }

    fun save(context: Context, settings: Settings): Boolean {
        val file = settingsFile(context) ?: return false
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return false
                }
            }
            val safeSettings = sanitize(settings)
            val json = JSONObject()
                .put("allowMicrophoneAnalysis", false)
                .put("allowCameraAnalysis", false)
                .put("allowMimicLearning", false)
                .put("allowDailyLearning", false)
                .put("allowCompanionSuggestions", false)
                .put("requireYesNoConfirmation", safeSettings.requireYesNoConfirmation)
                .put("lastUpdatedAt", safeSettings.lastUpdatedAt.coerceAtLeast(0L))
            file.writeText(json.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun settingsFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, SETTINGS_FILE)
    }

    private fun sanitize(settings: Settings): Settings {
        return settings.copy(
            allowMicrophoneAnalysis = false,
            allowCameraAnalysis = false,
            allowMimicLearning = false,
            allowDailyLearning = false,
            allowCompanionSuggestions = false,
            requireYesNoConfirmation = true,
            lastUpdatedAt = settings.lastUpdatedAt.coerceAtLeast(0L)
        )
    }
}

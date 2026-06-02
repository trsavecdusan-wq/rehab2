package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

object AacAssistSettings {
    const val MODE_OFF = "OFF"
    const val MODE_TEST = "TEST"
    private const val SETTINGS_FILE = "NovaRehab/data/aac_assist_settings.json"
    private const val DEFAULT_MAX_SUGGESTIONS = 3

    data class Settings(
        val enabled: Boolean = false,
        val mode: String = MODE_OFF,
        val showSuggestions: Boolean = false,
        val maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
        val allowMicrophone: Boolean = false,
        val allowNetwork: Boolean = false,
        val lastUpdatedAt: Long = 0L
    )

    fun isEnabled(context: Context): Boolean {
        val settings = load(context)
        return settings.enabled && settings.mode != MODE_OFF
    }

    fun isMicrophoneAllowed(context: Context): Boolean {
        return isEnabled(context) && load(context).allowMicrophone
    }

    fun isNetworkAllowed(context: Context): Boolean {
        return isEnabled(context) && load(context).allowNetwork
    }

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
                    enabled = json.optBoolean("enabled", false),
                    mode = json.optString("mode", MODE_OFF),
                    showSuggestions = json.optBoolean("showSuggestions", false),
                    maxSuggestions = json.optInt("maxSuggestions", DEFAULT_MAX_SUGGESTIONS),
                    allowMicrophone = json.optBoolean("allowMicrophone", false),
                    allowNetwork = json.optBoolean("allowNetwork", false),
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
                .put("enabled", safeSettings.enabled)
                .put("mode", safeSettings.mode)
                .put("showSuggestions", safeSettings.showSuggestions)
                .put("maxSuggestions", safeSettings.maxSuggestions)
                .put("allowMicrophone", false)
                .put("allowNetwork", false)
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
        val normalizedMode = settings.mode.trim().uppercase().takeIf { mode ->
            mode == MODE_TEST && settings.enabled
        } ?: MODE_OFF
        val enabled = settings.enabled && normalizedMode != MODE_OFF
        return settings.copy(
            enabled = enabled,
            mode = normalizedMode,
            showSuggestions = enabled && settings.showSuggestions,
            maxSuggestions = settings.maxSuggestions.coerceIn(1, DEFAULT_MAX_SUGGESTIONS),
            allowMicrophone = false,
            allowNetwork = false,
            lastUpdatedAt = settings.lastUpdatedAt.coerceAtLeast(0L)
        )
    }
}

package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacKeywordListenerSettings {
    const val MODE_OFF = "OFF"
    private const val SETTINGS_FILE = "NovaRehab/data/aac_keyword_listener_settings.json"
    private const val DEFAULT_MAX_SUGGESTIONS = 3
    private val DEFAULT_KEYWORDS = listOf(
        "voda",
        "\u010daj",
        "kava",
        "wc",
        "boli",
        "\u017eana",
        "du\u0161an",
        "domov"
    )

    data class Settings(
        val enabled: Boolean = false,
        val mode: String = MODE_OFF,
        val allowMicrophone: Boolean = false,
        val allowBackgroundListening: Boolean = false,
        val allowNetwork: Boolean = false,
        val wakeWords: List<String> = emptyList(),
        val keywords: List<String> = DEFAULT_KEYWORDS,
        val maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
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
                    enabled = json.optBoolean("enabled", false),
                    mode = json.optString("mode", MODE_OFF),
                    allowMicrophone = json.optBoolean("allowMicrophone", false),
                    allowBackgroundListening = json.optBoolean("allowBackgroundListening", false),
                    allowNetwork = json.optBoolean("allowNetwork", false),
                    wakeWords = json.optJSONArray("wakeWords").toStringList(),
                    keywords = json.optJSONArray("keywords").toStringList().ifEmpty { DEFAULT_KEYWORDS },
                    maxSuggestions = json.optInt("maxSuggestions", DEFAULT_MAX_SUGGESTIONS),
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
                .put("allowMicrophone", false)
                .put("allowBackgroundListening", false)
                .put("allowNetwork", false)
                .put("wakeWords", JSONArray(safeSettings.wakeWords))
                .put("keywords", JSONArray(safeSettings.keywords))
                .put("maxSuggestions", safeSettings.maxSuggestions)
                .put("lastUpdatedAt", safeSettings.lastUpdatedAt.coerceAtLeast(0L))
            file.writeText(json.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isEnabled(context: Context): Boolean {
        val settings = load(context)
        return settings.enabled && settings.mode != MODE_OFF
    }

    fun isMicrophoneAllowed(context: Context): Boolean {
        return isEnabled(context) && load(context).allowMicrophone
    }

    fun isBackgroundListeningAllowed(context: Context): Boolean {
        return isEnabled(context) && load(context).allowBackgroundListening
    }

    fun isNetworkAllowed(context: Context): Boolean {
        return isEnabled(context) && load(context).allowNetwork
    }

    fun keywords(context: Context): List<String> {
        return load(context).keywords
    }

    fun settingsFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, SETTINGS_FILE)
    }

    private fun sanitize(settings: Settings): Settings {
        val normalizedMode = settings.mode.trim().uppercase().takeIf { mode ->
            mode != MODE_OFF && settings.enabled
        } ?: MODE_OFF
        val enabled = settings.enabled && normalizedMode != MODE_OFF
        val safeKeywords = settings.keywords
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()
            .ifEmpty { DEFAULT_KEYWORDS }
        return settings.copy(
            enabled = enabled,
            mode = normalizedMode,
            allowMicrophone = false,
            allowBackgroundListening = false,
            allowNetwork = false,
            wakeWords = emptyList(),
            keywords = safeKeywords,
            maxSuggestions = settings.maxSuggestions.coerceIn(1, DEFAULT_MAX_SUGGESTIONS),
            lastUpdatedAt = settings.lastUpdatedAt.coerceAtLeast(0L)
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }
        return (0 until length())
            .mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
    }
}

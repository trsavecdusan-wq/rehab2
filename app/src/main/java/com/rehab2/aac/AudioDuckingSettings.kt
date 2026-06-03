package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AudioDuckingSettings(
    val enabled: Boolean = true,
    val duckingPercent: Int = DEFAULT_DUCKING_PERCENT,
    val lastUpdatedAt: Long = 0L
) {
    companion object {
        const val DEFAULT_DUCKING_PERCENT = 70
        private const val SETTINGS_FILE = "NovaRehab/data/audio_ducking_settings.json"
        private val ALLOWED_DUCKING_PERCENTS = setOf(0, 25, 50, 70, 75, 90)

        fun settingsFile(context: Context): File? {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return null
            return File(externalFilesDir, SETTINGS_FILE)
        }

        fun load(context: Context): AudioDuckingSettings {
            val file = settingsFile(context) ?: return AudioDuckingSettings()
            if (!file.exists() || !file.isFile) {
                save(context, AudioDuckingSettings())
                return AudioDuckingSettings()
            }

            return try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                AudioDuckingSettings(
                    enabled = json.optBoolean("enabled", true),
                    duckingPercent = normalizeDuckingPercent(json.optInt("duckingPercent", DEFAULT_DUCKING_PERCENT)),
                    lastUpdatedAt = json.optLong("lastUpdatedAt", 0L)
                )
            } catch (_: Exception) {
                AudioDuckingSettings()
            }
        }

        fun save(context: Context, settings: AudioDuckingSettings): Boolean {
            val file = settingsFile(context) ?: return false
            return try {
                file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                val normalized = settings.copy(duckingPercent = normalizeDuckingPercent(settings.duckingPercent))
                val json = JSONObject()
                    .put("enabled", normalized.enabled)
                    .put("duckingPercent", normalized.duckingPercent)
                    .put("lastUpdatedAt", normalized.lastUpdatedAt)
                file.writeText(json.toString(2), Charsets.UTF_8)
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun normalizeDuckingPercent(value: Int): Int {
            return if (value in ALLOWED_DUCKING_PERCENTS) value else DEFAULT_DUCKING_PERCENT
        }
    }
}

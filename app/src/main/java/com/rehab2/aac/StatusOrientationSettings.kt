package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

data class StatusOrientationSettings(
    val enabled: Boolean = true,
    val speakGreeting: Boolean = true,
    val speakDate: Boolean = true,
    val speakTime: Boolean = true,
    val speakWeather: Boolean = false,
    val weatherSourceUrl: String = "",
    val selectedWeatherSourceName: String = "",
    val lastUpdatedAt: Long = 0L
) {
    companion object {
        private const val SETTINGS_FILE = "NovaRehab/data/status_orientation_settings.json"
        const val DEFAULT_WEATHER_SOURCE_NAME = "Open-Meteo test"
        const val DEFAULT_WEATHER_SOURCE_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=46.56&longitude=16.16&current=temperature_2m,weather_code"

        fun settingsFile(context: Context): File? {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return null
            return File(externalFilesDir, SETTINGS_FILE)
        }

        fun load(context: Context): StatusOrientationSettings {
            val file = settingsFile(context) ?: return withDefaultWeatherSource(StatusOrientationSettings())
            if (!file.exists() || !file.isFile) {
                val defaults = withDefaultWeatherSource(StatusOrientationSettings())
                save(context, defaults)
                return defaults
            }

            return try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val loaded = StatusOrientationSettings(
                    enabled = json.optBoolean("enabled", true),
                    speakGreeting = json.optBoolean("speakGreeting", true),
                    speakDate = json.optBoolean("speakDate", true),
                    speakTime = json.optBoolean("speakTime", true),
                    speakWeather = json.optBoolean("speakWeather", json.optBoolean("weatherEnabled", false)),
                    weatherSourceUrl = json.optString("weatherSourceUrl", ""),
                    selectedWeatherSourceName = json.optString("selectedWeatherSourceName", ""),
                    lastUpdatedAt = json.optLong("lastUpdatedAt", 0L)
                )
                val normalized = withDefaultWeatherSource(loaded)
                if (normalized != loaded) {
                    save(context, normalized)
                }
                normalized
            } catch (_: Exception) {
                val defaults = withDefaultWeatherSource(StatusOrientationSettings())
                save(context, defaults)
                defaults
            }
        }

        fun save(context: Context, settings: StatusOrientationSettings): Boolean {
            val file = settingsFile(context) ?: return false
            return try {
                file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                val safeSettings = withDefaultWeatherSource(settings)
                val json = JSONObject()
                    .put("enabled", safeSettings.enabled)
                    .put("speakGreeting", safeSettings.speakGreeting)
                    .put("speakDate", safeSettings.speakDate)
                    .put("speakTime", safeSettings.speakTime)
                    .put("speakWeather", safeSettings.speakWeather)
                    .put("weatherSourceUrl", safeSettings.weatherSourceUrl)
                    .put("selectedWeatherSourceName", safeSettings.selectedWeatherSourceName)
                    .put("lastUpdatedAt", safeSettings.lastUpdatedAt)
                file.writeText(json.toString(2), Charsets.UTF_8)
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun withDefaultWeatherSource(settings: StatusOrientationSettings): StatusOrientationSettings {
            val trimmedUrl = settings.weatherSourceUrl.trim()
            val weatherSourceUrl = trimmedUrl.ifBlank { DEFAULT_WEATHER_SOURCE_URL }
            val selectedWeatherSourceName = settings.selectedWeatherSourceName.trim().ifBlank {
                if (weatherSourceUrl == DEFAULT_WEATHER_SOURCE_URL) {
                    DEFAULT_WEATHER_SOURCE_NAME
                } else {
                    "Ročni vir"
                }
            }
            return settings.copy(
                speakWeather = settings.speakWeather && weatherSourceUrl.isNotBlank(),
                weatherSourceUrl = weatherSourceUrl,
                selectedWeatherSourceName = selectedWeatherSourceName
            )
        }
    }
}

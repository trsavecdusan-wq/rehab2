package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AacSpeechLoudnessSettings(
    val gain: Double = DEFAULT_GAIN,
    val lastUpdatedAt: Long = 0L
) {
    fun label(): String {
        return when (normalizeGain(gain)) {
            1.0 -> "Normalno"
            1.1 -> "+10 %"
            1.2 -> "+20 %"
            1.3 -> "+30 %"
            else -> "+20 %"
        }
    }

    companion object {
        const val DEFAULT_GAIN = 1.2
        private const val SETTINGS_FILE = "NovaRehab/data/aac_speech_loudness_settings.json"
        val ALLOWED_GAINS = doubleArrayOf(1.0, 1.1, 1.2, 1.3)

        fun settingsFile(context: Context): File? {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return null
            return File(externalFilesDir, SETTINGS_FILE)
        }

        fun load(context: Context): AacSpeechLoudnessSettings {
            val file = settingsFile(context) ?: return AacSpeechLoudnessSettings()
            if (!file.exists() || !file.isFile) {
                save(context, AacSpeechLoudnessSettings())
                return AacSpeechLoudnessSettings()
            }

            return try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                AacSpeechLoudnessSettings(
                    gain = normalizeGain(json.optDouble("gain", DEFAULT_GAIN)),
                    lastUpdatedAt = json.optLong("lastUpdatedAt", 0L)
                )
            } catch (_: Exception) {
                AacSpeechLoudnessSettings()
            }
        }

        fun save(context: Context, settings: AacSpeechLoudnessSettings): Boolean {
            val file = settingsFile(context) ?: return false
            return try {
                file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                val normalized = settings.copy(gain = normalizeGain(settings.gain))
                val json = JSONObject()
                    .put("gain", normalized.gain)
                    .put("lastUpdatedAt", normalized.lastUpdatedAt)
                file.writeText(json.toString(2), Charsets.UTF_8)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun normalizeGain(value: Double): Double {
            return ALLOWED_GAINS.firstOrNull { allowed ->
                kotlin.math.abs(allowed - value) < 0.001
            } ?: DEFAULT_GAIN
        }
    }
}

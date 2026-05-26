package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AacSpeechApiConfig(
    val enabled: Boolean = false,
    val provider: String = PROVIDER_OPENAI,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val voiceId: String = DEFAULT_VOICE_ID,
    val responseFormat: String = DEFAULT_RESPONSE_FORMAT,
    val speed: Double = DEFAULT_SPEED
) {
    fun isOpenAiEnabled(): Boolean {
        return enabled &&
            provider.equals(PROVIDER_OPENAI, ignoreCase = true) &&
            baseUrl.isNotBlank() &&
            apiKey.isNotBlank()
    }

    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    fun normalizedVoiceId(): String = voiceId.trim().ifBlank { DEFAULT_VOICE_ID }

    fun normalizedModel(): String = model.trim().ifBlank { DEFAULT_MODEL }

    fun normalizedResponseFormat(): String = responseFormat.trim().lowercase().ifBlank {
        DEFAULT_RESPONSE_FORMAT
    }

    fun normalizedSpeed(): Double = speed.coerceIn(0.25, 4.0)

    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_VOICE_ID = "marin"
        const val DEFAULT_RESPONSE_FORMAT = "mp3"
        const val DEFAULT_SPEED = 0.88

        fun readDiagnostics(context: Context): String {
            val configFile = getConfigFile(context)
            val config = read(context)
            return buildString {
                append("Speech API config: ")
                append("exists=").append(configFile.exists() && configFile.isFile)
                append(", enabled=").append(config.enabled)
                append(", provider=").append(config.provider.ifBlank { "(blank)" })
                append(", baseUrl=").append(config.baseUrl.ifBlank { "(blank)" })
                append(", model=").append(config.normalizedModel())
                append(", voiceId=").append(config.normalizedVoiceId())
                append(", apiKeyPresent=").append(config.apiKey.isNotBlank())
            }
        }

        fun ensureExampleConfigFile(context: Context): File? {
            val exampleFile = getExampleConfigFile(context)
            if (exampleFile.exists() && exampleFile.length() > 0L) {
                return exampleFile
            }

            val parent = exampleFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return null
            }

            return try {
                exampleFile.writeText(exampleJson(), Charsets.UTF_8)
                exampleFile.takeIf { it.exists() && it.length() > 0L }
            } catch (_: Exception) {
                null
            }
        }

        fun read(context: Context): AacSpeechApiConfig {
            val configFile = getConfigFile(context)
            if (!configFile.exists() || !configFile.isFile || configFile.length() <= 0L) {
                return AacSpeechApiConfig()
            }

            return try {
                val json = JSONObject(configFile.readText(Charsets.UTF_8))
                AacSpeechApiConfig(
                    enabled = json.optBoolean("enabled", false),
                    provider = json.optString("provider", PROVIDER_OPENAI),
                    baseUrl = json.optString("baseUrl", DEFAULT_BASE_URL),
                    apiKey = json.optString("apiKey", ""),
                    model = json.optString("model", DEFAULT_MODEL),
                    voiceId = json.optString("voiceId", DEFAULT_VOICE_ID),
                    responseFormat = json.optString("responseFormat", DEFAULT_RESPONSE_FORMAT),
                    speed = json.optDouble("speed", DEFAULT_SPEED)
                )
            } catch (_: Exception) {
                AacSpeechApiConfig()
            }
        }

        fun getConfigFile(context: Context): File {
            return File(context.filesDir, "NovaRehab2/aac/config/speech_api.json")
        }

        fun getExampleConfigFile(context: Context): File {
            return File(context.filesDir, "NovaRehab2/aac/config/speech_api_example.json")
        }

        private fun exampleJson(): String {
            return """
                {
                  "enabled": true,
                  "provider": "openai",
                  "baseUrl": "https://api.openai.com",
                  "apiKey": "PASTE_OPENAI_API_KEY_HERE",
                  "model": "gpt-4o-mini-tts",
                  "voiceId": "marin",
                  "responseFormat": "mp3",
                  "speed": 0.88
                }
            """.trimIndent()
        }
    }
}

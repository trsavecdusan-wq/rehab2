package com.rehab2.aac

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiAacSpeechApiClient(context: Context) : AacSpeechApiClient {
    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        text: String,
        languageCode: String,
        voiceId: String
    ): ByteArray? {
        val config = AacSpeechApiConfig.read(appContext)
        if (!config.isOpenAiEnabled()) {
            return null
        }

        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return null

        return try {
            val requestJson = JSONObject().apply {
                put("model", config.normalizedModel())
                put("input", trimmedText)
                put("voice", voiceId.trim().ifBlank { config.normalizedVoiceId() })
                put("speed", config.normalizedSpeed())
                put("instructions", languageInstructions(languageCode))
                put("response_format", config.normalizedResponseFormat())
            }

            val request = Request.Builder()
                .url(buildEndpoint(config.normalizedBaseUrl(), "v1/audio/speech"))
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "OpenAI speech failed: HTTP ${response.code}")
                    return null
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.size <= MIN_AUDIO_BYTES) {
                    Log.e(TAG, "OpenAI speech failed: empty audio response")
                    null
                } else {
                    bytes
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "OpenAI speech failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun buildEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val cleanPath = path.trim().trimStart('/')
        if (base.endsWith("/v1")) {
            return "$base/${cleanPath.removePrefix("v1/")}"
        }
        return "$base/$cleanPath"
    }

    private fun languageInstructions(languageCode: String): String {
        return when (AacLanguageResolver.normalize(languageCode)) {
            "uk", "ua" -> "Speak clearly in Ukrainian."
            "en" -> "Speak clearly in English."
            "de" -> "Speak clearly in German."
            "hr" -> "Speak clearly in Croatian."
            "sr" -> "Speak clearly in Serbian."
            else -> "Speak clearly in Slovenian."
        }
    }

    private companion object {
        const val TAG = "OpenAiAacSpeech"
        const val MIN_AUDIO_BYTES = 1024
    }
}

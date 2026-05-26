package com.rehab2.aac

import android.util.Log
import java.io.File

class AacSpeechCoordinator(
    private val speechCache: AacSpeechCache,
    private val apiClient: AacSpeechApiClient = AacSpeechApiClient.NotConfigured,
    private val voiceIdProvider: () -> String = { DEFAULT_VOICE_ID },
    private val speedProvider: () -> Double = { DEFAULT_SPEED }
) {
    fun getOrGenerateSpeechFile(
        text: String,
        languageCode: String,
        voiceId: String = voiceIdProvider(),
        speed: Double = speedProvider()
    ): File? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        val cachedFile = speechCache.getGeneratedSpeechFile(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId,
            speed = speed
        )
        if (cachedFile != null) {
            Log.d(TAG, "Generated speech cache hit: ${cachedFile.absolutePath}")
            return cachedFile
        }

        val generatedAudio = apiClient.generateSpeech(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId,
            speed = speed
        ) ?: return null

        val savedFile = speechCache.saveGeneratedSpeech(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId,
            speed = speed,
            audioBytes = generatedAudio
        )
        if (savedFile != null) {
            Log.d(TAG, "Generated speech cache saved: ${savedFile.absolutePath}")
        }
        return savedFile
    }

    private companion object {
        const val TAG = "AacSpeechCoordinator"
        const val DEFAULT_VOICE_ID = "default"
        const val DEFAULT_SPEED = 0.88
    }
}

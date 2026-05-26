package com.rehab2.aac

import java.io.File

class AacSpeechCoordinator(
    private val speechCache: AacSpeechCache,
    private val apiClient: AacSpeechApiClient = AacSpeechApiClient.NotConfigured,
    private val voiceIdProvider: () -> String = { DEFAULT_VOICE_ID }
) {
    fun getOrGenerateSpeechFile(
        text: String,
        languageCode: String,
        voiceId: String = voiceIdProvider()
    ): File? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        val cachedFile = speechCache.getGeneratedSpeechFile(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId
        )
        if (cachedFile != null) return cachedFile

        val generatedAudio = apiClient.generateSpeech(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId
        ) ?: return null

        return speechCache.saveGeneratedSpeech(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId,
            audioBytes = generatedAudio
        )
    }

    private companion object {
        const val DEFAULT_VOICE_ID = "default"
    }
}

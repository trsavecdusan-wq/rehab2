package com.rehab2.aac

interface AacSpeechApiClient {
    fun generateSpeech(
        text: String,
        languageCode: String,
        voiceId: String
    ): ByteArray?

    object NotConfigured : AacSpeechApiClient {
        override fun generateSpeech(
            text: String,
            languageCode: String,
            voiceId: String
        ): ByteArray? = null
    }
}

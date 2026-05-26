package com.rehab2.aac

interface AacSpeechApiClient {
    fun generateSpeech(
        text: String,
        languageCode: String,
        voiceId: String,
        speed: Double
    ): ByteArray?

    object NotConfigured : AacSpeechApiClient {
        override fun generateSpeech(
            text: String,
            languageCode: String,
            voiceId: String,
            speed: Double
        ): ByteArray? = null
    }
}

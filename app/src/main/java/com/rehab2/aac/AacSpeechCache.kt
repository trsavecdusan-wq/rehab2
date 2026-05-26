package com.rehab2.aac

import android.content.Context
import java.io.File
import java.security.MessageDigest

class AacSpeechCache(private val context: Context) {
    fun getCacheMp3File(itemId: String): File? = getCacheFile(itemId, "mp3")

    fun getCacheWavFile(itemId: String): File? = getCacheFile(itemId, "wav")

    fun getExistingCacheFile(itemId: String): File? {
        val mp3File = getCacheMp3File(itemId)
        if (mp3File != null && mp3File.exists() && mp3File.isFile) {
            return mp3File
        }

        val wavFile = getCacheWavFile(itemId)
        return if (wavFile != null && wavFile.exists() && wavFile.isFile) wavFile else null
    }

    fun getGeneratedSpeechFile(
        text: String,
        languageCode: String,
        voiceId: String,
        speed: Double
    ): File? {
        val cacheFile = getGeneratedSpeechCacheFile(text, languageCode, voiceId, speed) ?: return null
        return if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
            cacheFile
        } else {
            null
        }
    }

    fun saveGeneratedSpeech(
        text: String,
        languageCode: String,
        voiceId: String,
        speed: Double,
        audioBytes: ByteArray
    ): File? {
        if (audioBytes.isEmpty()) return null

        val cacheFile = getGeneratedSpeechCacheFile(text, languageCode, voiceId, speed) ?: return null
        if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
            return cacheFile
        }

        val parentDir = cacheFile.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return null
        }

        return try {
            cacheFile.writeBytes(audioBytes)
            if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getCacheFile(itemId: String, extension: String): File? {
        val normalizedItemId = itemId.trim()
        if (normalizedItemId.isBlank()) {
            return null
        }

        val audioDir = AacLocalStorage.getAudioSlDir(context) ?: return null
        return File(audioDir, "$normalizedItemId.$extension")
    }

    private fun getGeneratedSpeechCacheFile(
        text: String,
        languageCode: String,
        voiceId: String,
        speed: Double
    ): File? {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return null

        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        val normalizedVoice = normalizeVoiceId(voiceId)
        val normalizedSpeed = normalizeSpeed(speed)
        val normalizedHashText = normalizeTextForHash(normalizedText)
        val hash = sha256("$normalizedLanguage|$normalizedVoice|$normalizedSpeed|$normalizedHashText")
        val audioDir = File(
            context.filesDir,
            "NovaRehab2/aac/audio/generated/$normalizedLanguage/$normalizedVoice"
        )
        return File(audioDir, "$hash.mp3")
    }

    private fun normalizeTextForHash(value: String): String {
        return value.trim().replace(Regex("\\s+"), " ")
    }

    private fun normalizeVoiceId(value: String): String {
        return value.trim()
            .ifBlank { DEFAULT_VOICE_ID }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun normalizeSpeed(value: Double): String {
        return "%.2f".format(java.util.Locale.ROOT, value.coerceIn(0.25, 4.0))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DEFAULT_VOICE_ID = "default"
    }
}

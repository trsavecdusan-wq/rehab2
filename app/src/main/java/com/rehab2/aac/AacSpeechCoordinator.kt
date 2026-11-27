package com.rehab2.aac

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore

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

        val identity = speechCache.generationIdentity(normalizedLanguage)
        val key = listOf(speechCache.namespace, identity, normalizedLanguage, voiceId, speed.toString(), trimmed).joinToString("\u001f")
        val task = FutureTask<File?> {
            if (!generationSlots.tryAcquire()) return@FutureTask speechCache.getLegacyGeneratedSpeechFile(trimmed, normalizedLanguage, voiceId, speed)
            try { generateAndSave(trimmed, normalizedLanguage, voiceId, speed) }
            finally { generationSlots.release() }
        }
        val existing = inFlight.putIfAbsent(key, task)
        if (existing != null) return runCatching { existing.get() }.getOrNull()
        return try { task.run(); task.get() } catch (_: Exception) {
            speechCache.getLegacyGeneratedSpeechFile(trimmed, normalizedLanguage, voiceId, speed)
        } finally { inFlight.remove(key, task) }
    }

    private fun generateAndSave(trimmed: String, normalizedLanguage: String, voiceId: String, speed: Double): File? {
        // Recheck after joining the request queue; another owner may already have populated it.
        speechCache.getGeneratedSpeechFile(trimmed, normalizedLanguage, voiceId, speed)?.let { return it }
        val identity = speechCache.generationIdentity(normalizedLanguage)
        val generatedAudio = apiClient.generateSpeech(
            text = trimmed,
            languageCode = normalizedLanguage,
            voiceId = voiceId,
            speed = speed
        ) ?: return speechCache.getLegacyGeneratedSpeechFile(
            trimmed, normalizedLanguage, voiceId, speed
        )

        // A service/settings change during HTTP must not mislabel the returned recording.
        if (identity != speechCache.generationIdentity(normalizedLanguage)) return null
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
        return savedFile ?: speechCache.getLegacyGeneratedSpeechFile(trimmed, normalizedLanguage, voiceId, speed)
    }

    private companion object {
        val inFlight = ConcurrentHashMap<String, FutureTask<File?>>()
        val generationSlots = Semaphore(2)
        const val TAG = "AacSpeechCoordinator"
        const val DEFAULT_VOICE_ID = "default"
        const val DEFAULT_SPEED = 0.88
    }
}

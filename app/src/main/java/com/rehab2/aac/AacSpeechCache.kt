package com.rehab2.aac

import android.content.Context
import java.io.File

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

    private fun getCacheFile(itemId: String, extension: String): File? {
        val normalizedItemId = itemId.trim()
        if (normalizedItemId.isBlank()) {
            return null
        }

        val audioDir = AacLocalStorage.getAudioSlDir(context) ?: return null
        return File(audioDir, "$normalizedItemId.$extension")
    }
}
package com.rehab2.aac

import android.content.Context
import java.io.File

class AacSpeechCache(private val context: Context) {
    companion object {
        private const val AUDIO_ROOT_DIR_NAME = "NovaRehab2/aac/audio/sl"
    }

    fun getCacheFile(itemId: String): File? {
        val normalizedItemId = itemId.trim()
        if (normalizedItemId.isBlank()) {
            return null
        }

        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(File(externalFilesDir, AUDIO_ROOT_DIR_NAME), "$normalizedItemId.mp3")
    }

    fun getExistingCacheFile(itemId: String): File? {
        val cacheFile = getCacheFile(itemId) ?: return null
        return if (cacheFile.exists() && cacheFile.isFile) cacheFile else null
    }
}
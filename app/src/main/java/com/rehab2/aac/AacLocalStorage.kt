package com.rehab2.aac

import android.content.Context
import java.io.File

object AacLocalStorage {
    private const val AAC_ROOT_DIR_NAME = "NovaRehab2/aac"
    private const val SEEDED_TEST_AUDIO_ASSET_PATH = "aac/audio/sl/water.wav"
    private const val SEEDED_TEST_AUDIO_FILE_NAME = "water.wav"

    fun getRootDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, AAC_ROOT_DIR_NAME)
    }

    fun getPagesDir(context: Context): File? = getRootDir(context)?.let { File(it, "pages") }

    fun getIconsSocaDir(context: Context): File? = getRootDir(context)?.let { File(it, "icons/soca") }

    fun getIconsArasaacDir(context: Context): File? = getRootDir(context)?.let { File(it, "icons/arasaac") }

    fun getIconsPatientDir(context: Context): File? = getRootDir(context)?.let { File(it, "icons/patient") }

    fun getIconsCustomDir(context: Context): File? = getRootDir(context)?.let { File(it, "icons/custom") }

    fun getAudioSlDir(context: Context): File? = getRootDir(context)?.let { File(it, "audio/sl") }

    fun getAudioUkDir(context: Context): File? = getRootDir(context)?.let { File(it, "audio/uk") }

    fun ensureStructure(context: Context): Boolean {
        val dirs = listOfNotNull(
            getRootDir(context),
            getPagesDir(context),
            getIconsSocaDir(context),
            getIconsArasaacDir(context),
            getIconsPatientDir(context),
            getIconsCustomDir(context),
            getAudioSlDir(context),
            getAudioUkDir(context)
        )

        if (dirs.isEmpty()) {
            return false
        }

        return dirs.all { dir -> dir.exists() || dir.mkdirs() }
    }

    fun seedBundledTestAudio(context: Context): Boolean {
        val audioSlDir = getAudioSlDir(context) ?: return false
        if (!audioSlDir.exists() && !audioSlDir.mkdirs()) {
            return false
        }

        val targetFile = File(audioSlDir, SEEDED_TEST_AUDIO_FILE_NAME)
        if (targetFile.exists() && targetFile.isFile) {
            return true
        }

        return try {
            context.assets.open(SEEDED_TEST_AUDIO_ASSET_PATH).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
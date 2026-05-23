package com.rehab2.aac

import android.content.Context
import java.io.File

object AacLocalStorage {
    private const val AAC_ROOT_DIR_NAME = "NovaRehab2/aac"
    private const val SEEDED_TEST_AUDIO_ASSET_PATH = "aac/audio/sl/water.wav"
    private const val SEEDED_TEST_AUDIO_FILE_NAME = "water.wav"
    private val DEFAULT_PAGE_ASSET_NAMES = listOf(
        "home.json",
        "drinks.json",
        "juice.json",
        "food.json",
        "pain.json"
    )

    private var lastEnsureResult = "NOT_RUN"
    private var lastSeedPagesResult = "NOT_RUN"
    private var lastSeedAudioResult = "NOT_RUN"

    var lastStorageDebugStatus: String = "NOT_RUN"
        private set

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

        val result = dirs.isNotEmpty() && dirs.all { dir -> dir.exists() || dir.mkdirs() }
        lastEnsureResult = if (result) "OK" else "FAIL"
        refreshDebugStatus(context)
        return result
    }

    fun seedBundledDefaultPages(context: Context): Boolean {
        val pagesDir = getPagesDir(context)
        if (pagesDir == null || (!pagesDir.exists() && !pagesDir.mkdirs())) {
            lastSeedPagesResult = "FAIL"
            refreshDebugStatus(context)
            return false
        }

        val result = try {
            DEFAULT_PAGE_ASSET_NAMES.all { assetName ->
                copyAssetIfMissingOrEmpty(
                    context = context,
                    assetPath = "aac/pages/4assetName",
                    targetFile = File(pagesDir, assetName)
                )
            }
        } catch (_: Exception) {
            false
        }

        lastSeedPagesResult = if (result) "OK" else "FAIL"
        refreshDebugStatus(context)
        return result
    }

    fun seedBundledTestAudio(context: Context): Boolean {
        val audioSlDir = getAudioSlDir(context)
        if (audioSlDir == null || (!audioSlDir.exists() && !audioSlDir.mkdirs())) {
            lastSeedAudioResult = "FAIL"
            refreshDebugStatus(context)
            return false
        }

        val result = try {
            copyAssetIfMissingOrEmpty(
                context = context,
                assetPath = SEEDED_TEST_AUDIO_ASSET_PATH,
                targetFile = File(audioSlDir, SEEDED_TEST_AUDIO_FILE_NAME)
            )
        } catch (_: Exception) {
            false
        }

        lastSeedAudioResult = if (result) "OK" else "FAIL"
        refreshDebugStatus(context)
        return result
    }

    private fun copyAssetIfMissingOrEmpty(context: Context, assetPath: String, targetFile: File): Boolean {
        if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0L) {
            return true
        }

        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return false
        }

        return try {
            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.exists() && targetFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshDebugStatus(context: Context) {
        val rootPath = getRootDir(context)?.absolutePath.orEmpty()
        val pagesPath = getPagesDir(context)?.absolutePath.orEmpty()
        val audioSlPath = getAudioSlDir(context)?.absolutePath.orEmpty()
        lastStorageDebugStatus = "root=4rootPath pages=4pagesPath audioSl=4audioSlPath ensure=4lastEnsureResult seedPages=4lastSeedPagesResult seedAudio=4lastSeedAudioResult"
    }
}
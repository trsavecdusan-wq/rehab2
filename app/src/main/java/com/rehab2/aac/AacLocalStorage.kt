package com.rehab2.aac

import android.content.Context
import java.io.File

object AacLocalStorage {
    private const val AAC_ROOT_DIR_NAME = "NovaRehab2/aac"
    private const val SEEDED_TEST_AUDIO_ASSET_PATH = "aac/audio/sl/water.wav"
    private const val SEEDED_TEST_AUDIO_FILE_NAME = "water.wav"
    private const val SEEDED_TEST_V2_PAGE_ASSET_PATH = "aac/pages/drinks_v2_test.json"
    private const val SEEDED_TEST_V2_PAGE_FILE_NAME = "drinks_v2_test.json"
    private const val SEEDED_DRINKS_V2_PAGE_ASSET_PATH = "aac/pages/drinks_v2.json"
    private const val SEEDED_DRINKS_V2_PAGE_FILE_NAME = "drinks_v2.json"
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

        val result = dirs.isNotEmpty() &&
            dirs.all { dir -> dir.exists() || dir.mkdirs() } &&
            AacStoragePaths.ensureAacContentDirs(context)
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
                    assetPath = "aac/pages/$assetName",
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

    fun seedBundledTestV2Page(context: Context): Boolean {
        val pagesDir = getPagesDir(context)
        if (pagesDir == null || (!pagesDir.exists() && !pagesDir.mkdirs())) {
            refreshDebugStatus(context)
            return false
        }

        return try {
            copyAssetIfMissingOrEmpty(
                context = context,
                assetPath = SEEDED_TEST_V2_PAGE_ASSET_PATH,
                targetFile = File(pagesDir, SEEDED_TEST_V2_PAGE_FILE_NAME)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun seedBundledDrinksV2Page(context: Context): Boolean {
        val pagesDir = getPagesDir(context)
        if (pagesDir == null || (!pagesDir.exists() && !pagesDir.mkdirs())) {
            refreshDebugStatus(context)
            return false
        }

        return try {
            copyAssetIfMissingOrEmpty(
                context = context,
                assetPath = SEEDED_DRINKS_V2_PAGE_ASSET_PATH,
                targetFile = File(pagesDir, SEEDED_DRINKS_V2_PAGE_FILE_NAME)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun rebuildBundledDrinksV2Page(context: Context): Boolean {
        val pagesDir = getPagesDir(context)
        if (pagesDir == null || (!pagesDir.exists() && !pagesDir.mkdirs())) {
            refreshDebugStatus(context)
            return false
        }

        return try {
            copyAssetReplacing(
                context = context,
                assetPath = SEEDED_DRINKS_V2_PAGE_ASSET_PATH,
                targetFile = File(pagesDir, SEEDED_DRINKS_V2_PAGE_FILE_NAME)
            )
        } catch (_: Exception) {
            false
        }
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

    private fun copyAssetReplacing(context: Context, assetPath: String, targetFile: File): Boolean {
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            return false
        }

        if (targetFile.exists() && !targetFile.delete()) {
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
        lastStorageDebugStatus = "root=$rootPath pages=$pagesPath audioSl=$audioSlPath ensure=$lastEnsureResult seedPages=$lastSeedPagesResult seedAudio=$lastSeedAudioResult"
    }
}

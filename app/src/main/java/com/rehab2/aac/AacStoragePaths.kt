package com.rehab2.aac

import android.content.Context
import java.io.File

object AacStoragePaths {
    const val PROFILES_DATA_DIR = "NovaRehab/data/profiles/"
    // Soča starter AAC paths such as "soca/voda.png" resolve to NovaRehab/icons/soca/voda.png.
    const val SOCA_ICONS_DIR = "NovaRehab/icons/soca/"
    const val SYSTEM_ICONS_DIR = "NovaRehab/icons/system/"
    const val ARASAAC_ICONS_DIR = "NovaRehab/icons/arasaac/"
    const val CUSTOM_ICONS_DIR = "NovaRehab/icons/custom/"
    const val TRANSLATIONS_DATA_DIR = "NovaRehab/data/translations/"
    const val AUDIO_DATA_DIR = "NovaRehab/data/audio/"
    const val IMPORT_DIR = "NovaRehab/import/"
    const val AAC_ITEMS_FILE = "NovaRehab/data/aac_items.json"
    const val PROFILES_DIR = "NovaRehab/profiles/"
    const val SOCA_STARTER_WATER_ICON = "voda.png"
    const val SOCA_STARTER_WC_ICON = "wc.png"
    const val SOCA_STARTER_HELP_ICON = "pomoc.png"
    const val SOCA_STARTER_PAIN_ICON = "boli.png"

    fun getAacItemsFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, AAC_ITEMS_FILE)
    }

    fun getProfilesDataDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, PROFILES_DATA_DIR)
    }

    fun getIconsSocaDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, SOCA_ICONS_DIR)
    }

    fun getIconsSystemDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, SYSTEM_ICONS_DIR)
    }

    fun getIconsArasaacDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, ARASAAC_ICONS_DIR)
    }

    fun getIconsCustomDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, CUSTOM_ICONS_DIR)
    }

    fun getImportDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, IMPORT_DIR)
    }

    fun getTranslationsDataDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, TRANSLATIONS_DATA_DIR)
    }

    fun getAudioDataDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, AUDIO_DATA_DIR)
    }

    fun ensureAacContentDirs(context: Context): Boolean {
        val dirs = listOfNotNull(
            getAacItemsFile(context)?.parentFile,
            getProfilesDataDir(context),
            getIconsSocaDir(context),
            getIconsSystemDir(context),
            getIconsCustomDir(context),
            getIconsArasaacDir(context),
            getTranslationsDataDir(context),
            getAudioDataDir(context),
            getImportDir(context)
        )
        return dirs.size == 9 && dirs.all { dir -> dir.exists() || dir.mkdirs() }
    }

    fun resolveIconFile(context: Context, imagePath: String, iconSource: IconSource): File? {
        val rawPath = imagePath.trim()
        if (rawPath.isEmpty()) {
            return null
        }

        val directFile = File(rawPath)
        if (directFile.isAbsolute) {
            return directFile
        }

        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        val normalizedPath = rawPath.replace('\\', '/').removePrefix("/")

        if (normalizedPath.startsWith("NovaRehab/icons/", ignoreCase = true)) {
            return File(externalFilesDir, normalizedPath)
        }

        if (normalizedPath.startsWith("icons/", ignoreCase = true)) {
            return File(externalFilesDir, "NovaRehab/$normalizedPath")
        }

        val baseRelativeDir = when (iconSource) {
            IconSource.SOCA -> SOCA_ICONS_DIR
            IconSource.ARASAAC -> ARASAAC_ICONS_DIR
            IconSource.CUSTOM,
            IconSource.PATIENT -> CUSTOM_ICONS_DIR
            IconSource.SYSTEM -> SYSTEM_ICONS_DIR
        }

        val fullRelativePath = if (
            normalizedPath.startsWith("custom/", ignoreCase = true) ||
            normalizedPath.startsWith("soca/", ignoreCase = true) ||
            normalizedPath.startsWith("system/", ignoreCase = true) ||
            normalizedPath.startsWith("arasaac/", ignoreCase = true)
        ) {
            "NovaRehab/icons/$normalizedPath"
        } else {
            baseRelativeDir + normalizedPath
        }

        return File(externalFilesDir, fullRelativePath)
    }
}

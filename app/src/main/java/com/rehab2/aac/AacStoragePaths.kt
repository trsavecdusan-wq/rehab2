package com.rehab2.aac

import android.content.Context
import java.io.File

object AacStoragePaths {
    const val PROFILES_DATA_DIR = "NovaRehab/data/profiles/"
    const val SOCA_ICONS_DIR = "NovaRehab/icons/soca/"
    const val ARASAAC_ICONS_DIR = "NovaRehab/icons/arasaac/"
    const val CUSTOM_ICONS_DIR = "NovaRehab/icons/custom/"
    const val AAC_ITEMS_FILE = "NovaRehab/data/aac_items.json"
    const val PROFILES_DIR = "NovaRehab/profiles/"

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

    fun getIconsArasaacDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, ARASAAC_ICONS_DIR)
    }

    fun getIconsCustomDir(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, CUSTOM_ICONS_DIR)
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

        val baseRelativeDir = when (iconSource) {
            IconSource.SOCA -> SOCA_ICONS_DIR
            IconSource.ARASAAC -> ARASAAC_ICONS_DIR
            IconSource.CUSTOM,
            IconSource.PATIENT -> CUSTOM_ICONS_DIR
            IconSource.SYSTEM -> return null
        }

        val fullRelativePath = if (
            normalizedPath.startsWith("custom/") ||
            normalizedPath.startsWith("soca/") ||
            normalizedPath.startsWith("arasaac/")
        ) {
            "NovaRehab/icons/$normalizedPath"
        } else {
            baseRelativeDir + normalizedPath
        }

        return File(externalFilesDir, fullRelativePath)
    }
}

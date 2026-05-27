package com.rehab2.aac

import android.content.Context
import java.io.File

object AacContentDiagnostics {
    private val sampleImageRelativePaths = listOf(
        "custom/water.png",
        "custom/juice.png",
        "custom/coffee.png",
        "custom/tea.png"
    )

    data class Report(
        val storageUnavailable: Boolean,
        val basePath: String?,
        val itemsJsonExists: Boolean,
        val profilesDirExists: Boolean,
        val domProfileExists: Boolean,
        val profileJsonFileNames: List<String>,
        val customIconsDirExists: Boolean,
        val socaIconsDirExists: Boolean,
        val arasaacIconsDirExists: Boolean,
        val missingSampleImages: List<String>
    )

    fun inspect(context: Context): Report {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: return Report(
                storageUnavailable = true,
                basePath = null,
                itemsJsonExists = false,
                domProfileExists = false,
                profilesDirExists = false,
                profileJsonFileNames = emptyList(),
                customIconsDirExists = false,
                socaIconsDirExists = false,
                arasaacIconsDirExists = false,
                missingSampleImages = emptyList()
            )

        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)
        val profileJsonFileNames = if (profilesDir?.exists() == true && profilesDir.isDirectory) {
            profilesDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.map { it.name }
                .orEmpty()
        } else {
            emptyList()
        }
        val domProfileExists = profileJsonFileNames.any {
            it.equals("dom.json", ignoreCase = true)
        }

        val missingImages = sampleImageRelativePaths.mapNotNull { relativePath ->
            val resolved = AacStoragePaths.resolveIconFile(context, relativePath, IconSource.CUSTOM)
            val exists = resolved?.exists() == true && resolved.isFile
            if (exists) null else relativePath.substringAfterLast('/')
        }

        return Report(
            storageUnavailable = false,
            basePath = externalFilesDir.absolutePath,
            domProfileExists = domProfileExists,
            itemsJsonExists = itemsFile?.exists() == true && itemsFile.isFile,
            profilesDirExists = profilesDir?.exists() == true && profilesDir.isDirectory,
            profileJsonFileNames = profileJsonFileNames,
            customIconsDirExists = customIconsDir?.exists() == true && customIconsDir.isDirectory,
            socaIconsDirExists = socaIconsDir?.exists() == true && socaIconsDir.isDirectory,
            arasaacIconsDirExists = arasaacIconsDir?.exists() == true && arasaacIconsDir.isDirectory,
            missingSampleImages = missingImages
        )
    }
}

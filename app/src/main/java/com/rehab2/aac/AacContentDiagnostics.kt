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
        val domProfileExists: Boolean,
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
                customIconsDirExists = false,
                socaIconsDirExists = false,
                arasaacIconsDirExists = false,
                missingSampleImages = emptyList()
            )

        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val domProfileFile = profilesDir?.let { File(it, "dom.json") }
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)

        val missingImages = sampleImageRelativePaths.mapNotNull { relativePath ->
            val resolved = AacStoragePaths.resolveIconFile(context, relativePath, IconSource.CUSTOM)
            val exists = resolved?.exists() == true && resolved.isFile
            if (exists) null else relativePath.substringAfterLast('/')
        }

        return Report(
            storageUnavailable = false,
            basePath = externalFilesDir.absolutePath,
            itemsJsonExists = itemsFile?.exists() == true && itemsFile.isFile,
            domProfileExists = domProfileFile?.exists() == true && domProfileFile.isFile,
            customIconsDirExists = customIconsDir?.exists() == true && customIconsDir.isDirectory,
            socaIconsDirExists = socaIconsDir?.exists() == true && socaIconsDir.isDirectory,
            arasaacIconsDirExists = arasaacIconsDir?.exists() == true && arasaacIconsDir.isDirectory,
            missingSampleImages = missingImages
        )
    }
}

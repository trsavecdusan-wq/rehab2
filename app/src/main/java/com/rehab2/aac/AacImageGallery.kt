package com.rehab2.aac

import android.content.Context
import java.io.File

object AacImageGallery {
    data class Source(
        val title: String,
        val iconSource: IconSource,
        val roots: List<File>,
        val pathPrefix: String,
        val recursive: Boolean = true
    )

    data class Entry(
        val file: File,
        val source: Source,
        val imagePath: String
    )

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

    fun sources(context: Context): List<Source> {
        return listOf(
            Source(
                title = "SYSTEM",
                iconSource = IconSource.SYSTEM,
                roots = listOfNotNull(AacStoragePaths.getIconsSystemDir(context)),
                pathPrefix = "system"
            ),
            Source(
                title = "SO\u010cA",
                iconSource = IconSource.SOCA,
                roots = listOfNotNull(AacStoragePaths.getIconsSocaDir(context)),
                pathPrefix = "soca"
            ),
            Source(
                title = "ARASAAC",
                iconSource = IconSource.ARASAAC,
                roots = listOfNotNull(AacStoragePaths.getIconsArasaacDir(context)),
                pathPrefix = "arasaac"
            ),
            Source(
                title = "PACIENT",
                iconSource = IconSource.PATIENT,
                roots = listOfNotNull(AacStoragePaths.getIconsPatientDir(context)),
                pathPrefix = ""
            ),
            Source(
                title = "CUSTOM",
                iconSource = IconSource.CUSTOM,
                roots = listOfNotNull(AacStoragePaths.getIconsCustomDir(context)),
                pathPrefix = "custom"
            )
        )
    }

    fun scan(source: Source): List<Entry> {
        return source.roots
            .filter { root -> root.exists() && root.isDirectory }
            .flatMap { root ->
                val files = if (source.recursive) {
                    root.walkTopDown().filter { it.isFile }
                } else {
                    root.listFiles()?.asSequence()?.filter { it.isFile } ?: emptySequence()
                }
                files
                    .filter(::isSupportedImage)
                    .map { file ->
                        Entry(
                            file = file,
                            source = source,
                            imagePath = relativeImagePath(root, source.pathPrefix, file)
                        )
                    }
                    .toList()
            }
            .distinctBy { entry -> "${entry.source.iconSource.name}:${entry.imagePath}" }
            .sortedWith(compareBy({ it.file.parentFile?.name.orEmpty() }, { it.file.name.lowercase() }))
    }

    private fun isSupportedImage(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in imageExtensions
    }

    private fun relativeImagePath(root: File, prefix: String, file: File): String {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        return if (prefix.isBlank()) relative else "$prefix/$relative"
    }
}

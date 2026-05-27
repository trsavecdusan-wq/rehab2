package com.rehab2.aac

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

object AacPackImportPreflight {

    sealed class Result {
        data class Success(
            val entryCount: Int,
            val importEntryNames: List<String>,
            val hasItems: Boolean,
            val profileCount: Int,
            val profileNames: List<String>,
            val customIconCount: Int,
            val socaIconCount: Int,
            val arasaacIconCount: Int,
            val hasManifest: Boolean
        ) : Result()

        data class Rejected(val reason: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun inspect(contentResolver: ContentResolver, uri: Uri): Result {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    inspect(zip)
                }
            } ?: Result.Failure("Datoteke ni mogoce odpreti.")
        } catch (error: IOException) {
            Result.Failure("ZIP datoteke ni mogoce prebrati: ${error.message ?: "neznana napaka"}")
        } catch (error: SecurityException) {
            Result.Failure("Ni dovoljenja za branje izbrane datoteke.")
        } catch (error: Exception) {
            Result.Failure("Preverjanje ni uspelo: ${error.message ?: "neznana napaka"}")
        }
    }

    private fun inspect(zip: ZipInputStream): Result {
        var entryCount = 0
        val importEntryNames = mutableListOf<String>()
        var hasItems = false
        var profileCount = 0
        val profileNames = mutableListOf<String>()
        var customIconCount = 0
        var socaIconCount = 0
        var arasaacIconCount = 0
        var hasManifest = false

        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            val unsafeReason = rejectionReasonForEntry(name)
            if (unsafeReason != null) {
                return Result.Rejected("Nevarna pot v ZIP: $unsafeReason")
            }

            if (!entry.isDirectory) {
                if (!isAllowedImportEntry(name)) {
                    return Result.Rejected("Nedovoljena datoteka v ZIP: $name")
                }
                entryCount += 1
                importEntryNames += name
                when {
                    name == "data/aac_items.json" -> hasItems = true
                    isDirectJsonChild(name, "data/profiles/") -> {
                        profileCount += 1
                        profileNames += name.removePrefix("data/profiles/")
                    }
                    name.startsWith("icons/custom/") -> customIconCount += 1
                    name.startsWith("icons/soca/") -> socaIconCount += 1
                    name.startsWith("icons/arasaac/") -> arasaacIconCount += 1
                    name == "aac_export_manifest.json" -> hasManifest = true
                }
            }

            zip.closeEntry()
        }

        return Result.Success(
            entryCount = entryCount,
            importEntryNames = importEntryNames.sortedBy { it.lowercase(Locale.ROOT) },
            hasItems = hasItems,
            profileCount = profileCount,
            profileNames = profileNames.sortedBy { it.lowercase(Locale.ROOT) },
            customIconCount = customIconCount,
            socaIconCount = socaIconCount,
            arasaacIconCount = arasaacIconCount,
            hasManifest = hasManifest
        )
    }

    internal fun rejectionReasonForEntry(name: String?): String? {
        if (name.isNullOrEmpty()) {
            return "prazno ime vnosa"
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return name
        }
        if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) {
            return name
        }
        if (name.contains('\\')) {
            return name
        }
        if (name.split('/').any { it == ".." }) {
            return name
        }
        return null
    }

    internal fun isAllowedImportEntry(name: String): Boolean {
        return when {
            name == "data/aac_items.json" -> true
            isDirectJsonChild(name, "data/profiles/") -> true
            name == "aac_export_manifest.json" -> true
            isAllowedImageChild(name, "icons/custom/") -> true
            isAllowedImageChild(name, "icons/soca/") -> true
            isAllowedImageChild(name, "icons/arasaac/") -> true
            else -> false
        }
    }

    internal fun relativeDestinationPath(name: String): String? {
        if (!isAllowedImportEntry(name)) {
            return null
        }
        return if (name == "aac_export_manifest.json") {
            "NovaRehab/$name"
        } else {
            "NovaRehab/$name"
        }
    }

    private fun isDirectJsonChild(name: String, directory: String): Boolean {
        if (!name.startsWith(directory) || !name.endsWith(".json")) {
            return false
        }
        val childName = name.removePrefix(directory)
        return childName.isNotEmpty() && !childName.contains("/")
    }

    private fun isAllowedImageChild(name: String, directory: String): Boolean {
        val childName = name.removePrefix(directory)
        if (!name.startsWith(directory) || childName.isEmpty()) {
            return false
        }
        val lowerName = childName.lowercase(Locale.ROOT)
        return lowerName.endsWith(".png") ||
            lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".webp")
    }
}

package com.rehab2.aac

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

object AacPackImportPreflight {

    private const val MAX_MANIFEST_PREVIEW_BYTES = 64 * 1024
    private const val MAX_PROFILE_EMPTY_CHECK_BYTES = 64 * 1024
    private const val SUSPICIOUS_ICON_FOLDER_COUNT = 1_000
    private const val SUSPICIOUS_TOTAL_ICON_COUNT = 3_000

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
            val hasManifest: Boolean,
            val manifestMetadata: ManifestMetadata?,
            val integrityWarnings: List<String>
        ) : Result()

        data class Rejected(val reason: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    data class ManifestMetadata(
        val packageName: String?,
        val packageVersion: String?,
        val exportTimestamp: String?,
        val therapistAuthor: String?,
        val description: String?,
        val warning: String?
    )

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
        val seenEntryNames = mutableSetOf<String>()
        val duplicateEntryNames = mutableListOf<String>()
        var hasItems = false
        var profileCount = 0
        val profileNames = mutableListOf<String>()
        val emptyProfileNames = mutableListOf<String>()
        var customIconCount = 0
        var socaIconCount = 0
        var arasaacIconCount = 0
        var hasManifest = false
        var manifestMetadata: ManifestMetadata? = null

        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            val unsafeReason = rejectionReasonForEntry(name)
            if (unsafeReason != null) {
                return Result.Rejected("Nevarna pot v ZIP: $unsafeReason")
            }
            if (!seenEntryNames.add(name) && duplicateEntryNames.size < 10) {
                duplicateEntryNames += name
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
                        val profileName = name.removePrefix("data/profiles/")
                        profileNames += profileName
                        if (isProfileEntryEmpty(zip)) {
                            emptyProfileNames += profileName
                        }
                    }
                    name.startsWith("icons/custom/") -> customIconCount += 1
                    name.startsWith("icons/soca/") -> socaIconCount += 1
                    name.startsWith("icons/arasaac/") -> arasaacIconCount += 1
                    name == "aac_export_manifest.json" -> {
                        hasManifest = true
                        manifestMetadata = readManifestMetadata(zip)
                    }
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
            hasManifest = hasManifest,
            manifestMetadata = manifestMetadata,
            integrityWarnings = buildIntegrityWarnings(
                hasItems = hasItems,
                profileCount = profileCount,
                customIconCount = customIconCount,
                socaIconCount = socaIconCount,
                arasaacIconCount = arasaacIconCount,
                duplicateEntryNames = duplicateEntryNames,
                emptyProfileNames = emptyProfileNames
            )
        )
    }

    private fun buildIntegrityWarnings(
        hasItems: Boolean,
        profileCount: Int,
        customIconCount: Int,
        socaIconCount: Int,
        arasaacIconCount: Int,
        duplicateEntryNames: List<String>,
        emptyProfileNames: List<String>
    ): List<String> {
        val warnings = mutableListOf<String>()
        val totalIconCount = customIconCount + socaIconCount + arasaacIconCount

        if (duplicateEntryNames.isNotEmpty()) {
            warnings += buildString {
                append("ZIP vsebuje podvojena imena vnosov: ")
                append(duplicateEntryNames.joinToString(", "))
            }
        }
        if (emptyProfileNames.isNotEmpty()) {
            warnings += buildString {
                append("Prazne profilne JSON datoteke: ")
                append(emptyProfileNames.take(10).joinToString(", "))
            }
        }
        if (!hasItems) {
            warnings += "AAC items JSON manjka."
        }
        if (profileCount == 0) {
            warnings += "V ZIP ni profilnih JSON datotek."
        }
        if (profileCount > 0 && totalIconCount == 0) {
            warnings += "Profili so prisotni, ikon pa ni."
        }
        if (totalIconCount > 0 && profileCount == 0) {
            warnings += "Ikone so prisotne, profilov pa ni."
        }
        if (customIconCount > SUSPICIOUS_ICON_FOLDER_COUNT) {
            warnings += "Nenavadno veliko custom ikon: $customIconCount."
        }
        if (socaIconCount > SUSPICIOUS_ICON_FOLDER_COUNT) {
            warnings += "Nenavadno veliko SOCA ikon: $socaIconCount."
        }
        if (arasaacIconCount > SUSPICIOUS_ICON_FOLDER_COUNT) {
            warnings += "Nenavadno veliko ARASAAC ikon: $arasaacIconCount."
        }
        if (totalIconCount > SUSPICIOUS_TOTAL_ICON_COUNT) {
            warnings += "Nenavadno veliko ikon skupaj: $totalIconCount."
        }

        return warnings
    }

    private fun isProfileEntryEmpty(zip: ZipInputStream): Boolean {
        val buffer = ByteArray(1024)
        var totalBytes = 0
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) {
                return true
            }
            totalBytes += read
            for (index in 0 until read) {
                if (!buffer[index].toInt().toChar().isWhitespace()) {
                    return false
                }
            }
            if (totalBytes > MAX_PROFILE_EMPTY_CHECK_BYTES) {
                return false
            }
        }
    }

    private fun readManifestMetadata(zip: ZipInputStream): ManifestMetadata {
        return try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var totalBytes = 0
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                totalBytes += read
                if (totalBytes > MAX_MANIFEST_PREVIEW_BYTES) {
                    return ManifestMetadata(
                        packageName = null,
                        packageVersion = null,
                        exportTimestamp = null,
                        therapistAuthor = null,
                        description = null,
                        warning = "Manifest je prevelik za predogled metapodatkov."
                    )
                }
                output.write(buffer, 0, read)
            }

            val manifestText = output.toString(Charsets.UTF_8.name()).trim()
            if (manifestText.isEmpty()) {
                return ManifestMetadata(
                    packageName = null,
                    packageVersion = null,
                    exportTimestamp = null,
                    therapistAuthor = null,
                    description = null,
                    warning = "Manifest je prazen."
                )
            }

            val json = JSONObject(manifestText)
            ManifestMetadata(
                packageName = firstString(json, "packageName", "name", "title"),
                packageVersion = firstString(json, "packageVersion", "version", "appVersion", "exportVersion"),
                exportTimestamp = firstString(json, "exportTimestamp", "exportedAt", "createdAt"),
                therapistAuthor = firstString(json, "therapist", "author", "exportedBy"),
                description = firstString(json, "description"),
                warning = null
            )
        } catch (_: Exception) {
            ManifestMetadata(
                packageName = null,
                packageVersion = null,
                exportTimestamp = null,
                therapistAuthor = null,
                description = null,
                warning = "Manifest ni veljaven JSON za predogled metapodatkov."
            )
        }
    }

    private fun firstString(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = json.opt(key)?.toString()?.trim()
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return null
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

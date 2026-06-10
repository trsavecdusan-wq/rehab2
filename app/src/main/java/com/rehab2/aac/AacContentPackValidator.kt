package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacContentPackValidator {
    data class Result(
        val valid: Boolean,
        val warnings: List<String>,
        val missingPaths: List<String>,
        val brokenProfileFiles: List<String>,
        val unreadableFiles: List<String>,
        val missingIconReferences: List<String>
    )

    fun validate(context: Context): Result {
        val warnings = mutableListOf<String>()
        val missingPaths = mutableListOf<String>()
        val brokenProfileFiles = mutableListOf<String>()
        val unreadableFiles = mutableListOf<String>()
        val missingIconReferences = linkedSetOf<String>()

        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)

        if (itemsFile == null || !itemsFile.exists() || !itemsFile.isFile) {
            missingPaths += AacStoragePaths.AAC_ITEMS_FILE
        } else {
            validateItemsFile(
                context = context,
                file = itemsFile,
                warnings = warnings,
                unreadableFiles = unreadableFiles,
                missingIconReferences = missingIconReferences
            )
        }

        if (profilesDir == null || !profilesDir.exists() || !profilesDir.isDirectory) {
            missingPaths += AacStoragePaths.PROFILES_DATA_DIR
        } else {
            val profileFiles = profilesDir.listFiles { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }.orEmpty()

            if (profileFiles.isEmpty()) {
                warnings += "No profile JSON files found in ${AacStoragePaths.PROFILES_DATA_DIR}"
            }

            profileFiles.forEach { file ->
                validateProfileFile(
                    context = context,
                    file = file,
                    warnings = warnings,
                    brokenProfileFiles = brokenProfileFiles,
                    unreadableFiles = unreadableFiles,
                    missingIconReferences = missingIconReferences
                )
            }
        }

        if (customIconsDir == null || !customIconsDir.exists() || !customIconsDir.isDirectory) {
            missingPaths += AacStoragePaths.CUSTOM_ICONS_DIR
        }
        if (socaIconsDir == null || !socaIconsDir.exists() || !socaIconsDir.isDirectory) {
            missingPaths += AacStoragePaths.SOCA_ICONS_DIR
        }
        if (arasaacIconsDir == null || !arasaacIconsDir.exists() || !arasaacIconsDir.isDirectory) {
            missingPaths += AacStoragePaths.ARASAAC_ICONS_DIR
        }

        val valid = missingPaths.isEmpty() && brokenProfileFiles.isEmpty() && unreadableFiles.isEmpty()

        return Result(
            valid = valid,
            warnings = warnings.distinct(),
            missingPaths = missingPaths.distinct(),
            brokenProfileFiles = brokenProfileFiles.distinct(),
            unreadableFiles = unreadableFiles.distinct(),
            missingIconReferences = missingIconReferences.toList()
        )
    }

    private fun validateItemsFile(
        context: Context,
        file: File,
        warnings: MutableList<String>,
        unreadableFiles: MutableList<String>,
        missingIconReferences: MutableSet<String>
    ) {
        val root = readJsonRoot(file, unreadableFiles) ?: return
        val itemsArray = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        if (itemsArray.length() == 0) {
            warnings += "AAC items JSON contains no items."
            return
        }

        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim().ifBlank { "item_$index" }
            val imagePath = item.optString("imagePath").trim()
            val iconSource = parseIconSource(item.optString("iconSource"))
            collectMissingIconReference(
                context = context,
                ownerLabel = "item:$itemId",
                imagePath = imagePath,
                iconSource = iconSource,
                warnings = warnings,
                missingIconReferences = missingIconReferences
            )
        }
    }

    private fun validateProfileFile(
        context: Context,
        file: File,
        warnings: MutableList<String>,
        brokenProfileFiles: MutableList<String>,
        unreadableFiles: MutableList<String>,
        missingIconReferences: MutableSet<String>
    ) {
        val root = readJsonRoot(file, unreadableFiles) ?: run {
            brokenProfileFiles += file.name
            return
        }

        val profiles = when (root) {
            is JSONArray -> buildList {
                for (index in 0 until root.length()) {
                    root.optJSONObject(index)?.let(::add)
                }
            }
            is JSONObject -> {
                val profilesArray = root.optJSONArray("profiles")
                if (profilesArray != null) {
                    buildList {
                        for (index in 0 until profilesArray.length()) {
                            profilesArray.optJSONObject(index)?.let(::add)
                        }
                    }
                } else {
                    listOf(root)
                }
            }
            else -> emptyList()
        }

        if (profiles.isEmpty()) {
            brokenProfileFiles += file.name
            warnings += "Profile file ${file.name} contains no valid profiles."
            return
        }

        profiles.forEachIndexed { index, profile ->
            val profileId = profile.optString("id").trim().ifBlank { "${file.name}#$index" }
            val iconPath = profile.optString("icon").trim()
            val iconSource = inferProfileIconSource(iconPath)
            collectMissingIconReference(
                context = context,
                ownerLabel = "profile:$profileId",
                imagePath = iconPath,
                iconSource = iconSource,
                warnings = warnings,
                missingIconReferences = missingIconReferences
            )
        }
    }

    private fun collectMissingIconReference(
        context: Context,
        ownerLabel: String,
        imagePath: String,
        iconSource: IconSource,
        warnings: MutableList<String>,
        missingIconReferences: MutableSet<String>
    ) {
        if (imagePath.isBlank() || iconSource == IconSource.SYSTEM) {
            return
        }

        val resolved = AacStoragePaths.resolveIconFile(context, imagePath, iconSource)
        val exists = resolved?.exists() == true && resolved.isFile
        if (!exists) {
            val warning = "$ownerLabel missing icon: $imagePath"
            warnings += warning
            missingIconReferences += warning
        }
    }

    private fun readJsonRoot(file: File, unreadableFiles: MutableList<String>): Any? {
        return try {
            val json = file.readText(Charsets.UTF_8).trim()
            when {
                json.startsWith("[") -> JSONArray(json)
                json.startsWith("{") -> JSONObject(json)
                else -> {
                    unreadableFiles += file.name
                    null
                }
            }
        } catch (_: Exception) {
            unreadableFiles += file.name
            null
        }
    }

    private fun parseIconSource(value: String?): IconSource {
        return when (value?.trim()?.uppercase()) {
            "SOCA" -> IconSource.SOCA
            "ARASAAC" -> IconSource.ARASAAC
            "PATIENT", "PATIENT_PHOTO" -> IconSource.PATIENT_PHOTO
            "CUSTOM", "CUSTOM_PHOTO" -> IconSource.CUSTOM_PHOTO
            "PLACE", "PLACE_PHOTO" -> IconSource.PLACE_PHOTO
            else -> IconSource.SYSTEM
        }
    }

    private fun inferProfileIconSource(iconPath: String): IconSource {
        val normalized = iconPath.replace('\\', '/').lowercase()
        return when {
            normalized.startsWith("soca/") || normalized.contains("/soca/") -> IconSource.SOCA
            normalized.startsWith("arasaac/") || normalized.contains("/arasaac/") -> IconSource.ARASAAC
            normalized.isBlank() -> IconSource.SYSTEM
            else -> IconSource.CUSTOM_PHOTO
        }
    }
}

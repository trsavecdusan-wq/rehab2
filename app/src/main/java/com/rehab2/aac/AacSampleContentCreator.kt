package com.rehab2.aac

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object AacSampleContentCreator {
    private const val TAG = "AacSampleContentCreator"

    data class Result(
        val createdFiles: List<String>,
        val skippedFiles: List<String>,
        val failed: Boolean
    )

    fun createIfMissing(context: Context): Result {
        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)
        if (
            itemsFile == null ||
            profilesDir == null ||
            customIconsDir == null ||
            socaIconsDir == null ||
            arasaacIconsDir == null
        ) {
            return Result(created, skipped, failed = true)
        }

        return try {
            ensureParentExists(itemsFile.parentFile)
            ensureParentExists(profilesDir)
            ensureParentExists(customIconsDir)
            ensureParentExists(socaIconsDir)
            ensureParentExists(arasaacIconsDir)

            val profileFiles = listOf(
                java.io.File(profilesDir, "dom.json") to buildDomProfileJson(),
                java.io.File(profilesDir, "video_call.json") to buildVideoCallProfileJson(),
                java.io.File(profilesDir, "real_world.json") to buildRealWorldProfileJson()
            )

            if (itemsFile.exists()) {
                Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${itemsFile.absolutePath}")
                skipped += itemsFile.absolutePath
            } else {
                itemsFile.writeText(buildItemsJson().toString(2), Charsets.UTF_8)
                Log.d(TAG, "AAC_SAMPLE CREATED_ITEMS_JSON path=${itemsFile.absolutePath}")
                created += itemsFile.absolutePath
            }

            profileFiles.forEach { (profileFile, profileJson) ->
                if (profileFile.exists()) {
                    Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${profileFile.absolutePath}")
                    skipped += profileFile.absolutePath
                } else {
                    ensureParentExists(profileFile.parentFile)
                    profileFile.writeText(profileJson.toString(2), Charsets.UTF_8)
                    Log.d(TAG, "AAC_SAMPLE CREATED_PROFILE_JSON path=${profileFile.absolutePath}")
                    created += profileFile.absolutePath
                }
            }

            Result(created, skipped, failed = false)
        } catch (error: Exception) {
            Log.w(TAG, "AAC_SAMPLE CREATE_FAILED", error)
            Result(created, skipped, failed = true)
        }
    }

    private fun ensureParentExists(dir: java.io.File?) {
        if (dir == null) throw IllegalStateException("Storage directory is unavailable.")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Could not create directory: ${dir.absolutePath}")
        }
    }

    private fun buildItemsJson(): JSONObject {
        return JSONObject().put(
            "items",
            JSONArray()
                .put(
                    sampleItem(
                        id = "yes",
                        label = "DA",
                        imagePath = "",
                        isRootItem = true,
                        priority = 0
                    )
                )
                .put(
                    sampleItem(
                        id = "no",
                        label = "NE",
                        imagePath = "",
                        isRootItem = true,
                        priority = 1
                    )
                )
                .put(
                    sampleItem(
                        id = "help",
                        label = "POMOČ",
                        imagePath = "",
                        isRootItem = true,
                        priority = 2
                    )
                )
                .put(
                    sampleItem(
                        id = "pain",
                        label = "BOLI",
                        imagePath = "",
                        isRootItem = true,
                        priority = 3
                    )
                )
                .put(
                    sampleItem(
                        id = "thirsty",
                        label = "ŽEJNA",
                        imagePath = "",
                        conceptId = "thirsty",
                        isRootItem = true,
                        priority = 4,
                        followUpQuestion = "Kaj želiš piti?"
                    ).put(
                        "children",
                        JSONArray()
                            .put("water")
                            .put("juice")
                            .put("coffee")
                            .put("tea")
                    )
                )
                .put(
                    sampleItem(
                        id = "water",
                        label = "VODA",
                        imagePath = "custom/water.png",
                        conceptId = "water",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 10,
                        vendingNumber = "12"
                    )
                )
                .put(
                    sampleItem(
                        id = "juice",
                        label = "SOK",
                        imagePath = "custom/juice.png",
                        conceptId = "juice",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 11,
                        vendingNumber = "14"
                    )
                )
                .put(
                    sampleItem(
                        id = "coffee",
                        label = "KAVA",
                        imagePath = "custom/coffee.png",
                        conceptId = "coffee",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 12,
                        vendingNumber = "21"
                    )
                )
                .put(
                    sampleItem(
                        id = "tea",
                        label = "ČAJ",
                        imagePath = "custom/tea.png",
                        conceptId = "tea",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 13,
                        vendingNumber = "22"
                    )
                )
        )
    }

    private fun buildDomProfileJson(): JSONObject {
        return buildProfileJson(
            id = "dom",
            displayName = "DOM",
            icon = "custom/dom.png",
            context = AacCommunicationContext.NORMAL_COMMUNICATION,
            itemIds = listOf("yes", "no", "help", "pain", "thirsty")
        )
    }

    private fun buildVideoCallProfileJson(): JSONObject {
        return buildProfileJson(
            id = "video_call",
            displayName = "VIDEO CALL",
            icon = "arasaac/video_call.png",
            context = AacCommunicationContext.VIDEO_CALL_COMMUNICATION,
            itemIds = listOf("yes", "no", "help", "pain")
        )
    }

    private fun buildRealWorldProfileJson(): JSONObject {
        return buildProfileJson(
            id = "real_world",
            displayName = "REAL WORLD",
            icon = "soca/real_world.png",
            context = AacCommunicationContext.REAL_WORLD_ASSISTANT,
            itemIds = listOf("thirsty", "help", "pain")
        )
    }

    private fun buildProfileJson(
        id: String,
        displayName: String,
        icon: String,
        context: AacCommunicationContext,
        itemIds: List<String>
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("icon", icon)
            .put("context", context.name)
            .put("itemIds", JSONArray().apply {
                itemIds.forEach { put(it) }
            })
            .put("enabled", true)
    }

    private fun sampleItem(
        id: String,
        label: String,
        imagePath: String,
        conceptId: String? = null,
        parentId: String? = null,
        isRootItem: Boolean,
        isHiddenUntilParent: Boolean = false,
        priority: Int,
        followUpQuestion: String? = null,
        vendingNumber: String? = null
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("labelSl", label)
            .put("text", label)
            .put("speechText", label.lowercase())
            .put("imagePath", imagePath)
            .put("iconSource", inferIconSourceName(imagePath))
            .put("actionType", "speak")
            .put("targetPageId", "")
            .put("conceptId", conceptId)
            .put("parentId", parentId)
            .put("isRootItem", isRootItem)
            .put("isHiddenUntilParent", isHiddenUntilParent)
            .put("priority", priority)
            .apply {
                if (!followUpQuestion.isNullOrBlank()) {
                    put("followUpQuestion", followUpQuestion)
                }
                if (!vendingNumber.isNullOrBlank()) {
                    put("vendingNumber", vendingNumber)
                }
            }
    }

    private fun inferIconSourceName(imagePath: String): String {
        val normalized = imagePath.trim().replace('\\', '/').lowercase()
        return when {
            normalized.isBlank() -> "SYSTEM"
            normalized.startsWith("soca/") || normalized.contains("/soca/") -> "SOCA"
            normalized.startsWith("arasaac/") || normalized.contains("/arasaac/") -> "ARASAAC"
            else -> "CUSTOM"
        }
    }
}

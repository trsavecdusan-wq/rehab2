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
        if (itemsFile == null || profilesDir == null) {
            return Result(created, skipped, failed = true)
        }

        return try {
            ensureParentExists(itemsFile.parentFile)
            ensureParentExists(profilesDir)

            val domProfileFile = java.io.File(profilesDir, "dom.json")

            if (itemsFile.exists()) {
                Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${itemsFile.absolutePath}")
                skipped += itemsFile.absolutePath
            } else {
                itemsFile.writeText(buildItemsJson().toString(2), Charsets.UTF_8)
                Log.d(TAG, "AAC_SAMPLE CREATED_ITEMS_JSON path=${itemsFile.absolutePath}")
                created += itemsFile.absolutePath
            }

            if (domProfileFile.exists()) {
                Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${domProfileFile.absolutePath}")
                skipped += domProfileFile.absolutePath
            } else {
                ensureParentExists(domProfileFile.parentFile)
                domProfileFile.writeText(buildDomProfileJson().toString(2), Charsets.UTF_8)
                Log.d(TAG, "AAC_SAMPLE CREATED_PROFILE_JSON path=${domProfileFile.absolutePath}")
                created += domProfileFile.absolutePath
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
        return JSONObject()
            .put("id", "dom")
            .put("displayName", "DOM")
            .put("context", AacCommunicationContext.NORMAL_COMMUNICATION.name)
            .put("itemIds", JSONArray().put("yes").put("no").put("help").put("pain").put("thirsty"))
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
            .put("iconSource", if (imagePath.isBlank()) "SYSTEM" else "CUSTOM")
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
}

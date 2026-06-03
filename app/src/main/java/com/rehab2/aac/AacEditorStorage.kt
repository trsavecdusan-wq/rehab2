package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AacEditorStorage {
    data class EditorPage(
        val pageId: String,
        val title: String,
        val items: List<AacItem>
    )

    fun loadPages(context: Context): List<EditorPage> {
        val items = AacLocalJsonLoader.loadItems(context, AacStarterContentV1.items())
        val pages = items
            .flatMap { item -> item.placements.map { placement -> placement.pageId } }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy(::pageSortKey, { it }))

        return pages.map { pageId ->
            EditorPage(
                pageId = pageId,
                title = pageTitle(pageId),
                items = items
                    .filter { item -> item.placements.any { it.pageId == pageId } }
                    .sortedBy { item -> item.placements.firstOrNull { it.pageId == pageId }?.position5x5 ?: Int.MAX_VALUE }
            )
        }
    }

    fun updateLabelSl(context: Context, itemId: String, labelSl: String): Boolean {
        if (labelSl.isBlank()) return false
        return updateItem(context, itemId) { item ->
            item.put("labelSl", labelSl.trim())
        }
    }

    fun updateSpeechTextSl(context: Context, itemId: String, speechTextSl: String): Boolean {
        if (speechTextSl.isBlank()) return false
        return updateItem(context, itemId) { item ->
            val normalized = speechTextSl.trim()
            item.put("speakTextSl", normalized)
            item.put("speechText", normalized)
            val speechTextByLanguage = JSONObject(item.optJSONObject("speechTextByLanguage")?.toString() ?: "{}")
            speechTextByLanguage.put("sl", normalized)
            item.put("speechTextByLanguage", speechTextByLanguage)
        }
    }

    fun updateImage(context: Context, itemId: String, iconSource: IconSource, imagePath: String): Boolean {
        if (imagePath.isBlank()) return false
        return updateItem(context, itemId) { item ->
            item.put("iconSource", iconSource.name)
            item.put("imagePath", imagePath.trim())
        }
    }

    private fun updateItem(context: Context, itemId: String, mutate: (JSONObject) -> Unit): Boolean {
        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return false

        return try {
            val storedJson = readStoredItemsJson(itemsFile)
            val itemsArray = storedJson.itemsArray
            var updated = false
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (item.optString("id").trim() != itemId) continue
                mutate(item)
                item.put("userEdited", true)
                updated = true
                break
            }
            if (!updated) return false

            val nextJson = storedJson.toJsonText()
            itemsFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
            itemsFile.writeText(nextJson, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readStoredItemsJson(itemsFile: java.io.File): StoredItemsJson {
        if (itemsFile.exists() && itemsFile.isFile) {
            try {
                val raw = itemsFile.readText(Charsets.UTF_8).trim()
                if (raw.startsWith("[")) {
                    val itemsArray = JSONArray(raw)
                    return StoredItemsJson(rootObject = null, itemsArray = itemsArray)
                }
                if (raw.startsWith("{")) {
                    val rootObject = JSONObject(raw)
                    return StoredItemsJson(
                        rootObject = rootObject,
                        itemsArray = rootObject.optJSONArray("items") ?: JSONArray()
                    )
                }
            } catch (_: Exception) {
                // Fall through to a safe starter-content file.
            }
        }
        return StoredItemsJson(
            rootObject = JSONObject().put("items", starterItemsJson()),
            itemsArray = starterItemsJson()
        )
    }

    private fun starterItemsJson(): JSONArray {
        return JSONArray().apply {
            AacStarterContentV1.items().forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("labelSl", item.labelSl)
                    .put("imagePath", item.imagePath)
                    .put("audioSl", item.audioSl)
                    .put("actionType", item.actionType)
                    .put("targetPageId", item.targetPageId)
                    .put("speakTextSl", item.speakTextSl ?: item.resolvedSpeechText)
                    .put("speechText", item.speechText ?: item.resolvedSpeechText)
                    .put("iconSource", item.iconSource.name)
                    .put("isRootItem", item.isRootItem)
                    .put("isHiddenUntilParent", item.isHiddenUntilParent)
                    .put("addsToSentence", item.addsToSentence)
                    .put("speaksImmediately", item.speaksImmediately)
                    .put("opensSubicons", item.opensSubicons)
                    .put("priority", item.priority)
                    .also { json ->
                        item.categoryId?.let { json.put("categoryId", it) }
                        item.conceptId?.let { json.put("conceptId", it) }
                        item.parentId?.let { json.put("parentId", it) }
                        item.fixedTopRowPosition?.let { json.put("fixedTopRowPosition", it) }
                        if (item.children.isNotEmpty()) json.put("children", jsonArrayOf(item.children))
                        if (item.visibleUnderIds.isNotEmpty()) json.put("visibleUnderIds", jsonArrayOf(item.visibleUnderIds))
                        if (item.questionByLanguage.isNotEmpty()) json.put("questionByLanguage", JSONObject(item.questionByLanguage))
                        if (item.placements.isNotEmpty()) {
                            json.put("placements", JSONArray().apply {
                                item.placements.forEach { placement ->
                                    put(JSONObject()
                                        .put("pageId", placement.pageId)
                                        .put("position5x5", placement.position5x5)
                                    )
                                }
                            })
                        }
                    }
                )
            }
        }
    }

    private fun jsonArrayOf(values: List<String>): JSONArray {
        return JSONArray().apply {
            values.forEach { value -> put(value) }
        }
    }

    private data class StoredItemsJson(
        val rootObject: JSONObject?,
        val itemsArray: JSONArray
    ) {
        fun toJsonText(): String {
            return if (rootObject != null) {
                rootObject.put("items", itemsArray)
                rootObject.toString(2)
            } else {
                itemsArray.toString(2)
            }
        }
    }

    private fun pageTitle(pageId: String): String {
        val number = pageId.removePrefix("page_").toIntOrNull()
        return if (number != null) "STRAN $number" else pageId.uppercase()
    }

    private fun pageSortKey(pageId: String): Int {
        return pageId.removePrefix("page_").toIntOrNull() ?: Int.MAX_VALUE
    }
}

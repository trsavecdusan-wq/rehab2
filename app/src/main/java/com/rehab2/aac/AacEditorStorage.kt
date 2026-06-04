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

    data class NewIcon(
        val id: String,
        val parentId: String,
        val labelSl: String,
        val speechTextSl: String,
        val iconSource: IconSource,
        val imagePath: String
    )

    data class CopyIcon(
        val sourceId: String,
        val newId: String,
        val labelSl: String,
        val speechTextSl: String,
        val iconSource: IconSource,
        val imagePath: String
    )

    data class CoreRepairItem(
        val id: String,
        val labelSl: String,
        val speechTextSl: String
    )

    fun loadPages(context: Context): List<EditorPage> {
        val items = loadItems(context)
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

    fun loadItems(context: Context): List<AacItem> {
        return AacLocalJsonLoader.loadItems(context, AacStarterContentV1.items())
    }

    fun hiddenItemIds(context: Context): Set<String> {
        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return emptySet()
        return try {
            itemObjects(readStoredItemsJson(itemsFile).itemsArray)
                .filter { it.optBoolean("hidden", false) }
                .mapNotNull { it.optString("id").trim().takeIf(String::isNotBlank) }
                .toSet()
        } catch (_: Exception) {
            emptySet()
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

    fun updateChildren(context: Context, itemId: String, children: List<String>): Boolean {
        return updateItem(context, itemId) { item ->
            item.put("children", jsonArrayOf(children.distinct()))
        }
    }

    fun addNewChildIcon(context: Context, newIcon: NewIcon): Boolean {
        val itemId = newIcon.id.trim()
        val parentId = newIcon.parentId.trim()
        val labelSl = newIcon.labelSl.trim()
        val speechTextSl = newIcon.speechTextSl.trim()
        if (itemId.isBlank() || parentId.isBlank() || labelSl.isBlank() || speechTextSl.isBlank()) return false

        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return false
        return try {
            val storedJson = readStoredItemsJson(itemsFile)
            val itemsArray = storedJson.itemsArray
            val items = itemObjects(itemsArray)
            if (items.any { it.optString("id").trim() == itemId }) return false
            val parent = items.firstOrNull { it.optString("id").trim() == parentId } ?: return false
            val parentChildren = stringList(parent.optJSONArray("children"))
            if (itemId in parentChildren) return false

            val newItem = JSONObject()
                .put("id", itemId)
                .put("labelSl", labelSl)
                .put("speakTextSl", speechTextSl)
                .put("speechText", speechTextSl)
                .put("speechTextByLanguage", JSONObject().put("sl", speechTextSl))
                .put("actionType", "speak")
                .put("iconSource", newIcon.iconSource.name)
                .put("imagePath", newIcon.imagePath.trim())
                .put("isRootItem", false)
                .put("isHiddenUntilParent", false)
                .put("parentId", parentId)
                .put("visibleUnderIds", jsonArrayOf(listOf(parentId)))
                .put("children", JSONArray())
                .put("addsToSentence", true)
                .put("speaksImmediately", true)
                .put("opensSubicons", false)
                .put("meaningId", "custom.$itemId")
                .put("meaningType", "CUSTOM")
                .put("meaningGroup", "custom")
                .put("semanticTags", jsonArrayOf(listOf(labelSl.lowercase(), itemId)))
                .put("searchKeywordsByLanguage", JSONObject().put("sl", jsonArrayOf(listOf(labelSl.lowercase(), itemId))))
                .put("userEdited", true)

            parent.put("children", jsonArrayOf(parentChildren + itemId))
            parent.put("userEdited", true)
            itemsArray.put(newItem)

            itemsFile.parentFile?.let { parentDir -> if (!parentDir.exists()) parentDir.mkdirs() }
            itemsFile.writeText(storedJson.toJsonText(), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun copyIconAsSibling(context: Context, copyIcon: CopyIcon): Boolean {
        val sourceId = copyIcon.sourceId.trim()
        val itemId = copyIcon.newId.trim()
        val labelSl = copyIcon.labelSl.trim()
        val speechTextSl = copyIcon.speechTextSl.trim()
        if (sourceId.isBlank() || itemId.isBlank() || labelSl.isBlank() || speechTextSl.isBlank()) return false

        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return false
        return try {
            val storedJson = readStoredItemsJson(itemsFile)
            val itemsArray = storedJson.itemsArray
            val items = itemObjects(itemsArray)
            if (items.any { it.optString("id").trim() == itemId }) return false
            val source = items.firstOrNull { it.optString("id").trim() == sourceId } ?: return false
            val parent = items.firstOrNull { item -> sourceId in stringList(item.optJSONArray("children")) } ?: return false
            val parentChildren = stringList(parent.optJSONArray("children"))
            if (itemId in parentChildren) return false

            val newItem = JSONObject(source.toString())
            newItem.put("id", itemId)
            newItem.put("labelSl", labelSl)
            newItem.put("speakTextSl", speechTextSl)
            newItem.put("speechText", speechTextSl)
            newItem.put("speechTextByLanguage", JSONObject().put("sl", speechTextSl))
            newItem.put("iconSource", copyIcon.iconSource.name)
            newItem.put("imagePath", copyIcon.imagePath.trim())
            newItem.put("children", JSONArray())
            newItem.put("visibleUnderIds", jsonArrayOf(listOf(parent.optString("id").trim())))
            newItem.put("isRootItem", false)
            newItem.put("opensSubicons", false)
            newItem.put("speaksImmediately", true)
            newItem.put("actionType", "speak")
            newItem.put("targetPageId", "")
            newItem.put("parentId", parent.optString("id").trim())
            newItem.put("userEdited", true)
            newItem.remove("placements")
            newItem.remove("fixedTopRowPosition")
            newItem.remove("hidden")

            parent.put("children", jsonArrayOf(parentChildren + itemId))
            parent.put("userEdited", true)
            itemsArray.put(newItem)

            itemsFile.parentFile?.let { parentDir -> if (!parentDir.exists()) parentDir.mkdirs() }
            itemsFile.writeText(storedJson.toJsonText(), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setHidden(context: Context, itemId: String, hidden: Boolean): Boolean {
        val protectedIds = setOf(
            "no",
            "yes",
            "dont_understand",
            "thank_you",
            "sorry",
            "people",
            "need",
            "problem",
            "thirsty",
            "hungry",
            "pain",
            "wc"
        )
        val normalizedId = itemId.trim()
        if (hidden && normalizedId in protectedIds) return false

        val items = loadItems(context)
        val item = items.firstOrNull { it.id == normalizedId } ?: return false
        if (hidden && item.placements.isNotEmpty()) {
            val pageIds = item.placements.map { it.pageId }.toSet()
            pageIds.forEach { pageId ->
                val visibleOnPage = items
                    .filter { candidate -> candidate.id != normalizedId && candidate.placements.any { it.pageId == pageId } }
                    .filterNot { candidate -> candidate.id in hiddenItemIds(context) }
                if (visibleOnPage.isEmpty()) return false
            }
        }

        return updateItem(context, normalizedId) { json ->
            json.put("hidden", hidden)
        }
    }

    fun rawItemsForAudit(context: Context): JSONArray {
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        return try {
            if (itemsFile != null) {
                readStoredItemsJson(itemsFile).itemsArray
            } else {
                starterItemsJson()
            }
        } catch (_: Exception) {
            starterItemsJson()
        }
    }

    fun addMissingCoreStarterItems(context: Context, repairItems: Collection<CoreRepairItem>): Int {
        val requestedItems = repairItems
            .map { it.copy(id = it.id.trim(), labelSl = it.labelSl.trim(), speechTextSl = it.speechTextSl.trim()) }
            .filter { it.id.isNotBlank() && it.labelSl.isNotBlank() && it.speechTextSl.isNotBlank() }
            .distinctBy { it.id }
        if (requestedItems.isEmpty()) return 0

        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return 0
        return try {
            val storedJson = readStoredItemsJson(itemsFile)
            val itemsArray = storedJson.itemsArray
            val items = itemObjects(itemsArray)
            val existingIds = items
                .map { it.optString("id").trim() }
                .toSet()
            val occupiedFixedPositions = items
                .mapNotNull { item ->
                    item.optInt("fixedTopRowPosition", 0)
                        .takeIf { it in 1..5 }
                }
                .toMutableSet()
            val occupiedPageOnePositions = occupiedPageOnePositions(itemsArray).toMutableSet()
            val starterItemsById = AacStarterContentV1.items()
                .filter { starterItem -> requestedItems.any { it.id == starterItem.id } }
                .associateBy { it.id }
            var addedCount = 0

            requestedItems.forEach { repairItem ->
                if (repairItem.id in existingIds) return@forEach
                val starterItem = starterItemsById[repairItem.id] ?: return@forEach
                val itemJson = starterItemJson(starterItem)
                itemJson.put("labelSl", repairItem.labelSl)
                itemJson.put("speakTextSl", repairItem.speechTextSl)
                itemJson.put("speechText", repairItem.speechTextSl)
                itemJson.put("speechTextByLanguage", JSONObject().put("sl", repairItem.speechTextSl))

                val fixedTopRowPosition = itemJson.optInt("fixedTopRowPosition", 0)
                if (fixedTopRowPosition in 1..5) {
                    if (fixedTopRowPosition in occupiedFixedPositions) {
                        itemJson.remove("fixedTopRowPosition")
                    } else {
                        occupiedFixedPositions += fixedTopRowPosition
                    }
                }
                if (itemJson.optInt("fixedTopRowPosition", 0) !in 1..5) {
                    placeOnFirstPageIfSpaceExists(itemJson, occupiedPageOnePositions)
                }

                itemsArray.put(itemJson)
                addedCount += 1
            }

            if (addedCount <= 0) return 0
            itemsFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
            itemsFile.writeText(storedJson.toJsonText(), Charsets.UTF_8)
            addedCount
        } catch (_: Exception) {
            0
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
                put(starterItemJson(item))
            }
        }
    }

    private fun starterItemJson(item: AacItem): JSONObject {
        return JSONObject()
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
    }

    private fun jsonArrayOf(values: List<String>): JSONArray {
        return JSONArray().apply {
            values.forEach { value -> put(value) }
        }
    }

    private fun itemObjects(itemsArray: JSONArray): List<JSONObject> {
        return buildList {
            for (index in 0 until itemsArray.length()) {
                itemsArray.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun occupiedPageOnePositions(itemsArray: JSONArray): Set<Int> {
        return itemObjects(itemsArray).flatMap { item ->
            val placements = item.optJSONArray("placements") ?: return@flatMap emptyList()
            buildList {
                for (index in 0 until placements.length()) {
                    val placement = placements.optJSONObject(index) ?: continue
                    if (placement.optString("pageId").trim() == "page_1") {
                        val position = placement.optInt("position5x5", 0)
                        if (position in 1..25) add(position)
                    }
                }
            }
        }.toSet()
    }

    private fun placeOnFirstPageIfSpaceExists(item: JSONObject, occupiedPositions: MutableSet<Int>) {
        val currentPlacements = item.optJSONArray("placements")
        val nextPlacements = JSONArray()
        var hasSafePageOnePlacement = false
        if (currentPlacements != null) {
            for (index in 0 until currentPlacements.length()) {
                val placement = currentPlacements.optJSONObject(index) ?: continue
                if (placement.optString("pageId").trim() != "page_1") {
                    nextPlacements.put(placement)
                    continue
                }
                val position = placement.optInt("position5x5", 0)
                if (position in 1..25 && position !in occupiedPositions && !hasSafePageOnePlacement) {
                    nextPlacements.put(placement)
                    occupiedPositions += position
                    hasSafePageOnePlacement = true
                }
            }
        }
        if (!hasSafePageOnePlacement) {
            firstOpenPageOnePosition(occupiedPositions)?.let { position ->
                nextPlacements.put(JSONObject().put("pageId", "page_1").put("position5x5", position))
                occupiedPositions += position
            }
        }
        if (nextPlacements.length() > 0) {
            item.put("placements", nextPlacements)
        } else {
            item.remove("placements")
        }
    }

    private fun firstOpenPageOnePosition(occupiedPositions: Set<Int>): Int? {
        return (1..25).firstOrNull { it !in occupiedPositions }
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

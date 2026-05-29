package com.rehab2.aac

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacContentBootstrap {
    private const val TAG = "AacContentBootstrap"
    private const val PATIENT_PAGE_PREFS_NAME = "aac_patient_pages"
    private const val KEY_PATIENT_PAGES = "patient_pages"
    private const val KEY_DEFAULT_PATIENT_PAGE_ID = "default_patient_page_id"
    private const val PATIENT_PAGE_SEPARATOR = "\u001E"
    private const val PATIENT_PAGE_FIELD_SEPARATOR = "\u001F"
    private const val DEFAULT_PAGE_ID = "page_1"
    private const val DEFAULT_PAGE_TITLE = "STRAN 1"
    private const val DOM_PROFILE_ID = "dom"
    private const val DOM_PROFILE_FILE = "dom.json"

    data class Result(
        val itemCount: Int,
        val existingPageCount: Int,
        val createdDefaultPage: Boolean,
        val defaultPageId: String,
        val addedPlacements: Int,
        val domProfileLinkedItemCount: Int,
        val domProfileUpdated: Boolean,
        val fixedRowCount: Int,
        val visibleNormalItemCount: Int,
        val skipped: Boolean,
        val reason: String
    )

    fun ensurePatientStartupContent(context: Context, fallbackItems: List<AacItem>): Result {
        AacStoragePaths.ensureAacContentDirs(context)
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val rawItems = loadItemsJson(itemsFile, fallbackItems)
        val itemsArray = rawItems.itemsArray
        val itemCount = itemsArray.length()
        if (itemCount == 0) {
            return Result(
                itemCount = 0,
                existingPageCount = currentPatientPages(context).size,
                createdDefaultPage = false,
                defaultPageId = "",
                addedPlacements = 0,
                domProfileLinkedItemCount = 0,
                domProfileUpdated = false,
                fixedRowCount = 0,
                visibleNormalItemCount = 0,
                skipped = true,
                reason = "no_aac_items"
            )
        }

        val existingPages = currentPatientPages(context)
        val createdDefaultPage = existingPages.isEmpty()
        if (createdDefaultPage) {
            savePatientPages(context, listOf(DEFAULT_PAGE_ID to DEFAULT_PAGE_TITLE))
            setDefaultPatientPage(context, DEFAULT_PAGE_ID)
        } else if (currentDefaultPatientPage(context).isBlank()) {
            setDefaultPatientPage(context, existingPages.first().first)
        }
        val defaultPageId = currentDefaultPatientPage(context).ifBlank { DEFAULT_PAGE_ID }
        val addedPlacements = if (createdDefaultPage) {
            addDefaultPagePlacements(itemsArray, defaultPageId)
        } else {
            0
        }
        if (createdDefaultPage && addedPlacements > 0) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        } else if (itemsFile?.exists() != true && rawItems.createdFromFallback) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        }

        val pageItemIds = itemIdsOnPage(itemsArray, defaultPageId)
        val domProfileResult = ensureDomProfileLinked(context, pageItemIds.ifEmpty { rootItemIds(itemsArray) })
        val fixedRowCount = fixedRowItemIds(itemsArray).size
        val visibleNormalItemCount = pageItemIds.filter { it !in fixedRowItemIds(itemsArray) }.size

        val result = Result(
            itemCount = itemCount,
            existingPageCount = existingPages.size,
            createdDefaultPage = createdDefaultPage,
            defaultPageId = defaultPageId,
            addedPlacements = addedPlacements,
            domProfileLinkedItemCount = domProfileResult.linkedItemCount,
            domProfileUpdated = domProfileResult.updated,
            fixedRowCount = fixedRowCount,
            visibleNormalItemCount = visibleNormalItemCount,
            skipped = false,
            reason = if (createdDefaultPage) "bootstrap_created" else "pages_already_exist"
        )
        Log.d(
            TAG,
            "AAC_BOOTSTRAP defaultPage=${result.defaultPageId} items=${result.itemCount} normalVisible=${result.visibleNormalItemCount} fixed=${result.fixedRowCount} placementsAdded=${result.addedPlacements} domLinked=${result.domProfileLinkedItemCount} reason=${result.reason}"
        )
        return result
    }

    private fun addDefaultPagePlacements(itemsArray: JSONArray, pageId: String): Int {
        val occupiedPositions = occupiedPositions(itemsArray, pageId).toMutableSet()
        val fixedIds = fixedRowItemIds(itemsArray)
        val candidates = rootItemObjects(itemsArray)
            .filter { item -> item.optString("id").trim() !in fixedIds }
            .filterNot { item -> itemAlreadyOnPage(item, pageId) }
        val freePositions = (6..25).filter { it !in occupiedPositions }
        var added = 0
        candidates.zip(freePositions).forEach { (item, position) ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            placements.put(JSONObject().put("pageId", pageId).put("position5x5", position))
            item.put("placements", placements)
            occupiedPositions += position
            added++
        }
        return added
    }

    private fun ensureDomProfileLinked(context: Context, itemIds: List<String>): ProfileBootstrapResult {
        val safeItemIds = itemIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (safeItemIds.isEmpty()) {
            return ProfileBootstrapResult(linkedItemCount = 0, updated = false)
        }
        val profilesDir = AacStoragePaths.getProfilesDataDir(context) ?: return ProfileBootstrapResult(0, false)
        if (!profilesDir.exists() && !profilesDir.mkdirs()) {
            return ProfileBootstrapResult(0, false)
        }
        val profileFile = File(profilesDir, DOM_PROFILE_FILE)
        val profileJson = readProfileJson(profileFile) ?: JSONObject()
            .put("id", DOM_PROFILE_ID)
            .put("displayName", "DOM")
            .put("context", AacCommunicationContext.NORMAL_COMMUNICATION.name)
            .put("enabled", true)
        val existingItemIds = stringList(profileJson.optJSONArray("itemIds"))
        if (existingItemIds.isNotEmpty()) {
            return ProfileBootstrapResult(linkedItemCount = existingItemIds.size, updated = false)
        }
        profileJson.put("itemIds", JSONArray().apply { safeItemIds.forEach { itemId -> put(itemId) } })
        profileFile.writeText(profileJson.toString(2), Charsets.UTF_8)
        return ProfileBootstrapResult(linkedItemCount = safeItemIds.size, updated = true)
    }

    private fun loadItemsJson(itemsFile: File?, fallbackItems: List<AacItem>): RawItemsJson {
        if (itemsFile?.isFile == true) {
            return try {
                val raw = itemsFile.readText(Charsets.UTF_8).trim()
                val root = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
                val itemsArray = when (root) {
                    is JSONArray -> root
                    is JSONObject -> root.optJSONArray("items") ?: JSONArray()
                    else -> JSONArray()
                }
                RawItemsJson(root = root, itemsArray = itemsArray, createdFromFallback = false)
            } catch (error: Exception) {
                Log.w(TAG, "AAC_BOOTSTRAP_ITEMS_READ_FAILED", error)
                fallbackRawItems(fallbackItems)
            }
        }
        return fallbackRawItems(fallbackItems)
    }

    private fun fallbackRawItems(fallbackItems: List<AacItem>): RawItemsJson {
        val itemsArray = JSONArray().apply {
            fallbackItems.forEach { item -> put(item.toBootstrapJson()) }
        }
        return RawItemsJson(root = JSONObject().put("items", itemsArray), itemsArray = itemsArray, createdFromFallback = true)
    }

    private fun saveItemsJson(itemsFile: File?, rawItems: RawItemsJson, itemsArray: JSONArray) {
        val file = itemsFile ?: return
        file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        val output = when (val root = rawItems.root) {
            is JSONArray -> itemsArray
            is JSONObject -> root.put("items", itemsArray)
            else -> JSONObject().put("items", itemsArray)
        }
        file.writeText(output.toString(2), Charsets.UTF_8)
    }

    private fun currentPatientPages(context: Context): List<Pair<String, String>> {
        val encoded = context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PATIENT_PAGES, "")
            .orEmpty()
        return encoded.split(PATIENT_PAGE_SEPARATOR)
            .mapNotNull { encodedPage ->
                val parts = encodedPage.split(PATIENT_PAGE_FIELD_SEPARATOR)
                val pageId = parts.getOrNull(0).orEmpty().trim()
                val title = parts.getOrNull(1).orEmpty().trim().ifBlank { pageId }
                if (isSafePageId(pageId)) pageId to title else null
            }
            .distinctBy { it.first }
    }

    private fun savePatientPages(context: Context, pages: List<Pair<String, String>>) {
        val encoded = pages
            .filter { isSafePageId(it.first) }
            .joinToString(PATIENT_PAGE_SEPARATOR) { (pageId, title) ->
                pageId + PATIENT_PAGE_FIELD_SEPARATOR + title
            }
        context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATIENT_PAGES, encoded)
            .apply()
    }

    private fun setDefaultPatientPage(context: Context, pageId: String) {
        context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_PATIENT_PAGE_ID, pageId)
            .apply()
    }

    private fun currentDefaultPatientPage(context: Context): String {
        return context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_PATIENT_PAGE_ID, "")
            .orEmpty()
            .trim()
            .takeIf(::isSafePageId)
            .orEmpty()
    }

    private fun rootItemObjects(itemsArray: JSONArray): List<JSONObject> {
        return itemObjects(itemsArray)
            .filter { item ->
                val hasParent = item.optString("parentId").trim().isNotBlank() ||
                    (item.optJSONArray("visibleUnderIds")?.length() ?: 0) > 0 ||
                    (item.optJSONArray("parentIds")?.length() ?: 0) > 0
                val isRoot = if (item.has("isRootItem")) item.optBoolean("isRootItem", true) else !hasParent
                isRoot && !item.optBoolean("isHiddenUntilParent", false)
            }
            .sortedWith(compareBy<JSONObject> { it.optInt("priority", Int.MAX_VALUE) }.thenBy { it.optString("id") })
    }

    private fun itemObjects(itemsArray: JSONArray): List<JSONObject> {
        return buildList {
            for (index in 0 until itemsArray.length()) {
                itemsArray.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun rootItemIds(itemsArray: JSONArray): List<String> {
        return rootItemObjects(itemsArray).map { it.optString("id").trim() }.filter { it.isNotBlank() }
    }

    private fun itemIdsOnPage(itemsArray: JSONArray, pageId: String): List<String> {
        return itemObjects(itemsArray).filter { itemAlreadyOnPage(it, pageId) }
            .map { it.optString("id").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun occupiedPositions(itemsArray: JSONArray, pageId: String): Set<Int> {
        return itemObjects(itemsArray).flatMap { item ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            buildList {
                for (index in 0 until placements.length()) {
                    val placement = placements.optJSONObject(index) ?: continue
                    if (placement.optString("pageId").trim() == pageId) {
                        val position = placement.optInt("position5x5", 0)
                        if (position in 1..25) add(position)
                    }
                }
            }
        }.toSet()
    }

    private fun itemAlreadyOnPage(item: JSONObject, pageId: String): Boolean {
        val placements = item.optJSONArray("placements") ?: return false
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (placement.optString("pageId").trim() == pageId && placement.optInt("position5x5", 0) in 1..25) {
                return true
            }
        }
        return false
    }

    private fun fixedRowItemIds(itemsArray: JSONArray): Set<String> {
        return itemObjects(itemsArray)
            .filter { item -> item.optInt("fixedTopRowPosition", 0) in 1..5 }
            .map { it.optString("id").trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun readProfileJson(profileFile: File): JSONObject? {
        if (!profileFile.isFile) return null
        return try {
            JSONObject(profileFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "AAC_BOOTSTRAP_PROFILE_READ_FAILED file=${profileFile.name}", error)
            null
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun isSafePageId(pageId: String): Boolean {
        return pageId.isNotBlank() && pageId.matches(Regex("[A-Za-z0-9_-]+"))
    }

    private fun AacItem.toBootstrapJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("labelSl", labelSl)
            .put("imagePath", imagePath)
            .put("audioSl", audioSl)
            .put("actionType", actionType)
            .put("targetPageId", targetPageId)
            .put("speakTextSl", speakTextSl ?: resolvedSpeechText)
            .put("speechText", speechText ?: resolvedSpeechText)
            .put("iconSource", iconSource.name)
            .put("isRootItem", isRootItem)
            .put("isHiddenUntilParent", isHiddenUntilParent)
            .put("addsToSentence", addsToSentence)
            .put("speaksImmediately", speaksImmediately)
            .put("opensSubicons", opensSubicons)
            .put("priority", priority)
            .also { json ->
                labelUk?.let { json.put("labelUk", it) }
                labelEn?.let { json.put("labelEn", it) }
                speakTextUk?.let { json.put("speakTextUk", it) }
                speechTextEn?.let { json.put("speechTextEn", it) }
                categoryId?.let { json.put("categoryId", it) }
                if (scenarioIds.isNotEmpty()) json.put("scenarioIds", JSONArray(scenarioIds))
                conceptId?.let { json.put("conceptId", it) }
                parentId?.let { json.put("parentId", it) }
                fixedTopRowPosition?.let { json.put("fixedTopRowPosition", it) }
                if (children.isNotEmpty()) json.put("children", JSONArray(children))
                if (visibleUnderIds.isNotEmpty()) json.put("visibleUnderIds", JSONArray(visibleUnderIds))
                if (questionByLanguage.isNotEmpty()) json.put("questionByLanguage", JSONObject(questionByLanguage))
                if (labelByLanguage.isNotEmpty()) json.put("labelByLanguage", JSONObject(labelByLanguage))
                if (speechTextByLanguage.isNotEmpty()) json.put("speechTextByLanguage", JSONObject(speechTextByLanguage))
                if (placements.isNotEmpty()) {
                    json.put("placements", JSONArray().apply {
                        placements.forEach { placement ->
                            put(JSONObject().put("pageId", placement.pageId).put("position5x5", placement.position5x5))
                        }
                    })
                }
            }
    }

    private data class RawItemsJson(
        val root: Any,
        val itemsArray: JSONArray,
        val createdFromFallback: Boolean
    )

    private data class ProfileBootstrapResult(
        val linkedItemCount: Int,
        val updated: Boolean
    )
}

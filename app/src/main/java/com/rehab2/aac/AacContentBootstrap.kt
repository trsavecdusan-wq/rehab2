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
    private const val DEBUG_PREFS_NAME = "aac_dom_profile_debug"
    private const val KEY_DEBUG_PROFILE_FILE_PATH = "profile_file_path"
    private const val KEY_DEBUG_PROFILE_FILE_EXISTS = "profile_file_exists"
    private const val KEY_DEBUG_PROFILE_TYPE = "profile_type"
    private const val KEY_DEBUG_DOM_PROFILE_FOUND = "dom_profile_found"
    private const val KEY_DEBUG_DOM_PROFILE_ID = "dom_profile_id"
    private const val KEY_DEBUG_ITEM_IDS_BEFORE = "item_ids_before"
    private const val KEY_DEBUG_ITEM_IDS_AFTER = "item_ids_after"
    private const val SYSTEM_ICON_ASSET_DIR = "NovaRehab/icons/system"
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    )

    private val BUNDLED_SYSTEM_ICON_FILES = listOf(
        "come_to_me.png",
        "dont_understand.png",
        "help.png",
        "home.png",
        "hungry.png",
        "miss_someone.png",
        "need.png",
        "no.png",
        "other.png",
        "pain.png",
        "people.png",
        "please.png",
        "problem.png",
        "real_world.png",
        "repeat.png",
        "rest.png",
        "slower.png",
        "sorry.png",
        "thank_you.png",
        "thirsty.png",
        "tired.png",
        "wait.png",
        "wc.png",
        "what.png",
        "when.png",
        "where.png",
        "yes.png"
    )

    private val ROOT_SYSTEM_ICON_REPAIRS = mapOf(
        "people" to "system/people.png",
        "need" to "system/need.png",
        "problem" to "system/problem.png",
        "what_root" to "system/what.png",
        "where_root" to "system/where.png",
        "when_root" to "system/when.png",
        "home" to "system/home.png",
        "other" to "system/other.png",
        "real_world" to "system/real_world.png"
    )

    private val FIXED_ROW_SYSTEM_ICON_REPAIRS = mapOf(
        "no" to "system/no.png",
        "yes" to "system/yes.png"
    )

    private val PERSON_PHOTO_REPAIRS = mapOf(
        "person_dusan" to "person_dusan.jpg",
        "person_zana" to "person_zana.jpg",
        "person_franc" to "person_franc.jpg",
        "person_inna" to "person_inna.jpg",
        "person_julija" to "person_julija.jpg",
        "person_oksana" to "person_oksana.jpg",
        "person_sergej" to "person_sergej.jpg"
    )

    private val PEOPLE_GROUP_CHILD_REPAIRS = mapOf(
        "family_group" to listOf("person_zana", "person_sergej", "miss_you", "love_you", "contact_call"),
        "friends_group" to listOf(
            "person_dusan",
            "person_franc",
            "person_inna",
            "person_julija",
            "person_oksana",
            "contact_message",
            "contact_call",
            "miss_you",
            "when_come",
            "come_to_me"
        )
    )

    private val CRITICAL_SYSTEM_ICON_FILES = setOf(
        "no.png",
        "yes.png"
    )

    private val OPTIONAL_ROOT_SYSTEM_ICON_REPAIRS = mapOf(
        "feeling" to "system/feeling.png",
        "care" to "system/care.png",
        "health" to "system/health.png",
        "cannot" to "system/cannot.png",
        "cold_hot" to "system/cold_hot.png"
    )

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

    data class DomProfileDebug(
        val profileFilePath: String,
        val profileFileExists: Boolean,
        val profileType: String,
        val domProfileFound: Boolean,
        val domProfileId: String,
        val itemIdsBefore: Int,
        val itemIdsAfter: Int
    )

    fun ensurePatientStartupContent(context: Context, fallbackItems: List<AacItem>): Result {
        AacStoragePaths.ensureAacContentDirs(context)
        val seededSystemIcons = seedBundledSystemIcons(context)
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val rawItems = loadItemsJson(itemsFile, fallbackItems)
        val itemsArray = rawItems.itemsArray
        val starterItems = AacStarterContentV1.items()
        val mergedMissingSystemItems = mergeMissingSystemItems(itemsArray, fallbackItems + starterItems)
        val repairedStarterCategoryChildren = repairStarterCategoryChildren(itemsArray, starterItems)
        val repairedPeopleGroupChildren = repairPeopleGroupChildren(itemsArray)
        val repairedConversationTreeV3Metadata = repairConversationTreeV3Metadata(itemsArray, starterItems)
        val repairedRootSystemIcons = repairRootSystemIconMetadata(context, itemsArray)
        val repairedFixedRowSystemIcons = repairFixedRowSystemIconMetadata(context, itemsArray)
        val repairedPersonPhotoMetadata = repairPersonPhotoMetadata(context, itemsArray)
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
        val defaultPagePlacements = if (createdDefaultPage) {
            addDefaultPagePlacements(itemsArray, defaultPageId)
        } else {
            0
        }
        val starterPlacements = if (!createdDefaultPage) {
            addStarterItemsToEmptyDefaultPageSlots(itemsArray, defaultPageId)
        } else {
            0
        }
        val addedPlacements = defaultPagePlacements + starterPlacements
        val repairedFixedTopRowMetadata = repairFixedTopRowMetadata(itemsArray)
        val repairedDefaultPageV3Placements = repairDefaultPageV3Placements(itemsArray, defaultPageId)
        val repairedNoUnderstandLabels = repairNoUnderstandSystemLabels(itemsArray)
        val repairedDrinkSpeechItems = repairDrinkChildSpeechItems(itemsArray)
        val repairedFoodSpeechItems = repairFoodChildSpeechItems(itemsArray)
        val repairedPainSpeechItems = repairPainSpeechItems(itemsArray)
        if (
            addedPlacements > 0 ||
            mergedMissingSystemItems > 0 ||
            repairedStarterCategoryChildren > 0 ||
            repairedPeopleGroupChildren > 0 ||
            repairedConversationTreeV3Metadata > 0 ||
            repairedRootSystemIcons > 0 ||
            repairedFixedRowSystemIcons > 0 ||
            repairedPersonPhotoMetadata > 0 ||
            repairedFixedTopRowMetadata > 0 ||
            repairedDefaultPageV3Placements > 0 ||
            repairedNoUnderstandLabels > 0 ||
            repairedDrinkSpeechItems > 0 ||
            repairedFoodSpeechItems > 0 ||
            repairedPainSpeechItems > 0
        ) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        } else if (itemsFile?.exists() != true && rawItems.createdFromFallback) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        }

        val pageItemIds = itemIdsOnPage(itemsArray, defaultPageId)
        val domProfileResult = ensureDomProfileLinked(context, pageItemIds.ifEmpty { rootItemIds(itemsArray) })
        val syncedDomRelations = syncDomProfileRelations(context, itemsArray)
        if (syncedDomRelations > 0) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        }
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
            "AAC_BOOTSTRAP defaultPage=${result.defaultPageId} items=${result.itemCount} normalVisible=${result.visibleNormalItemCount} fixed=${result.fixedRowCount} placementsAdded=${result.addedPlacements} starterPlacementsAdded=$starterPlacements domLinked=${result.domProfileLinkedItemCount} domRelationsSynced=$syncedDomRelations seededSystemIcons=$seededSystemIcons mergedMissingSystemItems=$mergedMissingSystemItems starterCategoryChildrenRepaired=$repairedStarterCategoryChildren peopleGroupChildrenRepaired=$repairedPeopleGroupChildren conversationTreeV3MetadataRepaired=$repairedConversationTreeV3Metadata rootSystemIconsRepaired=$repairedRootSystemIcons fixedRowSystemIconsRepaired=$repairedFixedRowSystemIcons personPhotoRepaired=$repairedPersonPhotoMetadata fixedTopRowRepaired=$repairedFixedTopRowMetadata defaultPageV3Repaired=$repairedDefaultPageV3Placements noUnderstandRepaired=$repairedNoUnderstandLabels drinkSpeechRepaired=$repairedDrinkSpeechItems foodSpeechRepaired=$repairedFoodSpeechItems painSpeechRepaired=$repairedPainSpeechItems reason=${result.reason}"
        )
        return result
    }

    private fun seedBundledSystemIcons(context: Context): Int {
        val systemDir = AacStoragePaths.getIconsSystemDir(context) ?: return 0
        if (!systemDir.exists() && !systemDir.mkdirs()) return 0

        var seeded = 0
        BUNDLED_SYSTEM_ICON_FILES.forEach { fileName ->
            val targetFile = File(systemDir, fileName)
            val assetPath = "$SYSTEM_ICON_ASSET_DIR/$fileName"
            try {
                val assetBytes = context.assets.open(assetPath).use { input -> input.readBytes() }
                if (isRuntimeSystemIconUsable(targetFile, fileName, assetBytes)) {
                    seeded++
                    return@forEach
                }

                targetFile.outputStream().use { output ->
                    output.write(assetBytes)
                }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    seeded++
                }
            } catch (error: Exception) {
                Log.w(TAG, "AAC_BOOTSTRAP_SYSTEM_ICON_SEED_FAILED asset=$assetPath", error)
            }
        }
        return seeded
    }

    private fun isRuntimeSystemIconUsable(file: File, fileName: String, assetBytes: ByteArray): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        if (!hasPngHeader(file)) return false
        if (fileName in CRITICAL_SYSTEM_ICON_FILES) {
            return try {
                file.readBytes().contentEquals(assetBytes)
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    private fun hasPngHeader(file: File): Boolean {
        return try {
            if (file.length() < PNG_SIGNATURE.size) return false
            file.inputStream().use { input ->
                val header = ByteArray(PNG_SIGNATURE.size)
                val read = input.read(header)
                read == PNG_SIGNATURE.size && header.contentEquals(PNG_SIGNATURE)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun repairRootSystemIconMetadata(context: Context, itemsArray: JSONArray): Int {
        val repairs = ROOT_SYSTEM_ICON_REPAIRS + OPTIONAL_ROOT_SYSTEM_ICON_REPAIRS.filterValues { imagePath ->
            AacStoragePaths.resolveIconFile(context, imagePath, IconSource.SYSTEM)?.isFile == true
        }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        repairs.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            if (AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.SYSTEM)?.isFile != true) {
                return@forEach
            }

            val currentImagePath = item.optString("imagePath").trim()
            val currentIconSource = item.optString("iconSource").trim().uppercase()
            val hasProtectedExternalImage = currentImagePath.isNotBlank() &&
                currentIconSource in setOf(
                    IconSource.CUSTOM.name,
                    IconSource.PATIENT.name,
                    IconSource.SOCA.name,
                    IconSource.ARASAAC.name
                )
            if (hasProtectedExternalImage) return@forEach

            var itemRepaired = 0
            if (currentImagePath.isBlank()) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (currentIconSource != IconSource.SYSTEM.name) {
                item.put("iconSource", IconSource.SYSTEM.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun repairFixedRowSystemIconMetadata(context: Context, itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        FIXED_ROW_SYSTEM_ICON_REPAIRS.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val resolvedFile = AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.SYSTEM)
            if (resolvedFile?.isFile != true || !hasPngHeader(resolvedFile)) return@forEach

            var itemRepaired = 0
            if (item.optString("imagePath").trim() != desiredImagePath) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (item.optString("iconSource").trim().uppercase() != IconSource.SYSTEM.name) {
                item.put("iconSource", IconSource.SYSTEM.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun repairPersonPhotoMetadata(context: Context, itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        PERSON_PHOTO_REPAIRS.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val resolvedFile = AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.PATIENT)
            if (resolvedFile?.isFile != true || resolvedFile.length() <= 0L) return@forEach

            var itemRepaired = 0
            if (item.optString("imagePath").trim() != desiredImagePath) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (item.optString("iconSource").trim().uppercase() != IconSource.PATIENT.name) {
                item.put("iconSource", IconSource.PATIENT.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun mergeMissingSystemItems(itemsArray: JSONArray, fallbackItems: List<AacItem>): Int {
        val existingIds = itemObjects(itemsArray)
            .map { item -> item.optString("id").trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        var merged = 0
        fallbackItems.forEach { item ->
            val id = item.id.trim()
            if (id.isBlank() || id in existingIds) return@forEach
            itemsArray.put(item.toBootstrapJson())
            existingIds += id
            merged++
        }
        return merged
    }

    private fun repairStarterCategoryChildren(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0
        starterItems
            .filter { starter -> starter.children.isNotEmpty() }
            .forEach { starter ->
                val item = itemsById[starter.id] ?: return@forEach
                if (isUserProtected(item)) return@forEach
                val children = item.optJSONArray("children") ?: JSONArray()
                val existingChildren = stringList(children).toMutableSet()
                var itemRepaired = 0
                starter.children.forEach { childId ->
                    if (childId.isNotBlank() && childId in itemsById && existingChildren.add(childId)) {
                        children.put(childId)
                        itemRepaired++
                        repaired++
                    }
                }
                if (itemRepaired > 0 || item.optJSONArray("children") == null) {
                    item.put("children", children)
                }
            }
        return repaired
    }

    private fun repairPeopleGroupChildren(itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        PEOPLE_GROUP_CHILD_REPAIRS.forEach { (id, desiredChildren) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val existingChildren = stringList(item.optJSONArray("children"))
            if (existingChildren == desiredChildren) return@forEach
            item.put("children", jsonArrayOf(desiredChildren))
            repaired++
        }
        return repaired
    }

    private fun repairConversationTreeV3Metadata(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val guidedBranchIds = setOf(
            "people",
            "socialno",
            "need",
            "problem",
            "please",
            "pogovor",
            "what_root",
            "where_root",
            "when_root",
            "person_dusan",
            "person_zana",
            "person_sergej",
            "person_julija",
            "person_oksana",
            "person_inna",
            "person_franc",
            "person_other",
            "miss_someone",
            "contact_call",
            "contact_message",
            "help",
            "repeat",
            "slower",
            "more",
            "turn_me",
            "cannot",
            "cold_hot",
            "uncomfortable",
            "thirsty",
            "drink",
            "hungry",
            "food",
            "pain",
            "wc",
            "real_world",
            "vending_drinks",
            "vending_coffee_tea"
        )
        val starterById = starterItems
            .filter { item -> item.id in guidedBranchIds }
            .associateBy { item -> item.id }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0
        starterById.forEach { (id, starter) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            if (starter.opensSubicons && !item.optBoolean("opensSubicons", false)) {
                item.put("opensSubicons", true)
                repaired++
            }
            if (starter.opensSubicons && item.optBoolean("speaksImmediately", true)) {
                item.put("speaksImmediately", false)
                repaired++
            }
            if (starter.opensSubicons && (item.optString("actionType").isBlank() || item.optString("actionType") == "speak")) {
                item.put("actionType", "open_subicons")
                repaired++
            }
            if (starter.questionByLanguage.isNotEmpty()) {
                val questions = item.optJSONObject("questionByLanguage") ?: JSONObject()
                starter.questionByLanguage.forEach { (languageCode, question) ->
                    if (questions.optString(languageCode).isBlank()) {
                        questions.put(languageCode, question)
                        repaired++
                    }
                }
                item.put("questionByLanguage", questions)
            }
        }
        return repaired
    }

    private fun repairNoUnderstandSystemLabels(itemsArray: JSONArray): Int {
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            val id = item.optString("id").trim()
            if (id != "no_understand" && id != "dont_understand") return@forEach
            if (isUserProtected(item)) return@forEach

            val currentLabel = item.optString("labelSl")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
            val isKnownUnsafeLabel = currentLabel.isBlank() ||
                currentLabel == "NE" ||
                currentLabel == "NE\nRAZUMEM"
            if (!isKnownUnsafeLabel) return@forEach

            item.put("labelSl", "NE RAZUMEM")
            item.put("text", "NE RAZUMEM")
            item.put("baseText", "NE RAZUMEM")
            item.put("labelUk", "Я НЕ РОЗУМІЮ")
            item.put("labelEn", "I DON'T UNDERSTAND")
            item.put("speechText", "ne razumem")
            item.put("speakTextSl", "ne razumem")
            item.put("speakTextUk", "Я не розумію")
            item.put("speechTextEn", "I don't understand")
            item.put("labelByLanguage", JSONObject(item.optJSONObject("labelByLanguage")?.toString() ?: "{}").apply {
                put("sl", "NE RAZUMEM")
                put("uk", "Я НЕ РОЗУМІЮ")
                put("en", "I DON'T UNDERSTAND")
            })
            item.put("speechTextByLanguage", JSONObject(item.optJSONObject("speechTextByLanguage")?.toString() ?: "{}").apply {
                put("sl", "ne razumem")
                put("uk", "Я не розумію")
                put("en", "I don't understand")
            })
            repaired++
        }
        return repaired
    }

    private fun repairFixedTopRowMetadata(itemsArray: JSONArray): Int {
        val desiredPositions = mapOf(
            "no" to 1,
            "yes" to 2,
            "dont_understand" to 3,
            "thank_you" to 4,
            "sorry" to 5
        )
        val legacyFixedRowIds = setOf("help", "pain", "stop", "no_understand")
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            if (isUserProtected(item)) return@forEach
            val id = item.optString("id").trim()
            val desiredPosition = desiredPositions[id]
            if (desiredPosition != null) {
                if (item.optInt("fixedTopRowPosition", 0) != desiredPosition) {
                    item.put("fixedTopRowPosition", desiredPosition)
                    repaired++
                }
                return@forEach
            }
            if (id in legacyFixedRowIds && item.optInt("fixedTopRowPosition", 0) in 1..5) {
                item.remove("fixedTopRowPosition")
                repaired++
            }
        }
        return repaired
    }

    private fun repairDefaultPageV3Placements(itemsArray: JSONArray, pageId: String): Int {
        if (pageId.isBlank()) return 0
        val desiredPositions = mapOf(
            "people" to 1,
            "need" to 2,
            "problem" to 3,
            "thirsty" to 4,
            "hungry" to 5,
            "pain" to 6,
            "wc" to 7,
            "tired" to 8,
            "rest" to 9,
            "please" to 10,
            "what_root" to 11,
            "where_root" to 12,
            "when_root" to 13,
            "home" to 14,
            "other" to 15,
            "real_world" to 16,
            "feeling" to 17,
            "care" to 18,
            "health" to 19,
            "repeat" to 20,
            "wait" to 21,
            "come_to_me" to 22,
            "cannot" to 23,
            "cold_hot" to 24,
            "uncomfortable" to 25
        )
        val legacyNonRootIds = setOf(
            "help",
            "family_group",
            "friends_group",
            "call",
            "message",
            "miss_someone",
            "what_do",
            "where_go",
            "when_come",
            "i_want",
            "dont_want",
            "drink",
            "food"
        )
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            if (isUserProtected(item)) return@forEach
            val id = item.optString("id").trim()
            val desiredPosition = desiredPositions[id]
            var itemRepaired = 0
            if (id in legacyNonRootIds && item.optBoolean("isRootItem", false)) {
                item.put("isRootItem", false)
                itemRepaired++
            } else if (desiredPosition != null && !item.optBoolean("isRootItem", false)) {
                item.put("isRootItem", true)
                itemRepaired++
            }
            val existingPlacements = item.optJSONArray("placements") ?: JSONArray()
            val nextPlacements = JSONArray()
            var removedDefaultPagePlacement = false
            for (index in 0 until existingPlacements.length()) {
                val placement = existingPlacements.optJSONObject(index) ?: continue
                if (placement.optString("pageId").trim() == pageId) {
                    removedDefaultPagePlacement = true
                } else {
                    nextPlacements.put(placement)
                }
            }
            if (desiredPosition != null) {
                nextPlacements.put(JSONObject().put("pageId", pageId).put("position5x5", desiredPosition))
            }
            if (removedDefaultPagePlacement || desiredPosition != null && existingPlacements.length() != nextPlacements.length()) {
                if (nextPlacements.length() > 0) {
                    item.put("placements", nextPlacements)
                } else {
                    item.remove("placements")
                }
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun repairDrinkChildSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        listOf("drink", "thirsty").forEach { parentId ->
            itemsById[parentId]?.let { parent ->
                if (isUserProtected(parent)) return@let
                repaired += ensureParentQuestionMetadata(
                    item = parent,
                    childRepairs = DRINK_CHILD_REPAIRS,
                    questionSl = "Kaj želiš piti?",
                    questionUk = "Що ти хочеш пити?",
                    questionEn = "What do you want to drink?"
                )
            }
        }

        DRINK_CHILD_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        return repaired
    }

    private fun repairFoodChildSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        val food = itemsById["food"]
        if (food != null) {
            if (!isUserProtected(food)) {
                repaired += ensureParentQuestionMetadata(
                    item = food,
                    childRepairs = FOOD_CHILD_REPAIRS,
                    questionSl = "Kaj želiš jesti?",
                    questionUk = "Що ти хочеш їсти?",
                    questionEn = "What do you want to eat?"
                )
            }
        }

        FOOD_CHILD_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        return repaired
    }

    private fun repairPainSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        val pain = itemsById["pain"]
        if (pain != null) {
            if (!isUserProtected(pain)) {
                repaired += ensureParentQuestionMetadata(
                    item = pain,
                    childRepairs = PAIN_CHILD_REPAIRS,
                    questionSl = "Kje te boli?",
                    questionUk = "Де тебе болить?",
                    questionEn = "Where does it hurt?"
                )
                repaired += ensureGuidedPainNode(
                    item = pain,
                    children = listOf("head", "belly", "leg", "arm", "back", "chest", "throat"),
                    questionSl = "Kje te boli?"
                )
            }
        }

        PAIN_CHILD_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        PAIN_GUIDED_NODE_REPAIRS.forEach { (id, children, questionSl) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            repaired += ensureGuidedPainNode(item, children, questionSl)
        }
        return repaired
    }

    private fun ensureGuidedPainNode(item: JSONObject, children: List<String>, questionSl: String): Int {
        var repaired = 0
        val currentChildren = stringList(item.optJSONArray("children"))
        if (currentChildren != children) {
            item.put("children", jsonArrayOf(children))
            repaired++
        }
        if (!item.optBoolean("opensSubicons", false)) {
            item.put("opensSubicons", true)
            repaired++
        }
        if (item.optBoolean("speaksImmediately", true)) {
            item.put("speaksImmediately", false)
            repaired++
        }
        if (item.optString("actionType").isBlank() || item.optString("actionType") == "speak") {
            item.put("actionType", "open_subicons")
            repaired++
        }
        val questionByLanguage = item.optJSONObject("questionByLanguage") ?: JSONObject()
        if (questionByLanguage.optString("sl").isBlank()) {
            questionByLanguage.put("sl", questionSl)
            item.put("questionByLanguage", questionByLanguage)
            repaired++
        }
        return repaired
    }

    private fun ensureParentQuestionMetadata(
        item: JSONObject,
        childRepairs: List<FoodChildRepair>,
        questionSl: String,
        questionUk: String,
        questionEn: String
    ): Int {
        if (isUserProtected(item)) return 0
        var repaired = 0
        val children = item.optJSONArray("children") ?: JSONArray()
        val childIds = stringList(children).toMutableSet()
        childRepairs.forEach { repair ->
            if (childIds.add(repair.id)) {
                children.put(repair.id)
                repaired++
            }
        }
        if (repaired > 0 || item.optJSONArray("children") == null) {
            item.put("children", children)
        }
        val questionByLanguage = item.optJSONObject("questionByLanguage") ?: JSONObject()
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "sl",
            questionSl,
            legacyQuestionValues(questionSl) +
                setOf(item.optString("speechText").trim().lowercase(), item.optString("speakTextSl").trim().lowercase())
        )
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "uk",
            questionUk,
            legacyQuestionValues(questionUk) + setOf(item.optString("speakTextUk").trim().lowercase())
        )
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "en",
            questionEn,
            legacyQuestionValues(questionEn) + setOf(item.optString("speechTextEn").trim().lowercase())
        )
        item.put("questionByLanguage", questionByLanguage)
        if (!item.optBoolean("opensSubicons", false)) {
            item.put("opensSubicons", true)
            repaired++
        }
        if (item.optBoolean("addsToSentence", true)) {
            item.put("addsToSentence", false)
            repaired++
        }
        if (item.optBoolean("speaksImmediately", true)) {
            item.put("speaksImmediately", false)
            repaired++
        }
        if (item.optString("actionType").isBlank() || item.optString("actionType") == "speak") {
            item.put("actionType", "open_subicons")
            repaired++
        }
        return repaired
    }

    private fun legacyQuestionValues(question: String): Set<String> {
        return setOf(
            question.removeSuffix("?"),
            question
                .replace("Що ти хочеш", "Що хочеш")
                .removeSuffix("?"),
            question
                .replace("Що ти хочеш", "Що хочеш"),
            question.replace("Де тебе болить", "Де болить").removeSuffix("?"),
            question.replace("Де тебе болить", "Де болить")
        )
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != question.trim().lowercase() }
            .toSet()
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

    private fun addStarterItemsToEmptyDefaultPageSlots(itemsArray: JSONArray, pageId: String): Int {
        if (pageId.isBlank()) return 0
        val occupiedPositions = occupiedPositions(itemsArray, pageId).toMutableSet()
        val freePositions = (6..25).filter { it !in occupiedPositions }
        if (freePositions.isEmpty()) return 0

        val fixedIds = fixedRowItemIds(itemsArray)
        val alreadyOnPage = itemIdsOnPage(itemsArray, pageId).toMutableSet()
        val itemsById = rootItemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()
        val candidates = STARTER_VISIBILITY_PRIORITY
            .asSequence()
            .mapNotNull { itemId -> itemsById[itemId] }
            .filterNot(::isUserProtected)
            .filter { item -> item.optString("id").trim() !in fixedIds }
            .filter { item -> item.optString("id").trim() !in alreadyOnPage }
            .distinctBy { item -> item.optString("id").trim() }
            .toList()

        var added = 0
        candidates.zip(freePositions).forEach { (item, position) ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            placements.put(JSONObject().put("pageId", pageId).put("position5x5", position))
            item.put("placements", placements)
            occupiedPositions += position
            alreadyOnPage += item.optString("id").trim()
            added++
        }
        return added
    }

    private fun ensureDomProfileLinked(context: Context, itemIds: List<String>): ProfileBootstrapResult {
        val safeItemIds = itemIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (safeItemIds.isEmpty()) {
            saveDomProfileDebug(
                context = context,
                profileFile = null,
                profileFileExists = false,
                profileType = "NO_SAFE_ITEMS",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(linkedItemCount = 0, updated = false)
        }
        val profilesDir = AacStoragePaths.getProfilesDataDir(context) ?: run {
            saveDomProfileDebug(
                context = context,
                profileFile = null,
                profileFileExists = false,
                profileType = "NO_PROFILE_DIR",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(0, false)
        }
        if (!profilesDir.exists() && !profilesDir.mkdirs()) {
            saveDomProfileDebug(
                context = context,
                profileFile = File(profilesDir, DOM_PROFILE_FILE),
                profileFileExists = false,
                profileType = "PROFILE_DIR_CREATE_FAILED",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(0, false)
        }
        val profileFile = File(profilesDir, DOM_PROFILE_FILE)
        val rootJson = readProfileJson(profileFile)
        val profileType = domProfileType(rootJson)
        val profileJson = domProfileJson(rootJson) ?: JSONObject()
            .put("id", DOM_PROFILE_ID)
            .put("displayName", "DOM")
            .put("context", AacCommunicationContext.NORMAL_COMMUNICATION.name)
            .put("enabled", true)
        val domProfileFound = domProfileJson(rootJson) != null
        val domProfileId = profileJson.optString("id").trim()
        val existingItemIds = stringList(profileJson.optJSONArray("itemIds"))
        if (existingItemIds.isNotEmpty()) {
            saveDomProfileDebug(
                context = context,
                profileFile = profileFile,
                profileFileExists = profileFile.isFile,
                profileType = profileType,
                domProfileFound = domProfileFound,
                domProfileId = domProfileId,
                itemIdsBefore = existingItemIds.size,
                itemIdsAfter = existingItemIds.size
            )
            return ProfileBootstrapResult(linkedItemCount = existingItemIds.size, updated = false)
        }
        profileJson.put("itemIds", JSONArray().apply { safeItemIds.forEach { itemId -> put(itemId) } })
        val outputJson = if (rootJson?.has("profiles") == true) {
            ensureDomProfileInRoot(rootJson, profileJson)
        } else {
            profileJson
        }
        profileFile.writeText(outputJson.toString(2), Charsets.UTF_8)
        val writtenRootJson = readProfileJson(profileFile)
        val itemIdsAfter = stringList(domProfileJson(writtenRootJson)?.optJSONArray("itemIds")).size
        saveDomProfileDebug(
            context = context,
            profileFile = profileFile,
            profileFileExists = profileFile.isFile,
            profileType = profileType,
            domProfileFound = domProfileFound,
            domProfileId = domProfileId,
            itemIdsBefore = existingItemIds.size,
            itemIdsAfter = itemIdsAfter
        )
        Log.d(
            TAG,
            "DOM_PROFILE_DEBUG file=${profileFile.absolutePath} exists=${profileFile.isFile} type=$profileType found=$domProfileFound id=$domProfileId before=${existingItemIds.size} after=$itemIdsAfter"
        )
        return ProfileBootstrapResult(linkedItemCount = safeItemIds.size, updated = true)
    }

    fun inspectDomProfileDebug(context: Context): DomProfileDebug {
        val profileFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        }
        val rootJson = profileFile?.let { readProfileJson(it) }
        val profileJson = domProfileJson(rootJson)
        val currentItemIdsCount = stringList(profileJson?.optJSONArray("itemIds")).size
        val prefs = context.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
        return DomProfileDebug(
            profileFilePath = profileFile?.absolutePath
                ?: prefs.getString(KEY_DEBUG_PROFILE_FILE_PATH, "").orEmpty(),
            profileFileExists = profileFile?.isFile
                ?: prefs.getBoolean(KEY_DEBUG_PROFILE_FILE_EXISTS, false),
            profileType = domProfileType(rootJson),
            domProfileFound = profileJson != null,
            domProfileId = profileJson?.optString("id")?.trim().orEmpty(),
            itemIdsBefore = prefs.getInt(KEY_DEBUG_ITEM_IDS_BEFORE, currentItemIdsCount),
            itemIdsAfter = prefs.getInt(KEY_DEBUG_ITEM_IDS_AFTER, currentItemIdsCount)
        )
    }

    private fun syncDomProfileRelations(context: Context, itemsArray: JSONArray): Int {
        val domItemIds = domProfileItemIds(context).toSet()
        if (domItemIds.isEmpty()) return 0
        var updatedCount = 0
        itemObjects(itemsArray).forEach { item ->
            val itemId = item.optString("id").trim()
            if (itemId.isBlank() || itemId !in domItemIds || itemHasDomProfileRelation(item)) {
                return@forEach
            }
            val profileIds = item.optJSONArray("profileIds") ?: JSONArray()
            profileIds.put(DOM_PROFILE_ID)
            item.put("profileIds", profileIds)
            updatedCount += 1
        }
        if (updatedCount > 0) {
            Log.d(TAG, "AAC_BOOTSTRAP_DOM_PROFILE_RELATIONS_SYNCED count=$updatedCount")
        }
        return updatedCount
    }

    private fun domProfileItemIds(context: Context): List<String> {
        val profileFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        } ?: return emptyList()
        val rootJson = readProfileJson(profileFile)
        val profileJson = domProfileJson(rootJson) ?: return emptyList()
        return stringList(profileJson.optJSONArray("itemIds"))
    }

    private fun itemHasDomProfileRelation(item: JSONObject): Boolean {
        val directIds = listOf("profileId", "profile_id", "profile")
            .map { key -> item.optString(key).trim() }
        val arrayIds = listOf("profileIds", "profile_ids", "profiles")
            .flatMap { key -> stringList(item.optJSONArray(key)) }
        return DOM_PROFILE_ID in (directIds + arrayIds)
    }

    private fun domProfileJson(rootJson: JSONObject?): JSONObject? {
        if (rootJson == null) return null
        if (rootJson.optString("id").trim() == DOM_PROFILE_ID) {
            return rootJson
        }
        val profiles = rootJson.optJSONArray("profiles") ?: return null
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) {
                return profile
            }
        }
        return null
    }

    private fun domProfileType(rootJson: JSONObject?): String {
        if (rootJson == null) return "MISSING"
        if (rootJson.optString("id").trim() == DOM_PROFILE_ID) return "DIRECT"
        if (rootJson.has("profiles")) return "WRAPPED"
        return "UNKNOWN"
    }

    private fun ensureDomProfileInRoot(rootJson: JSONObject, profileJson: JSONObject): JSONObject {
        val profiles = rootJson.optJSONArray("profiles") ?: JSONArray().also { rootJson.put("profiles", it) }
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) {
                return rootJson
            }
        }
        profiles.put(profileJson)
        return rootJson
    }

    private fun loadItemsJson(itemsFile: File?, fallbackItems: List<AacItem>): RawItemsJson {
        if (itemsFile?.isFile == true) {
            return try {
                val raw = itemsFile.readText(Charsets.UTF_8).trim()
                if (raw.startsWith("[")) {
                    val itemsArray = JSONArray(raw)
                    RawItemsJson(rootObject = null, rootArray = itemsArray, itemsArray = itemsArray, createdFromFallback = false)
                } else {
                    val rootObject = JSONObject(raw)
                    RawItemsJson(
                        rootObject = rootObject,
                        rootArray = null,
                        itemsArray = rootObject.optJSONArray("items") ?: JSONArray(),
                        createdFromFallback = false
                    )
                }
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
        return RawItemsJson(
            rootObject = JSONObject().put("items", itemsArray),
            rootArray = null,
            itemsArray = itemsArray,
            createdFromFallback = true
        )
    }

    private fun saveItemsJson(itemsFile: File?, rawItems: RawItemsJson, itemsArray: JSONArray) {
        val file = itemsFile ?: return
        file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        val outputText = when {
            rawItems.rootArray != null -> {
                itemsArray.toString(2)
            }
            rawItems.rootObject != null -> {
                rawItems.rootObject.put("items", itemsArray)
                rawItems.rootObject.toString(2)
            }
            else -> {
                JSONObject().put("items", itemsArray).toString(2)
            }
        }
        file.writeText(outputText, Charsets.UTF_8)
    }

    private fun jsonArrayOf(values: List<String>): JSONArray {
        return JSONArray().apply {
            values.forEach { value -> put(value) }
        }
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

    private fun isUserProtected(item: JSONObject): Boolean {
        return item.optBoolean("userEdited", false) || item.optBoolean("locked", false)
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

    private val STARTER_VISIBILITY_PRIORITY = listOf(
        "yes",
        "no",
        "help",
        "no_understand",
        "dont_understand",
        "wait",
        "water",
        "coffee",
        "change_diaper",
        "diaper",
        "body_position",
        "uncomfortable",
        "pain",
        "bad_feeling",
        "bad",
        "tired",
        "afraid",
        "unsafe",
        "not_safe",
        "stop",
        "stop_movement",
        "afraid_fall",
        "fear_falling"
    )

    private fun readProfileJson(profileFile: File): JSONObject? {
        if (!profileFile.isFile) return null
        return try {
            JSONObject(profileFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "AAC_BOOTSTRAP_PROFILE_READ_FAILED file=${profileFile.name}", error)
            null
        }
    }

    private fun saveDomProfileDebug(
        context: Context,
        profileFile: File?,
        profileFileExists: Boolean,
        profileType: String,
        domProfileFound: Boolean,
        domProfileId: String,
        itemIdsBefore: Int,
        itemIdsAfter: Int
    ) {
        context.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEBUG_PROFILE_FILE_PATH, profileFile?.absolutePath.orEmpty())
            .putBoolean(KEY_DEBUG_PROFILE_FILE_EXISTS, profileFileExists)
            .putString(KEY_DEBUG_PROFILE_TYPE, profileType)
            .putBoolean(KEY_DEBUG_DOM_PROFILE_FOUND, domProfileFound)
            .putString(KEY_DEBUG_DOM_PROFILE_ID, domProfileId)
            .putInt(KEY_DEBUG_ITEM_IDS_BEFORE, itemIdsBefore)
            .putInt(KEY_DEBUG_ITEM_IDS_AFTER, itemIdsAfter)
            .apply()
        Log.d(
            TAG,
            "DOM_PROFILE_DEBUG file=${profileFile?.absolutePath.orEmpty()} exists=$profileFileExists type=$profileType found=$domProfileFound id=$domProfileId before=$itemIdsBefore after=$itemIdsAfter"
        )
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
                meaning?.let { json.put("meaning", it) }
                meaningId?.let { json.put("meaningId", it) }
                meaningType?.let { json.put("meaningType", it) }
                meaningGroup?.let { json.put("meaningGroup", it) }
                if (semanticTags.isNotEmpty()) json.put("semanticTags", jsonArrayOf(semanticTags))
                if (searchKeywordsByLanguage.isNotEmpty()) {
                    json.put("searchKeywordsByLanguage", JSONObject().apply {
                        searchKeywordsByLanguage.forEach { (languageCode, keywords) ->
                            if (languageCode.isNotBlank() && keywords.isNotEmpty()) {
                                put(languageCode, jsonArrayOf(keywords))
                            }
                        }
                    })
                }
                if (scenarioIds.isNotEmpty()) json.put("scenarioIds", jsonArrayOf(scenarioIds))
                conceptId?.let { json.put("conceptId", it) }
                parentId?.let { json.put("parentId", it) }
                fixedTopRowPosition?.let { json.put("fixedTopRowPosition", it) }
                if (children.isNotEmpty()) json.put("children", jsonArrayOf(children))
                if (visibleUnderIds.isNotEmpty()) json.put("visibleUnderIds", jsonArrayOf(visibleUnderIds))
                if (questionByLanguage.isNotEmpty()) json.put("questionByLanguage", JSONObject(questionByLanguage))
                if (labelByLanguage.isNotEmpty()) json.put("labelByLanguage", JSONObject(labelByLanguage))
                if (speechTextByLanguage.isNotEmpty()) json.put("speechTextByLanguage", JSONObject(speechTextByLanguage))
                if (locked) json.put("locked", true)
                if (userEdited) json.put("userEdited", true)
                if (placements.isNotEmpty()) {
                    json.put("placements", JSONArray().apply {
                        placements.forEach { placement ->
                            put(JSONObject().put("pageId", placement.pageId).put("position5x5", placement.position5x5))
                        }
                    })
                }
            }
    }

    private data class FoodChildRepair(
        val id: String,
        val labelSl: String,
        val labelUk: String,
        val labelEn: String,
        val speakTextSl: String,
        val speakTextUk: String,
        val speechTextEn: String,
        val parentId: String = "food"
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put("id", id)
                .put("labelSl", labelSl)
                .put("labelUk", labelUk)
                .put("labelEn", labelEn)
                .put("text", labelSl)
                .put("baseText", labelSl)
                .put("speechText", speakTextSl)
                .put("speakTextSl", speakTextSl)
                .put("speakTextUk", speakTextUk)
                .put("speechTextEn", speechTextEn)
                .put("imagePath", "")
                .put("iconSource", IconSource.SYSTEM.name)
                .put("actionType", "speak")
                .put("targetPageId", "")
                .put("conceptId", id)
                .put("parentId", parentId)
                .put("visibleUnderIds", JSONArray().put(parentId))
                .put("isRootItem", false)
                .put("isHiddenUntilParent", true)
                .put("addsToSentence", true)
                .put("speaksImmediately", true)
                .put("opensSubicons", false)
        }

        fun applyTo(item: JSONObject): Int {
            var repaired = 0
            val legacySpeechSlValues = legacyValues(labelSl, labelEn, id, speakTextSl)
            val legacySpeechUkValues = legacyValues(labelUk, "", id, speakTextUk)
            val legacySpeechEnValues = legacyValues(labelEn, "", id, speechTextEn)
            repaired += putIfBlankOrLegacy(item, "speechText", speakTextSl, legacySpeechSlValues)
            repaired += putIfBlankOrLegacy(item, "speakTextSl", speakTextSl, legacySpeechSlValues)
            repaired += putIfBlank(item, "labelUk", labelUk)
            repaired += putIfBlank(item, "labelEn", labelEn)
            repaired += putIfBlankOrLegacy(item, "speakTextUk", speakTextUk, legacySpeechUkValues)
            repaired += putIfBlankOrLegacy(item, "speechTextEn", speechTextEn, legacySpeechEnValues)
            val speechTextByLanguage = item.optJSONObject("speechTextByLanguage") ?: JSONObject()
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "sl", speakTextSl, legacySpeechSlValues)
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "uk", speakTextUk, legacySpeechUkValues)
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "en", speechTextEn, legacySpeechEnValues)
            item.put("speechTextByLanguage", speechTextByLanguage)
            repaired += putIfBlank(item, "parentId", parentId)
            if (!hasJsonArrayValue(item.optJSONArray("visibleUnderIds"), parentId)) {
                val visibleUnderIds = item.optJSONArray("visibleUnderIds") ?: JSONArray()
                visibleUnderIds.put(parentId)
                item.put("visibleUnderIds", visibleUnderIds)
                repaired++
            }
            if (item.optBoolean("isRootItem", true)) {
                item.put("isRootItem", false)
                repaired++
            }
            if (!item.optBoolean("isHiddenUntilParent", false)) {
                item.put("isHiddenUntilParent", true)
                repaired++
            }
            if (!item.optBoolean("addsToSentence", true)) {
                item.put("addsToSentence", true)
                repaired++
            }
            if (!item.optBoolean("speaksImmediately", true)) {
                item.put("speaksImmediately", true)
                repaired++
            }
            if (item.optBoolean("opensSubicons", false)) {
                item.put("opensSubicons", false)
                repaired++
            }
            if (item.optString("actionType").isBlank() || item.optString("actionType") == "open_subicons") {
                item.put("actionType", "speak")
                repaired++
            }
            return repaired
        }

        private fun putIfBlank(item: JSONObject, key: String, value: String): Int {
            return if (item.optString(key).trim().isBlank()) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putIfBlankOrLegacy(item: JSONObject, key: String, value: String, legacyValues: Set<String>): Int {
            val current = item.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putLanguageIfBlankOrLegacy(
            target: JSONObject,
            key: String,
            value: String,
            legacyValues: Set<String>
        ): Int {
            val current = target.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                target.put(key, value)
                1
            } else {
                0
            }
        }

        private fun legacyValues(labelSl: String, labelEn: String, id: String, fullSpeech: String): Set<String> {
            val values = mutableSetOf(labelSl.lowercase(), labelEn.lowercase(), id)
            val normalizedFullSpeech = fullSpeech.trim()
            values += normalizedFullSpeech
                .replace("I want to eat ", "I want ")
                .replace("I want to drink ", "I want ")
                .replace("Я хочу їсти ", "Я хочу ")
                .replace("Я хочу пити ", "Я хочу ")
                .trim()
                .lowercase()
            values += normalizedFullSpeech
                .replace("želim jesti ", "")
                .replace("želim piti ", "")
                .replace("boli me v ", "")
                .replace("boli me ", "")
                .replace("I want to eat ", "")
                .replace("I want to drink ", "")
                .replace("My ", "")
                .replace(" hurts", "")
                .replace("У мене болить у ", "")
                .replace("У мене болить ", "")
                .replace("Я хочу їсти ", "")
                .replace("Я хочу пити ", "")
                .trim()
                .lowercase()
            return values.filter { it.isNotBlank() }.toSet()
        }

        private fun hasJsonArrayValue(array: JSONArray?, expected: String): Boolean {
            if (array == null) return false
            for (index in 0 until array.length()) {
                if (array.optString(index).trim() == expected) return true
            }
            return false
        }
    }

    private fun putLanguageIfBlankOrLegacy(target: JSONObject, key: String, value: String, legacyValues: Set<String>): Int {
        val current = target.optString(key).trim()
        return if (current.isBlank() || current.lowercase() in legacyValues) {
            target.put(key, value)
            1
        } else {
            0
        }
    }

    private val DRINK_CHILD_REPAIRS = listOf(
        FoodChildRepair(
            id = "water",
            labelSl = "VODA",
            labelUk = "ВОДА",
            labelEn = "WATER",
            speakTextSl = "želim piti vodo",
            speakTextUk = "Я хочу пити воду",
            speechTextEn = "I want to drink water",
            parentId = "drink"
        ),
        FoodChildRepair(
            id = "juice",
            labelSl = "SOK",
            labelUk = "СІК",
            labelEn = "JUICE",
            speakTextSl = "želim piti sok",
            speakTextUk = "Я хочу пити сік",
            speechTextEn = "I want to drink juice",
            parentId = "drink"
        ),
        FoodChildRepair(
            id = "tea",
            labelSl = "ČAJ",
            labelUk = "ЧАЙ",
            labelEn = "TEA",
            speakTextSl = "želim piti čaj",
            speakTextUk = "Я хочу пити чай",
            speechTextEn = "I want to drink tea",
            parentId = "drink"
        ),
        FoodChildRepair(
            id = "coffee",
            labelSl = "KAVA",
            labelUk = "КАВА",
            labelEn = "COFFEE",
            speakTextSl = "želim piti kavo",
            speakTextUk = "Я хочу пити каву",
            speechTextEn = "I want to drink coffee",
            parentId = "drink"
        )
    )

    private val FOOD_CHILD_REPAIRS = listOf(
        FoodChildRepair(
            id = "soup",
            labelSl = "JUHA",
            labelUk = "СУП",
            labelEn = "SOUP",
            speakTextSl = "želim jesti juho",
            speakTextUk = "Я хочу їсти суп",
            speechTextEn = "I want to eat soup"
        ),
        FoodChildRepair(
            id = "bread",
            labelSl = "KRUH",
            labelUk = "ХЛІБ",
            labelEn = "BREAD",
            speakTextSl = "želim jesti kruh",
            speakTextUk = "Я хочу їсти хліб",
            speechTextEn = "I want to eat bread"
        ),
        FoodChildRepair(
            id = "fruit",
            labelSl = "SADJE",
            labelUk = "ФРУКТИ",
            labelEn = "FRUIT",
            speakTextSl = "želim jesti sadje",
            speakTextUk = "Я хочу їсти фрукти",
            speechTextEn = "I want to eat fruit"
        )
    )

    private val PAIN_CHILD_REPAIRS = listOf(
        FoodChildRepair(
            id = "head",
            labelSl = "GLAVA",
            labelUk = "ГОЛОВА",
            labelEn = "HEAD",
            speakTextSl = "boli me glava",
            speakTextUk = "У мене болить голова",
            speechTextEn = "My head hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "arm",
            labelSl = "ROKA",
            labelUk = "РУКА",
            labelEn = "ARM",
            speakTextSl = "boli me roka",
            speakTextUk = "У мене болить рука",
            speechTextEn = "My arm hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "leg",
            labelSl = "NOGA",
            labelUk = "НОГА",
            labelEn = "LEG",
            speakTextSl = "boli me noga",
            speakTextUk = "У мене болить нога",
            speechTextEn = "My leg hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "belly",
            labelSl = "TREBUH",
            labelUk = "ЖИВІТ",
            labelEn = "BELLY",
            speakTextSl = "boli me trebuh",
            speakTextUk = "У мене болить живіт",
            speechTextEn = "My stomach hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "back",
            labelSl = "HRBET",
            labelUk = "СПИНА",
            labelEn = "BACK",
            speakTextSl = "boli me hrbet",
            speakTextUk = "У мене болить спина",
            speechTextEn = "My back hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "chest",
            labelSl = "PRSI",
            labelUk = "ГРУДИ",
            labelEn = "CHEST",
            speakTextSl = "boli me v prsih",
            speakTextUk = "У мене болить у грудях",
            speechTextEn = "My chest hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "throat",
            labelSl = "GRLO",
            labelUk = "ГОРЛО",
            labelEn = "THROAT",
            speakTextSl = "boli me grlo",
            speakTextUk = "У мене болить горло",
            speechTextEn = "My throat hurts",
            parentId = "pain"
        )
    )

    private val PAIN_GUIDED_NODE_REPAIRS = listOf(
        Triple("head", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("belly", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("back", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("chest", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("throat", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("leg", painSideStrengthAndTimeChildren(), "Katera stran?"),
        Triple("arm", painSideStrengthAndTimeChildren(), "Katera stran?"),
        Triple("pain_left", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("pain_right", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("pain_both", painStrengthAndTimeChildren(), "Kako močno boli?"),
        Triple("pain_light", painTimeChildren(), "Od kdaj boli?"),
        Triple("pain_medium", painTimeChildren(), "Od kdaj boli?"),
        Triple("pain_strong", painTimeChildren(), "Od kdaj boli?")
    )

    private fun painSideStrengthAndTimeChildren(): List<String> {
        return listOf(
            "pain_left",
            "pain_right",
            "pain_both",
            "pain_light",
            "pain_medium",
            "pain_strong",
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening"
        )
    }

    private fun painStrengthAndTimeChildren(): List<String> {
        return listOf(
            "pain_light",
            "pain_medium",
            "pain_strong",
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening"
        )
    }

    private fun painTimeChildren(): List<String> {
        return listOf(
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening"
        )
    }

    private data class RawItemsJson(
        val rootObject: JSONObject?,
        val rootArray: JSONArray?,
        val itemsArray: JSONArray,
        val createdFromFallback: Boolean
    )

    private data class ProfileBootstrapResult(
        val linkedItemCount: Int,
        val updated: Boolean
    )
}

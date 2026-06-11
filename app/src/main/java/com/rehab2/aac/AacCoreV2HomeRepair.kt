package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AacCoreV2HomeRepair {
    private const val DOM_PROFILE_ID = "dom"
    private const val DOM_PROFILE_FILE = "dom.json"
    private const val HOME_PAGE_ID = "page_1"
    private const val PATIENT_PAGE_PREFS_NAME = "aac_patient_pages"
    private const val KEY_PATIENT_PAGES = "patient_pages"
    private const val KEY_DEFAULT_PATIENT_PAGE_ID = "default_patient_page_id"
    private const val PATIENT_PAGE_FIELD_SEPARATOR = "\u001F"
    private const val CORE_V2_REPAIR_PREFS_NAME = "aac_core_v2_home_repair"
    private const val KEY_CORE_V2_HOME_REPAIR_DONE = "aac_core_v2_home_repair_done"
    private const val KEY_AAC_HOME_LAYOUT_VERSION = "aac_home_layout_version"
    private const val CORE_V2_HOME_LAYOUT_VERSION = "core_v2"

    private val lockedIds = listOf(
        "no",
        "dont_understand",
        "yes",
        "thank_you",
        "sorry",
        "wc",
        "pain",
        "thirsty",
        "hungry",
        "tired",
        "i_want",
        "need",
        "people",
        "miss_someone",
        "call",
        "feeling",
        "place_group",
        "care",
        "health",
        "dont_want",
        "please",
        "wait",
        "repeat",
        "pogovor",
        "activity_group"
    )

    private val fixedPositions = mapOf(
        "no" to 1,
        "dont_understand" to 2,
        "yes" to 3,
        "thank_you" to 4,
        "sorry" to 5
    )

    private val mainPositions = mapOf(
        "wc" to 6,
        "pain" to 7,
        "thirsty" to 8,
        "hungry" to 9,
        "tired" to 10,
        "i_want" to 11,
        "need" to 12,
        "people" to 13,
        "miss_someone" to 14,
        "call" to 15,
        "feeling" to 16,
        "place_group" to 17,
        "care" to 18,
        "health" to 19,
        "dont_want" to 20,
        "please" to 21,
        "wait" to 22,
        "repeat" to 23,
        "pogovor" to 24,
        "activity_group" to 25
    )

    private val homePageIds = setOf("page_1", "page_2", "page_3", "page_4")

    sealed class Result {
        data class Success(
            val backupDir: File,
            val itemsFilePath: String,
            val domFilePath: String,
            val beforeDomRootItemIds: List<String>,
            val afterDomRootItemIds: List<String>,
            val beforeDomRootCount: Int,
            val afterDomRootCount: Int,
            val activeProfileBefore: String,
            val activeProfileAfter: String,
            val activeProfileChangedCount: Int,
            val beforePage1Positions: List<String>,
            val afterPage1Positions: List<String>,
            val fixedRowUpdatedCount: Int,
            val placementsUpdatedCount: Int,
            val domRootChangedCount: Int,
            val removedDomRootItemCount: Int,
            val jsonWriteVerified: Boolean,
            val noChangeReason: String
        ) : Result()

        data class Failure(val reason: String) : Result()
    }

    fun execute(context: Context): Result = repair(context)

    fun writeExceptionReport(context: Context, error: Throwable, stage: String): String {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return ""
        val backupDir = createBackupDir(externalFilesDir) ?: return ""
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val domFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        }
        return try {
            writeErrorReport(
                backupDir = backupDir,
                itemsFile = itemsFile,
                domFile = domFile,
                stage = stage,
                errorClass = error.javaClass.name,
                errorMessage = error.message.orEmpty(),
                reason = "AacCoreV2HomeRepair exception"
            )
            backupDir.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    fun writeFailureReport(context: Context, reason: String, stage: String): String {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return ""
        val backupDir = createBackupDir(externalFilesDir) ?: return ""
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val domFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        }
        return try {
            writeErrorReport(
                backupDir = backupDir,
                itemsFile = itemsFile,
                domFile = domFile,
                stage = stage,
                errorClass = "",
                errorMessage = "",
                reason = reason
            )
            backupDir.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    fun repair(context: Context): Result {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: return Result.Failure("External files dir ni na voljo.")
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
            ?: return Result.Failure("Poti do aac_items.json ni mogoce odpreti.")
        if (!itemsFile.isFile) {
            return Result.Failure("aac_items.json ne obstaja. Repair ne resetira lokalnega AAC samodejno.")
        }

        val parsedItems = parseItemsFile(itemsFile)
            ?: return Result.Failure("aac_items.json je pokvarjen ali nima polja items.")
        val activeProfileBefore = activeProfileId(context)
        val itemsById = itemObjects(parsedItems.itemsArray).associateBy { it.optString("id").trim() }
        val missingIds = lockedIds.filterNot { it in itemsById }
        if (missingIds.isNotEmpty()) {
            return Result.Failure("Manjkajo zaklenjeni AAC itemi: ${missingIds.joinToString(", ")}")
        }
        val beforePage1Positions = page1PositionLines(parsedItems.itemsArray)

        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
            ?: return Result.Failure("Mape profilov ni mogoce odpreti.")
        val domFile = File(profilesDir, DOM_PROFILE_FILE)
        val parsedDom = parseDomFile(domFile)
        if (domFile.exists() && parsedDom == null) {
            return Result.Failure("dom.json je pokvarjen. Repair ustavljen brez pisanja.")
        }

        val backupDir = createBackupDir(externalFilesDir)
            ?: return Result.Failure("Backup mape ni bilo mogoce ustvariti.")
        backupBeforeWrite(itemsFile, domFile, backupDir)

        var fixedRowUpdatedCount = 0
        var placementsUpdatedCount = 0
        itemObjects(parsedItems.itemsArray).forEach { item ->
            val id = item.optString("id").trim()
            val fixedPosition = fixedPositions[id]
            if (fixedPosition != null) {
                if (item.optInt("fixedTopRowPosition", 0) != fixedPosition) {
                    item.put("fixedTopRowPosition", fixedPosition)
                    fixedRowUpdatedCount++
                }
            } else if (item.optInt("fixedTopRowPosition", 0) in 1..5) {
                item.remove("fixedTopRowPosition")
                fixedRowUpdatedCount++
            }

            val mainPosition = mainPositions[id]
            if (mainPosition != null) {
                val nextPlacements = placementsWithoutHomePages(item)
                nextPlacements.put(JSONObject().put("pageId", HOME_PAGE_ID).put("position5x5", mainPosition))
                if (placementsChanged(item.optJSONArray("placements"), nextPlacements)) {
                    item.put("placements", nextPlacements)
                    placementsUpdatedCount++
                }
                if (!item.optBoolean("isRootItem", false)) {
                    item.put("isRootItem", true)
                    placementsUpdatedCount++
                }
                if (item.optBoolean("isHiddenUntilParent", false)) {
                    item.put("isHiddenUntilParent", false)
                    placementsUpdatedCount++
                }
            } else if (id !in fixedPositions.keys) {
                val nextPlacements = placementsWithoutHomePages(item)
                if (placementsChanged(item.optJSONArray("placements"), nextPlacements)) {
                    if (nextPlacements.length() > 0) {
                        item.put("placements", nextPlacements)
                    } else {
                        item.remove("placements")
                    }
                    placementsUpdatedCount++
                }
            }
        }

        val domRoot = parsedDom?.root ?: JSONObject()
        val domProfile = domProfile(domRoot) ?: JSONObject().put("id", DOM_PROFILE_ID)
        val previousDomIds = stringList(domProfile.optJSONArray("itemIds"))
        domProfile.put("id", DOM_PROFILE_ID)
        domProfile.put("displayName", domProfile.optString("displayName").ifBlank { "DOM" })
        domProfile.put("itemIds", JSONArray().apply {
            lockedIds.forEach { itemId -> put(itemId) }
        })
        val afterDomTargetIds = lockedIds
        val domRootChangedCount = if (previousDomIds == afterDomTargetIds) 0 else 1
        val removedDomRootItemCount = previousDomIds.filterNot { it in lockedIds }.distinct().size
        val outputDom = writeDomProfile(domRoot, domProfile)

        var afterDomRootItemIds = emptyList<String>()
        var afterPage1Positions = emptyList<String>()
        var jsonWriteVerified = false
        var noChangeReason = ""
        try {
            if (!profilesDir.exists() && !profilesDir.mkdirs()) {
                return Result.Failure("Mape profilov ni bilo mogoce ustvariti.")
            }
            itemsFile.writeText(parsedItems.toJsonText(), Charsets.UTF_8)
            domFile.writeText(outputDom.toString(2), Charsets.UTF_8)
            writePatientPagePrefs(context)
            writeActiveDomProfile(context)
            writeCoreV2HomeRepairMarker(context)

            val writtenItems = parseItemsFile(itemsFile)
            val writtenDom = parseDomFile(domFile)
            afterDomRootItemIds = stringList(domProfile(writtenDom?.root ?: JSONObject())?.optJSONArray("itemIds"))
            afterPage1Positions = writtenItems?.let { page1PositionLines(it.itemsArray) }.orEmpty()
            jsonWriteVerified = writtenItems != null &&
                writtenDom != null &&
                afterDomRootItemIds == afterDomTargetIds &&
                page1PositionMatchesLocked(writtenItems.itemsArray)
            noChangeReason = buildNoChangeReason(
                fixedRowUpdatedCount = fixedRowUpdatedCount,
                placementsUpdatedCount = placementsUpdatedCount,
                domRootChangedCount = domRootChangedCount,
                activeProfileBefore = activeProfileBefore,
                activeProfileAfter = activeProfileId(context),
                beforeDomRootItemIds = previousDomIds,
                afterDomRootItemIds = afterDomRootItemIds,
                beforePage1Positions = beforePage1Positions,
                afterPage1Positions = afterPage1Positions,
                jsonWriteVerified = jsonWriteVerified
            )
            writeReport(
                backupDir = backupDir,
                itemsFile = itemsFile,
                domFile = domFile,
                activeProfileBefore = activeProfileBefore,
                activeProfileAfter = activeProfileId(context),
                beforeDomRootItemIds = previousDomIds,
                afterDomRootItemIds = afterDomRootItemIds,
                beforePage1Positions = beforePage1Positions,
                afterPage1Positions = afterPage1Positions,
                fixedRowUpdatedCount = fixedRowUpdatedCount,
                placementsUpdatedCount = placementsUpdatedCount,
                domRootChangedCount = domRootChangedCount,
                removedDomRootItemCount = removedDomRootItemCount,
                jsonWriteVerified = jsonWriteVerified,
                noChangeReason = noChangeReason
            )
        } catch (error: Exception) {
            runCatching {
                writeErrorReport(
                    backupDir = backupDir,
                    itemsFile = itemsFile,
                    domFile = domFile,
                    stage = "repair_write",
                    errorClass = error.javaClass.name,
                    errorMessage = error.message.orEmpty(),
                    reason = "Repair write failed"
                )
            }
            return Result.Failure(
                "Repair ni mogel shraniti sprememb: ${error.message ?: error.javaClass.simpleName}. " +
                    "Backup je shranjen tukaj: ${backupDir.absolutePath}"
            )
        }

        return Result.Success(
            backupDir = backupDir,
            itemsFilePath = itemsFile.absolutePath,
            domFilePath = domFile.absolutePath,
            beforeDomRootItemIds = previousDomIds,
            afterDomRootItemIds = afterDomRootItemIds,
            beforeDomRootCount = previousDomIds.size,
            afterDomRootCount = afterDomRootItemIds.size,
            activeProfileBefore = activeProfileBefore,
            activeProfileAfter = activeProfileId(context),
            activeProfileChangedCount = if (activeProfileBefore == DOM_PROFILE_ID) 0 else 1,
            beforePage1Positions = beforePage1Positions,
            afterPage1Positions = afterPage1Positions,
            fixedRowUpdatedCount = fixedRowUpdatedCount,
            placementsUpdatedCount = placementsUpdatedCount,
            domRootChangedCount = domRootChangedCount,
            removedDomRootItemCount = removedDomRootItemCount,
            jsonWriteVerified = jsonWriteVerified,
            noChangeReason = noChangeReason
        )
    }

    private fun parseItemsFile(file: File): ParsedItems? {
        return try {
            val text = file.readText(Charsets.UTF_8).trim()
            if (text.startsWith("[")) {
                ParsedItems(root = null, itemsArray = JSONArray(text))
            } else {
                val root = JSONObject(text)
                val items = root.optJSONArray("items") ?: return null
                ParsedItems(root = root, itemsArray = items)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDomFile(file: File): ParsedDom? {
        if (!file.exists()) return null
        return try {
            ParsedDom(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            null
        }
    }

    private fun createBackupDir(externalFilesDir: File): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val dir = File(externalFilesDir, "NovaRehab/backups/aac_core_v2_home_repair/$timestamp")
        return if (dir.exists() || dir.mkdirs()) dir else null
    }

    private fun backupBeforeWrite(itemsFile: File, domFile: File, backupDir: File) {
        itemsFile.copyTo(File(backupDir, "aac_items.before.json"), overwrite = true)
        if (domFile.isFile) {
            domFile.copyTo(File(backupDir, "dom.before.json"), overwrite = true)
        }
    }

    private fun placementsWithoutHomePages(item: JSONObject): JSONArray {
        val placements = item.optJSONArray("placements") ?: return JSONArray()
        return JSONArray().apply {
            for (index in 0 until placements.length()) {
                val placement = placements.optJSONObject(index) ?: continue
                if (placement.optString("pageId").trim() !in homePageIds) {
                    put(placement)
                }
            }
        }
    }

    private fun placementsChanged(old: JSONArray?, next: JSONArray): Boolean {
        if ((old == null || old.length() == 0) && next.length() == 0) {
            return false
        }
        val oldText = old?.toString() ?: ""
        return oldText != next.toString()
    }

    private fun page1PositionLines(itemsArray: JSONArray): List<String> {
        return page1PositionIds(itemsArray).mapIndexed { index, itemIds ->
            "${index + 1}: ${itemIds.ifEmpty { listOf("-") }.joinToString(", ")}"
        }
    }

    private fun page1PositionMatchesLocked(itemsArray: JSONArray): Boolean {
        val slots = page1PositionIds(itemsArray)
        return slots.size == lockedIds.size &&
            slots.map { it.singleOrNull().orEmpty() } == lockedIds
    }

    private fun page1PositionIds(itemsArray: JSONArray): List<List<String>> {
        val slots = List(lockedIds.size) { mutableListOf<String>() }
        itemObjects(itemsArray).forEach { item ->
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) return@forEach
            val fixedPosition = item.optInt("fixedTopRowPosition", 0)
            if (fixedPosition in 1..5) {
                slots[fixedPosition - 1].addUnique(itemId)
            }
            val placements = item.optJSONArray("placements") ?: return@forEach
            for (index in 0 until placements.length()) {
                val placement = placements.optJSONObject(index) ?: continue
                if (placement.optString("pageId").trim() != HOME_PAGE_ID) continue
                val position = placement.optInt("position5x5", 0)
                if (position in 1..lockedIds.size) {
                    slots[position - 1].addUnique(itemId)
                }
            }
        }
        return slots.map { it.toList() }
    }

    private fun MutableList<String>.addUnique(value: String) {
        if (value !in this) add(value)
    }

    private fun buildNoChangeReason(
        fixedRowUpdatedCount: Int,
        placementsUpdatedCount: Int,
        domRootChangedCount: Int,
        activeProfileBefore: String,
        activeProfileAfter: String,
        beforeDomRootItemIds: List<String>,
        afterDomRootItemIds: List<String>,
        beforePage1Positions: List<String>,
        afterPage1Positions: List<String>,
        jsonWriteVerified: Boolean
    ): String {
        val activeProfileChangedCount = if (activeProfileBefore == activeProfileAfter) 0 else 1
        if (fixedRowUpdatedCount + placementsUpdatedCount + domRootChangedCount + activeProfileChangedCount != 0) {
            return ""
        }
        if (!jsonWriteVerified) {
            return "No counters changed, but JSON write verification failed after repair."
        }
        if (beforeDomRootItemIds == afterDomRootItemIds && beforePage1Positions == afterPage1Positions) {
            return "No counters changed because DOM itemIds, active profile and page_1 positions already matched the repair target before writing."
        }
        return "No counters changed, but before/after snapshots differ. Check repair_report.json for path or parser mismatch."
    }

    private fun domProfile(root: JSONObject): JSONObject? {
        if (root.optString("id").trim() == DOM_PROFILE_ID) return root
        val profiles = root.optJSONArray("profiles") ?: return null
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) return profile
        }
        return null
    }

    private fun writeDomProfile(root: JSONObject, domProfile: JSONObject): JSONObject {
        if (!root.has("profiles") && (root.length() == 0 || root.optString("id").trim() == DOM_PROFILE_ID)) {
            return domProfile
        }
        val profiles = root.optJSONArray("profiles") ?: JSONArray().also { root.put("profiles", it) }
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) {
                profiles.put(index, domProfile)
                return root
            }
        }
        profiles.put(domProfile)
        return root
    }

    private fun writePatientPagePrefs(context: Context) {
        context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATIENT_PAGES, HOME_PAGE_ID + PATIENT_PAGE_FIELD_SEPARATOR + "DOM")
            .putString(KEY_DEFAULT_PATIENT_PAGE_ID, HOME_PAGE_ID)
            .apply()
    }

    private fun activeProfileId(context: Context): String {
        return context.getSharedPreferences(AacProfileStore.PREFS_FILE, Context.MODE_PRIVATE)
            .getString(AacProfileStore.PREF_ACTIVE_PROFILE_ID, AacProfileStore.DEFAULT_PROFILE_ID)
            .orEmpty()
            .ifBlank { AacProfileStore.DEFAULT_PROFILE_ID }
    }

    private fun writeActiveDomProfile(context: Context) {
        context.getSharedPreferences(AacProfileStore.PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(AacProfileStore.PREF_ACTIVE_PROFILE_ID, DOM_PROFILE_ID)
            .apply()
    }

    private fun writeCoreV2HomeRepairMarker(context: Context) {
        context.getSharedPreferences(CORE_V2_REPAIR_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CORE_V2_HOME_REPAIR_DONE, true)
            .putString(KEY_AAC_HOME_LAYOUT_VERSION, CORE_V2_HOME_LAYOUT_VERSION)
            .apply()
    }

    private fun writeReport(
        backupDir: File,
        itemsFile: File,
        domFile: File,
        activeProfileBefore: String,
        activeProfileAfter: String,
        beforeDomRootItemIds: List<String>,
        afterDomRootItemIds: List<String>,
        beforePage1Positions: List<String>,
        afterPage1Positions: List<String>,
        fixedRowUpdatedCount: Int,
        placementsUpdatedCount: Int,
        domRootChangedCount: Int,
        removedDomRootItemCount: Int,
        jsonWriteVerified: Boolean,
        noChangeReason: String
    ) {
        val report = JSONObject()
            .put("repairId", "aac_core_v2_home_repair")
            .put("versionName", "1.2.656")
            .put("executed", true)
            .put("itemsFilePath", itemsFile.absolutePath)
            .put("domFilePath", domFile.absolutePath)
            .put("backupPath", backupDir.absolutePath)
            .put("activeProfileBefore", activeProfileBefore)
            .put("activeProfileAfter", activeProfileAfter)
            .put("coreV2HomeRepairMarker", true)
            .put("aacHomeLayoutVersion", CORE_V2_HOME_LAYOUT_VERSION)
            .put("beforeDomRootCount", beforeDomRootItemIds.size)
            .put("afterDomRootCount", afterDomRootItemIds.size)
            .put("repositoryPathCheck", "AacLocalJsonLoader, AacRepository, editor, diagnostics and repair all use AacStoragePaths for AAC items and profiles.")
            .put("beforeDomRootItemIds", JSONArray().apply {
                beforeDomRootItemIds.forEach { itemId -> put(itemId) }
            })
            .put("afterDomRootItemIds", JSONArray().apply {
                afterDomRootItemIds.forEach { itemId -> put(itemId) }
            })
            .put("beforePage1Positions", JSONArray().apply {
                beforePage1Positions.forEach { line -> put(line) }
            })
            .put("afterPage1Positions", JSONArray().apply {
                afterPage1Positions.forEach { line -> put(line) }
            })
            .put("fixedRowUpdatedCount", fixedRowUpdatedCount)
            .put("placementsUpdatedCount", placementsUpdatedCount)
            .put("domRootChangedCount", domRootChangedCount)
            .put("removedDomRootItemCount", removedDomRootItemCount)
            .put("jsonWriteVerified", jsonWriteVerified)
            .put("noChangeReason", noChangeReason)
            .put("lockedIds", JSONArray().apply {
                lockedIds.forEach { itemId -> put(itemId) }
            })
            .put("warning", "Restart or reopen communicator after repair.")
        File(backupDir, "repair_report.json").writeText(report.toString(2), Charsets.UTF_8)
    }

    private fun writeErrorReport(
        backupDir: File,
        itemsFile: File?,
        domFile: File?,
        stage: String,
        errorClass: String,
        errorMessage: String,
        reason: String
    ) {
        val report = JSONObject()
            .put("repairId", "aac_core_v2_home_repair")
            .put("versionName", "1.2.656")
            .put("executed", false)
            .put("stage", stage)
            .put("reason", reason)
            .put("errorClass", errorClass)
            .put("errorMessage", errorMessage)
            .put("itemsFilePath", itemsFile?.absolutePath.orEmpty())
            .put("itemsFileExists", itemsFile?.isFile == true)
            .put("domFilePath", domFile?.absolutePath.orEmpty())
            .put("domFileExists", domFile?.isFile == true)
            .put("backupPath", backupDir.absolutePath)
        File(backupDir, "repair_report.json").writeText(report.toString(2), Charsets.UTF_8)
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
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private data class ParsedItems(
        val root: JSONObject?,
        val itemsArray: JSONArray
    ) {
        fun toJsonText(): String {
            return root?.put("items", itemsArray)?.toString(2) ?: itemsArray.toString(2)
        }
    }

    private data class ParsedDom(val root: JSONObject)
}

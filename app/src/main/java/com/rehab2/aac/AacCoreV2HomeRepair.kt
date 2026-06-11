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
            val fixedRowUpdatedCount: Int,
            val placementsUpdatedCount: Int,
            val removedDomRootItemCount: Int
        ) : Result()

        data class Failure(val reason: String) : Result()
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
        val itemsById = itemObjects(parsedItems.itemsArray).associateBy { it.optString("id").trim() }
        val missingIds = lockedIds.filterNot { it in itemsById }
        if (missingIds.isNotEmpty()) {
            return Result.Failure("Manjkajo zaklenjeni AAC itemi: ${missingIds.joinToString(", ")}")
        }

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
        val removedDomRootItemCount = previousDomIds.filterNot { it in lockedIds }.distinct().size
        val outputDom = writeDomProfile(domRoot, domProfile)

        try {
            if (!profilesDir.exists() && !profilesDir.mkdirs()) {
                return Result.Failure("Mape profilov ni bilo mogoce ustvariti.")
            }
            itemsFile.writeText(parsedItems.toJsonText(), Charsets.UTF_8)
            domFile.writeText(outputDom.toString(2), Charsets.UTF_8)
            writePatientPagePrefs(context)
            writeReport(backupDir, fixedRowUpdatedCount, placementsUpdatedCount, removedDomRootItemCount)
        } catch (error: Exception) {
            return Result.Failure(
                "Repair ni mogel shraniti sprememb: ${error.message ?: error.javaClass.simpleName}. " +
                    "Backup je shranjen tukaj: ${backupDir.absolutePath}"
            )
        }

        return Result.Success(
            backupDir = backupDir,
            fixedRowUpdatedCount = fixedRowUpdatedCount,
            placementsUpdatedCount = placementsUpdatedCount,
            removedDomRootItemCount = removedDomRootItemCount
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

    private fun writeReport(
        backupDir: File,
        fixedRowUpdatedCount: Int,
        placementsUpdatedCount: Int,
        removedDomRootItemCount: Int
    ) {
        val report = JSONObject()
            .put("repairId", "aac_core_v2_home_repair")
            .put("versionName", "1.2.642")
            .put("fixedRowUpdatedCount", fixedRowUpdatedCount)
            .put("placementsUpdatedCount", placementsUpdatedCount)
            .put("removedDomRootItemCount", removedDomRootItemCount)
            .put("lockedIds", JSONArray().apply {
                lockedIds.forEach { itemId -> put(itemId) }
            })
            .put("warning", "Restart or reopen communicator after repair.")
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

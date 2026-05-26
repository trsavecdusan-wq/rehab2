package com.rehab2.aac

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacLocalJsonLoader {
    private const val TAG = "AacLocalJsonLoader"

    fun loadItems(context: Context, fallbackItems: List<AacItem>): List<AacItem> {
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        if (itemsFile == null || !itemsFile.exists() || !itemsFile.isFile) {
            Log.d(TAG, "AAC_JSON ITEMS_FILE_MISSING")
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            return fallbackItems
        }

        return try {
            val raw = itemsFile.readText(Charsets.UTF_8)
            val json = raw.trim()
            val items = when {
                json.startsWith("[") -> parseItemsArray(JSONArray(json))
                json.startsWith("{") -> {
                    val obj = JSONObject(json)
                    parseItemsArray(obj.optJSONArray("items") ?: JSONArray())
                }
                else -> emptyList()
            }
            if (items.isEmpty()) {
                Log.d(TAG, "AAC_JSON ITEMS_PARSE_ERROR")
                Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
                fallbackItems
            } else {
                Log.d(TAG, "AAC_JSON ITEMS_LOADED count=${items.size}")
                items
            }
        } catch (error: Exception) {
            Log.w(TAG, "AAC_JSON ITEMS_PARSE_ERROR", error)
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            fallbackItems
        }
    }

    fun loadProfiles(context: Context, fallbackProfiles: List<AacProfile>): List<AacProfile> {
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val files = profilesDir?.takeIf { it.exists() && it.isDirectory }?.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()

        if (files.isEmpty()) {
            Log.d(TAG, "AAC_JSON PROFILES_FILE_MISSING")
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            return fallbackProfiles
        }

        return try {
            val profiles = files.flatMap { file ->
                parseProfilesFile(file)
            }.filter { it.enabled }

            if (profiles.isEmpty()) {
                Log.d(TAG, "AAC_JSON PROFILES_PARSE_ERROR")
                Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
                fallbackProfiles
            } else {
                Log.d(TAG, "AAC_JSON PROFILES_LOADED count=${profiles.size}")
                profiles
            }
        } catch (error: Exception) {
            Log.w(TAG, "AAC_JSON PROFILES_PARSE_ERROR", error)
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            fallbackProfiles
        }
    }

    private fun parseProfilesFile(file: File): List<AacProfile> {
        val json = file.readText(Charsets.UTF_8).trim()
        return when {
            json.startsWith("[") -> parseProfilesArray(JSONArray(json))
            json.startsWith("{") -> {
                val obj = JSONObject(json)
                if (obj.has("profiles")) {
                    parseProfilesArray(obj.optJSONArray("profiles") ?: JSONArray())
                } else {
                    parseProfile(obj)?.let(::listOf).orEmpty()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseItemsArray(array: JSONArray): List<AacItem> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseItem(item, index)?.let(::add)
            }
        }
    }

    private fun parseProfilesArray(array: JSONArray): List<AacProfile> {
        return buildList {
            for (index in 0 until array.length()) {
                val profile = array.optJSONObject(index) ?: continue
                parseProfile(profile)?.let(::add)
            }
        }
    }

    private fun parseItem(json: JSONObject, priorityFallback: Int): AacItem? {
        val id = json.optString("id").trim()
        if (id.isBlank()) {
            return null
        }

        val label = json.optString("labelSl")
            .ifBlank { json.optString("text") }
            .ifBlank { json.optString("label") }
            .ifBlank { id.uppercase() }

        return AacItem(
            id = id,
            labelSl = label,
            imagePath = json.optString("imagePath"),
            audioSl = json.optString("audioSl"),
            actionType = json.optString("actionType").ifBlank { "speak" },
            targetPageId = json.optString("targetPageId"),
            speakTextSl = json.optNullableString("speakTextSl")
                ?: json.optNullableString("speechText")
                ?: json.optNullableString("text"),
            speakTextUk = json.optNullableString("speakTextUk"),
            conceptId = json.optNullableString("conceptId"),
            children = parseStringList(json.optJSONArray("children")),
            sentenceRole = json.optNullableString("sentenceRole"),
            questionSl = json.optNullableString("questionSl"),
            questionUk = json.optNullableString("questionUk"),
            iconSource = parseIconSource(json.optString("iconSource")),
            parentId = json.optNullableString("parentId"),
            isRootItem = if (json.has("isRootItem")) json.optBoolean("isRootItem", true) else json.optNullableString("parentId").isNullOrBlank(),
            isHiddenUntilParent = json.optBoolean("isHiddenUntilParent", false),
            priority = json.optInt("priority", priorityFallback),
            followUpQuestion = json.optNullableString("followUpQuestion"),
            vendingNumber = json.optNullableString("vendingNumber"),
            vendingInstructionImagePath = json.optNullableString("vendingInstructionImagePath"),
            largeCupImagePath = json.optNullableString("largeCupImagePath"),
            hasLargeCupOption = json.optBoolean("hasLargeCupOption", false)
        )
    }

    private fun parseProfile(json: JSONObject): AacProfile? {
        val id = json.optString("id").trim()
        if (id.isBlank()) {
            return null
        }

        val displayName = json.optString("displayName")
            .ifBlank { json.optString("name") }
            .ifBlank { id.uppercase() }

        return AacProfile(
            id = id,
            displayName = displayName,
            icon = json.optNullableString("icon"),
            context = parseContext(json.optString("context")),
            itemIds = parseStringList(json.optJSONArray("itemIds")),
            enabled = if (json.has("enabled")) json.optBoolean("enabled", true) else true
        )
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun parseContext(value: String?): AacCommunicationContext {
        return AacCommunicationContext.fromPreference(value)
    }

    private fun parseIconSource(value: String?): IconSource {
        return when (value?.trim()?.uppercase()) {
            "SOCA" -> IconSource.SOCA
            "ARASAAC" -> IconSource.ARASAAC
            "PATIENT" -> IconSource.PATIENT
            "CUSTOM" -> IconSource.CUSTOM
            "SYSTEM" -> IconSource.SYSTEM
            else -> IconSource.SYSTEM
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        val value = optString(name).trim()
        return value.takeIf { it.isNotEmpty() }
    }
}

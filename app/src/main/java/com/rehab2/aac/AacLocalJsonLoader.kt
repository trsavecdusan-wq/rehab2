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

    fun loadProfiles(context: Context): List<AacProfile> {
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val files = profilesDir?.takeIf { it.exists() && it.isDirectory }?.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty()

        if (files.isEmpty()) {
            Log.d(TAG, "AAC_JSON PROFILES_FILE_MISSING")
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            return emptyList()
        }

        val profiles = buildList {
            files.forEach { file ->
                try {
                    addAll(parseProfilesFile(file))
                } catch (error: Exception) {
                    Log.w(TAG, "AAC_JSON PROFILES_PARSE_ERROR file=${file.name}", error)
                }
            }
        }.filter { it.enabled }

        return if (profiles.isEmpty()) {
            Log.d(TAG, "AAC_JSON PROFILES_PARSE_ERROR")
            Log.d(TAG, "AAC_JSON FALLBACK_SYSTEM_DATA")
            emptyList()
        } else {
            Log.d(TAG, "AAC_JSON PROFILES_LOADED count=${profiles.size}")
            profiles
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
        val actionType = json.optString("actionType").ifBlank { "speak" }
        val children = parseStringList(json.optJSONArray("children"))

        return AacItem(
            id = id,
            labelSl = label,
            imagePath = json.optString("imagePath"),
            audioSl = json.optString("audioSl"),
            actionType = actionType,
            targetPageId = json.optString("targetPageId"),
            speakTextSl = json.optNullableString("speakTextSl")
                ?: json.optNullableString("speechText")
                ?: json.optNullableString("text"),
            speakTextUk = json.optNullableString("speakTextUk"),
            labelUk = json.optNullableString("labelUk"),
            labelEn = json.optNullableString("labelEn"),
            speechText = json.optNullableString("speechText"),
            speechTextEn = json.optNullableString("speechTextEn")
                ?: json.optNullableString("speakTextEn"),
            baseLanguage = normalizeLanguageCode(
                json.optNullableString("baseLanguage")
                    ?: json.optNullableString("base_language")
            ),
            activeLanguages = parseActiveLanguages(json),
            labelByLanguage = parseLanguageTextMap(json.optJSONObject("labelByLanguage")),
            speechTextByLanguage = parseLanguageTextMap(json.optJSONObject("speechTextByLanguage")),
            translationCacheMeta = parseTranslationCacheMeta(json.optJSONObject("translationCacheMeta")),
            translationGenerated = json.optBoolean("translationGenerated", false),
            translationSource = json.optNullableString("translationSource"),
            translationManualOverride = json.optBoolean("translationManualOverride", false),
            learningRepresentations = parseLearningRepresentations(json.optJSONArray("learningRepresentations")),
            categoryId = json.optNullableString("categoryId")
                ?: json.optNullableString("category"),
            scenarioIds = parseStringList(json.optJSONArray("scenarioIds")),
            conceptId = json.optNullableString("conceptId"),
            children = children,
            sentenceRole = json.optNullableString("sentenceRole"),
            questionSl = json.optNullableString("questionSl"),
            questionUk = json.optNullableString("questionUk"),
            questionByLanguage = parseLanguageTextMap(json.optJSONObject("questionByLanguage")),
            iconSource = parseIconSource(json.optString("iconSource")),
            parentId = json.optNullableString("parentId"),
            visibleUnderIds = parseVisibleUnderIds(json),
            placements = parsePlacements(json.optJSONArray("placements")),
            isRootItem = if (json.has("isRootItem")) json.optBoolean("isRootItem", true) else json.optNullableString("parentId").isNullOrBlank(),
            isHiddenUntilParent = json.optBoolean("isHiddenUntilParent", false),
            fixedTopRowPosition = json.optFixedTopRowPosition(),
            addsToSentence = if (json.has("addsToSentence")) json.optBoolean("addsToSentence", true) else true,
            speaksImmediately = if (json.has("speaksImmediately")) json.optBoolean("speaksImmediately", true) else true,
            opensSubicons = if (json.has("opensSubicons")) {
                json.optBoolean("opensSubicons", false)
            } else {
                children.isNotEmpty() || actionType == "open_page"
            },
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

    private fun parseTranslationCacheMeta(json: JSONObject?): Map<String, AacTranslationCacheEntry> {
        if (json == null) return emptyMap()
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val languageCode = AacLanguageResolver.normalize(keys.next())
                val entry = json.optJSONObject(languageCode) ?: continue
                val sourceTextHash = entry.optString("sourceTextHash").trim()
                if (languageCode.isBlank() || sourceTextHash.isBlank()) continue
                put(
                    languageCode,
                    AacTranslationCacheEntry(
                        sourceLanguage = AacLanguageResolver.normalize(entry.optString("sourceLanguage")),
                        sourceText = entry.optString("sourceText"),
                        sourceTextHash = sourceTextHash,
                        targetLanguage = AacLanguageResolver.normalize(entry.optString("targetLanguage").ifBlank { languageCode }),
                        translatedAt = entry.optString("translatedAt"),
                        provider = entry.optNullableString("provider"),
                        model = entry.optNullableString("model")
                    )
                )
            }
        }
    }

    private fun parseVisibleUnderIds(json: JSONObject): List<String> {
        val explicitVisibility = parseStringList(json.optJSONArray("visibleUnderIds"))
        val parentIds = parseStringList(json.optJSONArray("parentIds"))
        val legacyParentId = json.optNullableString("parentId")
        return (explicitVisibility + parentIds + listOfNotNull(legacyParentId))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parsePlacements(array: JSONArray?): List<AacPlacement> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val placement = array.optJSONObject(index) ?: continue
                val pageId = placement.optString("pageId").trim()
                val position5x5 = placement.optInt("position5x5", 0)
                if (pageId.isNotEmpty() && position5x5 in 1..25) {
                    add(AacPlacement(pageId = pageId, position5x5 = position5x5))
                }
            }
        }
    }

    private fun parseActiveLanguages(json: JSONObject): List<String> {
        val parsed = parseStringList(json.optJSONArray("activeLanguages"))
            .map(::normalizeLanguageCode)
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ACTIVE_LANGUAGES)
        return parsed.ifEmpty { listOf(AacLanguageResolver.DEFAULT_LANGUAGE_CODE) }
    }

    private fun parseLanguageTextMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val languageCode = normalizeLanguageCode(key)
                val value = obj.optString(key).trim()
                if (languageCode.isNotBlank() && value.isNotEmpty()) {
                    put(languageCode, value)
                }
            }
        }
    }

    private fun parseLearningRepresentations(array: JSONArray?): List<AacLearningRepresentation> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val representation = array.optJSONObject(index) ?: continue
                val mode = representation.optString("mode").trim()
                if (mode.isBlank()) continue
                add(
                    AacLearningRepresentation(
                        mode = mode,
                        imagePath = representation.optNullableString("imagePath"),
                        textByLanguage = parseLanguageTextMap(representation.optJSONObject("textByLanguage")),
                        answerVariants = parseLearningAnswerVariants(representation.optJSONArray("answerVariants"))
                    )
                )
            }
        }
    }

    private fun parseLearningAnswerVariants(array: JSONArray?): List<AacLearningAnswerVariant> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val answer = array.optJSONObject(index) ?: continue
                val id = answer.optString("id").trim()
                if (id.isBlank()) continue
                add(
                    AacLearningAnswerVariant(
                        id = id,
                        textByLanguage = parseLanguageTextMap(answer.optJSONObject("textByLanguage")),
                        correct = answer.optBoolean("correct", false)
                    )
                )
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

    private fun normalizeLanguageCode(value: String?): String {
        return AacLanguageResolver.normalize(value)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        val value = optString(name).trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optFixedTopRowPosition(): Int? {
        val value = when {
            has("fixedTopRowPosition") -> optInt("fixedTopRowPosition", 0)
            has("fixed_top_row_position") -> optInt("fixed_top_row_position", 0)
            else -> 0
        }
        return value.takeIf { it in 1..5 }
    }

    private const val MAX_ACTIVE_LANGUAGES = 3
}

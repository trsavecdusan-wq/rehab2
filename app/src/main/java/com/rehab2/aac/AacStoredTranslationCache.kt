package com.rehab2.aac

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

object AacStoredTranslationCache {
    data class Translation(
        val label: String,
        val speechText: String,
        val cacheEntry: AacTranslationCacheEntry? = null
    )

    data class PretranslationResult(
        val languageCode: String,
        val missingCount: Int,
        val translatedCount: Int,
        val failedCount: Int
    )

    fun ensureTranslation(
        context: Context,
        item: AacItem,
        languageCode: String
    ): Translation? {
        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        if (normalizedLanguage == AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return null
        }

        val hasStoredLabel = AacLocalizedTextResolver.hasStoredLabelForLanguage(item, normalizedLanguage)
        val hasStoredSpeechText = AacLocalizedTextResolver.hasStoredSpeakTextForLanguage(item, normalizedLanguage)
        val storedLabel = AacLocalizedTextResolver.resolveLabel(item, normalizedLanguage)
        val storedSpeechText = AacLocalizedTextResolver.resolveSpeakText(item, normalizedLanguage)
        val refreshNeeded = needsTranslationRefresh(item, normalizedLanguage)
        if (hasStoredLabel && hasStoredSpeechText && !refreshNeeded) {
            return Translation(storedLabel, storedSpeechText, item.translationCacheMeta[normalizedLanguage])
        }

        val generated = generateTranslation(context, item, normalizedLanguage) ?: return null
        return if (saveTranslation(context, item, normalizedLanguage, generated)) {
            Translation(
                label = if (hasStoredLabel && !refreshNeeded) storedLabel else generated.label,
                speechText = if (hasStoredSpeechText && !refreshNeeded) storedSpeechText else generated.speechText,
                cacheEntry = generated.cacheEntry
            )
        } else {
            null
        }
    }

    fun needsTranslationRefresh(item: AacItem, languageCode: String): Boolean {
        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        if (normalizedLanguage == AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return false
        }

        val hasStoredLabel = AacLocalizedTextResolver.hasStoredLabelForLanguage(item, normalizedLanguage)
        val hasStoredSpeechText = AacLocalizedTextResolver.hasStoredSpeakTextForLanguage(item, normalizedLanguage)
        if (!hasStoredLabel || !hasStoredSpeechText) {
            return true
        }
        if (item.translationManualOverride) {
            return false
        }

        val meta = item.translationCacheMeta[normalizedLanguage]
        if (meta == null) {
            return item.translationGenerated || item.translationSource == "ai"
        }
        return meta.sourceTextHash != sourceTextHash(item)
    }

    fun ensureTranslationsForLanguage(
        context: Context,
        languageCode: String,
        items: List<AacItem>
    ): PretranslationResult {
        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        if (normalizedLanguage == AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            val result = PretranslationResult(normalizedLanguage, 0, 0, 0)
            savePretranslationResult(context, result)
            return result
        }

        var missingCount = 0
        var translatedCount = 0
        var failedCount = 0
        items.asSequence()
            .filter { item -> item.id.isNotBlank() && item.labelSl.isNotBlank() }
            .distinctBy { item -> item.id }
            .forEach { item ->
                if (!needsTranslationRefresh(item, normalizedLanguage)) {
                    return@forEach
                }
                missingCount += 1
                if (ensureTranslation(context, item, normalizedLanguage) != null) {
                    translatedCount += 1
                } else {
                    failedCount += 1
                }
            }
        val result = PretranslationResult(normalizedLanguage, missingCount, translatedCount, failedCount)
        savePretranslationResult(context, result)
        return result
    }

    fun readLastPretranslationResult(context: Context): PretranslationResult? {
        val prefs = context.getSharedPreferences(PREFS_PRETRANSLATION_STATUS, Context.MODE_PRIVATE)
        val languageCode = prefs.getString(KEY_PRETRANSLATION_LANGUAGE, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return PretranslationResult(
            languageCode = languageCode,
            missingCount = prefs.getInt(KEY_PRETRANSLATION_MISSING, 0),
            translatedCount = prefs.getInt(KEY_PRETRANSLATION_TRANSLATED, 0),
            failedCount = prefs.getInt(KEY_PRETRANSLATION_FAILED, 0)
        )
    }

    private fun generateTranslation(
        context: Context,
        item: AacItem,
        languageCode: String
    ): Translation? {
        val config = AacSpeechApiConfig.read(context)
        if (!config.isOpenAiEnabled()) {
            return null
        }

        val sourceLabel = item.labelSl.trim().ifBlank { item.id }
        val sourceSpeech = item.speakTextSl?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: item.speechText?.trim()?.takeIf { it.isNotBlank() }
            ?: sourceLabel
        val sourceText = sourceTextFor(item)
        val sourceLanguage = AacLanguageResolver.normalize(item.baseLanguage).ifBlank {
            AacLanguageResolver.DEFAULT_LANGUAGE_CODE
        }
        val sourceTextHash = sourceTextHash(item)
        val model = translationModel(config.normalizedModel())

        return try {
            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put(
                            "content",
                            "Translate AAC patient button text. Return only compact JSON with keys label and speechText. Keep meaning simple and patient-safe."
                        )
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "Target language: ${languageName(languageCode)} ($languageCode)\nLabel: $sourceLabel\nSpeech text: $sourceSpeech"
                        )
                    })
                })
                put("temperature", 0)
            }
            val request = Request.Builder()
                .url(buildEndpoint(config.normalizedBaseUrl(), "v1/chat/completions"))
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AAC translation failed HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string().orEmpty()
                val content = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                parseTranslation(content)?.let { translation ->
                    translation.copy(
                        cacheEntry = AacTranslationCacheEntry(
                            sourceLanguage = sourceLanguage,
                            sourceText = sourceText,
                            sourceTextHash = sourceTextHash,
                            targetLanguage = languageCode,
                            translatedAt = System.currentTimeMillis().toString(),
                            provider = "openai",
                            model = model
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "AAC translation failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun saveTranslation(
        context: Context,
        sourceItem: AacItem,
        languageCode: String,
        translation: Translation
    ): Boolean {
        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return false
        if (itemsFile.exists() && (!itemsFile.isFile || itemsFile.length() > MAX_ITEMS_BYTES)) return false
        return try {
            itemsFile.parentFile?.mkdirs()
            val itemsText = if (itemsFile.isFile) itemsFile.readText(Charsets.UTF_8) else ""
            val trimmed = itemsText.trimStart()
            val rootObject = when {
                trimmed.isBlank() -> JSONObject()
                trimmed.startsWith("[") -> null
                else -> JSONObject(itemsText)
            }
            val itemsArray = rootObject?.optJSONArray("items")
                ?: if (rootObject == null) JSONArray(itemsText) else JSONArray().also { rootObject.put("items", it) }
            val item = findItemById(itemsArray, sourceItem.id) ?: sourceItem.toJson().also { itemsArray.put(it) }
            val labelByLanguage = item.optJSONObject("labelByLanguage") ?: JSONObject()
            val speechTextByLanguage = item.optJSONObject("speechTextByLanguage") ?: JSONObject()
            val existingMeta = parseCacheEntry(item.optJSONObject("translationCacheMeta")?.optJSONObject(languageCode))
            val stale = existingMeta?.sourceTextHash != sourceTextHash(sourceItem)
            val generatedLegacyTranslation = sourceItem.translationGenerated || sourceItem.translationSource == "ai"
            val mayOverwrite = !sourceItem.translationManualOverride &&
                ((existingMeta != null && stale) || (existingMeta == null && generatedLegacyTranslation))
            if (labelByLanguage.optString(languageCode).trim().isBlank() || mayOverwrite) {
                labelByLanguage.put(languageCode, translation.label)
            }
            if (speechTextByLanguage.optString(languageCode).trim().isBlank() || mayOverwrite) {
                speechTextByLanguage.put(languageCode, translation.speechText)
            }
            item.put("labelByLanguage", labelByLanguage)
            item.put("speechTextByLanguage", speechTextByLanguage)
            val translationCacheMeta = item.optJSONObject("translationCacheMeta") ?: JSONObject()
            translation.cacheEntry?.let { cacheEntry ->
                translationCacheMeta.put(languageCode, cacheEntry.toJson(sourceItem.id))
                item.put("translationCacheMeta", translationCacheMeta)
            }
            item.put("translationGenerated", true)
            item.put("translationSource", "ai")
            val output = rootObject?.toString(2) ?: itemsArray.toString(2)
            itemsFile.writeText(output, Charsets.UTF_8)
            true
        } catch (error: Exception) {
            Log.w(TAG, "AAC translation save failed: ${error.javaClass.simpleName}")
            false
        }
    }

    private fun savePretranslationResult(context: Context, result: PretranslationResult) {
        context.getSharedPreferences(PREFS_PRETRANSLATION_STATUS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRETRANSLATION_LANGUAGE, result.languageCode)
            .putInt(KEY_PRETRANSLATION_MISSING, result.missingCount)
            .putInt(KEY_PRETRANSLATION_TRANSLATED, result.translatedCount)
            .putInt(KEY_PRETRANSLATION_FAILED, result.failedCount)
            .apply()
    }

    private fun AacItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("labelSl", labelSl)
            put("imagePath", imagePath)
            put("audioSl", audioSl)
            put("actionType", actionType)
            put("targetPageId", targetPageId)
            speakTextSl?.let { put("speakTextSl", it) }
            speakTextUk?.let { put("speakTextUk", it) }
            labelUk?.let { put("labelUk", it) }
            labelEn?.let { put("labelEn", it) }
            speechText?.let { put("speechText", it) }
            speechTextEn?.let { put("speechTextEn", it) }
            put("baseLanguage", baseLanguage)
            put("activeLanguages", JSONArray(activeLanguages))
            if (labelByLanguage.isNotEmpty()) put("labelByLanguage", JSONObject(labelByLanguage))
            if (speechTextByLanguage.isNotEmpty()) put("speechTextByLanguage", JSONObject(speechTextByLanguage))
            if (translationCacheMeta.isNotEmpty()) {
                put("translationCacheMeta", JSONObject().apply {
                    translationCacheMeta.forEach { (languageCode, entry) ->
                        put(languageCode, entry.toJson(id))
                    }
                })
            }
            put("translationGenerated", translationGenerated)
            translationSource?.let { put("translationSource", it) }
            put("translationManualOverride", translationManualOverride)
            categoryId?.let { put("categoryId", it) }
            if (scenarioIds.isNotEmpty()) {
                put("scenarioIds", JSONArray().apply {
                    scenarioIds.forEach { scenarioId -> put(scenarioId) }
                })
            }
            conceptId?.let { put("conceptId", it) }
            if (children.isNotEmpty()) put("children", JSONArray(children))
            sentenceRole?.let { put("sentenceRole", it) }
            questionSl?.let { put("questionSl", it) }
            questionUk?.let { put("questionUk", it) }
            if (questionByLanguage.isNotEmpty()) put("questionByLanguage", JSONObject(questionByLanguage))
            put("iconSource", iconSource.name)
            parentId?.let { put("parentId", it) }
            if (visibleUnderIds.isNotEmpty()) put("visibleUnderIds", JSONArray(visibleUnderIds))
            if (placements.isNotEmpty()) {
                put(
                    "placements",
                    JSONArray().apply {
                        placements.forEach { placement ->
                            put(JSONObject().apply {
                                put("pageId", placement.pageId)
                                put("position5x5", placement.position5x5)
                            })
                        }
                    }
                )
            }
            put("isRootItem", isRootItem)
            put("isHiddenUntilParent", isHiddenUntilParent)
            fixedTopRowPosition?.let { put("fixedTopRowPosition", it) }
            put("addsToSentence", addsToSentence)
            put("speaksImmediately", speaksImmediately)
            put("opensSubicons", opensSubicons)
            put("priority", priority)
        }
    }

    private fun findItemById(itemsArray: JSONArray, itemId: String): JSONObject? {
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            if (item.optString("id").trim() == itemId) {
                return item
            }
        }
        return null
    }

    private fun parseTranslation(content: String): Translation? {
        val jsonText = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val json = JSONObject(jsonText)
            val label = json.optString("label").trim()
            val speechText = json.optString("speechText").trim().ifBlank { label }
            if (label.isBlank()) null else Translation(label = label, speechText = speechText)
        } catch (_: Exception) {
            val clean = jsonText.lineSequence().firstOrNull()?.trim().orEmpty()
            if (clean.isBlank()) null else Translation(label = clean, speechText = clean)
        }
    }

    private fun AacTranslationCacheEntry.toJson(itemId: String): JSONObject {
        return JSONObject().apply {
            put("itemId", itemId)
            put("sourceLanguage", sourceLanguage)
            put("sourceText", sourceText)
            put("sourceTextHash", sourceTextHash)
            put("targetLanguage", targetLanguage)
            put("translatedAt", translatedAt)
            provider?.let { put("provider", it) }
            model?.let { put("model", it) }
        }
    }

    private fun parseCacheEntry(json: JSONObject?): AacTranslationCacheEntry? {
        if (json == null) return null
        val sourceTextHash = json.optString("sourceTextHash").trim()
        if (sourceTextHash.isBlank()) return null
        return AacTranslationCacheEntry(
            sourceLanguage = AacLanguageResolver.normalize(json.optString("sourceLanguage")),
            sourceText = json.optString("sourceText"),
            sourceTextHash = sourceTextHash,
            targetLanguage = AacLanguageResolver.normalize(json.optString("targetLanguage")),
            translatedAt = json.optString("translatedAt"),
            provider = json.optString("provider").trim().takeIf { it.isNotBlank() },
            model = json.optString("model").trim().takeIf { it.isNotBlank() }
        )
    }

    private fun sourceTextFor(item: AacItem): String {
        val label = item.labelSl.trim().ifBlank { item.id.trim() }
        val speech = item.speakTextSl?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: item.speechText?.trim()?.takeIf { it.isNotBlank() }
            ?: label
        return "label=$label\nspeech=$speech"
    }

    private fun sourceTextHash(item: AacItem): String {
        val normalized = sourceTextFor(item).trim().replace(Regex("\\s+"), " ")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun translationModel(configuredModel: String): String {
        return if (configuredModel.contains("tts", ignoreCase = true)) {
            DEFAULT_TRANSLATION_MODEL
        } else {
            configuredModel
        }
    }

    private fun buildEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val cleanPath = path.trim().trimStart('/')
        if (base.endsWith("/v1")) {
            return "$base/${cleanPath.removePrefix("v1/")}"
        }
        return "$base/$cleanPath"
    }

    private fun languageName(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "uk", "ua" -> "Ukrainian"
            "en" -> "English"
            "de" -> "German"
            "hr" -> "Croatian"
            "sr" -> "Serbian"
            else -> languageCode
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val TAG = "AacTranslationCache"
    private const val DEFAULT_TRANSLATION_MODEL = "gpt-4o-mini"
    private const val MAX_ITEMS_BYTES = 512 * 1024L
    private const val PREFS_PRETRANSLATION_STATUS = "aac_pretranslation_status"
    private const val KEY_PRETRANSLATION_LANGUAGE = "language"
    private const val KEY_PRETRANSLATION_MISSING = "missing"
    private const val KEY_PRETRANSLATION_TRANSLATED = "translated"
    private const val KEY_PRETRANSLATION_FAILED = "failed"
}

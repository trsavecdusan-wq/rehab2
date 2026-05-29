package com.rehab2.aac

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object AacStoredTranslationCache {
    data class Translation(
        val label: String,
        val speechText: String
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

        val storedLabel = AacLocalizedTextResolver.resolveLabel(item, normalizedLanguage)
        val storedSpeechText = AacLocalizedTextResolver.resolveSpeakText(item, normalizedLanguage)
        val hasStoredTranslation =
            AacLocalizedTextResolver.hasStoredLabelForLanguage(item, normalizedLanguage) ||
                AacLocalizedTextResolver.hasStoredSpeakTextForLanguage(item, normalizedLanguage)
        if (hasStoredTranslation) {
            return Translation(storedLabel, storedSpeechText)
        }

        val generated = generateTranslation(context, item, normalizedLanguage) ?: return null
        return if (saveTranslation(context, item.id, normalizedLanguage, generated)) {
            generated
        } else {
            null
        }
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

        return try {
            val requestJson = JSONObject().apply {
                put("model", translationModel(config.normalizedModel()))
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
                parseTranslation(content)
            }
        } catch (error: Exception) {
            Log.w(TAG, "AAC translation failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun saveTranslation(
        context: Context,
        itemId: String,
        languageCode: String,
        translation: Translation
    ): Boolean {
        val itemsFile = AacStoragePaths.getAacItemsFile(context) ?: return false
        if (!itemsFile.isFile || itemsFile.length() > MAX_ITEMS_BYTES) return false
        return try {
            val itemsText = itemsFile.readText(Charsets.UTF_8)
            val trimmed = itemsText.trimStart()
            val rootObject = if (trimmed.startsWith("[")) null else JSONObject(itemsText)
            val itemsArray = rootObject?.optJSONArray("items")
                ?: if (rootObject == null) JSONArray(itemsText) else return false
            val item = findItemById(itemsArray, itemId) ?: return false
            val labelByLanguage = item.optJSONObject("labelByLanguage") ?: JSONObject()
            val speechTextByLanguage = item.optJSONObject("speechTextByLanguage") ?: JSONObject()
            labelByLanguage.put(languageCode, translation.label)
            speechTextByLanguage.put(languageCode, translation.speechText)
            item.put("labelByLanguage", labelByLanguage)
            item.put("speechTextByLanguage", speechTextByLanguage)
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
}

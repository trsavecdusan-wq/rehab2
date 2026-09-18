package com.rehab2.aac

import org.json.JSONObject
import java.nio.charset.Charset

/** Merge a system repair proposal without replacing patient/therapist content. */
internal object AacUpgradeFieldPolicy {
    private val textFields = setOf(
        "labelSl", "labelUk", "labelEn", "text", "baseText", "base_text",
        "speechText", "speakTextSl", "speakTextUk", "speechTextSl", "speechTextEn",
        "questionSl", "questionUk", "followUpQuestion", "customSpeech", "customLabel"
    )
    private val languageFields = setOf(
        "labelByLanguage", "speechTextByLanguage", "questionByLanguage", "translations"
    )
    private val editFlags = setOf(
        "userEdited", "locked", "lockedByUser", "modifiedByTherapist", "therapistEdited",
        "manualEdit", "manualOverride", "customized", "translationManualOverride"
    )

    fun hasCustomImage(item: JSONObject): Boolean {
        val path = item.optString("imagePath").trim()
        val sources = listOf(item.optString("iconSource"), item.optString("source"))
            .map { it.trim().uppercase() }
        return path.isNotBlank() && (
            sources.any { it in setOf("PATIENT", "CUSTOM", "THERAPIST", "CUSTOM_PHOTO", "PATIENT_PHOTO", "PLACE_PHOTO", "SOCA", "ARASAAC") } ||
                !path.startsWith("system/", ignoreCase = true)
            )
    }

    fun mergeUserFields(before: JSONObject, proposed: JSONObject) {
        val edited = editFlags.any { before.optBoolean(it, false) } ||
            before.optString("source").trim().uppercase() in setOf("PATIENT", "CUSTOM", "THERAPIST")
        // Only explicit editing provenance protects text; shipped legacy values remain repairable.
        if (edited) {
            textFields.forEach { key ->
                if (before.has(key)) proposed.put(key, before.get(key)) else proposed.remove(key)
            }
            languageFields.forEach { key ->
                val old = before.optJSONObject(key)
                if (old != null) {
                    proposed.put(key, JSONObject(old.toString()))
                } else {
                    if (before.has(key)) proposed.put(key, before.get(key)) else proposed.remove(key)
                }
            }
        }
        if (hasCustomImage(before)) {
            listOf("imagePath", "iconSource", "suggestedIconPath").forEach { key ->
                if (before.has(key)) proposed.put(key, before.get(key)) else proposed.remove(key)
            }
        }
        // Editing and translation provenance is never part of a system repair.
        (editFlags + setOf("source", "translationSource", "translationCacheMeta", "translationGenerated")).forEach { key ->
            if (before.has(key)) proposed.put(key, before.get(key))
        }
    }

    /** Match only encodings of known shipped values, never decode arbitrary user text. */
    fun repairKnownEncoding(item: JSONObject, knownValues: Map<String, String>): Int {
        var count = 0
        (textFields + languageFields).forEach { key ->
            val value = item.opt(key)
            if (value is String) {
                knownValues[value]?.let { corrected -> item.put(key, corrected); count++ }
            } else if (value is JSONObject) {
                value.keys().forEach { language ->
                    knownValues[value.optString(language)]?.let { corrected ->
                        value.put(language, corrected)
                        count++
                    }
                }
            }
        }
        return count
    }

    fun legacyEncoding(value: String): String {
        val windows = Charset.forName("windows-1250")
        return value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val decoded = String(byteArrayOf(byte), windows)
            if (decoded == "\uFFFD") (byte.toInt() and 255).toChar().toString() else decoded
        }
    }
}

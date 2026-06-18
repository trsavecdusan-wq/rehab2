package com.rehab2.aac

import android.util.Log

object AacLocalizedTextResolver {
    data class ResolvedSpeechText(
        val text: String,
        val languageCode: String,
        val usedLanguageFallback: Boolean
    )

    fun resolveLabel(item: AacItem, languageCode: String): String {
        val localizedTexts = item.toLocalizedTexts()
        return resolveText(localizedTexts, languageCode) { it.label }
            ?: item.labelSl.takeIf { it.isNotBlank() }
            ?: item.id
    }

    fun resolveSpeakText(item: AacItem, languageCode: String): String {
        return resolveSpeakTextResult(item, languageCode).text
    }

    fun resolveSpeakTextResult(item: AacItem, languageCode: String): ResolvedSpeechText {
        val localizedTexts = item.toLocalizedTexts()
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        if (selectedCode == "uk") {
            val ukrainianText = localizedTexts["uk"]
            ukrainianText?.speakText.clean()?.let { text ->
                return ResolvedSpeechText(text, selectedCode, usedLanguageFallback = false)
            }
            ukrainianText?.label.clean()?.let { text ->
                Log.w(TAG, "Missing uk speechText itemId=${item.id}; using uk label fallback")
                return ResolvedSpeechText(text, selectedCode, usedLanguageFallback = true)
            }
            localizedTexts[AacLanguageResolver.DEFAULT_LANGUAGE_CODE]?.speakText.clean()?.let { text ->
                Log.w(TAG, "Missing uk speechText itemId=${item.id}; using sl speech fallback")
                return ResolvedSpeechText(
                    text,
                    AacLanguageResolver.DEFAULT_LANGUAGE_CODE,
                    usedLanguageFallback = true
                )
            }
            localizedTexts[AacLanguageResolver.DEFAULT_LANGUAGE_CODE]?.label.clean()?.let { text ->
                Log.w(TAG, "Missing uk speechText itemId=${item.id}; using sl label fallback")
                return ResolvedSpeechText(
                    text,
                    AacLanguageResolver.DEFAULT_LANGUAGE_CODE,
                    usedLanguageFallback = true
                )
            }
            Log.w(TAG, "Missing uk speechText itemId=${item.id}; no fallback text available")
            return ResolvedSpeechText("", selectedCode, usedLanguageFallback = true)
        }

        return ResolvedSpeechText(
            text = resolveText(localizedTexts, languageCode) { it.speakText }
                ?: resolveLabel(item, languageCode),
            languageCode = selectedCode,
            usedLanguageFallback = false
        )
    }

    fun resolveQuestion(item: AacItem, languageCode: String): String? {
        val localizedTexts = item.toLocalizedTexts()
        return resolveText(localizedTexts, languageCode) { it.question }
    }

    fun hasStoredLabelForLanguage(item: AacItem, languageCode: String): Boolean {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        return item.toLocalizedTexts()[selectedCode]?.label.clean() != null
    }

    fun hasStoredSpeakTextForLanguage(item: AacItem, languageCode: String): Boolean {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        return item.toLocalizedTexts()[selectedCode]?.speakText.clean() != null
    }

    private fun resolveText(
        localizedTexts: Map<String, AacLocalizedText>,
        languageCode: String,
        selector: (AacLocalizedText) -> String?
    ): String? {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        if (selectedCode == AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return localizedTexts[AacLanguageResolver.DEFAULT_LANGUAGE_CODE]?.let(selector).clean()
        }
        return localizedTexts[selectedCode]?.let(selector).clean()
            ?: localizedTexts[AacLanguageResolver.DEFAULT_LANGUAGE_CODE]?.let(selector).clean()
            ?: localizedTexts.values.firstNotNullOfOrNull { selector(it).clean() }
    }

    private fun AacItem.toLocalizedTexts(): Map<String, AacLocalizedText> {
        return buildMap {
            put(
                AacLanguageResolver.DEFAULT_LANGUAGE_CODE,
                AacLocalizedText(
                    label = labelSl,
                    speakText = speakTextSl ?: speechText ?: labelSl,
                    question = questionSl,
                    learningText = labelSl
                )
            )
            val ukText = AacLocalizedText(
                label = labelUk,
                speakText = speakTextUk,
                question = questionUk
            )
            if (ukText.label != null || ukText.speakText != null || ukText.question != null) {
                put("uk", ukText)
            }
            val enText = AacLocalizedText(
                label = labelEn,
                speakText = speechTextEn
            )
            if (enText.label != null || enText.speakText != null) {
                put("en", enText)
            }
            val localLanguageCodes = (activeLanguages + labelByLanguage.keys + speechTextByLanguage.keys + questionByLanguage.keys)
                .map(AacLanguageResolver::normalize)
                .filter { it.isNotBlank() }
                .distinct()
            localLanguageCodes.forEach { languageCode ->
                val existingText = get(languageCode)
                val localText = AacLocalizedText(
                    label = labelByLanguage[languageCode],
                    speakText = speechTextByLanguage[languageCode],
                    question = questionByLanguage[languageCode],
                    learningText = labelByLanguage[languageCode]
                )
                put(
                    languageCode,
                    AacLocalizedText(
                        label = localText.label ?: existingText?.label,
                        speakText = localText.speakText ?: existingText?.speakText,
                        question = localText.question ?: existingText?.question,
                        learningText = localText.learningText ?: existingText?.learningText
                    )
                )
            }
        }
    }

    private fun String?.clean(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val TAG = "AacLocalizedText"
}

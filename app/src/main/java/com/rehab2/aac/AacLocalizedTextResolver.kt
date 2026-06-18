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
            ?: resolveText(localizedTexts, languageCode) { it.speakText }
            ?: safeMissingTextFallback(item, languageCode)
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
            Log.w(TAG, "Missing uk speechText itemId=${item.id}; no fallback text available")
            return ResolvedSpeechText("", selectedCode, usedLanguageFallback = true)
        }

        if (selectedCode != AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            val selectedText = resolveText(localizedTexts, selectedCode) { it.speakText }
                ?: resolveText(localizedTexts, selectedCode) { it.label }
            return ResolvedSpeechText(
                text = selectedText.orEmpty(),
                languageCode = selectedCode,
                usedLanguageFallback = selectedText.isNullOrBlank()
            )
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

    fun hasStoredQuestionForLanguage(item: AacItem, languageCode: String): Boolean {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        return item.toLocalizedTexts()[selectedCode]?.question.clean() != null
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
    }

    private fun safeMissingTextFallback(item: AacItem, languageCode: String): String? {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
        if (selectedCode == AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return item.labelSl.takeIf { it.isNotBlank() }
        }
        return item.id
            .trim()
            .replace('_', ' ')
            .uppercase()
            .takeIf { it.isNotBlank() }
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

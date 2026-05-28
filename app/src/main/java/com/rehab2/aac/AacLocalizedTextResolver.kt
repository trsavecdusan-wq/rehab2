package com.rehab2.aac

object AacLocalizedTextResolver {
    fun resolveLabel(item: AacItem, languageCode: String): String {
        val localizedTexts = item.toLocalizedTexts()
        return resolveText(localizedTexts, languageCode) { it.label }
            ?: item.labelSl.takeIf { it.isNotBlank() }
            ?: item.id
    }

    fun resolveSpeakText(item: AacItem, languageCode: String): String {
        val localizedTexts = item.toLocalizedTexts()
        return resolveText(localizedTexts, languageCode) { it.speakText }
            ?: resolveLabel(item, languageCode)
    }

    fun resolveQuestion(item: AacItem, languageCode: String): String? {
        val localizedTexts = item.toLocalizedTexts()
        return resolveText(localizedTexts, languageCode) { it.question }
    }

    private fun resolveText(
        localizedTexts: Map<String, AacLocalizedText>,
        languageCode: String,
        selector: (AacLocalizedText) -> String?
    ): String? {
        val selectedCode = AacLanguageResolver.normalize(languageCode)
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
        }
    }

    private fun String?.clean(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

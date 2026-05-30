package com.rehab2.aac

data class AacPlacement(
    val pageId: String,
    val position5x5: Int
)

data class AacLearningAnswerVariant(
    val id: String,
    val textByLanguage: Map<String, String> = emptyMap(),
    val correct: Boolean = false
)

data class AacLearningRepresentation(
    val mode: String,
    val imagePath: String? = null,
    val textByLanguage: Map<String, String> = emptyMap(),
    val answerVariants: List<AacLearningAnswerVariant> = emptyList()
)

data class AacTranslationCacheEntry(
    val sourceLanguage: String,
    val sourceText: String,
    val sourceTextHash: String,
    val targetLanguage: String,
    val translatedAt: String,
    val provider: String? = null,
    val model: String? = null
)

data class AacItem(
    val id: String,
    // Keep current JSON fields compatible while future content can add base_text,
    // translations, learning answer options, and cached audio metadata alongside them.
    val labelSl: String,
    val imagePath: String,
    val audioSl: String = "",
    val actionType: String,
    val targetPageId: String,
    val speakTextSl: String? = null,
    val speakTextUk: String? = null,
    val labelUk: String? = null,
    val labelEn: String? = null,
    val speechText: String? = null,
    val speechTextEn: String? = null,
    val baseLanguage: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE,
    val activeLanguages: List<String> = listOf(AacLanguageResolver.DEFAULT_LANGUAGE_CODE),
    val labelByLanguage: Map<String, String> = emptyMap(),
    val speechTextByLanguage: Map<String, String> = emptyMap(),
    val translationCacheMeta: Map<String, AacTranslationCacheEntry> = emptyMap(),
    // Translations are saved metadata. Patient runtime must resolve local text only, never live-translate.
    val translationGenerated: Boolean = false,
    val translationSource: String? = null,
    val translationManualOverride: Boolean = false,
    // Learning reuses this AAC concept instead of creating a duplicate learning database object.
    val learningRepresentations: List<AacLearningRepresentation> = emptyList(),
    // Therapist organization only. Patient placement is controlled by placements/visibility metadata.
    val categoryId: String? = null,
    // Stable meaning/category semantics for future large content sets. This is not patient placement.
    val meaning: String? = null,
    // Scenario metadata is for future guided helper flows (for example vending or restaurant use).
    // It must not replace profiles, categories, or patient page placement.
    val scenarioIds: List<String> = emptyList(),
    val conceptId: String? = null,
    val children: List<String> = emptyList(),
    val sentenceRole: String? = null,
    val questionSl: String? = null,
    val questionUk: String? = null,
    val questionByLanguage: Map<String, String> = emptyMap(),
    // Local icon sources stay separate: SOCA -> icons/soca, CUSTOM/PATIENT -> icons/custom,
    // ARASAAC -> icons/arasaac, SYSTEM -> text/fallback only.
    val iconSource: IconSource = IconSource.SYSTEM,
    val parentId: String? = null,
    // Future visibility model: one AAC object can appear as root, under one parent,
    // under multiple parents, or inside learning/message flows without duplicating the item.
    val visibleUnderIds: List<String> = emptyList(),
    // Future placement model uses a single 5x5 reference grid; smaller grids adapt from this order.
    val placements: List<AacPlacement> = emptyList(),
    val isRootItem: Boolean = true,
    val isHiddenUntilParent: Boolean = false,
    // Optional content/settings hook for future therapist-configured fixed top-row positions 1..5.
    val fixedTopRowPosition: Int? = null,
    val addsToSentence: Boolean = true,
    val speaksImmediately: Boolean = true,
    val opensSubicons: Boolean = false,
    val priority: Int = 0,
    val followUpQuestion: String? = null,
    val vendingNumber: String? = null,
    val vendingInstructionImagePath: String? = null,
    val largeCupImagePath: String? = null,
    val hasLargeCupOption: Boolean = false,
    // Upgrade safety: protected local items must win over bundled starter repairs.
    val locked: Boolean = false,
    val userEdited: Boolean = false
) {
    val text: String
        get() = labelSl

    val resolvedSpeechText: String
        get() = speechText?.trim()?.takeIf { it.isNotEmpty() }
            ?: speakTextSl?.trim()?.takeIf { it.isNotEmpty() }
            ?: labelSl
}

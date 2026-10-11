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
    val semanticType: AacSemanticType = AacSemanticType.CATEGORY,
    val activeForPatient: Boolean = true,
    val learningModeEnabled: Boolean = true,
    val guidedQuestionsEnabled: Boolean = true,
    // Therapist organization only. Patient placement is controlled by placements/visibility metadata.
    val categoryId: String? = null,
    // Stable meaning/category semantics for future large content sets. This is not patient placement.
    val meaning: String? = null,
    val meaningId: String? = null,
    val meaningType: String? = null,
    val meaningGroup: String? = null,
    val semanticTags: List<String> = emptyList(),
    val searchKeywordsByLanguage: Map<String, List<String>> = emptyMap(),
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
    val suggestedIconPath: String? = null,
    val parentId: String? = null,
    // Future visibility model: one AAC object can appear as root, under one parent,
    // under multiple parents, or inside learning/message flows without duplicating the item.
    val visibleUnderIds: List<String> = emptyList(),
    val canAppearInMultipleParents: Boolean = true,
    // Future placement model uses a single 5x5 reference grid; smaller grids adapt from this order.
    val placements: List<AacPlacement> = emptyList(),
    val protectedPlacement: Boolean = false,
    val isRootItem: Boolean = true,
    val isHiddenUntilParent: Boolean = false,
    // Derived from AacFixedTopRow.positions; neither editors nor suggestions may assign slots.
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
    val userEdited: Boolean = false,
    val imageOwner: String? = null,
    val patientManualImages: Map<String, AacManualImage> = emptyMap(),
    val labelSlFemale: String? = null,
    val labelSlMale: String? = null,
    val labelUkFemale: String? = null,
    val labelUkMale: String? = null,
    val speechSl: String? = null,
    val speechSlFemale: String? = null,
    val speechSlMale: String? = null,
    val speechUk: String? = null,
    val speechUkFemale: String? = null,
    val speechUkMale: String? = null
) {
    val text: String
        get() = labelSl

    val resolvedSpeechText: String
        get() = speechText?.trim()?.takeIf { it.isNotEmpty() }
            ?: speakTextSl?.trim()?.takeIf { it.isNotEmpty() }
            ?: labelSl
}

/** Explicit ownership in the existing AAC JSON; legacy unscoped images retain their owner. */
data class AacManualImage(val imagePath: String, val iconSource: IconSource)

object AacPatientManualImages {
    fun parse(json: org.json.JSONObject?): Map<String, AacManualImage> = buildMap {
        if (json != null) for (id in json.keys()) {
            val row = json.optJSONObject(id) ?: continue
            val path = row.optString("imagePath").trim()
            val source = runCatching { IconSource.valueOf(row.optString("iconSource")) }.getOrNull() ?: continue
            if (id.isNotBlank() && path.isNotBlank()) put(id, AacManualImage(path, source))
        }
    }
    fun json(images: Map<String, AacManualImage>) = org.json.JSONObject().apply {
        images.forEach { (id, image) -> put(id, org.json.JSONObject()
            .put("imagePath", image.imagePath).put("iconSource", image.iconSource.name)) }
    }
    fun update(item: org.json.JSONObject, patientId: String, legacyOwner: String, image: AacManualImage?) {
        require(patientId.isNotBlank())
        val images = parse(item.optJSONObject("patientManualImages")).toMutableMap()
        if (image == null) images.remove(patientId) else images[patientId] = image
        item.put("patientManualImages", json(images))
        if (image == null && patientId == legacyOwner) {
            item.put("imagePath", "").put("iconSource", IconSource.SYSTEM.name)
        }
    }
}

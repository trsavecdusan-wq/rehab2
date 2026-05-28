package com.rehab2.aac

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
    val conceptId: String? = null,
    val children: List<String> = emptyList(),
    val sentenceRole: String? = null,
    val questionSl: String? = null,
    val questionUk: String? = null,
    // Local icon sources stay separate: SOCA -> icons/soca, CUSTOM/PATIENT -> icons/custom,
    // ARASAAC -> icons/arasaac, SYSTEM -> text/fallback only.
    val iconSource: IconSource = IconSource.SYSTEM,
    val parentId: String? = null,
    // Future visibility model: one AAC object can appear as root, under one parent,
    // under multiple parents, or inside learning/message flows without duplicating the item.
    val visibleUnderIds: List<String> = emptyList(),
    val isRootItem: Boolean = true,
    val isHiddenUntilParent: Boolean = false,
    // Optional content/settings hook for future therapist-configured fixed top-row positions 1..5.
    val fixedTopRowPosition: Int? = null,
    val priority: Int = 0,
    val followUpQuestion: String? = null,
    val vendingNumber: String? = null,
    val vendingInstructionImagePath: String? = null,
    val largeCupImagePath: String? = null,
    val hasLargeCupOption: Boolean = false
) {
    val text: String
        get() = labelSl

    val speechText: String
        get() = speakTextSl?.trim()?.takeIf { it.isNotEmpty() } ?: labelSl
}

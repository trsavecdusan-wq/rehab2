package com.rehab2.aac

data class AacItem(
    val id: String,
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
    val iconSource: IconSource = IconSource.SYSTEM,
    val parentId: String? = null,
    val isRootItem: Boolean = true,
    val isHiddenUntilParent: Boolean = false,
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

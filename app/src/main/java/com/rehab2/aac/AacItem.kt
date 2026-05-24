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
    val sentenceRole: String? = null
)

package com.rehab2.aac

enum class IconSource {
    SOCA,
    ARASAAC,
    PATIENT,
    CUSTOM,
    SYSTEM
}

data class AacConcept(
    val id: String,
    val baseText: String,
    val category: String,
    val activeIconId: String?,
    val speakTextSl: String?,
    val speakTextUk: String?
)

data class AacIconVariant(
    val id: String,
    val conceptId: String,
    val source: IconSource,
    val imagePath: String,
    val label: String?
)

data class AacConversationNode(
    val id: String,
    val conceptId: String?,
    val title: String,
    val titleUk: String?,
    val speakTextSl: String?,
    val speakTextUk: String?,
    val questionSl: String?,
    val questionUk: String?,
    val children: List<String>,
    val sentenceRole: String?
)

data class AacSentenceItem(
    val conceptId: String,
    val text: String,
    val role: String?
)


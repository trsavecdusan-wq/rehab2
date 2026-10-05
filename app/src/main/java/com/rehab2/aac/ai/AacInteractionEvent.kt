package com.rehab2.aac.ai

import java.util.UUID

enum class AacEventType { ICON_ACTIVATED, PAGE_OPENED, SENTENCE_SPOKEN, SENTENCE_CLEARED }

data class AacInteractionEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val sourceScreen: String,
    val itemId: String? = null,
    val pageId: String,
    val gridSize: Int,
    val languageCode: String,
    val isFixedRow: Boolean = false,
    val eventType: AacEventType,
    val itemIdsInSentence: List<String> = emptyList()
)

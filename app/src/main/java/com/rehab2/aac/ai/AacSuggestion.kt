package com.rehab2.aac.ai

import java.util.UUID

enum class SuggestionStatus { DETECTED, TEMPORARY, ADMIN_REVIEW, APPROVED, REJECTED, MERGED_WITH_EXISTING }
enum class TemporaryCategory { PERSON, PLACE, FOOD, DRINK, CLOTHING, ACTIVITY, HEALTH, OTHER }

data class AacSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val normalizedMeaning: String,
    val labelSl: String,
    val labelUk: String = "",
    val speechSl: String = "",
    val speechUk: String = "",
    val suggestedParentId: String? = null,
    val semanticTags: List<String> = emptyList(),
    val sourceType: String = "LOCAL",
    val sourceContext: String = "",
    val confidence: Double = 0.0,
    val status: SuggestionStatus = SuggestionStatus.DETECTED,
    val temporaryCategory: TemporaryCategory = TemporaryCategory.OTHER,
    val duplicateOfItemId: String? = null,
    val adminNote: String = ""
)

package com.rehab2.aac

data class AacGuidedQuestion(
    val id: String,
    val questionSl: String,
    val semanticSelectionItemId: String
)

object AacGuidedLearningSelection {
    fun resolveFixedAnswer(
        question: AacGuidedQuestion?,
        fixedAnswerItemId: String
    ): String? {
        if (question == null) return null
        return when (fixedAnswerItemId.trim().lowercase()) {
            "yes", "da" -> question.semanticSelectionItemId
            "no", "ne" -> null
            else -> fixedAnswerItemId
        }
    }
}

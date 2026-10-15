package com.rehab2.learning

const val LEARNING_SCHEMA_VERSION = 1

enum class LearningTaskType { VISUAL_RECOGNITION, SPOKEN_INSTRUCTION, YES_NO_QUESTION, SYMBOL_LEARNING, SPEECH_ATTEMPT, RHYTHM }
enum class LearningOutcome { INDEPENDENT, WITH_HINT, ASSISTED, INCORRECT, NO_RESPONSE, SKIPPED }
enum class LearningHintLevel { REPEAT_INSTRUCTION, HIGHLIGHT_TARGET, SHOW_ANSWER }
enum class DistractorStrategy { SAME_GROUP, OTHER_GROUP }
enum class AssessedBy { SYSTEM, THERAPIST }

/** Same names as the existing AAC learningRepresentations.mode values. */
object LearningPresentation {
    const val IMAGE_TEXT = "image_text"
    const val IMAGE_ONLY = "image_only"
    const val TEXT_ONLY = "text_only"
    val ALL = setOf(IMAGE_TEXT, IMAGE_ONLY, TEXT_ONLY)
    /** Learning-only meaning of an empty list; AAC editor semantics are not changed. */
    val DEFAULT_WHEN_EMPTY = setOf(IMAGE_TEXT, IMAGE_ONLY)
}

data class LearningTask(
    val taskId: String,
    val taskType: LearningTaskType,
    val targetItemId: String,
    val distractorItemIds: List<String> = emptyList(),
    val questionItemId: String? = null,
    val distractorStrategy: DistractorStrategy = DistractorStrategy.OTHER_GROUP,
    val presentation: String = LearningPresentation.IMAGE_ONLY,
    val instructionTextByLanguage: Map<String, String> = emptyMap(),
    val speechAttemptWord: String? = null,
    val musicTrackId: String? = null,
    val enabled: Boolean = true
)

data class LearningSettings(
    val patientId: String,
    val choiceCount: Int = 2,
    val hintDelaySec: Int = 8,
    val maxHints: Int = 2,
    val hintLevels: List<LearningHintLevel> = listOf(
        LearningHintLevel.REPEAT_INSTRUCTION, LearningHintLevel.HIGHLIGHT_TARGET, LearningHintLevel.SHOW_ANSWER),
    val sessionMaxTasks: Int = 8,
    val allowedTaskTypes: Set<LearningTaskType> = setOf(LearningTaskType.VISUAL_RECOGNITION),
    val answerSide: String = ANSWER_SIDE_LEFT,
    val allowTextOnly: Boolean = false,
    val feedbackSound: Boolean = true
) {
    companion object {
        const val ANSWER_SIDE_LEFT = "left"
        const val ANSWER_SIDE_RIGHT = "right"
        const val ANSWER_SIDE_AUTO = "auto"
        private val ANSWER_SIDES = setOf(ANSWER_SIDE_LEFT, ANSWER_SIDE_RIGHT, ANSWER_SIDE_AUTO)
    }

    /** Out-of-range values are clamped, never rejected. */
    fun normalized(): LearningSettings = copy(
        choiceCount = choiceCount.coerceIn(2, 6),
        hintDelaySec = hintDelaySec.coerceIn(3, 60),
        maxHints = maxHints.coerceIn(0, 3),
        hintLevels = hintLevels.distinct(),
        sessionMaxTasks = sessionMaxTasks.coerceIn(1, 20),
        answerSide = answerSide.takeIf { it in ANSWER_SIDES } ?: ANSWER_SIDE_LEFT
    )
}

data class LearningAttempt(
    val attemptId: String,
    val patientId: String,
    val sessionId: String,
    val taskId: String,
    val taskType: LearningTaskType,
    val targetItemId: String,
    val shownItemIds: List<String>,
    val selectedItemId: String?,
    val outcome: LearningOutcome,
    val hintsUsed: Int,
    val responseMs: Long,
    val languageCode: String,
    val assessedBy: AssessedBy,
    val speechRating: Int? = null,
    val timestamp: Long
)

data class ReviewEntry(
    val patientId: String,
    val itemId: String,
    val taskType: LearningTaskType,
    val box: Int,
    val nextDueEpochDay: Long,
    val lastOutcome: LearningOutcome,
    val updatedAt: Long
)

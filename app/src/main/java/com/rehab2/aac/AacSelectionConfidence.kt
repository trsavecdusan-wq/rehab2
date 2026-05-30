package com.rehab2.aac

data class AacSelectionConfidence(
    val itemId: String,
    val confidence: Float,
    val signals: List<String> = emptyList(),
    val requiresHumanConfirmation: Boolean = false
) {
    fun isSensitiveSuggestion(): Boolean {
        return itemId.trim().lowercase() in SENSITIVE_ITEM_IDS
    }

    fun mustConfirmBeforeSpeech(): Boolean {
        return requiresHumanConfirmation || isSensitiveSuggestion()
    }

    companion object {
        private val SENSITIVE_ITEM_IDS = setOf(
            "pain",
            "doctor",
            "home",
            "fear",
            "bad",
            "no_therapy",
            "call_help",
            "help"
        )
    }
}

/*
 * Future mimic or face-tracking signals may assist icon suggestions, but they must never
 * decide instead of the patient. Any camera, mimic, or AI signal must be optional,
 * privacy-preserving, and enabled only with caregiver/therapist authorization.
 *
 * Sensitive meanings such as pain, doctor, home, fear, feeling bad, refusing therapy,
 * or calling for help must not be spoken from AI/mimic confidence alone. They require
 * explicit patient confirmation through the AAC surface.
 */

package com.rehab2.aac

object AacWcV1SentenceBuilder {
    private val WC_V1_IDS = setOf(
        "wc_wet",
        "wc_dirty",
        "wc_wet_and_dirty",
        "wc_diaper_change",
        "wc_burning",
        "wc_pain",
        "wc_itching",
        "wc_blood",
        "wc_please",
        "wc_now",
        "wc_very_urgent",
        "wc_call_nurse"
    )

    fun canBuild(items: List<AacItem>): Boolean {
        return items.any { item -> item.id in WC_V1_IDS }
    }

    fun buildSentence(items: List<AacItem>): String {
        val ids = items.map { item -> item.id }.toSet()
        if (ids.isEmpty()) return ""

        val parts = mutableListOf<String>()
        val polite = "wc_please" in ids
        val now = "wc_now" in ids
        val veryUrgent = "wc_very_urgent" in ids
        val hasAction = "wc_diaper_change" in ids
        val state = when {
            "wc_wet_and_dirty" in ids -> "Mokra in umazana sem."
            "wc_wet" in ids -> "Mokra sem."
            "wc_dirty" in ids -> "Umazana sem."
            else -> null
        }
        val problem = when {
            "wc_blood" in ids -> "Vidim kri."
            "wc_burning" in ids -> "Peče me."
            "wc_pain" in ids -> "Boli me."
            "wc_itching" in ids -> "Srbi me."
            else -> null
        }

        when {
            hasAction && polite && now -> parts += "Prosim, takoj potrebujem menjavo plenice."
            hasAction && polite && veryUrgent -> parts += "Prosim, zelo nujno potrebujem menjavo plenice."
            hasAction && now -> parts += "Takoj potrebujem menjavo plenice."
            hasAction && veryUrgent -> parts += "Zelo nujno potrebujem menjavo plenice."
            hasAction && polite -> parts += "Prosim, potrebujem menjavo plenice."
            hasAction -> parts += "Potrebujem menjavo plenice."
        }

        if (state != null) {
            parts += state
        }
        if (problem != null) {
            parts += if (veryUrgent && !hasAction) "Zelo nujno, ${problem.replaceFirstChar { it.lowercase() }}" else problem
        }
        if ("wc_call_nurse" in ids) {
            parts += "Prosim, pokličite medicinsko sestro."
        }

        return parts.joinToString(" ")
    }
}

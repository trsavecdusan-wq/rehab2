package com.rehab2.aac

import java.util.Locale

data class AacGuidedPrompt(
    val questionSl: String,
    val childIds: List<String>
)

object AacGuidedPromptEngine {
    fun promptFor(item: AacItem): AacGuidedPrompt? {
        return promptForId(item.id)
    }

    fun questionFor(item: AacItem): String {
        return promptFor(item)?.questionSl.orEmpty()
    }

    fun childIdsFor(item: AacItem): List<String> {
        return promptFor(item)?.childIds.orEmpty()
    }

    fun followUpFor(sequenceItems: List<AacItem>, selectedItem: AacItem): AacGuidedPrompt? {
        val rootItem = sequenceItems.firstOrNull() ?: return null
        val rootId = normalize(rootItem.id)
        val selectedId = normalize(selectedItem.id)
        return when {
            rootId in DRINK_ROOT_IDS && selectedId in DRINK_TARGET_IDS -> AacGuidedPrompt(
                questionSl = "Toplo ali hladno?",
                childIds = listOf("drink_cold", "drink_warm", "drink_small", "drink_more", "food_enough", "back_to_main")
            )
            rootId in PAIN_ROOT_IDS && selectedId in PAIN_BODY_PART_IDS -> AacGuidedPrompt(
                questionSl = "Kako mo\u010dno te boli?",
                childIds = listOf("pain_light", "pain_medium", "pain_very_strong", "back_to_main")
            )
            else -> null
        }
    }

    fun hasFlow(item: AacItem): Boolean {
        return promptFor(item) != null
    }

    fun isFollowUpAnswer(item: AacItem): Boolean {
        return normalize(item.id) in FOLLOW_UP_ANSWER_IDS
    }

    private fun promptForId(id: String): AacGuidedPrompt? {
        return when (normalize(id)) {
            "thirsty", "drink" -> AacGuidedPrompt(
                questionSl = "Kaj bi rada pila?",
                childIds = listOf(
                    "water",
                    "tea",
                    "coffee",
                    "drink_fanta",
                    "drink_pepsi",
                    "drink_coca_cola",
                    "juice",
                    "drink_milk"
                )
            )
            "hungry", "food" -> AacGuidedPrompt(
                questionSl = "Kaj bi rada jedla?",
                childIds = listOf("soup", "bread", "food_yogurt", "food_banana", "food_apple", "food_lunch")
            )
            "pain", "pain_area" -> AacGuidedPrompt(
                questionSl = "Kaj te boli?",
                childIds = listOf("head", "arm", "leg", "belly", "back", "throat")
            )
            "miss_someone" -> AacGuidedPrompt(
                questionSl = "Koga pogre\u0161a\u0161?",
                childIds = listOf(
                    "person_dusan",
                    "person_zana",
                    "person_sergej",
                    "person_julija",
                    "person_oksana",
                    "person_inna",
                    "person_franc"
                )
            )
            "dont_want" -> AacGuidedPrompt(
                questionSl = "\u010cesa no\u010de\u0161?",
                childIds = listOf(
                    "water",
                    "tea",
                    "coffee",
                    "drink_fanta",
                    "drink_pepsi",
                    "drink_coca_cola",
                    "juice",
                    "soup",
                    "bread",
                    "food_yogurt",
                    "food_banana",
                    "therapy"
                )
            )
            else -> null
        }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale("sl", "SI"))
            .replace("\u010d", "c")
            .replace("\u0161", "s")
            .replace("\u017e", "z")
            .replace("\u0107", "c")
            .replace("\u0111", "d")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private val DRINK_ROOT_IDS = setOf("thirsty", "drink")
    private val DRINK_TARGET_IDS = setOf(
        "water",
        "tea",
        "coffee",
        "drink_fanta",
        "drink_pepsi",
        "drink_coca_cola",
        "juice",
        "drink_milk"
    )
    private val PAIN_ROOT_IDS = setOf("pain", "pain_area")
    private val PAIN_BODY_PART_IDS = setOf("head", "arm", "leg", "belly", "back", "chest", "throat")
    private val FOLLOW_UP_ANSWER_IDS = setOf(
        "drink_cold",
        "drink_warm",
        "drink_small",
        "drink_more",
        "food_enough",
        "pain_light",
        "pain_medium",
        "pain_very_strong"
    )
}

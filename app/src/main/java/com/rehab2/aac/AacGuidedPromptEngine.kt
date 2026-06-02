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
        return dailyLifeFollowUp(rootId, selectedId)
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
                questionSl = "Kaj bi pila?",
                childIds = listOf(
                    "water",
                    "tea",
                    "coffee",
                    "drink_fanta",
                    "drink_coca_cola",
                    "drink_pepsi",
                    "juice",
                    "drink_milk"
                )
            )
            "hungry", "food" -> AacGuidedPrompt(
                questionSl = "Kaj bi jedla?",
                childIds = listOf("soup", "bread", "food_yogurt", "food_banana", "food_apple", "food_lunch", "food_dinner", "sweet")
            )
            "pain", "pain_area" -> AacGuidedPrompt(
                questionSl = "Kaj te boli?",
                childIds = listOf("head", "arm", "leg", "belly", "back", "throat")
            )
            "wc" -> AacGuidedPrompt(
                questionSl = "Kaj potrebuje\u0161 glede WC?",
                childIds = listOf("wc_now", "wc_soon", "wc_help", "dressing_help", "washing_help")
            )
            "real_world" -> AacGuidedPrompt(
                questionSl = "Kje potrebuje\u0161 pomo\u010d?",
                childIds = listOf("vending_drinks", "vending_coffee_tea", "shop", "restaurant", "transport")
            )
            "vending_drinks" -> AacGuidedPrompt(
                questionSl = "Kaj \u017eeli\u0161 izbrati?",
                childIds = listOf("drink_fanta", "drink_coca_cola", "drink_pepsi", "water", "juice")
            )
            "vending_coffee_tea" -> AacGuidedPrompt(
                questionSl = "Kaj \u017eeli\u0161 izbrati?",
                childIds = listOf("coffee", "tea", "drink_milk", "vending_photo_step_1", "vending_photo_step_2")
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

    private fun dailyLifeFollowUp(rootId: String, selectedId: String): AacGuidedPrompt? {
        return when {
            rootId in DRINK_ROOT_IDS && selectedId == "tea" -> AacGuidedPrompt(
                questionSl = "Velika skodelica?",
                childIds = listOf("yes", "no")
            )
            rootId in DRINK_ROOT_IDS && selectedId == "coffee" -> AacGuidedPrompt(
                questionSl = "Kak\u0161na kava?",
                childIds = listOf("coffee_plain", "coffee_white", "coffee_cappuccino")
            )
            rootId in DRINK_ROOT_IDS && selectedId == "drink_fanta" -> AacGuidedPrompt(
                questionSl = "Kak\u0161na Fanta?",
                childIds = listOf("drink_cold", "drink_no_additive")
            )
            rootId in FOOD_ROOT_IDS && selectedId in FOOD_TARGET_IDS -> AacGuidedPrompt(
                questionSl = "Koliko ali kdaj?",
                childIds = listOf("food_little", "food_more", "food_enough", "food_later")
            )
            rootId in PAIN_ROOT_IDS && selectedId in PAIN_BODY_PART_IDS -> AacGuidedPrompt(
                questionSl = "Kako mo\u010dno?",
                childIds = listOf("pain_light", "pain_medium", "pain_very")
            )
            rootId in PAIN_ROOT_IDS && selectedId in PAIN_INTENSITY_IDS -> AacGuidedPrompt(
                questionSl = "Od kdaj?",
                childIds = listOf("pain_now", "pain_today", "pain_many_days")
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
    private val FOOD_ROOT_IDS = setOf("hungry", "food")
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
    private val FOOD_TARGET_IDS = setOf(
        "soup",
        "bread",
        "food_yogurt",
        "food_banana",
        "food_apple",
        "food_lunch",
        "food_dinner",
        "sweet"
    )
    private val PAIN_ROOT_IDS = setOf("pain", "pain_area")
    private val PAIN_BODY_PART_IDS = setOf("head", "arm", "leg", "belly", "back", "chest", "throat")
    private val PAIN_INTENSITY_IDS = setOf("pain_light", "pain_medium", "pain_very", "pain_very_strong")
    private val FOLLOW_UP_ANSWER_IDS = setOf(
        "yes",
        "drink_cold",
        "drink_warm",
        "drink_small",
        "drink_more",
        "drink_no_additive",
        "coffee_plain",
        "coffee_white",
        "coffee_cappuccino",
        "food_little",
        "food_enough",
        "food_later",
        "pain_light",
        "pain_medium",
        "pain_very",
        "pain_now",
        "pain_today",
        "pain_many_days",
        "pain_very_strong"
    )
}

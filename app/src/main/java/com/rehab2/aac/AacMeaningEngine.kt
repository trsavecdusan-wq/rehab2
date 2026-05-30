package com.rehab2.aac

enum class MeaningType {
    CORE_PERSON,
    CORE_ACTION,
    QUESTION,
    NEED,
    FOOD,
    DRINK,
    PAIN,
    PLACE,
    PEOPLE,
    FEELING,
    CONFIRMATION,
    UNKNOWN
}

data class AacMeaningContext(
    val currentSequence: List<String> = emptyList(),
    val currentProfileId: String? = null,
    val currentPageId: String? = null,
    val lastSelectedItemId: String? = null
)

data class AacSuggestion(
    val itemId: String,
    val reason: String,
    val priority: Int
)

object AacMeaningEngine {
    fun suggest(context: AacMeaningContext): List<AacSuggestion> {
        val normalizedSequence = context.currentSequence.map(::normalizeToken)
        val last = normalizedSequence.lastOrNull().orEmpty()
        val painContext = normalizedSequence.any { it in PAIN_TOKENS }
        val foodContext = normalizedSequence.any { it in FOOD_TOKENS }
        val drinkContext = normalizedSequence.any { it in DRINK_TOKENS }

        return when {
            last in WANT_TOKENS -> suggestions(
                "drink" to "Po želji najprej ponudi pijačo.",
                "food" to "Po želji ponudi hrano.",
                "wc" to "Pogosta osnovna potreba.",
                "help" to "Pomoč mora ostati hitro dosegljiva.",
                "rest" to "Počitek je pogosta potreba po naporu."
            )
            last in GO_TOKENS -> suggestions(
                "question_where" to "Po gibanju vprašaj kam.",
                "home" to "Domov je pogost cilj.",
                "wc" to "WC je pogost cilj.",
                "room" to "Soba je varna osnovna možnost.",
                "walk" to "Sprehod je pogosta aktivnost."
            )
            last in PAIN_TOKENS -> suggestions(
                "question_where" to "Pri bolečini je najprej pomembno mesto.",
                "head" to "Pogost odgovor na bolečino.",
                "arm" to "Pogost odgovor na bolečino.",
                "leg" to "Pogost odgovor na bolečino.",
                "belly" to "Pogost odgovor na bolečino."
            )
            last in WHERE_TOKENS && painContext -> suggestions(
                "head" to "Mesto bolečine.",
                "arm" to "Mesto bolečine.",
                "leg" to "Mesto bolečine.",
                "belly" to "Mesto bolečine.",
                "back" to "Mesto bolečine."
            )
            last in WHAT_TOKENS && foodContext -> suggestions(
                "soup" to "Hrana: juha.",
                "bread" to "Hrana: kruh.",
                "fruit" to "Hrana: sadje."
            )
            last in WHAT_TOKENS && drinkContext -> suggestions(
                "water" to "Pijača: voda.",
                "juice" to "Pijača: sok.",
                "tea" to "Pijača: čaj.",
                "coffee" to "Pijača: kava."
            )
            else -> emptyList()
        }
    }

    fun buildDebugSuggestions(sequenceLabels: List<String>): List<String> {
        return suggest(AacMeaningContext(currentSequence = sequenceLabels))
            .map { suggestion -> "${suggestion.itemId}: ${suggestion.reason}" }
    }

    fun meaningTypeFor(labelOrItemId: String): MeaningType {
        val token = normalizeToken(labelOrItemId)
        return when {
            token in PERSON_TOKENS -> MeaningType.CORE_PERSON
            token in WANT_TOKENS || token in GO_TOKENS -> MeaningType.CORE_ACTION
            token in WHERE_TOKENS || token in WHAT_TOKENS -> MeaningType.QUESTION
            token in FOOD_TOKENS -> MeaningType.FOOD
            token in DRINK_TOKENS -> MeaningType.DRINK
            token in PAIN_TOKENS -> MeaningType.PAIN
            token in PLACE_TOKENS -> MeaningType.PLACE
            token in PEOPLE_TOKENS -> MeaningType.PEOPLE
            token in FEELING_TOKENS -> MeaningType.FEELING
            token in CONFIRMATION_TOKENS -> MeaningType.CONFIRMATION
            else -> MeaningType.UNKNOWN
        }
    }

    private fun suggestions(vararg pairs: Pair<String, String>): List<AacSuggestion> {
        return pairs.mapIndexed { index, pair ->
            AacSuggestion(
                itemId = pair.first,
                reason = pair.second,
                priority = index + 1
            )
        }
    }

    private fun normalizeToken(value: String): String {
        return value.trim()
            .lowercase()
            .replace("č", "c")
            .replace("š", "s")
            .replace("ž", "z")
            .replace("ć", "c")
            .replace("đ", "d")
            .replace(Regex("\\s+"), "_")
    }

    private val PERSON_TOKENS = setOf("jaz", "me", "i")
    private val WANT_TOKENS = setOf("zelim", "rabim", "hocem", "want", "need")
    private val GO_TOKENS = setOf("grem", "iti", "go")
    private val WHERE_TOKENS = setOf("kje", "kam", "question_where", "where")
    private val WHAT_TOKENS = setOf("kaj", "question_what", "what")
    private val FOOD_TOKENS = setOf("hrana", "food", "hungry", "lacna")
    private val DRINK_TOKENS = setOf("pijaca", "drink", "drinks", "thirsty", "zejna")
    private val PAIN_TOKENS = setOf("boli", "bolecina", "pain")
    private val PLACE_TOKENS = setOf("domov", "home", "wc", "soba", "room", "sprehod", "walk")
    private val PEOPLE_TOKENS = setOf("druzina", "family", "zdravnik", "doctor")
    private val FEELING_TOKENS = setOf("dobro", "slabo", "strah", "utrujena", "good", "bad", "fear", "tired")
    private val CONFIRMATION_TOKENS = setOf("da", "ne", "yes", "no", "tak", "ni")
}

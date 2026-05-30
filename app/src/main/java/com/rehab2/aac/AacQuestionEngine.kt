package com.rehab2.aac

object AacQuestionEngine {
    fun nextQuestion(tokens: List<String>): String? {
        val normalized = tokens.map(::normalizeToken).filter { it.isNotBlank() }
        if (normalized.isEmpty()) return null

        return when {
            normalized.containsPain() && normalized.containsBodyPart() -> "Kako močno boli?"
            normalized.containsPain() -> "Kje boli?"
            normalized.containsWant() && normalized.containsDrink() -> "Kaj bi pili?"
            normalized.containsWant() && normalized.containsFood() -> "Kaj bi jedli?"
            normalized.containsGo() -> "Kam bi šli?"
            normalized.containsCall() -> "Koga naj pokličem?"
            normalized.containsBadFeeling() -> "Kaj je narobe?"
            else -> null
        }
    }

    fun nextQuestion(vararg tokens: String): String? = nextQuestion(tokens.toList())

    private fun normalizeToken(value: String): String {
        return value.trim()
            .lowercase()
            .replace("č", "c")
            .replace("š", "s")
            .replace("ž", "z")
            .replace("ć", "c")
            .replace("đ", "d")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun List<String>.containsPain(): Boolean = any { it in PAIN_TOKENS }

    private fun List<String>.containsBodyPart(): Boolean = any { it in BODY_PART_TOKENS }

    private fun List<String>.containsWant(): Boolean = any { it in WANT_TOKENS }

    private fun List<String>.containsDrink(): Boolean = any { it in DRINK_TOKENS }

    private fun List<String>.containsFood(): Boolean = any { it in FOOD_TOKENS }

    private fun List<String>.containsGo(): Boolean = any { it in GO_TOKENS }

    private fun List<String>.containsCall(): Boolean = any { it in CALL_TOKENS }

    private fun List<String>.containsBadFeeling(): Boolean = any { it in BAD_FEELING_TOKENS }

    private val PAIN_TOKENS = setOf("boli", "bolecina", "pain")
    private val BODY_PART_TOKENS = setOf(
        "glava",
        "head",
        "roka",
        "arm",
        "noga",
        "leg",
        "trebuh",
        "belly",
        "stomach",
        "hrbet",
        "back",
        "prsi",
        "chest",
        "grlo",
        "throat"
    )
    private val WANT_TOKENS = setOf("zelim", "hocem", "rabim", "jaz_zelim", "want", "need")
    private val DRINK_TOKENS = setOf("pijaca", "piti", "voda", "sok", "caj", "kava", "drink", "water", "juice", "tea", "coffee")
    private val FOOD_TOKENS = setOf("hrana", "jesti", "juha", "kruh", "sadje", "food", "soup", "bread", "fruit")
    private val GO_TOKENS = setOf("grem", "iti", "go")
    private val CALL_TOKENS = setOf("klici", "poklici", "call")
    private val BAD_FEELING_TOKENS = setOf("slabo", "bad", "sick")
}

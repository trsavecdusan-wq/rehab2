package com.rehab2.aac

object AacNaturalSentenceBuilder {
    fun buildSentence(tokens: List<String>): String {
        val normalized = tokens.map(::normalizeToken).filter { it.isNotBlank() }
        if (normalized.isEmpty()) return ""

        return when {
            normalized.isSingle("pomoc", "help") -> "Prosim, pomagajte mi."
            normalized.isNoUnderstand() -> "Ne razumem."
            normalized.isSingle("da", "yes") -> "Da."
            normalized.isSingle("ne", "no") -> "Ne."
            normalized.isSingle("utrujen", "utrujena", "tired") -> "Utrujen sem."
            normalized.isSingle("slabo", "bad") -> "Slabo se počutim."
            normalized.isSingle("dobro", "good") -> "Dobro sem."
            normalized.containsWant() -> buildWantSentence(normalized)
            normalized.containsPain() -> buildPainSentence(normalized)
            normalized.containsGo() -> buildGoSentence(normalized)
            else -> fallbackSentence(tokens)
        }
    }

    fun buildSentence(vararg tokens: String): String = buildSentence(tokens.toList())

    private fun buildWantSentence(tokens: List<String>): String {
        return when {
            tokens.any { it in WATER_TOKENS } -> "Rad bi vodo."
            tokens.any { it in COFFEE_TOKENS } -> "Rad bi kavo."
            tokens.any { it in FOOD_TOKENS } -> "Rad bi jedel."
            else -> fallbackSentence(tokens)
        }
    }

    private fun buildPainSentence(tokens: List<String>): String {
        return when {
            tokens.any { it in LEG_TOKENS } -> "Boli me noga."
            tokens.any { it in HEAD_TOKENS } -> "Boli me glava."
            tokens.any { it in ARM_TOKENS } -> "Boli me roka."
            else -> fallbackSentence(tokens)
        }
    }

    private fun buildGoSentence(tokens: List<String>): String {
        return when {
            tokens.any { it in HOME_TOKENS } -> "Rad bi šel domov."
            tokens.any { it in WC_TOKENS } -> "Rad bi šel na stranišče."
            else -> fallbackSentence(tokens)
        }
    }

    private fun fallbackSentence(tokens: List<String>): String {
        val text = tokens
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.trim() }
            .trim()

        return when {
            text.isBlank() -> ""
            text.endsWith(".") || text.endsWith("!") || text.endsWith("?") -> text
            else -> "$text."
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
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun List<String>.containsWant(): Boolean = any { it in WANT_TOKENS }

    private fun List<String>.containsPain(): Boolean = any { it in PAIN_TOKENS }

    private fun List<String>.containsGo(): Boolean = any { it in GO_TOKENS }

    private fun List<String>.isNoUnderstand(): Boolean {
        return isSingle("ne_razumem", "no_understand", "dont_understand") ||
            containsAll(listOf("ne", "razumem"))
    }

    private fun List<String>.isSingle(vararg values: String): Boolean {
        return size == 1 && first() in values.toSet()
    }

    private val WANT_TOKENS = setOf("zelim", "hocem", "rabim", "jaz_zelim", "want", "need")
    private val PAIN_TOKENS = setOf("boli", "bolecina", "pain")
    private val GO_TOKENS = setOf("grem", "iti", "go")
    private val WATER_TOKENS = setOf("voda", "vodo", "water")
    private val COFFEE_TOKENS = setOf("kava", "kavo", "coffee")
    private val FOOD_TOKENS = setOf("hrana", "hrano", "jesti", "food", "hungry")
    private val LEG_TOKENS = setOf("noga", "nogo", "leg")
    private val HEAD_TOKENS = setOf("glava", "glavo", "head")
    private val ARM_TOKENS = setOf("roka", "roko", "arm")
    private val HOME_TOKENS = setOf("domov", "dom", "home")
    private val WC_TOKENS = setOf("wc", "stranisce", "toilet")
}

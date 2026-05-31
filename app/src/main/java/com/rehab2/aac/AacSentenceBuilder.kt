package com.rehab2.aac

import java.util.Locale

object AacSentenceBuilder {
    fun buildSlovenianSentence(items: List<AacItem>): String {
        if (items.isEmpty()) return ""

        simpleSentence(items)?.let { return it }

        val keys = items.flatMap(::itemKeys).toSet()
        return when {
            keys.any { it in DONT_WANT_KEYS } -> buildDontWantSentence(items)
            keys.any { it in WANT_KEYS } -> buildWantSentence(items)
            keys.any { it in MISS_KEYS } -> buildMissSentence(items)
            keys.any { it in PAIN_KEYS } -> buildPainSentence(items)
            else -> ""
        }
    }

    private fun simpleSentence(items: List<AacItem>): String? {
        if (items.size != 1) return null
        val keys = itemKeys(items.first()).toSet()
        return when {
            keys.any { it in THANK_YOU_KEYS } -> "Hvala."
            keys.any { it in HELP_KEYS } -> "Prosim, pomagajte mi."
            keys.any { it in NO_UNDERSTAND_KEYS } -> "Ne razumem."
            keys.any { it in YES_KEYS } -> "Da."
            keys.any { it in NO_KEYS } -> "Ne."
            keys.any { it in TIRED_KEYS } -> "Utrujena sem."
            keys.any { it in REST_KEYS } -> "Rada bi počivala."
            keys.any { it in WC_KEYS } -> "Moram na WC."
            else -> null
        }
    }

    private fun buildWantSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> WANT_TARGETS[item.idKey()] }
            ?: return ""
        return "Rada bi $target."
    }

    private fun buildDontWantSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> DONT_WANT_TARGETS[item.idKey()] }
            ?: return ""
        return "Nočem $target."
    }

    private fun buildMissSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> MISS_TARGETS[item.idKey()] }
            ?: return ""
        return "Pogrešam $target."
    }

    private fun buildPainSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> PAIN_TARGETS[item.idKey()] }
            ?: return ""
        return "Boli me $target."
    }

    private fun itemKeys(item: AacItem): List<String> {
        return listOfNotNull(
            item.id,
            item.conceptId,
            item.meaningId,
            item.meaningType,
            item.meaningGroup,
            item.categoryId,
            item.labelSl,
            item.speakTextSl,
            item.speechText
        )
            .flatMap { value -> value.split(' ', '_', '-', '/') }
            .map(::normalize)
            .filter { it.isNotBlank() }
    }

    private fun AacItem.idKey(): String = normalize(id)

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale("sl", "SI"))
            .replace("č", "c")
            .replace("š", "s")
            .replace("ž", "z")
            .replace("ć", "c")
            .replace("đ", "d")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private val WANT_KEYS = setOf(
        "i_want",
        "rada",
        "zelim",
        "hocem",
        "rabim",
        "want",
        "need",
        "drink",
        "thirsty",
        "pijaca",
        "zejna",
        "food",
        "hungry",
        "hrana",
        "lacna"
    )
    private val DONT_WANT_KEYS = setOf("dont_want", "nocem", "nočem")
    private val MISS_KEYS = setOf("miss_someone", "miss_you", "pogresam")
    private val PAIN_KEYS = setOf("pain", "pain_area", "boli", "bolecina")
    private val THANK_YOU_KEYS = setOf("thank_you", "hvala")
    private val HELP_KEYS = setOf("help", "pomoc")
    private val NO_UNDERSTAND_KEYS = setOf("no_understand", "dont_understand", "ne_razumem")
    private val YES_KEYS = setOf("yes", "da")
    private val NO_KEYS = setOf("no", "ne")
    private val TIRED_KEYS = setOf("tired", "utrujena")
    private val REST_KEYS = setOf("rest", "pocitek")
    private val WC_KEYS = setOf("wc", "toilet", "stranisce")

    private val WANT_TARGETS = mapOf(
        "drink_fanta" to "Fanto",
        "drink_coca_cola" to "Coca Colo",
        "drink_pepsi" to "Pepsi",
        "drink_water" to "vodo",
        "water" to "vodo",
        "drink_tea" to "čaj",
        "tea" to "čaj",
        "drink_coffee" to "kavo",
        "coffee" to "kavo",
        "juice" to "sok",
        "drink_milk" to "mleko",
        "food_soup" to "juho",
        "soup" to "juho",
        "bread" to "kruh",
        "fruit" to "sadje",
        "food_yogurt" to "jogurt",
        "food_banana" to "banano",
        "food_apple" to "jabolko",
        "rest" to "počivati",
        "wc" to "na WC"
    )

    private val DONT_WANT_TARGETS = mapOf(
        "drink_fanta" to "Fante",
        "drink_coca_cola" to "Coca Cole",
        "drink_pepsi" to "Pepsija",
        "drink_water" to "vode",
        "water" to "vode",
        "drink_tea" to "čaja",
        "tea" to "čaja",
        "drink_coffee" to "kave",
        "coffee" to "kave",
        "juice" to "soka",
        "drink_milk" to "mleka",
        "food_soup" to "juhe",
        "soup" to "juhe",
        "bread" to "kruha",
        "fruit" to "sadja",
        "food_yogurt" to "jogurta",
        "food_banana" to "banane",
        "food_apple" to "jabolka",
        "therapy" to "terapije"
    )

    private val MISS_TARGETS = mapOf(
        "person_zana" to "Žano",
        "sister_zana" to "Žano",
        "person_dusan" to "Dušana",
        "dusan" to "Dušana",
        "person_sergej" to "Sergeja",
        "grandfather_sergej" to "Sergeja",
        "person_julija" to "Julijo",
        "julija" to "Julijo",
        "person_oksana" to "Oksano",
        "oksana" to "Oksano",
        "person_inna" to "Inno",
        "inna" to "Inno",
        "person_franc" to "Franca",
        "franc" to "Franca"
    )

    private val PAIN_TARGETS = mapOf(
        "leg" to "noga",
        "head" to "glava",
        "arm" to "roka",
        "belly" to "trebuh",
        "back" to "hrbet",
        "chest" to "v prsih",
        "throat" to "grlo"
    )
}

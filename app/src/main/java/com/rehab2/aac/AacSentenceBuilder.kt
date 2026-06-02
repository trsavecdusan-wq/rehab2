package com.rehab2.aac

import java.util.Locale

object AacSentenceBuilder {
    fun buildSlovenianSentence(items: List<AacItem>): String {
        if (items.isEmpty()) return ""

        simpleSentence(items)?.let { return it }

        val keys = items.flatMap(::itemKeys).toSet()
        return when {
            items.anyIdIn(PERSON_ACTION_IDS) && items.hasPersonTarget() -> buildPeopleActionSentence(items)
            items.hasId("need") -> buildNeedSentence(items)
            items.hasId("problem") -> buildProblemSentence(items)
            items.hasId("please") -> buildPleaseSentence(items)
            keys.any { it in DONT_WANT_KEYS } -> buildDontWantSentence(items)
            keys.any { it in WANT_KEYS } -> buildWantSentence(items)
            keys.any { it in MISS_KEYS } -> buildMissSentence(items)
            keys.any { it in PAIN_KEYS } -> buildPainSentence(items)
            else -> ""
        }
    }

    private fun simpleSentence(items: List<AacItem>): String? {
        if (items.size != 1) return null
        val item = items.first()
        SIMPLE_SENTENCES_BY_ID[item.idKey()]?.let { return it }
        val keys = itemKeys(item).toSet()
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

    private fun buildPeopleActionSentence(items: List<AacItem>): String {
        val person = items.firstNotNullOfOrNull { item -> PERSON_TARGETS[item.idKey()] } ?: return ""
        return when {
            items.hasId("miss_someone") || items.hasId("miss_you") -> {
                when (items.firstNotNullOfOrNull { item -> MISS_INTENSITIES[item.idKey()] }) {
                    "little" -> "Malo pogrešam ${person.accusative}."
                    "very" -> "Zelo pogrešam ${person.accusative}."
                    else -> "Pogrešam ${person.accusative}."
                }
            }
            items.hasId("contact_call") -> "Prosim, pokliči ${person.accusative}."
            items.hasId("person_where_is") -> "Kje je ${person.nominative}?"
            items.hasId("come_to_me") -> "Naj pride ${person.nominative}."
            items.hasId("love_you") -> "Rada imam ${person.accusative}."
            items.hasId("person_tell") -> "Povej ${person.dative}."
            else -> ""
        }
    }

    private fun buildNeedSentence(items: List<AacItem>): String {
        if (items.hasId("help")) {
            val helpTarget = items.firstNotNullOfOrNull { item -> NEED_HELP_TARGETS[item.idKey()] }
            if (helpTarget != null) return "Potrebujem pomoč pri $helpTarget."
        }
        val target = items.firstNotNullOfOrNull { item -> NEED_TARGETS[item.idKey()] } ?: return ""
        return "Potrebujem $target."
    }

    private fun buildProblemSentence(items: List<AacItem>): String {
        if (items.hasId("cannot")) {
            items.firstNotNullOfOrNull { item -> CANNOT_SENTENCES[item.idKey()] }?.let { return it }
        }
        if (items.hasId("uncomfortable")) {
            items.firstNotNullOfOrNull { item -> UNCOMFORTABLE_SENTENCES[item.idKey()] }?.let { return it }
        }
        if (items.hasId("cold_hot")) {
            items.firstNotNullOfOrNull { item -> TEMPERATURE_SENTENCES[item.idKey()] }?.let { return it }
        }
        return items.firstNotNullOfOrNull { item -> PROBLEM_SENTENCES[item.idKey()] } ?: ""
    }

    private fun buildPleaseSentence(items: List<AacItem>): String {
        if (items.hasId("help")) {
            val helpTarget = items.firstNotNullOfOrNull { item -> PLEASE_HELP_TARGETS[item.idKey()] }
            if (helpTarget != null) return "Prosim, pomagaj mi pri $helpTarget."
        }
        if (items.hasId("repeat")) {
            items.firstNotNullOfOrNull { item -> REPEAT_SENTENCES[item.idKey()] }?.let { return it }
        }
        if (items.hasId("slower")) {
            items.firstNotNullOfOrNull { item -> SLOWER_SENTENCES[item.idKey()] }?.let { return it }
        }
        if (items.hasId("turn_me")) {
            val direction = items.firstNotNullOfOrNull { item -> TURN_DIRECTIONS[item.idKey()] }
            if (direction != null) return "Prosim, obrni me $direction."
        }
        val target = items.firstNotNullOfOrNull { item -> PLEASE_TARGETS[item.idKey()] } ?: return ""
        return "Prosim, $target."
    }

    private fun buildWantSentence(items: List<AacItem>): String {
        val targetKey = items.firstNotNullOfOrNull { item ->
            val key = item.idKey()
            if (WANT_TARGETS.containsKey(key) || DRINK_TARGETS.containsKey(key)) key else null
        } ?: return ""
        val drinkModifier = items.firstNotNullOfOrNull { item -> DRINK_MODIFIERS[item.idKey()] }
        DRINK_TARGETS[targetKey]?.let { target ->
            return when (drinkModifier) {
                "cold" -> "Rada bi ${target.coldPhrase}."
                "warm" -> "Rada bi ${target.warmPhrase}."
                "small" -> "Rada bi malo ${target.genitive}."
                "more" -> "Rada bi več ${target.genitive}."
                else -> "Rada bi ${target.accusative}."
            }
        }
        val target = WANT_TARGETS[targetKey] ?: return ""
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
        return when (items.firstNotNullOfOrNull { item -> PAIN_INTENSITIES[item.idKey()] }) {
            "light" -> "Malo me boli $target."
            "medium" -> "Srednje močno me boli $target."
            "very_strong" -> "Zelo me boli $target."
            else -> "Boli me $target."
        }
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

    private fun List<AacItem>.hasId(id: String): Boolean {
        return any { item -> item.idKey() == id }
    }

    private fun List<AacItem>.anyIdIn(ids: Set<String>): Boolean {
        return any { item -> item.idKey() in ids }
    }

    private fun List<AacItem>.hasPersonTarget(): Boolean {
        return any { item -> item.idKey() in PERSON_TARGETS }
    }

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

    private data class DrinkTarget(
        val accusative: String,
        val genitive: String,
        val coldPhrase: String,
        val warmPhrase: String
    )

    private data class PersonTarget(
        val nominative: String,
        val accusative: String,
        val dative: String
    )

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

    private val PERSON_ACTION_IDS = setOf(
        "miss_someone",
        "miss_you",
        "contact_call",
        "person_where_is",
        "come_to_me",
        "love_you",
        "person_tell"
    )

    private val PERSON_TARGETS = mapOf(
        "person_dusan" to PersonTarget("Dušan", "Dušana", "Dušanu"),
        "dusan" to PersonTarget("Dušan", "Dušana", "Dušanu"),
        "person_zana" to PersonTarget("Žana", "Žano", "Žani"),
        "sister_zana" to PersonTarget("Žana", "Žano", "Žani"),
        "person_sergej" to PersonTarget("Sergej", "Sergeja", "Sergeju"),
        "grandfather_sergej" to PersonTarget("Sergej", "Sergeja", "Sergeju"),
        "person_julija" to PersonTarget("Julija", "Julijo", "Juliji"),
        "julija" to PersonTarget("Julija", "Julijo", "Juliji"),
        "person_oksana" to PersonTarget("Oksana", "Oksano", "Oksani"),
        "oksana" to PersonTarget("Oksana", "Oksano", "Oksani"),
        "person_inna" to PersonTarget("Inna", "Inno", "Inni"),
        "inna" to PersonTarget("Inna", "Inno", "Inni"),
        "person_franc" to PersonTarget("Franc", "Franca", "Francu"),
        "franc" to PersonTarget("Franc", "Franca", "Francu")
    )

    private val MISS_INTENSITIES = mapOf(
        "miss_little" to "little",
        "miss_very" to "very"
    )

    private val NEED_TARGETS = mapOf(
        "help" to "pomoč",
        "water" to "vodo",
        "food" to "hrano",
        "wc" to "WC",
        "blanket" to "odejo",
        "wheelchair" to "voziček",
        "crutch" to "berglo",
        "doctor" to "zdravnika",
        "nurse" to "medicinsko sestro",
        "therapy" to "terapevta"
    )

    private val NEED_HELP_TARGETS = mapOf(
        "wc" to "WC",
        "dressing" to "oblačenju",
        "dressing_help" to "oblačenju",
        "washing_help" to "umivanju",
        "position" to "položaju",
        "body_position" to "položaju",
        "bed" to "postelji",
        "water" to "vodi",
        "food" to "hrani"
    )

    private val PROBLEM_SENTENCES = mapOf(
        "pain" to "Boli me.",
        "cannot" to "Ne morem.",
        "cold" to "Mraz mi je.",
        "hot" to "Vroče mi je.",
        "afraid" to "Strah me je.",
        "bad" to "Slabo mi je.",
        "uncomfortable" to "Neudobno mi je.",
        "dont_know_problem" to "Ne vem."
    )

    private val CANNOT_SENTENCES = mapOf(
        "cannot_speak" to "Ne morem govoriti.",
        "cannot_stand" to "Ne morem vstati.",
        "cannot_drink" to "Ne morem piti.",
        "cannot_eat" to "Ne morem jesti.",
        "cannot_move" to "Ne morem se premakniti.",
        "cannot_understand" to "Ne morem razumeti."
    )

    private val UNCOMFORTABLE_SENTENCES = mapOf(
        "position" to "Neudoben je položaj.",
        "bed" to "Neudobna je postelja.",
        "blanket" to "Neudobna je odeja.",
        "wheelchair" to "Neudoben je voziček.",
        "clothing" to "Neudobno je oblačilo."
    )

    private val TEMPERATURE_SENTENCES = mapOf(
        "cold" to "Mraz mi je.",
        "hot" to "Vroče mi je."
    )

    private val PLEASE_TARGETS = mapOf(
        "help" to "pomagaj mi",
        "wait" to "počakaj",
        "repeat" to "ponovi",
        "slower" to "govori počasneje",
        "come_to_me" to "pridi k meni",
        "look_at_me" to "poglej me",
        "turn_me" to "obrni me",
        "fix_me" to "popravi me"
    )

    private val PLEASE_HELP_TARGETS = mapOf(
        "wc" to "WC",
        "dressing" to "oblačenju",
        "dressing_help" to "oblačenju",
        "washing_help" to "umivanju",
        "position" to "položaju",
        "body_position" to "položaju",
        "drink" to "pijači",
        "water" to "pijači",
        "food" to "hrani"
    )

    private val REPEAT_SENTENCES = mapOf(
        "repeat_question" to "Prosim, ponovi vprašanje.",
        "repeat_last_sentence" to "Prosim, ponovi zadnji stavek.",
        "repeat_slower" to "Prosim, ponovi počasneje."
    )

    private val SLOWER_SENTENCES = mapOf(
        "slower_little" to "Prosim, govori malo počasneje.",
        "slower_much" to "Prosim, govori zelo počasneje."
    )

    private val TURN_DIRECTIONS = mapOf(
        "turn_left" to "levo",
        "turn_right" to "desno",
        "turn_back" to "na hrbet",
        "turn_side" to "na bok"
    )

    private val SIMPLE_SENTENCES_BY_ID = mapOf(
        "cannot" to "Ne morem.",
        "cold" to "Mraz mi je.",
        "hot" to "Vroče mi je.",
        "afraid" to "Strah me je.",
        "bad" to "Slabo mi je.",
        "uncomfortable" to "Neudobno mi je.",
        "dont_know_problem" to "Ne vem.",
        "what_do" to "Kaj bomo delali?",
        "what_is_this" to "Kaj je to?",
        "what_is_happening" to "Kaj se dogaja?",
        "what_next" to "Kaj bo potem?",
        "what_did_i_say" to "Kaj sem rekla?",
        "where_zana" to "Kje je Žana?",
        "where_dusan" to "Kje je Dušan?",
        "where_are_we" to "Kje smo?",
        "where_phone" to "Kje je telefon?",
        "where_wheelchair" to "Kje je voziček?",
        "when_come" to "Kdaj pride?",
        "when_go" to "Kdaj gremo?",
        "when_therapy" to "Kdaj bo terapija?",
        "when_home" to "Kdaj gremo domov?",
        "when_eat" to "Kdaj jemo?",
        "miss_little" to "Malo pogrešam.",
        "miss_very" to "Zelo pogrešam.",
        "repeat_question" to "Prosim, ponovi vprašanje.",
        "repeat_last_sentence" to "Prosim, ponovi zadnji stavek.",
        "repeat_slower" to "Prosim, ponovi počasneje.",
        "slower_little" to "Prosim, govori malo počasneje.",
        "slower_much" to "Prosim, govori zelo počasneje.",
        "turn_left" to "Prosim, obrni me levo.",
        "turn_right" to "Prosim, obrni me desno.",
        "turn_back" to "Prosim, obrni me na hrbet.",
        "turn_side" to "Prosim, obrni me na bok.",
        "cannot_speak" to "Ne morem govoriti.",
        "cannot_stand" to "Ne morem vstati.",
        "cannot_drink" to "Ne morem piti.",
        "cannot_eat" to "Ne morem jesti.",
        "cannot_move" to "Ne morem se premakniti.",
        "cannot_understand" to "Ne morem razumeti.",
        "clothing" to "Neudobno je oblačilo."
    )

    private val DRINK_TARGETS = mapOf(
        "drink_fanta" to DrinkTarget("Fanto", "Fante", "hladno Fanto", "toplo Fanto"),
        "drink_coca_cola" to DrinkTarget("Coca Colo", "Coca Cole", "hladno Coca Colo", "toplo Coca Colo"),
        "drink_pepsi" to DrinkTarget("Pepsi", "Pepsija", "hladen Pepsi", "topel Pepsi"),
        "drink_water" to DrinkTarget("vodo", "vode", "hladno vodo", "toplo vodo"),
        "water" to DrinkTarget("vodo", "vode", "hladno vodo", "toplo vodo"),
        "drink_tea" to DrinkTarget("čaj", "čaja", "hladen čaj", "topel čaj"),
        "tea" to DrinkTarget("čaj", "čaja", "hladen čaj", "topel čaj"),
        "drink_coffee" to DrinkTarget("kavo", "kave", "hladno kavo", "toplo kavo"),
        "coffee" to DrinkTarget("kavo", "kave", "hladno kavo", "toplo kavo"),
        "juice" to DrinkTarget("sok", "soka", "hladen sok", "topel sok"),
        "drink_milk" to DrinkTarget("mleko", "mleka", "hladno mleko", "toplo mleko")
    )

    private val WANT_TARGETS = mapOf(
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

    private val DRINK_MODIFIERS = mapOf(
        "drink_cold" to "cold",
        "drink_warm" to "warm",
        "drink_small" to "small",
        "drink_more" to "more"
    )

    private val PAIN_INTENSITIES = mapOf(
        "pain_light" to "light",
        "pain_medium" to "medium",
        "pain_very_strong" to "very_strong"
    )
}

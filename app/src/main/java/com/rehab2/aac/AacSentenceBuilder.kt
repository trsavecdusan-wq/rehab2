package com.rehab2.aac

import java.util.Locale

object AacSentenceBuilder {
    fun buildSlovenianSentence(items: List<AacItem>): String {
        if (items.isEmpty()) return ""

        if (AacWcV1SentenceBuilder.canBuild(items)) {
            AacWcV1SentenceBuilder.buildSentence(items).takeIf { it.isNotBlank() }?.let { return it }
        }

        hungryV1TerminalSentence(items)?.let { return it }

        simpleSentence(items)?.let { return it }

        val keys = items.flatMap(::itemKeys).toSet()
        return when {
            items.anyIdIn(PERSON_ACTION_IDS) && items.hasPersonTarget() -> buildPeopleActionSentence(items)
            items.hasId("need") -> buildNeedSentence(items)
            items.hasCareIntent() -> buildCareSentence(items)
            items.hasId("problem") -> buildProblemSentence(items)
            items.hasId("please") -> buildPleaseSentence(items)
            items.hasId("wc") -> buildWcSentence(items)
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
            keys.any { it in REST_KEYS } -> "Rada bi poÄŤivala."
            keys.any { it in WC_KEYS } -> "Moram v toaleto."
            else -> null
        }
    }

    private fun hungryV1TerminalSentence(items: List<AacItem>): String? {
        // TODO: multi-item meal sentence builder in future version.
        return HUNGRY_V1_TERMINAL_SENTENCES[items.lastOrNull()?.idKey()]
    }

    private fun buildPeopleActionSentence(items: List<AacItem>): String {
        val person = items.firstNotNullOfOrNull { item -> PERSON_TARGETS[item.idKey()] } ?: return ""
        return when {
            items.hasId("miss_someone") || items.hasId("miss_you") -> {
                when (items.firstNotNullOfOrNull { item -> MISS_INTENSITIES[item.idKey()] }) {
                    "little" -> "Malo pogreĹˇam ${person.accusative}."
                    "very" -> "Zelo pogreĹˇam ${person.accusative}."
                    else -> "PogreĹˇam ${person.accusative}."
                }
            }
            items.hasId("person_see") -> "Rada bi videla ${person.accusative}."
            items.hasId("person_come") -> "Rada bi, da pride ${person.nominative}."
            items.hasId("contact_call") -> "Prosim, pokliÄŤite ${person.accusative}${personLaterSuffix(items)}."
            items.hasId("contact_message") || items.hasId("message") -> "Rada bi poslala sporoÄŤilo ${person.dative}${personLaterSuffix(items)}."
            items.hasId("i_want") || items.hasId("rada_bi") -> "Rada bi govorila z ${person.instrumental}."
            items.hasId("person_where_is") -> "Kje je ${person.nominative}?"
            items.hasId("come_to_me") -> "Naj pride ${person.nominative}."
            items.hasId("love_you") -> "Rada imam ${person.accusative}."
            items.hasId("person_tell") -> "Povej ${person.dative}."
            else -> ""
        }
    }

    private fun personLaterSuffix(items: List<AacItem>): String {
        return if (items.hasId("later")) " kasneje" else ""
    }

    private fun buildNeedSentence(items: List<AacItem>): String {
        if (items.hasId("help")) {
            val helpTarget = items.firstNotNullOfOrNull { item -> NEED_HELP_TARGETS[item.idKey()] }
            if (helpTarget != null) return "Potrebujem pomoÄŤ pri $helpTarget."
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
            items.firstNotNullOfOrNull { item -> TURN_SENTENCES[item.idKey()] }?.let { return it }
            val direction = items.firstNotNullOfOrNull { item -> TURN_DIRECTIONS[item.idKey()] }
            if (direction != null) return "Prosim, obrni me $direction."
        }
        val target = items.firstNotNullOfOrNull { item -> PLEASE_TARGETS[item.idKey()] } ?: return ""
        return "Prosim, $target."
    }

    private fun buildWantSentence(items: List<AacItem>): String {
        if ((items.hasId("i_want") || items.hasId("rada_bi")) && items.hasId("later")) {
            return "Rada bi kasneje."
        }
        buildDrinkDetailSentence(items)?.let { return it }
        buildFoodDetailSentence(items)?.let { return it }

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
                "more" -> "Rada bi veÄŤ ${target.genitive}."
                else -> "Rada bi ${target.accusative}."
            }
        }
        val target = WANT_TARGETS[targetKey] ?: return ""
        return "Rada bi $target."
    }

    private fun buildDrinkDetailSentence(items: List<AacItem>): String? {
        return when {
            items.hasId("tea") && items.hasId("yes") -> "Rada bi velik \u010daj."
            items.hasId("tea") && items.hasId("no") -> "Rada bi \u010daj."
            items.hasId("tea_large") -> "Rada bi velik \u010daj."
            items.hasId("tea_regular") -> "Rada bi \u010daj."
            items.hasId("coffee_plain") -> "Rada bi navadno kavo."
            items.hasId("coffee_white") -> "Rada bi belo kavo."
            items.hasId("coffee_cappuccino") -> "Rada bi cappuccino."
            items.hasId("drink_fanta") && items.hasId("drink_no_additive") -> "Rada bi Fanto brez dodatka."
            else -> null
        }
    }

    private fun buildFoodDetailSentence(items: List<AacItem>): String? {
        if (items.hasId("food_later")) return "Kasneje bi jedla."
        if (items.hasId("food_enough")) return "Dovolj je."

        val targetKey = items.firstNotNullOfOrNull { item ->
            val key = item.idKey()
            if (FOOD_TARGETS.containsKey(key)) key else null
        } ?: return null
        val target = FOOD_TARGETS[targetKey] ?: return null
        val genitive = FOOD_GENITIVE_TARGETS[targetKey] ?: target
        return when {
            items.hasId("food_little") -> "Rada bi malo $genitive."
            items.hasId("food_more") -> "Rada bi ve\u010d $genitive."
            else -> "Rada bi $target."
        }
    }

    private fun buildWcSentence(items: List<AacItem>): String {
        return when {
            items.hasId("wc_now") -> "Takoj moram v toaleto."
            items.hasId("wc_soon") -> "Kmalu moram v toaleto."
            items.hasId("wc_help") -> "Potrebujem pomo\u010d pri toaleti."
            items.hasId("dressing_help") -> "Prosim, preoblecite me."
            items.hasId("washing_help") -> "Potrebujem umivanje."
            else -> "Moram v toaleto."
        }
    }

    private fun buildCareSentence(items: List<AacItem>): String {
        return items.firstNotNullOfOrNull { item -> CARE_SENTENCES[item.idKey()] }
            ?: if (items.hasId("care")) "Potrebujem pomoÄŤ pri negi." else ""
    }

    private fun buildDontWantSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> DONT_WANT_TARGETS[item.idKey()] }
            ?: return ""
        return "NoÄŤem $target."
    }

    private fun buildMissSentence(items: List<AacItem>): String {
        val target = items.firstNotNullOfOrNull { item -> MISS_TARGETS[item.idKey()] }
            ?: return ""
        return "PogreĹˇam $target."
    }

    private fun buildPainSentence(items: List<AacItem>): String {
        val bodyKey = items.asReversed().firstNotNullOfOrNull { item ->
            item.idKey().takeIf { key -> key in PAIN_BODY_FORMS || key in PAIN_TARGETS }
        }
        if (bodyKey == null) {
            return if (items.hasId("pain")) "Boli me." else ""
        }
        val side = items.firstNotNullOfOrNull { item -> PAIN_SIDES[item.idKey()] }
        val target = painTargetFor(bodyKey, side)
        val intensity = items.firstNotNullOfOrNull { item -> PAIN_INTENSITIES[item.idKey()] }
        val timePrefix = items.firstNotNullOfOrNull { item -> PAIN_TIME_PREFIXES[item.idKey()] }
        val context = items.firstNotNullOfOrNull { item -> PAIN_CONTEXTS[item.idKey()] }
        val verb = painVerbFor(bodyKey, side)
        if (timePrefix != null && intensity != null) {
            return "$timePrefix me ${painContextPrefix(context)}${painIntensityAdverb(intensity)} $verb $target."
        }
        if (timePrefix != null) {
            return "$timePrefix me ${painContextPrefix(context)}$verb $target."
        }
        if (context != null && intensity != null) {
            return "${painContextSentencePrefix(context)} me ${painIntensityAdverb(intensity)} $verb $target."
        }
        if (context != null) {
            return "${painContextSentencePrefix(context)} me $verb $target."
        }
        return when (intensity) {
            "light" -> "Malo me $verb $target."
            "medium" -> "Srednje močno me $verb $target."
            "strong" -> "Močno me $verb $target."
            "very", "very_strong" -> "Zelo me $verb $target."
            else -> "${verb.replaceFirstChar { it.titlecase(Locale("sl", "SI")) }} me $target."
        }
    }

    private fun painContextPrefix(context: String?): String {
        return context?.let { "$it " }.orEmpty()
    }

    private fun painContextSentencePrefix(context: String): String {
        return context.replaceFirstChar { it.titlecase(Locale("sl", "SI")) }
    }

    private fun painIntensityAdverb(intensity: String): String {
        return when (intensity) {
            "light" -> "malo"
            "medium" -> "srednje"
            "strong" -> "moÄŤno"
            "very", "very_strong" -> "zelo"
            else -> ""
        }
    }

    private fun painTargetFor(bodyKey: String, side: String?): String {
        val body = PAIN_BODY_FORMS[bodyKey]
        if (body != null) {
            return when (side) {
                "left" -> body.left ?: body.singular
                "right" -> body.right ?: body.singular
                "both" -> body.both ?: body.singular
                else -> body.singular
            }
        }
        return PAIN_TARGETS[bodyKey].orEmpty()
    }

    private fun painVerbFor(bodyKey: String, side: String?): String {
        val body = PAIN_BODY_FORMS[bodyKey] ?: return "boli"
        return if (side == "both" && body.both != null) body.bothVerb else body.singularVerb
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

    private fun List<AacItem>.hasCareIntent(): Boolean {
        return any { item -> item.idKey() in CARE_ROOT_IDS || item.idKey() in CARE_SENTENCES }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale("sl", "SI"))
            .replace("ÄŤ", "c")
            .replace("Ĺˇ", "s")
            .replace("Ĺľ", "z")
            .replace("Ä‡", "c")
            .replace("Ä‘", "d")
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
        val dative: String,
        val instrumental: String
    )

    private data class PainBodyForm(
        val singular: String,
        val left: String? = null,
        val right: String? = null,
        val both: String? = null,
        val singularVerb: String = "boli",
        val bothVerb: String = "bolita"
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
    private val DONT_WANT_KEYS = setOf("dont_want", "nocem", "noÄŤem")
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
        "contact_message",
        "message",
        "i_want",
        "rada_bi",
        "person_see",
        "person_come",
        "person_where_is",
        "come_to_me",
        "love_you",
        "person_tell"
    )

    private val PERSON_TARGETS = mapOf(
        "person_dusan" to PersonTarget("DuĹˇan", "DuĹˇana", "DuĹˇanu", "DuĹˇanom"),
        "dusan" to PersonTarget("DuĹˇan", "DuĹˇana", "DuĹˇanu", "DuĹˇanom"),
        "person_zana" to PersonTarget("Ĺ˝ana", "Ĺ˝ano", "Ĺ˝ani", "Ĺ˝ano"),
        "sister_zana" to PersonTarget("Ĺ˝ana", "Ĺ˝ano", "Ĺ˝ani", "Ĺ˝ano"),
        "person_sergej" to PersonTarget("Sergej", "Sergeja", "Sergeju", "Sergejem"),
        "grandfather_sergej" to PersonTarget("Sergej", "Sergeja", "Sergeju", "Sergejem"),
        "person_julija" to PersonTarget("Julija", "Julijo", "Juliji", "Julijo"),
        "julija" to PersonTarget("Julija", "Julijo", "Juliji", "Julijo"),
        "person_oksana" to PersonTarget("Oksana", "Oksano", "Oksani", "Oksano"),
        "oksana" to PersonTarget("Oksana", "Oksano", "Oksani", "Oksano"),
        "person_inna" to PersonTarget("Inna", "Inno", "Inni", "Inno"),
        "inna" to PersonTarget("Inna", "Inno", "Inni", "Inno"),
        "person_franc" to PersonTarget("Franc", "Franca", "Francu", "Francem"),
        "franc" to PersonTarget("Franc", "Franca", "Francu", "Francem")
    )

    private val MISS_INTENSITIES = mapOf(
        "miss_little" to "little",
        "miss_very" to "very"
    )

    private val HUNGRY_V1_TERMINAL_SENTENCES = mapOf(
        "hungry_beef_soup" to "Prosim, rada bi govejo juho.",
        "hungry_chicken_soup" to "Prosim, rada bi kokošjo juho.",
        "hungry_vegetable_soup" to "Prosim, rada bi zelenjavno juho.",
        "hungry_pork" to "Prosim, rada bi svinjino.",
        "hungry_chicken" to "Prosim, rada bi piščanca.",
        "hungry_beef" to "Prosim, rada bi govedino.",
        "hungry_veal" to "Prosim, rada bi teletino.",
        "hungry_lamb" to "Prosim, rada bi jagnjetino.",
        "hungry_kid_goat" to "Prosim, rada bi kozlička.",
        "hungry_fish" to "Prosim, rada bi ribo.",
        "hungry_pasta" to "Prosim, rada bi testenine.",
        "hungry_rice" to "Prosim, rada bi riž.",
        "hungry_vegetables" to "Prosim, rada bi zelenjavo.",
        "hungry_roasted_potato" to "Prosim, rada bi pražen krompir.",
        "hungry_fries" to "Prosim, rada bi pomfri.",
        "hungry_mashed_potato" to "Prosim, rada bi pire krompir.",
        "hungry_yogurt" to "Prosim, rada bi jogurt.",
        "hungry_fruit_yogurt" to "Prosim, rada bi sadni jogurt.",
        "hungry_chips" to "Prosim, rada bi čips.",
        "hungry_crackers" to "Prosim, rada bi krekerje.",
        "hungry_hamburger" to "Prosim, rada bi hamburger.",
        "hungry_cevapcici" to "Prosim, rada bi čevapčiče.",
        "hungry_pleskavica" to "Prosim, rada bi pleskavico.",
        "hungry_hotdog" to "Prosim, rada bi hotdog.",
        "hungry_pizza" to "Prosim, rada bi pico.",
        "hungry_burek" to "Prosim, rada bi burek.",
        "hungry_toast" to "Prosim, rada bi toast.",
        "hungry_pancakes" to "Prosim, rada bi palačinke.",
        "hungry_apple" to "Prosim, rada bi jabolko.",
        "hungry_pear" to "Prosim, rada bi hruško.",
        "hungry_banana" to "Prosim, rada bi banano.",
        "hungry_grapes" to "Prosim, rada bi grozdje.",
        "hungry_blueberries" to "Prosim, rada bi borovnice.",
        "hungry_strawberries" to "Prosim, rada bi jagode.",
        "hungry_kiwi" to "Prosim, rada bi kivi.",
        "hungry_ice_cream" to "Prosim, rada bi sladoled.",
        "hungry_cake" to "Prosim, rada bi torto.",
        "hungry_cookies" to "Prosim, rada bi piškote.",
        "hungry_doughnut" to "Prosim, rada bi krof.",
        "hungry_kremsnita" to "Prosim, rada bi kremšnito."
    )

    private val NEED_TARGETS = mapOf(
        "help" to "pomoÄŤ",
        "water" to "vodo",
        "food" to "hrano",
        "wc" to "WC",
        "blanket" to "odejo",
        "wheelchair" to "voziÄŤek",
        "crutch" to "berglo",
        "doctor" to "zdravnika",
        "nurse" to "medicinsko sestro",
        "therapy" to "terapevta"
    )

    private val NEED_HELP_TARGETS = mapOf(
        "wc" to "WC",
        "help_drinking" to "pitju",
        "help_feeding" to "hranjenju",
        "dressing" to "oblaÄŤenju",
        "dressing_help" to "oblaÄŤenju",
        "washing_help" to "umivanju",
        "position" to "poloĹľaju",
        "body_position" to "poloĹľaju",
        "bed" to "postelji",
        "water" to "vodi",
        "food" to "hrani"
    )

    private val PROBLEM_SENTENCES = mapOf(
        "pain" to "Boli me.",
        "cannot" to "Ne morem.",
        "cold" to "Mraz mi je.",
        "hot" to "VroÄŤe mi je.",
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
        "position" to "Neudoben je poloĹľaj.",
        "bed" to "Neudobna je postelja.",
        "blanket" to "Neudobna je odeja.",
        "wheelchair" to "Neudoben je voziÄŤek.",
        "clothing" to "Neudobno je oblaÄŤilo."
    )

    private val TEMPERATURE_SENTENCES = mapOf(
        "cold" to "Mraz mi je.",
        "hot" to "VroÄŤe mi je."
    )

    private val PLEASE_TARGETS = mapOf(
        "help" to "pomagaj mi",
        "wait" to "poÄŤakaj",
        "repeat" to "ponovi",
        "slower" to "govori poÄŤasneje",
        "come_to_me" to "pridi k meni",
        "look_at_me" to "poglej me",
        "turn_me" to "obrni me",
        "fix_me" to "popravi me"
    )

    private val PLEASE_HELP_TARGETS = mapOf(
        "wc" to "WC",
        "help_drinking" to "pitju",
        "help_feeding" to "hranjenju",
        "dressing" to "oblaÄŤenju",
        "dressing_help" to "oblaÄŤenju",
        "washing_help" to "umivanju",
        "position" to "poloĹľaju",
        "body_position" to "poloĹľaju",
        "drink" to "pijaÄŤi",
        "water" to "pijaÄŤi",
        "food" to "hrani"
    )

    private val REPEAT_SENTENCES = mapOf(
        "repeat_question" to "Prosim, ponovi vpraĹˇanje.",
        "repeat_last_sentence" to "Prosim, ponovi zadnji stavek.",
        "repeat_slower" to "Prosim, ponovi poÄŤasneje."
    )

    private val SLOWER_SENTENCES = mapOf(
        "slower_little" to "Prosim, govori malo poÄŤasneje.",
        "slower_much" to "Prosim, govori zelo poÄŤasneje."
    )

    private val TURN_DIRECTIONS = mapOf(
        "turn_left" to "levo",
        "turn_right" to "desno",
        "turn_back" to "na hrbet",
        "turn_side" to "na bok"
    )

    private val CARE_ROOT_IDS = setOf("care", "care_group", "change_position")

    private val CARE_SENTENCES = mapOf(
        "care" to "Potrebujem pomoÄŤ pri negi.",
        "care_group" to "Potrebujem pomoÄŤ pri negi.",
        "washing_help" to "Potrebujem pomoÄŤ pri umivanju.",
        "dressing_help" to "Potrebujem pomoÄŤ pri preoblaÄŤenju.",
        "bed" to "Prosim, pomagajte mi v posteljo.",
        "wheelchair" to "Prosim, pomagajte mi v voziÄŤek.",
        "blanket" to "Potrebujem odejo.",
        "pillow" to "Potrebujem blazino.",
        "change_position" to "Prosim, pomagajte mi spremeniti poloĹľaj.",
        "turn_left" to "Prosim, obrnite me na levo.",
        "turn_right" to "Prosim, obrnite me na desno.",
        "sit_up" to "Prosim, dvignite me.",
        "lie_down" to "Prosim, poloĹľite me."
    )

    private val TURN_SENTENCES = mapOf(
        "turn_left" to "Prosim, obrnite me na levo.",
        "turn_right" to "Prosim, obrnite me na desno.",
        "turn_back" to "Prosim, obrnite me na hrbet.",
        "turn_side" to "Prosim, obrnite me na bok."
    )

    private val SIMPLE_SENTENCES_BY_ID = mapOf(
        "cannot" to "Ne morem.",
        "cold" to "Mraz mi je.",
        "hot" to "VroÄŤe mi je.",
        "afraid" to "Strah me je.",
        "bad" to "Slabo mi je.",
        "uncomfortable" to "Neudobno mi je.",
        "dont_know_problem" to "Ne vem.",
        "what_do" to "Kaj bomo delali?",
        "what_is_this" to "Kaj je to?",
        "what_is_happening" to "Kaj se dogaja?",
        "what_next" to "Kaj bo potem?",
        "what_did_i_say" to "Kaj sem rekla?",
        "activity_group" to "Izberite dejavnost.",
        "music" to "Rada bi posluĹˇala glasbo.",
        "tv" to "Rada bi gledala televizijo.",
        "environment_group" to "Izberite pomoÄŤ v prostoru.",
        "turn_on_tv" to "Prosim, priĹľgite televizijo.",
        "turn_off_tv" to "Prosim, ugasnite televizijo.",
        "turn_on_light" to "Prosim, priĹľgite luÄŤ.",
        "turn_off_light" to "Prosim, ugasnite luÄŤ.",
        "open_window" to "Prosim, odprite okno.",
        "close_window" to "Prosim, zaprite okno.",
        "walk" to "Rada bi Ĺˇla na sprehod s spremstvom.",
        "visit" to "Rada bi obisk.",
        "where_zana" to "Kje je Ĺ˝ana?",
        "where_dusan" to "Kje je DuĹˇan?",
        "where_are_we" to "Kje smo?",
        "where_phone" to "Kje je telefon?",
        "where_wheelchair" to "Kje je voziÄŤek?",
        "when_come" to "Kdaj pride?",
        "when_go" to "Kdaj gremo?",
        "when_therapy" to "Kdaj bo terapija?",
        "when_home" to "Kdaj gremo domov?",
        "when_eat" to "Kdaj jemo?",
        "time_group" to "Izberite ÄŤas.",
        "today" to "Danes.",
        "tomorrow" to "Jutri.",
        "yesterday" to "VÄŤeraj.",
        "now" to "Zdaj.",
        "later" to "Kasneje.",
        "morning" to "Zjutraj.",
        "afternoon" to "Popoldne.",
        "evening" to "ZveÄŤer.",
        "night" to "PonoÄŤi.",
        "miss_little" to "Malo pogreĹˇam.",
        "miss_very" to "Zelo pogreĹˇam.",
        "repeat_question" to "Prosim, ponovi vpraĹˇanje.",
        "repeat_last_sentence" to "Prosim, ponovi zadnji stavek.",
        "repeat_slower" to "Prosim, ponovi poÄŤasneje.",
        "slower_little" to "Prosim, govori malo poÄŤasneje.",
        "slower_much" to "Prosim, govori zelo poÄŤasneje.",
        "turn_left" to "Prosim, obrnite me na levo.",
        "turn_right" to "Prosim, obrnite me na desno.",
        "turn_back" to "Prosim, obrnite me na hrbet.",
        "turn_side" to "Prosim, obrnite me na bok.",
        "cannot_speak" to "Ne morem govoriti.",
        "cannot_stand" to "Ne morem vstati.",
        "cannot_drink" to "Ne morem piti.",
        "cannot_eat" to "Ne morem jesti.",
        "cannot_move" to "Ne morem se premakniti.",
        "cannot_understand" to "Ne morem razumeti.",
        "cold_feeling" to "Mrzlo mi je.",
        "hot_feeling" to "VroÄŤe mi je.",
        "sleepy" to "Spi se mi.",
        "need_rest" to "Rabim poÄŤitek.",
        "help_drinking" to "Potrebujem pomo\u010d pri pitju.",
        "help_feeding" to "Potrebujem pomo\u010d pri hranjenju.",
        "dressing" to "Potrebujem pomo\u010d pri obla\u010denju.",
        "change_position" to "Prosim, pomagajte mi spremeniti poloĹľaj.",
        "sit_up" to "Prosim, dvignite me.",
        "lie_down" to "Prosim, poloĹľite me.",
        "tea_large" to "Rada bi velik \u010daj.",
        "tea_regular" to "Rada bi \u010daj.",
        "coffee_plain" to "Rada bi navadno kavo.",
        "coffee_white" to "Rada bi belo kavo.",
        "coffee_cappuccino" to "Rada bi cappuccino.",
        "food_little" to "Rada bi malo.",
        "food_more" to "\u0160e malo, prosim.",
        "food_enough" to "Dovolj je.",
        "food_later" to "Kasneje bi jedla.",
        "pain_now" to "Boli me zdaj.",
        "pain_today" to "Boli me od danes.",
        "pain_many_days" to "Boli me ve\u010d dni.",
        "pain_very" to "Zelo boli.",
        "shop" to "Prosim, pomagajte mi v trgovini.",
        "restaurant" to "Prosim, pomagajte mi v restavraciji.",
        "transport" to "Prosim, pomagajte mi pri prevozu.",
        "place_group" to "Izberite kraj.",
        "room" to "Rada bi v sobo.",
        "terrace" to "Rada bi na teraso.",
        "bathroom" to "Rada bi v kopalnico.",
        "dining_room" to "Rada bi v jedilnico.",
        "outside" to "Rada bi ven.",
        "inside" to "Rada bi notri.",
        "therapy" to "Rada bi na terapijo.",
        "good" to "Dobro sem.",
        "angry" to "Jezna sem.",
        "sad" to "\u017dalostna sem.",
        "happy" to "Vesela sem.",
        "peace" to "Rada bi mir.",
        "fix_me" to "Prosim, popravi me.",
        "body_position" to "Prosim, popravite moj polo\u017eaj.",
        "clothing" to "Neudobno je oblaÄŤilo."
    )

    private val DRINK_TARGETS = mapOf(
        "drink_fanta" to DrinkTarget("Fanto", "Fante", "hladno Fanto", "toplo Fanto"),
        "drink_coca_cola" to DrinkTarget("Coca Colo", "Coca Cole", "hladno Coca Colo", "toplo Coca Colo"),
        "drink_pepsi" to DrinkTarget("Pepsi", "Pepsija", "hladen Pepsi", "topel Pepsi"),
        "drink_water" to DrinkTarget("vodo", "vode", "hladno vodo", "toplo vodo"),
        "water" to DrinkTarget("vodo", "vode", "hladno vodo", "toplo vodo"),
        "drink_tea" to DrinkTarget("ÄŤaj", "ÄŤaja", "hladen ÄŤaj", "topel ÄŤaj"),
        "tea" to DrinkTarget("ÄŤaj", "ÄŤaja", "hladen ÄŤaj", "topel ÄŤaj"),
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
        "ice_cream" to "sladoled",
        "potato" to "krompir",
        "rice" to "riĹľ",
        "food_yogurt" to "jogurt",
        "food_banana" to "banano",
        "food_apple" to "jabolko",
        "food_lunch" to "kosilo",
        "food_dinner" to "ve\u010derjo",
        "sweet" to "nekaj sladkega",
        "rest" to "poÄŤivala",
        "wc" to "v toaleto",
        "music" to "posluĹˇala glasbo",
        "tv" to "gledala televizijo",
        "walk" to "Ĺˇla na sprehod s spremstvom",
        "visit" to "obisk",
        "room" to "v sobo",
        "terrace" to "na teraso",
        "bathroom" to "v kopalnico",
        "dining_room" to "v jedilnico",
        "home" to "domov",
        "outside" to "ven",
        "inside" to "notri",
        "therapy" to "na terapijo"
    )

    private val FOOD_TARGETS = mapOf(
        "food_soup" to "juho",
        "soup" to "juho",
        "bread" to "kruh",
        "fruit" to "sadje",
        "ice_cream" to "sladoled",
        "potato" to "krompir",
        "rice" to "riĹľ",
        "food_yogurt" to "jogurt",
        "food_banana" to "banano",
        "food_apple" to "jabolko",
        "food_lunch" to "kosilo",
        "food_dinner" to "ve\u010derjo",
        "sweet" to "nekaj sladkega"
    )

    private val FOOD_GENITIVE_TARGETS = mapOf(
        "food_soup" to "juhe",
        "soup" to "juhe",
        "bread" to "kruha",
        "fruit" to "sadja",
        "ice_cream" to "sladoleda",
        "potato" to "krompirja",
        "rice" to "riĹľa",
        "food_yogurt" to "jogurta",
        "food_banana" to "banane",
        "food_apple" to "jabolka",
        "food_lunch" to "kosila",
        "food_dinner" to "ve\u010derje",
        "sweet" to "sladkega"
    )

    private val DONT_WANT_TARGETS = mapOf(
        "drink_fanta" to "Fante",
        "drink_coca_cola" to "Coca Cole",
        "drink_pepsi" to "Pepsija",
        "drink_water" to "vode",
        "water" to "vode",
        "drink_tea" to "ÄŤaja",
        "tea" to "ÄŤaja",
        "drink_coffee" to "kave",
        "coffee" to "kave",
        "juice" to "soka",
        "drink_milk" to "mleka",
        "food_soup" to "juhe",
        "soup" to "juhe",
        "bread" to "kruha",
        "fruit" to "sadja",
        "ice_cream" to "sladoleda",
        "potato" to "krompirja",
        "rice" to "riĹľa",
        "food_yogurt" to "jogurta",
        "food_banana" to "banane",
        "food_apple" to "jabolka",
        "therapy" to "terapije"
    )

    private val MISS_TARGETS = mapOf(
        "person_zana" to "Ĺ˝ano",
        "sister_zana" to "Ĺ˝ano",
        "person_dusan" to "DuĹˇana",
        "dusan" to "DuĹˇana",
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
        "throat" to "grlo",
        "neck" to "vrat",
        "nose" to "nos",
        "mouth" to "usta",
        "left_arm" to "leva roka",
        "right_arm" to "desna roka",
        "left_leg" to "leva noga",
        "right_leg" to "desna noga",
        "arm_shoulder" to "rama",
        "arm_upper" to "nadlaht",
        "arm_elbow" to "komolec",
        "arm_forearm" to "podlaht",
        "arm_wrist" to "zapestje",
        "arm_fingers" to "prsti na roki",
        "leg_hip" to "kolk",
        "leg_thigh" to "stegno",
        "leg_knee" to "koleno",
        "leg_shin" to "golen",
        "leg_ankle" to "gleĹľenj",
        "leg_foot" to "stopalo",
        "leg_toes" to "prsti na nogi",
        "back_upper" to "zgornji del hrbta",
        "back_middle" to "srednji del hrbta",
        "back_lower" to "spodnji del hrbta",
        "belly_left" to "leva stran trebuha",
        "belly_right" to "desna stran trebuha",
        "belly_upper" to "zgornji del trebuha",
        "belly_lower" to "spodnji del trebuha",
        "eye" to "oko",
        "eye_left" to "levo oko",
        "eye_right" to "desno oko",
        "eye_both" to "obe oÄŤesi",
        "ear" to "uho",
        "ear_left" to "levo uho",
        "ear_right" to "desno uho",
        "ear_both" to "obe uĹˇesi",
        "tooth" to "zob",
        "tooth_left" to "levi zob",
        "tooth_right" to "desni zob",
        "tooth_upper" to "zgornji zob",
        "tooth_lower" to "spodnji zob"
    )

    private val PAIN_BODY_FORMS = mapOf(
        "leg" to PainBodyForm(
            singular = "noga",
            left = "leva noga",
            right = "desna noga",
            both = "obe nogi"
        ),
        "arm" to PainBodyForm(
            singular = "roka",
            left = "leva roka",
            right = "desna roka",
            both = "obe roki"
        ),
        "head" to PainBodyForm(singular = "glava"),
        "belly" to PainBodyForm(singular = "trebuh"),
        "back" to PainBodyForm(singular = "hrbet"),
        "left_arm" to PainBodyForm(singular = "leva roka"),
        "right_arm" to PainBodyForm(singular = "desna roka"),
        "left_leg" to PainBodyForm(singular = "leva noga"),
        "right_leg" to PainBodyForm(singular = "desna noga"),
        "arm_shoulder" to PainBodyForm(singular = "rama", left = "leva rama", right = "desna rama"),
        "arm_upper" to PainBodyForm(singular = "nadlaht", left = "leva nadlaht", right = "desna nadlaht"),
        "arm_elbow" to PainBodyForm(singular = "komolec", left = "levi komolec", right = "desni komolec"),
        "arm_forearm" to PainBodyForm(singular = "podlaht", left = "leva podlaht", right = "desna podlaht"),
        "arm_wrist" to PainBodyForm(singular = "zapestje", left = "levo zapestje", right = "desno zapestje"),
        "arm_fingers" to PainBodyForm(singular = "prsti na roki", left = "prsti na levi roki", right = "prsti na desni roki"),
        "leg_hip" to PainBodyForm(singular = "kolk", left = "levi kolk", right = "desni kolk"),
        "leg_thigh" to PainBodyForm(singular = "stegno", left = "levo stegno", right = "desno stegno"),
        "leg_knee" to PainBodyForm(singular = "koleno", left = "levo koleno", right = "desno koleno"),
        "leg_shin" to PainBodyForm(singular = "golen", left = "leva golen", right = "desna golen"),
        "leg_ankle" to PainBodyForm(singular = "gleženj", left = "levi gleženj", right = "desni gleženj"),
        "leg_foot" to PainBodyForm(singular = "stopalo", left = "levo stopalo", right = "desno stopalo"),
        "leg_toes" to PainBodyForm(singular = "prsti na nogi", left = "prsti na levi nogi", right = "prsti na desni nogi")
    )

    private val PAIN_SIDES = mapOf(
        "pain_left" to "left",
        "left_side" to "left",
        "left_arm" to "left",
        "left_leg" to "left",
        "pain_right" to "right",
        "right_side" to "right",
        "right_arm" to "right",
        "right_leg" to "right",
        "pain_both" to "both"
    )

    private val DRINK_MODIFIERS = mapOf(
        "drink_cold" to "cold",
        "drink_warm" to "warm",
        "drink_small" to "small",
        "drink_more" to "more",
        "drink_no_additive" to "no_additive"
    )

    private val PAIN_INTENSITIES = mapOf(
        "pain_light" to "light",
        "pain_medium" to "medium",
        "pain_strong" to "strong",
        "pain_very" to "very",
        "pain_very_strong" to "very_strong"
    )

    private val PAIN_TIME_PREFIXES = mapOf(
        "pain_now" to "Zdaj",
        "pain_today" to "Od danes",
        "pain_many_days" to "Ve\u010d dni",
        "pain_since_today" to "Od danes",
        "pain_since_yesterday" to "Od včeraj",
        "pain_since_morning" to "Od jutra",
        "pain_since_evening" to "Od večera",
        "pain_since_long" to "Že dolgo"
    )

    private val PAIN_CONTEXTS = mapOf(
        "pain_when_moving" to "pri gibanju",
        "pain_when_lifting" to "pri dvigovanju",
        "pain_when_gripping" to "pri prijemu",
        "pain_when_walking" to "pri hoji",
        "pain_when_sitting" to "pri sedenju",
        "pain_when_standing" to "pri vstajanju"
    )
}

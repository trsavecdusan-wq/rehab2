package com.rehab2.aac

object AacStarterContentV1 {
    fun items(): List<AacItem> = listOf(
        starter("yes", "DA", "Da.", "core.yes", "CONFIRMATION", "core", listOf("da", "potrditev"), fixedTopRowPosition = 1),
        starter("dont_understand", "NE RAZUMEM", "Ne razumem.", "core.dont_understand", "CONFIRMATION", "core", listOf("razumevanje", "ne_razumem"), fixedTopRowPosition = 2),
        starter("no", "NE", "Ne.", "core.no", "CONFIRMATION", "core", listOf("ne", "zavrnitev"), fixedTopRowPosition = 3),
        starter("thank_you", "HVALA", "Hvala.", "core.thank_you", "CONFIRMATION", "core", listOf("hvala", "zahvala"), fixedTopRowPosition = 4),
        starter("help", "POMOČ", "Prosim, pomagajte mi.", "core.help", "NEED", "core", listOf("pomoč", "nujno"), fixedTopRowPosition = 5),
        starter("wait", "POČAKAJ", "Počakajte, prosim.", "core.wait", "CORE_ACTION", "core", listOf("počakaj", "čas")),
        starter("repeat", "PONOVI", "Prosim, ponovite.", "core.repeat", "CORE_ACTION", "core", listOf("ponovi", "razumevanje")),
        starter("slower", "POČASNEJE", "Prosim, govorite počasneje.", "core.slower", "CORE_ACTION", "core", listOf("počasneje", "razumevanje")),
        starter("understand", "RAZUMEM", "Razumem.", "core.understand", "CONFIRMATION", "core", listOf("razumem", "potrditev")),

        starter("family_group", "DRUŽINA", "Družina.", "people.family_group", "PEOPLE", "people", listOf("družina", "ljudje"), placements = pageOne(6), opensSubicons = true, children = listOf("dusan", "daughter", "son", "family", "who_is_coming", "when_come"), questionByLanguage = mapOf("sl" to "Koga iz družine?", "uk" to "Кого з родини?", "en" to "Which family member?")),
        starter("friends_group", "PRIJATELJI", "Prijatelji.", "people.friends_group", "PEOPLE", "people", listOf("prijatelji", "ljudje"), placements = pageOne(7), opensSubicons = true, children = listOf("therapist", "doctor", "nurse", "who_is_coming", "when_come"), questionByLanguage = mapOf("sl" to "Kateri prijatelj?", "uk" to "Який друг?", "en" to "Which friend?")),
        starter("call", "POKLIČI", "Prosim, pokličite nekoga.", "people.call", "CORE_ACTION", "people", listOf("pokliči", "telefon"), placements = pageOne(8), opensSubicons = true, children = listOf("dusan", "daughter", "son", "doctor", "family"), questionByLanguage = mapOf("sl" to "Koga naj pokličem?", "uk" to "Кому подзвонити?", "en" to "Who should I call?")),
        starter("message", "SPOROČILO", "Pošljite sporočilo.", "people.message", "CORE_ACTION", "people", listOf("sporočilo"), placements = pageOne(9)),
        starter("miss_someone", "POGREŠAM", "Pogrešam vas.", "people.miss_someone", "PEOPLE", "people", listOf("pogrešam", "ljudje"), placements = pageOne(10)),

        starter("thirsty", "ŽEJNA SEM", "Žejna sem.", "drink.thirsty", "DRINK", "drink", listOf("žejna", "piti"), placements = pageOne(11), opensSubicons = true, children = listOf("water", "water_detail", "juice", "tea", "coffee"), questionByLanguage = mapOf("sl" to "Kaj bi pila?", "uk" to "Що ти хочеш пити?", "en" to "What do you want to drink?")),
        starter("hungry", "LAČNA SEM", "Lačna sem.", "food.hungry", "FOOD", "food", listOf("lačna", "jesti"), placements = pageOne(12), opensSubicons = true, children = listOf("soup", "bread", "fruit"), questionByLanguage = mapOf("sl" to "Kaj želiš jesti?", "uk" to "Що ти хочеш їсти?", "en" to "What do you want to eat?")),
        starter("wc", "WC", "Moram na WC.", "care.wc", "NEED", "care", listOf("wc", "stranišče"), placements = pageOne(14)),
        starter("tired", "UTRUJENA", "Utrujena sem.", "feeling.tired", "FEELING", "feeling", listOf("utrujena", "počutje"), placements = pageOne(15), visibleUnderIds = listOf("feeling")),

        starter("what_do", "KAJ BOMO DELALI?", "Kaj bomo delali?", "activity.what_do", "QUESTION", "activity", listOf("kaj", "delali"), placements = pageOne(16)),
        starter("where_go", "KAM GREMO?", "Kam gremo?", "place.where_go", "QUESTION", "place", listOf("kam", "gremo"), placements = pageOne(17)),
        starter("i_want", "RADA BI", "Rada bi nekaj.", "core.i_want", "CORE_ACTION", "core", listOf("rada_bi", "želim"), placements = pageOne(18)),
        starter("dont_want", "NOČEM", "Tega nočem.", "core.dont_want", "CORE_ACTION", "core", listOf("nočem", "zavrnitev"), placements = pageOne(19)),
        starter("rest", "POČITEK", "Rada bi počivala.", "care.rest", "NEED", "care", listOf("počitek", "utrujena"), placements = pageOne(20)),
        starter("drink", "PIJAČA", "Kaj bi pila?", "drink.root", "DRINK", "drink", listOf("pijača", "piti"), placements = pageOne(21), opensSubicons = true, children = listOf("water", "water_detail", "juice", "tea", "coffee"), questionByLanguage = mapOf("sl" to "Kaj bi pila?", "uk" to "Що ти хочеш пити?", "en" to "What do you want to drink?")),
        starter("food", "HRANA", "Kaj želiš jesti?", "food.root", "FOOD", "food", listOf("hrana", "jesti"), placements = pageOne(22), opensSubicons = true, children = listOf("soup", "bread", "fruit"), questionByLanguage = mapOf("sl" to "Kaj želiš jesti?", "uk" to "Що ти хочеш їсти?", "en" to "What do you want to eat?")),
        starter("feeling", "POČUTJE", "Kako se počutiš?", "feeling.root", "FEELING", "feeling", listOf("počutje"), placements = pageOne(23), opensSubicons = true, children = listOf("good", "bad", "tired", "afraid", "cold", "hot"), questionByLanguage = mapOf("sl" to "Kako se počutiš?", "uk" to "Як ти почуваєшся?", "en" to "How do you feel?")),
        starter("care", "NEGA", "Kaj potrebuješ?", "care.root", "NEED", "care", listOf("nega", "pomoč"), placements = pageOne(24), opensSubicons = true, children = listOf("diaper", "dressing_help", "washing_help", "body_position", "uncomfortable"), questionByLanguage = mapOf("sl" to "Kaj potrebuješ?", "uk" to "Що тобі потрібно?", "en" to "What do you need?")),
        starter("health", "ZDRAVJE", "Kaj je z zdravjem?", "health.root", "NEED", "health", listOf("zdravje"), placements = pageOne(25), opensSubicons = true, children = listOf("doctor", "nurse", "pain_area", "bad"), questionByLanguage = mapOf("sl" to "Kaj je z zdravjem?", "uk" to "Що зі здоров'ям?", "en" to "What is wrong with health?")),
        starter("more", "VEČ", "Kaj še potrebuješ?", "core.more", "CORE_ACTION", "core", listOf("več"), opensSubicons = true, children = listOf("repeat", "slower", "understand", "wheelchair", "not_safe", "stop_movement", "need_rest", "fear_falling", "move_me"), questionByLanguage = mapOf("sl" to "Kaj še potrebuješ?", "uk" to "Що ще потрібно?", "en" to "What else do you need?")),
        starter("pain_area", "BOLEČINA", "Kje te boli?", "pain.area", "PAIN", "pain", listOf("bolečina", "kje"), opensSubicons = true, children = listOf("head", "arm", "leg", "belly", "back", "chest", "throat"), questionByLanguage = mapOf("sl" to "Kje te boli?", "uk" to "Де тебе болить?", "en" to "Where does it hurt?")),

        starter("water", "VODA", "Rada bi vodo.", "drink.water", "DRINK", "drink", listOf("voda", "piti"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("water_detail", "VODA PODROBNO", "Kakšno vodo?", "drink.water.detail", "QUESTION", "drink", listOf("voda", "podrobno", "kakšna"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("cold_water", "MRZLA", "Rada bi mrzlo vodo.", "drink.water.cold", "DRINK", "drink", listOf("mrzla", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("warm_water", "TOPLA", "Rada bi toplo vodo.", "drink.water.warm", "DRINK", "drink", listOf("topla", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("non_sparkling_water", "NEGAZIRANA", "Rada bi negazirano vodo.", "drink.water.non_sparkling", "DRINK", "drink", listOf("negazirana", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("sparkling_water", "GAZIRANA", "Rada bi gazirano vodo.", "drink.water.sparkling", "DRINK", "drink", listOf("gazirana", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("coffee", "KAVA", "Rada bi kavo.", "drink.coffee", "DRINK", "drink", listOf("kava", "piti"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("tea", "ČAJ", "Rada bi čaj.", "drink.tea", "DRINK", "drink", listOf("čaj", "piti"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("juice", "SOK", "Rada bi sok.", "drink.juice", "DRINK", "drink", listOf("sok", "piti"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("not_thirsty", "NISEM ŽEJNA", "Nisem žejna.", "drink.not_thirsty", "DRINK", "drink", listOf("nisem_žejna", "piti"), visibleUnderIds = listOf("drink", "thirsty")),

        starter("not_hungry", "NISEM LAČNA", "Nisem lačna.", "food.not_hungry", "FOOD", "food", listOf("nisem_lačna", "jesti"), visibleUnderIds = listOf("food", "hungry")),
        starter("soup", "JUHA", "Rada bi jedla juho.", "food.soup", "FOOD", "food", listOf("juha", "jesti"), visibleUnderIds = listOf("food", "hungry")),
        starter("bread", "KRUH", "Rada bi jedla kruh.", "food.bread", "FOOD", "food", listOf("kruh", "jesti"), visibleUnderIds = listOf("food", "hungry")),
        starter("fruit", "SADJE", "Rada bi jedla sadje.", "food.fruit", "FOOD", "food", listOf("sadje", "jesti"), visibleUnderIds = listOf("food", "hungry")),

        starter("diaper", "PLENICA", "Prosim, zamenjajte mi plenico.", "care.diaper", "NEED", "care", listOf("plenica", "nega"), visibleUnderIds = listOf("care")),
        starter("dressing_help", "OBLAČENJE", "Prosim, pomagajte mi pri oblačenju.", "care.dressing", "NEED", "care", listOf("oblačenje", "nega"), visibleUnderIds = listOf("care")),
        starter("washing_help", "UMIVANJE", "Prosim, pomagajte mi pri umivanju.", "care.washing", "NEED", "care", listOf("umivanje", "nega"), visibleUnderIds = listOf("care")),
        starter("body_position", "POLOŽAJ", "Prosim, popravite moj položaj.", "care.position", "NEED", "care", listOf("položaj", "nega"), visibleUnderIds = listOf("care")),
        starter("pillow", "BLAZINA", "Prosim, popravite blazino.", "care.pillow", "NEED", "care", listOf("blazina", "udobje"), visibleUnderIds = listOf("care")),
        starter("blanket", "ODEJA", "Prosim, popravite odejo.", "care.blanket", "NEED", "care", listOf("odeja", "udobje"), visibleUnderIds = listOf("care")),
        starter("uncomfortable", "NEUDOBNO", "Neudobno mi je.", "care.uncomfortable", "FEELING", "care", listOf("neudobno", "udobje"), visibleUnderIds = listOf("care")),

        starter("pain", "BOLI ME", "Boli me.", "pain.general", "PAIN", "pain", listOf("bolečina", "boli"), placements = pageOne(13), opensSubicons = true, children = listOf("head", "arm", "leg", "belly", "back", "chest", "throat"), questionByLanguage = mapOf("sl" to "Kje te boli?", "uk" to "Де тебе болить?", "en" to "Where does it hurt?")),
        starter("head", "GLAVA", "Boli me glava.", "pain.head", "PAIN", "pain", listOf("glava", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("arm", "ROKA", "Boli me roka.", "pain.arm", "PAIN", "pain", listOf("roka", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("leg", "NOGA", "Boli me noga.", "pain.leg", "PAIN", "pain", listOf("noga", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("back", "HRBET", "Boli me hrbet.", "pain.back", "PAIN", "pain", listOf("hrbet", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("belly", "TREBUH", "Boli me trebuh.", "pain.belly", "PAIN", "pain", listOf("trebuh", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("chest", "PRSI", "Boli me v prsih.", "pain.chest", "PAIN", "pain", listOf("prsi", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("throat", "GRLO", "Boli me grlo.", "pain.throat", "PAIN", "pain", listOf("grlo", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("pain_light", "MALO BOLI", "Malo boli.", "pain.light", "PAIN", "pain", listOf("malo_boli", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),
        starter("pain_strong", "MOČNO BOLI", "Močno boli.", "pain.strong", "PAIN", "pain", listOf("močno_boli", "bolečina"), visibleUnderIds = listOf("pain", "pain_area")),

        starter("good", "DOBRO SEM", "Dobro sem.", "feeling.good", "FEELING", "feeling", listOf("dobro", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("bad", "SLABO SEM", "Slabo se počutim.", "feeling.bad", "FEELING", "feeling", listOf("slabo", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("afraid", "STRAH ME JE", "Strah me je.", "feeling.afraid", "FEELING", "feeling", listOf("strah", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("angry", "JEZNA SEM", "Jezna sem.", "feeling.angry", "FEELING", "feeling", listOf("jezna", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("sad", "ŽALOSTNA SEM", "Žalostna sem.", "feeling.sad", "FEELING", "feeling", listOf("žalostna", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("happy", "VESELA SEM", "Vesela sem.", "feeling.happy", "FEELING", "feeling", listOf("vesela", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("cold", "ZEBE ME", "Zebe me.", "feeling.cold", "FEELING", "feeling", listOf("zebe", "mraz"), visibleUnderIds = listOf("feeling")),
        starter("hot", "VROČE MI JE", "Vroče mi je.", "feeling.hot", "FEELING", "feeling", listOf("vroče", "počutje"), visibleUnderIds = listOf("feeling")),

        starter("dusan", "DUŠAN", "Prosim, pokličite Dušana.", "people.dusan", "PEOPLE", "people", listOf("dušan", "družina"), visibleUnderIds = listOf("call")),
        starter("daughter", "HČERKA", "Prosim, pokličite hčerko.", "people.daughter", "PEOPLE", "people", listOf("hčerka", "družina"), visibleUnderIds = listOf("call")),
        starter("son", "SIN", "Prosim, pokličite sina.", "people.son", "PEOPLE", "people", listOf("sin", "družina"), visibleUnderIds = listOf("call")),
        starter("therapist", "TERAPEVT", "Prosim, pokličite terapevta.", "people.therapist", "PEOPLE", "people", listOf("terapevt", "oseba"), visibleUnderIds = listOf("call")),
        starter("doctor", "ZDRAVNIK", "Prosim, pokličite zdravnika.", "people.doctor", "PEOPLE", "people", listOf("zdravnik", "oseba"), visibleUnderIds = listOf("call", "health")),
        starter("nurse", "MEDICINSKA SESTRA", "Prosim, pokličite medicinsko sestro.", "people.nurse", "PEOPLE", "people", listOf("medicinska_sestra", "oseba"), visibleUnderIds = listOf("call", "health")),
        starter("who_is_coming", "KDO PRIHAJA", "Kdo prihaja?", "people.who_is_coming", "QUESTION", "people", listOf("kdo", "prihaja")),
        starter("when_come", "KDAJ PRIDEŠ", "Kdaj prideš?", "people.when_come", "QUESTION", "people", listOf("kdaj", "prideš")),

        starter("wheelchair", "VOZIČEK", "Prosim, pripravite voziček.", "mobility.wheelchair", "NEED", "mobility", listOf("voziček", "premik")),
        starter("crutch", "BERGLA", "Prosim, pomagajte mi z berglo.", "mobility.crutch", "NEED", "mobility", listOf("bergla", "varnost")),
        starter("not_safe", "NE VARNO", "Ne počutim se varno.", "mobility.not_safe", "FEELING", "mobility", listOf("varnost", "ne_varno")),
        starter("stop_movement", "USTAVIMO SE", "Ustavimo se, prosim.", "mobility.stop", "CORE_ACTION", "mobility", listOf("ustavimo", "varnost")),
        starter("need_rest", "RABIM POČITEK", "Rabim počitek.", "mobility.rest", "NEED", "mobility", listOf("počitek", "utrujena")),
        starter("fear_falling", "STRAH PASTI", "Strah me je, da bom padla.", "mobility.fear_falling", "FEELING", "mobility", listOf("padec", "strah")),
        starter("help_to_wc", "POMAGAJTE MI DO WC", "Prosim, pomagajte mi do stranišča.", "mobility.help_to_wc", "NEED", "mobility", listOf("wc", "pomoč", "varno")),
        starter("move_me", "PREMAKNITE ME", "Prosim, premaknite me.", "mobility.move_me", "NEED", "mobility", listOf("premik", "pomoč"))
    )

    private fun starter(
        id: String,
        label: String,
        speech: String,
        meaningId: String,
        meaningType: String,
        meaningGroup: String,
        semanticTags: List<String>,
        visibleUnderIds: List<String> = emptyList(),
        addsToSentence: Boolean = false,
        fixedTopRowPosition: Int? = null,
        placements: List<AacPlacement> = emptyList(),
        opensSubicons: Boolean = false,
        children: List<String> = emptyList(),
        questionByLanguage: Map<String, String> = emptyMap()
    ): AacItem {
        return AacItem(
            id = id,
            labelSl = label,
            imagePath = "",
            actionType = if (opensSubicons) "open_subicons" else "speak",
            targetPageId = "",
            speakTextSl = speech,
            speechText = speech,
            iconSource = IconSource.SYSTEM,
            isRootItem = visibleUnderIds.isEmpty(),
            isHiddenUntilParent = false,
            visibleUnderIds = visibleUnderIds,
            children = children,
            placements = placements,
            questionByLanguage = questionByLanguage,
            addsToSentence = addsToSentence,
            speaksImmediately = !opensSubicons,
            opensSubicons = opensSubicons,
            meaningId = meaningId,
            meaningType = meaningType,
            meaningGroup = meaningGroup,
            fixedTopRowPosition = fixedTopRowPosition,
            semanticTags = semanticTags,
            searchKeywordsByLanguage = mapOf("sl" to semanticTags),
            priority = START_PRIORITY + STARTER_IDS.indexOf(id).takeIf { it >= 0 }.orZero()
        )
    }

    private fun pageOne(position: Int): List<AacPlacement> {
        return listOf(AacPlacement(pageId = "page_1", position5x5 = position))
    }

    private fun Int?.orZero(): Int = this ?: 0

    private const val START_PRIORITY = 100
    private val STARTER_IDS = listOf(
        "yes", "dont_understand", "no", "thank_you", "help", "wait", "repeat", "slower", "understand",
        "family_group", "friends_group", "call", "message", "miss_someone",
        "thirsty", "hungry", "pain", "wc", "tired", "what_do", "where_go", "i_want", "dont_want",
        "rest", "drink", "food", "feeling", "care", "health", "pain_area",
        "water", "water_detail", "cold_water", "warm_water", "non_sparkling_water", "sparkling_water", "coffee", "tea", "juice", "not_thirsty",
        "not_hungry", "soup", "bread", "fruit",
        "diaper", "dressing_help", "washing_help", "body_position", "pillow", "blanket", "uncomfortable",
        "head", "arm", "leg", "back", "belly", "chest", "throat", "pain_light", "pain_strong",
        "good", "bad", "afraid", "angry", "sad", "happy", "cold", "hot",
        "dusan", "daughter", "son", "therapist", "doctor", "nurse", "who_is_coming", "when_come",
        "wheelchair", "crutch", "not_safe", "stop_movement", "need_rest", "fear_falling", "help_to_wc", "move_me",
        "more"
    )
}

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
        starter("more", "VEČ", "Kaj še potrebuješ?", "core.more", "CORE_ACTION", "core", listOf("več"), visibleUnderIds = listOf("legacy_more"), opensSubicons = true, children = listOf("repeat", "slower", "understand", "wheelchair", "not_safe", "stop_movement", "need_rest", "fear_falling", "move_me"), questionByLanguage = mapOf("sl" to "Kaj še potrebuješ?", "uk" to "Що ще потрібно?", "en" to "What else do you need?")),
        starter("pain_area", "BOLEČINA", "Kje te boli?", "pain.area", "PAIN", "pain", listOf("bolečina", "kje"), opensSubicons = true, children = listOf("head", "arm", "leg", "belly", "back", "chest", "throat"), questionByLanguage = mapOf("sl" to "Kje te boli?", "uk" to "Де тебе болить?", "en" to "Where does it hurt?")),

        starter("sister_zana", "SESTRA ŽANA", "Pokličite sestro Žano.", "people.sister_zana", "PEOPLE", "people", listOf("žana", "sestra"), placements = pageTwo(6)),
        starter("grandfather_sergej", "DEDEK SERGEJ", "Pokličite dedka Sergeja.", "people.grandfather_sergej", "PEOPLE", "people", listOf("sergej", "dedek"), placements = pageTwo(7)),
        starter("julija", "JULIJA", "Pokličite Julijo.", "people.julija", "PEOPLE", "people", listOf("julija", "prijatelji"), placements = pageTwo(8)),
        starter("oksana", "OKSANA", "Pokličite Oksano.", "people.oksana", "PEOPLE", "people", listOf("oksana", "prijatelji"), placements = pageTwo(9)),
        starter("inna", "INNA", "Pokličite Inno.", "people.inna", "PEOPLE", "people", listOf("inna", "prijatelji"), placements = pageTwo(10)),
        starter("franc", "FRANC", "Pokličite Franca.", "people.franc", "PEOPLE", "people", listOf("franc", "prijatelji"), placements = pageTwo(12)),
        starter("miss_you", "POGREŠAM TE", "Pogrešam te.", "people.miss_you", "PEOPLE", "people", listOf("pogrešam", "ljubezen"), placements = pageTwo(13)),
        starter("love_you", "RADA TE IMAM", "Rada te imam.", "people.love_you", "PEOPLE", "people", listOf("rada_te_imam", "ljubezen"), placements = pageTwo(14)),
        starter("sorry", "OPROSTI", "Oprosti.", "core.sorry", "FEELING", "core", listOf("oprosti"), placements = pageTwo(15)),
        starter("please", "PROSIM", "Prosim.", "core.please", "CORE_ACTION", "core", listOf("prosim"), placements = pageTwo(16)),
        starter("call_me", "POKLIČI ME", "Prosim, pokliči me.", "people.call_me", "CORE_ACTION", "people", listOf("pokliči_me"), placements = pageTwo(17)),
        starter("come_to_me", "PRIDI K MENI", "Prosim, pridi k meni.", "people.come_to_me", "CORE_ACTION", "people", listOf("pridi", "k_meni"), placements = pageTwo(19)),
        starter("contact_message", "SPOROČILO", "Želim poslati sporočilo.", "people.contact_message", "CORE_ACTION", "people", listOf("sporočilo", "ljudje"), placements = pageTwo(22)),
        starter("contact_call", "POKLIČI", "Prosim, pokličite to osebo.", "people.contact_call", "CORE_ACTION", "people", listOf("pokliči", "ljudje"), placements = pageTwo(23)),
        starter("contact_help", "POMOČ", "Prosim, pomagajte mi.", "people.contact_help", "NEED", "people", listOf("pomoč", "ljudje"), placements = pageTwo(24)),
        starter("back_to_main", "NAZAJ", "Nazaj.", "core.back", "CORE_ACTION", "core", listOf("nazaj"), placements = pageTwo(25) + pageFour(20), targetPageId = "page_1"),

        starter("water", "VODA", "Rada bi vodo.", "drink.water", "DRINK", "drink", listOf("voda", "piti"), placements = pageThree(6)),
        starter("water_detail", "VODA PODROBNO", "Kakšno vodo?", "drink.water.detail", "QUESTION", "drink", listOf("voda", "podrobno", "kakšna"), visibleUnderIds = listOf("drink", "thirsty")),
        starter("cold_water", "MRZLA", "Rada bi mrzlo vodo.", "drink.water.cold", "DRINK", "drink", listOf("mrzla", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("warm_water", "TOPLA", "Rada bi toplo vodo.", "drink.water.warm", "DRINK", "drink", listOf("topla", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("non_sparkling_water", "NEGAZIRANA", "Rada bi negazirano vodo.", "drink.water.non_sparkling", "DRINK", "drink", listOf("negazirana", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("sparkling_water", "GAZIRANA", "Rada bi gazirano vodo.", "drink.water.sparkling", "DRINK", "drink", listOf("gazirana", "voda"), visibleUnderIds = listOf("water_detail"), addsToSentence = true),
        starter("coffee", "KAVA", "Rada bi kavo.", "drink.coffee", "DRINK", "drink", listOf("kava", "piti"), placements = pageThree(7)),
        starter("tea", "ČAJ", "Rada bi čaj.", "drink.tea", "DRINK", "drink", listOf("čaj", "piti"), placements = pageThree(8)),
        starter("juice", "SOK", "Rada bi sok.", "drink.juice", "DRINK", "drink", listOf("sok", "piti"), placements = pageThree(9)),
        starter("not_thirsty", "NISEM ŽEJNA", "Nisem žejna.", "drink.not_thirsty", "DRINK", "drink", listOf("nisem_žejna", "piti"), visibleUnderIds = listOf("drink", "thirsty")),

        starter("not_hungry", "NISEM LAČNA", "Nisem lačna.", "food.not_hungry", "FOOD", "food", listOf("nisem_lačna", "jesti"), visibleUnderIds = listOf("food", "hungry")),
        starter("soup", "JUHA", "Rada bi jedla juho.", "food.soup", "FOOD", "food", listOf("juha", "jesti"), placements = pageThree(10)),
        starter("bread", "KRUH", "Rada bi jedla kruh.", "food.bread", "FOOD", "food", listOf("kruh", "jesti"), placements = pageThree(11)),
        starter("fruit", "SADJE", "Rada bi jedla sadje.", "food.fruit", "FOOD", "food", listOf("sadje", "jesti"), placements = pageThree(12)),
        starter("meat", "MESO", "Rada bi jedla meso.", "food.meat", "FOOD", "food", listOf("meso", "jesti"), placements = pageThree(13)),
        starter("sweet", "SLADKO", "Rada bi nekaj sladkega.", "food.sweet", "FOOD", "food", listOf("sladko", "jesti"), placements = pageThree(14)),
        starter("other_food", "DRUGO", "Rada bi nekaj drugega.", "food.other", "FOOD", "food", listOf("drugo", "jesti"), placements = pageThree(15)),

        starter("diaper", "PLENICA", "Prosim, zamenjajte mi plenico.", "care.diaper", "NEED", "care", listOf("plenica", "nega"), visibleUnderIds = listOf("care")),
        starter("dressing_help", "OBLAČENJE", "Prosim, pomagajte mi pri oblačenju.", "care.dressing", "NEED", "care", listOf("oblačenje", "nega"), visibleUnderIds = listOf("care")),
        starter("washing_help", "UMIVANJE", "Prosim, pomagajte mi pri umivanju.", "care.washing", "NEED", "care", listOf("umivanje", "nega"), visibleUnderIds = listOf("care")),
        starter("body_position", "POLOŽAJ", "Prosim, popravite moj položaj.", "care.position", "NEED", "care", listOf("položaj", "nega"), visibleUnderIds = listOf("care")),
        starter("pillow", "BLAZINA", "Prosim, popravite blazino.", "care.pillow", "NEED", "care", listOf("blazina", "udobje"), visibleUnderIds = listOf("care")),
        starter("blanket", "ODEJA", "Prosim, popravite odejo.", "care.blanket", "NEED", "care", listOf("odeja", "udobje"), visibleUnderIds = listOf("care")),
        starter("uncomfortable", "NEUDOBNO", "Neudobno mi je.", "care.uncomfortable", "FEELING", "care", listOf("neudobno", "udobje"), visibleUnderIds = listOf("care")),

        starter("pain", "BOLI ME", "Boli me.", "pain.general", "PAIN", "pain", listOf("bolečina", "boli"), placements = pageOne(13), opensSubicons = true, children = listOf("head", "arm", "leg", "belly", "back", "chest", "throat"), questionByLanguage = mapOf("sl" to "Kje te boli?", "uk" to "Де тебе болить?", "en" to "Where does it hurt?")),
        starter("head", "GLAVA", "Boli me glava.", "pain.head", "PAIN", "pain", listOf("glava", "bolečina"), placements = pageFour(6)),
        starter("arm", "ROKA", "Boli me roka.", "pain.arm", "PAIN", "pain", listOf("roka", "bolečina"), placements = pageFour(7)),
        starter("leg", "NOGA", "Boli me noga.", "pain.leg", "PAIN", "pain", listOf("noga", "bolečina"), placements = pageFour(8)),
        starter("back", "HRBET", "Boli me hrbet.", "pain.back", "PAIN", "pain", listOf("hrbet", "bolečina"), placements = pageFour(9)),
        starter("belly", "TREBUH", "Boli me trebuh.", "pain.belly", "PAIN", "pain", listOf("trebuh", "bolečina"), placements = pageFour(10)),
        starter("chest", "PRSI", "Boli me v prsih.", "pain.chest", "PAIN", "pain", listOf("prsi", "bolečina"), placements = pageFour(11)),
        starter("throat", "GRLO", "Boli me grlo.", "pain.throat", "PAIN", "pain", listOf("grlo", "bolečina"), placements = pageFour(12)),
        starter("left_side", "LEVA", "Na levi strani.", "pain.left", "PAIN", "pain", listOf("leva", "stran"), placements = pageFour(13)),
        starter("right_side", "DESNA", "Na desni strani.", "pain.right", "PAIN", "pain", listOf("desna", "stran"), placements = pageFour(14)),
        starter("other_body", "DRUGO", "Boli drugje.", "pain.other", "PAIN", "pain", listOf("drugo", "bolečina"), placements = pageFour(15)),
        starter("pain_light", "MALO", "Malo boli.", "pain.light", "PAIN", "pain", listOf("malo_boli", "bolečina"), placements = pageFour(16)),
        starter("pain_medium", "SREDNJE", "Srednje močno boli.", "pain.medium", "PAIN", "pain", listOf("srednje", "bolečina"), placements = pageFour(17)),
        starter("pain_strong", "MOČNO", "Močno boli.", "pain.strong", "PAIN", "pain", listOf("močno_boli", "bolečina"), placements = pageFour(18)),
        starter("pain_very_strong", "ZELO MOČNO", "Zelo močno boli.", "pain.very_strong", "PAIN", "pain", listOf("zelo_močno", "bolečina"), placements = pageFour(19)),

        starter("good", "DOBRO SEM", "Dobro sem.", "feeling.good", "FEELING", "feeling", listOf("dobro", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("bad", "SLABO SEM", "Slabo se počutim.", "feeling.bad", "FEELING", "feeling", listOf("slabo", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("afraid", "STRAH ME JE", "Strah me je.", "feeling.afraid", "FEELING", "feeling", listOf("strah", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("angry", "JEZNA SEM", "Jezna sem.", "feeling.angry", "FEELING", "feeling", listOf("jezna", "počutje"), visibleUnderIds = listOf("feeling")),
        starter("sad", "ŽALOSTNA SEM", "Žalostna sem.", "feeling.sad", "FEELING", "feeling", listOf("žalostna", "počutje"), placements = pageTwo(21), visibleUnderIds = listOf("feeling")),
        starter("happy", "VESELA SEM", "Vesela sem.", "feeling.happy", "FEELING", "feeling", listOf("vesela", "počutje"), placements = pageTwo(20), visibleUnderIds = listOf("feeling")),
        starter("cold", "ZEBE ME", "Zebe me.", "feeling.cold", "FEELING", "feeling", listOf("zebe", "mraz"), visibleUnderIds = listOf("feeling")),
        starter("hot", "VROČE MI JE", "Vroče mi je.", "feeling.hot", "FEELING", "feeling", listOf("vroče", "počutje"), visibleUnderIds = listOf("feeling")),

        starter("dusan", "DUŠAN", "Prosim, pokličite Dušana.", "people.dusan", "PEOPLE", "people", listOf("dušan", "družina"), placements = pageTwo(11)),
        starter("daughter", "HČERKA", "Prosim, pokličite hčerko.", "people.daughter", "PEOPLE", "people", listOf("hčerka", "družina"), visibleUnderIds = listOf("call")),
        starter("son", "SIN", "Prosim, pokličite sina.", "people.son", "PEOPLE", "people", listOf("sin", "družina"), visibleUnderIds = listOf("call")),
        starter("therapist", "TERAPEVT", "Prosim, pokličite terapevta.", "people.therapist", "PEOPLE", "people", listOf("terapevt", "oseba"), visibleUnderIds = listOf("call")),
        starter("doctor", "ZDRAVNIK", "Prosim, pokličite zdravnika.", "people.doctor", "PEOPLE", "people", listOf("zdravnik", "oseba"), visibleUnderIds = listOf("call", "health")),
        starter("nurse", "MEDICINSKA SESTRA", "Prosim, pokličite medicinsko sestro.", "people.nurse", "PEOPLE", "people", listOf("medicinska_sestra", "oseba"), visibleUnderIds = listOf("call", "health")),
        starter("who_is_coming", "KDO PRIHAJA", "Kdo prihaja?", "people.who_is_coming", "QUESTION", "people", listOf("kdo", "prihaja")),
        starter("when_come", "KDAJ PRIDEŠ?", "Kdaj prideš?", "people.when_come", "QUESTION", "people", listOf("kdaj", "prideš"), placements = pageTwo(18)),

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
        questionByLanguage: Map<String, String> = emptyMap(),
        targetPageId: String = ""
    ): AacItem {
        return AacItem(
            id = id,
            labelSl = label,
            imagePath = "",
            actionType = when {
                targetPageId.isNotBlank() -> "open_page"
                opensSubicons -> "open_subicons"
                else -> "speak"
            },
            targetPageId = targetPageId,
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

    private fun pageTwo(position: Int): List<AacPlacement> {
        return listOf(AacPlacement(pageId = "page_2", position5x5 = position))
    }

    private fun pageThree(position: Int): List<AacPlacement> {
        return listOf(AacPlacement(pageId = "page_3", position5x5 = position))
    }

    private fun pageFour(position: Int): List<AacPlacement> {
        return listOf(AacPlacement(pageId = "page_4", position5x5 = position))
    }

    private fun Int?.orZero(): Int = this ?: 0

    private const val START_PRIORITY = 100
    private val STARTER_IDS = listOf(
        "yes", "dont_understand", "no", "thank_you", "help", "wait", "repeat", "slower", "understand",
        "family_group", "friends_group", "call", "message", "miss_someone",
        "thirsty", "hungry", "pain", "wc", "tired", "what_do", "where_go", "i_want", "dont_want",
        "rest", "drink", "food", "feeling", "care", "health",
        "sister_zana", "grandfather_sergej", "julija", "oksana", "inna",
        "dusan", "franc", "miss_you", "love_you", "sorry", "please", "call_me", "when_come", "come_to_me", "happy",
        "sad", "contact_message", "contact_call", "contact_help", "back_to_main",
        "water", "coffee", "tea", "juice", "soup", "bread", "fruit", "meat", "sweet", "other_food",
        "head", "arm", "leg", "back", "belly", "chest", "throat", "left_side", "right_side", "other_body",
        "pain_light", "pain_medium", "pain_strong", "pain_very_strong",
        "water_detail", "cold_water", "warm_water", "non_sparkling_water", "sparkling_water", "not_thirsty",
        "not_hungry", "diaper", "dressing_help", "washing_help", "body_position", "pillow", "blanket", "uncomfortable",
        "pain_area", "good", "bad", "afraid", "angry", "cold", "hot",
        "daughter", "son", "therapist", "doctor", "nurse", "who_is_coming",
        "wheelchair", "crutch", "not_safe", "stop_movement", "need_rest", "fear_falling", "help_to_wc", "move_me",
        "more"
    )
}

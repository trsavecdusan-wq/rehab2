package com.rehab2.aac

object AacStarterContentV1 {
    fun items(): List<AacItem> = listOf(
        starter("yes", "DA", "Da.", "core.yes", "CONFIRMATION", "core", listOf("da", "potrditev")),
        starter("no", "NE", "Ne.", "core.no", "CONFIRMATION", "core", listOf("ne", "zavrnitev")),
        starter("help", "POMOČ", "Prosim, pomagajte mi.", "core.help", "NEED", "core", listOf("pomoč", "nujno")),
        starter("dont_understand", "NE RAZUMEM", "Ne razumem.", "core.dont_understand", "CONFIRMATION", "core", listOf("razumevanje", "ne_razumem")),
        starter("wait", "POČAKAJ", "Počakajte, prosim.", "core.wait", "CORE_ACTION", "core", listOf("počakaj", "čas")),
        starter("repeat", "PONOVI", "Prosim, ponovite.", "core.repeat", "CORE_ACTION", "core", listOf("ponovi", "razumevanje")),
        starter("slower", "POČASNEJE", "Prosim, govorite počasneje.", "core.slower", "CORE_ACTION", "core", listOf("počasneje", "razumevanje")),
        starter("understand", "RAZUMEM", "Razumem.", "core.understand", "CONFIRMATION", "core", listOf("razumem", "potrditev")),

        starter("water", "VODA", "Rada bi vodo.", "drink.water", "DRINK", "drink", listOf("voda", "piti")),
        starter("coffee", "KAVA", "Rada bi kavo.", "drink.coffee", "DRINK", "drink", listOf("kava", "piti")),
        starter("tea", "ČAJ", "Rada bi čaj.", "drink.tea", "DRINK", "drink", listOf("čaj", "piti")),
        starter("juice", "SOK", "Rada bi sok.", "drink.juice", "DRINK", "drink", listOf("sok", "piti")),
        starter("not_thirsty", "NISEM ŽEJNA", "Nisem žejna.", "drink.not_thirsty", "DRINK", "drink", listOf("nisem_žejna", "piti")),

        starter("hungry", "LAČNA SEM", "Lačna sem.", "food.hungry", "FOOD", "food", listOf("lačna", "jesti")),
        starter("not_hungry", "NISEM LAČNA", "Nisem lačna.", "food.not_hungry", "FOOD", "food", listOf("nisem_lačna", "jesti")),
        starter("soup", "JUHA", "Rada bi jedla juho.", "food.soup", "FOOD", "food", listOf("juha", "jesti")),
        starter("bread", "KRUH", "Rada bi jedla kruh.", "food.bread", "FOOD", "food", listOf("kruh", "jesti")),
        starter("fruit", "SADJE", "Rada bi jedla sadje.", "food.fruit", "FOOD", "food", listOf("sadje", "jesti")),

        starter("diaper", "PLENICA", "Prosim, zamenjajte mi plenico.", "care.diaper", "NEED", "care", listOf("plenica", "nega")),
        starter("dressing_help", "OBLAČENJE", "Prosim, pomagajte mi pri oblačenju.", "care.dressing", "NEED", "care", listOf("oblačenje", "nega")),
        starter("washing_help", "UMIVANJE", "Prosim, pomagajte mi pri umivanju.", "care.washing", "NEED", "care", listOf("umivanje", "nega")),
        starter("body_position", "POLOŽAJ", "Prosim, popravite moj položaj.", "care.position", "NEED", "care", listOf("položaj", "nega")),
        starter("pillow", "BLAZINA", "Prosim, popravite blazino.", "care.pillow", "NEED", "care", listOf("blazina", "udobje")),
        starter("blanket", "ODEJA", "Prosim, popravite odejo.", "care.blanket", "NEED", "care", listOf("odeja", "udobje")),
        starter("uncomfortable", "NEUDOBNO", "Neudobno mi je.", "care.uncomfortable", "FEELING", "care", listOf("neudobno", "udobje")),

        starter("pain", "BOLI ME", "Boli me.", "pain.general", "PAIN", "pain", listOf("bolečina", "boli")),
        starter("head", "GLAVA", "Boli me glava.", "pain.head", "PAIN", "pain", listOf("glava", "bolečina")),
        starter("arm", "ROKA", "Boli me roka.", "pain.arm", "PAIN", "pain", listOf("roka", "bolečina")),
        starter("leg", "NOGA", "Boli me noga.", "pain.leg", "PAIN", "pain", listOf("noga", "bolečina")),
        starter("back", "HRBET", "Boli me hrbet.", "pain.back", "PAIN", "pain", listOf("hrbet", "bolečina")),
        starter("belly", "TREBUH", "Boli me trebuh.", "pain.belly", "PAIN", "pain", listOf("trebuh", "bolečina")),
        starter("chest", "PRSI", "Boli me v prsih.", "pain.chest", "PAIN", "pain", listOf("prsi", "bolečina")),
        starter("throat", "GRLO", "Boli me grlo.", "pain.throat", "PAIN", "pain", listOf("grlo", "bolečina")),
        starter("pain_light", "MALO BOLI", "Malo boli.", "pain.light", "PAIN", "pain", listOf("malo_boli", "bolečina")),
        starter("pain_strong", "MOČNO BOLI", "Močno boli.", "pain.strong", "PAIN", "pain", listOf("močno_boli", "bolečina")),

        starter("good", "DOBRO SEM", "Dobro sem.", "feeling.good", "FEELING", "feeling", listOf("dobro", "počutje")),
        starter("bad", "SLABO SEM", "Slabo se počutim.", "feeling.bad", "FEELING", "feeling", listOf("slabo", "počutje")),
        starter("tired", "UTRUJENA SEM", "Utrujena sem.", "feeling.tired", "FEELING", "feeling", listOf("utrujena", "počutje")),
        starter("afraid", "STRAH ME JE", "Strah me je.", "feeling.afraid", "FEELING", "feeling", listOf("strah", "počutje")),
        starter("angry", "JEZNA SEM", "Jezna sem.", "feeling.angry", "FEELING", "feeling", listOf("jezna", "počutje")),
        starter("sad", "ŽALOSTNA SEM", "Žalostna sem.", "feeling.sad", "FEELING", "feeling", listOf("žalostna", "počutje")),
        starter("happy", "VESELA SEM", "Vesela sem.", "feeling.happy", "FEELING", "feeling", listOf("vesela", "počutje")),
        starter("cold", "ZEBE ME", "Zebe me.", "feeling.cold", "FEELING", "feeling", listOf("zebe", "mraz")),
        starter("hot", "VROČE MI JE", "Vroče mi je.", "feeling.hot", "FEELING", "feeling", listOf("vroče", "počutje")),

        starter("dusan", "DUŠAN", "Prosim, pokličite Dušana.", "people.dusan", "PEOPLE", "people", listOf("dušan", "družina")),
        starter("daughter", "HČERKA", "Prosim, pokličite hčerko.", "people.daughter", "PEOPLE", "people", listOf("hčerka", "družina")),
        starter("son", "SIN", "Prosim, pokličite sina.", "people.son", "PEOPLE", "people", listOf("sin", "družina")),
        starter("therapist", "TERAPEVT", "Prosim, pokličite terapevta.", "people.therapist", "PEOPLE", "people", listOf("terapevt", "oseba")),
        starter("doctor", "ZDRAVNIK", "Prosim, pokličite zdravnika.", "people.doctor", "PEOPLE", "people", listOf("zdravnik", "oseba")),
        starter("nurse", "MEDICINSKA SESTRA", "Prosim, pokličite medicinsko sestro.", "people.nurse", "PEOPLE", "people", listOf("medicinska_sestra", "oseba")),
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
        semanticTags: List<String>
    ): AacItem {
        return AacItem(
            id = id,
            labelSl = label,
            imagePath = "",
            actionType = "speak",
            targetPageId = "",
            speakTextSl = speech,
            speechText = speech,
            iconSource = IconSource.SYSTEM,
            isRootItem = true,
            isHiddenUntilParent = false,
            addsToSentence = false,
            speaksImmediately = true,
            opensSubicons = false,
            meaningId = meaningId,
            meaningType = meaningType,
            meaningGroup = meaningGroup,
            semanticTags = semanticTags,
            searchKeywordsByLanguage = mapOf("sl" to semanticTags),
            priority = START_PRIORITY + STARTER_IDS.indexOf(id).takeIf { it >= 0 }.orZero()
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    private const val START_PRIORITY = 100
    private val STARTER_IDS = listOf(
        "yes", "no", "help", "dont_understand", "wait", "repeat", "slower", "understand",
        "water", "coffee", "tea", "juice", "not_thirsty",
        "hungry", "not_hungry", "soup", "bread", "fruit",
        "diaper", "dressing_help", "washing_help", "body_position", "pillow", "blanket", "uncomfortable",
        "pain", "head", "arm", "leg", "back", "belly", "chest", "throat", "pain_light", "pain_strong",
        "good", "bad", "tired", "afraid", "angry", "sad", "happy", "cold", "hot",
        "dusan", "daughter", "son", "therapist", "doctor", "nurse", "who_is_coming", "when_come",
        "wheelchair", "crutch", "not_safe", "stop_movement", "need_rest", "fear_falling", "help_to_wc", "move_me"
    )
}

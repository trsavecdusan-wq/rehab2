package com.rehab2.aac

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object AacSampleContentCreator {
    private const val TAG = "AacSampleContentCreator"

    data class Result(
        val createdFiles: List<String>,
        val skippedFiles: List<String>,
        val failed: Boolean
    )

    fun createIfMissing(context: Context): Result {
        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)
        if (
            itemsFile == null ||
            profilesDir == null ||
            customIconsDir == null ||
            socaIconsDir == null ||
            arasaacIconsDir == null
        ) {
            return Result(created, skipped, failed = true)
        }

        return try {
            if (!AacStoragePaths.ensureAacContentDirs(context)) {
                return Result(created, skipped, failed = true)
            }

            val profileFiles = listOf(
                java.io.File(profilesDir, "dom.json") to buildDomProfileJson(),
                java.io.File(profilesDir, "video_call.json") to buildVideoCallProfileJson(),
                java.io.File(profilesDir, "real_world.json") to buildRealWorldProfileJson()
            )

            if (itemsFile.exists()) {
                Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${itemsFile.absolutePath}")
                skipped += itemsFile.absolutePath
            } else {
                itemsFile.writeText(buildItemsJson().toString(2), Charsets.UTF_8)
                Log.d(TAG, "AAC_SAMPLE CREATED_ITEMS_JSON path=${itemsFile.absolutePath}")
                created += itemsFile.absolutePath
            }

            profileFiles.forEach { (profileFile, profileJson) ->
                if (profileFile.exists()) {
                    Log.d(TAG, "AAC_SAMPLE SKIP_EXISTING_FILE path=${profileFile.absolutePath}")
                    skipped += profileFile.absolutePath
                } else {
                    ensureParentExists(profileFile.parentFile)
                    profileFile.writeText(profileJson.toString(2), Charsets.UTF_8)
                    Log.d(TAG, "AAC_SAMPLE CREATED_PROFILE_JSON path=${profileFile.absolutePath}")
                    created += profileFile.absolutePath
                }
            }

            Result(created, skipped, failed = false)
        } catch (error: Exception) {
            Log.w(TAG, "AAC_SAMPLE CREATE_FAILED", error)
            Result(created, skipped, failed = true)
        }
    }

    private fun ensureParentExists(dir: java.io.File?) {
        if (dir == null) throw IllegalStateException("Storage directory is unavailable.")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Could not create directory: ${dir.absolutePath}")
        }
    }

    private fun buildItemsJson(): JSONObject {
        return JSONObject().put(
            "items",
            JSONArray()
                .put(
                    sampleItem(
                        id = "yes",
                        label = "DA",
                        labelUk = "ТАК",
                        labelEn = "YES",
                        speechSl = "da",
                        speechUk = "так",
                        speechEn = "yes",
                        imagePath = "",
                        isRootItem = true,
                        priority = 0,
                        fixedTopRowPosition = 2
                    )
                )
                .put(
                    sampleItem(
                        id = "dont_understand",
                        label = "NE RAZUMEM",
                        labelUk = "Я НЕ РОЗУМІЮ",
                        labelEn = "I DON'T UNDERSTAND",
                        speechSl = "ne razumem",
                        speechUk = "Я не розумію",
                        speechEn = "I don't understand",
                        imagePath = "",
                        conceptId = "dont_understand",
                        isRootItem = true,
                        priority = 1,
                        fixedTopRowPosition = 3
                    )
                        .put("labelUk", "Я НЕ РОЗУМІЮ")
                        .put("speakTextUk", "Я не розумію")
                )
                .put(
                    sampleItem(
                        id = "no",
                        label = "NE",
                        labelUk = "НІ",
                        labelEn = "NO",
                        speechSl = "ne",
                        speechUk = "ні",
                        speechEn = "no",
                        imagePath = "",
                        isRootItem = true,
                        priority = 2,
                        fixedTopRowPosition = 1
                    )
                )
                .put(
                    sampleItem(
                        id = "thank_you",
                        label = "HVALA",
                        imagePath = "",
                        conceptId = "thank_you",
                        isRootItem = true,
                        priority = 3,
                        fixedTopRowPosition = 4
                    )
                )
                .put(
                    sampleItem(
                        id = "sorry",
                        label = "OPROSTI",
                        imagePath = "",
                        conceptId = "sorry",
                        isRootItem = true,
                        priority = 4,
                        fixedTopRowPosition = 5
                    )
                )
                .put(
                    sampleItem(
                        id = "help",
                        label = "POMOČ",
                        imagePath = "",
                        isRootItem = true,
                        priority = 5
                    )
                        .put("labelSl", "POMOČ")
                        .put("text", "POMOČ")
                        .put("baseText", "POMOČ")
                        .put("labelUk", "ДОПОМОГА")
                        .put("labelEn", "HELP")
                        .put("speechText", "pomagaj mi")
                        .put("speakTextSl", "pomagaj mi")
                        .put("speakTextUk", "Допоможіть мені")
                        .put("speechTextEn", "Help me")
                )
                .put(
                    sampleItem(
                        id = "pain",
                        label = "BOLI",
                        imagePath = "",
                        isRootItem = true,
                        priority = 6
                    )
                        .put("labelUk", "БОЛИТЬ")
                        .put("labelEn", "PAIN")
                        .put("speechText", "boli me")
                        .put("speakTextSl", "boli me")
                        .put("speakTextUk", "Мені болить")
                        .put("speechTextEn", "I am in pain")
                        .put("opensSubicons", true)
                        .put("addsToSentence", false)
                        .put("speaksImmediately", false)
                        .put("actionType", "open_subicons")
                        .put("questionByLanguage", JSONObject()
                            .put("sl", "Kje te boli?")
                            .put("uk", "Де тебе болить?")
                            .put("en", "Where does it hurt?")
                        )
                        .put(
                            "children",
                            JSONArray()
                                .put("head")
                                .put("arm")
                                .put("leg")
                                .put("belly")
                                .put("back")
                                .put("chest")
                                .put("throat")
                        )
                )
                .put(
                    sampleItem(
                        id = "thirsty",
                        label = "ŽEJNA",
                        imagePath = "",
                        conceptId = "thirsty",
                        isRootItem = true,
                        priority = 7,
                        followUpQuestion = "Kaj želiš piti?"
                    )
                        .put("labelSl", "ŽEJNA")
                        .put("text", "ŽEJNA")
                        .put("baseText", "ŽEJNA")
                        .put("labelUk", "ХОЧУ ПИТИ")
                        .put("labelEn", "THIRSTY")
                        .put("speechText", "žejna sem")
                        .put("speakTextSl", "žejna sem")
                        .put("speakTextUk", "Я хочу пити")
                        .put("speechTextEn", "I am thirsty")
                        .put("followUpQuestion", "Kaj želiš piti?")
                        .put(
                        "children",
                        JSONArray()
                            .put("water")
                            .put("juice")
                            .put("coffee")
                            .put("tea")
                    )
                )
                .put(
                    sampleItem(
                        id = "hungry",
                        label = "LAČNA",
                        labelUk = "ХОЧУ ЇСТИ",
                        labelEn = "HUNGRY",
                        speechSl = "lačna sem",
                        speechUk = "Я хочу їсти",
                        speechEn = "I am hungry",
                        imagePath = "",
                        conceptId = "hungry",
                        isRootItem = true,
                        priority = 8
                    )
                )
                .put(
                    sampleItem(
                        id = "food",
                        label = "HRANA",
                        labelUk = "ЇЖА",
                        labelEn = "FOOD",
                        speechSl = "kaj želiš jesti",
                        speechUk = "Що ти хочеш їсти",
                        speechEn = "What do you want to eat",
                        imagePath = "",
                        conceptId = "food",
                        isRootItem = true,
                        priority = 9
                    )
                        .put("opensSubicons", true)
                        .put("addsToSentence", false)
                        .put("speaksImmediately", false)
                        .put("actionType", "open_subicons")
                        .put("questionByLanguage", JSONObject()
                            .put("sl", "Kaj želiš jesti?")
                            .put("uk", "Що ти хочеш їсти?")
                            .put("en", "What do you want to eat?")
                        )
                        .put(
                            "children",
                            JSONArray()
                                .put("soup")
                                .put("bread")
                                .put("fruit")
                        )
                )
                .put(
                    sampleItem(
                        id = "wc",
                        label = "WC",
                        labelUk = "ТУАЛЕТ",
                        labelEn = "TOILET",
                        speechSl = "moram v toaleto",
                        speechUk = "Мені потрібно в туалет",
                        speechEn = "I need the toilet",
                        imagePath = "",
                        conceptId = "wc",
                        isRootItem = true,
                        priority = 9
                    )
                )
                .put(
                    sampleItem(
                        id = "turn_me",
                        label = "OBRNI ME",
                        labelUk = "ПОВЕРНІТЬ МЕНЕ",
                        labelEn = "TURN ME",
                        speechSl = "prosim, obrni me",
                        speechUk = "Будь ласка, поверніть мене",
                        speechEn = "Please turn me",
                        imagePath = "",
                        conceptId = "turn_me",
                        isRootItem = true,
                        priority = 10
                    )
                )
                .put(
                    sampleItem(
                        id = "sleep",
                        label = "SPATI",
                        labelUk = "СПАТИ",
                        labelEn = "SLEEP",
                        speechSl = "želim spati",
                        speechUk = "Я хочу спати",
                        speechEn = "I want to sleep",
                        imagePath = "",
                        conceptId = "sleep",
                        isRootItem = true,
                        priority = 11
                    )
                )
                .put(
                    sampleItem(
                        id = "tired",
                        label = "UTRUJENA",
                        labelUk = "ВТОМЛЕНА",
                        labelEn = "TIRED",
                        speechSl = "utrujena sem",
                        speechUk = "Я втомлена",
                        speechEn = "I am tired",
                        imagePath = "",
                        conceptId = "tired",
                        isRootItem = true,
                        priority = 12
                    )
                )
                .put(
                    sampleItem(
                        id = "cold",
                        label = "MRAZ",
                        labelUk = "ХОЛОДНО",
                        labelEn = "COLD",
                        speechSl = "zebe me",
                        speechUk = "Мені холодно",
                        speechEn = "I am cold",
                        imagePath = "",
                        conceptId = "cold",
                        isRootItem = true,
                        priority = 13
                    )
                )
                .put(
                    sampleItem(
                        id = "hot",
                        label = "VROČE",
                        labelUk = "ЖАРКО",
                        labelEn = "HOT",
                        speechSl = "vroče mi je",
                        speechUk = "Мені жарко",
                        speechEn = "I am hot",
                        imagePath = "",
                        conceptId = "hot",
                        isRootItem = true,
                        priority = 14
                    )
                )
                .put(
                    sampleItem(
                        id = "good",
                        label = "DOBRO",
                        labelUk = "ДОБРЕ",
                        labelEn = "GOOD",
                        speechSl = "dobro sem",
                        speechUk = "Мені добре",
                        speechEn = "I am good",
                        imagePath = "",
                        conceptId = "good",
                        isRootItem = true,
                        priority = 15
                    )
                )
                .put(
                    sampleItem(
                        id = "bad",
                        label = "SLABO",
                        labelUk = "ПОГАНО",
                        labelEn = "BAD",
                        speechSl = "slabo mi je",
                        speechUk = "Мені погано",
                        speechEn = "I feel bad",
                        imagePath = "",
                        conceptId = "bad",
                        isRootItem = true,
                        priority = 16
                    )
                )
                .put(
                    sampleItem(
                        id = "doctor",
                        label = "ZDRAVNIK",
                        labelUk = "ЛІКАР",
                        labelEn = "DOCTOR",
                        speechSl = "pokličite zdravnika",
                        speechUk = "Покличте лікаря",
                        speechEn = "Call the doctor",
                        imagePath = "",
                        conceptId = "doctor",
                        isRootItem = true,
                        priority = 17
                    )
                )
                .put(
                    sampleItem(
                        id = "family",
                        label = "DRUŽINA",
                        labelUk = "СІМ'Я",
                        labelEn = "FAMILY",
                        speechSl = "pokličite družino",
                        speechUk = "Покличте сім'ю",
                        speechEn = "Call my family",
                        imagePath = "",
                        conceptId = "family",
                        isRootItem = true,
                        priority = 18
                    )
                )
                .put(
                    sampleItem(
                        id = "water",
                        label = "VODA",
                        labelUk = "ВОДА",
                        labelEn = "WATER",
                        speechSl = "želim piti vodo",
                        speechUk = "Я хочу пити воду",
                        speechEn = "I want to drink water",
                        imagePath = "custom/water.png",
                        conceptId = "water",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 10,
                        vendingNumber = "12"
                    )
                )
                .put(
                    sampleItem(
                        id = "juice",
                        label = "SOK",
                        labelUk = "СІК",
                        labelEn = "JUICE",
                        speechSl = "želim piti sok",
                        speechUk = "Я хочу пити сік",
                        speechEn = "I want to drink juice",
                        imagePath = "custom/juice.png",
                        conceptId = "juice",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 11,
                        vendingNumber = "14"
                    )
                )
                .put(
                    sampleItem(
                        id = "coffee",
                        label = "KAVA",
                        labelUk = "КАВА",
                        labelEn = "COFFEE",
                        speechSl = "želim piti kavo",
                        speechUk = "Я хочу пити каву",
                        speechEn = "I want to drink coffee",
                        imagePath = "custom/coffee.png",
                        conceptId = "coffee",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 12,
                        vendingNumber = "21"
                    )
                )
                .put(
                    sampleItem(
                        id = "tea",
                        label = "ČAJ",
                        labelUk = "ЧАЙ",
                        labelEn = "TEA",
                        speechSl = "želim piti čaj",
                        speechUk = "Я хочу пити чай",
                        speechEn = "I want to drink tea",
                        imagePath = "custom/tea.png",
                        conceptId = "tea",
                        parentId = "thirsty",
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 13,
                        vendingNumber = "22"
                    )
                )
                .put(
                    sampleItem(
                        id = "soup",
                        label = "JUHA",
                        labelUk = "СУП",
                        labelEn = "SOUP",
                        speechSl = "želim jesti juho",
                        speechUk = "Я хочу їсти суп",
                        speechEn = "I want to eat soup",
                        imagePath = "",
                        conceptId = "soup",
                        parentId = "food",
                        visibleUnderIds = listOf("food"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 14
                    )
                )
                .put(
                    sampleItem(
                        id = "bread",
                        label = "KRUH",
                        labelUk = "ХЛІБ",
                        labelEn = "BREAD",
                        speechSl = "želim jesti kruh",
                        speechUk = "Я хочу їсти хліб",
                        speechEn = "I want to eat bread",
                        imagePath = "",
                        conceptId = "bread",
                        parentId = "food",
                        visibleUnderIds = listOf("food"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 15
                    )
                )
                .put(
                    sampleItem(
                        id = "fruit",
                        label = "SADJE",
                        labelUk = "ФРУКТИ",
                        labelEn = "FRUIT",
                        speechSl = "želim jesti sadje",
                        speechUk = "Я хочу їсти фрукти",
                        speechEn = "I want to eat fruit",
                        imagePath = "",
                        conceptId = "fruit",
                        parentId = "food",
                        visibleUnderIds = listOf("food"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 16
                    )
                )
                .put(
                    sampleItem(
                        id = "head",
                        label = "GLAVA",
                        labelUk = "ГОЛОВА",
                        labelEn = "HEAD",
                        speechSl = "boli me glava",
                        speechUk = "У мене болить голова",
                        speechEn = "My head hurts",
                        imagePath = "",
                        conceptId = "head",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 17
                    )
                )
                .put(
                    sampleItem(
                        id = "arm",
                        label = "ROKA",
                        labelUk = "РУКА",
                        labelEn = "ARM",
                        speechSl = "boli me roka",
                        speechUk = "У мене болить рука",
                        speechEn = "My arm hurts",
                        imagePath = "",
                        conceptId = "arm",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 18
                    )
                )
                .put(
                    sampleItem(
                        id = "leg",
                        label = "NOGA",
                        labelUk = "НОГА",
                        labelEn = "LEG",
                        speechSl = "boli me noga",
                        speechUk = "У мене болить нога",
                        speechEn = "My leg hurts",
                        imagePath = "",
                        conceptId = "leg",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 19
                    )
                )
                .put(
                    sampleItem(
                        id = "belly",
                        label = "TREBUH",
                        labelUk = "ЖИВІТ",
                        labelEn = "BELLY",
                        speechSl = "boli me trebuh",
                        speechUk = "У мене болить живіт",
                        speechEn = "My stomach hurts",
                        imagePath = "",
                        conceptId = "belly",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 20
                    )
                )
                .put(
                    sampleItem(
                        id = "back",
                        label = "HRBET",
                        labelUk = "СПИНА",
                        labelEn = "BACK",
                        speechSl = "boli me hrbet",
                        speechUk = "У мене болить спина",
                        speechEn = "My back hurts",
                        imagePath = "",
                        conceptId = "back",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 21
                    )
                )
                .put(
                    sampleItem(
                        id = "chest",
                        label = "PRSI",
                        labelUk = "ГРУДИ",
                        labelEn = "CHEST",
                        speechSl = "boli me v prsih",
                        speechUk = "У мене болить у грудях",
                        speechEn = "My chest hurts",
                        imagePath = "",
                        conceptId = "chest",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 22
                    )
                )
                .put(
                    sampleItem(
                        id = "throat",
                        label = "GRLO",
                        labelUk = "ГОРЛО",
                        labelEn = "THROAT",
                        speechSl = "boli me grlo",
                        speechUk = "У мене болить горло",
                        speechEn = "My throat hurts",
                        imagePath = "",
                        conceptId = "throat",
                        parentId = "pain",
                        visibleUnderIds = listOf("pain"),
                        isRootItem = false,
                        isHiddenUntilParent = true,
                        priority = 23
                    )
                )
                .put(
                    sampleItem(
                        id = "soca_water",
                        label = "VODA",
                        labelUk = "WATER",
                        imagePath = "soca/voda.png",
                        conceptId = "water",
                        categoryId = "basic_needs",
                        isRootItem = true,
                        priority = 20,
                        visibleUnderIds = listOf("drinks", "want", "basic_needs"),
                        placements = listOf("drinks" to 1)
                    )
                )
                .put(
                    sampleItem(
                        id = "soca_wc",
                        label = "WC",
                        labelUk = "TOILET",
                        imagePath = "soca/wc.png",
                        conceptId = "wc",
                        categoryId = "basic_needs",
                        isRootItem = true,
                        priority = 21
                    )
                )
                .put(
                    sampleItem(
                        id = "soca_help",
                        label = "POMOČ",
                        labelUk = "HELP",
                        imagePath = "soca/pomoc.png",
                        conceptId = "help",
                        categoryId = "basic_needs",
                        isRootItem = true,
                        priority = 22,
                        visibleUnderIds = listOf("home", "emergency", "basic_needs"),
                        placements = listOf("home" to 3, "basic_needs" to 3)
                    )
                )
                .put(
                    sampleItem(
                        id = "soca_pain",
                        label = "BOLI",
                        labelUk = "PAIN",
                        imagePath = "soca/boli.png",
                        conceptId = "pain",
                        categoryId = "basic_needs",
                        isRootItem = true,
                        priority = 23
                    )
                )
        )
    }

    private fun buildDomProfileJson(): JSONObject {
        return buildProfileJson(
            id = "dom",
            displayName = "DOM",
            icon = "custom/dom.png",
            context = AacCommunicationContext.NORMAL_COMMUNICATION,
            itemIds = listOf("no", "yes", "dont_understand", "thank_you", "sorry", "help", "pain", "thirsty")
        )
    }

    private fun buildVideoCallProfileJson(): JSONObject {
        return buildProfileJson(
            id = "video_call",
            displayName = "VIDEO CALL",
            icon = "arasaac/video_call.png",
            context = AacCommunicationContext.VIDEO_CALL_COMMUNICATION,
            itemIds = listOf("no", "yes", "dont_understand", "thank_you", "sorry", "help", "pain")
        )
    }

    private fun buildRealWorldProfileJson(): JSONObject {
        return buildProfileJson(
            id = "real_world",
            displayName = "RESNIČNI SVET",
            icon = "soca/real_world.png",
            context = AacCommunicationContext.REAL_WORLD_ASSISTANT,
            itemIds = listOf("no", "yes", "dont_understand", "thank_you", "sorry", "soca_water", "soca_wc", "soca_help", "soca_pain", "thirsty")
        )
    }

    private fun buildProfileJson(
        id: String,
        displayName: String,
        icon: String,
        context: AacCommunicationContext,
        itemIds: List<String>
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("icon", icon)
            .put("context", context.name)
            .put("itemIds", JSONArray().apply {
                itemIds.forEach { put(it) }
            })
            .put("enabled", true)
    }

    private fun sampleItem(
        id: String,
        label: String,
        labelUk: String? = null,
        labelEn: String? = null,
        speechSl: String? = null,
        speechUk: String? = null,
        speechEn: String? = null,
        imagePath: String,
        conceptId: String? = null,
        categoryId: String? = null,
        parentId: String? = null,
        visibleUnderIds: List<String> = emptyList(),
        placements: List<Pair<String, Int>> = emptyList(),
        isRootItem: Boolean,
        isHiddenUntilParent: Boolean = false,
        priority: Int,
        fixedTopRowPosition: Int? = null,
        followUpQuestion: String? = null,
        vendingNumber: String? = null
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("labelSl", label)
            .put("labelUk", labelUk)
            .put("labelEn", labelEn)
            .put("text", label)
            .put("speechText", speechSl ?: label.lowercase())
            .put("speakTextSl", speechSl ?: label.lowercase())
            .put("speakTextUk", speechUk ?: labelUk?.lowercase())
            .put("speechTextEn", speechEn ?: labelEn?.lowercase())
            .put("baseText", label)
            .put("categoryId", categoryId)
            .put("imagePath", imagePath)
            .put("iconSource", inferIconSourceName(imagePath))
            .put("actionType", "speak")
            .put("targetPageId", "")
            .put("conceptId", conceptId)
            .put("parentId", parentId)
            .put("isRootItem", isRootItem)
            .put("isHiddenUntilParent", isHiddenUntilParent)
            .put("priority", priority)
            .apply {
                if (visibleUnderIds.isNotEmpty()) {
                    put("visibleUnderIds", JSONArray().apply {
                        visibleUnderIds.forEach { put(it) }
                    })
                }
                if (placements.isNotEmpty()) {
                    put("placements", JSONArray().apply {
                        placements.forEach { (pageId, position5x5) ->
                            if (pageId.isNotBlank() && position5x5 in 1..25) {
                                put(JSONObject().put("pageId", pageId).put("position5x5", position5x5))
                            }
                        }
                    })
                }
                if (fixedTopRowPosition != null) {
                    put("fixedTopRowPosition", fixedTopRowPosition)
                }
                if (!followUpQuestion.isNullOrBlank()) {
                    put("followUpQuestion", followUpQuestion)
                }
                if (!vendingNumber.isNullOrBlank()) {
                    put("vendingNumber", vendingNumber)
                }
            }
    }

    private fun inferIconSourceName(imagePath: String): String {
        val normalized = imagePath.trim().replace('\\', '/').lowercase()
        return when {
            normalized.isBlank() -> "SYSTEM"
            normalized.startsWith("soca/") || normalized.contains("/soca/") -> "SOCA"
            normalized.startsWith("arasaac/") || normalized.contains("/arasaac/") -> "ARASAAC"
            else -> "CUSTOM"
        }
    }
}

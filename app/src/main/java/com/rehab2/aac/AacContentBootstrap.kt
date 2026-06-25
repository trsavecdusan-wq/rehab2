package com.rehab2.aac

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacContentBootstrap {
    private const val TAG = "AacContentBootstrap"
    private const val PATIENT_PAGE_PREFS_NAME = "aac_patient_pages"
    private const val KEY_PATIENT_PAGES = "patient_pages"
    private const val KEY_DEFAULT_PATIENT_PAGE_ID = "default_patient_page_id"
    private const val PATIENT_PAGE_SEPARATOR = "\u001E"
    private const val PATIENT_PAGE_FIELD_SEPARATOR = "\u001F"
    private const val CORE_V2_REPAIR_PREFS_NAME = "aac_core_v2_home_repair"
    private const val KEY_CORE_V2_HOME_REPAIR_DONE = "aac_core_v2_home_repair_done"
    private const val KEY_AAC_HOME_LAYOUT_VERSION = "aac_home_layout_version"
    private const val CORE_V2_HOME_LAYOUT_VERSION = "core_v2"
    private const val DEFAULT_PAGE_ID = "page_1"
    private const val DEFAULT_PAGE_TITLE = "STRAN 1"
    private const val DOM_PROFILE_ID = "dom"
    private const val DOM_PROFILE_FILE = "dom.json"
    private const val DEBUG_PREFS_NAME = "aac_dom_profile_debug"
    private const val KEY_DEBUG_PROFILE_FILE_PATH = "profile_file_path"
    private const val KEY_DEBUG_PROFILE_FILE_EXISTS = "profile_file_exists"
    private const val KEY_DEBUG_PROFILE_TYPE = "profile_type"
    private const val KEY_DEBUG_DOM_PROFILE_FOUND = "dom_profile_found"
    private const val KEY_DEBUG_DOM_PROFILE_ID = "dom_profile_id"
    private const val KEY_DEBUG_ITEM_IDS_BEFORE = "item_ids_before"
    private const val KEY_DEBUG_ITEM_IDS_AFTER = "item_ids_after"
    private const val SYSTEM_ICON_ASSET_DIR = "NovaRehab/icons/system"
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    )

    private val TOALETA_V1_WC_CHILDREN = listOf(
        "wc_wet",
        "wc_dirty",
        "wc_wet_and_dirty",
        "nurse_help"
    )
    private val TOALETA_V1_NURSE_CHILDREN = listOf(
        "help_dressing",
        "help_washing",
        "help_showering",
        "noticed_blood"
    )
    private val TOALETA_V1_EXCLUDED_WC_CHILDREN = setOf(
        "wc_diaper_change",
        "wc_burning",
        "wc_pain",
        "wc_itching",
        "wc_blood",
        "wc_please",
        "wc_now",
        "wc_soon",
        "wc_help",
        "wc_very_urgent",
        "wc_call_nurse"
    )

    private val AAC_SPEECH_QUALITY_REPAIR_IDS = setOf(
        "pain",
        "wc",
        "nurse_help",
        "help_dressing",
        "help_washing",
        "help_showering",
        "noticed_blood",
        "water",
        "cold_water",
        "non_sparkling_water",
        "flavored_water",
        "mineral_water",
        "radenska",
        "wc_now",
        "wc_soon",
        "wc_help",
        "juice",
        "orange_juice",
        "apple_juice",
        "blueberry_juice",
        "strawberry_juice",
        "cedevita",
        "left_arm",
        "right_arm",
        "left_leg",
        "right_leg",
        "arm_palm"
    )

    private val QUICK_PATIENT_SYSTEM_ICON_IDS = setOf(
        "help_washing",
        "help_showering",
        "help_dressing",
        "noticed_blood",
        "nurse_help",
        "wc_help",
        "wc_now",
        "wc_soon",
        "wc_wet",
        "wc_dirty",
        "wc_wet_and_dirty",
        "water",
        "tea",
        "coffee",
        "juice",
        "sparkling_drink",
        "milk_drinks",
        "drink_fanta",
        "drink_coca_cola",
        "drink_pepsi",
        "drink_radenska",
        "non_sparkling_water",
        "mineral_water",
        "cold_water",
        "back",
        "belly",
        "chest",
        "head",
        "left_arm",
        "right_arm",
        "left_leg",
        "right_leg",
        "arm_shoulder",
        "arm_elbow",
        "arm_wrist",
        "arm_palm",
        "leg_hip",
        "leg_knee",
        "leg_ankle",
        "leg_foot",
        "pain_light",
        "pain_medium",
        "pain_strong",
        "pain_very_strong"
    )

    private val PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID = mapOf(
        "wc" to "system/toilet_general.png",
        "nurse_help" to "system/toilet_nurse.png",
        "help_washing" to "system/toilet_wash.png",
        "help_showering" to "system/toilet_shower.png",
        "help_dressing" to "system/toilet_dress.png",
        "noticed_blood" to "system/toilet_blood.png",
        "wc_help" to "system/toilet_help.png",
        "wc_now" to "system/toilet_now.png",
        "wc_soon" to "system/toilet_soon.png",
        "wc_wet" to "system/toilet_wet.png",
        "wc_dirty" to "system/toilet_dirty.png",
        "wc_wet_and_dirty" to "system/toilet_both.png",
        "thirsty" to "system/thirsty.png",
        "pain" to "system/pain_general.png",
        "back" to "system/pain_back.png",
        "chest" to "system/pain_chest.png",
        "belly" to "system/pain_belly.png",
        "head" to "system/pain_head.png",
        "left_arm" to "system/pain_left_arm.png",
        "right_arm" to "system/pain_right_arm.png",
        "left_leg" to "system/pain_left_leg.png",
        "right_leg" to "system/pain_right_leg.png",
        "back_upper" to "system/pain_back_upper.png",
        "back_middle" to "system/pain_back_middle.png",
        "back_lower" to "system/pain_back_lower.png",
        "belly_left" to "system/body_belly.png",
        "belly_right" to "system/body_belly.png",
        "belly_upper" to "system/body_belly.png",
        "belly_lower" to "system/body_belly.png",
        "arm_shoulder" to "system/pain_shoulder.png",
        "arm_upper" to "system/pain_upper_arm.png",
        "arm_elbow" to "system/pain_elbow.png",
        "arm_forearm" to "system/pain_forearm.png",
        "arm_wrist" to "system/pain_wrist.png",
        "arm_palm" to "system/pain_hand.png",
        "arm_fingers" to "system/pain_hand_fingers.png",
        "leg_hip" to "system/pain_hip.png",
        "leg_thigh" to "system/pain_thigh.png",
        "leg_knee" to "system/pain_knee.png",
        "leg_shin" to "system/pain_shin.png",
        "leg_ankle" to "system/pain_ankle.png",
        "leg_foot" to "system/pain_foot.png",
        "leg_toes" to "system/pain_toes.png",
        "pain_light" to "system/pain_mild.png",
        "pain_medium" to "system/pain_medium.png",
        "pain_strong" to "system/pain_strong.png",
        "pain_very_strong" to "system/pain_very_strong.png",
        "water" to "system/drink_water.png",
        "cold_water" to "system/drink_water_cold.png",
        "non_sparkling_water" to "system/drink_water_still.png",
        "mineral_water" to "system/drink_water_mineral.png",
        "flavored_water" to "system/drink_flavored_water.png",
        "radenska" to "system/drink_radenska.png",
        "tea" to "system/drink_tea.png",
        "coffee" to "system/drink_coffee.png",
        "juice" to "system/drink_juice.png",
        "sparkling_drink" to "system/drink_sparkling_category.png",
        "milk_drinks" to "system/drink_milk.png",
        "drink_fanta" to "system/drink_fanta.png",
        "drink_coca_cola" to "system/drink_coca_cola.png",
        "drink_pepsi" to "system/drink_pepsi.png",
        "drink_radenska" to "system/drink_radenska.png",
        "orange_juice" to "system/drink_juice_orange.png",
        "apple_juice" to "system/drink_juice_apple.png",
        "blueberry_juice" to "system/drink_juice_blueberry.png",
        "strawberry_juice" to "system/drink_juice_strawberry.png",
        "cedevita" to "system/drink_cedevita.png",
        "coffee_plain" to "system/coffee_black.png",
        "coffee_milk" to "system/coffee_milk.png",
        "coffee_no_sugar" to "system/coffee_no_sugar.png",
        "tea_chamomile" to "system/tea_chamomile.png",
        "tea_chamomile_lemon" to "system/tea_add_lemon.png",
        "tea_chamomile_honey" to "system/tea_add_honey.png",
        "tea_chamomile_honey_lemon" to "system/tea_add_lemon_honey.png",
        "tea_fruit" to "system/tea_fruit.png",
        "tea_fruit_lemon" to "system/tea_add_lemon.png",
        "tea_fruit_honey" to "system/tea_add_honey.png",
        "tea_fruit_honey_lemon" to "system/tea_add_lemon_honey.png",
        "tea_green" to "system/tea_green.png",
        "tea_green_lemon" to "system/tea_add_lemon.png",
        "tea_green_honey" to "system/tea_add_honey.png",
        "tea_green_honey_lemon" to "system/tea_add_lemon_honey.png",
        "tea_black" to "system/tea_black.png",
        "tea_black_lemon" to "system/tea_add_lemon.png",
        "tea_black_honey" to "system/tea_add_honey.png",
        "tea_black_honey_lemon" to "system/tea_add_lemon_honey.png",
        "tea_mint" to "system/tea_mint.png",
        "tea_mint_lemon" to "system/tea_add_lemon.png",
        "tea_mint_honey" to "system/tea_add_honey.png",
        "tea_mint_honey_lemon" to "system/tea_add_lemon_honey.png",
        "tea_rosehip_lemon" to "system/tea_add_lemon.png",
        "tea_rosehip_honey" to "system/tea_add_honey.png",
        "tea_rosehip_honey_lemon" to "system/tea_add_lemon_honey.png",
        "soup" to "system/food_soup.png",
        "bread" to "system/food_bread.png",
        "fruit" to "system/food_fruit.png",
        "meat" to "system/food_meat.png",
        "potato" to "system/food_potato.png",
        "rice" to "system/food_rice.png",
        "ice_cream" to "system/snack_ice_cream.png",
        "food_yogurt" to "system/food_yogurt.png",
        "food_banana" to "system/food_banana.png",
        "food_apple" to "system/food_apple.png",
        "food_lunch" to "system/food_main_dish.png",
        "food_dinner" to "system/food_main_dish.png",
        "hungry_main_dish" to "system/food_main_dish.png",
        "hungry_snack" to "system/food_snack.png",
        "hungry_fast_food" to "system/food_fast_food.png",
        "hungry_soup" to "system/food_soup.png",
        "hungry_beef_soup" to "system/food_beef_soup.png",
        "hungry_chicken_soup" to "system/food_chicken_soup.png",
        "hungry_meat" to "system/food_meat.png",
        "hungry_side_dishes" to "system/food_sides.png",
        "hungry_fruit" to "system/food_fruit.png",
        "hungry_dessert" to "system/food_dessert.png",
        "hungry_potato" to "system/food_potato.png",
        "hungry_chicken" to "system/food_chicken.png",
        "hungry_fish" to "system/food_fish.png",
        "hungry_pasta" to "system/food_pasta.png",
        "hungry_rice" to "system/food_rice.png",
        "hungry_vegetables" to "system/food_vegetables.png",
        "hungry_yogurt" to "system/food_yogurt.png",
        "hungry_apple" to "system/food_apple.png",
        "hungry_pear" to "system/food_pear.png",
        "hungry_banana" to "system/food_banana.png",
        "hungry_grapes" to "system/food_grapes.png",
        "hungry_blueberries" to "system/food_blueberries.png",
        "hungry_strawberries" to "system/food_strawberry.png",
        "hungry_kiwi" to "system/food_kiwi.png",
        "hungry_mashed_potato" to "system/food_mashed_potato.png",
        "hungry_ice_cream" to "system/food_icecream.png",
        "hungry_cake" to "system/food_cake.png",
        "hungry_cookies" to "system/snack_cookies.png",
        "hungry_hamburger" to "system/food_hamburger.png",
        "hungry_pizza" to "system/food_pizza.png",
        "hungry_doughnut" to "system/food_donut.png",
        "hungry_pancakes" to "system/food_pancakes.png",
        "feeling" to "system/feeling.png",
        "care" to "system/care.png",
        "health" to "system/health.png",
        "cannot" to "system/cannot.png",
        "cold_hot" to "system/cold_hot.png",
        "dressing" to "system/care_dress.png",
        "dressing_help" to "system/care_change_clothes.png",
        "washing_help" to "system/care_wash.png",
        "diaper" to "system/care_diaper.png",
        "medicine" to "system/care_medicine.png",
        "doctor" to "system/person_doctor.png",
        "nurse" to "system/person_nurse.png",
        "therapy" to "system/place_therapy.png",
        "wheelchair" to "system/transport_wheelchair.png",
        "bed" to "system/place_bed.png",
        "room" to "system/place_room.png",
        "balcony" to "system/place_balcony.png",
        "courtyard" to "system/place_yard.png",
        "bathroom" to "system/place_bathroom.png",
        "shop" to "system/place_shop.png",
        "vending_drinks" to "system/place_vending_machine.png",
        "vending_coffee_tea" to "system/place_vending_machine.png"
    )

    private val PROFESSIONAL_SYSTEM_ICON_FILES = PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID.values
        .map { path -> path.substringAfterLast('/') }
        .distinct()

    private val BUNDLED_SYSTEM_ICON_FILES = listOf(
        "aac_drink_coffee.png",
        "aac_drink_cola.png",
        "aac_drink_cold_water.png",
        "aac_drink_fanta.png",
        "aac_drink_juice.png",
        "aac_drink_milk.png",
        "aac_drink_pepsi.png",
        "aac_drink_radenska.png",
        "aac_drink_soda.png",
        "aac_drink_sparkling_water.png",
        "aac_drink_still_water.png",
        "aac_drink_tea.png",
        "aac_drink_water.png",
        "aac_help.png",
        "aac_now.png",
        "aac_nurse.png",
        "aac_pain_ankle.png",
        "aac_pain_back.png",
        "aac_pain_belly.png",
        "aac_pain_chest.png",
        "aac_pain_elbow.png",
        "aac_pain_foot.png",
        "aac_pain_head.png",
        "aac_pain_hip.png",
        "aac_pain_knee.png",
        "aac_pain_left_arm.png",
        "aac_pain_left_leg.png",
        "aac_pain_light.png",
        "aac_pain_medium.png",
        "aac_pain_palm.png",
        "aac_pain_right_arm.png",
        "aac_pain_right_leg.png",
        "aac_pain_shoulder.png",
        "aac_pain_strong.png",
        "aac_pain_very_strong.png",
        "aac_pain_wrist.png",
        "aac_soon.png",
        "aac_toilet_blood.png",
        "aac_toilet_dressing.png",
        "aac_toilet_shower.png",
        "aac_toilet_washing.png",
        "aac_wc_both.png",
        "aac_wc_dirty.png",
        "aac_wc_wet.png",
        "come_to_me.png",
        "dont_understand.png",
        "help.png",
        "home.png",
        "hungry.png",
        "miss_someone.png",
        "need.png",
        "no.png",
        "other.png",
        "pain.png",
        "people.png",
        "please.png",
        "problem.png",
        "real_world.png",
        "repeat.png",
        "rest.png",
        "slower.png",
        "sorry.png",
        "thank_you.png",
        "thirsty.png",
        "tired.png",
        "wait.png",
        "wc.png",
        "what.png",
        "when.png",
        "where.png",
        "yes.png"
    )
        .plus(PROFESSIONAL_SYSTEM_ICON_FILES)
        .distinct()

    private val ROOT_SYSTEM_ICON_REPAIRS = mapOf(
        "people" to "system/people.png",
        "need" to "system/need.png",
        "problem" to "system/problem.png",
        "what_root" to "system/what.png",
        "where_root" to "system/where.png",
        "when_root" to "system/when.png",
        "home" to "system/home.png",
        "other" to "system/other.png",
        "real_world" to "system/real_world.png"
    )

    private val FIXED_ROW_SYSTEM_ICON_REPAIRS = mapOf(
        "no" to "system/no.png",
        "yes" to "system/yes.png"
    )

    private val PERSON_PHOTO_REPAIRS = mapOf(
        "person_dusan" to "person_dusan.jpg",
        "person_zana" to "person_zana.jpg",
        "person_franc" to "person_franc.jpg",
        "person_inna" to "person_inna.jpg",
        "person_julija" to "person_julija.jpg",
        "person_oksana" to "person_oksana.jpg",
        "person_sergej" to "person_sergej.jpg"
    )

    private val PEOPLE_GROUP_CHILD_REPAIRS = mapOf(
        "family_group" to listOf("person_zana", "person_sergej", "miss_you", "love_you", "contact_call"),
        "friends_group" to listOf(
            "person_dusan",
            "person_franc",
            "person_inna",
            "person_julija",
            "person_oksana",
            "contact_message",
            "contact_call",
            "miss_you",
            "when_come",
            "come_to_me"
        )
    )

    private val CRITICAL_SYSTEM_ICON_FILES = setOf(
        "no.png",
        "yes.png"
    )

    private val OPTIONAL_ROOT_SYSTEM_ICON_REPAIRS = mapOf(
        "feeling" to "system/feeling.png",
        "care" to "system/care.png",
        "health" to "system/health.png",
        "cannot" to "system/cannot.png",
        "cold_hot" to "system/cold_hot.png"
    )

    data class Result(
        val itemCount: Int,
        val existingPageCount: Int,
        val createdDefaultPage: Boolean,
        val defaultPageId: String,
        val addedPlacements: Int,
        val domProfileLinkedItemCount: Int,
        val domProfileUpdated: Boolean,
        val fixedRowCount: Int,
        val visibleNormalItemCount: Int,
        val skipped: Boolean,
        val reason: String
    )

    data class DomProfileDebug(
        val profileFilePath: String,
        val profileFileExists: Boolean,
        val profileType: String,
        val domProfileFound: Boolean,
        val domProfileId: String,
        val itemIdsBefore: Int,
        val itemIdsAfter: Int
    )

    fun ensurePatientStartupContent(context: Context, fallbackItems: List<AacItem>): Result {
        AacStoragePaths.ensureAacContentDirs(context)
        val seededSystemIcons = seedBundledSystemIcons(context)
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val rawItems = loadItemsJson(itemsFile, fallbackItems)
        val itemsArray = rawItems.itemsArray
        val starterItems = AacStarterContentV1.items()
        val mergedMissingSystemItems = mergeMissingSystemItems(itemsArray, fallbackItems + starterItems)
        val repairedStarterCategoryChildren = repairStarterCategoryChildren(itemsArray, starterItems)
        val repairedToaletaV1Tree = repairToaletaV1Tree(itemsArray)
        val repairedHungryV1TestTree = repairHungryV1TestTree(itemsArray, starterItems)
        val repairedPeopleGroupChildren = repairPeopleGroupChildren(itemsArray)
        val repairedConversationTreeV3Metadata = repairConversationTreeV3Metadata(itemsArray, starterItems)
        val repairedRootSystemIcons = repairRootSystemIconMetadata(context, itemsArray)
        val repairedFixedRowSystemIcons = repairFixedRowSystemIconMetadata(context, itemsArray)
        val repairedPersonPhotoMetadata = repairPersonPhotoMetadata(context, itemsArray)
        val repairedStarterUkrainianContent = repairStarterUkrainianContent(itemsArray, starterItems)
        val repairedStarterSpeechQualityContent = repairStarterSpeechQualityContent(itemsArray, starterItems)
        val repairedStarterSystemIcons = repairStarterSystemIconContent(context, itemsArray, starterItems)
        logProfessionalSystemIconAudit(context, itemsArray)
        val itemCount = itemsArray.length()
        if (itemCount == 0) {
            return Result(
                itemCount = 0,
                existingPageCount = currentPatientPages(context).size,
                createdDefaultPage = false,
                defaultPageId = "",
                addedPlacements = 0,
                domProfileLinkedItemCount = 0,
                domProfileUpdated = false,
                fixedRowCount = 0,
                visibleNormalItemCount = 0,
                skipped = true,
                reason = "no_aac_items"
            )
        }

        val existingPages = currentPatientPages(context)
        val createdDefaultPage = existingPages.isEmpty()
        if (createdDefaultPage) {
            savePatientPages(context, listOf(DEFAULT_PAGE_ID to DEFAULT_PAGE_TITLE))
            setDefaultPatientPage(context, DEFAULT_PAGE_ID)
        } else if (currentDefaultPatientPage(context).isBlank()) {
            setDefaultPatientPage(context, existingPages.first().first)
        }
        val defaultPageId = currentDefaultPatientPage(context).ifBlank { DEFAULT_PAGE_ID }
        val defaultPagePlacements = if (createdDefaultPage) {
            addDefaultPagePlacements(itemsArray, defaultPageId)
        } else {
            0
        }
        val starterPlacements = if (!createdDefaultPage) {
            addStarterItemsToEmptyDefaultPageSlots(itemsArray, defaultPageId)
        } else {
            0
        }
        val addedPlacements = defaultPagePlacements + starterPlacements
        val repairedFixedTopRowMetadata = repairFixedTopRowMetadata(itemsArray)
        val repairedDefaultPageV3Placements = repairDefaultPageV3Placements(context, itemsArray, defaultPageId)
        val repairedNoUnderstandLabels = repairNoUnderstandSystemLabels(itemsArray)
        val repairedDrinkSpeechItems = repairDrinkChildSpeechItems(itemsArray)
        val repairedFoodSpeechItems = repairFoodChildSpeechItems(itemsArray)
        val repairedPainSpeechItems = repairPainSpeechItems(itemsArray)
        if (
            addedPlacements > 0 ||
            mergedMissingSystemItems > 0 ||
            repairedStarterCategoryChildren > 0 ||
            repairedToaletaV1Tree > 0 ||
            repairedHungryV1TestTree > 0 ||
            repairedPeopleGroupChildren > 0 ||
            repairedConversationTreeV3Metadata > 0 ||
            repairedRootSystemIcons > 0 ||
            repairedFixedRowSystemIcons > 0 ||
            repairedPersonPhotoMetadata > 0 ||
            repairedFixedTopRowMetadata > 0 ||
            repairedDefaultPageV3Placements > 0 ||
            repairedNoUnderstandLabels > 0 ||
            repairedDrinkSpeechItems > 0 ||
            repairedFoodSpeechItems > 0 ||
            repairedPainSpeechItems > 0 ||
            repairedStarterUkrainianContent > 0 ||
            repairedStarterSpeechQualityContent > 0 ||
            repairedStarterSystemIcons > 0
        ) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        } else if (itemsFile?.exists() != true && rawItems.createdFromFallback) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        }

        val pageItemIds = itemIdsOnPage(itemsArray, defaultPageId)
        val domProfileResult = ensureDomProfileLinked(context, pageItemIds.ifEmpty { rootItemIds(itemsArray) })
        val syncedDomRelations = syncDomProfileRelations(context, itemsArray)
        if (syncedDomRelations > 0) {
            saveItemsJson(itemsFile, rawItems, itemsArray)
        }
        val fixedRowCount = fixedRowItemIds(itemsArray).size
        val visibleNormalItemCount = pageItemIds.filter { it !in fixedRowItemIds(itemsArray) }.size

        val result = Result(
            itemCount = itemCount,
            existingPageCount = existingPages.size,
            createdDefaultPage = createdDefaultPage,
            defaultPageId = defaultPageId,
            addedPlacements = addedPlacements,
            domProfileLinkedItemCount = domProfileResult.linkedItemCount,
            domProfileUpdated = domProfileResult.updated,
            fixedRowCount = fixedRowCount,
            visibleNormalItemCount = visibleNormalItemCount,
            skipped = false,
            reason = if (createdDefaultPage) "bootstrap_created" else "pages_already_exist"
        )
        Log.d(
            TAG,
            "AAC_BOOTSTRAP defaultPage=${result.defaultPageId} items=${result.itemCount} normalVisible=${result.visibleNormalItemCount} fixed=${result.fixedRowCount} placementsAdded=${result.addedPlacements} starterPlacementsAdded=$starterPlacements domLinked=${result.domProfileLinkedItemCount} domRelationsSynced=$syncedDomRelations seededSystemIcons=$seededSystemIcons mergedMissingSystemItems=$mergedMissingSystemItems starterCategoryChildrenRepaired=$repairedStarterCategoryChildren toaletaV1TreeRepaired=$repairedToaletaV1Tree peopleGroupChildrenRepaired=$repairedPeopleGroupChildren conversationTreeV3MetadataRepaired=$repairedConversationTreeV3Metadata rootSystemIconsRepaired=$repairedRootSystemIcons fixedRowSystemIconsRepaired=$repairedFixedRowSystemIcons personPhotoRepaired=$repairedPersonPhotoMetadata starterUkrainianContentRepaired=$repairedStarterUkrainianContent starterSpeechQualityContentRepaired=$repairedStarterSpeechQualityContent starterSystemIconsRepaired=$repairedStarterSystemIcons fixedTopRowRepaired=$repairedFixedTopRowMetadata defaultPageV3Repaired=$repairedDefaultPageV3Placements noUnderstandRepaired=$repairedNoUnderstandLabels drinkSpeechRepaired=$repairedDrinkSpeechItems foodSpeechRepaired=$repairedFoodSpeechItems painSpeechRepaired=$repairedPainSpeechItems reason=${result.reason}"
        )
        return result
    }

    private fun seedBundledSystemIcons(context: Context): Int {
        val systemDir = AacStoragePaths.getIconsSystemDir(context) ?: return 0
        if (!systemDir.exists() && !systemDir.mkdirs()) return 0

        var seeded = 0
        val availableAssets = availableBundledSystemIconFiles(context)
        BUNDLED_SYSTEM_ICON_FILES.forEach { fileName ->
            if (fileName !in availableAssets) return@forEach
            val targetFile = File(systemDir, fileName)
            val assetPath = "$SYSTEM_ICON_ASSET_DIR/$fileName"
            try {
                val assetBytes = context.assets.open(assetPath).use { input -> input.readBytes() }
                if (isRuntimeSystemIconUsable(targetFile, fileName, assetBytes)) {
                    seeded++
                    return@forEach
                }

                targetFile.outputStream().use { output ->
                    output.write(assetBytes)
                }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    seeded++
                }
            } catch (error: Exception) {
                Log.w(TAG, "AAC_BOOTSTRAP_SYSTEM_ICON_SEED_FAILED asset=$assetPath", error)
            }
        }
        return seeded
    }

    private fun isRuntimeSystemIconUsable(file: File, fileName: String, assetBytes: ByteArray): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        if (!hasPngHeader(file)) return false
        if (fileName in CRITICAL_SYSTEM_ICON_FILES) {
            return try {
                file.readBytes().contentEquals(assetBytes)
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    private fun hasPngHeader(file: File): Boolean {
        return try {
            if (file.length() < PNG_SIGNATURE.size) return false
            file.inputStream().use { input ->
                val header = ByteArray(PNG_SIGNATURE.size)
                val read = input.read(header)
                read == PNG_SIGNATURE.size && header.contentEquals(PNG_SIGNATURE)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun repairRootSystemIconMetadata(context: Context, itemsArray: JSONArray): Int {
        val repairs = ROOT_SYSTEM_ICON_REPAIRS + OPTIONAL_ROOT_SYSTEM_ICON_REPAIRS.filterValues { imagePath ->
            AacStoragePaths.resolveIconFile(context, imagePath, IconSource.SYSTEM)?.isFile == true
        }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        repairs.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            if (AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.SYSTEM)?.isFile != true) {
                return@forEach
            }

            val currentImagePath = item.optString("imagePath").trim()
            val currentIconSource = item.optString("iconSource").trim().uppercase()
            val hasProtectedExternalImage = currentImagePath.isNotBlank() &&
                currentIconSource in setOf(
                    IconSource.CUSTOM.name,
                    IconSource.PATIENT.name,
                    IconSource.SOCA.name,
                    IconSource.ARASAAC.name
                )
            if (hasProtectedExternalImage) return@forEach

            var itemRepaired = 0
            if (currentImagePath.isBlank()) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (currentIconSource != IconSource.SYSTEM.name) {
                item.put("iconSource", IconSource.SYSTEM.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun repairFixedRowSystemIconMetadata(context: Context, itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        FIXED_ROW_SYSTEM_ICON_REPAIRS.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val resolvedFile = AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.SYSTEM)
            if (resolvedFile?.isFile != true || !hasPngHeader(resolvedFile)) return@forEach

            var itemRepaired = 0
            if (item.optString("imagePath").trim() != desiredImagePath) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (item.optString("iconSource").trim().uppercase() != IconSource.SYSTEM.name) {
                item.put("iconSource", IconSource.SYSTEM.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun repairPersonPhotoMetadata(context: Context, itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        PERSON_PHOTO_REPAIRS.forEach { (id, desiredImagePath) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val resolvedFile = AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.PATIENT)
            if (resolvedFile?.isFile != true || resolvedFile.length() <= 0L) return@forEach

            var itemRepaired = 0
            if (item.optString("imagePath").trim() != desiredImagePath) {
                item.put("imagePath", desiredImagePath)
                itemRepaired++
            }
            if (item.optString("iconSource").trim().uppercase() != IconSource.PATIENT.name) {
                item.put("iconSource", IconSource.PATIENT.name)
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun mergeMissingSystemItems(itemsArray: JSONArray, fallbackItems: List<AacItem>): Int {
        val existingIds = itemObjects(itemsArray)
            .map { item -> item.optString("id").trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        var merged = 0
        fallbackItems.forEach { item ->
            val id = item.id.trim()
            if (id.isBlank() || id in existingIds) return@forEach
            itemsArray.put(item.toBootstrapJson())
            existingIds += id
            merged++
        }
        return merged
    }

    private fun repairStarterUkrainianContent(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val starterById = starterItems.associateBy { item -> item.id }
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            val starter = starterById[item.optString("id").trim()] ?: return@forEach
            if (isProtectedStarterContentItem(item)) return@forEach
            repaired += putLanguageValueIfBlank(item, "labelByLanguage", "uk", starter.labelByLanguage["uk"].orEmpty())
            repaired += putLanguageValueIfBlank(
                item,
                "speechTextByLanguage",
                "uk",
                starter.speechTextByLanguage["uk"].orEmpty()
            )
            repaired += putLanguageValueIfBlank(
                item,
                "questionByLanguage",
                "uk",
                starter.questionByLanguage["uk"].orEmpty()
            )
        }
        return repaired
    }

    private fun repairStarterSystemIconContent(
        context: Context,
        itemsArray: JSONArray,
        starterItems: List<AacItem>
    ): Int {
        val starterById = starterItems.associateBy { item -> item.id }
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            val itemId = item.optString("id").trim()
            val starter = starterById[itemId] ?: return@forEach
            val isQuickPatientSystemIcon = itemId in QUICK_PATIENT_SYSTEM_ICON_IDS
            val isProfessionalSystemIcon = itemId in PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID
            if (isProtectedStarterSystemRepairItem(item)) return@forEach
            if (!isQuickPatientSystemIcon && !isProfessionalSystemIcon && hasUsableLocalIcon(context, item)) return@forEach

            val desiredImagePath = preferredStarterSystemIconPath(context, itemId, starter.imagePath.trim())
            if (desiredImagePath.isBlank()) return@forEach
            if (isQuickPatientSystemIcon &&
                !desiredImagePath.startsWith("system/aac_", ignoreCase = true) &&
                desiredImagePath != PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID[itemId]
            ) {
                return@forEach
            }
            if (AacStoragePaths.resolveIconFile(context, desiredImagePath, IconSource.SYSTEM)?.isFile != true) {
                return@forEach
            }

            repaired += putIfDifferent(item, "imagePath", desiredImagePath)
            repaired += putIfDifferent(item, "iconSource", IconSource.SYSTEM.name)
        }
        return repaired
    }

    private fun preferredStarterSystemIconPath(context: Context, itemId: String, fallbackImagePath: String): String {
        val professionalPath = PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID[itemId].orEmpty()
        if (professionalPath.isNotBlank() &&
            AacStoragePaths.resolveIconFile(context, professionalPath, IconSource.SYSTEM)?.isFile == true
        ) {
            return professionalPath
        }
        return fallbackImagePath
    }

    private fun logProfessionalSystemIconAudit(context: Context, itemsArray: JSONArray) {
        val availableAssets = availableBundledSystemIconFiles(context)
        val existingProfessionalFiles = PROFESSIONAL_SYSTEM_ICON_FILES.filter { fileName -> fileName in availableAssets }
        val missingProfessionalFiles = PROFESSIONAL_SYSTEM_ICON_FILES.filterNot { fileName -> fileName in availableAssets }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        val fallbackIds = PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID
            .filter { (_, professionalPath) ->
                AacStoragePaths.resolveIconFile(context, professionalPath, IconSource.SYSTEM)?.isFile != true
            }
            .keys
        val professionalLocalJsonIds = PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID
            .filter { (id, professionalPath) ->
                itemsById[id]?.optString("imagePath")?.trim() == professionalPath &&
                    itemsById[id]?.optString("iconSource")?.trim()?.uppercase() == IconSource.SYSTEM.name
            }
            .keys
        Log.d(
            TAG,
            "AAC_PRO_ICON_AUDIT mappings=${PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID.size} existingAssets=${existingProfessionalFiles.size} missingAssets=${missingProfessionalFiles.size} missingFiles=${missingProfessionalFiles.joinToString(",")} fallbackIds=${fallbackIds.joinToString(",")} localJsonProfessional=${professionalLocalJsonIds.joinToString(",")}"
        )
    }

    private fun availableBundledSystemIconFiles(context: Context): Set<String> {
        return try {
            context.assets.list(SYSTEM_ICON_ASSET_DIR)?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun repairStarterSpeechQualityContent(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val starterById = starterItems.associateBy { item -> item.id }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0
        AAC_SPEECH_QUALITY_REPAIR_IDS.forEach { id ->
            val starter = starterById[id] ?: return@forEach
            val item = itemsById[id] ?: return@forEach
            if (isProtectedStarterSystemRepairItem(item)) return@forEach
            repaired += putIfDifferent(item, "labelSl", starter.labelSl)
            repaired += putIfDifferent(item, "speakTextSl", starter.speakTextSl.orEmpty())
            repaired += putIfDifferent(item, "speechTextSl", starter.speakTextSl.orEmpty())
            repaired += putIfDifferent(item, "speechText", starter.speechText.orEmpty())
            repaired += putLanguageValue(item, "speechTextByLanguage", "sl", starter.speakTextSl.orEmpty())
            starter.labelByLanguage.forEach { (languageCode, label) ->
                repaired += putLanguageValue(item, "labelByLanguage", languageCode, label)
            }
            starter.speechTextByLanguage.forEach { (languageCode, speechText) ->
                repaired += putLanguageValue(item, "speechTextByLanguage", languageCode, speechText)
            }
            starter.questionByLanguage.forEach { (languageCode, question) ->
                repaired += putLanguageValue(item, "questionByLanguage", languageCode, question)
            }
        }
        return repaired
    }

    private fun repairStarterCategoryChildren(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0
        starterItems
            .filter { starter -> starter.children.isNotEmpty() }
            .forEach { starter ->
                val item = itemsById[starter.id] ?: return@forEach
                if (isUserProtected(item)) return@forEach
                val children = item.optJSONArray("children") ?: JSONArray()
                val existingChildren = stringList(children).toMutableSet()
                var itemRepaired = 0
                starter.children.forEach { childId ->
                    if (childId.isNotBlank() && childId in itemsById && existingChildren.add(childId)) {
                        children.put(childId)
                        itemRepaired++
                        repaired++
                    }
                }
                if (itemRepaired > 0 || item.optJSONArray("children") == null) {
                    item.put("children", children)
                }
            }
        return repaired
    }

    private fun repairHungryV1TestTree(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val starterById = starterItems.associateBy { item -> item.id }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0

        HUNGRY_V1_TEST_IDS.forEach { id ->
            val item = itemsById[id] ?: return@forEach
            if (isProtectedLocalAacItem(item)) return@forEach
            val starter = starterById[id] ?: return@forEach
            repaired += putIfDifferent(item, "labelSl", starter.labelSl)
            repaired += putIfDifferent(item, "speakTextSl", starter.speakTextSl.orEmpty())
            repaired += putIfDifferent(item, "speechText", starter.speechText.orEmpty())
            repaired += putIfDifferent(item, "actionType", starter.actionType)
            repaired += putIfDifferent(item, "opensSubicons", starter.opensSubicons)
            repaired += putIfDifferent(item, "speaksImmediately", starter.speaksImmediately)
            if (starter.children.isEmpty()) {
                repaired += removeChildrenIfPresent(item)
            } else {
                repaired += replaceChildrenIfDifferent(item, starter.children)
            }
            if (id != "hungry") {
                repaired += ensureOnlyVisibleUnder(item, starter.visibleUnderIds)
            }
            if (starter.questionByLanguage.isNotEmpty()) {
                starter.questionByLanguage.forEach { (languageCode, question) ->
                    repaired += putLanguageValue(item, "questionByLanguage", languageCode, question)
                }
            } else {
                repaired += removeQuestionMetadata(item)
            }
        }

        HUNGRY_V1_TEST_LEGACY_CHILDREN.forEach { id ->
            itemsById[id]?.let { item ->
                if (isProtectedLocalAacItem(item)) return@let
                repaired += removeVisibleUnderValue(item, "hungry")
            }
        }

        return repaired
    }

    private fun isProtectedLocalAacItem(item: JSONObject): Boolean {
        val source = item.optString("source")
            .ifBlank { item.optString("iconSource") }
            .trim()
            .uppercase()
        return item.optBoolean("lockedByUser", false) ||
            item.optBoolean("modifiedByTherapist", false) ||
            item.optBoolean("userEdited", false) ||
            item.optBoolean("therapistEdited", false) ||
            item.optBoolean("manualEdit", false) ||
            item.optBoolean("manualOverride", false) ||
            item.optBoolean("customized", false) ||
            source == "CUSTOM" ||
            source == "PATIENT" ||
            source == "THERAPIST"
    }

    private fun isProtectedStarterContentItem(item: JSONObject): Boolean {
        val source = item.optString("source")
            .ifBlank { item.optString("iconSource") }
            .trim()
            .uppercase()
        return item.optBoolean("lockedByUser", false) ||
            item.optBoolean("modifiedByTherapist", false) ||
            item.optBoolean("userEdited", false) ||
            item.optBoolean("therapistEdited", false) ||
            item.optBoolean("manualEdit", false) ||
            item.optBoolean("manualOverride", false) ||
            item.optBoolean("customized", false) ||
            item.optBoolean("locked", false) ||
            source == "CUSTOM" ||
            source == "PATIENT" ||
            source == "THERAPIST"
    }

    private fun isProtectedStarterSystemRepairItem(item: JSONObject): Boolean {
        val source = item.optString("source").trim().uppercase()
        return item.optBoolean("lockedByUser", false) ||
            item.optBoolean("modifiedByTherapist", false) ||
            item.optBoolean("userEdited", false) ||
            item.optBoolean("therapistEdited", false) ||
            item.optBoolean("manualEdit", false) ||
            item.optBoolean("manualOverride", false) ||
            item.optBoolean("customized", false) ||
            item.optBoolean("locked", false) ||
            source == "CUSTOM" ||
            source == "PATIENT" ||
            source == "THERAPIST"
    }

    private fun hasUsableLocalIcon(context: Context, item: JSONObject): Boolean {
        val imagePath = item.optString("imagePath").trim()
        if (imagePath.isBlank()) return false
        val iconSource = when (item.optString("iconSource").trim().uppercase()) {
            IconSource.SOCA.name -> IconSource.SOCA
            IconSource.ARASAAC.name -> IconSource.ARASAAC
            IconSource.CUSTOM.name, IconSource.CUSTOM_PHOTO.name -> IconSource.CUSTOM
            IconSource.PATIENT.name, IconSource.PATIENT_PHOTO.name -> IconSource.PATIENT
            IconSource.PLACE_PHOTO.name -> IconSource.PLACE_PHOTO
            else -> IconSource.SYSTEM
        }
        return AacStoragePaths.resolveIconFile(context, imagePath, iconSource)?.isFile == true
    }

    private fun repairToaletaV1Tree(itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0

        itemsById["wc"]?.let { item ->
            if (isProtectedLocalAacItem(item)) return@let
            repaired += putIfDifferent(item, "labelSl", "TOALETA")
            repaired += putIfDifferent(item, "speakTextSl", "Moram v toaleto.")
            repaired += putIfDifferent(item, "speechText", "Moram v toaleto.")
            repaired += putLanguageValue(item, "labelByLanguage", "uk", "Đ˘ĐŁĐĐ›Đ•Đ˘")
            repaired += putLanguageValue(item, "speechTextByLanguage", "uk", "Мені потрібно в туалет.")
            repaired += putIfDifferent(item, "actionType", "open_subicons")
            repaired += putIfDifferent(item, "opensSubicons", true)
            repaired += putIfDifferent(item, "speaksImmediately", false)
            repaired += replaceChildrenIfDifferent(item, TOALETA_V1_WC_CHILDREN)
            repaired += removeQuestionMetadata(item)
            repaired += putLanguageValue(item, "questionByLanguage", "sl", "Izberi, kaj potrebuješ.")
        }

        itemsById["nurse_help"]?.let { item ->
            if (isProtectedStarterSystemRepairItem(item)) return@let
            repaired += putIfDifferent(item, "labelSl", "SESTRA")
            repaired += putIfDifferent(item, "speakTextSl", "Potrebujem medicinsko sestro.")
            repaired += putIfDifferent(item, "speechTextSl", "Potrebujem medicinsko sestro.")
            repaired += putIfDifferent(item, "speechText", "Potrebujem medicinsko sestro.")
            repaired += putLanguageValue(item, "speechTextByLanguage", "sl", "Potrebujem medicinsko sestro.")
            repaired += putLanguageValue(item, "labelByLanguage", "uk", "МЕДСЕСТРА")
            repaired += putIfDifferent(item, "speakTextUk", "Мені потрібна медсестра.")
            repaired += putLanguageValue(item, "speechTextByLanguage", "uk", "Мені потрібна медсестра.")
            repaired += putIfDifferent(item, "imagePath", "system/aac_nurse.png")
            repaired += putIfDifferent(item, "iconSource", IconSource.SYSTEM.name)
            repaired += putIfDifferent(item, "actionType", "open_subicons")
            repaired += putIfDifferent(item, "opensSubicons", true)
            repaired += putIfDifferent(item, "speaksImmediately", false)
            repaired += replaceChildrenIfDifferent(item, TOALETA_V1_NURSE_CHILDREN)
            repaired += ensureOnlyVisibleUnder(item, listOf("wc"))
            repaired += removeQuestionMetadata(item)
            repaired += putLanguageValue(item, "questionByLanguage", "sl", "Kaj naj naredi sestra?")
            repaired += putLanguageValue(item, "questionByLanguage", "uk", "Що має зробити медсестра?")
        }

        val terminalRepairs = mapOf(
            "wc_wet" to ToaletaTerminalRepair(
                labelSl = "MOKRA",
                labelUk = "ĐśĐžĐšĐ Đ",
                speechSl = "Prosim, zamenjajte mi plenico. Mokra sem.",
                speechUk = "Đ‘ŃĐ´ŃŚ Đ»Đ°ŃĐşĐ°, Đ·Đ°ĐĽŃ–Đ˝Ń–Ń‚ŃŚ ĐĽĐµĐ˝Ń– ĐżŃ–Đ´ĐłŃĐ·ĐľĐş. ĐŻ ĐĽĐľĐşŃ€Đ°."
            ),
            "wc_dirty" to ToaletaTerminalRepair(
                labelSl = "UMAZANA",
                labelUk = "Đ‘Đ ĐŁĐ”ĐťĐ",
                speechSl = "Prosim, zamenjajte mi plenico. Umazana sem.",
                speechUk = "Đ‘ŃĐ´ŃŚ Đ»Đ°ŃĐşĐ°, Đ·Đ°ĐĽŃ–Đ˝Ń–Ń‚ŃŚ ĐĽĐµĐ˝Ń– ĐżŃ–Đ´ĐłŃĐ·ĐľĐş. ĐŻ Đ±Ń€ŃĐ´Đ˝Đ°."
            ),
            "wc_wet_and_dirty" to ToaletaTerminalRepair(
                labelSl = "OBOJE",
                labelUk = "ĐžĐ‘ĐžĐ„",
                speechSl = "Prosim, zamenjajte mi plenico. Mokra in umazana sem.",
                speechUk = "Đ‘ŃĐ´ŃŚ Đ»Đ°ŃĐşĐ°, Đ·Đ°ĐĽŃ–Đ˝Ń–Ń‚ŃŚ ĐĽĐµĐ˝Ń– ĐżŃ–Đ´ĐłŃĐ·ĐľĐş. ĐŻ ĐĽĐľĐşŃ€Đ° Ń– Đ±Ń€ŃĐ´Đ˝Đ°."
            ),
            "help_dressing" to ToaletaTerminalRepair(
                labelSl = "OBLAČENJE",
                labelUk = "ОДЯГАННЯ",
                speechSl = "Rabim pomoč pri oblačenju.",
                speechUk = "Мені потрібна допомога з одяганням."
            ),
            "help_washing" to ToaletaTerminalRepair(
                labelSl = "UMIVANJE",
                labelUk = "МИТТЯ",
                speechSl = "Rabim pomoč pri umivanju.",
                speechUk = "Мені потрібна допомога з миттям."
            ),
            "help_showering" to ToaletaTerminalRepair(
                labelSl = "TUŠ",
                labelUk = "ДУШ",
                speechSl = "Rabim pomoč pri tuširanju.",
                speechUk = "Мені потрібна допомога з душем."
            ),
            "noticed_blood" to ToaletaTerminalRepair(
                labelSl = "KRI",
                labelUk = "КРОВ",
                speechSl = "Opazila sem kri pri negi.",
                speechUk = "Я помітила кров під час догляду. Будь ласка, покличте медсестру."
            )
        )
        terminalRepairs.forEach { (id, repair) ->
            val item = itemsById[id] ?: return@forEach
            if (isProtectedLocalAacItem(item)) return@forEach
            val parentId = if (id in TOALETA_V1_WC_CHILDREN) "wc" else "nurse_help"
            repaired += putIfDifferent(item, "labelSl", repair.labelSl)
            repaired += putIfDifferent(item, "speakTextSl", repair.speechSl)
            repaired += putIfDifferent(item, "speechText", repair.speechSl)
            repaired += putLanguageValue(item, "labelByLanguage", "uk", repair.labelUk)
            repaired += putLanguageValue(item, "speechTextByLanguage", "uk", repair.speechUk)
            repaired += putIfDifferent(item, "actionType", "speak")
            repaired += putIfDifferent(item, "opensSubicons", false)
            repaired += putIfDifferent(item, "speaksImmediately", true)
            repaired += removeChildrenIfPresent(item)
            repaired += removeQuestionMetadata(item)
            repaired += ensureOnlyVisibleUnder(item, listOf(parentId))
        }

        TOALETA_V1_EXCLUDED_WC_CHILDREN.forEach { id ->
            itemsById[id]?.let { item ->
                if (isProtectedLocalAacItem(item)) return@let
                repaired += removeVisibleUnderValue(item, "wc")
            }
        }

        return repaired
    }

    private fun replaceChildrenIfDifferent(item: JSONObject, children: List<String>): Int {
        if (stringList(item.optJSONArray("children")) == children) return 0
        item.put("children", jsonArrayOf(children))
        return 1
    }

    private fun removeChildrenIfPresent(item: JSONObject): Int {
        if (!item.has("children")) return 0
        item.remove("children")
        return 1
    }

    private fun ensureOnlyVisibleUnder(item: JSONObject, parentIds: List<String>): Int {
        if (stringList(item.optJSONArray("visibleUnderIds")) == parentIds) return 0
        item.put("visibleUnderIds", jsonArrayOf(parentIds))
        return 1
    }

    private fun removeVisibleUnderValue(item: JSONObject, parentId: String): Int {
        val current = stringList(item.optJSONArray("visibleUnderIds"))
        if (parentId !in current) return 0
        val next = current.filterNot { it == parentId }
        if (next.isEmpty()) {
            item.remove("visibleUnderIds")
        } else {
            item.put("visibleUnderIds", jsonArrayOf(next))
        }
        return 1
    }

    private fun removeQuestionMetadata(item: JSONObject): Int {
        var repaired = 0
        listOf("questionSl", "questionUk", "questionByLanguage", "followUpQuestion").forEach { key ->
            if (item.has(key)) {
                item.remove(key)
                repaired++
            }
        }
        return repaired
    }

    private fun putLanguageValue(item: JSONObject, objectKey: String, languageCode: String, value: String): Int {
        val languageObject = item.optJSONObject(objectKey) ?: JSONObject()
        if (languageObject.optString(languageCode) == value) {
            if (!item.has(objectKey)) {
                item.put(objectKey, languageObject)
                return 1
            }
            return 0
        }
        languageObject.put(languageCode, value)
        item.put(objectKey, languageObject)
        return 1
    }

    private fun putLanguageValueIfBlank(
        item: JSONObject,
        objectKey: String,
        languageCode: String,
        value: String
    ): Int {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return 0
        val languageObject = item.optJSONObject(objectKey) ?: JSONObject()
        if (languageObject.optString(languageCode).trim().isNotBlank()) return 0
        languageObject.put(languageCode, cleaned)
        item.put(objectKey, languageObject)
        return 1
    }

    private fun putIfDifferent(item: JSONObject, key: String, value: String): Int {
        if (item.optString(key) == value) return 0
        item.put(key, value)
        return 1
    }

    private fun putIfDifferent(item: JSONObject, key: String, value: Boolean): Int {
        if (item.has(key) && item.optBoolean(key) == value) return 0
        item.put(key, value)
        return 1
    }

    private data class ToaletaTerminalRepair(
        val labelSl: String,
        val labelUk: String,
        val speechSl: String,
        val speechUk: String
    )

    private val HUNGRY_V1_TEST_IDS = setOf(
        "hungry",
        "hungry_main_dish",
        "hungry_snack",
        "hungry_fast_food",
        "hungry_fruit",
        "hungry_dessert",
        "hungry_soup",
        "hungry_meat",
        "hungry_side_dishes",
        "hungry_potato",
        "hungry_beef_soup",
        "hungry_chicken_soup",
        "hungry_vegetable_soup",
        "hungry_pork",
        "hungry_chicken",
        "hungry_beef",
        "hungry_veal",
        "hungry_lamb",
        "hungry_kid_goat",
        "hungry_fish",
        "hungry_pasta",
        "hungry_rice",
        "hungry_vegetables",
        "hungry_roasted_potato",
        "hungry_fries",
        "hungry_mashed_potato",
        "hungry_yogurt",
        "hungry_fruit_yogurt",
        "hungry_chips",
        "hungry_crackers",
        "hungry_hamburger",
        "hungry_cevapcici",
        "hungry_pleskavica",
        "hungry_hotdog",
        "hungry_pizza",
        "hungry_burek",
        "hungry_toast",
        "hungry_pancakes",
        "hungry_apple",
        "hungry_pear",
        "hungry_banana",
        "hungry_grapes",
        "hungry_blueberries",
        "hungry_strawberries",
        "hungry_kiwi",
        "hungry_ice_cream",
        "hungry_cake",
        "hungry_cookies",
        "hungry_doughnut",
        "hungry_kremsnita"
    )

    private val HUNGRY_V1_TEST_LEGACY_CHILDREN = setOf(
        "soup",
        "bread",
        "fruit",
        "ice_cream",
        "potato",
        "rice",
        "food_yogurt",
        "food_banana",
        "food_apple",
        "food_lunch",
        "food_dinner",
        "sweet"
    )

    private fun repairPeopleGroupChildren(itemsArray: JSONArray): Int {
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()

        var repaired = 0
        PEOPLE_GROUP_CHILD_REPAIRS.forEach { (id, desiredChildren) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            val existingChildren = stringList(item.optJSONArray("children"))
            if (existingChildren == desiredChildren) return@forEach
            item.put("children", jsonArrayOf(desiredChildren))
            repaired++
        }
        return repaired
    }

    private fun repairConversationTreeV3Metadata(itemsArray: JSONArray, starterItems: List<AacItem>): Int {
        val guidedBranchIds = setOf(
            "people",
            "about_me",
            "socialno",
            "need",
            "problem",
            "please",
            "pogovor",
            "what_root",
            "where_root",
            "when_root",
            "place_group",
            "i_want",
            "person_dusan",
            "person_zana",
            "person_sergej",
            "person_julija",
            "person_oksana",
            "person_inna",
            "person_franc",
            "person_other",
            "miss_someone",
            "contact_call",
            "contact_message",
            "help",
            "repeat",
            "slower",
            "more",
            "turn_me",
            "cannot",
            "cold_hot",
            "care",
            "clothing",
            "uncomfortable",
            "thirsty",
            "drink",
            "hungry",
            "food",
            "health",
            "emergency",
            "pain",
            "wc",
            "real_world",
            "vending_drinks",
            "vending_coffee_tea"
        )
        val starterById = starterItems
            .filter { item -> item.id in guidedBranchIds }
            .associateBy { item -> item.id }
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        var repaired = 0
        starterById.forEach { (id, starter) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            if (starter.opensSubicons && !item.optBoolean("opensSubicons", false)) {
                item.put("opensSubicons", true)
                repaired++
            }
            if (starter.opensSubicons && item.optBoolean("speaksImmediately", true)) {
                item.put("speaksImmediately", false)
                repaired++
            }
            if (starter.opensSubicons && (item.optString("actionType").isBlank() || item.optString("actionType") == "speak")) {
                item.put("actionType", "open_subicons")
                repaired++
            }
            if (starter.questionByLanguage.isNotEmpty()) {
                val questions = item.optJSONObject("questionByLanguage") ?: JSONObject()
                starter.questionByLanguage.forEach { (languageCode, question) ->
                    if (questions.optString(languageCode).isBlank()) {
                        questions.put(languageCode, question)
                        repaired++
                    }
                }
                item.put("questionByLanguage", questions)
            }
        }
        return repaired
    }

    private fun repairNoUnderstandSystemLabels(itemsArray: JSONArray): Int {
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            val id = item.optString("id").trim()
            if (id != "no_understand" && id != "dont_understand") return@forEach
            if (isUserProtected(item)) return@forEach

            val currentLabel = item.optString("labelSl")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
            val isKnownUnsafeLabel = currentLabel.isBlank() ||
                currentLabel == "NE" ||
                currentLabel == "NE\nRAZUMEM"
            if (!isKnownUnsafeLabel) return@forEach

            item.put("labelSl", "NE RAZUMEM")
            item.put("text", "NE RAZUMEM")
            item.put("baseText", "NE RAZUMEM")
            item.put("labelUk", "ĐŻ ĐťĐ• Đ ĐžĐ—ĐŁĐśĐ†Đ®")
            item.put("labelEn", "I DON'T UNDERSTAND")
            item.put("speechText", "ne razumem")
            item.put("speakTextSl", "ne razumem")
            item.put("speakTextUk", "ĐŻ Đ˝Đµ Ń€ĐľĐ·ŃĐĽŃ–ŃŽ")
            item.put("speechTextEn", "I don't understand")
            item.put("labelByLanguage", JSONObject(item.optJSONObject("labelByLanguage")?.toString() ?: "{}").apply {
                put("sl", "NE RAZUMEM")
                put("uk", "ĐŻ ĐťĐ• Đ ĐžĐ—ĐŁĐśĐ†Đ®")
                put("en", "I DON'T UNDERSTAND")
            })
            item.put("speechTextByLanguage", JSONObject(item.optJSONObject("speechTextByLanguage")?.toString() ?: "{}").apply {
                put("sl", "ne razumem")
                put("uk", "ĐŻ Đ˝Đµ Ń€ĐľĐ·ŃĐĽŃ–ŃŽ")
                put("en", "I don't understand")
            })
            repaired++
        }
        return repaired
    }

    private fun repairFixedTopRowMetadata(itemsArray: JSONArray): Int {
        val desiredPositions = mapOf(
            "no" to 1,
            "dont_understand" to 2,
            "yes" to 3,
            "thank_you" to 4,
            "sorry" to 5
        )
        val legacyFixedRowIds = setOf("help", "pain", "stop", "no_understand")
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            if (isUserProtected(item)) return@forEach
            val id = item.optString("id").trim()
            val desiredPosition = desiredPositions[id]
            if (desiredPosition != null) {
                if (item.optInt("fixedTopRowPosition", 0) != desiredPosition) {
                    item.put("fixedTopRowPosition", desiredPosition)
                    repaired++
                }
                return@forEach
            }
            if (id in legacyFixedRowIds && item.optInt("fixedTopRowPosition", 0) in 1..5) {
                item.remove("fixedTopRowPosition")
                repaired++
            }
        }
        return repaired
    }

    private fun repairDefaultPageV3Placements(context: Context, itemsArray: JSONArray, pageId: String): Int {
        if (pageId.isBlank()) return 0
        if (isCoreV2HomeRepairMarked(context) || isCoreV2HomeLayout(itemsArray, pageId)) {
            return 0
        }
        val desiredPositions = mapOf(
            "people" to 1,
            "need" to 2,
            "problem" to 3,
            "thirsty" to 4,
            "hungry" to 5,
            "pain" to 6,
            "wc" to 7,
            "tired" to 8,
            "rest" to 9,
            "please" to 10,
            "what_root" to 11,
            "where_root" to 12,
            "when_root" to 13,
            "i_want" to 14,
            "other" to 15,
            "place_group" to 16,
            "feeling" to 17,
            "care" to 18,
            "health" to 19,
            "repeat" to 20,
            "wait" to 21,
            "come_to_me" to 22,
            "cannot" to 23,
            "cold_hot" to 24,
            "uncomfortable" to 25
        )
        val legacyNonRootIds = setOf(
            "help",
            "family_group",
            "friends_group",
            "call",
            "message",
            "miss_someone",
            "what_do",
            "where_go",
            "when_come",
            "dont_want",
            "drink",
            "food"
        )
        var repaired = 0
        itemObjects(itemsArray).forEach { item ->
            if (isUserProtected(item)) return@forEach
            val id = item.optString("id").trim()
            val desiredPosition = desiredPositions[id]
            var itemRepaired = 0
            if (id in legacyNonRootIds && item.optBoolean("isRootItem", false)) {
                item.put("isRootItem", false)
                itemRepaired++
            } else if (desiredPosition != null && !item.optBoolean("isRootItem", false)) {
                item.put("isRootItem", true)
                itemRepaired++
            }
            val existingPlacements = item.optJSONArray("placements") ?: JSONArray()
            val nextPlacements = JSONArray()
            var removedDefaultPagePlacement = false
            for (index in 0 until existingPlacements.length()) {
                val placement = existingPlacements.optJSONObject(index) ?: continue
                if (placement.optString("pageId").trim() == pageId) {
                    removedDefaultPagePlacement = true
                } else {
                    nextPlacements.put(placement)
                }
            }
            if (desiredPosition != null) {
                nextPlacements.put(JSONObject().put("pageId", pageId).put("position5x5", desiredPosition))
            }
            if (removedDefaultPagePlacement || desiredPosition != null && existingPlacements.length() != nextPlacements.length()) {
                if (nextPlacements.length() > 0) {
                    item.put("placements", nextPlacements)
                } else {
                    item.remove("placements")
                }
                itemRepaired++
            }
            if (itemRepaired > 0) {
                repaired++
            }
        }
        return repaired
    }

    private fun isCoreV2HomeRepairMarked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(CORE_V2_REPAIR_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CORE_V2_HOME_REPAIR_DONE, false) ||
            prefs.getString(KEY_AAC_HOME_LAYOUT_VERSION, "").orEmpty() == CORE_V2_HOME_LAYOUT_VERSION
    }

    private fun isCoreV2HomeLayout(itemsArray: JSONArray, pageId: String): Boolean {
        val expectedFixedPositions = mapOf(
            "no" to 1,
            "dont_understand" to 2,
            "yes" to 3,
            "thank_you" to 4,
            "sorry" to 5
        )
        val expectedPagePositions = mapOf(
            "wc" to 6,
            "pain" to 7,
            "thirsty" to 8,
            "hungry" to 9,
            "tired" to 10,
            "i_want" to 11,
            "need" to 12,
            "people" to 13,
            "miss_someone" to 14,
            "call" to 15,
            "feeling" to 16,
            "place_group" to 17,
            "care" to 18,
            "health" to 19,
            "dont_want" to 20,
            "please" to 21,
            "wait" to 22,
            "repeat" to 23,
            "pogovor" to 24,
            "activity_group" to 25
        )
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item ->
                item.optString("id").trim().takeIf { it.isNotBlank() }?.let { id -> id to item }
            }
            .toMap()
        val fixedMatches = expectedFixedPositions.all { (itemId, position) ->
            itemsById[itemId]?.optInt("fixedTopRowPosition", 0) == position
        }
        val pageMatches = expectedPagePositions.all { (itemId, position) ->
            itemHasPlacement(itemsById[itemId], pageId, position)
        }
        return fixedMatches && pageMatches
    }

    private fun itemHasPlacement(item: JSONObject?, pageId: String, position: Int): Boolean {
        val placements = item?.optJSONArray("placements") ?: return false
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (
                placement.optString("pageId").trim() == pageId &&
                placement.optInt("position5x5", 0) == position
            ) {
                return true
            }
        }
        return false
    }

    private fun repairDrinkChildSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        DRINK_TREE_CHILDREN.forEach { (parentId, childIds) ->
            itemsById[parentId]?.let { parent ->
                if (isUserProtected(parent)) return@let
                repaired += ensureDrinkBranchMetadata(
                    item = parent,
                    childIds = childIds,
                    questionSl = DRINK_BRANCH_QUESTIONS[parentId] ?: "Izberi, kaj želiš piti."
                )
            }
        }

        DRINK_SPEECH_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        return repaired
    }

    private fun repairFoodChildSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        val food = itemsById["food"]
        if (food != null) {
            if (!isUserProtected(food)) {
                repaired += ensureParentQuestionMetadata(
                    item = food,
                    childRepairs = FOOD_CHILD_REPAIRS,
                    questionSl = "Kaj ĹľeliĹˇ jesti?",
                    questionUk = "Đ©Đľ Ń‚Đ¸ Ń…ĐľŃ‡ĐµŃ Ń—ŃŃ‚Đ¸?",
                    questionEn = "What do you want to eat?"
                )
            }
        }

        FOOD_CHILD_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        return repaired
    }

    private fun repairPainSpeechItems(itemsArray: JSONArray): Int {
        var repaired = 0
        val itemsById = itemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()

        val pain = itemsById["pain"]
        if (pain != null) {
            if (!isUserProtected(pain)) {
                repaired += ensureParentQuestionMetadata(
                    item = pain,
                    childRepairs = PAIN_CHILD_REPAIRS,
                    questionSl = "Kje te boli?",
                    questionUk = "Đ”Đµ Ń‚ĐµĐ±Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ?",
                    questionEn = "Where does it hurt?"
                )
                repaired += ensureGuidedPainNode(
                    item = pain,
                    children = painRootChildren(),
                    questionSl = "Izberi, kaj te boli."
                )
            }
        }

        PAIN_CHILD_REPAIRS.forEach { repair ->
            val item = itemsById[repair.id]
            if (item == null) {
                itemsArray.put(repair.toJson())
                repaired++
            } else {
                if (isUserProtected(item)) return@forEach
                repaired += repair.applyTo(item)
            }
        }
        PAIN_GUIDED_NODE_REPAIRS.forEach { (id, children, questionSl) ->
            val item = itemsById[id] ?: return@forEach
            if (isUserProtected(item)) return@forEach
            repaired += ensureGuidedPainNode(item, children, questionSl)
        }
        return repaired
    }

    private fun ensureGuidedPainNode(item: JSONObject, children: List<String>, questionSl: String): Int {
        var repaired = 0
        val currentChildren = stringList(item.optJSONArray("children"))
        if (currentChildren != children) {
            item.put("children", jsonArrayOf(children))
            repaired++
        }
        if (!item.optBoolean("opensSubicons", false)) {
            item.put("opensSubicons", true)
            repaired++
        }
        if (item.optBoolean("speaksImmediately", true)) {
            item.put("speaksImmediately", false)
            repaired++
        }
        if (item.optString("actionType").isBlank() || item.optString("actionType") == "speak") {
            item.put("actionType", "open_subicons")
            repaired++
        }
        val questionByLanguage = item.optJSONObject("questionByLanguage") ?: JSONObject()
        if (questionByLanguage.optString("sl") != questionSl) {
            questionByLanguage.put("sl", questionSl)
            item.put("questionByLanguage", questionByLanguage)
            repaired++
        }
        return repaired
    }

    private fun ensureParentQuestionMetadata(
        item: JSONObject,
        childRepairs: List<FoodChildRepair>,
        questionSl: String,
        questionUk: String,
        questionEn: String
    ): Int {
        if (isUserProtected(item)) return 0
        var repaired = 0
        val children = item.optJSONArray("children") ?: JSONArray()
        val childIds = stringList(children).toMutableSet()
        childRepairs.forEach { repair ->
            if (childIds.add(repair.id)) {
                children.put(repair.id)
                repaired++
            }
        }
        if (repaired > 0 || item.optJSONArray("children") == null) {
            item.put("children", children)
        }
        val questionByLanguage = item.optJSONObject("questionByLanguage") ?: JSONObject()
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "sl",
            questionSl,
            legacyQuestionValues(questionSl) +
                setOf(item.optString("speechText").trim().lowercase(), item.optString("speakTextSl").trim().lowercase())
        )
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "uk",
            questionUk,
            legacyQuestionValues(questionUk) + setOf(item.optString("speakTextUk").trim().lowercase())
        )
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "en",
            questionEn,
            legacyQuestionValues(questionEn) + setOf(item.optString("speechTextEn").trim().lowercase())
        )
        item.put("questionByLanguage", questionByLanguage)
        if (!item.optBoolean("opensSubicons", false)) {
            item.put("opensSubicons", true)
            repaired++
        }
        if (item.optBoolean("addsToSentence", true)) {
            item.put("addsToSentence", false)
            repaired++
        }
        if (item.optBoolean("speaksImmediately", true)) {
            item.put("speaksImmediately", false)
            repaired++
        }
        if (item.optString("actionType").isBlank() || item.optString("actionType") == "speak") {
            item.put("actionType", "open_subicons")
            repaired++
        }
        return repaired
    }

    private fun ensureDrinkBranchMetadata(
        item: JSONObject,
        childIds: List<String>,
        questionSl: String
    ): Int {
        if (isUserProtected(item)) return 0
        var repaired = 0
        val currentChildren = stringList(item.optJSONArray("children"))
        if (currentChildren != childIds) {
            item.put("children", jsonArrayOf(childIds))
            repaired++
        }
        val questionByLanguage = item.optJSONObject("questionByLanguage") ?: JSONObject()
        repaired += putLanguageIfBlankOrLegacy(
            questionByLanguage,
            "sl",
            questionSl,
            legacyQuestionValues(questionSl) +
                setOf(item.optString("speechText").trim().lowercase(), item.optString("speakTextSl").trim().lowercase())
        )
        item.put("questionByLanguage", questionByLanguage)
        if (!item.optBoolean("opensSubicons", false)) {
            item.put("opensSubicons", true)
            repaired++
        }
        if (item.optBoolean("addsToSentence", true)) {
            item.put("addsToSentence", false)
            repaired++
        }
        if (item.optBoolean("speaksImmediately", true)) {
            item.put("speaksImmediately", false)
            repaired++
        }
        if (item.optString("actionType").isBlank() || item.optString("actionType") == "speak") {
            item.put("actionType", "open_subicons")
            repaired++
        }
        return repaired
    }

    private fun legacyQuestionValues(question: String): Set<String> {
        return setOf(
            question.removeSuffix("?"),
            question
                .replace("Đ©Đľ Ń‚Đ¸ Ń…ĐľŃ‡ĐµŃ", "Đ©Đľ Ń…ĐľŃ‡ĐµŃ")
                .removeSuffix("?"),
            question
                .replace("Đ©Đľ Ń‚Đ¸ Ń…ĐľŃ‡ĐµŃ", "Đ©Đľ Ń…ĐľŃ‡ĐµŃ"),
            question.replace("Đ”Đµ Ń‚ĐµĐ±Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ", "Đ”Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ").removeSuffix("?"),
            question.replace("Đ”Đµ Ń‚ĐµĐ±Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ", "Đ”Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ")
        )
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != question.trim().lowercase() }
            .toSet()
    }

    private fun addDefaultPagePlacements(itemsArray: JSONArray, pageId: String): Int {
        val occupiedPositions = occupiedPositions(itemsArray, pageId).toMutableSet()
        val fixedIds = fixedRowItemIds(itemsArray)
        val candidates = rootItemObjects(itemsArray)
            .filter { item -> item.optString("id").trim() !in fixedIds }
            .filterNot { item -> itemAlreadyOnPage(item, pageId) }
        val freePositions = (6..25).filter { it !in occupiedPositions }
        var added = 0
        candidates.zip(freePositions).forEach { (item, position) ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            placements.put(JSONObject().put("pageId", pageId).put("position5x5", position))
            item.put("placements", placements)
            occupiedPositions += position
            added++
        }
        return added
    }

    private fun addStarterItemsToEmptyDefaultPageSlots(itemsArray: JSONArray, pageId: String): Int {
        if (pageId.isBlank()) return 0
        val occupiedPositions = occupiedPositions(itemsArray, pageId).toMutableSet()
        val freePositions = (6..25).filter { it !in occupiedPositions }
        if (freePositions.isEmpty()) return 0

        val fixedIds = fixedRowItemIds(itemsArray)
        val alreadyOnPage = itemIdsOnPage(itemsArray, pageId).toMutableSet()
        val itemsById = rootItemObjects(itemsArray)
            .mapNotNull { item -> item.optString("id").trim().takeIf { it.isNotBlank() }?.let { it to item } }
            .toMap()
        val candidates = STARTER_VISIBILITY_PRIORITY
            .asSequence()
            .mapNotNull { itemId -> itemsById[itemId] }
            .filterNot(::isUserProtected)
            .filter { item -> item.optString("id").trim() !in fixedIds }
            .filter { item -> item.optString("id").trim() !in alreadyOnPage }
            .distinctBy { item -> item.optString("id").trim() }
            .toList()

        var added = 0
        candidates.zip(freePositions).forEach { (item, position) ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            placements.put(JSONObject().put("pageId", pageId).put("position5x5", position))
            item.put("placements", placements)
            occupiedPositions += position
            alreadyOnPage += item.optString("id").trim()
            added++
        }
        return added
    }

    private fun ensureDomProfileLinked(context: Context, itemIds: List<String>): ProfileBootstrapResult {
        val safeItemIds = itemIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (safeItemIds.isEmpty()) {
            saveDomProfileDebug(
                context = context,
                profileFile = null,
                profileFileExists = false,
                profileType = "NO_SAFE_ITEMS",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(linkedItemCount = 0, updated = false)
        }
        val profilesDir = AacStoragePaths.getProfilesDataDir(context) ?: run {
            saveDomProfileDebug(
                context = context,
                profileFile = null,
                profileFileExists = false,
                profileType = "NO_PROFILE_DIR",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(0, false)
        }
        if (!profilesDir.exists() && !profilesDir.mkdirs()) {
            saveDomProfileDebug(
                context = context,
                profileFile = File(profilesDir, DOM_PROFILE_FILE),
                profileFileExists = false,
                profileType = "PROFILE_DIR_CREATE_FAILED",
                domProfileFound = false,
                domProfileId = "",
                itemIdsBefore = 0,
                itemIdsAfter = 0
            )
            return ProfileBootstrapResult(0, false)
        }
        val profileFile = File(profilesDir, DOM_PROFILE_FILE)
        val rootJson = readProfileJson(profileFile)
        val profileType = domProfileType(rootJson)
        val profileJson = domProfileJson(rootJson) ?: JSONObject()
            .put("id", DOM_PROFILE_ID)
            .put("displayName", "DOM")
            .put("context", AacCommunicationContext.NORMAL_COMMUNICATION.name)
            .put("enabled", true)
        val domProfileFound = domProfileJson(rootJson) != null
        val domProfileId = profileJson.optString("id").trim()
        val existingItemIds = stringList(profileJson.optJSONArray("itemIds"))
        if (existingItemIds.isNotEmpty()) {
            saveDomProfileDebug(
                context = context,
                profileFile = profileFile,
                profileFileExists = profileFile.isFile,
                profileType = profileType,
                domProfileFound = domProfileFound,
                domProfileId = domProfileId,
                itemIdsBefore = existingItemIds.size,
                itemIdsAfter = existingItemIds.size
            )
            return ProfileBootstrapResult(linkedItemCount = existingItemIds.size, updated = false)
        }
        profileJson.put("itemIds", JSONArray().apply { safeItemIds.forEach { itemId -> put(itemId) } })
        val outputJson = if (rootJson?.has("profiles") == true) {
            ensureDomProfileInRoot(rootJson, profileJson)
        } else {
            profileJson
        }
        profileFile.writeText(outputJson.toString(2), Charsets.UTF_8)
        val writtenRootJson = readProfileJson(profileFile)
        val itemIdsAfter = stringList(domProfileJson(writtenRootJson)?.optJSONArray("itemIds")).size
        saveDomProfileDebug(
            context = context,
            profileFile = profileFile,
            profileFileExists = profileFile.isFile,
            profileType = profileType,
            domProfileFound = domProfileFound,
            domProfileId = domProfileId,
            itemIdsBefore = existingItemIds.size,
            itemIdsAfter = itemIdsAfter
        )
        Log.d(
            TAG,
            "DOM_PROFILE_DEBUG file=${profileFile.absolutePath} exists=${profileFile.isFile} type=$profileType found=$domProfileFound id=$domProfileId before=${existingItemIds.size} after=$itemIdsAfter"
        )
        return ProfileBootstrapResult(linkedItemCount = safeItemIds.size, updated = true)
    }

    fun inspectDomProfileDebug(context: Context): DomProfileDebug {
        val profileFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        }
        val rootJson = profileFile?.let { readProfileJson(it) }
        val profileJson = domProfileJson(rootJson)
        val currentItemIdsCount = stringList(profileJson?.optJSONArray("itemIds")).size
        val prefs = context.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
        return DomProfileDebug(
            profileFilePath = profileFile?.absolutePath
                ?: prefs.getString(KEY_DEBUG_PROFILE_FILE_PATH, "").orEmpty(),
            profileFileExists = profileFile?.isFile
                ?: prefs.getBoolean(KEY_DEBUG_PROFILE_FILE_EXISTS, false),
            profileType = domProfileType(rootJson),
            domProfileFound = profileJson != null,
            domProfileId = profileJson?.optString("id")?.trim().orEmpty(),
            itemIdsBefore = prefs.getInt(KEY_DEBUG_ITEM_IDS_BEFORE, currentItemIdsCount),
            itemIdsAfter = prefs.getInt(KEY_DEBUG_ITEM_IDS_AFTER, currentItemIdsCount)
        )
    }

    private fun syncDomProfileRelations(context: Context, itemsArray: JSONArray): Int {
        val domItemIds = domProfileItemIds(context).toSet()
        if (domItemIds.isEmpty()) return 0
        var updatedCount = 0
        itemObjects(itemsArray).forEach { item ->
            val itemId = item.optString("id").trim()
            if (itemId.isBlank() || itemId !in domItemIds || itemHasDomProfileRelation(item)) {
                return@forEach
            }
            val profileIds = item.optJSONArray("profileIds") ?: JSONArray()
            profileIds.put(DOM_PROFILE_ID)
            item.put("profileIds", profileIds)
            updatedCount += 1
        }
        if (updatedCount > 0) {
            Log.d(TAG, "AAC_BOOTSTRAP_DOM_PROFILE_RELATIONS_SYNCED count=$updatedCount")
        }
        return updatedCount
    }

    private fun domProfileItemIds(context: Context): List<String> {
        val profileFile = AacStoragePaths.getProfilesDataDir(context)?.let { profilesDir ->
            File(profilesDir, DOM_PROFILE_FILE)
        } ?: return emptyList()
        val rootJson = readProfileJson(profileFile)
        val profileJson = domProfileJson(rootJson) ?: return emptyList()
        return stringList(profileJson.optJSONArray("itemIds"))
    }

    private fun itemHasDomProfileRelation(item: JSONObject): Boolean {
        val directIds = listOf("profileId", "profile_id", "profile")
            .map { key -> item.optString(key).trim() }
        val arrayIds = listOf("profileIds", "profile_ids", "profiles")
            .flatMap { key -> stringList(item.optJSONArray(key)) }
        return DOM_PROFILE_ID in (directIds + arrayIds)
    }

    private fun domProfileJson(rootJson: JSONObject?): JSONObject? {
        if (rootJson == null) return null
        if (rootJson.optString("id").trim() == DOM_PROFILE_ID) {
            return rootJson
        }
        val profiles = rootJson.optJSONArray("profiles") ?: return null
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) {
                return profile
            }
        }
        return null
    }

    private fun domProfileType(rootJson: JSONObject?): String {
        if (rootJson == null) return "MISSING"
        if (rootJson.optString("id").trim() == DOM_PROFILE_ID) return "DIRECT"
        if (rootJson.has("profiles")) return "WRAPPED"
        return "UNKNOWN"
    }

    private fun ensureDomProfileInRoot(rootJson: JSONObject, profileJson: JSONObject): JSONObject {
        val profiles = rootJson.optJSONArray("profiles") ?: JSONArray().also { rootJson.put("profiles", it) }
        for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optString("id").trim() == DOM_PROFILE_ID) {
                return rootJson
            }
        }
        profiles.put(profileJson)
        return rootJson
    }

    private fun loadItemsJson(itemsFile: File?, fallbackItems: List<AacItem>): RawItemsJson {
        if (itemsFile?.isFile == true) {
            return try {
                val raw = itemsFile.readText(Charsets.UTF_8).trim()
                if (raw.startsWith("[")) {
                    val itemsArray = JSONArray(raw)
                    RawItemsJson(rootObject = null, rootArray = itemsArray, itemsArray = itemsArray, createdFromFallback = false)
                } else {
                    val rootObject = JSONObject(raw)
                    RawItemsJson(
                        rootObject = rootObject,
                        rootArray = null,
                        itemsArray = rootObject.optJSONArray("items") ?: JSONArray(),
                        createdFromFallback = false
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "AAC_BOOTSTRAP_ITEMS_READ_FAILED", error)
                fallbackRawItems(fallbackItems)
            }
        }
        return fallbackRawItems(fallbackItems)
    }

    private fun fallbackRawItems(fallbackItems: List<AacItem>): RawItemsJson {
        val itemsArray = JSONArray().apply {
            fallbackItems.forEach { item -> put(item.toBootstrapJson()) }
        }
        return RawItemsJson(
            rootObject = JSONObject().put("items", itemsArray),
            rootArray = null,
            itemsArray = itemsArray,
            createdFromFallback = true
        )
    }

    private fun saveItemsJson(itemsFile: File?, rawItems: RawItemsJson, itemsArray: JSONArray) {
        val file = itemsFile ?: return
        file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        val outputText = when {
            rawItems.rootArray != null -> {
                itemsArray.toString(2)
            }
            rawItems.rootObject != null -> {
                rawItems.rootObject.put("items", itemsArray)
                rawItems.rootObject.toString(2)
            }
            else -> {
                JSONObject().put("items", itemsArray).toString(2)
            }
        }
        file.writeText(outputText, Charsets.UTF_8)
    }

    private fun jsonArrayOf(values: List<String>): JSONArray {
        return JSONArray().apply {
            values.forEach { value -> put(value) }
        }
    }

    private fun currentPatientPages(context: Context): List<Pair<String, String>> {
        val encoded = context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PATIENT_PAGES, "")
            .orEmpty()
        return encoded.split(PATIENT_PAGE_SEPARATOR)
            .mapNotNull { encodedPage ->
                val parts = encodedPage.split(PATIENT_PAGE_FIELD_SEPARATOR)
                val pageId = parts.getOrNull(0).orEmpty().trim()
                val title = parts.getOrNull(1).orEmpty().trim().ifBlank { pageId }
                if (isSafePageId(pageId)) pageId to title else null
            }
            .distinctBy { it.first }
    }

    private fun savePatientPages(context: Context, pages: List<Pair<String, String>>) {
        val encoded = pages
            .filter { isSafePageId(it.first) }
            .joinToString(PATIENT_PAGE_SEPARATOR) { (pageId, title) ->
                pageId + PATIENT_PAGE_FIELD_SEPARATOR + title
            }
        context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATIENT_PAGES, encoded)
            .apply()
    }

    private fun setDefaultPatientPage(context: Context, pageId: String) {
        context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_PATIENT_PAGE_ID, pageId)
            .apply()
    }

    private fun currentDefaultPatientPage(context: Context): String {
        return context.getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_PATIENT_PAGE_ID, "")
            .orEmpty()
            .trim()
            .takeIf(::isSafePageId)
            .orEmpty()
    }

    private fun rootItemObjects(itemsArray: JSONArray): List<JSONObject> {
        return itemObjects(itemsArray)
            .filter { item ->
                val hasParent = item.optString("parentId").trim().isNotBlank() ||
                    (item.optJSONArray("visibleUnderIds")?.length() ?: 0) > 0 ||
                    (item.optJSONArray("parentIds")?.length() ?: 0) > 0
                val isRoot = if (item.has("isRootItem")) item.optBoolean("isRootItem", true) else !hasParent
                isRoot && !item.optBoolean("isHiddenUntilParent", false)
            }
            .sortedWith(compareBy<JSONObject> { it.optInt("priority", Int.MAX_VALUE) }.thenBy { it.optString("id") })
    }

    private fun itemObjects(itemsArray: JSONArray): List<JSONObject> {
        return buildList {
            for (index in 0 until itemsArray.length()) {
                itemsArray.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun isUserProtected(item: JSONObject): Boolean {
        return item.optBoolean("userEdited", false) || item.optBoolean("locked", false)
    }

    private fun rootItemIds(itemsArray: JSONArray): List<String> {
        return rootItemObjects(itemsArray).map { it.optString("id").trim() }.filter { it.isNotBlank() }
    }

    private fun itemIdsOnPage(itemsArray: JSONArray, pageId: String): List<String> {
        return itemObjects(itemsArray).filter { itemAlreadyOnPage(it, pageId) }
            .map { it.optString("id").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun occupiedPositions(itemsArray: JSONArray, pageId: String): Set<Int> {
        return itemObjects(itemsArray).flatMap { item ->
            val placements = item.optJSONArray("placements") ?: JSONArray()
            buildList {
                for (index in 0 until placements.length()) {
                    val placement = placements.optJSONObject(index) ?: continue
                    if (placement.optString("pageId").trim() == pageId) {
                        val position = placement.optInt("position5x5", 0)
                        if (position in 1..25) add(position)
                    }
                }
            }
        }.toSet()
    }

    private fun itemAlreadyOnPage(item: JSONObject, pageId: String): Boolean {
        val placements = item.optJSONArray("placements") ?: return false
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (placement.optString("pageId").trim() == pageId && placement.optInt("position5x5", 0) in 1..25) {
                return true
            }
        }
        return false
    }

    private fun fixedRowItemIds(itemsArray: JSONArray): Set<String> {
        return itemObjects(itemsArray)
            .filter { item -> item.optInt("fixedTopRowPosition", 0) in 1..5 }
            .map { it.optString("id").trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private val STARTER_VISIBILITY_PRIORITY = listOf(
        "yes",
        "no",
        "help",
        "no_understand",
        "dont_understand",
        "wait",
        "water",
        "coffee",
        "change_diaper",
        "diaper",
        "body_position",
        "uncomfortable",
        "pain",
        "bad_feeling",
        "bad",
        "tired",
        "afraid",
        "unsafe",
        "not_safe",
        "stop",
        "stop_movement",
        "afraid_fall",
        "fear_falling"
    )

    private fun readProfileJson(profileFile: File): JSONObject? {
        if (!profileFile.isFile) return null
        return try {
            JSONObject(profileFile.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "AAC_BOOTSTRAP_PROFILE_READ_FAILED file=${profileFile.name}", error)
            null
        }
    }

    private fun saveDomProfileDebug(
        context: Context,
        profileFile: File?,
        profileFileExists: Boolean,
        profileType: String,
        domProfileFound: Boolean,
        domProfileId: String,
        itemIdsBefore: Int,
        itemIdsAfter: Int
    ) {
        context.getSharedPreferences(DEBUG_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEBUG_PROFILE_FILE_PATH, profileFile?.absolutePath.orEmpty())
            .putBoolean(KEY_DEBUG_PROFILE_FILE_EXISTS, profileFileExists)
            .putString(KEY_DEBUG_PROFILE_TYPE, profileType)
            .putBoolean(KEY_DEBUG_DOM_PROFILE_FOUND, domProfileFound)
            .putString(KEY_DEBUG_DOM_PROFILE_ID, domProfileId)
            .putInt(KEY_DEBUG_ITEM_IDS_BEFORE, itemIdsBefore)
            .putInt(KEY_DEBUG_ITEM_IDS_AFTER, itemIdsAfter)
            .apply()
        Log.d(
            TAG,
            "DOM_PROFILE_DEBUG file=${profileFile?.absolutePath.orEmpty()} exists=$profileFileExists type=$profileType found=$domProfileFound id=$domProfileId before=$itemIdsBefore after=$itemIdsAfter"
        )
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun isSafePageId(pageId: String): Boolean {
        return pageId.isNotBlank() && pageId.matches(Regex("[A-Za-z0-9_-]+"))
    }

    private fun AacItem.toBootstrapJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("labelSl", labelSl)
            .put("imagePath", imagePath)
            .put("audioSl", audioSl)
            .put("actionType", actionType)
            .put("targetPageId", targetPageId)
            .put("speakTextSl", speakTextSl ?: resolvedSpeechText)
            .put("speechText", speechText ?: resolvedSpeechText)
            .put("iconSource", iconSource.name)
            .put("isRootItem", isRootItem)
            .put("isHiddenUntilParent", isHiddenUntilParent)
            .put("addsToSentence", addsToSentence)
            .put("speaksImmediately", speaksImmediately)
            .put("opensSubicons", opensSubicons)
            .put("priority", priority)
            .also { json ->
                labelUk?.let { json.put("labelUk", it) }
                labelEn?.let { json.put("labelEn", it) }
                speakTextUk?.let { json.put("speakTextUk", it) }
                speechTextEn?.let { json.put("speechTextEn", it) }
                categoryId?.let { json.put("categoryId", it) }
                meaning?.let { json.put("meaning", it) }
                meaningId?.let { json.put("meaningId", it) }
                meaningType?.let { json.put("meaningType", it) }
                meaningGroup?.let { json.put("meaningGroup", it) }
                if (semanticTags.isNotEmpty()) json.put("semanticTags", jsonArrayOf(semanticTags))
                if (searchKeywordsByLanguage.isNotEmpty()) {
                    json.put("searchKeywordsByLanguage", JSONObject().apply {
                        searchKeywordsByLanguage.forEach { (languageCode, keywords) ->
                            if (languageCode.isNotBlank() && keywords.isNotEmpty()) {
                                put(languageCode, jsonArrayOf(keywords))
                            }
                        }
                    })
                }
                if (scenarioIds.isNotEmpty()) json.put("scenarioIds", jsonArrayOf(scenarioIds))
                conceptId?.let { json.put("conceptId", it) }
                parentId?.let { json.put("parentId", it) }
                fixedTopRowPosition?.let { json.put("fixedTopRowPosition", it) }
                if (children.isNotEmpty()) json.put("children", jsonArrayOf(children))
                if (visibleUnderIds.isNotEmpty()) json.put("visibleUnderIds", jsonArrayOf(visibleUnderIds))
                if (questionByLanguage.isNotEmpty()) json.put("questionByLanguage", JSONObject(questionByLanguage))
                if (labelByLanguage.isNotEmpty()) json.put("labelByLanguage", JSONObject(labelByLanguage))
                if (speechTextByLanguage.isNotEmpty()) json.put("speechTextByLanguage", JSONObject(speechTextByLanguage))
                if (locked) json.put("locked", true)
                if (userEdited) json.put("userEdited", true)
                if (placements.isNotEmpty()) {
                    json.put("placements", JSONArray().apply {
                        placements.forEach { placement ->
                            put(JSONObject().put("pageId", placement.pageId).put("position5x5", placement.position5x5))
                        }
                    })
                }
            }
    }

    private data class FoodChildRepair(
        val id: String,
        val labelSl: String,
        val labelUk: String,
        val labelEn: String,
        val speakTextSl: String,
        val speakTextUk: String,
        val speechTextEn: String,
        val parentId: String = "food"
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put("id", id)
                .put("labelSl", labelSl)
                .put("labelUk", labelUk)
                .put("labelEn", labelEn)
                .put("text", labelSl)
                .put("baseText", labelSl)
                .put("speechText", speakTextSl)
                .put("speakTextSl", speakTextSl)
                .put("speakTextUk", speakTextUk)
                .put("speechTextEn", speechTextEn)
                .put("imagePath", "")
                .put("iconSource", IconSource.SYSTEM.name)
                .put("actionType", "speak")
                .put("targetPageId", "")
                .put("conceptId", id)
                .put("parentId", parentId)
                .put("visibleUnderIds", JSONArray().put(parentId))
                .put("isRootItem", false)
                .put("isHiddenUntilParent", true)
                .put("addsToSentence", true)
                .put("speaksImmediately", true)
                .put("opensSubicons", false)
        }

        fun applyTo(item: JSONObject): Int {
            var repaired = 0
            val legacySpeechSlValues = legacyValues(labelSl, labelEn, id, speakTextSl)
            val legacySpeechUkValues = legacyValues(labelUk, "", id, speakTextUk)
            val legacySpeechEnValues = legacyValues(labelEn, "", id, speechTextEn)
            repaired += putIfBlankOrLegacy(item, "speechText", speakTextSl, legacySpeechSlValues)
            repaired += putIfBlankOrLegacy(item, "speakTextSl", speakTextSl, legacySpeechSlValues)
            repaired += putIfBlank(item, "labelUk", labelUk)
            repaired += putIfBlank(item, "labelEn", labelEn)
            repaired += putIfBlankOrLegacy(item, "speakTextUk", speakTextUk, legacySpeechUkValues)
            repaired += putIfBlankOrLegacy(item, "speechTextEn", speechTextEn, legacySpeechEnValues)
            val speechTextByLanguage = item.optJSONObject("speechTextByLanguage") ?: JSONObject()
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "sl", speakTextSl, legacySpeechSlValues)
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "uk", speakTextUk, legacySpeechUkValues)
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "en", speechTextEn, legacySpeechEnValues)
            item.put("speechTextByLanguage", speechTextByLanguage)
            repaired += putIfBlank(item, "parentId", parentId)
            if (!hasJsonArrayValue(item.optJSONArray("visibleUnderIds"), parentId)) {
                val visibleUnderIds = item.optJSONArray("visibleUnderIds") ?: JSONArray()
                visibleUnderIds.put(parentId)
                item.put("visibleUnderIds", visibleUnderIds)
                repaired++
            }
            if (item.optBoolean("isRootItem", true)) {
                item.put("isRootItem", false)
                repaired++
            }
            if (!item.optBoolean("isHiddenUntilParent", false)) {
                item.put("isHiddenUntilParent", true)
                repaired++
            }
            if (!item.optBoolean("addsToSentence", true)) {
                item.put("addsToSentence", true)
                repaired++
            }
            if (!item.optBoolean("speaksImmediately", true)) {
                item.put("speaksImmediately", true)
                repaired++
            }
            if (item.optBoolean("opensSubicons", false)) {
                item.put("opensSubicons", false)
                repaired++
            }
            if (item.optString("actionType").isBlank() || item.optString("actionType") == "open_subicons") {
                item.put("actionType", "speak")
                repaired++
            }
            return repaired
        }

        private fun putIfBlank(item: JSONObject, key: String, value: String): Int {
            return if (item.optString(key).trim().isBlank()) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putIfBlankOrLegacy(item: JSONObject, key: String, value: String, legacyValues: Set<String>): Int {
            val current = item.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putLanguageIfBlankOrLegacy(
            target: JSONObject,
            key: String,
            value: String,
            legacyValues: Set<String>
        ): Int {
            val current = target.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                target.put(key, value)
                1
            } else {
                0
            }
        }

        private fun legacyValues(labelSl: String, labelEn: String, id: String, fullSpeech: String): Set<String> {
            val values = mutableSetOf(labelSl.lowercase(), labelEn.lowercase(), id)
            val normalizedFullSpeech = fullSpeech.trim()
            values += normalizedFullSpeech
                .replace("I want to eat ", "I want ")
                .replace("I want to drink ", "I want ")
                .replace("ĐŻ Ń…ĐľŃ‡Ń Ń—ŃŃ‚Đ¸ ", "ĐŻ Ń…ĐľŃ‡Ń ")
                .replace("ĐŻ Ń…ĐľŃ‡Ń ĐżĐ¸Ń‚Đ¸ ", "ĐŻ Ń…ĐľŃ‡Ń ")
                .trim()
                .lowercase()
            values += normalizedFullSpeech
                .replace("Ĺľelim jesti ", "")
                .replace("Ĺľelim piti ", "")
                .replace("boli me v ", "")
                .replace("boli me ", "")
                .replace("I want to eat ", "")
                .replace("I want to drink ", "")
                .replace("My ", "")
                .replace(" hurts", "")
                .replace("ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ Ń ", "")
                .replace("ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ ", "")
                .replace("ĐŻ Ń…ĐľŃ‡Ń Ń—ŃŃ‚Đ¸ ", "")
                .replace("ĐŻ Ń…ĐľŃ‡Ń ĐżĐ¸Ń‚Đ¸ ", "")
                .trim()
                .lowercase()
            return values.filter { it.isNotBlank() }.toSet()
        }

        private fun hasJsonArrayValue(array: JSONArray?, expected: String): Boolean {
            if (array == null) return false
            for (index in 0 until array.length()) {
                if (array.optString(index).trim() == expected) return true
            }
            return false
        }
    }

    private data class DrinkSpeechRepair(
        val id: String,
        val labelSl: String,
        val speechTextSl: String,
        val parentId: String,
        val labelUk: String = "",
        val speechTextUk: String = ""
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put("id", id)
                .put("labelSl", labelSl)
                .put("text", labelSl)
                .put("baseText", labelSl)
                .put("speechText", speechTextSl)
                .put("speakTextSl", speechTextSl)
                .put(
                    "speechTextByLanguage",
                    JSONObject()
                        .put("sl", speechTextSl)
                        .apply {
                            if (speechTextUk.isNotBlank()) put("uk", speechTextUk)
                        }
                )
                .put("imagePath", "")
                .put("iconSource", IconSource.SYSTEM.name)
                .put("actionType", "speak")
                .put("targetPageId", "")
                .put("conceptId", id)
                .put("parentId", parentId)
                .put("visibleUnderIds", JSONArray().put(parentId))
                .put("isRootItem", false)
                .put("isHiddenUntilParent", true)
                .put("addsToSentence", true)
                .put("speaksImmediately", true)
                .put("opensSubicons", false)
        }

        fun applyTo(item: JSONObject): Int {
            var repaired = 0
            val legacySpeechSlValues = legacyValues(labelSl, "", id, speechTextSl)
            repaired += putIfBlankOrLegacy(item, "labelSl", labelSl, legacyValues(labelSl, "", id, labelSl))
            repaired += putIfBlankOrLegacy(item, "text", labelSl, legacyValues(labelSl, "", id, labelSl))
            repaired += putIfBlankOrLegacy(item, "baseText", labelSl, legacyValues(labelSl, "", id, labelSl))
            repaired += putIfBlankOrLegacy(item, "speechText", speechTextSl, legacySpeechSlValues)
            repaired += putIfBlankOrLegacy(item, "speakTextSl", speechTextSl, legacySpeechSlValues)
            val speechTextByLanguage = item.optJSONObject("speechTextByLanguage") ?: JSONObject()
            repaired += putLanguageIfBlankOrLegacy(speechTextByLanguage, "sl", speechTextSl, legacySpeechSlValues)
            if (speechTextUk.isNotBlank()) {
                repaired += putLanguageIfBlankOrLegacy(
                    speechTextByLanguage,
                    "uk",
                    speechTextUk,
                    legacyValues(labelUk, "", id, speechTextUk)
                )
            }
            item.put("speechTextByLanguage", speechTextByLanguage)
            if (labelUk.isNotBlank()) {
                val labelByLanguage = item.optJSONObject("labelByLanguage") ?: JSONObject()
                repaired += putLanguageIfBlankOrLegacy(
                    labelByLanguage,
                    "uk",
                    labelUk,
                    legacyValues(labelUk, "", id, labelUk)
                )
                item.put("labelByLanguage", labelByLanguage)
            }
            repaired += putIfBlank(item, "parentId", parentId)
            if (!hasJsonArrayValue(item.optJSONArray("visibleUnderIds"), parentId)) {
                val visibleUnderIds = item.optJSONArray("visibleUnderIds") ?: JSONArray()
                visibleUnderIds.put(parentId)
                item.put("visibleUnderIds", visibleUnderIds)
                repaired++
            }
            if (item.optBoolean("isRootItem", true)) {
                item.put("isRootItem", false)
                repaired++
            }
            if (!item.optBoolean("isHiddenUntilParent", false)) {
                item.put("isHiddenUntilParent", true)
                repaired++
            }
            if (!item.optBoolean("addsToSentence", true)) {
                item.put("addsToSentence", true)
                repaired++
            }
            if (!item.optBoolean("speaksImmediately", true)) {
                item.put("speaksImmediately", true)
                repaired++
            }
            if (item.optBoolean("opensSubicons", false)) {
                item.put("opensSubicons", false)
                repaired++
            }
            if (item.optString("actionType").isBlank() || item.optString("actionType") == "open_subicons") {
                item.put("actionType", "speak")
                repaired++
            }
            return repaired
        }

        private fun putIfBlank(item: JSONObject, key: String, value: String): Int {
            return if (item.optString(key).trim().isBlank()) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putIfBlankOrLegacy(item: JSONObject, key: String, value: String, legacyValues: Set<String>): Int {
            val current = item.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                item.put(key, value)
                1
            } else {
                0
            }
        }

        private fun putLanguageIfBlankOrLegacy(
            target: JSONObject,
            key: String,
            value: String,
            legacyValues: Set<String>
        ): Int {
            val current = target.optString(key).trim()
            return if (current.isBlank() || current.lowercase() in legacyValues) {
                target.put(key, value)
                1
            } else {
                0
            }
        }

        private fun legacyValues(labelSl: String, labelEn: String, id: String, fullSpeech: String): Set<String> {
            val values = mutableSetOf(labelSl.lowercase(), labelEn.lowercase(), id)
            val normalizedFullSpeech = fullSpeech.trim()
            values += normalizedFullSpeech
            values += normalizedFullSpeech
                .replace("Prosim, rada bi ", "")
                .replace("Rada bi ", "")
                .trim()
                .lowercase()
            return values.filter { it.isNotBlank() }.toSet()
        }

        private fun hasJsonArrayValue(array: JSONArray?, expected: String): Boolean {
            if (array == null) return false
            for (index in 0 until array.length()) {
                if (array.optString(index).trim() == expected) return true
            }
            return false
        }
    }

    private fun putLanguageIfBlankOrLegacy(target: JSONObject, key: String, value: String, legacyValues: Set<String>): Int {
        val current = target.optString(key).trim()
        return if (current.isBlank() || current.lowercase() in legacyValues) {
            target.put(key, value)
            1
        } else {
            0
        }
    }

    private val DRINK_TREE_CHILDREN = mapOf(
        "thirsty" to listOf("water", "tea", "coffee", "juice", "sparkling_drink", "milk_drinks"),
        "drink" to listOf("water", "tea", "coffee", "juice", "sparkling_drink", "milk_drinks"),
        "water" to listOf("non_sparkling_water", "flavored_water", "mineral_water", "cold_water"),
        "tea" to listOf("tea_chamomile", "tea_fruit", "tea_green", "tea_black", "tea_mint", "tea_rosehip"),
        "tea_chamomile" to listOf("tea_chamomile_lemon", "tea_chamomile_honey", "tea_chamomile_honey_lemon"),
        "tea_fruit" to listOf("tea_fruit_lemon", "tea_fruit_honey", "tea_fruit_honey_lemon"),
        "tea_green" to listOf("tea_green_lemon", "tea_green_honey", "tea_green_honey_lemon"),
        "tea_black" to listOf("tea_black_lemon", "tea_black_honey", "tea_black_honey_lemon"),
        "tea_mint" to listOf("tea_mint_lemon", "tea_mint_honey", "tea_mint_honey_lemon"),
        "tea_rosehip" to listOf("tea_rosehip_lemon", "tea_rosehip_honey", "tea_rosehip_honey_lemon"),
        "coffee" to listOf("coffee_plain", "coffee_milk", "coffee_no_sugar"),
        "juice" to listOf("orange_juice", "apple_juice", "blueberry_juice", "strawberry_juice", "cedevita"),
        "sparkling_drink" to listOf("drink_fanta", "drink_coca_cola", "drink_pepsi", "radenska"),
        "milk_drinks" to listOf("drink_yogurt", "cocoa_drink", "drink_milk", "chocolate_milk")
    )

    private val DRINK_BRANCH_QUESTIONS = mapOf(
        "thirsty" to "Izberi, kaj želiš piti.",
        "drink" to "Izberi, kaj želiš piti.",
        "water" to "Kakšno vodo?",
        "tea" to "Kakšen čaj?",
        "tea_chamomile" to "Kaj dodaš v kamilični čaj?",
        "tea_fruit" to "Kaj dodaš v sadni čaj?",
        "tea_green" to "Kaj dodaš v zeleni čaj?",
        "tea_black" to "Kaj dodaš v črni čaj?",
        "tea_mint" to "Kaj dodaš v metin čaj?",
        "tea_rosehip" to "Kaj dodaš v šipkov čaj?",
        "coffee" to "Kakšno kavo?",
        "juice" to "Kakšen sok?",
        "sparkling_drink" to "Katero gazirano pijačo?",
        "milk_drinks" to "Kateri mlečni napitek?"
    )

    private val DRINK_SPEECH_REPAIRS = listOf(
        DrinkSpeechRepair("non_sparkling_water", "NAVADNA", "Rada bi negazirano vodo.", "water", "НЕГАЗОВАНА ВОДА", "Я хочу негазованої води."),
        DrinkSpeechRepair("flavored_water", "VODA Z OKUSOM", "Rada bi vodo z okusom.", "water", "ВОДА ЗІ СМАКОМ", "Я хочу воду зі смаком."),
        DrinkSpeechRepair("mineral_water", "MINERALNA", "Rada bi mineralno vodo.", "water", "ГАЗОВАНА ВОДА", "Я хочу газованої води."),
        DrinkSpeechRepair("cold_water", "HLADNA", "Rada bi mrzlo vodo.", "water", "ХОЛОДНА ВОДА", "Я хочу холодної води."),
        DrinkSpeechRepair("tea_chamomile_lemon", "Z LIMONO", "Prosim, rada bi kamilični čaj z limono.", "tea_chamomile"),
        DrinkSpeechRepair("tea_chamomile_honey", "Z MEDOM", "Prosim, rada bi kamilični čaj z medom.", "tea_chamomile"),
        DrinkSpeechRepair("tea_chamomile_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi kamilični čaj z medom in limono.", "tea_chamomile"),
        DrinkSpeechRepair("tea_fruit_lemon", "Z LIMONO", "Prosim, rada bi sadni čaj z limono.", "tea_fruit"),
        DrinkSpeechRepair("tea_fruit_honey", "Z MEDOM", "Prosim, rada bi sadni čaj z medom.", "tea_fruit"),
        DrinkSpeechRepair("tea_fruit_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi sadni čaj z medom in limono.", "tea_fruit"),
        DrinkSpeechRepair("tea_green_lemon", "Z LIMONO", "Prosim, rada bi zeleni čaj z limono.", "tea_green"),
        DrinkSpeechRepair("tea_green_honey", "Z MEDOM", "Prosim, rada bi zeleni čaj z medom.", "tea_green"),
        DrinkSpeechRepair("tea_green_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi zeleni čaj z medom in limono.", "tea_green"),
        DrinkSpeechRepair("tea_black_lemon", "Z LIMONO", "Prosim, rada bi črni čaj z limono.", "tea_black"),
        DrinkSpeechRepair("tea_black_honey", "Z MEDOM", "Prosim, rada bi črni čaj z medom.", "tea_black"),
        DrinkSpeechRepair("tea_black_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi črni čaj z medom in limono.", "tea_black"),
        DrinkSpeechRepair("tea_mint_lemon", "Z LIMONO", "Prosim, rada bi metin čaj z limono.", "tea_mint"),
        DrinkSpeechRepair("tea_mint_honey", "Z MEDOM", "Prosim, rada bi metin čaj z medom.", "tea_mint"),
        DrinkSpeechRepair("tea_mint_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi metin čaj z medom in limono.", "tea_mint"),
        DrinkSpeechRepair("tea_rosehip_lemon", "Z LIMONO", "Prosim, rada bi šipkov čaj z limono.", "tea_rosehip"),
        DrinkSpeechRepair("tea_rosehip_honey", "Z MEDOM", "Prosim, rada bi šipkov čaj z medom.", "tea_rosehip"),
        DrinkSpeechRepair("tea_rosehip_honey_lemon", "Z MEDOM IN LIMONO", "Prosim, rada bi šipkov čaj z medom in limono.", "tea_rosehip"),
        DrinkSpeechRepair("coffee_plain", "NAVADNA", "Prosim, rada bi navadno kavo.", "coffee"),
        DrinkSpeechRepair("coffee_milk", "Z MLEKOM", "Prosim, rada bi kavo z mlekom.", "coffee"),
        DrinkSpeechRepair("coffee_no_sugar", "BREZ SLADKORJA", "Prosim, rada bi kavo brez sladkorja.", "coffee"),
        DrinkSpeechRepair("orange_juice", "POMARANČNI", "Rada bi pomarančni sok.", "juice", "АПЕЛЬСИНОВИЙ СІК", "Я хочу апельсиновий сік."),
        DrinkSpeechRepair("apple_juice", "JABOLČNI", "Rada bi jabolčni sok.", "juice", "ЯБЛУЧНИЙ СІК", "Я хочу яблучний сік."),
        DrinkSpeechRepair("blueberry_juice", "BOROVNIČEV", "Rada bi sok iz borovnic.", "juice", "ЧОРНИЧНИЙ СІК", "Я хочу чорничний сік."),
        DrinkSpeechRepair("strawberry_juice", "JAGODNI", "Rada bi jagodni sok.", "juice", "ПОЛУНИЧНИЙ СІК", "Я хочу полуничний сік."),
        DrinkSpeechRepair("cedevita", "CEDEVITA", "Rada bi Cedevito.", "juice", "ЦЕДЕВІТА", "Я хочу Цедевіту."),
        DrinkSpeechRepair("drink_fanta", "FANTA", "Prosim, rada bi Fanto.", "sparkling_drink"),
        DrinkSpeechRepair("drink_coca_cola", "COCA COLA", "Prosim, rada bi Coca-Colo.", "sparkling_drink"),
        DrinkSpeechRepair("drink_pepsi", "PEPSI", "Prosim, rada bi Pepsi.", "sparkling_drink"),
        DrinkSpeechRepair("radenska", "RADENSKA", "Rada bi Radensko.", "sparkling_drink", "РАДЕНСЬКА", "Я хочу Раденську."),
        DrinkSpeechRepair("drink_yogurt", "JOGURT", "Prosim, rada bi jogurt.", "milk_drinks"),
        DrinkSpeechRepair("cocoa_drink", "KAKAV", "Prosim, rada bi kakav.", "milk_drinks"),
        DrinkSpeechRepair("drink_milk", "MLEKO", "Prosim, rada bi mleko.", "milk_drinks"),
        DrinkSpeechRepair("chocolate_milk", "ČOKOLADNO MLEKO", "Prosim, rada bi čokoladno mleko.", "milk_drinks")
    )

    private val FOOD_CHILD_REPAIRS = listOf(
        FoodChildRepair(
            id = "soup",
            labelSl = "JUHA",
            labelUk = "ĐˇĐŁĐź",
            labelEn = "SOUP",
            speakTextSl = "Ĺľelim jesti juho",
            speakTextUk = "ĐŻ Ń…ĐľŃ‡Ń Ń—ŃŃ‚Đ¸ ŃŃĐż",
            speechTextEn = "I want to eat soup"
        ),
        FoodChildRepair(
            id = "bread",
            labelSl = "KRUH",
            labelUk = "ĐĄĐ›Đ†Đ‘",
            labelEn = "BREAD",
            speakTextSl = "Ĺľelim jesti kruh",
            speakTextUk = "ĐŻ Ń…ĐľŃ‡Ń Ń—ŃŃ‚Đ¸ Ń…Đ»Ń–Đ±",
            speechTextEn = "I want to eat bread"
        ),
        FoodChildRepair(
            id = "fruit",
            labelSl = "SADJE",
            labelUk = "Đ¤Đ ĐŁĐšĐ˘Đ",
            labelEn = "FRUIT",
            speakTextSl = "Ĺľelim jesti sadje",
            speakTextUk = "ĐŻ Ń…ĐľŃ‡Ń Ń—ŃŃ‚Đ¸ Ń„Ń€ŃĐşŃ‚Đ¸",
            speechTextEn = "I want to eat fruit"
        )
    )

    private val PAIN_CHILD_REPAIRS = listOf(
        FoodChildRepair(
            id = "head",
            labelSl = "GLAVA",
            labelUk = "Đ“ĐžĐ›ĐžĐ’Đ",
            labelEn = "HEAD",
            speakTextSl = "boli me glava",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ ĐłĐľĐ»ĐľĐ˛Đ°",
            speechTextEn = "My head hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "arm",
            labelSl = "ROKA",
            labelUk = "Đ ĐŁĐšĐ",
            labelEn = "ARM",
            speakTextSl = "boli me roka",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ Ń€ŃĐşĐ°",
            speechTextEn = "My arm hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "leg",
            labelSl = "NOGA",
            labelUk = "ĐťĐžĐ“Đ",
            labelEn = "LEG",
            speakTextSl = "boli me noga",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ Đ˝ĐľĐłĐ°",
            speechTextEn = "My leg hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "belly",
            labelSl = "TREBUH",
            labelUk = "Đ–ĐĐ’Đ†Đ˘",
            labelEn = "BELLY",
            speakTextSl = "boli me trebuh",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ Đ¶Đ¸Đ˛Ń–Ń‚",
            speechTextEn = "My stomach hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "back",
            labelSl = "HRBET",
            labelUk = "ĐˇĐźĐĐťĐ",
            labelEn = "BACK",
            speakTextSl = "boli me hrbet",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ ŃĐżĐ¸Đ˝Đ°",
            speechTextEn = "My back hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "chest",
            labelSl = "PRSI",
            labelUk = "Đ“Đ ĐŁĐ”Đ",
            labelEn = "CHEST",
            speakTextSl = "boli me v prsih",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ Ń ĐłŃ€ŃĐ´ŃŹŃ…",
            speechTextEn = "My chest hurts",
            parentId = "pain"
        ),
        FoodChildRepair(
            id = "throat",
            labelSl = "GRLO",
            labelUk = "Đ“ĐžĐ Đ›Đž",
            labelEn = "THROAT",
            speakTextSl = "boli me grlo",
            speakTextUk = "ĐŁ ĐĽĐµĐ˝Đµ Đ±ĐľĐ»Đ¸Ń‚ŃŚ ĐłĐľŃ€Đ»Đľ",
            speechTextEn = "My throat hurts",
            parentId = "pain"
        )
    )

    private val PAIN_GUIDED_NODE_REPAIRS = listOf(
        Triple("head", painStrengthChildren(), "Kako močno boli?"),
        Triple("left_arm", painArmDetailChildren(), "Kje na levi roki?"),
        Triple("right_arm", painArmDetailChildren(), "Kje na desni roki?"),
        Triple("left_leg", painLegDetailChildren(), "Kje na levi nogi?"),
        Triple("right_leg", painLegDetailChildren(), "Kje na desni nogi?"),
        Triple("back", painBackDetailChildren(), "Kje boli?"),
        Triple("belly", painBellyDetailChildren(), "Kje boli?"),
        Triple("chest", painStrengthChildren(), "Kako močno boli?"),
        Triple("neck", painStrengthChildren(), "Kako močno boli?"),
        Triple("eye", listOf("eye_left", "eye_right", "eye_both"), "Katero oko?"),
        Triple("ear", listOf("ear_left", "ear_right", "ear_both"), "Katero uho?"),
        Triple("nose", painStrengthChildren(), "Kako močno boli?"),
        Triple("mouth", painStrengthChildren(), "Kako močno boli?"),
        Triple("tooth", listOf("tooth_left", "tooth_right", "tooth_upper", "tooth_lower"), "Kateri zob?"),
        Triple("arm_shoulder", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_upper", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_elbow", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_forearm", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_wrist", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_palm", painStrengthChildren(), "Kako močno boli?"),
        Triple("arm_fingers", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_hip", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_thigh", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_knee", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_shin", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_ankle", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_foot", painStrengthChildren(), "Kako močno boli?"),
        Triple("leg_toes", painStrengthChildren(), "Kako močno boli?"),
        Triple("back_upper", painStrengthChildren(), "Kako močno boli?"),
        Triple("back_middle", painStrengthChildren(), "Kako močno boli?"),
        Triple("back_lower", painStrengthChildren(), "Kako močno boli?"),
        Triple("belly_left", painStrengthChildren(), "Kako močno boli?"),
        Triple("belly_right", painStrengthChildren(), "Kako močno boli?"),
        Triple("belly_upper", painStrengthChildren(), "Kako močno boli?"),
        Triple("belly_lower", painStrengthChildren(), "Kako močno boli?"),
        Triple("eye_left", painStrengthChildren(), "Kako močno boli?"),
        Triple("eye_right", painStrengthChildren(), "Kako močno boli?"),
        Triple("eye_both", painStrengthChildren(), "Kako močno boli?"),
        Triple("ear_left", painStrengthChildren(), "Kako močno boli?"),
        Triple("ear_right", painStrengthChildren(), "Kako močno boli?"),
        Triple("ear_both", painStrengthChildren(), "Kako močno boli?"),
        Triple("tooth_left", painStrengthChildren(), "Kako močno boli?"),
        Triple("tooth_right", painStrengthChildren(), "Kako močno boli?"),
        Triple("tooth_upper", painStrengthChildren(), "Kako močno boli?"),
        Triple("tooth_lower", painStrengthChildren(), "Kako močno boli?"),
        Triple("pain_light", painTimeChildren(), "Od kdaj boli?"),
        Triple("pain_medium", painTimeChildren(), "Od kdaj boli?"),
        Triple("pain_strong", painTimeChildren(), "Od kdaj boli?"),
        Triple("pain_very_strong", painTimeChildren(), "Od kdaj boli?")
    )

    private fun painSideStrengthAndTimeChildren(): List<String> {
        return listOf(
            "pain_left",
            "pain_right",
            "pain_both",
            "pain_light",
            "pain_medium",
            "pain_strong",
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening",
            "pain_since_long"
        )
    }

    private fun painRootChildren(): List<String> {
        return listOf(
            "head",
            "left_arm",
            "right_arm",
            "left_leg",
            "right_leg",
            "back",
            "belly",
            "chest",
            "eye",
            "ear",
            "nose",
            "mouth",
            "tooth",
            "neck"
        )
    }

    private fun painArmDetailChildren(): List<String> {
        return listOf(
            "arm_shoulder",
            "arm_upper",
            "arm_elbow",
            "arm_forearm",
            "arm_wrist",
            "arm_palm",
            "arm_fingers"
        )
    }

    private fun painLegDetailChildren(): List<String> {
        return listOf(
            "leg_hip",
            "leg_thigh",
            "leg_knee",
            "leg_shin",
            "leg_ankle",
            "leg_foot",
            "leg_toes"
        )
    }

    private fun painBackDetailChildren(): List<String> {
        return listOf(
            "back_upper",
            "back_middle",
            "back_lower"
        )
    }

    private fun painBellyDetailChildren(): List<String> {
        return listOf(
            "belly_left",
            "belly_right",
            "belly_upper",
            "belly_lower"
        )
    }

    private fun painStrengthChildren(): List<String> {
        return listOf(
            "pain_light",
            "pain_medium",
            "pain_strong",
            "pain_very_strong"
        )
    }

    private fun painStrengthAndTimeChildren(): List<String> {
        return listOf(
            "pain_light",
            "pain_medium",
            "pain_strong",
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening"
        )
    }

    private fun painTimeChildren(): List<String> {
        return listOf(
            "pain_since_today",
            "pain_since_yesterday",
            "pain_since_morning",
            "pain_since_evening",
            "pain_since_long"
        )
    }

    private data class RawItemsJson(
        val rootObject: JSONObject?,
        val rootArray: JSONArray?,
        val itemsArray: JSONArray,
        val createdFromFallback: Boolean
    )

    private data class ProfileBootstrapResult(
        val linkedItemCount: Int,
        val updated: Boolean
    )
}

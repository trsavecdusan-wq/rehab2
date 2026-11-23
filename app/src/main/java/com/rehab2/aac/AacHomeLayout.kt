package com.rehab2.aac

import android.content.Context
import java.io.File

/** Versioned presentation overlay. Never rewrites patient items, links, pages or pictures. */
object AacHomeLayout {
    val ids = listOf("yes", "no", "thank_you", "understand", "dont_understand",
        "wc", "pain", "cold", "bad", "tired", "thirsty", "hungry", "hot", "good", "sleepy",
        "family_group", "friends_group", "wait", "slower", "faster", "need", "i_want",
        "correct_message", "lets_go", "angry")
    val labels = listOf("DA", "NE", "HVALA", "RAZUMEM", "NE RAZUMEM", "TOALETA", "BOLI ME",
        "MRZLO", "SLABO", "UTRUJENA", "PIJAČA", "HRANA", "VROČE", "DOBRO", "SPATI",
        "DRUŽINA", "PRIJATELJI", "POČAKAJ", "POČASNEJE", "HITREJE", "RABIM", "ŽELIM",
        "POPRAVI", "GREMO", "JEZNA")
    val ukLabels = listOf("ТАК", "НІ", "ДЯКУЮ", "РОЗУМІЮ", "НЕ РОЗУМІЮ", "ТУАЛЕТ", "БОЛИТЬ",
        "ХОЛОДНО", "ПОГАНО", "ВТОМЛЕНА", "НАПОЇ", "ЇЖА", "СПЕКОТНО", "ДОБРЕ", "СПАТИ",
        "РОДИНА", "ДРУЗІ", "ЗАЧЕКАЙ", "ПОВІЛЬНІШЕ", "ШВИДШЕ", "ПОТРІБНО", "ХОЧУ",
        "ВИПРАВ", "ХОДІМО", "СЕРДИТА")
    private val speech = mapOf("need" to "Nekaj potrebujem.", "i_want" to "Nekaj želim.",
        "thirsty" to "Želim piti.", "hungry" to "Želim jesti.", "sleepy" to "Rada bi spala.")
    private val ukSpeech = mapOf("need" to "Мені дещо потрібно.", "i_want" to "Я дещо хочу.",
        "thirsty" to "Я хочу пити.", "hungry" to "Я хочу їсти.", "sleepy" to "Я хочу спати.",
        "understand" to "Я розумію.")
    private fun key(context: Context) = "reviewed_home_824_" + AacPatientImages.store(context).currentPatientId()
    fun enabled(context: Context): Boolean = runCatching {
        AacGridV4.enabled(context) || context.getSharedPreferences("aac_home_layout", Context.MODE_PRIVATE).getBoolean(key(context), true)
    }.getOrDefault(false)
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("aac_home_layout", Context.MODE_PRIVATE).edit().putBoolean(key(context), value).commit()
        initialize(context)
    }
    fun initialize(context: Context) {
        AacFixedTopRow.reviewedLayout = enabled(context)
    }
    fun backup(context: Context): Boolean = runCatching {
        val store = AacPatientImages.store(context)
        store.backupMigrationOnce(store.currentPatientId(), "home-824-presentation",
            AacStoragePaths.getAacItemsFile(context), AacStoragePaths.getPatientProfileFile(context),
            AacStoragePaths.getProfilesDataDir(context)?.let { File(it, "dom.json") },
            File(context.applicationInfo.dataDir, "shared_prefs/aac_patient_pages.xml"))
        true
    }.getOrDefault(false)

    fun additions(): List<AacItem> = listOf(
        extra("faster", "HITREJE", "Prosim, hitreje.", "ШВИДШЕ", "Будь ласка, швидше."),
        extra("correct_message", "POPRAVI", "Prosim, popravi to.", "ВИПРАВ", "Будь ласка, виправ це."),
        extra("lets_go", "GREMO", "Gremo, prosim.", "ХОДІМО", "Ходімо, будь ласка.")
    )
    private fun extra(id: String, label: String, sl: String, uk: String, ukText: String) = AacItem(
        id = id, labelSl = label, imagePath = "system/home824_$id.png", actionType = "speak", targetPageId = "",
        speakTextSl = sl, speechText = sl, speakTextUk = ukText, labelUk = uk,
        labelByLanguage = mapOf("sl" to label, "uk" to uk), speechTextByLanguage = mapOf("sl" to sl, "uk" to ukText),
        conceptId = id, sentenceRole = "quick", speaksImmediately = true, isRootItem = true)

    fun catalog(context: Context, stored: Collection<AacItem>): List<AacItem> {
        if (!enabled(context)) return stored.toList()
        return AacGridV4.apply(context, (AacStarterContentV1.items() + stored).associateBy { it.id }.values.map(::decorate))
    }
    fun decorate(item: AacItem): AacItem {
        val index = ids.indexOf(item.id)
        if (index < 0) return item
        val custom = item.userEdited || item.locked || item.translationManualOverride
        val sl = if (custom) null else speech[item.id]
        val uk = if (custom) null else ukSpeech[item.id]
        return item.copy(labelSl = labels[index], labelUk = ukLabels[index],
            labelByLanguage = item.labelByLanguage + mapOf("sl" to labels[index], "uk" to ukLabels[index]),
            speakTextSl = sl ?: item.speakTextSl, speechText = sl ?: item.speechText,
            speakTextUk = uk ?: item.speakTextUk,
            speechTextByLanguage = item.speechTextByLanguage + listOfNotNull(sl?.let { "sl" to it }, uk?.let { "uk" to it }).toMap(),
            fixedTopRowPosition = (index + 1).takeIf { it <= 5 },
            conceptId = item.conceptId ?: item.id, isRootItem = true, isHiddenUntilParent = false)
    }
    fun ordered(catalog: Collection<AacItem>): List<AacItem> {
        val byId = catalog.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.let(::decorate) }
    }
    /** Extra existing roots stay reachable on page two; no extra concepts enter page one. */
    fun homeItems(catalog: Collection<AacItem>): List<AacItem> = ordered(catalog) +
        catalog.filter { it.id !in ids && (it.isRootItem || it.id == "help" || it.placements.isNotEmpty()) }
            .distinctBy { it.id }

    fun systemImage(id: String): String? = when (id) {
        "faster", "correct_message", "lets_go", "understand", "hungry", "need" -> "system/home824_$id.png"
        else -> AacAuditedIcons.defaultPaths[id]
    }
}

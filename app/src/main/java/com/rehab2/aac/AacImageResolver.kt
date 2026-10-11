package com.rehab2.aac

import java.io.File

/** Shared by patient screens, admin preview and future AAC surfaces. No bitmap cache. */
object AacImageResolver {
    private val genderRelevant = mapOf("sleep" to "action_sleep", "tired" to "state_tired",
        "pain" to "health_general_pain", "cannot_breathe" to "health_breathing_distress", "chest_pain" to "health_chest_pain")

    fun resolve(item: AacItem, patientId: String, visualSex: PatientGender, legacyOwner: String,
        manual: () -> File?, generated: () -> File?, system: (String) -> File?,
        neutralPath: String, valid: (File) -> Boolean, scopedManual: () -> File? = { null }): File? {
        scopedManual()?.takeIf(valid)?.let { return it }
        // The pre-existing single manual override keeps precedence (including locked/non-SYSTEM paths).
        val owned = item.imageOwner?.uppercase()?.let { it.isNotBlank() && it != "SYSTEM" } == true ||
            ((item.locked || item.userEdited) && item.imagePath.isNotBlank()) || item.iconSource != IconSource.SYSTEM || (item.imagePath.isNotBlank() && !item.imagePath.startsWith("system/"))
        if (owned && patientId == legacyOwner) manual()?.takeIf(valid)?.let { return it }
        generated()?.takeIf(valid)?.let { return it }
        if (visualSex != PatientGender.NEUTRAL) genderRelevant[item.id]?.let { stem ->
            system("system/"+stem+"_"+visualSex.name.lowercase()+".png")?.takeIf(valid)?.let { return it }
        }
        system(neutralPath)?.takeIf(valid)?.let { return it }
        return system("system/other.png")?.takeIf(valid)
        // Null means the existing visible text/symbol fallback; never hide the tile.
    }
}

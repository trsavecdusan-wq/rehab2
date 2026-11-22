package com.rehab2.patient

import android.content.Context
import com.rehab2.aac.AacStoragePaths
import org.json.JSONObject

object PatientProfileStore {
    fun load(context: Context): PatientProfile {
        com.rehab2.aac.AacPatientImages.store(context)
        val file = AacStoragePaths.getPatientProfileFile(context) ?: return PatientProfile()
        if (!file.isFile) return PatientProfile()

        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            PatientProfile(
                gender = json.optString("gender").trim(),
                paralyzedSide = json.optString("paralyzedSide").trim(),
                dominantHand = json.optString("dominantHand").trim(),
                aphasiaLevel = json.optString("aphasiaLevel").trim(),
                communicationLevel = json.optString("communicationLevel").trim(),
                primaryLanguage = json.optString("primaryLanguage").trim().ifBlank { "sl" },
                secondaryLanguage = json.optString("secondaryLanguage").trim(),
                personalityMode = json.optString("personalityMode").trim()
            )
        } catch (_: Exception) {
            PatientProfile()
        }
    }

    fun save(context: Context, profile: PatientProfile): Boolean {
        val file = AacStoragePaths.getPatientProfileFile(context) ?: return false
        val parent = file.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false

        return try {
            val json = if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
            val updates = profile.toJson()
            updates.keys().forEach { key -> json.put(key, updates.get(key)) }
            file.writeText(json.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun PatientProfile.toJson(): JSONObject {
        return JSONObject()
            .put("gender", gender)
            .put("paralyzedSide", paralyzedSide)
            .put("dominantHand", dominantHand)
            .put("aphasiaLevel", aphasiaLevel)
            .put("communicationLevel", communicationLevel)
            .put("primaryLanguage", primaryLanguage.ifBlank { "sl" })
            .put("secondaryLanguage", secondaryLanguage)
            .put("personalityMode", personalityMode)
    }
}

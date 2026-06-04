package com.rehab2.aac

import android.content.Context
import org.json.JSONObject

data class PatientProfileSettings(
    val firstName: String = "",
    val lastName: String = "",
    val age: String = "",
    val birthDate: String = "",
    val homeTown: String = "",
    val country: String = "",
    val mainLanguage: String = "",
    val caregiverContact: String = "",
    val therapistContact: String = "",
    val shortDescription: String = ""
) {
    companion object {
        const val EMPTY_FIELD_SENTENCE = "Ta podatek \u0161e ni vpisan."

        fun load(context: Context): PatientProfileSettings {
            val file = AacStoragePaths.getPatientProfileFile(context) ?: return PatientProfileSettings()
            if (!file.exists() || !file.isFile) {
                save(context, PatientProfileSettings())
                return PatientProfileSettings()
            }

            return try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                PatientProfileSettings(
                    firstName = json.optString("firstName", ""),
                    lastName = json.optString("lastName", ""),
                    age = json.optString("age", ""),
                    birthDate = json.optString("birthDate", ""),
                    homeTown = json.optString("homeTown", ""),
                    country = json.optString("country", ""),
                    mainLanguage = json.optString("mainLanguage", ""),
                    caregiverContact = json.optString("caregiverContact", ""),
                    therapistContact = json.optString("therapistContact", ""),
                    shortDescription = json.optString("shortDescription", "")
                )
            } catch (_: Exception) {
                PatientProfileSettings()
            }
        }

        fun save(context: Context, settings: PatientProfileSettings): Boolean {
            val file = AacStoragePaths.getPatientProfileFile(context) ?: return false
            return try {
                file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
                val normalized = settings.normalized()
                val json = JSONObject()
                    .put("firstName", normalized.firstName)
                    .put("lastName", normalized.lastName)
                    .put("age", normalized.age)
                    .put("birthDate", normalized.birthDate)
                    .put("homeTown", normalized.homeTown)
                    .put("country", normalized.country)
                    .put("mainLanguage", normalized.mainLanguage)
                    .put("caregiverContact", normalized.caregiverContact)
                    .put("therapistContact", normalized.therapistContact)
                    .put("shortDescription", normalized.shortDescription)
                file.writeText(json.toString(2), Charsets.UTF_8)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun speechForItem(context: Context, itemId: String): String {
            return load(context).speechForItem(itemId)
        }
    }

    fun speechForItem(itemId: String): String {
        val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        return when (itemId) {
            "about_me" -> shortDescription.ifBlank { fullName }.sentenceOrEmpty { it.ensureSentenceEnding() }
            "my_name" -> fullName.ifBlank { "" }.let { if (it.isBlank()) EMPTY_FIELD_SENTENCE else "Ime mi je $it." }
            "my_age" -> age.sentenceOrEmpty { "Stara sem $it let." }
            "my_birth_date" -> birthDate.sentenceOrEmpty { "Rojena sem $it." }
            "my_home" -> homeTown.sentenceOrEmpty { "\u017divim v $it." }
            "my_country" -> country.sentenceOrEmpty { "Sem iz $it." }
            "my_language" -> mainLanguage.sentenceOrEmpty { "Govorim $it." }
            "my_caregiver" -> caregiverContact.sentenceOrEmpty { "Moj skrbnik je $it." }
            "my_therapist" -> therapistContact.sentenceOrEmpty { "Moj terapevt je $it." }
            "call_my_caregiver" -> caregiverContact.sentenceOrEmpty { "Prosim, pokli\u010dite mojega skrbnika: $it." }
            "call_my_therapist" -> therapistContact.sentenceOrEmpty { "Prosim, pokli\u010dite mojega terapevta: $it." }
            else -> EMPTY_FIELD_SENTENCE
        }
    }

    fun speechByItemId(): Map<String, String> {
        return PATIENT_PROFILE_ITEM_IDS.associateWith(::speechForItem)
    }

    private fun normalized(): PatientProfileSettings {
        return copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            age = age.trim(),
            birthDate = birthDate.trim(),
            homeTown = homeTown.trim(),
            country = country.trim(),
            mainLanguage = mainLanguage.trim(),
            caregiverContact = caregiverContact.trim(),
            therapistContact = therapistContact.trim(),
            shortDescription = shortDescription.trim()
        )
    }

    private fun String.sentenceOrEmpty(build: (String) -> String = { it }): String {
        return trim().takeIf { it.isNotBlank() }?.let(build) ?: EMPTY_FIELD_SENTENCE
    }

    private fun String.ensureSentenceEnding(): String {
        val value = trim()
        return if (value.endsWith(".") || value.endsWith("!") || value.endsWith("?")) value else "$value."
    }
}

val PATIENT_PROFILE_ITEM_IDS = listOf(
    "about_me",
    "my_name",
    "my_age",
    "my_birth_date",
    "my_home",
    "my_country",
    "my_language",
    "my_caregiver",
    "my_therapist",
    "call_my_caregiver",
    "call_my_therapist"
)

package com.rehab2.aac

enum class PatientGender { FEMALE, MALE, NEUTRAL;
    companion object { fun parse(raw: String?) = values().firstOrNull { it.name == raw?.uppercase() } ?: NEUTRAL }
}

/** Installed by the local patient runtime; no voice selection or catalog mutation. */
object AacGenderContext {
    var gender: () -> PatientGender = { PatientGender.NEUTRAL }
}

object AacLabelResolver {
    fun variant(item: AacItem, language: String, gender: PatientGender): String? = when (AacLanguageResolver.normalize(language)) {
        "sl" -> when (gender) { PatientGender.FEMALE -> item.labelSlFemale; PatientGender.MALE -> item.labelSlMale; else -> null }
        "uk" -> when (gender) { PatientGender.FEMALE -> item.labelUkFemale; PatientGender.MALE -> item.labelUkMale; else -> null }
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
    fun resolve(item: AacItem, language: String, gender: PatientGender): String =
        variant(item, language, gender) ?: AacLocalizedTextResolver.resolveLegacyLabel(item, language)
}

object AacSpeechResolver {
    fun variant(item: AacItem, language: String, gender: PatientGender): String? {
        val specific = when (AacLanguageResolver.normalize(language)) {
            "sl" -> when (gender) { PatientGender.FEMALE -> item.speechSlFemale; PatientGender.MALE -> item.speechSlMale; else -> null }
            "uk" -> when (gender) { PatientGender.FEMALE -> item.speechUkFemale; PatientGender.MALE -> item.speechUkMale; else -> null }
            else -> null
        }
        val neutral = when (AacLanguageResolver.normalize(language)) { "sl" -> item.speechSl; "uk" -> item.speechUk; else -> null }
        return specific?.trim()?.takeIf { it.isNotEmpty() } ?: neutral?.trim()?.takeIf { it.isNotEmpty() }
    }
    fun resolve(item: AacItem, language: String, gender: PatientGender): String =
        variant(item, language, gender) ?: AacLocalizedTextResolver.resolveLegacySpeakTextResult(item, language).text
}

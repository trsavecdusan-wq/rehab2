package com.rehab2.aac

/** Agreement is always with the patient, never with the GUIDE voice. */
object AacQuestionResolver {
    fun guided(text: String, language: String, gender: PatientGender = AacGenderContext.gender()): String {
        if (AacLanguageResolver.normalize(language) == "uk") return when(text) {
            "Kaj želiš piti?" -> "Що ти хочеш пити?"
            "Kaj želiš izbrati?" -> "Що ти хочеш обрати?"
            "Koga pogrešaš?" -> "За ким ти сумуєш?"
            else -> text
        }
        if (AacLanguageResolver.normalize(language) != "sl" || gender != PatientGender.MALE) return text
        return when(text) {
            "Kaj bi jedla?" -> "Kaj bi jedel?"
            "Ali si utrujena?" -> "Ali si utrujen?"
            "Ali si lačna?" -> "Ali si lačen?"
            "Ali si žejna?" -> "Ali si žejen?"
            else -> text
        }
    }
    fun variant(item: AacItem, language: String, gender: PatientGender): String? {
        val lang = AacLanguageResolver.normalize(language)
        val specific = when (lang) {
            "sl" -> when (gender) { PatientGender.FEMALE -> item.questionSlFemale; PatientGender.MALE -> item.questionSlMale; else -> null }
            "uk" -> when (gender) { PatientGender.FEMALE -> item.questionUkFemale; PatientGender.MALE -> item.questionUkMale; else -> null }
            else -> null
        }
        val neutral = when (lang) { "sl" -> item.questionSlNeutral; "uk" -> item.questionUkNeutral; else -> null }
        return specific?.trim()?.takeIf { it.isNotEmpty() }
            ?: neutral?.trim()?.takeIf { it.isNotEmpty() }

    }
    fun resolve(item: AacItem, language: String, gender: PatientGender): String? =
        variant(item, language, gender) ?: defaultQuestion(item, AacLanguageResolver.normalize(language), gender)
            ?: AacLocalizedTextResolver.resolveLegacyQuestion(item, language)

    private fun defaultQuestion(item: AacItem, language: String, gender: PatientGender): String? {
        // Existing authored questions always win; built-in defaults only fill missing content.
        if (AacLocalizedTextResolver.resolveLegacyQuestion(item, language) != null || gender == PatientGender.NEUTRAL) return null
        val female = gender == PatientGender.FEMALE
        return when (language) {
            "sl" -> when(item.id) {
                "tired" -> if(female) "Ali si utrujena?" else "Ali si utrujen?"
                "hungry" -> if(female) "Ali si lačna?" else "Ali si lačen?"
                "thirsty" -> if(female) "Ali si žejna?" else "Ali si žejen?"
                else -> null
            }
            "uk" -> when(item.id) {
                "tired" -> if(female) "Ти втомлена?" else "Ти втомлений?"
                "hungry" -> if(female) "Ти голодна?" else "Ти голодний?"
                "thirsty" -> "Ти хочеш пити?"
                else -> null
            }
            else -> null
        }
    }
}

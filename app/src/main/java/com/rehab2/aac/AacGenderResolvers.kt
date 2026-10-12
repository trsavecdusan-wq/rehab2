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
        variant(item, language, gender) ?: AacPatientGrammar.resolve(AacLocalizedTextResolver.resolveLegacyLabel(item, language), language, gender)
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
        variant(item, language, gender) ?: AacPatientGrammar.resolve(AacLocalizedTextResolver.resolveLegacySpeakTextResult(item, language).text, language, gender)
}

/** Limited first-person agreement; never derives grammar from voice or visual sex. */
object AacPatientGrammar {
    fun resolve(text: String, language: String, gender: PatientGender): String {
        if (gender == PatientGender.NEUTRAL) return text
        val female = gender == PatientGender.FEMALE
        val pairs = when(AacLanguageResolver.normalize(language)) {
            "sl" -> listOf("Rada bi počivala" to "Rad bi počival", "Rada bi povedala" to "Rad bi povedal",
                "Rada bi jedla" to "Rad bi jedel", "Rada bi pila" to "Rad bi pil", "Rada bi šla" to "Rad bi šel",
                "Utrujena sem" to "Utrujen sem", "Lačna sem" to "Lačen sem", "Žejna sem" to "Žejen sem",
                "Rada bi" to "Rad bi", "utrujena sem" to "utrujen sem", "lačna sem" to "lačen sem", "žejna sem" to "žejen sem",
                "rada bi" to "rad bi", "UTRUJENA SEM" to "UTRUJEN SEM", "LAČNA SEM" to "LAČEN SEM", "ŽEJNA SEM" to "ŽEJEN SEM")
            "uk" -> listOf("Я втомлена" to "Я втомлений", "Я втомилася" to "Я втомився", "Я голодна" to "Я голодний",
                "Я ВТОМИЛАСЯ" to "Я ВТОМИВСЯ", "Я ГОЛОДНА" to "Я ГОЛОДНИЙ")
            else -> emptyList()
        }
        for ((f,m) in pairs) {
            val replacement = if(female) f else m
            for (prefix in listOf(f,m)) {
                if (text == prefix || text.startsWith(prefix+".") || text.startsWith(prefix+" ") || text.startsWith(prefix+"!"))
                    return replacement+text.removePrefix(prefix)
            }
        }
        return text
    }
}

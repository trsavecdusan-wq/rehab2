package com.rehab2.patient

import com.rehab2.aac.PatientGender

data class PatientProfile(
    val id: String = "legacy",
    val displayName: String = "Obstoječi pacient",
    val visualSex: PatientGender = PatientGender.NEUTRAL,
    val grammaticalGender: PatientGender = PatientGender.NEUTRAL,
    val gender: String = "",
    val paralyzedSide: String = "",
    val dominantHand: String = "",
    val aphasiaLevel: String = "",
    val communicationLevel: String = "",
    val primaryLanguage: String = "sl",
    val secondaryLanguage: String = "",
    val personalityMode: String = "",
    val patientVoice: String = "",
    val guideVoice: String = "",
    val patientVoiceByLanguage: Map<String, String> = emptyMap(),
    val guideVoiceByLanguage: Map<String, String> = emptyMap(),
    val patientAndroidVoiceByLanguage: Map<String, String> = emptyMap(),
    val guideAndroidVoiceByLanguage: Map<String, String> = emptyMap()
)

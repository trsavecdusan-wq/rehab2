package com.rehab2.patient

data class PatientProfile(
    val gender: String = "",
    val paralyzedSide: String = "",
    val dominantHand: String = "",
    val aphasiaLevel: String = "",
    val communicationLevel: String = "",
    val primaryLanguage: String = "sl",
    val secondaryLanguage: String = "",
    val personalityMode: String = ""
)

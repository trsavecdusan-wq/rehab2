package com.rehab2.aac

import com.rehab2.patient.PatientProfile
import java.util.Locale

/** A speaker's role is independent of icon ownership and grammatical agreement. */
enum class AacSpeechRole { PATIENT, GUIDE, SYSTEM }
enum class AacVoiceGender { FEMALE, MALE, DEFAULT }
data class AacResolvedVoice(
    val voiceId: String, val androidVoiceId: String, val gender: AacVoiceGender,
    val role: AacSpeechRole = AacSpeechRole.PATIENT, val oppositeAndroidVoiceId: String = ""
)
data class AacVoiceCandidate(val name: String, val locale: Locale, val network: Boolean, val quality: Int)
enum class AacVoiceFallback { VOICE, LANGUAGE_DEFAULT, ENGINE_DEFAULT }
data class AacVoiceSelection(val path: AacVoiceFallback, val voiceName: String? = null)

object AacVoiceResolver {
    fun resolve(role: AacSpeechRole, language: String, profile: PatientProfile, defaultVoice: String = "default"): AacResolvedVoice {
        val patient = role == AacSpeechRole.PATIENT
        val gender = when (profile.grammaticalGender) {
            PatientGender.FEMALE -> if (patient) AacVoiceGender.FEMALE else AacVoiceGender.MALE
            PatientGender.MALE -> if (patient) AacVoiceGender.MALE else AacVoiceGender.FEMALE
            PatientGender.NEUTRAL -> AacVoiceGender.DEFAULT
        }
        val lang = AacLanguageResolver.normalize(language)
        val overrides = if (patient) profile.patientVoiceByLanguage else profile.guideVoiceByLanguage
        val explicit = overrides[lang]?.takeIf { it.isNotBlank() }
            ?: (if (patient) profile.patientVoice else profile.guideVoice).takeIf { it.isNotBlank() }
        val voice = explicit ?: when (gender) {
            AacVoiceGender.FEMALE -> "nova"
            AacVoiceGender.MALE -> "onyx"
            AacVoiceGender.DEFAULT -> defaultVoice
        }
        val android = (if (patient) profile.patientAndroidVoiceByLanguage else profile.guideAndroidVoiceByLanguage)[lang].orEmpty()
        val opposite = (if (patient) profile.guideAndroidVoiceByLanguage else profile.patientAndroidVoiceByLanguage)[lang].orEmpty()
        return AacResolvedVoice(voice, android, gender, if (patient) AacSpeechRole.PATIENT else AacSpeechRole.GUIDE, opposite)
    }

    /** Locale and engine metadata determine fallback order; names never imply gender. */
    fun resolveVoiceCandidates(voice: AacResolvedVoice, target: Locale, installed: List<AacVoiceCandidate>): List<AacVoiceCandidate> {
        val compatible = installed.filter { it.locale.language == target.language }
            .sortedWith(compareBy<AacVoiceCandidate> { if (it.locale == target) 0 else 1 }
                .thenBy { it.network }.thenByDescending { it.quality }.thenBy { it.name })
        val requested = compatible.firstOrNull { it.name == voice.androidVoiceId }
        val opposite = compatible.firstOrNull { it.name == voice.oppositeAndroidVoiceId }
            ?: if (voice.role != AacSpeechRole.PATIENT) compatible.firstOrNull() else null
        val alternatives = compatible.filter { it != requested }
            .sortedWith(compareBy<AacVoiceCandidate> { if (it.locale == target) 0 else 1 }
                .thenBy { it == opposite }.thenBy { it.network }
                .thenByDescending { it.quality }.thenBy { it.name })
        return listOfNotNull(requested) + alternatives
    }

    /** Android calls are injected so error handling is exercised by the JVM regression tests. */
    fun selectVoice(
        candidates: List<AacVoiceCandidate>, setVoice: (AacVoiceCandidate) -> Int,
        setLanguage: () -> Int, restoreEngineDefault: () -> Unit
    ): AacVoiceSelection {
        for (candidate in candidates) {
            if (runCatching { setVoice(candidate) }.getOrDefault(-1) == 0)
                return AacVoiceSelection(AacVoiceFallback.VOICE, candidate.name)
        }
        if (runCatching { setLanguage() }.getOrDefault(-1) >= 0)
            return AacVoiceSelection(AacVoiceFallback.LANGUAGE_DEFAULT)
        runCatching { restoreEngineDefault() }
        return AacVoiceSelection(AacVoiceFallback.ENGINE_DEFAULT)
    }
}

package com.rehab2.aac

import android.content.Context
import com.rehab2.patient.PatientSpeechGrammar

/** Patient UUID remains owned by the existing image store. No second patient/profile catalog. */
object AacPatientVoiceSettings {
    @Volatile private var app: Context? = null
    fun bind(context: Context) { app = context.applicationContext }
    fun stamp(context: Context): String = runCatching {
        val id = AacPatientImages.store(context).currentPatientId()
        val file = java.io.File(context.noBackupFilesDir, "aac-patients/$id/images.properties")
        val digest = if (file.isFile) java.security.MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) } else "empty"
        "$id:${male(context)}:$digest"
    }.getOrDefault("unavailable")
    fun itemText(item: AacItem, text: String, language: String): String {
        if (item.userEdited || item.locked || item.translationManualOverride) return text
        val context = app ?: return text
        return this.text(context, text, language)
    }
    fun male(context: Context): Boolean {
        val store = AacPatientImages.store(context)
        val id = runCatching { store.currentPatientId() }.getOrDefault("unavailable")
        val saved = context.getSharedPreferences("patient_voice_roles", Context.MODE_PRIVATE).getString("$id.gender", null)
        val legacy = runCatching { store.ownsLegacyImages(id) }.getOrDefault(false)
        val gender = saved ?: if (legacy) PatientProfileSettings.load(context).gender else PatientProfileSettings.GENDER_FEMALE
        return gender == PatientProfileSettings.GENDER_MALE
    }
    fun saveGender(context: Context, gender: String) {
        val id = runCatching { AacPatientImages.store(context).currentPatientId() }.getOrNull() ?: return
        context.getSharedPreferences("patient_voice_roles", Context.MODE_PRIVATE).edit()
            .putString("$id.gender", PatientProfileSettings.normalizeGender(gender)).apply()
    }
    fun voice(context: Context, guide: Boolean): String {
        val id = runCatching { AacPatientImages.store(context).currentPatientId() }.getOrDefault("unavailable")
        val prefs = context.getSharedPreferences("patient_voice_roles", Context.MODE_PRIVATE)
        val key = "$id.${if (guide) "guide" else "patient"}.${if (male(context)) "male" else "female"}"
        return prefs.getString(key, null)?.takeIf { it in voices }
            ?: PatientSpeechGrammar.voice(male(context), guide)
    }
    fun saveVoice(context: Context, guide: Boolean, voice: String) {
        require(voice in voices)
        val id = AacPatientImages.store(context).currentPatientId()
        val key = "$id.${if (guide) "guide" else "patient"}.${if (male(context)) "male" else "female"}"
        context.getSharedPreferences("patient_voice_roles", Context.MODE_PRIVATE).edit().putString(key, voice).apply()
    }
    fun text(context: Context, text: String, language: String) =
        PatientSpeechGrammar.resolve(text, AacLanguageResolver.normalize(language), male(context))
    val voices = listOf("coral", "onyx", "marin", "cedar", "nova", "alloy", "ash", "ballad", "echo", "fable", "sage", "shimmer", "verse")
}

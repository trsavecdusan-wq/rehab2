package com.rehab2.aac

import android.content.Context

/** Opt-in presentation only, keyed by the existing patient UUID. */
object AacIconPresentation {
    private fun prefs(c: Context) = c.getSharedPreferences("patient_icon_presentation",Context.MODE_PRIVATE)
    fun hidden(c: Context): Boolean = prefs(c).getBoolean("hidden_"+AacPatientImages.store(c).currentPatientId(),false)
    fun light(c: Context): Boolean = prefs(c).getBoolean("light_"+AacPatientImages.store(c).currentPatientId(),AacGridV4.enabled(c))
    fun save(c: Context, patient: String, hidden: Boolean, light: Boolean) {
        check(patient==AacPatientImages.store(c).currentPatientId()) { "Pacient se je spremenil." }
        check(prefs(c).edit().putBoolean("hidden_$patient",hidden).putBoolean("light_$patient",light).commit()) { "Nastavitve ni mogoče shraniti." }
    }
}

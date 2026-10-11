package com.rehab2.patient
import android.content.Context
import com.rehab2.aac.AacStoragePaths
object PatientProfileStore {
    fun load(context: Context): PatientProfile = runCatching {
        PatientProfileCatalog(requireNotNull(AacStoragePaths.getPatientProfileFile(context))).active()
    }.getOrDefault(PatientProfile())
    fun save(context: Context, profile: PatientProfile): Boolean = runCatching {
        PatientProfileCatalog(requireNotNull(AacStoragePaths.getPatientProfileFile(context))).save(profile)
        true
    }.getOrDefault(false)
}

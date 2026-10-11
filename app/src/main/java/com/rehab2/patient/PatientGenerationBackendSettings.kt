package com.rehab2.patient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/** Endpoint is local configuration. A real authentication integration can supply short-lived sessions. */
object PatientGenerationBackendSettings {
    @Volatile var session: PatientGenerationSession = PatientGenerationSession { null }
    fun read(context: Context): PatientGenerationBackendConfig {
        val prefs=context.getSharedPreferences("patient_generation_backend",Context.MODE_PRIVATE)
        val namespace=prefs.getString("scopeNamespace",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("scopeNamespace",it).commit() }
        return PatientGenerationBackendConfig(prefs.getString("endpoint","").orEmpty(),prefs.getBoolean("requireAuth",true),namespace)
    }
    fun save(context: Context, endpoint: String, requireAuth: Boolean) {
        require(endpoint.isBlank() || PatientGenerationBackendConfig(endpoint).validEndpoint()) { "Potreben je HTTPS naslov brez uporabnika, query ali fragmenta." }
        context.getSharedPreferences("patient_generation_backend",Context.MODE_PRIVATE).edit()
            .putString("endpoint",endpoint.trim()).putBoolean("requireAuth",requireAuth).apply()
    }
    fun transport(context: Context, cancellation: PatientGenerationCancellation): PatientIconGenerationTransport {
        val config=read(context)
        val delegate=HttpPatientIconGenerationTransport(config,session,File(context.cacheDir,"patient_generation_candidates"),cancellation)
        return object:PatientIconGenerationTransport {
            override fun generate(request:PatientIconGenerationRequest,referenceImage:File):PatientIconGenerationResult {
                if(cancellation.isCancelled) return PatientIconGenerationResult(GenerationBackendStatus.CANCELLED)
                if(!config.validEndpoint()) return PatientIconGenerationResult(GenerationBackendStatus.CONFIGURATION_REQUIRED)
                // Decode/re-encode pixels only: never upload EXIF location, device tags or the original filename.
                // The catalog has already validated this patient's primary reference; original bytes are untouched.
                val upload=File.createTempFile("reference_upload_",".png",context.cacheDir)
                return try {
                    val bitmap=BitmapFactory.decodeFile(referenceImage.path)
                        ?: return PatientIconGenerationResult(GenerationBackendStatus.INVALID_RESPONSE)
                    try { upload.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
                    finally { bitmap.recycle() }
                    delegate.generate(request,upload)
                } catch(_:Exception) { PatientIconGenerationResult(if(cancellation.isCancelled) GenerationBackendStatus.CANCELLED else GenerationBackendStatus.INVALID_RESPONSE) }
                finally { upload.delete() }
            }
        }
    }
}

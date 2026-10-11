package com.rehab2.patient

import com.rehab2.aac.PatientGender
import org.json.JSONObject

enum class PatientIconGenerationStatus { DRAFT, READY_FOR_GENERATION, GENERATED, REVIEW_REQUIRED, APPROVED, REJECTED }
enum class PatientIconGenerationTemplate {
    PERSON_ACTION, GENERAL_PAIN, BODY_PAIN, EMOTION, DAILY_ACTIVITY, COMMUNICATION_ACTION, OTHER;

    fun payload(request: PatientIconGenerationRequest): JSONObject = JSONObject()
        .put("requestId", request.requestId).put("patientId", request.patientId).put("itemId", request.itemId)
        .put("meaning", request.meaning).put("referenceImagePath", request.referenceImagePath)
        .put("grammaticalGender", request.grammaticalGender.name).put("labelSl", request.labelSl).put("labelUk", request.labelUk)
        .put("requestedAt", request.createdAt).put("visualSex", request.visualSex.name).put("category", request.category).put("template", name)
        .put("requirements", "Use the supplied adult patient reference; 512x512 RGBA with transparent background; realistic adult matching reference identity, correct anatomy, one clear meaning; no text, watermark, cartoon or crop artefacts. " + when (this) {
            GENERAL_PAIN -> "Express general pain; do not localize to one body part."
            BODY_PAIN -> "Show only the exact body area and patient-side specified by meaning; red highlight; correct anatomy."
            EMOTION -> "Show the exact requested emotion clearly."
            else -> "Show the exact requested meaning clearly with one central motif."
        }).put("bodyMeaning", request.meaning).put("backend", "NOT CONNECTED")
}

data class PatientIconGenerationRequest(
    val requestId: String, val patientId: String, val itemId: String, val meaning: String,
    val labelSl: String, val labelUk: String, val speechSl: String, val speechUk: String,
    val referenceImagePath: String, val visualSex: PatientGender, val category: String,
    val generationTemplate: PatientIconGenerationTemplate, val createdAt: Long,
    val status: PatientIconGenerationStatus = PatientIconGenerationStatus.DRAFT,
    val grammaticalGender: PatientGender = PatientGender.NEUTRAL
) {
    fun json(): JSONObject = JSONObject().put("requestId", requestId).put("patientId", patientId)
        .put("itemId", itemId).put("meaning", meaning).put("labelSl", labelSl).put("labelUk", labelUk)
        .put("speechSl", speechSl).put("speechUk", speechUk).put("referenceImagePath", referenceImagePath)
        .put("visualSex", visualSex.name).put("category", category).put("generationTemplate", generationTemplate.name)
        .put("createdAt", createdAt).put("requestedAt", createdAt).put("grammaticalGender", grammaticalGender.name).put("status", status.name)
}

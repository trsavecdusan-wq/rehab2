package com.rehab2.patient

import com.rehab2.aac.PatientGender
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLabelResolver
import com.rehab2.aac.AacSpeechResolver
import java.util.UUID
import org.json.JSONObject

enum class PatientIconGenerationStatus { DRAFT, READY_FOR_GENERATION, GENERATED, REVIEW_REQUIRED, APPROVED, REJECTED, REJECTED_INVALID, CANCELLED }
enum class PatientIconGenerationTemplate {
    PERSON_ACTION, GENERAL_PAIN, BODY_PAIN, EMOTION, DAILY_ACTIVITY, COMMUNICATION_ACTION, OTHER;

    fun payload(request: PatientIconGenerationRequest): JSONObject = JSONObject()
        .put("requestId", request.requestId).put("patientId", request.patientId).put("itemId", request.itemId)
        .put("meaning", request.meaning).put("referenceImagePath", request.referenceImagePath)
        .put("grammaticalGender", request.grammaticalGender.name).put("labelSl", request.labelSl).put("labelUk", request.labelUk)
        .put("requestedAt", request.createdAt).put("visualSex", request.visualSex.name).put("category", request.category).put("template", name)
        .put("requirements", "Use the supplied adult patient reference; 512x512 RGBA with transparent background; realistic adult matching reference identity, correct anatomy, one clear meaning; one clear AAC meaning; adult appropriate; isolated clean composition; no embedded text, watermark, cartoon styling, irrelevant objects or crop artefacts. " + when (this) {
            GENERAL_PAIN -> "Express general pain and distress, not only headache, abdominal pain or shoulder pain; do not localize to one body part."
            BODY_PAIN -> "Show only the exact body area and patient-side specified by meaning; red highlight; correct anatomy."
            EMOTION -> "Show the exact requested emotion clearly."
            PERSON_ACTION -> "Show the adult performing only the requested action."
            DAILY_ACTIVITY -> "Show one recognizable daily activity with only essential objects."
            COMMUNICATION_ACTION -> "Show one clear communicative gesture without written text."
            OTHER -> "Show only the requested AAC meaning, without speculative details."
        }).put("bodyMeaning", request.meaning).put("backend", "NOT CONNECTED")
}

data class PatientIconGenerationRequest(
    val requestId: String, val patientId: String, val itemId: String, val meaning: String,
    val labelSl: String, val labelUk: String, val speechSl: String, val speechUk: String,
    val referenceImagePath: String, val visualSex: PatientGender, val category: String,
    val generationTemplate: PatientIconGenerationTemplate, val createdAt: Long,
    val status: PatientIconGenerationStatus = PatientIconGenerationStatus.DRAFT,
    val grammaticalGender: PatientGender = PatientGender.NEUTRAL,
    val bodyRegion: String? = null, val bodySide: PatientBodySide? = null, val view: PatientBodyView? = null
) {
    fun json(): JSONObject = JSONObject().put("requestId", requestId).put("patientId", patientId)
        .put("itemId", itemId).put("meaning", meaning).put("labelSl", labelSl).put("labelUk", labelUk)
        .put("speechSl", speechSl).put("speechUk", speechUk).put("referenceImagePath", referenceImagePath)
        .put("visualSex", visualSex.name).put("category", category).put("generationTemplate", generationTemplate.name)
        .put("createdAt", createdAt).put("requestedAt", createdAt).put("grammaticalGender", grammaticalGender.name).put("status", status.name).put("bodyRegion",bodyRegion).put("bodySide",bodySide?.name).put("view",view?.name)
}

enum class PatientBodySide { LEFT, RIGHT, BOTH, CENTER }
enum class PatientBodyView { FRONT, BACK, SIDE }

/** The only request/prompt builder; UI supplies structured choices, never a free-form prompt. */
object PatientIconRequestBuilder {
    fun create(profile: PatientProfile, item: AacItem, referenceName: String, template: PatientIconGenerationTemplate,
        bodyRegion: String? = null, bodySide: PatientBodySide? = null, view: PatientBodyView? = null): PatientIconGenerationRequest {
        val request=PatientIconGenerationRequest(UUID.randomUUID().toString(),profile.id,item.id,item.meaningId ?: item.meaning ?: item.id,
            AacLabelResolver.resolve(item,"sl",profile.grammaticalGender),AacLabelResolver.resolve(item,"uk",profile.grammaticalGender),
            AacSpeechResolver.resolve(item,"sl",profile.grammaticalGender),AacSpeechResolver.resolve(item,"uk",profile.grammaticalGender),
            referenceName,profile.visualSex,item.categoryId.orEmpty(),template,System.currentTimeMillis(),
            grammaticalGender=profile.grammaticalGender,bodyRegion=bodyRegion,bodySide=bodySide,view=view)
        validate(request)
        return request
    }
    fun validate(request: PatientIconGenerationRequest) {
        if(request.generationTemplate==PatientIconGenerationTemplate.BODY_PAIN) {
            require(!request.bodyRegion.isNullOrBlank() && request.bodyRegion.length<=80 && request.bodySide!=null && request.view!=null) { "BODY_REGION_SIDE_VIEW_REQUIRED" }
        }
    }
    fun wire(request: PatientIconGenerationRequest, opaquePatientId: String): JSONObject {
        validate(request)
        var constraints=request.generationTemplate.payload(request).getString("requirements")
        if(request.generationTemplate==PatientIconGenerationTemplate.BODY_PAIN) {
            constraints += " Body region: ${request.bodyRegion}; side: ${request.bodySide}; view: ${request.view}. LEFT/RIGHT always refer to the patient's anatomy. In FRONT view patient LEFT is viewer RIGHT; patient RIGHT is viewer LEFT. Never infer side from filenames."
        }
        if(request.itemId=="cannot_breathe") constraints += " Show breathing distress, NOT choking, food obstruction, hands around throat or any foreign object in the mouth."
        if(request.itemId=="chest_pain") constraints += " Show clear chest pain, NOT abdominal pain or a heart symbol alone. Keep the person's face and head fully visible."
        // No profile, local filename, grammatical gender, history or other patient data is sent.
        return JSONObject().put("requestId",request.requestId).put("patientId",opaquePatientId)
            .put("itemId",request.itemId).put("meaning",request.meaning)
            .put("label",request.labelSl).put("speechContext",request.speechSl)
            .put("visualSex",request.visualSex.name).put("generationTemplate",request.generationTemplate.name)
            .put("bodyRegion",request.bodyRegion).put("bodySide",request.bodySide?.name).put("view",request.view?.name)
            .put("constraints",constraints)
    }
}

package com.rehab2.patient

import java.io.File

/** Implementations must use an authenticated backend; no provider credentials belong in Android. */
interface PatientIconGenerationTransport {
    fun generate(request: PatientIconGenerationRequest, referenceImage: File): PatientIconGenerationResult

    object NotConfigured : PatientIconGenerationTransport {
        override fun generate(request: PatientIconGenerationRequest, referenceImage: File) =
            PatientIconGenerationResult(status = GenerationBackendStatus.CONFIGURATION_REQUIRED)
    }
}
enum class GenerationBackendStatus { CONFIGURATION_REQUIRED, GENERATED, FAILED }
data class PatientIconGenerationResult(
    val status: GenerationBackendStatus,
    val generationId: String = "",
    val requestId: String = "",
    val patientId: String = "",
    val itemId: String = "",
    val candidateImage: File? = null,
    val metadata: Map<String, String> = emptyMap()
)

/** Only queues a reviewed candidate; approval is a separate explicit catalog operation. */
class PatientIconGenerationWorkflow(
    private val catalog: PatientIconCatalog,
    private val transport: PatientIconGenerationTransport = PatientIconGenerationTransport.NotConfigured
) {
    fun generate(request: PatientIconGenerationRequest): PatientIconGenerationResult {
        val stored = catalog.requests(request.patientId).single { it.getString("requestId") == request.requestId }
        require(stored.getString("itemId") == request.itemId && stored.getString("status") == "READY_FOR_GENERATION")
        require(stored.getString("referenceImagePath") == request.referenceImagePath)
        val supplied = request.json()
        for (key in supplied.keys()) {
            if (key != "status") require(stored.optString(key) == supplied.optString(key)) { "REQUEST_CHANGED" }
        }
        val reference = requireNotNull(catalog.reference(request.patientId))
        require(reference.name == request.referenceImagePath) { "REFERENCE_CHANGED" }
        val result = transport.generate(request, reference)
        if (result.status == GenerationBackendStatus.GENERATED) {
            require(result.generationId.isNotBlank() && result.requestId == request.requestId &&
                result.patientId == request.patientId && result.itemId == request.itemId) { "GENERATION_SCOPE_MISMATCH" }
            catalog.importGeneratedCandidate(request, result)
        }
        return result
    }
}

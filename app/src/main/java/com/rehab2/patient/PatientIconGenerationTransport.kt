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
enum class GenerationBackendStatus { SUCCESS, CONFIGURATION_REQUIRED, NETWORK_ERROR, AUTH_ERROR, SERVER_ERROR, INVALID_RESPONSE, CANCELLED, GENERATED, FAILED }
data class PatientIconGenerationResult(
    val status: GenerationBackendStatus,
    val generationId: String = "",
    val requestId: String = "",
    val patientId: String = "",
    val itemId: String = "",
    val candidateImage: File? = null,
    val metadata: Map<String, String> = emptyMap(),
    val mimeType: String = "", val contentHash: String = "", val createdAt: Long = 0,
    val temporaryImage: Boolean = false
)

/** Only queues a reviewed candidate; approval is a separate explicit catalog operation. */
class PatientIconGenerationWorkflow(
    private val catalog: PatientIconCatalog,
    private val transport: PatientIconGenerationTransport = PatientIconGenerationTransport.NotConfigured
) {
    fun generate(request: PatientIconGenerationRequest, cancellation: PatientGenerationCancellation = PatientGenerationCancellation()): PatientIconGenerationResult {
        val stored = catalog.requests(request.patientId).single { it.getString("requestId") == request.requestId }
        require(stored.getString("itemId") == request.itemId && stored.getString("status") == "READY_FOR_GENERATION")
        require(stored.getString("referenceImagePath") == request.referenceImagePath)
        val supplied = request.json()
        for (key in supplied.keys()) {
            if (key != "status") require(stored.optString(key) == supplied.optString(key)) { "REQUEST_CHANGED" }
        }
        val reference = requireNotNull(catalog.reference(request.patientId))
        require(reference.name == request.referenceImagePath) { "REFERENCE_CHANGED" }
        if(cancellation.isCancelled) {
            catalog.finishGeneration(request.patientId,request.requestId,"CANCELLED")
            return PatientIconGenerationResult(GenerationBackendStatus.CANCELLED)
        }
        val result = try { transport.generate(request, reference) }
            catch (_: java.io.IOException) { PatientIconGenerationResult(GenerationBackendStatus.NETWORK_ERROR) }
        if(result.status==GenerationBackendStatus.SUCCESS) {
            try {
                return cancellation.complete {
                    try {
                        require(result.mimeType=="image/png" && result.createdAt>0 && result.contentHash.matches(Regex("[a-f0-9]{64}")))
                        require(result.contentHash==PatientIconCatalog.hash(requireNotNull(result.candidateImage)))
                        catalog.importGeneratedCandidate(request,result)
                        result
                    } catch (_: Exception) {
                        catalog.finishGeneration(request.patientId,request.requestId,"REJECTED_INVALID")
                        PatientIconGenerationResult(GenerationBackendStatus.INVALID_RESPONSE)
                    }
                } ?: run {
                    catalog.finishGeneration(request.patientId,request.requestId,"CANCELLED")
                    PatientIconGenerationResult(GenerationBackendStatus.CANCELLED)
                }
            } finally { if(result.temporaryImage) result.candidateImage?.delete() }
        }
        if(result.status==GenerationBackendStatus.INVALID_RESPONSE || result.status==GenerationBackendStatus.CANCELLED || cancellation.isCancelled) {
            val cancelled=cancellation.isCancelled || result.status==GenerationBackendStatus.CANCELLED
            catalog.finishGeneration(request.patientId,request.requestId,if(cancelled) "CANCELLED" else "REJECTED_INVALID")
            return PatientIconGenerationResult(if(cancelled) GenerationBackendStatus.CANCELLED else GenerationBackendStatus.INVALID_RESPONSE)
        }
        if (result.status == GenerationBackendStatus.GENERATED) {
            require(result.generationId.isNotBlank() && result.requestId == request.requestId &&
                result.patientId == request.patientId && result.itemId == request.itemId) { "GENERATION_SCOPE_MISMATCH" }
            catalog.importGeneratedCandidate(request, result)
        }
        return result
    }
}

/** Cancellation and candidate acceptance share one gate; cancellation never approves an image. */
class PatientGenerationCancellation {
    @Volatile var isCancelled=false
        private set
    private var abort: (() -> Unit)? = null
    @Synchronized fun cancel() { isCancelled=true; runCatching { abort?.invoke() } }
    @Synchronized fun onCancel(action: (() -> Unit)?) { abort=action; if(isCancelled) runCatching { action?.invoke() } }
    @Synchronized fun <T> complete(action: () -> T): T? = if(isCancelled) null else action()
}

package com.rehab2.patient

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

data class PatientGenerationBackendConfig(val endpoint: String = "", val requireAuth: Boolean = true, val scopeNamespace: String = "") {
    fun validEndpoint(): Boolean = runCatching {
        val uri=URI(endpoint)
        uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null
    }.getOrDefault(false)
}
/** Return only a short-lived session intended for this exact backend endpoint. Never a provider key. */
fun interface PatientGenerationSession { fun token(endpoint: String): String? }
data class PatientGenerationHttpResponse(val status: Int, val headers: Map<String,String>, val bytes: ByteArray)
fun interface PatientGenerationHttpClient {
    fun post(config: PatientGenerationBackendConfig, token: String?, metadata: JSONObject, reference: File,
        cancellation: PatientGenerationCancellation): PatientGenerationHttpResponse
}

/** V1 backend contract: POST multipart metadata (JSON) and reference (original image bytes).
 * Successful response: image/png bytes plus X-Generation-Id, X-Request-Id, X-Patient-Id,
 * X-Item-Id, X-Content-SHA256 (lowercase hex), X-Created-At (Unix milliseconds).
 * Patient ID is the opaque ID received in metadata, not a local profile identifier.
 * Backend owns provider credentials and authentication; no provider API is called by this client.
 * HTTPS only; redirects are disabled so reference and session cannot leave the configured endpoint.
 */
class PatientGenerationHttpsClient : PatientGenerationHttpClient {
    override fun post(config: PatientGenerationBackendConfig, token: String?, metadata: JSONObject, reference: File,
        cancellation: PatientGenerationCancellation): PatientGenerationHttpResponse {
        require(config.validEndpoint())
        val connection=URI(config.endpoint).toURL().openConnection() as HttpsURLConnection
        cancellation.onCancel { connection.disconnect() }
        try {
            if(cancellation.isCancelled) throw IOException("CANCELLED")
            val boundary="aac_"+UUID.randomUUID().toString()
            connection.requestMethod="POST"; connection.doOutput=true; connection.instanceFollowRedirects=false
            connection.connectTimeout=15000; connection.readTimeout=120000
            connection.setChunkedStreamingMode(8192)
            connection.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept","image/png")
            connection.setRequestProperty("Cache-Control","no-store")
            if(!token.isNullOrBlank()) connection.setRequestProperty("Authorization","Bearer $token")
            connection.outputStream.use { output ->
                fun write(s:String)=output.write(s.toByteArray(Charsets.UTF_8))
                write("--$boundary\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n")
                output.write(metadata.toString().toByteArray(Charsets.UTF_8))
                write("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"reference\"; filename=\"reference-image\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                reference.inputStream().use { input ->
                    val buffer=ByteArray(8192); var total=0
                    while(true) {
                        if(cancellation.isCancelled) throw IOException("CANCELLED")
                        val n=input.read(buffer); if(n<0) break
                        total+=n; require(total<=16*1024*1024); output.write(buffer,0,n)
                    }
                }
                write("\r\n--$boundary--\r\n")
            }
            val status=connection.responseCode
            // Error bodies may contain provider internals: never read, persist or display them.
            if(status!=200) return PatientGenerationHttpResponse(status,emptyMap(),ByteArray(0))
            val headers=listOf("Content-Type","X-Generation-Id","X-Request-Id","X-Patient-Id","X-Item-Id","X-Content-SHA256","X-Created-At")
                .associateWith { connection.getHeaderField(it).orEmpty() }
            val bytes=connection.inputStream.use { input ->
                val output=ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) {
                    if(cancellation.isCancelled) throw IOException("CANCELLED")
                    val n=input.read(buffer); if(n<0) break
                    require(output.size()+n<=16*1024*1024) { "RESPONSE_TOO_LARGE" }; output.write(buffer,0,n)
                }
                output.toByteArray()
            }
            return PatientGenerationHttpResponse(status,headers,bytes)
        } finally { cancellation.onCancel(null); connection.disconnect() }
    }
}

class HttpPatientIconGenerationTransport(
    private val config: PatientGenerationBackendConfig,
    private val session: PatientGenerationSession,
    private val temporaryDirectory: File,
    private val cancellation: PatientGenerationCancellation,
    private val client: PatientGenerationHttpClient = PatientGenerationHttpsClient()
) : PatientIconGenerationTransport {
    override fun generate(request: PatientIconGenerationRequest, referenceImage: File): PatientIconGenerationResult {
        fun result(status:GenerationBackendStatus)=PatientIconGenerationResult(status)
        if(cancellation.isCancelled) return result(GenerationBackendStatus.CANCELLED)
        if(!config.validEndpoint() || config.scopeNamespace.isBlank()) return result(GenerationBackendStatus.CONFIGURATION_REQUIRED)
        val token=runCatching { session.token(config.endpoint) }.getOrNull()?.takeIf { it.isNotBlank() }
        if(config.requireAuth && token==null) return result(GenerationBackendStatus.CONFIGURATION_REQUIRED)
        if(token!=null && (token.length>8192 || token.any { it=='\r' || it=='\n' })) return result(GenerationBackendStatus.AUTH_ERROR)
        var temporary: File?=null
        try {
            require(referenceImage.isFile && referenceImage.canRead() && referenceImage.length() in 1..16L*1024*1024)
            val opaque=MessageDigest.getInstance("SHA-256").digest((config.scopeNamespace+"\u0000"+request.patientId).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val response=client.post(config,token,PatientIconRequestBuilder.wire(request,opaque),referenceImage,cancellation)
            if(cancellation.isCancelled) return result(GenerationBackendStatus.CANCELLED)
            if(response.status==401 || response.status==403) return result(GenerationBackendStatus.AUTH_ERROR)
            if(response.status!=200) return result(GenerationBackendStatus.SERVER_ERROR)
            fun header(name:String)=response.headers.entries.firstOrNull { it.key.equals(name,true) }?.value.orEmpty()
            val mime=header("Content-Type").substringBefore(';').trim()
            val generation=header("X-Generation-Id")
            val created=header("X-Created-At").toLongOrNull() ?: 0L
            require(mime=="image/png" && generation.matches(Regex("[A-Za-z0-9_-]{1,128}")) && created>0)
            require(header("X-Request-Id")==request.requestId && header("X-Patient-Id")==opaque && header("X-Item-Id")==request.itemId)
            require(response.bytes.size in 26..16*1024*1024)
            require(response.bytes.take(8)==listOf(137,80,78,71,13,10,26,10).map { it.toByte() })
            val hash=MessageDigest.getInstance("SHA-256").digest(response.bytes).joinToString("") { "%02x".format(it) }
            require(hash==header("X-Content-SHA256"))
            // Locally chosen patient scope and random filename; no remote paths or filenames are accepted.
            val directory=File(temporaryDirectory,opaque)
            check(directory.isDirectory || directory.mkdirs())
            val file=File.createTempFile("candidate_",".png",directory); temporary=file
            file.outputStream().use { it.write(response.bytes); it.fd.sync() }
            if(cancellation.isCancelled) return result(GenerationBackendStatus.CANCELLED)
            return PatientIconGenerationResult(GenerationBackendStatus.SUCCESS,generation,request.requestId,request.patientId,request.itemId,file,
                mimeType=mime,contentHash=hash,createdAt=created,temporaryImage=true).also { temporary=null }
        } catch (_: IOException) {
            return result(if(cancellation.isCancelled) GenerationBackendStatus.CANCELLED else GenerationBackendStatus.NETWORK_ERROR)
        } catch (_: Exception) {
            return result(if(cancellation.isCancelled) GenerationBackendStatus.CANCELLED else GenerationBackendStatus.INVALID_RESPONSE)
        } finally { temporary?.delete() }
    }
}

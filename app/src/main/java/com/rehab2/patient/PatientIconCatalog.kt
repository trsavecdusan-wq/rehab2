package com.rehab2.patient

import com.rehab2.aac.AacItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class PatientImageCheck(val valid: Boolean, val format: String = "", val width: Int = 0,
    val height: Int = 0, val alpha: Boolean = false, val transparent: Boolean = false, val nonEmpty: Boolean = false)
enum class PatientIconReplacement { REPLACE, KEEP_OLD, CANCEL }

/** Metadata and immutable images live directly in the EXISTING icons/patient directory.
 * Every lookup requires patientId; filenames never determine ownership or activation.
 */
class PatientIconCatalog(private val directory: File, private val inspect: (File) -> PatientImageCheck,
    private val knownPatient: (String) -> Boolean, private val itemById: (String) -> AacItem?) {
    companion object {
        private val lock = Any()
        fun hash(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }
    private val manifest get() = File(directory, "patient_icon_catalog_v1.json")
    private fun read(): JSONObject {
        val backup = File(directory, manifest.name + ".bak")
        val source = if (manifest.isFile) manifest else backup
        return if (source.isFile) JSONObject(source.readText()) else JSONObject().put("references", JSONObject()).put("requests", JSONArray()).put("icons", JSONArray())
    }
    private fun write(value: JSONObject) {
        check(directory.isDirectory || directory.mkdirs())
        val tmp = File(directory, manifest.name + ".tmp")
        val backup = File(directory, manifest.name + ".bak")
        tmp.outputStream().use { it.write(value.toString(2).toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if (manifest.exists()) { check(!backup.exists() || backup.delete()); check(manifest.renameTo(backup)) }
        if (!tmp.renameTo(manifest)) { backup.renameTo(manifest); error("Metadata write failed") }
        backup.delete()
    }
    private fun patient(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && knownPatient(id)) { "PATIENT_MISMATCH" } }
    private fun file(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]+\\.(png|jpg|webp)"))) { "INVALID_PATH" }
        return File(directory, name).also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    }
    private fun rows(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun valid(f: File, icon: Boolean): PatientImageCheck {
        require(f.isFile && f.length() in 1..(16L * 1024 * 1024)) { "MISSING_OR_EMPTY_IMAGE" }
        val v = inspect(f)
        require(v.valid && v.nonEmpty && v.width in 1..4096 && v.height in 1..4096) { "INVALID_IMAGE" }
        if (icon) {
            val header=f.inputStream().use { input -> ByteArray(26).also { require(input.read(it)==26) } }
            require(header.take(8)==listOf(137,80,78,71,13,10,26,10).map { it.toByte() } && header[25].toInt()==6 &&
                v.format == "PNG" && v.width == 512 && v.height == 512 && v.alpha && v.transparent) { "PNG_512_RGBA_TRANSPARENCY_REQUIRED" }
        }
        return v
    }
    private fun copy(input: File, icon: Boolean): Pair<File, String> {
        val v = valid(input, icon)
        require(v.format in setOf("PNG", "JPEG", "WEBP"))
        check(directory.isDirectory || directory.mkdirs())
        val extension = when (v.format) { "JPEG" -> "jpg"; "WEBP" -> "webp"; else -> "png" }
        val target = file("patient_" + UUID.randomUUID().toString() + "." + extension)
        check(target.createNewFile()) { "FILENAME_COLLISION" }
        try { input.inputStream().use { src -> target.outputStream().use { src.copyTo(it) } }; valid(target, icon) }
        catch (e: Exception) { target.delete(); throw e }
        return target to hash(target)
    }
    fun reference(patientId: String): File? = synchronized(lock) {
        patient(patientId)
        runCatching {
            val r = read().getJSONObject("references").optJSONObject(patientId) ?: return@synchronized null
            file(r.getString("filePath")).takeIf { valid(it, false).valid && hash(it) == r.getString("sha256") }
        }.getOrNull()
    }
    fun setReference(patientId: String, input: File) = synchronized(lock) {
        patient(patientId); val root = read(); val (f, h) = copy(input, false)
        val refs = root.getJSONObject("references"); val previous = refs.optJSONObject(patientId)?.optString("filePath")
        refs.put(patientId, JSONObject().put("patientId", patientId).put("filePath", f.name).put("sha256", h))
        write(root)
        previous?.let { file(it).delete() }
    }
    fun removeReference(patientId: String) = synchronized(lock) {
        patient(patientId); val root = read(); val old = root.getJSONObject("references").remove(patientId) as? JSONObject
        write(root); old?.let { file(it.getString("filePath")).delete() }; Unit
    }
    fun create(request: PatientIconGenerationRequest): JSONObject = synchronized(lock) {
        patient(request.patientId)
        val item=itemById(request.itemId) ?: error("SEMANTIC_MAPPING_REQUIRED")
        require(request.status==PatientIconGenerationStatus.DRAFT && request.meaning==(item.meaningId ?: item.meaning ?: item.id)) { "SEMANTIC_MAPPING_REQUIRED" }
        require(request.referenceImagePath == reference(request.patientId)?.name) { "REFERENCE_REQUIRED" }
        val root = read(); require(rows(root.getJSONArray("requests")).none { it.optString("requestId") == request.requestId })
        val json = request.copy(status = PatientIconGenerationStatus.READY_FOR_GENERATION).json()
            .put("baseRevision", rows(root.getJSONArray("icons")).filter { it.getString("patientId")==request.patientId && it.getString("itemId")==request.itemId }.maxOfOrNull { it.getInt("revision") } ?: 0)
        root.getJSONArray("requests").put(json); write(root); JSONObject(json.toString())
    }
    fun requests(patientId: String): List<JSONObject> = synchronized(lock) {
        patient(patientId); rows(read().getJSONArray("requests")).filter { it.getString("patientId") == patientId }.map { JSONObject(it.toString()) }
    }
    private fun request(root: JSONObject, patientId: String, requestId: String): JSONObject {
        patient(patientId)
        return rows(root.getJSONArray("requests")).single { it.getString("patientId") == patientId && it.getString("requestId") == requestId }
    }
    fun importCandidate(patientId: String, requestId: String, input: File) = synchronized(lock) {
        val root = read(); val r = request(root, patientId, requestId)
        require(r.getString("status") == "READY_FOR_GENERATION") { "INVALID_TRANSITION" }
        val (f, h) = copy(input, true)
        r.put("candidatePath", f.name).put("sha256", h).put("status", "REVIEW_REQUIRED")
            .put("history", JSONArray(listOf("DRAFT", "READY_FOR_GENERATION", "GENERATED", "REVIEW_REQUIRED")))
        write(root)
    }
    fun importGeneratedCandidate(request: PatientIconGenerationRequest, result: PatientIconGenerationResult) = synchronized(lock) {
        require(result.status == GenerationBackendStatus.GENERATED && result.generationId.isNotBlank() &&
            result.requestId == request.requestId && result.patientId == request.patientId && result.itemId == request.itemId) { "GENERATION_SCOPE_MISMATCH" }
        val stored = request(read(), request.patientId, request.requestId)
        require(stored.getString("itemId") == request.itemId && stored.getString("referenceImagePath") == reference(request.patientId)?.name)
        importCandidate(request.patientId, request.requestId, requireNotNull(result.candidateImage))
        val root = read(); val updated = request(root, request.patientId, request.requestId)
        updated.put("generationId", result.generationId)
        // Allowlisted provenance only; never persist arbitrary provider payloads or secrets.
        updated.put("generationMetadata", JSONObject(result.metadata.filterKeys { it in setOf("model", "format", "width", "height") }))
        write(root)
    }
    fun candidate(patientId: String, requestId: String): File? = synchronized(lock) {
        runCatching { val r = request(read(), patientId, requestId); file(r.getString("candidatePath")).takeIf { valid(it, true).valid && hash(it)==r.getString("sha256") } }.getOrNull()
    }
    fun duplicateWarning(patientId: String, requestId: String): Boolean = synchronized(lock) {
        val root = read(); val r = request(root, patientId, requestId)
        rows(root.getJSONArray("icons")).any { it.getString("patientId")==patientId && it.getString("sha256")==r.optString("sha256") }
    }
    fun approve(patientId: String, requestId: String, semanticReviewed: Boolean,
        replacement: PatientIconReplacement, duplicateAcknowledged: Boolean = false): Boolean = synchronized(lock) {
        val root = read(); val r = request(root, patientId, requestId)
        require(r.getString("status") == "REVIEW_REQUIRED" && semanticReviewed) { "ADMIN_REVIEW_REQUIRED" }
        require(reference(patientId)?.name == r.getString("referenceImagePath")) { "REFERENCE_CHANGED" }
        val id = r.getString("itemId"); val item = requireNotNull(itemById(id))
        require(r.getString("meaning") == (item.meaningId ?: item.meaning ?: item.id)) { "SEMANTIC_MAPPING_CHANGED" }
        val f = file(r.getString("candidatePath")); valid(f, true); require(hash(f)==r.getString("sha256")) { "IMAGE_CHANGED" }
        val icons = rows(root.getJSONArray("icons"))
        val same = icons.filter { it.getString("patientId")==patientId && it.getString("itemId")==id }
        require(r.optInt("baseRevision", 0) == (same.maxOfOrNull { it.getInt("revision") } ?: 0)) { "STALE_REVISION" }
        if (replacement != PatientIconReplacement.REPLACE) return@synchronized false
        require(!duplicateWarning(patientId, requestId) || duplicateAcknowledged) { "DUPLICATE_PATIENT_IMAGE_REVIEW_REQUIRED" }
        same.forEach { it.put("active", false) }
        root.getJSONArray("icons").put(JSONObject().put("patientId", patientId).put("itemId", id).put("filePath", f.name)
            .put("source", "PATIENT").put("origin", "GENERATED").put("approved", true).put("active", true)
            .put("revision", (same.maxOfOrNull { it.getInt("revision") } ?: 0) + 1)
            .put("createdAt", r.getLong("createdAt")).put("approvedAt", System.currentTimeMillis()).put("sha256", hash(f)))
        r.put("status", "APPROVED"); write(root); true
    }
    fun reject(patientId: String, requestId: String) = synchronized(lock) {
        val root=read(); val r=request(root,patientId,requestId)
        require(r.getString("status") in setOf("READY_FOR_GENERATION", "REVIEW_REQUIRED"))
        r.put("status","REJECTED"); write(root)
    }
    fun revisions(patientId: String, itemId: String): List<JSONObject> = synchronized(lock) {
        patient(patientId); rows(read().getJSONArray("icons")).filter { it.getString("patientId")==patientId && it.getString("itemId")==itemId }.map { JSONObject(it.toString()) }
    }
    fun active(patientId: String, itemId: String): File? = synchronized(lock) {
        runCatching {
            val row = revisions(patientId,itemId).singleOrNull { it.optBoolean("active") && it.optBoolean("approved") } ?: return@synchronized null
            file(row.getString("filePath")).takeIf { valid(it,true).valid && hash(it)==row.getString("sha256") }
        }.getOrNull()
    }
    fun activateRevision(patientId: String, itemId: String, revision: Int?) = synchronized(lock) {
        patient(patientId); val root=read()
        val rows=rows(root.getJSONArray("icons")).filter { it.getString("patientId")==patientId && it.getString("itemId")==itemId }
        if (revision != null) {
            val target=rows.single { it.getInt("revision")==revision && it.optBoolean("approved") }
            val f=file(target.getString("filePath")); valid(f,true); require(hash(f)==target.getString("sha256"))
        }
        rows.forEach { it.put("active", it.getInt("revision")==revision) }; write(root)
    }
}

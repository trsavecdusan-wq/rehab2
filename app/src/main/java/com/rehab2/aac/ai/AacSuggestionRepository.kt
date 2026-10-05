package com.rehab2.aac.ai

import com.rehab2.aac.AacItem
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** No production AAC writer is reachable here. APPROVED is review state only. */
class AacSuggestionRepository(directory: File, private val catalog: () -> List<AacItem>) {
    private val store = AiJsonStore(File(directory, "suggestions.json"))
    fun listAll(): List<AacSuggestion> = synchronized(AiJsonStore.lock) { read() }
    fun listByStatus(status: SuggestionStatus) = listAll().filter { it.status == status }
    fun create(value: AacSuggestion): AacSuggestion = synchronized(AiJsonStore.lock) {
        val rows = read(); require(rows.size < 2000) { "Dosežena omejitev predlogov" }
        require(rows.none { it.id == value.id })
        val next = checked(value.copy(status = SuggestionStatus.DETECTED, duplicateOfItemId = null))
        write(rows + next); next
    }
    fun update(value: AacSuggestion): AacSuggestion = change(value.id) { previous ->
        checked(value.copy(createdAt = previous.createdAt, status = SuggestionStatus.ADMIN_REVIEW, duplicateOfItemId = null))
    }
    fun approve(id: String, acknowledgePossibleDuplicate: Boolean = false): AacSuggestion = change(id) { value ->
        val result = duplicate(value)
        require(result.kind != DuplicateKind.EXACT_DUPLICATE) { "EXACT_DUPLICATE: uporabite ZDRUŽI Z OBSTOJEČIM (${result.itemIds.joinToString()})" }
        require(result.kind != DuplicateKind.POSSIBLE_DUPLICATE || acknowledgePossibleDuplicate) { "POSSIBLE_DUPLICATE: potreben je pregled opozorila" }
        value.copy(status = SuggestionStatus.APPROVED, duplicateOfItemId = null)
    }
    fun reject(id: String) = change(id) { it.copy(status = SuggestionStatus.REJECTED) }
    fun mergeWithExisting(id: String, itemId: String): AacSuggestion = change(id) { value ->
        require(catalog().any { it.id == itemId }) { "Neznan AAC ID" }
        value.copy(status = SuggestionStatus.MERGED_WITH_EXISTING, duplicateOfItemId = itemId)
    }
    fun duplicate(value: AacSuggestion) = AacDuplicateDetector.detect(value, catalog())
    private fun checked(value: AacSuggestion): AacSuggestion {
        require(value.labelSl.isNotBlank() && value.normalizedMeaning.isNotBlank()) { "Pomen in SL label sta obvezna" }
        require(value.confidence.isFinite() && value.confidence in 0.0..1.0)
        require(listOf(value.id, value.normalizedMeaning, value.labelSl, value.labelUk, value.speechSl, value.speechUk,
            value.sourceType, value.sourceContext, value.adminNote, value.suggestedParentId.orEmpty()).all { it.length <= 1000 })
        require(value.semanticTags.size <= 50 && value.semanticTags.all { it.length <= 100 })
        val result = duplicate(value)
        return value.copy(duplicateOfItemId = result.itemIds.firstOrNull(), status =
            if (result.kind == DuplicateKind.EXACT_DUPLICATE) SuggestionStatus.ADMIN_REVIEW else value.status)
    }
    private fun change(id: String, transform: (AacSuggestion) -> AacSuggestion): AacSuggestion = synchronized(AiJsonStore.lock) {
        val rows = read(); val old = rows.single { it.id == id }
        val next = transform(old).copy(updatedAt = System.currentTimeMillis())
        write(rows.map { if (it.id == id) next else it }); next
    }
    private fun read(): List<AacSuggestion> = store.read().let { rows -> (0 until rows.length()).map { i ->
        val o = rows.getJSONObject(i)
        AacSuggestion(id = o.getString("id"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
            normalizedMeaning = o.getString("normalizedMeaning"), labelSl = o.getString("labelSl"), labelUk = o.getString("labelUk"),
            speechSl = o.getString("speechSl"), speechUk = o.getString("speechUk"), suggestedParentId = o.optString("suggestedParentId").ifBlank { null },
            semanticTags = o.getJSONArray("semanticTags").strings(), sourceType = o.getString("sourceType"), sourceContext = o.getString("sourceContext"),
            confidence = o.getDouble("confidence"), status = SuggestionStatus.valueOf(o.getString("status")),
            temporaryCategory = TemporaryCategory.valueOf(o.getString("temporaryCategory")), duplicateOfItemId = o.optString("duplicateOfItemId").ifBlank { null },
            adminNote = o.getString("adminNote"))
    } }
    private fun write(values: List<AacSuggestion>) = store.write(JSONArray().also { rows -> values.forEach { s ->
        rows.put(JSONObject().put("id", s.id).put("createdAt", s.createdAt).put("updatedAt", s.updatedAt)
            .put("normalizedMeaning", s.normalizedMeaning).put("labelSl", s.labelSl).put("labelUk", s.labelUk)
            .put("speechSl", s.speechSl).put("speechUk", s.speechUk).put("suggestedParentId", s.suggestedParentId.orEmpty())
            .put("semanticTags", JSONArray(s.semanticTags)).put("sourceType", s.sourceType).put("sourceContext", s.sourceContext)
            .put("confidence", s.confidence).put("status", s.status.name).put("temporaryCategory", s.temporaryCategory.name)
            .put("duplicateOfItemId", s.duplicateOfItemId.orEmpty()).put("adminNote", s.adminNote))
    } })
}

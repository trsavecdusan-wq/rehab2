package com.rehab2.aac.ai

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Call from a worker. Retention applies to reads and writes, including after reopening. */
class AacEventRepository(directory: File, private val now: () -> Long = System::currentTimeMillis) {
    companion object { const val DEFAULT_RETENTION_DAYS = 30; const val MAX_EVENTS = 10000 }
    private val store = AiJsonStore(File(directory, "events.json"))
    fun append(event: AacInteractionEvent) = synchronized(AiJsonStore.lock) {
        require(event.gridSize in 3..6)
        require(listOf(event.eventId, event.sessionId, event.sourceScreen, event.itemId.orEmpty(), event.pageId, event.languageCode)
            .all { it.length <= 160 })
        require(event.itemIdsInSentence.size <= 100 && event.itemIdsInSentence.all { it.length <= 160 })
        val rows = retained().filterNot { it.eventId == event.eventId } + event
        save(rows.filter { it.timestamp >= cutoff() && it.timestamp <= now() }.sortedBy { it.timestamp }.takeLast(MAX_EVENTS))
    }
    fun listRecent(limit: Int = 100): List<AacInteractionEvent> = synchronized(AiJsonStore.lock) {
        val rows = retained(); save(rows); rows.sortedByDescending { it.timestamp }.take(limit.coerceIn(0, MAX_EVENTS))
    }
    fun countByItem(): Map<String, Int> = listRecent(MAX_EVENTS).filter { it.eventType == AacEventType.ICON_ACTIVATED }
        .mapNotNull { it.itemId }.groupingBy { it }.eachCount()
    fun deleteOlderThan(timestamp: Long): Int = synchronized(AiJsonStore.lock) {
        val before = read(); val after = before.filter { it.timestamp >= maxOf(timestamp, cutoff()) }
        save(after); before.size - after.size
    }
    private fun cutoff() = now() - DEFAULT_RETENTION_DAYS * 86400000L
    private fun retained() = read().filter { it.timestamp >= cutoff() && it.timestamp <= now() }.sortedBy { it.timestamp }.takeLast(MAX_EVENTS)
    private fun read(): List<AacInteractionEvent> = store.read().let { rows -> (0 until rows.length()).map { index ->
        val o = rows.getJSONObject(index)
        AacInteractionEvent(o.getString("eventId"), o.getLong("timestamp"), o.getString("sessionId"),
            o.getString("sourceScreen"), o.optString("itemId").takeIf { it.isNotBlank() }, o.getString("pageId"),
            o.getInt("gridSize"), o.getString("languageCode"), o.getBoolean("isFixedRow"),
            AacEventType.valueOf(o.getString("eventType")), o.getJSONArray("itemIdsInSentence").strings())
    } }
    private fun save(events: List<AacInteractionEvent>) = store.write(JSONArray().also { rows -> events.forEach { e ->
        rows.put(JSONObject().put("eventId", e.eventId).put("timestamp", e.timestamp).put("sessionId", e.sessionId)
            .put("sourceScreen", e.sourceScreen).put("itemId", e.itemId.orEmpty()).put("pageId", e.pageId)
            .put("gridSize", e.gridSize).put("languageCode", e.languageCode).put("isFixedRow", e.isFixedRow)
            .put("eventType", e.eventType.name).put("itemIdsInSentence", JSONArray(e.itemIdsInSentence)))
    } })
}

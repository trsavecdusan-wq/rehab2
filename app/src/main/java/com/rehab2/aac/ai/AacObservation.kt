package com.rehab2.aac.ai

import android.content.Context
import com.rehab2.aac.AacFixedTopRow
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Observation only. Bounded, nonblocking queue; all persistence and preference reads off UI. */
object AacObservation {
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(256),
        { job -> Thread(job, "aac-observation").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())
    @Volatile var enabled = false
        private set
    private val sessionId = UUID.randomUUID().toString()
    fun directory(context: Context) = File(context.noBackupFilesDir, "aac_ai_v1")
    fun initialize(context: Context) {
        val app = context.applicationContext
        runCatching { worker.execute { runCatching {
            enabled = app.getSharedPreferences("aac_ai_v1", Context.MODE_PRIVATE).getBoolean("usage_observation", false)
            val dir = directory(app)
            if (dir.exists()) AacEventRepository(dir).deleteOlderThan(System.currentTimeMillis() - 30 * 86400000L)
        } } }
    }
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("aac_ai_v1", Context.MODE_PRIVATE).edit().putBoolean("usage_observation", value).apply()
        enabled = value
    }
    fun record(context: Context, screen: String, type: AacEventType, itemId: String? = null,
               pageId: String, gridSize: Int, languageCode: String, sentenceIds: List<String> = emptyList()) {
        if (!enabled) return
        runCatching {
            val app = context.applicationContext
            val event = AacInteractionEvent(sessionId = sessionId, sourceScreen = screen, itemId = itemId,
                pageId = pageId, gridSize = gridSize, languageCode = languageCode,
                isFixedRow = itemId in AacFixedTopRow.rowIds(gridSize), eventType = type, itemIdsInSentence = sentenceIds.toList())
            worker.execute { runCatching { if (enabled) AacEventRepository(directory(app)).append(event) } }
        }
    }
}

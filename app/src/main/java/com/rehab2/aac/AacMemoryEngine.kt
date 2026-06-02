package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File

object AacMemoryEngine {
    private const val MEMORY_FILE = "NovaRehab/data/aac_memory.json"

    data class Transition(
        val rootId: String,
        val nextItemId: String,
        val count: Int,
        val lastUsedAt: Long
    )

    fun recordTransition(
        context: Context,
        rootId: String,
        nextItemId: String
    ): Boolean {
        val safeRootId = rootId.trim()
        val safeNextItemId = nextItemId.trim()
        if (safeRootId.isBlank() || safeNextItemId.isBlank()) {
            return false
        }

        val file = memoryFile(context) ?: return false
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return false
                }
            }
            val memory = readMemory(file)
            val root = memory.optJSONObject(safeRootId) ?: JSONObject().also { newRoot ->
                memory.put(safeRootId, newRoot)
            }
            val transition = root.optJSONObject(safeNextItemId) ?: JSONObject()
            val count = transition.optInt("count", 0).coerceAtLeast(0) + 1
            transition
                .put("count", count)
                .put("lastUsedAt", System.currentTimeMillis())
            root.put(safeNextItemId, transition)
            file.writeText(memory.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun topTransitions(
        context: Context,
        rootId: String,
        limit: Int
    ): List<Transition> {
        val safeRootId = rootId.trim()
        if (safeRootId.isBlank() || limit <= 0) {
            return emptyList()
        }

        val file = memoryFile(context) ?: return emptyList()
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        return try {
            val root = readMemory(file).optJSONObject(safeRootId) ?: return emptyList()
            root.keys().asSequence()
                .mapNotNull { nextItemId ->
                    val transition = root.optJSONObject(nextItemId) ?: return@mapNotNull null
                    Transition(
                        rootId = safeRootId,
                        nextItemId = nextItemId,
                        count = transition.optInt("count", 0).coerceAtLeast(0),
                        lastUsedAt = transition.optLong("lastUsedAt", 0L).coerceAtLeast(0L)
                    )
                }
                .filter { transition -> transition.count > 0 }
                .sortedWith(
                    compareByDescending<Transition> { transition -> transition.count }
                        .thenByDescending { transition -> transition.lastUsedAt }
                        .thenBy { transition -> transition.nextItemId }
                )
                .take(limit)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun memoryFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, MEMORY_FILE)
    }

    private fun readMemory(file: File): JSONObject {
        if (!file.exists() || !file.isFile) {
            return JSONObject()
        }
        val raw = file.readText(Charsets.UTF_8).trim()
        if (raw.isBlank()) {
            return JSONObject()
        }
        return JSONObject(raw)
    }
}

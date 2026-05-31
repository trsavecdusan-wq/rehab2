package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacUsageStats {
    private const val USAGE_STATS_FILE = "NovaRehab/data/aac_usage_stats.json"
    private const val MIN_TOP_SUGGESTION_USE_COUNT = 5

    data class Entry(
        val itemId: String,
        val useCount: Int,
        val lastUsedAt: Long
    )

    fun recordUse(context: Context, itemId: String, now: Long = System.currentTimeMillis()): Boolean {
        val safeItemId = itemId.trim()
        if (safeItemId.isEmpty()) {
            return false
        }
        val file = usageStatsFile(context) ?: return false
        val entries = loadEntries(file).toMutableMap()
        val existing = entries[safeItemId]
        entries[safeItemId] = Entry(
            itemId = safeItemId,
            useCount = ((existing?.useCount ?: 0) + 1).coerceAtLeast(1),
            lastUsedAt = now.coerceAtLeast(existing?.lastUsedAt ?: 0L)
        )
        return writeEntries(file, entries.values)
    }

    fun sortByUsage(context: Context, items: List<AacItem>): List<AacItem> {
        if (items.size < 2) {
            return items
        }
        val file = usageStatsFile(context) ?: return items
        val entries = loadEntries(file)
        if (entries.isEmpty()) {
            return items
        }
        return items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AacItem>> { indexed ->
                    entries[indexed.value.id]?.useCount ?: 0
                }.thenByDescending { indexed ->
                    entries[indexed.value.id]?.lastUsedAt ?: 0L
                }.thenBy { indexed ->
                    indexed.index
                }
            )
            .map { indexed -> indexed.value }
    }

    fun topSuggestion(context: Context, itemIds: List<String>): String? {
        if (itemIds.isEmpty()) {
            return null
        }
        val entries = load(context)
        if (entries.isEmpty()) {
            return null
        }
        return itemIds.asSequence()
            .mapNotNull { itemId ->
                val entry = entries[itemId] ?: return@mapNotNull null
                if (entry.useCount >= MIN_TOP_SUGGESTION_USE_COUNT) entry else null
            }
            .maxWithOrNull(
                compareBy<Entry> { entry -> entry.useCount }
                    .thenBy { entry -> entry.lastUsedAt }
            )
            ?.itemId
    }

    fun load(context: Context): Map<String, Entry> {
        val file = usageStatsFile(context) ?: return emptyMap()
        return loadEntries(file)
    }

    private fun usageStatsFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, USAGE_STATS_FILE)
    }

    private fun loadEntries(file: File): Map<String, Entry> {
        return try {
            if (!file.exists()) {
                return emptyMap()
            }
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val itemsArray = root.optJSONArray("items") ?: JSONArray()
            val entries = mutableMapOf<String, Entry>()
            for (index in 0 until itemsArray.length()) {
                val itemObject = itemsArray.optJSONObject(index) ?: continue
                val itemId = itemObject.optString("itemId").trim()
                if (itemId.isEmpty()) {
                    continue
                }
                entries[itemId] = Entry(
                    itemId = itemId,
                    useCount = itemObject.optInt("useCount", 0).coerceAtLeast(0),
                    lastUsedAt = itemObject.optLong("lastUsedAt", 0L).coerceAtLeast(0L)
                )
            }
            entries
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeEntries(file: File, entries: Collection<Entry>): Boolean {
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return false
                }
            }
            val itemsArray = JSONArray()
            entries.sortedBy { entry -> entry.itemId }.forEach { entry ->
                itemsArray.put(
                    JSONObject()
                        .put("itemId", entry.itemId)
                        .put("useCount", entry.useCount.coerceAtLeast(0))
                        .put("lastUsedAt", entry.lastUsedAt.coerceAtLeast(0L))
                )
            }
            val root = JSONObject().put("items", itemsArray)
            file.writeText(root.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }
}

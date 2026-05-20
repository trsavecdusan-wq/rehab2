package com.rehab2.aac

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AacRepository {
    companion object {
        private const val TAG = "AacRepository"
        private const val PAGES_DIR_PATH = "/storage/emulated/0/NovaRehab2/aac/pages"
    }

    fun loadHomePage(): AacPage {
        return loadPage("home") ?: fallbackPage()
    }

    fun loadPage(pageId: String): AacPage? {
        val normalizedPageId = pageId.trim()
        if (normalizedPageId.isBlank()) {
            return null
        }

        val fileName = if (normalizedPageId == "home") "home.json" else "$normalizedPageId.json"
        val file = File(PAGES_DIR_PATH, fileName)
        if (!file.exists() || !file.isFile) {
            return null
        }

        return try {
            val json = JSONObject(file.readText())
            parsePage(json)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse AAC page: $normalizedPageId", error)
            null
        }
    }

    private fun parsePage(json: JSONObject): AacPage? {
        val pageId = json.optString("pageId").ifBlank { "home" }
        val title = json.optString("title").ifBlank { "AAC V1" }
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val itemJson = itemsJson.optJSONObject(index) ?: continue
                add(
                    AacItem(
                        id = itemJson.optString("id").ifBlank { "item_$index" },
                        labelSl = itemJson.optString("labelSl").ifBlank { "Brez oznake" },
                        imagePath = itemJson.optString("imagePath"),
                        audioSl = itemJson.optString("audioSl"),
                        actionType = itemJson.optString("actionType"),
                        targetPageId = itemJson.optString("targetPageId")
                    )
                )
            }
        }

        if (items.isEmpty()) {
            return null
        }

        return AacPage(
            pageId = pageId,
            title = title,
            items = items
        )
    }

    private fun fallbackPage(): AacPage {
        return AacPage(
            pageId = "home",
            title = "AAC V1",
            items = listOf(
                AacItem("water", "VODA", "", "", "speak", ""),
                AacItem("juice", "SOK", "", "", "speak", ""),
                AacItem("food", "HRANA", "", "", "speak", ""),
                AacItem("wc", "WC", "", "", "speak", ""),
                AacItem("help", "POMO\u010C", "", "", "speak", "")
            )
        )
    }
}

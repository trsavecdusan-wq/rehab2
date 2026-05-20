package com.rehab2.aac

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AacRepository {
    companion object {
        private const val TAG = "AacRepository"
        private const val HOME_PAGE_PATH = "/storage/emulated/0/NovaRehab2/aac/pages/home.json"
    }

    fun loadHomePage(): AacPage {
        val file = File(HOME_PAGE_PATH)
        if (!file.exists() || !file.isFile) {
            return fallbackPage()
        }

        return try {
            val json = JSONObject(file.readText())
            parsePage(json)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse AAC home page, using fallback", error)
            fallbackPage()
        }
    }

    private fun parsePage(json: JSONObject): AacPage {
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
                        actionType = itemJson.optString("actionType"),
                        targetPageId = itemJson.optString("targetPageId")
                    )
                )
            }
        }

        return AacPage(
            pageId = pageId,
            title = title,
            items = if (items.isEmpty()) fallbackPage().items else items
        )
    }

    private fun fallbackPage(): AacPage {
        return AacPage(
            pageId = "home",
            title = "AAC V1",
            items = listOf(
                AacItem("water", "VODA", "", "speak", ""),
                AacItem("juice", "SOK", "", "speak", ""),
                AacItem("food", "HRANA", "", "speak", ""),
                AacItem("wc", "WC", "", "speak", ""),
                AacItem("help", "POMOČ", "", "speak", "")
            )
        )
    }
}

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

    var lastDebugCode: String = "OK"
        private set

    var lastDebugStatus: String = "OK"
        private set

    fun loadHomePage(): AacPage {
        return loadPage("home") ?: fallbackPage()
    }

    fun loadPage(pageId: String): AacPage? {
        val normalizedPageId = pageId.trim()
        if (normalizedPageId.isBlank()) {
            updateDebugStatus(
                code = "UNKNOWN_ERROR",
                path = "",
                exists = false,
                isFile = false,
                canRead = false,
                errorMessage = "Blank pageId"
            )
            return null
        }

        val fileName = if (normalizedPageId == "home") "home.json" else "$normalizedPageId.json"
        val file = File(PAGES_DIR_PATH, fileName)
        val exists = file.exists()
        val isFile = file.isFile
        val canRead = file.canRead()
        if (!exists) {
            updateDebugStatus("FILE_NOT_FOUND", file.absolutePath, exists, isFile, canRead, null)
            return null
        }

        if (!isFile) {
            updateDebugStatus("UNKNOWN_ERROR", file.absolutePath, exists, isFile, canRead, "Path is not a file")
            return null
        }

        if (!canRead) {
            updateDebugStatus("NOT_READABLE", file.absolutePath, exists, isFile, canRead, null)
            return null
        }

        return try {
            val json = JSONObject(file.readText())
            val page = parsePage(json)
            if (page == null) {
                updateDebugStatus("JSON_ERROR", file.absolutePath, exists, isFile, canRead, "Page has no items")
            } else {
                updateDebugStatus("OK", file.absolutePath, exists, isFile, canRead, null)
            }
            page
        } catch (error: SecurityException) {
            Log.w(TAG, "Permission denied while reading AAC page: $normalizedPageId", error)
            updateDebugStatus("PERMISSION_DENIED", file.absolutePath, exists, isFile, canRead, error.message)
            null
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse AAC page: $normalizedPageId", error)
            val code = if ((error.message ?: "").contains("Permission denied", ignoreCase = true)) {
                "PERMISSION_DENIED"
            } else {
                "JSON_ERROR"
            }
            updateDebugStatus(code, file.absolutePath, exists, isFile, canRead, error.message)
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

    private fun updateDebugStatus(
        code: String,
        path: String,
        exists: Boolean,
        isFile: Boolean,
        canRead: Boolean,
        errorMessage: String?
    ) {
        lastDebugCode = code
        val base = "code=$code path=$path exists=$exists isFile=$isFile canRead=$canRead"
        lastDebugStatus = if (errorMessage.isNullOrBlank()) {
            base
        } else {
            "$base error=$errorMessage"
        }
    }
}

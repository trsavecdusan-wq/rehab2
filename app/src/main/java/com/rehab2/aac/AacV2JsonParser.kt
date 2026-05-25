package com.rehab2.aac

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object AacV2JsonParser {
    fun parse(json: JSONObject): AacV2Page {
        return AacV2Page(
            version = json.optInt("version", 2),
            pageId = json.optString("pageId"),
            title = json.optString("title"),
            promptSl = json.optNullableString("promptSl"),
            promptUk = json.optNullableString("promptUk"),
            concepts = parseConcepts(json.optJSONArray("concepts")),
            icons = parseIcons(json.optJSONArray("icons")),
            nodes = parseNodes(json.optJSONArray("nodes"))
        )
    }

    private fun parseConcepts(array: JSONArray?): List<AacConcept> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    AacConcept(
                        id = item.optString("id"),
                        baseText = item.optString("baseText"),
                        category = item.optString("category"),
                        activeIconId = item.optNullableString("activeIconId"),
                        speakTextSl = item.optNullableString("speakTextSl"),
                        speakTextUk = item.optNullableString("speakTextUk")
                    )
                )
            }
        }
    }

    private fun parseIcons(array: JSONArray?): List<AacIconVariant> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    AacIconVariant(
                        id = item.optString("id"),
                        conceptId = item.optString("conceptId"),
                        source = parseIconSource(item.optString("source")),
                        imagePath = item.optString("imagePath"),
                        label = item.optNullableString("label")
                    )
                )
            }
        }
    }

    private fun parseNodes(array: JSONArray?): List<AacConversationNode> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val nodeId = item.optString("id")
                val children = parseStringList(item.optJSONArray("children"))
                if (nodeId == WATER_NODE_ID) {
                    Log.d(TAG, "TRACE water JSON children=${children.size} ids=$children")
                }
                add(
                    AacConversationNode(
                        id = nodeId,
                        conceptId = item.optNullableString("conceptId"),
                        title = item.optString("title"),
                        speakTextSl = item.optNullableString("speakTextSl"),
                        speakTextUk = item.optNullableString("speakTextUk"),
                        questionSl = item.optNullableString("questionSl"),
                        questionUk = item.optNullableString("questionUk"),
                        children = children,
                        sentenceRole = item.optNullableString("sentenceRole")
                    )
                )
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    private fun parseIconSource(value: String): IconSource {
        return when (value.trim().uppercase()) {
            "SOCA" -> IconSource.SOCA
            "ARASAAC" -> IconSource.ARASAAC
            "PATIENT" -> IconSource.PATIENT
            "CUSTOM" -> IconSource.CUSTOM
            else -> IconSource.CUSTOM
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null

        val value = optString(name).trim()
        return value.ifEmpty { null }
    }

    private const val TAG = "AacV2JsonParser"
    private const val WATER_NODE_ID = "water"
}

package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AacEditorAudit {
    data class Problem(
        val itemId: String,
        val labelSl: String,
        val problem: String,
        val suggestion: String
    )

    fun run(context: Context): List<Problem> {
        val rawItems = AacEditorStorage.rawItemsForAudit(context)
        val items = itemObjects(rawItems)
        val itemIds = items.map { it.optString("id").trim() }
        val existingIds = itemIds.filter { it.isNotBlank() }.toSet()
        val duplicateIds = itemIds.filter { it.isNotBlank() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val childrenById = items.associate { item ->
            item.optString("id").trim() to stringList(item.optJSONArray("children"))
        }
        val reachableIds = reachableFromPageOne(items, childrenById)

        return buildList {
            items.forEach { item ->
                val itemId = item.optString("id").trim()
                val label = item.optString("labelSl").trim()
                val speech = item.optString("speakTextSl").ifBlank { item.optString("speechText") }.trim()
                val imagePath = item.optString("imagePath").trim()
                val iconSource = parseIconSource(item.optString("iconSource"))
                val children = stringList(item.optJSONArray("children"))

                if (itemId.isBlank()) {
                    add(Problem("", label, "Manjka itemId.", "Vnesi stabilen itemId."))
                }
                if (itemId in duplicateIds) {
                    add(Problem(itemId, label, "Podvojen itemId.", "Preimenuj ali odstrani podvojeni lokalni item."))
                }
                if (label.isBlank()) {
                    add(Problem(itemId, label, "Manjka labelSl.", "Dodaj napis pod ikono."))
                }
                if (speech.isBlank()) {
                    add(Problem(itemId, label, "Manjka govor.", "Dodaj speechTextSl."))
                }
                if (imagePath.isBlank()) {
                    add(Problem(itemId, label, "Ikona je brez slike.", "Dodaj sliko ali pusti namenoma text-only."))
                } else {
                    val imageFile = AacStoragePaths.resolveIconFile(context, imagePath, iconSource)
                    if (imageFile?.isFile != true) {
                        add(Problem(itemId, label, "imagePath kaže na manjkajočo datoteko.", "Izberi obstoječo sliko."))
                    }
                }
                children.forEach { childId ->
                    if (childId !in existingIds) {
                        add(Problem(itemId, label, "Child kaže na neobstoječ item: $childId.", "Odstrani povezavo ali ustvari manjkajoči item."))
                    }
                }
                if (item.optBoolean("hidden", false)) {
                    add(Problem(itemId, label, "Item je označen kot SKRITO.", "Po potrebi ga pokaži v editorju."))
                }
                if (itemId.isNotBlank() && itemId !in reachableIds && item.optInt("fixedTopRowPosition", 0) !in 1..5) {
                    add(Problem(itemId, label, "Item ni dosegljiv iz page_1.", "Dodaj ga pod ustrezen parent ali ga pusti kot legacy."))
                }
            }
        }
    }

    fun format(problems: List<Problem>): String {
        if (problems.isEmpty()) return "Ni najdenih težav."
        return problems.joinToString("\n\n") { problem ->
            buildString {
                appendLine(problem.itemId.ifBlank { "(brez itemId)" })
                appendLine(problem.labelSl.ifBlank { "(brez labelSl)" })
                appendLine(problem.problem)
                append("Predlog: ${problem.suggestion}")
            }
        }
    }

    private fun reachableFromPageOne(
        items: List<JSONObject>,
        childrenById: Map<String, List<String>>
    ): Set<String> {
        val roots = items
            .filter { item -> item.optInt("fixedTopRowPosition", 0) in 1..5 || hasPageOnePlacement(item) }
            .mapNotNull { it.optString("id").trim().takeIf(String::isNotBlank) }
        val reachable = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        roots.forEach { pending.add(it) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!reachable.add(id)) continue
            childrenById[id].orEmpty().forEach { childId ->
                if (childId !in reachable) pending.add(childId)
            }
        }
        return reachable
    }

    private fun hasPageOnePlacement(item: JSONObject): Boolean {
        val placements = item.optJSONArray("placements") ?: return false
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (placement.optString("pageId") == "page_1") return true
        }
        return false
    }

    private fun parseIconSource(value: String): IconSource {
        return runCatching { IconSource.valueOf(value.ifBlank { IconSource.SYSTEM.name }) }.getOrDefault(IconSource.SYSTEM)
    }

    private fun itemObjects(array: JSONArray): List<JSONObject> {
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}

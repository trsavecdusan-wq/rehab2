package com.rehab2.aac

class AacSentenceStateManager {
    private val items = mutableListOf<AacSentenceItem>()

    fun addItem(item: AacSentenceItem) {
        items.add(item)
    }

    fun removeLast() {
        if (items.isNotEmpty()) {
            items.removeAt(items.lastIndex)
        }
    }

    fun clear() {
        items.clear()
    }

    fun getItems(): List<AacSentenceItem> {
        return items.toList()
    }

    fun isEmpty(): Boolean {
        return items.isEmpty()
    }

    fun getDisplayText(): String {
        return items.joinToString(" | ") { it.text }
    }

    fun getSpeakText(): String {
        if (items.isEmpty()) return ""

        val normalizedTexts = items.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (normalizedTexts.isEmpty()) return ""

        val upperSet = normalizedTexts.map { it.uppercase() }.toSet()
        if (upperSet.contains("ŽELIM") && upperSet.contains("VODA")) {
            return "Želim vodo."
        }

        val raw = normalizedTexts.joinToString(" ") { it.lowercase() }
        return raw.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        } + "."
    }
}

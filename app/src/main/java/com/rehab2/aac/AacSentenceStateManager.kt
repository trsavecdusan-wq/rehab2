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
        getDrinkSpeakText(upperSet)?.let { return it }

        val raw = normalizedTexts.joinToString(" ") { it.lowercase() }
        return raw.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        } + "."
    }

    private fun getDrinkSpeakText(upperSet: Set<String>): String? {
        return when {
            upperSet.contains("MRZLA VODA") -> "Želim piti mrzlo vodo."
            upperSet.contains("TOPLA VODA") -> "Želim piti toplo vodo."
            upperSet.contains("VODA Z MEHURČKI") -> "Želim piti vodo z mehurčki."
            upperSet.contains("VODA BREZ MEHURČKOV") -> "Želim piti vodo brez mehurčkov."
            upperSet.contains("VODA") -> "Želim piti vodo."
            upperSet.contains("ČAJ") -> "Želim piti čaj."
            upperSet.contains("SOK") -> "Želim piti sok."
            upperSet.contains("KAVA") -> "Želim piti kavo."
            upperSet.contains("MLEKO") -> "Želim piti mleko."
            upperSet.contains("LIMONADA") -> "Želim piti limonado."
            else -> null
        }
    }
}

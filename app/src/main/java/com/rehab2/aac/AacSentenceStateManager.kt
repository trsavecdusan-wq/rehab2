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
        return getSpeakText(AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun getSpeakText(languageCode: String): String {
        if (items.isEmpty()) return ""

        val normalizedTexts = items.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (normalizedTexts.isEmpty()) return ""

        if (AacLanguageResolver.normalize(languageCode) != AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return buildSimpleSentence(normalizedTexts)
        }

        val upperSet = normalizedTexts.map { it.uppercase() }.toSet()
        getDrinkSpeakText(upperSet)?.let { return it }

        return buildSimpleSentence(normalizedTexts)
    }

    private fun buildSimpleSentence(normalizedTexts: List<String>): String {
        val raw = normalizedTexts.joinToString(" ") { text ->
            text.trim().trimEnd('.', '!', '?').lowercase()
        }
        return raw.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        } + "."
    }

    private fun getDrinkSpeakText(upperSet: Set<String>): String? {
        return when {
            upperSet.contains("MRZLA VODA") -> "Rada bi mrzlo vodo."
            upperSet.contains("TOPLA VODA") -> "Rada bi toplo vodo."
            upperSet.contains("VODA Z MEHURČKI") -> "Rada bi vodo z mehurčki."
            upperSet.contains("VODA BREZ MEHURČKOV") -> "Rada bi vodo brez mehurčkov."
            upperSet.contains("VODA") -> "Rada bi vodo."
            upperSet.contains("ČAJ") -> "Rada bi čaj."
            upperSet.contains("SOK") -> "Rada bi sok."
            upperSet.contains("KAVA") -> "Rada bi kavo."
            upperSet.contains("MLEKO") -> "Rada bi mleko."
            upperSet.contains("LIMONADA") -> "Rada bi limonado."
            else -> null
        }
    }
}

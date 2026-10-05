package com.rehab2.aac.ai

import com.rehab2.aac.AacItem
import java.text.Normalizer
import java.util.Locale

enum class DuplicateKind { NO_DUPLICATE, EXACT_DUPLICATE, POSSIBLE_DUPLICATE }
data class DuplicateResult(val kind: DuplicateKind, val itemIds: List<String> = emptyList())

object AacDuplicateDetector {
    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun detect(suggestion: AacSuggestion, catalog: List<AacItem>): DuplicateResult {
        val queries = listOf(suggestion.id, suggestion.normalizedMeaning, suggestion.labelSl, suggestion.labelUk,
            suggestion.speechSl, suggestion.speechUk).map(::normalize).filter { it.isNotBlank() }.toSet()
        val tags = suggestion.semanticTags.map(::normalize).filter { it.isNotBlank() }.toSet()
        val exact = mutableListOf<String>(); val possible = mutableListOf<String>()
        catalog.forEach { item ->
            val values = (listOf(item.id, item.meaning.orEmpty(), item.meaningId.orEmpty(), item.conceptId.orEmpty(),
                item.labelSl, item.labelUk.orEmpty(), item.speakTextSl.orEmpty(), item.speakTextUk.orEmpty(), item.speechText.orEmpty()) +
                item.labelByLanguage.values + item.speechTextByLanguage.values).map(::normalize).filter { it.isNotBlank() }.toSet()
            val metadata = (item.semanticTags + item.searchKeywordsByLanguage.values.flatten()).map(::normalize).filter { it.isNotBlank() }.toSet()
            when {
                queries.intersect(values).isNotEmpty() -> exact += item.id
                queries.intersect(metadata).isNotEmpty() || tags.intersect(values + metadata).isNotEmpty() -> possible += item.id
            }
        }
        return when {
            exact.isNotEmpty() -> DuplicateResult(DuplicateKind.EXACT_DUPLICATE, exact.distinct().sorted())
            possible.isNotEmpty() -> DuplicateResult(DuplicateKind.POSSIBLE_DUPLICATE, possible.distinct().sorted())
            else -> DuplicateResult(DuplicateKind.NO_DUPLICATE)
        }
    }
}

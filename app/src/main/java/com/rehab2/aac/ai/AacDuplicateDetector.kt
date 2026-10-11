package com.rehab2.aac.ai

import com.rehab2.aac.AacItem
import java.text.Normalizer
import java.util.Locale

enum class DuplicateKind { NO_DUPLICATE, EXACT_DUPLICATE, POSSIBLE_DUPLICATE }
data class DuplicateResult(val kind: DuplicateKind, val itemIds: List<String> = emptyList())

enum class MeaningDuplicateKind { NO_DUPLICATE, EXACT_MEANING_DUPLICATE, POSSIBLE_MEANING_DUPLICATE, SAME_LABEL_DIFFERENT_CONTEXT }
data class MeaningDuplicateMatch(val itemId: String, val kind: MeaningDuplicateKind)
data class MeaningDuplicateResult(val kind: MeaningDuplicateKind, val matches: List<MeaningDuplicateMatch>)

/** Explicit input for catalog/import review; empty metadata never establishes a match. */
data class AacMeaningDescriptor(
    val id: String, val meaningId: String = "", val normalizedMeaning: String = "",
    val labelSl: String = "", val labelUk: String = "", val speechSl: String = "", val speechUk: String = "",
    val aliases: List<String> = emptyList(), val semanticTags: List<String> = emptyList(),
    val category: String = "", val contexts: List<String> = emptyList(), val otherValues: List<String> = emptyList()
)

object AacDuplicateDetector {
    private val marks = Regex("\\p{M}+")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")
    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(marks, "").replace(punctuation, " ").trim().replace(spaces, " ")

    fun detect(suggestion: AacSuggestion, catalog: List<AacItem>): DuplicateResult {
        val result = detectMeaning(AacMeaningDescriptor(id = suggestion.id, normalizedMeaning = suggestion.normalizedMeaning,
            labelSl = suggestion.labelSl, labelUk = suggestion.labelUk, speechSl = suggestion.speechSl, speechUk = suggestion.speechUk,
            semanticTags = suggestion.semanticTags, contexts = listOfNotNull(suggestion.suggestedParentId)), catalog.map(::describe))
        // Preserve the 794/796 API. Context collisions require explicit review, never automatic merging.
        val kind = when (result.kind) {
            MeaningDuplicateKind.NO_DUPLICATE -> DuplicateKind.NO_DUPLICATE
            MeaningDuplicateKind.EXACT_MEANING_DUPLICATE -> DuplicateKind.EXACT_DUPLICATE
            else -> DuplicateKind.POSSIBLE_DUPLICATE
        }
        return DuplicateResult(kind, result.matches.filter { kind != DuplicateKind.EXACT_DUPLICATE || it.kind == MeaningDuplicateKind.EXACT_MEANING_DUPLICATE }.map { it.itemId })
    }

    fun describe(item: AacItem) = AacMeaningDescriptor(item.id, item.meaningId.orEmpty(), item.meaning.orEmpty(),
        item.labelSl, item.labelUk.orEmpty(), item.speakTextSl.orEmpty(), item.speakTextUk.orEmpty(),
        item.searchKeywordsByLanguage.values.flatten(), item.semanticTags, item.categoryId.orEmpty(),
        listOfNotNull(item.parentId) + item.visibleUnderIds,
        listOf(item.conceptId.orEmpty(), item.speechText.orEmpty()) + item.labelByLanguage.values + item.speechTextByLanguage.values)

    fun detectMeaning(query: AacMeaningDescriptor, catalog: List<AacMeaningDescriptor>): MeaningDuplicateResult {
        val q = Prepared(query)
        val matches = catalog.mapNotNull { descriptor ->
            val item = Prepared(descriptor)
            val differentContext = (q.contexts.isNotEmpty() && item.contexts.isNotEmpty() && q.contexts.intersect(item.contexts).isEmpty()) ||
                (q.category.isNotEmpty() && item.category.isNotEmpty() && q.category != item.category) ||
                (q.meaningId.isNotEmpty() && item.meaningId.isNotEmpty() && q.meaningId != item.meaningId)
            val kind = when {
                q.keys.intersect(item.keys).isNotEmpty() -> MeaningDuplicateKind.EXACT_MEANING_DUPLICATE
                q.values.intersect(item.values).isNotEmpty() -> if (differentContext) MeaningDuplicateKind.SAME_LABEL_DIFFERENT_CONTEXT else MeaningDuplicateKind.EXACT_MEANING_DUPLICATE
                q.values.intersect(item.metadata).isNotEmpty() || q.metadata.intersect(item.values + item.metadata).isNotEmpty() -> MeaningDuplicateKind.POSSIBLE_MEANING_DUPLICATE
                else -> return@mapNotNull null
            }
            MeaningDuplicateMatch(descriptor.id, kind)
        }.distinct().sortedWith(compareBy({ it.kind.ordinal }, { it.itemId }))
        return MeaningDuplicateResult(matches.firstOrNull()?.kind ?: MeaningDuplicateKind.NO_DUPLICATE, matches)
    }

    private fun tokens(values: List<String>) = values.map(::normalize).filter { it.isNotEmpty() }.toSet()
    private class Prepared(d: AacMeaningDescriptor) {
        val keys = tokens(listOf(d.id, d.meaningId, d.normalizedMeaning))
        val values = keys + tokens(listOf(d.labelSl, d.labelUk, d.speechSl, d.speechUk) + d.otherValues)
        val metadata = tokens(d.aliases + d.semanticTags)
        val contexts = tokens(d.contexts)
        val category = normalize(d.category)
        val meaningId = normalize(d.meaningId)
    }
}

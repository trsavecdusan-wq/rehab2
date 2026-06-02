package com.rehab2.aac

import android.content.Context

object AacContextSuggestions {
    private val FIXED_TOP_ROW_IDS = setOf(
        "no",
        "yes",
        "dont_understand",
        "thank_you",
        "sorry"
    )

    fun suggest(
        context: Context,
        currentPageId: String,
        visibleItems: List<AacItem>,
        recentItems: List<AacItem>
    ): List<String> {
        val settings = AacAssistSettings.load(context)
        if (!settings.enabled || !settings.showSuggestions) {
            return emptyList()
        }

        val candidates = safeCandidates(visibleItems, recentItems)
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val usageEntries = AacUsageStats.load(context)
        if (usageEntries.isEmpty() && recentItems.isEmpty()) {
            return emptyList()
        }

        val topSuggestionId = AacUsageStats.topSuggestion(context, candidates.map { item -> item.id })
        val recentChildIds = recentItems.lastOrNull()?.children.orEmpty().toSet()
        val pageBias = currentPageId.trim().takeIf { it.isNotBlank() }

        return candidates.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AacItem>> { indexed ->
                    if (indexed.value.id == topSuggestionId) 1 else 0
                }.thenByDescending { indexed ->
                    if (indexed.value.id in recentChildIds) 1 else 0
                }.thenByDescending { indexed ->
                    usageEntries[indexed.value.id]?.useCount ?: 0
                }.thenByDescending { indexed ->
                    usageEntries[indexed.value.id]?.lastUsedAt ?: 0L
                }.thenBy { indexed ->
                    if (pageBias != null && indexed.value.visibleUnderIds.contains(pageBias)) 0 else 1
                }.thenBy { indexed ->
                    indexed.index
                }
            )
            .map { indexed -> indexed.value.id }
            .take(settings.maxSuggestions.coerceIn(1, 3))
    }

    private fun safeCandidates(
        visibleItems: List<AacItem>,
        recentItems: List<AacItem>
    ): List<AacItem> {
        if (visibleItems.isEmpty()) {
            return emptyList()
        }
        val visibleById = visibleItems.associateBy { item -> item.id }
        val recentChildIds = recentItems.lastOrNull()?.children.orEmpty().toSet()
        val visibleCandidateIds = visibleItems.map { item -> item.id }.toSet()
        val safeCandidateIds = (visibleCandidateIds + recentChildIds)
            .filter { itemId -> itemId !in FIXED_TOP_ROW_IDS }
            .distinct()
        return safeCandidateIds.mapNotNull { itemId -> visibleById[itemId] }
    }
}

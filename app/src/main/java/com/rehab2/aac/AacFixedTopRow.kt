package com.rehab2.aac

import java.util.Collections
import org.json.JSONArray

/** Motor-memory invariant. Suggestions may only receive non-fixed content. */
object AacFixedTopRow {
    val ids: List<String> = Collections.unmodifiableList(listOf("yes", "dont_understand", "no", "thank_you", "help"))
    val positions: Map<String, Int> = Collections.unmodifiableMap(ids.mapIndexed { index, id -> id to index + 1 }.toMap())

    // Existing Core V2 HOME placement: wc -> slot 6. Never infer this from ranked page content.
    const val HOME_SLOT_SIX_ID = "wc"
    val protectedIds: Set<String> = Collections.unmodifiableSet((ids + HOME_SLOT_SIX_ID).toSet())

    fun rowIds(columns: Int): List<String> {
        require(columns in 3..6)
        return (ids + HOME_SLOT_SIX_ID).take(columns)
    }

    fun rowItems(available: Collection<AacItem>, columns: Int): List<AacItem> {
        val byId = available.associateBy { it.id }
        return rowIds(columns).mapIndexed { index, id ->
            (byId[id] ?: defaults.getValue(id)).copy(
                fixedTopRowPosition = index + 1, protectedPlacement = true,
                isRootItem = true, isHiddenUntilParent = false
            )
        }
    }

    fun dynamicItems(items: List<AacItem>, catalog: Collection<AacItem>, columns: Int): List<AacItem> {
        val fixedIds = rowIds(columns).toSet()
        // At 3/4 columns the remaining canonical icons stay available as ordinary content.
        val overflow = this.items(catalog).drop(columns)
        return (overflow + items).filter { it.id !in fixedIds }.distinctBy { it.id }
            .map { it.copy(fixedTopRowPosition = null) }
    }

    fun dynamicCapacity(columns: Int): Int {
        require(columns in 3..6)
        return columns * (columns - 1)
    }

    private val defaults by lazy { AacStarterContentV1.items().associateBy { it.id } }

    /** Resolve independently of page visibility/ranking; recover missing items from shipped content. */
    fun items(available: Collection<AacItem>): List<AacItem> {
        val byId = available.associateBy { it.id }
        return ids.map { id ->
            (byId[id] ?: defaults.getValue(id)).copy(
                fixedTopRowPosition = positions.getValue(id), protectedPlacement = true,
                isRootItem = true, isHiddenUntilParent = false
            )
        }
    }

    fun content(items: List<AacItem>): List<AacItem> = items
        .filter { it.id !in positions }
        .map { if (it.fixedTopRowPosition == null) it else it.copy(fixedTopRowPosition = null) }

    /** Used by bootstrap, upgrade, repair and editor writes. Never trusts imported slot metadata. */
    fun repairMetadata(items: JSONArray): Int {
        var changed = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.has("fixed_top_row_position")) {
                item.remove("fixed_top_row_position")
                changed++
            }
            val position = positions[item.optString("id").trim()]
            if (position != null) {
                if (item.optInt("fixedTopRowPosition", 0) != position) {
                    item.put("fixedTopRowPosition", position)
                    changed++
                }
            } else if (item.has("fixedTopRowPosition")) {
                item.remove("fixedTopRowPosition")
                changed++
            }
        }
        return changed
    }
}

package com.rehab2.aac

/** Display-only positions. No persistent patient records or links are moved/deleted. */
object AacCareLayout {
    val toilet = mapOf("nurse_help" to 7, "wc_pee" to 16, "wc_poop" to 18,
        "wc_wet" to 20, "wc_diaper_change" to 22, "wc_dirty" to 24,
        "wc_wet_and_dirty" to 17)
    val nurse = mapOf("help_washing" to 11, "help_showering" to 13,
        "help_dressing" to 21, "noticed_blood" to 23)

    fun place(items: List<AacItem>, fixed: List<AacItem>, positions: Map<String, Int>): List<AacItem?> {
        val slots = MutableList<AacItem?>(25) { null }
        fixed.take(5).forEachIndexed { i, item -> slots[i] = item }
        val fixedIds = fixed.map { it.id }.toSet()
        val content = items.filter { it.id !in fixedIds }.distinctBy { it.id }
        content.forEach { item -> positions[item.id]?.let { slots[it] = item } }
        val placed = slots.filterNotNull().map { it.id }.toSet()
        content.filter { it.id !in placed }.zip((5..24).filter { slots[it] == null })
            .forEach { (item, index) -> slots[index] = item }
        return slots
    }
}

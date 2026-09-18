package com.rehab2.aac

/** The same locked order is used by seeding, upgrade repair and both patient screens. */
object AacFixedTopRow {
    val ids = listOf("yes", "dont_understand", "no", "thank_you", "help")
    val positions = ids.mapIndexed { index, id -> id to index + 1 }.toMap()
}

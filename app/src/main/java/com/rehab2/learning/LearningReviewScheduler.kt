package com.rehab2.learning

import java.util.TimeZone

/** Leitner boxes 1..5. Time is always passed in; the scheduler never reads the clock. */
object LearningReviewScheduler {
    val INTERVAL_DAYS = intArrayOf(1, 2, 4, 7, 14)
    const val MIN_BOX = 1
    const val MAX_BOX = 5
    private const val DAY_MS = 86_400_000L

    // Kotlin floorDiv instead of Math.floorDiv(long, long), which needs API 24.
    fun epochDay(timestampMs: Long, timeZone: TimeZone): Long =
        (timestampMs + timeZone.getOffset(timestampMs)).floorDiv(DAY_MS)

    fun apply(existing: ReviewEntry?, patientId: String, itemId: String, taskType: LearningTaskType,
              outcome: LearningOutcome, todayEpochDay: Long, now: Long): ReviewEntry? {
        val currentBox = (existing?.box ?: MIN_BOX).coerceIn(MIN_BOX, MAX_BOX)
        val (box, days) = when (outcome) {
            LearningOutcome.SKIPPED -> return existing
            LearningOutcome.INDEPENDENT -> {
                val next = minOf(currentBox + 1, MAX_BOX)
                next to INTERVAL_DAYS[next - 1]
            }
            LearningOutcome.WITH_HINT, LearningOutcome.ASSISTED -> currentBox to 1
            LearningOutcome.INCORRECT, LearningOutcome.NO_RESPONSE -> MIN_BOX to 1
        }
        return ReviewEntry(patientId, itemId, taskType, box, todayEpochDay + days, outcome, now)
    }

    fun due(entries: List<ReviewEntry>, todayEpochDay: Long): List<ReviewEntry> =
        entries.filter { it.nextDueEpochDay <= todayEpochDay }
            .sortedWith(compareBy<ReviewEntry>({ it.nextDueEpochDay }, { it.box }, { it.itemId }))
}

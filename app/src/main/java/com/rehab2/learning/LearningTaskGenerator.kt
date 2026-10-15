package com.rehab2.learning

import kotlin.random.Random

data class LearningCandidate(
    val itemId: String,
    val meaningGroup: String?,
    val hasImage: Boolean,
    val learningModeEnabled: Boolean,
    val activeForPatient: Boolean,
    val presentations: Set<String>
)

data class PlannedTask(val task: LearningTask, val shownItemIds: List<String>)

/** Knows nothing about AacItem; the AAC bridge arrives in phase 2. Same Random seed gives the same session. */
object LearningTaskGenerator {
    fun isUsable(c: LearningCandidate): Boolean =
        c.learningModeEnabled && c.activeForPatient && c.hasImage

    fun allowsPresentation(c: LearningCandidate, presentation: String): Boolean {
        if (presentation !in LearningPresentation.ALL) return false
        val allowed = c.presentations.ifEmpty { LearningPresentation.DEFAULT_WHEN_EMPTY }
        return presentation in allowed
    }

    fun distractors(target: LearningCandidate, pool: List<LearningCandidate>,
                    strategy: DistractorStrategy, count: Int, random: Random): List<String> {
        if (count <= 0) return emptyList()
        val usable = pool.filter { isUsable(it) && it.itemId != target.itemId }
            .distinctBy { it.itemId }
            .sortedBy { it.itemId }
        val group = target.meaningGroup
        val (sameGroup, otherGroup) = usable.partition { group != null && it.meaningGroup == group }
        val ordered = when (strategy) {
            DistractorStrategy.SAME_GROUP -> sameGroup.shuffled(random) + otherGroup.shuffled(random)
            DistractorStrategy.OTHER_GROUP -> otherGroup.shuffled(random) + sameGroup.shuffled(random)
        }
        return ordered.take(count).map { it.itemId }
    }

    fun planSession(settings: LearningSettings, tasks: List<LearningTask>, reviews: List<ReviewEntry>,
                    candidatesById: Map<String, LearningCandidate>, todayEpochDay: Long,
                    random: Random): List<PlannedTask> {
        val s = settings.normalized()
        val eligible = tasks.filter { it.enabled && it.taskType in s.allowedTaskTypes }
        val reviewByKey = reviews.associateBy { it.itemId to it.taskType }
        fun key(task: LearningTask) = task.targetItemId to task.taskType

        val ordered = ArrayList<LearningTask>()
        for (entry in LearningReviewScheduler.due(reviews, todayEpochDay)) {
            eligible.filter { key(it) == (entry.itemId to entry.taskType) && it !in ordered }.forEach { ordered += it }
        }
        eligible.filter { key(it) !in reviewByKey && it !in ordered }.forEach { ordered += it }

        val pool = candidatesById.values.sortedBy { it.itemId }
        val planned = ArrayList<PlannedTask>()
        for (task in ordered) {
            if (planned.size >= s.sessionMaxTasks) break
            plan(task, s, pool, candidatesById, random)?.let { planned += it }
        }

        // End on an already known item: the highest box moves last (new tasks count as box 0, ties keep the later one).
        if (planned.size >= 2) {
            val boxes = planned.map { reviewByKey[key(it.task)]?.box ?: 0 }
            val best = boxes.indices.last { boxes[it] == boxes.max() }
            planned.add(planned.removeAt(best))
        }
        return planned
    }

    private fun plan(task: LearningTask, s: LearningSettings, pool: List<LearningCandidate>,
                     candidatesById: Map<String, LearningCandidate>, random: Random): PlannedTask? {
        val target = candidatesById[task.targetItemId]?.takeIf(::isUsable) ?: return null
        if (task.presentation == LearningPresentation.TEXT_ONLY && !s.allowTextOnly) return null
        if (!allowsPresentation(target, task.presentation)) return null

        if (task.taskType == LearningTaskType.YES_NO_QUESTION) {
            val question = task.questionItemId?.let { candidatesById[it] } ?: return null
            if (!isUsable(question)) return null
            return PlannedTask(task, listOf(target.itemId))
        }

        val needed = s.choiceCount - 1
        val manual = task.distractorItemIds.distinct()
            .filter { it != target.itemId }
            .mapNotNull { candidatesById[it] }
            .filter(::isUsable)
            .take(needed)
            .map { it.itemId }
        val chosen = manual.toSet()
        val automatic = distractors(target, pool.filter { it.itemId !in chosen }, task.distractorStrategy,
            needed - manual.size, random)
        val shown = (listOf(target.itemId) + manual + automatic).shuffled(random)
        return if (shown.size < 2) null else PlannedTask(task, shown)
    }
}

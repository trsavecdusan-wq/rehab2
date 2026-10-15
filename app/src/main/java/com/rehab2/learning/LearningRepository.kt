package com.rehab2.learning

import java.io.File

/**
 * Learning data in its own internal directory. Never touches AAC files.
 * A file with a newer schemaVersion or unreadable JSON is loaded as empty and never overwritten.
 */
class LearningRepository(private val directory: File) {
    companion object {
        const val TASKS_FILE = "tasks.json"
        private val lock = Any()

        fun safeId(patientId: String): String =
            patientId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "legacy" }

        fun settingsFileName(patientId: String) = "settings_${safeId(patientId)}.json"
        fun attemptsFileName(patientId: String) = "attempts_${safeId(patientId)}.json"
        fun reviewFileName(patientId: String) = "review_${safeId(patientId)}.json"
    }

    private val unsupported = HashSet<String>()
    private val corrupt = HashSet<String>()

    /** File names whose schemaVersion is newer than this app understands. */
    val unsupportedFiles: Set<String> get() = synchronized(lock) { unsupported.toSet() }
    /** File names that could not be parsed at all. */
    val corruptFiles: Set<String> get() = synchronized(lock) { corrupt.toSet() }

    fun loadTasks(): List<LearningTask> = load(TASKS_FILE, LearningJsonCodec::decodeTasks)

    fun saveTasks(tasks: List<LearningTask>) =
        save(TASKS_FILE, LearningJsonCodec.encodeTasks(tasks), LearningJsonCodec::decodeTasks)

    fun loadSettings(patientId: String): LearningSettings =
        load(settingsFileName(patientId), LearningJsonCodec::decodeSettings)
            .firstOrNull { it.patientId == patientId }
            ?: LearningSettings(patientId)

    fun saveSettings(settings: LearningSettings) =
        save(settingsFileName(settings.patientId), LearningJsonCodec.encodeSettings(listOf(settings)),
            LearningJsonCodec::decodeSettings)

    fun loadAttempts(patientId: String): List<LearningAttempt> =
        load(attemptsFileName(patientId), LearningJsonCodec::decodeAttempts)

    fun loadReviews(patientId: String): List<ReviewEntry> =
        load(reviewFileName(patientId), LearningJsonCodec::decodeReviews)

    /** Appends the attempt and updates its review entry with LearningReviewScheduler. */
    fun recordAttempt(attempt: LearningAttempt, todayEpochDay: Long) = synchronized(lock) {
        val attemptsFile = attemptsFileName(attempt.patientId)
        val reviewFile = reviewFileName(attempt.patientId)
        val attempts = load(attemptsFile, LearningJsonCodec::decodeAttempts)
        val reviews = load(reviewFile, LearningJsonCodec::decodeReviews)
        // Check both files before writing either, so an attempt is never stored without its review update.
        for (name in listOf(attemptsFile, reviewFile)) {
            check(name !in unsupported && name !in corrupt) { "Learning file $name cannot be written" }
        }
        save(attemptsFile, LearningJsonCodec.encodeAttempts(attempts + attempt), LearningJsonCodec::decodeAttempts)

        val existing = reviews.firstOrNull { it.itemId == attempt.targetItemId && it.taskType == attempt.taskType }
        val updated = LearningReviewScheduler.apply(existing, attempt.patientId, attempt.targetItemId,
            attempt.taskType, attempt.outcome, todayEpochDay, attempt.timestamp)
        if (updated != null && updated != existing) {
            val rows = reviews.filterNot { it.itemId == attempt.targetItemId && it.taskType == attempt.taskType } + updated
            save(reviewFile, LearningJsonCodec.encodeReviews(rows), LearningJsonCodec::decodeReviews)
        }
    }

    private fun <T> load(name: String, decode: (String?) -> LearningDecoded<T>): List<T> = synchronized(lock) {
        val decoded = decode(LearningJsonStore(File(directory, name)).readText())
        if (decoded.unsupportedVersion) unsupported += name else unsupported -= name
        if (decoded.corrupt) corrupt += name else corrupt -= name
        decoded.rows
    }

    private fun <T> save(name: String, text: String, decode: (String?) -> LearningDecoded<T>) = synchronized(lock) {
        val store = LearningJsonStore(File(directory, name))
        val current = decode(store.readText())
        check(!current.unsupportedVersion) { "Learning file $name has a newer schema; not overwritten" }
        check(!current.corrupt) { "Learning file $name is unreadable; not overwritten" }
        store.writeText(text)
    }
}

package com.rehab2.learning

import org.json.JSONArray
import org.json.JSONObject

/** rows: decoded rows; skippedRows: rows with an unknown enum or a missing required field. */
data class LearningDecoded<T>(
    val rows: List<T>,
    val skippedRows: Int = 0,
    val unsupportedVersion: Boolean = false,
    val corrupt: Boolean = false
)

/** Root shape {"schemaVersion":1,"rows":[...]}. Decoding never throws. */
object LearningJsonCodec {
    private const val SCHEMA_KEY = "schemaVersion"
    private const val ROWS_KEY = "rows"

    fun encodeTasks(rows: List<LearningTask>): String = encode(rows, ::taskToJson)
    fun decodeTasks(text: String?): LearningDecoded<LearningTask> = decode(text, ::taskFromJson)
    fun encodeSettings(rows: List<LearningSettings>): String = encode(rows, ::settingsToJson)
    fun decodeSettings(text: String?): LearningDecoded<LearningSettings> = decode(text, ::settingsFromJson)
    fun encodeAttempts(rows: List<LearningAttempt>): String = encode(rows, ::attemptToJson)
    fun decodeAttempts(text: String?): LearningDecoded<LearningAttempt> = decode(text, ::attemptFromJson)
    fun encodeReviews(rows: List<ReviewEntry>): String = encode(rows, ::reviewToJson)
    fun decodeReviews(text: String?): LearningDecoded<ReviewEntry> = decode(text, ::reviewFromJson)

    private fun <T> encode(rows: List<T>, toJson: (T) -> JSONObject): String {
        val array = JSONArray()
        rows.forEach { array.put(toJson(it)) }
        return JSONObject().put(SCHEMA_KEY, LEARNING_SCHEMA_VERSION).put(ROWS_KEY, array).toString()
    }

    private fun <T> decode(text: String?, fromJson: (JSONObject) -> T): LearningDecoded<T> {
        if (text == null) return LearningDecoded(emptyList())
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return LearningDecoded(emptyList(), corrupt = true)
        val version = runCatching { root.getInt(SCHEMA_KEY) }.getOrNull()
            ?: return LearningDecoded(emptyList(), corrupt = true)
        if (version > LEARNING_SCHEMA_VERSION) return LearningDecoded(emptyList(), unsupportedVersion = true)
        if (version < 1) return LearningDecoded(emptyList(), corrupt = true)
        val array = root.optJSONArray(ROWS_KEY) ?: return LearningDecoded(emptyList(), corrupt = true)
        val rows = ArrayList<T>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val row = runCatching { fromJson(array.getJSONObject(index)) }.getOrNull()
            if (row == null) skipped++ else rows += row
        }
        return LearningDecoded(rows, skipped)
    }

    private fun taskToJson(t: LearningTask): JSONObject = JSONObject()
        .put("taskId", t.taskId)
        .put("taskType", t.taskType.name)
        .put("targetItemId", t.targetItemId)
        .put("distractorItemIds", JSONArray(t.distractorItemIds))
        .putOpt("questionItemId", t.questionItemId)
        .put("distractorStrategy", t.distractorStrategy.name)
        .put("presentation", t.presentation)
        .put("instructionTextByLanguage", JSONObject().also { o -> t.instructionTextByLanguage.forEach { (k, v) -> o.put(k, v) } })
        .putOpt("speechAttemptWord", t.speechAttemptWord)
        .putOpt("musicTrackId", t.musicTrackId)
        .put("enabled", t.enabled)

    private fun taskFromJson(o: JSONObject): LearningTask {
        val defaults = LearningTask("", LearningTaskType.VISUAL_RECOGNITION, "")
        return LearningTask(
            taskId = o.getString("taskId"),
            taskType = LearningTaskType.valueOf(o.getString("taskType")),
            targetItemId = o.getString("targetItemId"),
            distractorItemIds = o.optJSONArray("distractorItemIds")?.strings() ?: defaults.distractorItemIds,
            questionItemId = o.nullableString("questionItemId"),
            distractorStrategy = o.nullableString("distractorStrategy")?.let { DistractorStrategy.valueOf(it) }
                ?: defaults.distractorStrategy,
            presentation = o.nullableString("presentation") ?: defaults.presentation,
            instructionTextByLanguage = o.optJSONObject("instructionTextByLanguage")?.stringMap()
                ?: defaults.instructionTextByLanguage,
            speechAttemptWord = o.nullableString("speechAttemptWord"),
            musicTrackId = o.nullableString("musicTrackId"),
            enabled = if (o.has("enabled")) o.getBoolean("enabled") else defaults.enabled
        )
    }

    private fun settingsToJson(s: LearningSettings): JSONObject = JSONObject()
        .put("patientId", s.patientId)
        .put("choiceCount", s.choiceCount)
        .put("hintDelaySec", s.hintDelaySec)
        .put("maxHints", s.maxHints)
        .put("hintLevels", JSONArray(s.hintLevels.map { it.name }))
        .put("sessionMaxTasks", s.sessionMaxTasks)
        .put("allowedTaskTypes", JSONArray(s.allowedTaskTypes.map { it.name }.sorted()))
        .put("answerSide", s.answerSide)
        .put("allowTextOnly", s.allowTextOnly)
        .put("feedbackSound", s.feedbackSound)

    private fun settingsFromJson(o: JSONObject): LearningSettings {
        val d = LearningSettings(o.getString("patientId"))
        return d.copy(
            choiceCount = if (o.has("choiceCount")) o.getInt("choiceCount") else d.choiceCount,
            hintDelaySec = if (o.has("hintDelaySec")) o.getInt("hintDelaySec") else d.hintDelaySec,
            maxHints = if (o.has("maxHints")) o.getInt("maxHints") else d.maxHints,
            hintLevels = o.optJSONArray("hintLevels")?.strings()?.map { LearningHintLevel.valueOf(it) } ?: d.hintLevels,
            sessionMaxTasks = if (o.has("sessionMaxTasks")) o.getInt("sessionMaxTasks") else d.sessionMaxTasks,
            allowedTaskTypes = o.optJSONArray("allowedTaskTypes")?.strings()?.map { LearningTaskType.valueOf(it) }?.toSet()
                ?: d.allowedTaskTypes,
            answerSide = o.nullableString("answerSide") ?: d.answerSide,
            allowTextOnly = if (o.has("allowTextOnly")) o.getBoolean("allowTextOnly") else d.allowTextOnly,
            feedbackSound = if (o.has("feedbackSound")) o.getBoolean("feedbackSound") else d.feedbackSound
        )
    }

    private fun attemptToJson(a: LearningAttempt): JSONObject = JSONObject()
        .put("attemptId", a.attemptId)
        .put("patientId", a.patientId)
        .put("sessionId", a.sessionId)
        .put("taskId", a.taskId)
        .put("taskType", a.taskType.name)
        .put("targetItemId", a.targetItemId)
        .put("shownItemIds", JSONArray(a.shownItemIds))
        .putOpt("selectedItemId", a.selectedItemId)
        .put("outcome", a.outcome.name)
        .put("hintsUsed", a.hintsUsed)
        .put("responseMs", a.responseMs)
        .put("languageCode", a.languageCode)
        .put("assessedBy", a.assessedBy.name)
        .putOpt("speechRating", a.speechRating)
        .put("timestamp", a.timestamp)

    private fun attemptFromJson(o: JSONObject): LearningAttempt = LearningAttempt(
        attemptId = o.getString("attemptId"),
        patientId = o.getString("patientId"),
        sessionId = o.getString("sessionId"),
        taskId = o.getString("taskId"),
        taskType = LearningTaskType.valueOf(o.getString("taskType")),
        targetItemId = o.getString("targetItemId"),
        shownItemIds = o.getJSONArray("shownItemIds").strings(),
        selectedItemId = o.nullableString("selectedItemId"),
        outcome = LearningOutcome.valueOf(o.getString("outcome")),
        hintsUsed = o.getInt("hintsUsed"),
        responseMs = o.getLong("responseMs"),
        languageCode = o.getString("languageCode"),
        assessedBy = AssessedBy.valueOf(o.getString("assessedBy")),
        speechRating = if (o.has("speechRating") && !o.isNull("speechRating")) o.getInt("speechRating") else null,
        timestamp = o.getLong("timestamp")
    )

    private fun reviewToJson(r: ReviewEntry): JSONObject = JSONObject()
        .put("patientId", r.patientId)
        .put("itemId", r.itemId)
        .put("taskType", r.taskType.name)
        .put("box", r.box)
        .put("nextDueEpochDay", r.nextDueEpochDay)
        .put("lastOutcome", r.lastOutcome.name)
        .put("updatedAt", r.updatedAt)

    private fun reviewFromJson(o: JSONObject): ReviewEntry = ReviewEntry(
        patientId = o.getString("patientId"),
        itemId = o.getString("itemId"),
        taskType = LearningTaskType.valueOf(o.getString("taskType")),
        box = o.getInt("box"),
        nextDueEpochDay = o.getLong("nextDueEpochDay"),
        lastOutcome = LearningOutcome.valueOf(o.getString("lastOutcome")),
        updatedAt = o.getLong("updatedAt")
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONObject.stringMap(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val names = keys()
        while (names.hasNext()) {
            val key = names.next()
            result[key] = getString(key)
        }
        return result
    }
}

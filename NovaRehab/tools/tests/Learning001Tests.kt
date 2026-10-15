package com.rehab2.learning

import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

// Standalone JVM regression program, no JUnit. args[0] = temporary directory.
// Cases 1-14 are pure Kotlin; 15-17 need a real org.json on the classpath (layoutlib.jar).
fun main(args: Array<String>) {
    val root = File(args[0]).apply { mkdirs() }
    val p = "test-a"
    val today = 20_000L
    val t = LearningTaskType.VISUAL_RECOGNITION
    fun fail(action: () -> Unit) { check(runCatching(action).isFailure) }

    // 1
    val r1 = LearningReviewScheduler.apply(null, p, "banana", t, LearningOutcome.INDEPENDENT, today, 1L)!!
    check(r1.box == 2 && r1.nextDueEpochDay == today + 2)
    println("1 PASS null + INDEPENDENT -> box 2, today + 2")

    // 2
    var entry: ReviewEntry? = null
    repeat(5) { entry = LearningReviewScheduler.apply(entry, p, "banana", t, LearningOutcome.INDEPENDENT, today, 1L) }
    check(entry!!.box == 5 && entry!!.nextDueEpochDay == today + 14)
    entry = LearningReviewScheduler.apply(entry, p, "banana", t, LearningOutcome.INDEPENDENT, today, 1L)
    check(entry!!.box == 5 && entry!!.nextDueEpochDay == today + 14)
    println("2 PASS five INDEPENDENT -> box 5 / 14 days; sixth stays on 5")

    // 3
    val box3 = ReviewEntry(p, "banana", t, 3, today, LearningOutcome.INDEPENDENT, 0L)
    for (o in listOf(LearningOutcome.WITH_HINT, LearningOutcome.ASSISTED)) {
        val r = LearningReviewScheduler.apply(box3, p, "banana", t, o, today, 1L)!!
        check(r.box == 3 && r.nextDueEpochDay == today + 1 && r.lastOutcome == o)
    }
    println("3 PASS WITH_HINT / ASSISTED keep box, next day")

    // 4
    val box4 = box3.copy(box = 4)
    for (o in listOf(LearningOutcome.INCORRECT, LearningOutcome.NO_RESPONSE)) {
        val r = LearningReviewScheduler.apply(box4, p, "banana", t, o, today, 1L)!!
        check(r.box == 1 && r.nextDueEpochDay == today + 1)
    }
    println("4 PASS INCORRECT / NO_RESPONSE from box 4 -> box 1, next day")

    // 5
    check(LearningReviewScheduler.apply(box4, p, "banana", t, LearningOutcome.SKIPPED, today, 1L) === box4)
    check(LearningReviewScheduler.apply(null, p, "banana", t, LearningOutcome.SKIPPED, today, 1L) == null)
    println("5 PASS SKIPPED returns existing / null")

    // 6
    val lj = TimeZone.getTimeZone("Europe/Ljubljana")
    fun local(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(lj).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    fun utcDay(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }.timeInMillis / 86_400_000L
    fun day(ms: Long) = LearningReviewScheduler.epochDay(ms, lj)
    check(day(local(2026, 10, 14, 23, 59)) + 1 == day(local(2026, 10, 15, 0, 1)))
    check(day(local(2026, 10, 15, 0, 1)) == utcDay(2026, 10, 15))
    // 25. 10. 2026: clocks go back 03:00 -> 02:00, the day has 25 hours.
    check(lj.getOffset(local(2026, 10, 25, 1, 0)) != lj.getOffset(local(2026, 10, 25, 4, 0)))
    check(day(local(2026, 10, 24, 23, 59)) + 1 == day(local(2026, 10, 25, 0, 1)))
    check(day(local(2026, 10, 25, 0, 1)) == utcDay(2026, 10, 25))
    check(day(local(2026, 10, 25, 23, 59)) == utcDay(2026, 10, 25))
    check(day(local(2026, 10, 25, 23, 59)) + 1 == day(local(2026, 10, 26, 0, 1)))
    check(LearningReviewScheduler.epochDay(-1L, TimeZone.getTimeZone("UTC")) == -1L)
    println("6 PASS epochDay Europe/Ljubljana incl. DST end 25. 10. 2026")

    // 7
    val dueRows = listOf(
        ReviewEntry(p, "c", t, 2, today, LearningOutcome.WITH_HINT, 0L),
        ReviewEntry(p, "future", t, 1, today + 1, LearningOutcome.INCORRECT, 0L),
        ReviewEntry(p, "b", t, 1, today, LearningOutcome.INCORRECT, 0L),
        ReviewEntry(p, "a", t, 2, today, LearningOutcome.WITH_HINT, 0L),
        ReviewEntry(p, "old", t, 4, today - 3, LearningOutcome.INDEPENDENT, 0L))
    check(LearningReviewScheduler.due(dueRows, today).map { it.itemId } == listOf("old", "b", "a", "c"))
    println("7 PASS due filters and sorts (date, box, itemId)")

    // 8
    fun cand(id: String, group: String?, image: Boolean = true, mode: Boolean = true, active: Boolean = true,
             presentations: Set<String> = emptySet()) = LearningCandidate(id, group, image, mode, active, presentations)
    val target = cand("apple", "fruit")
    val pool = listOf(target, cand("pear", "fruit"), cand("plum", "fruit"),
        cand("cup", "kitchen"), cand("bed", "home"), cand("car", null), cand("pear", "fruit"))
    for (seed in 0 until 30) {
        val same = LearningTaskGenerator.distractors(target, pool, DistractorStrategy.SAME_GROUP, 2, Random(seed))
        check(same.toSet() == setOf("pear", "plum"))
        val other = LearningTaskGenerator.distractors(target, pool, DistractorStrategy.OTHER_GROUP, 3, Random(seed))
        check(other.toSet() == setOf("cup", "bed", "car"))
        val filled = LearningTaskGenerator.distractors(target, pool, DistractorStrategy.SAME_GROUP, 4, Random(seed))
        check(filled.size == 4 && filled.take(2).toSet() == setOf("pear", "plum") && filled.distinct() == filled)
        val all = LearningTaskGenerator.distractors(target, pool, DistractorStrategy.OTHER_GROUP, 10, Random(seed))
        check(all.size == 5 && "apple" !in all && all.distinct() == all)
    }
    check(LearningTaskGenerator.distractors(cand("x", null), pool, DistractorStrategy.SAME_GROUP, 2, Random(1)).size == 2)
    println("8 PASS distractors: no target, no duplicates, strategy respected, filled up")

    // 9
    val mixed = listOf(target, cand("ok1", "fruit"), cand("ok2", "x"),
        cand("noimage", "fruit", image = false), cand("nomode", "fruit", mode = false), cand("inactive", "fruit", active = false))
    val mixedById = mixed.associateBy { it.itemId }
    val wide = LearningSettings(p, choiceCount = 6)
    for (seed in 0 until 30) {
        val d = LearningTaskGenerator.distractors(target, mixed, DistractorStrategy.SAME_GROUP, 5, Random(seed))
        check(d.toSet() == setOf("ok1", "ok2"))
        val plan = LearningTaskGenerator.planSession(wide,
            listOf(LearningTask("t", t, "apple", distractorItemIds = listOf("noimage", "nomode", "inactive"))),
            emptyList(), mixedById, today, Random(seed))
        check(plan.single().shownItemIds.toSet() == setOf("apple", "ok1", "ok2"))
    }
    for (bad in listOf("noimage", "nomode", "inactive")) {
        check(LearningTaskGenerator.planSession(wide, listOf(LearningTask("t", t, bad)), emptyList(), mixedById, today, Random(1)).isEmpty())
    }
    println("9 PASS unusable candidates never shown; unusable target skipped")

    // 10
    val items = (1..8).map { cand("i$it", if (it % 2 == 0) "even" else "odd") }.associateBy { it.itemId }
    val tasks = (1..6).map { LearningTask("t$it", t, "i$it") }
    val reviews = listOf(
        ReviewEntry(p, "i1", t, 3, today, LearningOutcome.INDEPENDENT, 0L),
        ReviewEntry(p, "i2", t, 1, today - 1, LearningOutcome.INCORRECT, 0L),
        ReviewEntry(p, "i3", t, 2, today + 2, LearningOutcome.INDEPENDENT, 0L))
    val s3 = LearningSettings(p, choiceCount = 3, sessionMaxTasks = 3)
    val planA = LearningTaskGenerator.planSession(s3, tasks, reviews, items, today, Random(42))
    check(planA.map { it.task.taskId } == listOf("t2", "t4", "t1"))
    check(planA.all { it.shownItemIds.size == 3 && it.task.targetItemId in it.shownItemIds && it.shownItemIds.distinct().size == 3 })
    check(planA == LearningTaskGenerator.planSession(s3, tasks, reviews, items, today, Random(42)))
    val planAll = LearningTaskGenerator.planSession(s3.copy(sessionMaxTasks = 20), tasks, reviews, items, today, Random(7))
    check(planAll.map { it.task.taskId } == listOf("t2", "t4", "t5", "t6", "t1"))
    check(LearningTaskGenerator.planSession(s3.copy(sessionMaxTasks = 50), tasks, reviews, items, today, Random(7)) == planAll)
    val disabled = tasks.map { if (it.taskId == "t4") it.copy(enabled = false) else it } +
        LearningTask("y", LearningTaskType.SPOKEN_INSTRUCTION, "i7")
    check(LearningTaskGenerator.planSession(s3, disabled, reviews, items, today, Random(1)).map { it.task.taskId } == listOf("t2", "t5", "t1"))
    println("10 PASS due first, then new; sessionMaxTasks; highest box last; same seed -> same plan")

    // 11
    val n = LearningSettings(p, choiceCount = 9, maxHints = -1, hintDelaySec = 999, sessionMaxTasks = 0, answerSide = "up").normalized()
    check(n.choiceCount == 6 && n.maxHints == 0 && n.hintDelaySec == 60 && n.sessionMaxTasks == 1 && n.answerSide == "left")
    check(LearningSettings(p, choiceCount = 1).normalized().choiceCount == 2)
    println("11 PASS normalized clamps values")

    // 12
    val storeFile = File(root, "store/data.json")
    val store = LearningJsonStore(storeFile)
    check(store.readText() == null)
    store.writeText("first")
    check(store.readText() == "first")
    store.writeText("second")
    check(store.readText() == "second" && !File(storeFile.path + ".bak").exists() && !File(storeFile.path + ".tmp").exists())
    storeFile.writeText("half-written")
    File(storeFile.path + ".bak").writeText("previous")
    check(store.readText() == "previous" && !File(storeFile.path + ".bak").exists())
    fail { store.writeText("x".repeat(LearningJsonStore.MAX_BYTES + 1)) }
    check(store.readText() == "previous")
    println("12 PASS store write/read, .bak restore, oversize rejected and old file kept")

    // 13
    val yn = LearningSettings(p, allowedTaskTypes = setOf(LearningTaskType.YES_NO_QUESTION), choiceCount = 4)
    val ynItems = mapOf("apple" to target, "pear" to cand("pear", "fruit"), "bad" to cand("bad", "fruit", image = false))
    val ynPlan = LearningTaskGenerator.planSession(yn,
        listOf(LearningTask("q", LearningTaskType.YES_NO_QUESTION, "apple", questionItemId = "pear")), emptyList(), ynItems, today, Random(1))
    check(ynPlan.single().shownItemIds == listOf("apple"))
    for (question in listOf(null, "bad", "missing")) {
        check(LearningTaskGenerator.planSession(yn,
            listOf(LearningTask("q", LearningTaskType.YES_NO_QUESTION, "apple", questionItemId = question)), emptyList(), ynItems, today, Random(1)).isEmpty())
    }
    println("13 PASS YES_NO_QUESTION shows one item; missing/unusable question skipped")

    // 14
    val textItems = mapOf("apple" to cand("apple", "fruit", presentations = setOf(LearningPresentation.TEXT_ONLY, LearningPresentation.IMAGE_ONLY)),
        "pear" to cand("pear", "fruit"))
    val textTask = listOf(LearningTask("tx", t, "apple", presentation = LearningPresentation.TEXT_ONLY))
    check(LearningTaskGenerator.planSession(LearningSettings(p), textTask, emptyList(), textItems, today, Random(1)).isEmpty())
    check(LearningTaskGenerator.planSession(LearningSettings(p, allowTextOnly = true), textTask, emptyList(), textItems, today, Random(1)).size == 1)
    // Empty presentations mean image_text + image_only inside learning, so text_only stays excluded.
    val plain = mapOf("apple" to cand("apple", "fruit"), "pear" to cand("pear", "fruit"))
    check(LearningTaskGenerator.planSession(LearningSettings(p, allowTextOnly = true), textTask, emptyList(), plain, today, Random(1)).isEmpty())
    println("14 PASS text_only skipped unless allowTextOnly")

    // 15
    val task = LearningTask("t1", LearningTaskType.SPEECH_ATTEMPT, "banana", listOf("apple", "pear"), "banana",
        DistractorStrategy.SAME_GROUP, LearningPresentation.IMAGE_TEXT, mapOf("sl" to "Pokaži banano.", "uk" to "Покажи банан."),
        "banana", "song-1", false)
    val minimal = LearningTask("t2", t, "apple")
    val settings = LearningSettings(p, 4, 12, 1, listOf(LearningHintLevel.SHOW_ANSWER), 5,
        setOf(t, LearningTaskType.SPEECH_ATTEMPT), "right", true, false)
    val attempt = LearningAttempt("a1", p, "s1", "t1", LearningTaskType.SPEECH_ATTEMPT, "banana", listOf("banana", "apple"),
        null, LearningOutcome.WITH_HINT, 1, 4200L, "sl", AssessedBy.THERAPIST, 2, 1_760_000_000_000L)
    val review = ReviewEntry(p, "banana", t, 3, today, LearningOutcome.ASSISTED, 1_760_000_000_000L)
    check(LearningJsonCodec.decodeTasks(LearningJsonCodec.encodeTasks(listOf(task, minimal))) == LearningDecoded(listOf(task, minimal)))
    check(LearningJsonCodec.decodeSettings(LearningJsonCodec.encodeSettings(listOf(settings))) == LearningDecoded(listOf(settings)))
    check(LearningJsonCodec.decodeAttempts(LearningJsonCodec.encodeAttempts(listOf(attempt, attempt.copy(attemptId = "a2", selectedItemId = "apple", speechRating = null)))).rows ==
        listOf(attempt, attempt.copy(attemptId = "a2", selectedItemId = "apple", speechRating = null)))
    check(LearningJsonCodec.decodeReviews(LearningJsonCodec.encodeReviews(listOf(review))) == LearningDecoded(listOf(review)))
    val repo = LearningRepository(File(root, "repo"))
    repo.saveTasks(listOf(task, minimal))
    repo.saveSettings(settings)
    check(repo.loadTasks() == listOf(task, minimal) && repo.loadSettings(p) == settings)
    check(repo.loadSettings("other") == LearningSettings("other"))
    repo.recordAttempt(attempt.copy(taskType = t, outcome = LearningOutcome.INDEPENDENT), today)
    check(repo.loadAttempts(p).size == 1 && repo.loadReviews(p).single().box == 2)
    check(File(root, "repo/attempts_test-a.json").exists() && File(root, "repo/review_test-a.json").exists())
    check(LearningRepository.safeId("a b/č") == "a_b__" && LearningRepository.safeId("") == "legacy")
    println("15 PASS codec round trip for all four models; repository per-patient files")

    // 16
    val mixedJson = """{"schemaVersion":1,"rows":[
        {"taskId":"ok","taskType":"VISUAL_RECOGNITION","targetItemId":"apple"},
        {"taskId":"bad","taskType":"FLYING","targetItemId":"apple"},
        {"taskId":"nostrategy","taskType":"VISUAL_RECOGNITION","targetItemId":"pear","distractorStrategy":"RANDOM"},
        {"taskType":"VISUAL_RECOGNITION","targetItemId":"no-id"}]}"""
    val decoded = LearningJsonCodec.decodeTasks(mixedJson)
    check(decoded.rows.map { it.taskId } == listOf("ok") && decoded.skippedRows == 3 && !decoded.corrupt && !decoded.unsupportedVersion)
    check(LearningJsonCodec.decodeReviews("""{"schemaVersion":1,"rows":[{"patientId":"p","itemId":"x","taskType":"VISUAL_RECOGNITION","box":1,"nextDueEpochDay":1,"lastOutcome":"MAYBE","updatedAt":1}]}""").rows.isEmpty())
    check(LearningJsonCodec.decodeTasks("not json").corrupt)
    println("16 PASS unknown enum / missing field row skipped, others loaded")

    // 17
    val futureDir = File(root, "future").apply { mkdirs() }
    val futureText = """{"schemaVersion":2,"rows":[{"taskId":"x","taskType":"VISUAL_RECOGNITION","targetItemId":"apple"}]}"""
    val futureTasks = File(futureDir, LearningRepository.TASKS_FILE).apply { writeText(futureText) }
    val futureReview = File(futureDir, LearningRepository.reviewFileName(p)).apply { writeText(futureText) }
    val futureRepo = LearningRepository(futureDir)
    check(futureRepo.loadTasks().isEmpty() && LearningRepository.TASKS_FILE in futureRepo.unsupportedFiles)
    fail { futureRepo.saveTasks(listOf(minimal)) }
    check(futureTasks.readText() == futureText)
    fail { futureRepo.recordAttempt(attempt.copy(taskType = t), today) }
    check(futureReview.readText() == futureText && !File(futureDir, LearningRepository.attemptsFileName(p)).exists())
    check(LearningJsonCodec.decodeTasks(futureText) == LearningDecoded<LearningTask>(emptyList(), unsupportedVersion = true))
    println("17 PASS schemaVersion 2 -> empty, unsupportedVersion, file not overwritten")

    println("Learning001Tests OK")
}

package com.rehab2.aac

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Button
import com.rehab2.R

/** Explicit, local, bounded observation. No recorder, camera, network, or disk writes. */
object LiveTestSession {
    data class Event(val elapsedMs: Long, val kind: String, val detail: String, val tap: Int)
    private val events = ArrayList<Event>()
    private var started = 0L
    private var tap = 0
    private var patient = ""
    private var expires = 0L
    @Volatile var active = false
        private set
    @Synchronized fun start(context: Context) {
        clear()
        patient = AacPatientImages.store(context).currentPatientId()
        started = SystemClock.elapsedRealtime()
        expires = started + 24 * 60 * 60 * 1000L
        active = true
        record("session_start", "Lokalno; brez zvoka, videa in zunanjega AI")
    }
    @Synchronized fun stop() { record("session_stop"); active = false }
    @Synchronized fun clear() { active = false; events.clear(); tap = 0; patient = ""; expires = 0L }
    @Synchronized fun validate(context: Context) {
        if (expires > 0 && (SystemClock.elapsedRealtime() >= expires ||
                runCatching { AacPatientImages.store(context).currentPatientId() }.getOrNull() != patient)) clear()
    }
    @Synchronized fun record(kind: String, detail: String = "") {
        if (!active) return
        if (SystemClock.elapsedRealtime() >= expires) { clear(); return }
        if (kind == "tap") tap++
        if (events.size >= 2000) events.removeAt(0)
        events += Event(SystemClock.elapsedRealtime() - started, kind, detail.take(500), tap)
    }
    @Synchronized fun snapshot(): List<Event> {
        if (expires > 0 && SystemClock.elapsedRealtime() >= expires) clear()
        return events.toList()
    }
    fun attach(activity: Activity) {
        validate(activity)
        activity.findViewById<Button>(R.id.btnLiveTestStop)?.apply {
            visibility = if (active) View.VISIBLE else View.GONE
            text = "USTAVI PREIZKUS / ЗУПИНИТИ • lokalno"
            setOnClickListener { stop(); visibility = View.GONE }
        }
    }
    fun report(): String {
        val rows = snapshot()
        if (rows.isEmpty()) return "Ni zabeleženega preizkusa."
        return buildString {
            appendLine("Lokalni dogodki; meritve povratnih klicev, ne akustične meritve.")
            appendLine("Opazovanje ni potrditev bolečine, čustev, strinjanja ali razumevanja.")
            rows.filter { it.kind == "tap" }.takeLast(50).forEach { t ->
                val group = rows.filter { it.tap == t.tap }
                fun delta(kind: String) = group.firstOrNull { it.kind == kind && (kind != "input_ready" || it.elapsedMs >= (group.firstOrNull { e -> e.kind == "playback_end" }?.elapsedMs ?: Long.MAX_VALUE)) }?.let { "${it.elapsedMs - t.elapsedMs} ms" } ?: "—"
                appendLine("${t.detail}: prikaz ${delta("page_shown")}; začetek ${delta("sound_start")}; konec ${delta("playback_end")}; odziv ${delta("input_ready")}")
            }
            val corrections = rows.count { it.kind == "correction" || it.detail in setOf("correct_message", "not_this", "dont_understand") }
            if (corrections > 0) appendLine("PREDLOG ZA PREGLED: $corrections popravkov/nerazumevanj. S pacientko preverite pomen izbrane ikone; možne dopolnitve sodijo na drugo stran ali podikone.")
            rows.filter { it.kind.startsWith("note_") }.forEach { appendLine("${it.kind}: ${it.detail}") }
            appendLine("Predlogi ne spreminjajo komunikatorja. Spremembo potrdi skrbnik v urejevalniku.")
        }
    }
}

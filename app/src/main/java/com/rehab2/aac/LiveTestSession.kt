package com.rehab2.aac

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Button
import com.rehab2.R

/** Bounded session timeline. Capture consent and lifecycle are owned by LiveCapture. */
object LiveTestSession {
    data class Event(val elapsedMs: Long, val kind: String, val detail: String, val tap: Int)
    private val events = ArrayList<Event>()
    private var started = 0L
    private var tap = 0
    private var patient = ""
    private var expires = 0L
    var sessionId = ""
        private set
    @Volatile var active = false
        private set
    @Synchronized fun start(context: Context) {
        clear()
        patient = AacPatientImages.store(context).currentPatientId()
        sessionId = java.util.UUID.randomUUID().toString()
        started = SystemClock.elapsedRealtime()
        expires = started + 24 * 60 * 60 * 1000L
        active = true
        record("session_start", "Začetek skupne časovnice; podatki AI ostanejo nepotrjeni.")
    }
    @Synchronized fun stop() { record("session_stop"); active = false; LiveCapture.manualStop() }
    @Synchronized fun clear() { active = false; LiveCapture.stop(); events.clear(); tap = 0; patient = ""; expires = 0L }
    @Synchronized fun elapsedMs():Long = if(started==0L)0L else SystemClock.elapsedRealtime()-started
    @Synchronized fun annotate(kind:String,detail:String) {
        if(patient.isEmpty() || SystemClock.elapsedRealtime()>=expires)return
        if(events.size>=2000)events.removeAt(0)
        events += Event(elapsedMs(),kind,detail.take(2000),tap)
    }
    @Synchronized fun observation(kind:String,detail:String,startMs:Long) {
        if(!active || startMs<0 || startMs>elapsedMs())return
        if(events.size>=2000)events.removeAt(0)
        events += Event(startMs,kind,detail.take(2000),tap)
    }
    @Synchronized fun validate(context: Context) {
        if (expires > 0 && (SystemClock.elapsedRealtime() >= expires ||
                runCatching { AacPatientImages.store(context).currentPatientId() }.getOrNull() != patient)) clear()
    }
    @Synchronized fun record(kind: String, detail: String = "") {
        LiveCapture.speechEvent(kind)
        if (!active) return
        if (SystemClock.elapsedRealtime() >= expires) { clear(); return }
        if (kind == "tap") tap++
        if (events.size >= 2000) events.removeAt(0)
        events += Event(SystemClock.elapsedRealtime() - started, kind, detail.take(500), tap)
    }
    @Synchronized fun snapshot(): List<Event> {
        if (expires > 0 && SystemClock.elapsedRealtime() >= expires) clear()
        return events.sortedBy { it.elapsedMs }
    }
    fun attach(activity: Activity) {
        validate(activity)
        activity.findViewById<Button>(R.id.btnLiveTestStop)?.apply {
            visibility = if (active) View.VISIBLE else View.GONE
            text = LiveCapture.indicator()
            setOnClickListener { stop(); visibility = View.GONE }
        }
    }
    fun report(): String {
        val rows = snapshot()
        if (rows.isEmpty()) return "Ni zabeleženega preizkusa."
        return buildString {
            appendLine("Dogodki seje; meritve povratnih klicev, ne akustične meritve. AI ni potrjen odgovor.")
            appendLine("Seja: $sessionId")
            appendLine("Opazovanje ni potrditev bolečine, čustev, strinjanja ali razumevanja.")
            rows.filter { it.kind == "tap" }.takeLast(50).forEach { t ->
                val group = rows.filter { it.tap == t.tap }
                fun delta(kind: String) = group.firstOrNull { it.kind == kind && (kind != "input_ready" || it.elapsedMs >= (group.firstOrNull { e -> e.kind == "playback_end" }?.elapsedMs ?: Long.MAX_VALUE)) }?.let { "${it.elapsedMs - t.elapsedMs} ms" } ?: "—"
                appendLine("${t.detail}: prikaz ${delta("page_shown")}; začetek ${delta("sound_start")}; konec ${delta("playback_end")}; odziv ${delta("input_ready")}")
            }
            val corrections = rows.count { it.kind == "correction" || it.detail in setOf("correct_message", "not_this", "dont_understand") }
            if (corrections > 0) appendLine("PREDLOG ZA PREGLED: $corrections popravkov/nerazumevanj. S pacientko preverite pomen izbrane ikone; možne dopolnitve sodijo na drugo stran ali podikone.")
            appendLine("Časovnica (zadnjih 200 dogodkov):")
            rows.takeLast(200).forEach { appendLine("${it.elapsedMs} ms [${it.kind}]: ${it.detail}") }
            appendLine("Predlogi ne spreminjajo komunikatorja. Spremembo potrdi skrbnik v urejevalniku.")
        }
    }
}

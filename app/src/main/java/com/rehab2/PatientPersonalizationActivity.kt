package com.rehab2

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*
import com.rehab2.patient.PatientIconPersonalizer
import java.io.File

/** Caregiver flow. Existing UUID, IDs, fixed row and system assets remain unchanged. */
class PatientPersonalizationActivity : AppCompatActivity() {
    private lateinit var service: PatientIconPersonalizer
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var audio: AacAudioPlayer
    private var pickerPatient: String? = null
    private var candidate: File? = null
    private var candidateItem: String? = null
    private var candidatePatient: String? = null
    private var queue = ArrayDeque<String>()
    private var batchPatient: String? = null
    private var busy = false
    @Volatile private var generationSerial = 0
    @Volatile private var closed = false
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val patient = pickerPatient; pickerPatient = null
        if (uri != null && patient != null) {
            busy = true; status.text = "Shranjujem fotografijo …"
            Thread {
                val result = runCatching { service.importReference(patient, uri) }
                runOnUiThread {
                    if (!closed) {
                        busy = false
                        result.fold({ status.text = "Fotografija je pripravljena."; showReference() }, { status.text = "Fotografije ni mogoče uvoziti: ${it.message}" })
                    }
                }
            }.start()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AacPatientVoiceSettings.bind(this)
        pickerPatient = savedInstanceState?.getString("pickerPatient")
        service = PatientIconPersonalizer(this)
        audio = AacAudioPlayer(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 24) }
        setContentView(ScrollView(this).apply { addView(content) })
        text("OSEBNE IKONE IN GLASOVA", 24f)
        button("NAZAJ") { finish() }
        text("Izberi pacientovo fotografijo in ikone. Sistem ostane nespremenjen; nove slike se uporabijo samo pri tem pacientu po tvoji potrditvi.")
        button("SPOL PACIENTA: ${if (AacPatientVoiceSettings.male(this)) "Moški" else "Ženska"}") {
            if (busy) return@button
            AlertDialog.Builder(this).setTitle("Spol pacienta")
                .setItems(arrayOf("ŽENSKA", "MOŠKI")) { _, selected ->
                    val profile = PatientProfileSettings.load(this)
                    if (PatientProfileSettings.save(this, profile.copy(gender = if (selected == 0) "FEMALE" else "MALE"))) recreate()
                    else status.text = "Spola ni mogoče shraniti."
                }.show()
        }
        text("Ženska: njene izjave z ženskim glasom, vprašanja z moškim. Pri moškem obratno. Vprašanja slovnično nagovarjajo pacienta. Glasova lahko poslušaš in spremeniš.")
        voiceButton(false)
        voiceButton(true)
        text("Izbira glasov velja za spletni govor. Androidov nadomestni govor uporablja razpoložljiv glas naprave; razlika med glasovoma tam ni zagotovljena.")
        button("IZBERI FOTOGRAFIJO IZ GALERIJE") {
            if (!busy) { clearCandidate(); queue.clear(); pickerPatient = patientId(); picker.launch("image/*") }
        }
        preview = ImageView(this).apply { adjustViewBounds = true; maxHeight = 360 }
        content.addView(preview, LinearLayout.LayoutParams(-1, 360))
        status = text("")
        button("IZBERI IKONE ZA IZDELAVO") { if (!busy && candidate == null) chooseIcons() }
        button("PREKLIČI IZDELAVO") { generationSerial++; queue.clear(); service.cancel(); busy = false; clearCandidate(); status.text = "Izdelava preklicana." }
        button("NASTAVITEV STORITVE ZA SLIKE") { if (!busy) configureService() }
        showReference()
        if (service.configuration().optString("endpoint").isBlank()) status.text = "Storitev za izdelavo slik še ni nastavljena. Galerija in nastavitev spola/glasov sta na voljo."
    }
    private fun patientId() = AacPatientImages.store(this).currentPatientId()
    private fun text(value: String, size: Float = 17f): TextView = TextView(this).apply {
        text = value; textSize = size; setPadding(0, 12, 0, 12); content.addView(this)
    }
    private fun button(label: String, action: () -> Unit) {
        content.addView(Button(this).apply { text = label; minHeight = 64; setOnClickListener { action() } })
    }
    private fun voiceButton(guide: Boolean) {
        button(if (guide) "GLAS VPRAŠANJ TABLICE — POSLUŠAJ / SPREMENI" else "GLAS PACIENTA — POSLUŠAJ / SPREMENI") {
            val voices = AacPatientVoiceSettings.voices
            AlertDialog.Builder(this).setTitle(if (guide) "Glas vprašanj" else "Glas pacienta")
                .setSingleChoiceItems(voices.toTypedArray(), voices.indexOf(AacPatientVoiceSettings.voice(this, guide))) { _, which ->
                    AacPatientVoiceSettings.saveVoice(this, guide, voices[which])
                    if (guide) audio.speakGuideText("Kako se počutiš?", "sl") else audio.speakPatientText("Rada bi povedala, kako se počutim.", "sl")
                }.setPositiveButton("KONČANO", null).show()
        }
    }
    private fun showReference() { preview.setImageURI(android.net.Uri.fromFile(service.reference(patientId()))) }
    private fun chooseIcons() {
        if (!service.reference(patientId()).isFile) { status.text = "Najprej izberi fotografijo iz galerije."; return }
        if (service.configuration().optString("endpoint").isBlank()) { status.text = "Najprej nastavi storitev za izdelavo slik."; return }
        val patient = patientId()
        val items = service.eligible().sortedBy { it.labelSl }
        val selected = BooleanArray(items.size)
        AlertDialog.Builder(this).setTitle("Izberi osebne ikone")
            .setMultiChoiceItems(items.map { it.labelSl }.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setNeutralButton("VSE") { _, _ -> confirmGeneration(patient, items.map { it.id }) }
            .setPositiveButton("NAPREJ") { _, _ -> confirmGeneration(patient, items.filterIndexed { i, _ -> selected[i] }.map { it.id }) }
            .setNegativeButton("PREKLIČI", null).show()
    }
    private fun confirmGeneration(patient: String, ids: List<String>) {
        if (patient != patientId()) { status.text = "Pacient je spremenjen. Ponovno izberi ikone."; return }
        if (ids.isEmpty()) return
        val host = java.net.URI(service.configuration().getString("endpoint")).host
        AlertDialog.Builder(this).setTitle("Izdelaj ${ids.size} osebnih ikon?")
            .setMessage("Fotografija in sistemske ikone bodo poslane storitvi $host za izdelavo osebnih različic. Izdelava potrebuje internet in lahko povzroči strošek storitve. Vsako novo ikono boš pred uporabo pregledal. Obstoječe osebne slike se zamenjajo samo ob potrditvi posamezne nove ikone.")
            .setPositiveButton("IZDELAJ") { _, _ -> batchPatient = patient; queue = ArrayDeque(ids); next() }
            .setNegativeButton("PREKLIČI", null).show()
    }
    private fun next() {
        if (queue.isEmpty()) { status.text = "Pregled izbranih ikon je končan."; return }
        val patient = batchPatient
        if (patient == null || patient != patientId()) {
            queue.clear(); clearCandidate(); status.text = "Pacient je spremenjen. Izdelava je ustavljena."; return
        }
        val item = queue.removeFirst()
        val serial = ++generationSerial
        busy = true; status.text = "Izdelujem $item … Preostalih: ${queue.size}"
        Thread {
            val result = runCatching { service.generate(patient, item) { closed || serial != generationSerial } }
            runOnUiThread {
                if (closed || serial != generationSerial) { result.getOrNull()?.delete(); return@runOnUiThread }
                if (patient != patientId()) {
                    result.getOrNull()?.delete(); queue.clear(); busy = false
                    status.text = "Pacient je spremenjen. Predlog je zavržen."; return@runOnUiThread
                }
                busy = false
                result.fold({ file ->
                    candidate = file; candidateItem = item; candidatePatient = patient
                    preview.setImageURI(android.net.Uri.fromFile(file))
                    review()
                }, { queue.clear(); status.text = "Izdelava ni uspela: ${it.message ?: "preveri povezavo in nastavitev storitve"}" })
            }
        }.start()
    }
    private fun review() {
        val file = candidate ?: return
        val image = ImageView(this).apply { adjustViewBounds = true; setImageURI(android.net.Uri.fromFile(file)) }
        val comparison = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val original = ImageView(this).apply {
            adjustViewBounds = true; contentDescription = "Sistemska ikona"
            setImageURI(android.net.Uri.fromFile(service.systemPreview(requireNotNull(candidateItem))))
        }
        image.contentDescription = "Nova osebna ikona"
        comparison.addView(original, LinearLayout.LayoutParams(0, 320, 1f))
        comparison.addView(image, LinearLayout.LayoutParams(0, 320, 1f))
        AlertDialog.Builder(this).setTitle("SISTEMSKA  |  OSEBNA: $candidateItem").setView(comparison)
            .setMessage("Preveri podobnost, slog, pomen in stran telesa. Uporaba zamenja samo to osebno ikono; stara datoteka ostane shranjena.")
            .setPositiveButton("UPORABI") { _, _ ->
                runCatching { service.approve(requireNotNull(candidatePatient), requireNotNull(candidateItem), file) }
                    .fold({ clearCandidate(); next() }, { queue.clear(); clearCandidate(); status.text = "Ikone ni mogoče uporabiti: ${it.message}" })
            }.setNeutralButton("PONOVI") { _, _ ->
                candidateItem?.let { queue.addFirst(it) }; clearCandidate(); next()
            }.setNegativeButton("ZAVRNI") { _, _ -> clearCandidate(); next() }
            .setOnCancelListener { queue.clear(); clearCandidate() }.show()
    }
    private fun clearCandidate() { candidate?.delete(); candidate = null; candidateItem = null; candidatePatient = null }
    private fun configureService() {
        val config = service.configuration()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val endpoint = EditText(this).apply { hint = "HTTPS naslov storitve"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(config.optString("endpoint")) }
        val token = EditText(this).apply { hint = "Dostop do te storitve"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(endpoint); layout.addView(token)
        AlertDialog.Builder(this).setTitle("Storitev za osebne ikone").setView(layout)
            .setMessage("Vnesi naslov in dostopni žeton namenske storitve. Ključ ponudnika slik ostane na strežniku.")
            .setPositiveButton("SHRANI") { _, _ ->
                runCatching {
                    val address = endpoint.text.toString().trim()
                    val access = token.text.toString().ifBlank { if (address == config.optString("endpoint")) config.optString("token") else "" }
                    service.configure(address, access)
                }
                    .fold({ status.text = "Storitev je nastavljena; povezava še ni preverjena." }, { status.text = "Potreben je veljaven HTTPS naslov in dostopni žeton." })
            }.setNegativeButton("PREKLIČI", null).show()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("pickerPatient", pickerPatient); super.onSaveInstanceState(outState) }
    override fun onDestroy() { closed = true; generationSerial++; service.cancel(); clearCandidate(); audio.release(); super.onDestroy() }
}

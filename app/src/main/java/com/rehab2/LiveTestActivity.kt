package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*

/** Caregiver tools, entered from the existing PIN-protected settings path. */
class LiveTestActivity : AppCompatActivity() {
    private var photoItem: String? = null
    private var photoPatient: String? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = photoItem; val patient = photoPatient
        photoItem = null; photoPatient = null
        if (uri != null && id != null && patient != null) {
            val saved = AacPatientImages.importPhoto(this, patient, id, uri)
            toast(if (saved) "Osebna slika je shranjena samo pri tem pacientu." else "Slike ni bilo mogoče shraniti.")
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var report: TextView
    private val refresh = object : Runnable {
        override fun run() {
            LiveTestSession.validate(this@LiveTestActivity)
            status.text = "${if (LiveTestSession.active) "PREIZKUS POTEKA • lokalno" else "PREIZKUS USTAVLJEN"}\n${AacSpeechPreparation.status}"
            report.text = LiveTestSession.report()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoItem = savedInstanceState?.getString("photoItem")
        photoPatient = savedInstanceState?.getString("photoPatient")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        fun text(value: String) = TextView(this).apply { text = value; textSize = 18f; setPadding(0, 12, 0, 12); root.addView(this) }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value; textSize = 18f; minHeight = 64; setOnClickListener { action() }; root.addView(this)
        }
        text("PREIZKUS NA TABLICI • 1.2.830")
        button("MIKROFON, KAMERA IN INDIVIDUALNI PROFIL") { startActivity(Intent(this, LiveCaptureActivity::class.java)) }
        text("Nova prva stran 5 × 5 je prikaz nad shranjeno postavitvijo. Osebne slike in podikone ostanejo povezane. Prejšnjo postavitev lahko ponovno prikažete.")
        button("UPORABI NOVO MREŽO 5 × 5") {
            if (AacHomeLayout.backup(this)) { AacHomeLayout.setEnabled(this, true); toast("Nova mreža je vključena.") }
            else toast("Varnostna kopija ni uspela; postavitev ostaja nespremenjena.")
        }
        button("OBNOVI PRIKAZ PREJŠNJE POSTAVITVE") {
            AacHomeLayout.setEnabled(this, false); toast("Prikazana bo prejšnja shranjena postavitev.")
        }
        button("NASTAVI ŠTIRI PREMORE NA 0 ms") {
            AlertDialog.Builder(this).setTitle("Neposreden odziv")
                .setMessage("Nastavim pavzo prve vrstice, glavnih ikon, podikon in vprašanj na 0 ms? Druge nastavitve sestavljanja stavkov ostanejo enake.")
                .setNegativeButton("PREKLIČI", null).setPositiveButton("NASTAVI") { _, _ ->
                    getSharedPreferences(AacSpeechTimingSettings.PREFS_FILE, MODE_PRIVATE).edit()
                        .putLong(AacSpeechTimingSettings.PREF_FIXED_TOP_ROW_SPEAK_DELAY_MS, 0)
                        .putLong(AacSpeechTimingSettings.PREF_MAIN_ICON_SPEAK_DELAY_MS, 0)
                        .putLong(AacSpeechTimingSettings.PREF_SUB_ICON_SPEAK_DELAY_MS, 0)
                        .putLong("aac_voice_assistant_question_delay_ms", 0).apply()
                    toast("Štirje premori so nastavljeni na 0 ms.")
                }.show()
        }
        button("PRIPRAVI GOVOR PRVE STRANI") {
            AlertDialog.Builder(this).setTitle("Priprava posnetkov")
                .setMessage("Za trenutno izbrani jezik pripravim največ 50 fraz pacientke in vodnika. Uporabim nastavljeni govorni servis; manjkajoči posnetki lahko povzročijo stroške. Shranjeni posnetki se ponovno uporabijo. Brez samodejnega predvajanja.")
                .setNegativeButton("PREKLIČI", null).setPositiveButton("PRIPRAVI") { _, _ ->
                    if (!AacSpeechPreparation.start(this, AacLanguageResolver.readSelectedLanguageCode(this))) toast("Priprava že poteka.")
                }.show()
        }
        button("USTAVI PRIPRAVO GOVORA") { AacSpeechPreparation.cancel() }
        text("Živi preizkus: lokalno spremljanje izbranih ikon, vračanja in časov povratnih klicev. Brez mikrofona, kamere, surovega zvoka ali videa; nič se ne pošilja. Podatki so samo v pomnilniku do zaprtja procesa ali največ 24 ur. Izbrišete jih lahko takoj.")
        button("ZAČNI PREIZKUS / ПОЧАТИ") {
            AlertDialog.Builder(this).setTitle("Želite sodelovati? / Бажаєте взяти участь?")
                .setMessage("Beležili bomo dotike ikon. Kadarkoli lahko pritisnete USTAVI.\nМи записуватимемо натискання значків. Можна зупинити будь-коли.")
                .setNegativeButton("NE / НІ", null).setPositiveButton("DA / ТАК") { _, _ ->
                    LiveTestSession.start(this)
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }.show()
        }
        button("USTAVI PREIZKUS / ЗУПИНИТИ") { LiveTestSession.stop() }
        status = text("")
        text("Mikrofon, kamera, povezava AI in nastavitve ob zagonu so na ločenem zaslonu zgoraj. Brez nastavljenega backenda je možen le lokalni zajem; AI analiza ni na voljo.")
        val note = EditText(this).apply { hint = "Kratek zapis skrbnika (brez osebnih podatkov)"; root.addView(this) }
        button("DODAJ OPAŽANJE • NEPOTRJENO") {
            if (LiveTestSession.active) { LiveTestSession.record("note_observed", note.text.toString()); note.text.clear() }
            else toast("Najprej vključite preizkus.")
        }
        button("DODAJ ODGOVOR, POTRJEN S PACIENTKO") {
            if (LiveTestSession.active) { LiveTestSession.record("note_confirmed", note.text.toString()); note.text.clear() }
            else toast("Najprej vključite preizkus.")
        }
        report = text("")
        text("Za skupni pregled: POPRAVI trenutno izgovori »Prosim, popravi to.«; RABIM in ŽELIM odpreta obstoječe izbire; GREMO izgovori »Gremo, prosim.«. Pomen POPRAVI in cilj GREMO še uskladimo s pacientko. Predloge za dodatne pojme umestimo na drugo stran ali podikone.")
        text("Za osebno fotografijo DRUŽINE ali PRIJATELJEV v urejevalniku izberite ustrezno ikono in uvozite fotografijo. Shranjena bo samo v zasebni shrambi trenutnega pacienta.")
        button("ZAMENJAJ FOTOGRAFIJO IKONE") {
            AlertDialog.Builder(this).setTitle("Izberi ikono za zasebno fotografijo")
                .setItems(AacHomeLayout.labels.toTypedArray()) { _, index ->
                    photoItem = AacHomeLayout.ids[index]
                    photoPatient = AacPatientImages.store(this).currentPatientId()
                    photoPicker.launch("image/*")
                }.setNegativeButton("PREKLIČI", null).show()
        }
        button("OSEBNE SLIKE IN FOTOGRAFIJE") { startActivity(Intent(this, PatientPersonalizationActivity::class.java)) }
        button("IZBRIŠI PODATKE PREIZKUSA") { LiveTestSession.clear(); report.text = LiveTestSession.report() }
        button("NAZAJ") { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("photoItem", photoItem); outState.putString("photoPatient", photoPatient)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}

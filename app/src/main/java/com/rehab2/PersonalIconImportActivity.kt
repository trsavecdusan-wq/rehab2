package com.rehab2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*
import com.rehab2.patient.PersonalIconZip
import java.io.File

/** Offline review. No generator, network, shared storage or catalogue writes. */
class PersonalIconImportActivity : AppCompatActivity() {
    private lateinit var patient: String
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var pack: PersonalIconZip? = null
    private var busy = false
    private var pickerPending = false
    @Volatile private var closed = false
    private val choices = linkedMapOf<CheckBox, Pair<String, PersonalIconZip.Entry>>()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerPending = false
        if (uri != null && !busy) {
            busy = true; status.text = "Preverjam ZIP, kontrolne vsote in slike …"
            val app = applicationContext
            Thread {
                val result = runCatching {
                    check(AacPatientImages.store(app).currentPatientId() == patient) { "Pacient se je spremenil. Uvoz začni znova." }
                    val prepared = app.contentResolver.openInputStream(uri)!!.use {
                        PersonalIconZip.read(it, File(app.noBackupFilesDir, "aac-patients/$patient/import-staging"))
                    }
                    try {
                        prepared.entries.forEach { entry ->
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(entry.file.path, options)
                            check(options.outMimeType == "image/png" && options.outWidth == entry.width && options.outHeight == entry.height) { "Napačen format ali mere: ${entry.motif}" }
                            val bitmap = BitmapFactory.decodeFile(entry.file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                            check(bitmap != null) { "Slike ni mogoče odpreti: ${entry.motif}" }
                            bitmap.recycle()
                        }
                        prepared
                    } catch (e: Exception) { prepared.close(); throw e }
                }
                runOnUiThread {
                    busy = false
                    if (closed) result.getOrNull()?.close()
                    else result.fold({ pack?.close(); pack = it; review() }, { status.text = "Uvoz ni izveden: ${it.message}" })
                }
            }.start()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        patient = state?.getString("patient") ?: AacPatientImages.store(this).currentPatientId()
        pickerPending = state?.getBoolean("pickerPending") ?: false
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,16,24,24) }
        setContentView(ScrollView(this).apply { addView(content) })
        header()
        status = text(if (state != null) "Za nov pregled znova izberi ZIP; že dokončan uvoz ostane shranjen." else "Pripravljeni PNG-ji se uvozijo brez interneta ali storitve za izdelavo slik.")
        button("IZBERI ZIP Z OSEBNIMI IKONAMI") {
            if (!busy && !pickerPending && samePatient()) {
                pickerPending = true
                picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        }
    }
    private fun header() {
        text("UVOZ OSEBNIH IKON", 24f)
        val data = org.json.JSONObject(String(AacPatientImages.profiles(this).profileFor(patient), Charsets.UTF_8))
        text("Izbrani pacient: ${data.optString("firstName")} ${data.optString("lastName")}\nUUID: $patient")
        button("NAZAJ / PREKLIČI PREGLED") { if (!busy) finish() }
    }
    private fun samePatient(): Boolean {
        if (AacPatientImages.store(this).currentPatientId() == patient) return true
        status.text = "Pacient se je spremenil. Ta uvoz je ustavljen. Zapri pregled in ga začni pri pravem pacientu."
        return false
    }
    private fun review() {
        content.removeAllViews(); choices.clear(); header()
        status = text("Preverjene slike: ${pack!!.entries.size}. Nič še ni uvoženo. Označi samo povezave, katerih pomen, podobnost in kretnjo potrjuješ. Nobena ni izbrana vnaprej. Izvirniki ostanejo zasebni. Razpored 25 ikon se ne spremeni.")
        val catalog = AacHomeLayout.catalog(this, AacEditorStorage.loadItems(this)).associateBy { it.id }
        pack!!.entries.forEach { entry ->
            text(entry.motif.uppercase(), 21f)
            val preview = ImageView(this).apply {
                setBackgroundColor(0xff35404b.toInt()); adjustViewBounds = true
                setImageBitmap(BitmapFactory.decodeFile(entry.file.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
                contentDescription = "Predogled ${entry.motif}"
                setOnClickListener { setBackgroundColor(if (tag == true) 0xff35404b.toInt() else 0xfff4f4f4.toInt()); tag = tag != true }
            }
            content.addView(preview, LinearLayout.LayoutParams(180,180))
            text("Dotakni se slike za svetlo/temno ozadje. Preveri robove in razumljivost na majhnem gumbu.")
            val warning = when(entry.motif) {
                "popravi" -> "ZA PREGLED: telesni položaj/blazina. Ne povezujemo s POPRAVI SPOROČILO (correct_message)."
                "boli_me" -> "ZA PREGLED: roka na trebuhu lahko pomeni trebušno, ne splošne bolečine."
                "slabo" -> "ZA PREGLED: posoda in roka pred usti lahko pomenita slabost/bruhanje."
                "ne", "pocakaj" -> "ZA PREGLED: podobni kretnji NE in POČAKAJ."
                "rabim", "zelim" -> "ZA PREGLED: razlikovanje RABIM in ŽELIM."
                "hitreje", "gremo" -> "ZA PREGLED: razlikovanje HITREJE in GREMO; preveri levo roko in voziček."
                "hvala" -> "ZA PREGLED: nasmeh sam ne določa pomena zahvale."
                "dobro", "da" -> "ZA PREGLED: podoben palec navzgor pri DOBRO in DA."
                else -> "Potrdi podobnost in pomen s pacientko oziroma skrbnikom."
            }
            text(warning)
            entry.candidates.forEach { id ->
                val item = catalog[id]
                if (item == null || item.locked || id == "correct_message") text("Brez uvoza: $id (neznan, zaščiten ali neustrezen ID).")
                else {
                    AacPatientImages.resolve(this,item)?.let { current ->
                        content.addView(ImageView(this).apply {
                            contentDescription = "Trenutna ikona ${item.labelSl}"
                            setImageBitmap(BitmapFactory.decodeFile(current.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
                        }, LinearLayout.LayoutParams(90,90))
                    }
                    val existing = AacPatientImages.store(this).hasBinding(patient,id) || AacPatientImages.personalFile(this,id,false) != null
                    val choice = CheckBox(this).apply {
                        text = "POTRJUJEM: ${item.labelSl} · $id\n${item.speakTextSl.orEmpty()}${if(existing) "\nZAMENJAVA OSEBNE SLIKE" else ""}"
                        minHeight = 72
                    }
                    choices[choice] = id to entry; content.addView(choice)
                }
            }
        }
        button("UVOZI POTRJENE POVEZAVE") { if (!busy && samePatient()) confirm() }
    }
    private fun confirm() {
        val selected = choices.filterKeys { it.isChecked }.values.toList()
        if (selected.isEmpty()) { status.text = "Najprej označi potrjene povezave."; return }
        if (selected.map { it.first }.distinct().size != selected.size) { status.text = "Za isti ID izberi samo eno sliko."; return }
        val replaced = selected.filter { AacPatientImages.store(this).hasBinding(patient,it.first) || AacPatientImages.personalFile(this,it.first,false) != null }.map { it.first }
        AlertDialog.Builder(this).setTitle("Potrdi zasebni uvoz")
            .setMessage("Pacient UUID: $patient\nPovezave: ${selected.size}\nZamenjave: ${replaced.joinToString().ifBlank { "ni" }}\nOhranjeni bodo stari izvirniki in varnostna kopija povezav. Slike se ne pošiljajo na internet.")
            .setNegativeButton("PREKLIČI",null)
            .setPositiveButton("POTRJUJEM UVOZ IN ZAMENJAVE") { _, _ ->
                if (!busy && samePatient()) {
                    busy = true; status.text = "Uvažam v zasebni profil …"; choices.keys.forEach { it.isEnabled = false }
                    val app = applicationContext
                    Thread {
                        val result = runCatching {
                            val catalog = AacHomeLayout.catalog(app,AacEditorStorage.loadItems(app)).associateBy { it.id }
                            check(selected.all { catalog[it.first]?.locked == false }) { "Katalog se je spremenil." }
                            AacPatientImages.store(app).importBatch(patient, selected.associate { it.first to it.second.file },
                                selected.associate { it.first to it.second.sha256 }, true) { file ->
                                val b = BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize = 4 }); val ok = b != null; b?.recycle(); ok
                            }
                        }
                        runOnUiThread {
                            busy = false
                            if (!closed) result.fold({
                                pack?.close(); pack = null; content.removeAllViews(); header()
                                status = text("Uvoz je shranjen v tem profilu: ${selected.size} povezav. Varnostna kopija: ${it.name}. Prikazne kopije se obnovijo iz novih izvirnikov. Deluje tudi brez interneta.")
                                button("ODPRI KOMUNIKATOR") {
                                    startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
                                }
                            }, { status.text = "Uvoz ni potrjen: ${it.message}. Prejšnje povezave se ob ponovnem odprtju samodejno obnovijo, če je bil zapis prekinjen."; choices.keys.forEach { it.isEnabled = true } })
                            else { pack?.close(); pack = null }
                        }
                    }.start()
                }
            }.show()
    }
    private fun text(value: String,size: Float=17f) = TextView(this).apply { text=value;textSize=size;setPadding(0,12,0,12);content.addView(this) }
    private fun button(label:String,action:()->Unit) { content.addView(Button(this).apply { text=label;minHeight=64;setOnClickListener { action() } }) }
    override fun onSaveInstanceState(out: Bundle) { out.putString("patient",patient);out.putBoolean("pickerPending",pickerPending);super.onSaveInstanceState(out) }
    override fun onDestroy() { closed=true;if(!busy){pack?.close();pack=null};super.onDestroy() }
}

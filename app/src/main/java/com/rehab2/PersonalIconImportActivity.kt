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

/** Private review; optional consented HTTPS package download. No generator or catalogue writes. */
class PersonalIconImportActivity : AppCompatActivity() {
    private lateinit var patient: String
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var pack: PersonalIconZip? = null
    private var busy = false
    private var pickerPending = false
    @Volatile private var closed = false
    @Volatile private var cancelled = false
    private val choices = linkedMapOf<CheckBox, Pair<String, PersonalIconZip.Entry>>()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerPending = false
        if (uri != null && !busy) prepare {
            applicationContext.contentResolver.openInputStream(uri)!!.use { readPackage(it) }
        }
    }
    private fun readPackage(input: java.io.InputStream): PersonalIconZip = PersonalIconZip.read(input,
        File(applicationContext.noBackupFilesDir, "aac-patients/$patient/import-staging"))
    private fun prepare(load: () -> PersonalIconZip) {
        busy = true; cancelled = false; status.text = "Preverjam paket, kontrolne vsote in slike …"
        val app = applicationContext
        Thread {
            val result = runCatching {
                check(AacPatientImages.store(app).currentPatientId() == patient) { "Pacient se je spremenil. Uvoz začni znova." }
                val prepared = load()
                try {
                    prepared.entries.forEach { entry ->
                        check(!closed && !cancelled) { "Prenos preklican." }
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(entry.file.path, options)
                        check(options.outMimeType == "image/png" && options.outWidth == entry.width && options.outHeight == entry.height) { "Napačen format ali mere: ${entry.motif}" }
                        val bitmap = BitmapFactory.decodeFile(entry.file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                        check(bitmap != null) { "Slike ni mogoče odpreti: ${entry.motif}" }
                        if (prepared.archiveSha256 == com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256) {
                            check(bitmap.hasAlpha()) { "Slika v3 nima alfa kanala." }
                            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                            check(pixels.any { (it ushr 24)==0 }) { "Slika v3 nima prosojnega ozadja." }
                        }
                        bitmap.recycle()
                    }
                    check(AacPatientImages.store(app).currentPatientId() == patient) { "Pacient se je spremenil. Slike niso uvožene." }
                    if (prepared.archiveSha256 == com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256) {
                        check(prepared.entries.map { it.candidates.singleOrNull() } == AacHomeLayout.ids) { "Povezave paketa v3 niso skladne s prvo stranjo." }
                        checkLanaOwner()
                    }
                    prepared
                } catch (e: Exception) { prepared.close(); throw e }
            }
            runOnUiThread {
                busy = false
                if (closed) result.getOrNull()?.close()
                else result.fold({ pack?.close(); pack = it; review() }, { status.text = "Uvoz ni izveden: ${it.message}\nObstoječe slike so ohranjene. Prenos lahko ponoviš." })
            }
        }.start()
    }
    private fun lanaOwner(): String? {
        val store=AacPatientImages.store(applicationContext)
        return store.confirmedSeedPatient(com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256)
            ?: store.confirmedSeedPatient(com.rehab2.patient.VerifiedPackageDownload.PREVIOUS_LANA_SHA256)
    }
    private fun checkLanaOwner() {
        val owner=lanaOwner()
        check(owner==null || owner==patient) { "Paket pripada drugemu potrjenemu Laninemu profilu." }
    }
    private fun downloadLana() {
        if (busy || pickerPending || !samePatient()) return
        val store = AacPatientImages.store(this)
        val hash = com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256
        val owner = lanaOwner()
        if (owner != null && owner != patient) {
            status.text = "Ta osebni paket je potrjen za drug profil. Preklopi na potrjeni Lanin profil; slike se drugim pacientom ne dodajo."
            return
        }
        val profile = org.json.JSONObject(String(AacPatientImages.profiles(this).profileFor(patient), Charsets.UTF_8))
        AlertDialog.Builder(this).setTitle("Je izbran Lanin profil?")
            .setMessage("${profile.optString("firstName")} ${profile.optString("lastName")}\nUUID: $patient\nPotrjujem, da ta profil pripada Lani. Preneslo se bo približno 32 MB z GitHuba. Paket je začasno javno dostopen. Novi paket v3 ima 25 slik. Prenos ne spremeni ikon; povezave in zamenjave potrdiš pozneje. POPRAVI ostane neizbran.")
            .setNegativeButton("PREKLIČI",null)
            .setPositiveButton("POTRJUJEM PROFIL IN PRENOS") { _, _ ->
                if (!busy && samePatient()) {
                    runCatching { store.confirmSeedPatient(hash,patient) }.onFailure { status.text = "Profila ni mogoče potrditi: ${it.message}" }.onSuccess {
                        prepare {
                            val file = com.rehab2.patient.VerifiedPackageDownload().download(
                                com.rehab2.patient.VerifiedPackageDownload.LANA_URL,hash,
                                com.rehab2.patient.VerifiedPackageDownload.LANA_BYTES,
                                File(applicationContext.noBackupFilesDir,"aac-patients/$patient/downloads"),
                                { closed || cancelled }, { bytes,total -> runOnUiThread { if (!closed) status.text = "Prenašam Lanine ikone: ${bytes * 100 / total} % — nato preverim SHA-256." } })
                            try { file.inputStream().use { readPackage(it) } } finally { file.delete() }
                        }
                    }
                }
            }.show()
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        patient = state?.getString("patient") ?: AacPatientImages.store(this).currentPatientId()
        pickerPending = state?.getBoolean("pickerPending") ?: false
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,16,24,24) }
        setContentView(ScrollView(this).apply { addView(content) })
        header()
        status = text(if (state != null) "Za nov pregled znova izberi ZIP; že dokončan uvoz ostane shranjen." else "Pripravljeni PNG-ji se uvozijo brez interneta ali storitve za izdelavo slik.")
        button("PRIKAZ BREZ NAPISOV / VRNI PREJŠNJE SLIKE") { if (!busy) startActivity(Intent(this,PersonalIconDisplayActivity::class.java)) }
        button("PRENESI LANINE OSEBNE IKONE — NOVI PAKET v3") { downloadLana() }
        button("PREKLIČI PRENOS") { cancelled = true }
        if (state == null && intent.getBooleanExtra("offerLana",false)) content.post { if (!closed) downloadLana() }
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
        status = text("Preverjene slike: ${pack!!.entries.size}. Nič še ni uvoženo. Označi samo povezave, katerih pomen, podobnost in kretnjo potrjuješ. Nobena ni izbrana vnaprej. Neoznačena povezava ohrani trenutno sliko. Izvirniki ostanejo zasebni. Razpored 25 ikon se ne spremeni.")
        val catalog = AacHomeLayout.catalog(this, AacEditorStorage.loadItems(this)).associateBy { it.id }
        pack!!.entries.forEach { entry ->
            text(entry.motif.uppercase(), 21f)
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xff35404b.toInt()); adjustViewBounds = true
                setImageBitmap(BitmapFactory.decodeFile(entry.file.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
                contentDescription = "Predogled ${entry.motif}"
                setOnClickListener { setBackgroundColor(if (tag == true) 0xff35404b.toInt() else 0xfff4f4f4.toInt()); tag = tag != true }
            }
            if (entry.candidates.size == 1) {
                val target = catalog[entry.candidates[0]]
                text("LEVO: TRENUTNA SLIKA · DESNO: NOVI PREDLOG")
                val comparison=LinearLayout(this)
                val oldFile=target?.let { AacPatientImages.resolve(this,it) }
                comparison.addView(ImageView(this).apply {
                    scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(0xff35404b.toInt())
                    contentDescription="Trenutna slika ${target?.labelSl.orEmpty()}"
                    oldFile?.let { setImageBitmap(BitmapFactory.decodeFile(it.path,BitmapFactory.Options().apply{inSampleSize=8})) }
                },LinearLayout.LayoutParams(180,180))
                comparison.addView(preview,LinearLayout.LayoutParams(180,180))
                preview.setOnClickListener {
                    val color=if(preview.tag==true)0xff35404b.toInt() else 0xffeeeeee.toInt();preview.tag=preview.tag!=true
                    for(i in 0 until comparison.childCount)comparison.getChildAt(i).setBackgroundColor(color)
                }
                content.addView(comparison)
            } else content.addView(preview, LinearLayout.LayoutParams(180,180))
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
            text(entry.reviewNote.ifBlank { warning })
            entry.candidates.forEach { id ->
                val item = catalog[id]
                if (item == null || item.locked || (id == "correct_message" &&
                    (pack!!.archiveSha256 != com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256 || entry.sha256 != "ad879cca02e4cbd147baf02f2f2e743a65dda445a84a43a661f2c1f0cf571093"))) text("Brez uvoza: $id (neznan, zaščiten ali neustrezen ID).")
                else {
                    if (entry.candidates.size > 1) AacPatientImages.resolve(this,item)?.let { current ->
                        content.addView(ImageView(this).apply {
                            contentDescription = "Trenutna ikona ${item.labelSl}"
                            setImageBitmap(BitmapFactory.decodeFile(current.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
                        }, LinearLayout.LayoutParams(90,90))
                    }
                    val existing = AacPatientImages.store(this).hasBinding(patient,id) || AacPatientImages.personalFile(this,id,false) != null
                    val choice = CheckBox(this).apply {
                        text = "ZAMENJAJ — POTRJUJEM: ${item.labelSl} · $id\n${item.speakTextSl.orEmpty()}${if(existing) "\nZAMENJAVA OSEBNE SLIKE" else ""}"
                        minHeight = 72
                    }
                    if (pack!!.archiveSha256 == com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256 && id in setOf("correct_message","need","i_want")) {
                        choice.setOnClickListener {
                            if (choice.isChecked) {
                                choice.isChecked=false
                                AlertDialog.Builder(this).setTitle("Posebna potrditev pomena: ${item.labelSl}")
                                    .setMessage(entry.reviewNote+"\nPotrjujem uporabo tega poskusnega predloga za ID $id. Pomen ni samodejno preverjen.")
                                    .setNegativeButton("OHRANI TRENUTNO",null).setPositiveButton("POTRJUJEM TA POMEN"){_,_->choice.isChecked=true}.show()
                            }
                        }
                    }
                    choices[choice] = id to entry; content.addView(choice)
                }
            }
        }
        button("UVOZI POTRJENE POVEZAVE") { if (!busy && samePatient()) confirm() }
    }
    private fun confirm() {
        val isV3 = pack?.archiveSha256 == com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256
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
                            if (isV3) {
                                checkLanaOwner()
                                AacPatientImages.store(app).confirmSeedPatient(com.rehab2.patient.VerifiedPackageDownload.LANA_SHA256,patient)
                            }
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

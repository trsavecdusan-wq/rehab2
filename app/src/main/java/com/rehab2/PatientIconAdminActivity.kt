package com.rehab2

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*
import com.rehab2.patient.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Local-only review. A fresh one-shot PIN grant is required for every activity entry. */
class PatientIconAdminActivity : AppCompatActivity() {
    companion object {
        private var grantUntil=0L
        @Synchronized fun openAfterPin(context: Context) {
            grantUntil=SystemClock.elapsedRealtime()+10000
            context.startActivity(Intent(context,PatientIconAdminActivity::class.java))
        }
        @Synchronized private fun consumeGrant(): Boolean {
            val valid=grantUntil>SystemClock.elapsedRealtime(); grantUntil=0; return valid
        }
    }
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var panel: LinearLayout
    private lateinit var items: List<AacItem>
    private lateinit var profiles: PatientProfileCatalog
    private lateinit var icons: PatientIconCatalog
    private var selectedPatient=""
    private var selectedItem="sleep"
    private var selectedTemplate=PatientIconGenerationTemplate.PERSON_ACTION
    private var pendingPatient=""
    private var pendingRequest=""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!consumeGrant()) { finish(); return }
        panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        setContentView(ScrollView(this).apply { addView(panel) })
        runCatching {
            profiles=PatientIconRuntime.profiles(this)
            items=(AacStarterContentV1.items()+AacEditorStorage.loadItems(this)).associateBy { it.id }.values.sortedBy { it.labelSl }
            icons=PatientIconRuntime.catalog(this,items)
            selectedPatient=profiles.active().id
            render()
        }.onFailure { message(it.message ?: "Podatki niso berljivi.") }
    }
    private fun message(text: String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
    private fun text(value: String) { panel.addView(TextView(this).apply { text=value; textSize=17f }) }
    private fun button(title: String, action: () -> Unit) { panel.addView(Button(this).apply { text=title; setOnClickListener { action() } }) }
    private fun operation(action: () -> Unit) {
        fun enable(view: android.view.View, value: Boolean) {
            view.isEnabled=value
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount) enable(view.getChildAt(i),value)
        }
        enable(panel,false)
        worker.execute {
            val result=runCatching(action)
            runOnUiThread { if(!isFinishing && !isDestroyed) { result.onFailure { message(it.message ?: "Operacija ni uspela.") }; render() } }
        }
    }
    private fun choose(title: String, labels: List<String>, action: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, i -> action(i) }.setNegativeButton("PREKLIČI",null).show()
    }
    private fun preview(file: File?) {
        if(file==null) { text("Slika ni na voljo; AAC uporabi besedilni/simbolni fallback."); return }
        val view=ImageView(this).apply { adjustViewBounds=true; contentDescription="Predogled slike" }
        panel.addView(view,LinearLayout.LayoutParams(-1,320))
        worker.execute {
            val options=BitmapFactory.Options().apply { inSampleSize=2 }
            val bitmap=BitmapFactory.decodeFile(file.path,options)
            runOnUiThread { if(view.parent!=null && !isDestroyed) view.setImageBitmap(bitmap) else bitmap?.recycle() }
        }
    }
    private fun render() {
        runCatching { renderContent() }.onFailure {
            panel.removeAllViews(); panel.isEnabled=true
            text("Lokalnih podatkov ni mogoče varno prebrati. Spremembe niso uporabljene.")
            button("NAZAJ") { finish() }
        }
    }
    private fun renderContent() {
        panel.isEnabled=true
        panel.removeAllViews()
        val profile=profiles.list().single { it.id==selectedPatient }
        val item=items.firstOrNull { it.id==selectedItem } ?: items.first()
        selectedItem=item.id
        text("USTVARI PACIENTOVO IKONO")
        text("Vse slike ostanejo lokalno. GENERATION BACKEND NOT CONNECTED")
        button("1. PACIENT: "+profile.displayName) {
            val all=profiles.list(); choose("Izberi pacienta",all.map { it.displayName }) { selectedPatient=all[it].id; render() }
        }
        button("DODAJ PACIENTOV PROFIL") {
            val name=EditText(this).apply { hint="Oznaka profila" }
            AlertDialog.Builder(this).setTitle("Nov lokalni pacient").setView(name).setPositiveButton("DODAJ") { _,_ ->
                operation { selectedPatient=profiles.create(name.text.toString()).id }
            }.setNegativeButton("PREKLIČI",null).show()
        }
        button("UPORABI TA PROFIL V AAC") { operation { profiles.select(profile.id); PatientIconRuntime.bind(this) } }
        text("Aktivni AAC pacient: "+profiles.active().displayName)
        button("VIZUALNI SPOL: "+profile.visualSex.name) {
            choose("Vizualni spol",PatientGender.values().map { it.name }) { index -> operation { profiles.save(profile.copy(visualSex=PatientGender.values()[index])) } }
        }
        button("SLOVNIČNI SPOL: "+profile.grammaticalGender.name) {
            choose("Slovnični spol",PatientGender.values().map { it.name }) { index -> operation { profiles.save(profile.copy(grammaticalGender=PatientGender.values()[index])) } }
        }
        text("Nastavitev glasu TTS ostaja ločena.")
        button("2. AAC: "+item.labelSl+" ["+item.id+"]") {
            choose("AAC element",items.map { it.labelSl+" ["+it.id+"]" }) { selectedItem=items[it].id; render() }
        }
        text("3. Trenutna ikona za izbrani profil")
        preview(PatientIconRuntime.image(this,item,profile.id))
        text("Oznaka: "+AacLabelResolver.resolve(item,"sl",profile.grammaticalGender))
        text("Govor: "+AacSpeechResolver.resolve(item,"sl",profile.grammaticalGender))
        text("4. Primarna referenčna fotografija")
        preview(icons.reference(profile.id))
        button("IZBERI / UVOZI / ZAMENJAJ REFERENCO") { pendingPatient=profile.id; pick(100) }
        button("POSNEMI REFERENČNO FOTOGRAFIJO") { pendingPatient=profile.id; camera() }
        button("ODSTRANI REFERENCO") {
            AlertDialog.Builder(this).setMessage("Odstranim primarno referenco tega pacienta?").setPositiveButton("ODSTRANI") { _,_ -> operation { icons.removeReference(profile.id) } }.setNegativeButton("PREKLIČI",null).show()
        }
        button("5. PREDLOGA: "+selectedTemplate.name) {
            choose("Predloga",PatientIconGenerationTemplate.values().map { it.name }) { selectedTemplate=PatientIconGenerationTemplate.values()[it]; render() }
        }
        button("6. PRIPRAVI ZAHTEVO IN PREDOGLED") {
            operation {
                val reference=icons.reference(profile.id) ?: error("Najprej izberite referenčno fotografijo.")
                val r=PatientIconGenerationRequest(UUID.randomUUID().toString(),profile.id,item.id,item.meaningId ?: item.meaning ?: item.id,
                    AacLabelResolver.resolve(item,"sl",profile.grammaticalGender),AacLabelResolver.resolve(item,"uk",profile.grammaticalGender),
                    AacSpeechResolver.resolve(item,"sl",profile.grammaticalGender),AacSpeechResolver.resolve(item,"uk",profile.grammaticalGender),
                    reference.name,profile.visualSex,item.categoryId.orEmpty(),selectedTemplate,System.currentTimeMillis())
                icons.create(r)
                runOnUiThread { AlertDialog.Builder(this).setTitle("GENERATION BACKEND NOT CONNECTED").setMessage(r.json().toString(2)+"\n\n"+selectedTemplate.payload(r).toString(2)).setPositiveButton("ZAPRI",null).show() }
            }
        }
        icons.requests(profile.id).filter { it.getString("itemId")==item.id }.reversed().forEach { r ->
            val id=r.getString("requestId"); val status=r.getString("status")
            text("Zahteva "+id+" — "+status)
            if(status=="READY_FOR_GENERATION") button("UVOZI LOKALNI KANDIDAT") { pendingPatient=profile.id; pendingRequest=id; pick(101) }
            if(status=="REVIEW_REQUIRED") {
                preview(icons.candidate(profile.id,id))
                val duplicate=icons.duplicateWarning(profile.id,id)
                if(duplicate) text("OPOZORILO: ista slika že obstaja v tem pacientovem profilu.")
                button("PREGLED / ODOBRI") {
                    val check=CheckBox(this).apply { text="Preveril/a sem pomen, identiteto po referenci, anatomijo, besedilo, watermark in izrez." }
                    AlertDialog.Builder(this).setTitle(if(duplicate) "Potrdi tudi namerno ponovno uporabo slike" else "Odobritev slike")
                        .setView(check).setPositiveButton("REPLACE / ODOBRI") { _,_ ->
                            if(!check.isChecked) message("Potreben je vizualni pregled.") else operation { icons.approve(profile.id,id,true,PatientIconReplacement.REPLACE,duplicate) }
                        }.setNeutralButton("KEEP OLD",null)
                        .setNegativeButton("CANCEL",null).show()
                }
                button("ZAVRNI") { operation { icons.reject(profile.id,id) } }
            }
        }
        val revisions=icons.revisions(profile.id,item.id)
        if(revisions.isNotEmpty()) {
            button("IZKLOPI PERSONALIZIRANO IKONO") { operation { icons.activateRevision(profile.id,item.id,null) } }
            button("POVRNI PREJŠNJO REVIZIJO") {
                choose("Odobrene revizije",revisions.map { "Revizija "+it.getInt("revision")+if(it.optBoolean("active")) " (aktivna)" else "" }) { index ->
                    operation { icons.activateRevision(profile.id,item.id,revisions[index].getInt("revision")) }
                }
            }
        }
        button("NAZAJ") { finish() }
    }
    private fun pick(code: Int) = runCatching {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) },code)
    }.onFailure { message("Izbirnik slik ni na voljo.") }
    private fun camera() {
        runCatching { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE),102) }.onFailure { message("Kamera ni na voljo; uporabite uvoz fotografije.") }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK || requestCode !in setOf(100,101,102)) return
        val patient=pendingPatient; val request=pendingRequest
        operation {
            val temp=File.createTempFile("patient_import_",".tmp",cacheDir)
            try {
                if(requestCode==102) {
                    val bitmap=data?.extras?.get("data") as? Bitmap ?: error("Fotografija ni na voljo.")
                    temp.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                    bitmap.recycle()
                } else {
                    val uri=data?.data ?: error("Slika ni izbrana.")
                    contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                        val buffer=ByteArray(8192); var total=0
                        while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=16*1024*1024) { "Slika je prevelika." }; output.write(buffer,0,n) }
                    } } ?: error("Slike ni mogoče prebrati.")
                }
                if(requestCode==101) icons.importCandidate(patient,request,temp) else icons.setReference(patient,temp)
            } finally { temp.delete() }
        }
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}

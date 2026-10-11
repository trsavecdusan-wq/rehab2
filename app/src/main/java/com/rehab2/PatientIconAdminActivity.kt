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
import java.util.concurrent.Executors

/** Explicit admin generation and local review. A fresh one-shot PIN grant is required for every activity entry. */
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
    private var generationCancellation: PatientGenerationCancellation? = null
    private var bodyRegion: String? = null
    private var bodySide: PatientBodySide? = null
    private var bodyView: PatientBodyView? = null
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
        val backend=PatientGenerationBackendSettings.read(this)
        text(if(backend.validEndpoint()) "Referenčna fotografija se pošlje na ${backend.endpoint} samo ob GENERIRAJ." else "GENERIRANJE ŠE NI POVEZANO")
        if(backend.requireAuth && runCatching { PatientGenerationBackendSettings.session.token(backend.endpoint) }.getOrNull().isNullOrBlank()) text("Prijava v backend še ni povezana.")
        button("NASTAVITVE GENERIRANJA") {
            val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            val endpoint=EditText(this).apply { hint="HTTPS endpoint"; setText(backend.endpoint) }
            val auth=CheckBox(this).apply { text="Backend zahteva prijavljeno sejo"; isChecked=backend.requireAuth }
            form.addView(endpoint);form.addView(auth)
            AlertDialog.Builder(this).setTitle("Backend za slike").setView(form).setPositiveButton("SHRANI") { _,_ ->
                runCatching { PatientGenerationBackendSettings.save(this,endpoint.text.toString(),auth.isChecked) }
                    .onFailure { message(it.message ?: "Nastavitev ni veljavna.") }
                render()
            }.setNegativeButton("PREKLIČI",null).show()
        }
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
        text("Glas je ločen od slovničnega in vizualnega spola. Prazen ID uporabi privzeti glas.")
        for (language in listOf("sl", "uk")) for (role in listOf(AacSpeechRole.PATIENT, AacSpeechRole.GUIDE)) {
            val patient = role == AacSpeechRole.PATIENT
            val resolved = AacVoiceResolver.resolve(role, language, profile)
            button("GLAS $role / $language: ${resolved.voiceId}") {
                val form = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                val providerId = EditText(this).apply {
                    hint="ID obstoječega TTS ponudnika (prazno = privzeto)"
                    setText((if(patient) profile.patientVoiceByLanguage else profile.guideVoiceByLanguage)[language].orEmpty())
                }
                val androidId = EditText(this).apply {
                    hint="ID nameščenega Android glasu (prazno = samodejno)"
                    setText((if(patient) profile.patientAndroidVoiceByLanguage else profile.guideAndroidVoiceByLanguage)[language].orEmpty())
                }
                form.addView(providerId); form.addView(androidId)
                AlertDialog.Builder(this).setTitle("$role / $language").setView(form).setPositiveButton("SHRANI") { _,_ ->
                    val provider=providerId.text.toString().trim(); val android=androidId.text.toString().trim()
                    operation {
                        val updated=if(patient) profile.copy(patientVoiceByLanguage=profile.patientVoiceByLanguage+(language to provider),
                            patientAndroidVoiceByLanguage=profile.patientAndroidVoiceByLanguage+(language to android))
                        else profile.copy(guideVoiceByLanguage=profile.guideVoiceByLanguage+(language to provider),
                            guideAndroidVoiceByLanguage=profile.guideAndroidVoiceByLanguage+(language to android))
                        profiles.save(updated)
                    }
                }.setNegativeButton("PREKLIČI",null).show()
            }
        }
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
        if(selectedTemplate==PatientIconGenerationTemplate.BODY_PAIN) {
            button("DEL TELESA: "+(bodyRegion ?: "IZBERI")) {
                val regions=listOf("head","chest","abdomen","back","shoulder","arm","hand","hip","leg","knee","foot")
                choose("Del telesa",listOf("Glava","Prsni koš","Trebuh","Hrbet","Rama","Roka","Dlan","Kolk","Noga","Koleno","Stopalo")) { bodyRegion=regions[it]; render() }
            }
            button("PACIENTOVA STRAN: "+(bodySide?.name ?: "IZBERI")) {
                choose("Stran pacientovega telesa",PatientBodySide.values().map { it.name }) { bodySide=PatientBodySide.values()[it]; render() }
            }
            button("POGLED: "+(bodyView?.name ?: "IZBERI")) {
                choose("Pogled",PatientBodyView.values().map { it.name }) { bodyView=PatientBodyView.values()[it]; render() }
            }
            text("Pri pogledu od spredaj je pacientova LEVA na gledalčevi DESNI.")
        }
        button("6. GENERIRAJ") { generate(profile,item,selectedTemplate,bodyRegion,bodySide,bodyView) }
        icons.requests(profile.id).filter { it.getString("itemId")==item.id }.reversed().forEach { r ->
            val id=r.getString("requestId"); val status=r.getString("status")
            text("Zahteva "+id+" — "+status)
            button("PONOVNO GENERIRAJ") {
                generate(profile,item,PatientIconGenerationTemplate.valueOf(r.getString("generationTemplate")),
                    r.optString("bodyRegion").takeIf { it.isNotBlank() },
                    runCatching { PatientBodySide.valueOf(r.getString("bodySide")) }.getOrNull(),
                    runCatching { PatientBodyView.valueOf(r.getString("view")) }.getOrNull())
            }
            if(status=="READY_FOR_GENERATION") button("UVOZI LOKALNI KANDIDAT") { pendingPatient=profile.id; pendingRequest=id; pick(101) }
            if(status=="REVIEW_REQUIRED") {
                text("AI GENERATED / PATIENT — KANDIDAT, NI AKTIVEN")
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
    private fun generate(profile: PatientProfile, item: AacItem, template: PatientIconGenerationTemplate,
        region: String?, side: PatientBodySide?, view: PatientBodyView?) {
        if(generationCancellation!=null) return
        val reference=icons.reference(profile.id)
        if(reference==null) { message("NAJPREJ DODAJ REFERENČNO FOTOGRAFIJO"); return }
        val request=runCatching { PatientIconRequestBuilder.create(profile,item,reference.name,template,
            if(template==PatientIconGenerationTemplate.BODY_PAIN) region else null,
            if(template==PatientIconGenerationTemplate.BODY_PAIN) side else null,
            if(template==PatientIconGenerationTemplate.BODY_PAIN) view else null) }.getOrElse {
            message("Za bolečino izberi del telesa, pacientovo stran in pogled."); return
        }
        val cancellation=PatientGenerationCancellation(); generationCancellation=cancellation
        panel.removeAllViews(); text("GENERIRANJE — počakajte. Kandidat se ne aktivira samodejno.")
        button("PREKLIČI GENERIRANJE") { cancellation.cancel(); message("Generiranje je preklicano.") }
        worker.execute {
            val outcome=runCatching {
                icons.create(request)
                PatientIconGenerationWorkflow(icons,PatientGenerationBackendSettings.transport(this,cancellation)).generate(request,cancellation)
            }
            runOnUiThread {
                generationCancellation=null
                if(!isFinishing && !isDestroyed) {
                    val status=outcome.getOrNull()?.status
                    message(when(status) {
                        GenerationBackendStatus.SUCCESS -> "Kandidat je pripravljen za pregled. Potrebna je odobritev."
                        GenerationBackendStatus.CONFIGURATION_REQUIRED -> "GENERIRANJE ŠE NI POVEZANO"
                        GenerationBackendStatus.AUTH_ERROR -> "Prijava v backend ni veljavna."
                        GenerationBackendStatus.NETWORK_ERROR -> "Omrežje ni dosegljivo ali je čas čakanja potekel. AAC ostaja na voljo."
                        GenerationBackendStatus.CANCELLED -> "Generiranje je preklicano."
                        GenerationBackendStatus.INVALID_RESPONSE -> "Kandidat ni veljaven in ni bil uporabljen."
                        GenerationBackendStatus.SERVER_ERROR -> "Backend trenutno ne more ustvariti slike."
                        else -> "Generiranje ni uspelo. Preveri referenčno fotografijo in poskusi znova."
                    })
                    render()
                }
            }
        }
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
    override fun onDestroy() { generationCancellation?.cancel(); worker.shutdownNow(); super.onDestroy() }
}

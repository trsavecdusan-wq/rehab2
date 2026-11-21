package com.rehab2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rehab2.aac.*
import org.json.JSONObject

/** Entered through PIN-protected caregiver settings. Consent precedes each new capture permission. */
class LiveCaptureActivity:AppCompatActivity(){
    private lateinit var root:LinearLayout
    private lateinit var status:TextView
    private lateinit var mic:Switch
    private lateinit var cam:Switch
    private lateinit var ai:Switch
    private lateinit var raw:Switch
    private var binding=false
    private var pendingPermission=""
    private var pendingPatient=""
    private val handler=Handler(Looper.getMainLooper())
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted ->
        if(pendingPatient==AacPatientImages.store(this).currentPatientId()){
            val s=LiveCaptureSettings.load(this)
            LiveCaptureSettings.save(this,if(pendingPermission==Manifest.permission.RECORD_AUDIO)s.copy(microphone=granted) else s.copy(camera=granted))
            sync();if(!granted)toast("Dovoljenje je zavrnjeno. Komunikator normalno deluje.")
        }
    }
    private var exportText=""
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->
        if(uri!=null)runCatching{contentResolver.openOutputStream(uri)?.use{it.write(exportText.toByteArray(Charsets.UTF_8))}}.onFailure{toast("Izvoz ni uspel.")}
        exportText=""
    }
    private val refresh=object:Runnable{override fun run(){
        LiveTestSession.validate(this@LiveCaptureActivity)
        status.text=LiveCapture.status+"\n"+if(LiveTestSession.active)"SPREMLJANJE VKLJUČENO • ${LiveTestSession.elapsedMs()/1000} s" else "SPREMLJANJE USTAVLJENO"
        handler.postDelayed(this,1000)
    }}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        pendingPermission=savedInstanceState?.getString("permission").orEmpty();pendingPatient=savedInstanceState?.getString("patient").orEmpty()
        exportText=savedInstanceState?.getString("exportText").orEmpty()
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16)}
        text("SPREMLJANJE POSAMEZNEGA PACIENTA • 1.2.830")
        button("USTAVI SPREMLJANJE / ЗУПИНИТИ"){LiveTestSession.stop()}
        status=text("")
        text("Zajem poteka samo, ko je v ospredju komunikator ali zaslon preizkusa. V ozadju se mikrofon in kamera sprostita. Vračanje iz nastavitev ne prekliče ročne ustavitve. Nov zagon aplikacije upošteva spodnji izbiri trenutnega pacienta.")
        mic=toggle("MIKROFON OB ZAGONU"){enabled->captureChoice(true,enabled)}
        cam=toggle("KAMERA OB ZAGONU"){enabled->captureChoice(false,enabled)}
        ai=toggle("POŠILJANJE ZVOKA IN SLIK STORITVI AI"){enabled->
            choice(enabled,"Pošiljanje OpenAI prek vašega HTTPS-backenda. Zvok in slike lahko vsebujejo občutljive osebne podatke. Backend surovih posnetkov ne shranjuje; obdelava pri ponudniku ima njegova pravila hrambe. Privolitev velja tudi ob samodejnem zagonu."){
                LiveCaptureSettings.save(this,LiveCaptureSettings.load(this).copy(sendAi=enabled));LiveCapture.settingsChanged(this);sync()
            }
        }
        raw=toggle("SHRANI SUROVE POSNETKE LOKALNO • NAJVEČ 24 UR"){enabled->
            choice(enabled,"Izrecno dovolite zasebno lokalno hrambo zvoka in slik do 24 ur, največ 20 MB na pacienta. Privzeto ni vključena. Izvoz poročila ne vključuje teh posnetkov. Pretekle posnetke lahko izbrišete spodaj."){
                LiveCaptureSettings.save(this,LiveCaptureSettings.load(this).copy(keepRaw=enabled));LiveCapture.settingsChanged(this);sync()
            }
        }
        sync()
        text("Brez pošiljanja AI se beležijo le časi zajema in dogodki; brez samodejnega prepisa ali razlage slike. Pri pošiljanju je potreben preverjen backend. Ob napaki ni čakalne vrste posnetkov.")
        button("ZAČNI / PONOVNO POVEŽI SPREMLJANJE"){consentStart()}
        button("ODPRI KOMUNIKATOR"){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish()}
        text("POVEZAVA BACKENDA")
        val address=edit("HTTPS-naslov strežnika",LiveBackend.url(this))
        val token=edit("Omejeni ključ naprave (ne OpenAI API-ključ)","").apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
        button("SHRANI POVEZAVO"){
            LiveTestSession.stop()
            runCatching{LiveBackend.save(this,address.text.toString(),token.text.toString())}.onSuccess{token.text.clear();toast("Povezava shranjena. Preverite povezavo.")}.onFailure{toast(it.message?:"Napaka")}
        }
        button("PREVERI POVEZAVO"){LiveCapture.check(this){toast(it)}}
        text("POTRJENI PODATKI PACIENTA — ostanejo samo v tem profilu. Zapišite potrjene sposobnosti, jezik, način izbire, gibalne omejitve, preference in navade pred poškodbo. Ne sklepajte iz same mimike.")
        val note=edit("Podatek ali opažanje","")
        button("DODAJ PODATEK Z VIROM"){
            val sources=arrayOf("SKRBNIK: navade pred poškodbo","PACIENT: izrecno potrjeno","SKRBNIK: potrjena sposobnost ali preferenca","OPAŽANJE: nepotrjeno / NE VEM")
            AlertDialog.Builder(this).setTitle("Vir podatka").setItems(sources){_,i->
                runCatching{LivePatientNotes.add(this,note.text.toString(),sources[i])}.onSuccess{note.text.clear();toast("Shranjeno samo v tem profilu.")}.onFailure{toast(it.message?:"Napaka")}
            }.show()
        }
        button("PREGLEJ / POPRAVI / IZBRIŠI PODATKE PROFILA"){profile()}
        text("OPAŽANJA AI IN POTRJENI ODGOVORI SO LOČENI. Govorec je privzeto NEZNAN. Nepotrjen prepis, prikimavanje ali pogled ne pomenijo DA. Pri nejasnem glasu uporabite DA / NE / NE VEM ali skrbnikovo potrditev.")
        button("PREGLEJ OPAŽANJE IN POTRDI POMEN / GOVORCA"){review()}
        button("PREDLOGI ZA IKONE • NAJVEČ 3"){suggestions()}
        button("POROČILO TRENUTNE SEJE"){show("Poročilo",LiveTestSession.report())}
        button("SHRANI POROČILO ZASEBNO • IZBERI HRAMBO"){
            AlertDialog.Builder(this).setTitle("Hramba poročila brez surovega zvoka/slik").setItems(arrayOf("Samo v pomnilniku","7 dni","30 dni")){_,i->
                if(i>0)LivePatientNotes.saveReport(this,if(i==1)7 else 30,LiveTestSession.report());toast("Izbira upoštevana.")
            }.show()
        }
        button("PRETEKLA POROČILA TEGA PACIENTA"){
            val files=LivePatientNotes.reports(this)
            AlertDialog.Builder(this).setTitle("Zasebna poročila").setItems(files.map{it.name}.toTypedArray()){_,i->show("Poročilo",JSONObject(files[i].readText()).optString("report"))}.show()
        }
        button("IZVOZI POROČILO (BREZ POSNETKOV)"){
            exportText=LiveTestSession.report();export.launch("NovaRehab-preizkus.txt")
        }
        button("PREKLIČI SODELOVANJE IN IZBRIŠI SEJE"){
            LiveCaptureSettings.save(this,LiveCaptureSettings.Settings());LiveTestSession.stop();LiveTestSession.clear()
            LivePatientNotes.deleteReports(this);LivePatientNotes.deleteRaw(this);sync();toast("Zajem preklican; lokalne seje izbrisane. Profil lahko ločeno popravite ali izbrišete.")
        }
        button("IZBRIŠI SUROVE POSNETKE TEGA PACIENTA"){LivePatientNotes.deleteRaw(this);toast("Lokalni posnetki izbrisani.")}
        button("NAZAJ"){finish()}
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        outer.addView(Button(this).apply{
            id=R.id.btnLiveTestStop;text="USTAVI SPREMLJANJE / ЗУПИНИТИ";textSize=18f
            minHeight=(72*resources.displayMetrics.density).toInt();setOnClickListener{LiveTestSession.stop()}
        })
        outer.addView(ScrollView(this).apply{addView(root)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(outer)
    }
    private fun sync(){binding=true;val s=LiveCaptureSettings.load(this);mic.isChecked=s.microphone;cam.isChecked=s.camera;ai.isChecked=s.sendAi;raw.isChecked=s.keepRaw;binding=false}
    private fun captureChoice(microphone:Boolean,enabled:Boolean){
        if(!enabled){val s=LiveCaptureSettings.load(this);LiveCaptureSettings.save(this,if(microphone)s.copy(microphone=false)else s.copy(camera=false));LiveCapture.settingsChanged(this);return}
        choice(true,"${if(microphone)"Mikrofon zajema pogovor v 12-sekundnih delih, tudi posamezne besede in premore." else "Sprednja kamera zajema tri majhne slike vsakih 20 sekund; hitri gibi so lahko spregledani."}\nNastavitev velja za ta profil tudi ob prihodnjih zagonih. Pošiljanje AI in shranjevanje sta ločeni izbiri. Kadarkoli USTAVI.\nЗгодні? Можна зупинити будь-коли."){
            val permission=if(microphone)Manifest.permission.RECORD_AUDIO else Manifest.permission.CAMERA
            if(ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED){
                val s=LiveCaptureSettings.load(this);LiveCaptureSettings.save(this,if(microphone)s.copy(microphone=true)else s.copy(camera=true));sync()
            }else{pendingPermission=permission;pendingPatient=AacPatientImages.store(this).currentPatientId();permissions.launch(permission)}
        }
    }
    private fun choice(enabled:Boolean,message:String,action:()->Unit){
        if(!enabled){action();return}
        sync()
        AlertDialog.Builder(this).setTitle("Sodelovanje / Участь").setMessage(message)
            .setNegativeButton("NE / НІ"){_,_->sync()}.setNeutralButton("NE VEM / НЕ ЗНАЮ"){_,_->sync()}
            .setPositiveButton("DA / ТАК"){_,_->action()}.setOnCancelListener{sync()}.show()
    }
    private fun consentStart(){
        AlertDialog.Builder(this).setTitle("Začnemo? / Почнемо?").setMessage("Uporabljene bodo zgornje izbire. USTAVI prekine spremljanje.\nЗастосовуються вибрані налаштування. ЗУПИНИТИ припиняє спостереження.")
            .setNegativeButton("NE / НІ",null).setNeutralButton("NE VEM / НЕ ЗНАЮ",null).setPositiveButton("DA / ТАК"){_,_->
                val s=LiveCaptureSettings.load(this)
                val m=s.microphone && ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
                val c=s.camera && ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
                LiveCapture.start(this,m,c){toast(if(it)"Seja vključena; stanje zajema je prikazano zgoraj." else "Backend ni dosegljiv. Zajem AI izključen.")}
            }.show()
    }
    private fun profile(){
        val rows=LivePatientNotes.load(this)
        AlertDialog.Builder(this).setTitle("Podatki in viri").setItems((0 until rows.length()).map{rows.getJSONObject(it).let{j->"${j.optString("source")}: ${j.optString("text")}"}}.toTypedArray()){_,i->
            val input=EditText(this).apply{setText(rows.getJSONObject(i).optString("text"))}
            AlertDialog.Builder(this).setTitle("Popravek skrbnika").setView(input).setPositiveButton("SHRANI"){_,_->LivePatientNotes.replace(this,i,input.text.toString())}
                .setNeutralButton("IZBRIŠI"){_,_->LivePatientNotes.replace(this,i,null)}.setNegativeButton("NAZAJ",null).show()
        }.show()
    }
    private fun review(){
        val rows=LiveTestSession.snapshot().filter{it.kind.startsWith("ai_") || it.kind=="note_observed"}.takeLast(50)
        AlertDialog.Builder(this).setTitle("Izberi konkretni dogodek").setItems(rows.map{"${it.elapsedMs} ms ${it.detail}"}.toTypedArray()){_,i->
            val event=rows[i];val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            val meaning=EditText(this).apply{hint="Popravljen prepis ali potrjeni pomen (lahko NE VEM)";form.addView(this)}
            val source=Spinner(this).apply{adapter=ArrayAdapter(this@LiveCaptureActivity,android.R.layout.simple_spinner_dropdown_item,listOf("NEZNAN / NE VEM","PACIENT — ročno potrjen","SKRBNIK — ročno potrjen"));form.addView(this)}
            AlertDialog.Builder(this).setTitle("DA / NE / NE VEM — potrdite s pacientom").setMessage(event.detail).setView(form)
                .setPositiveButton("ZAPIŠI POTRDITEV"){_,_->
                    val detail="Dogodek ${event.elapsedMs} ms; vir ${source.selectedItem}; ${meaning.text}"
                    LiveTestSession.annotate(if(source.selectedItemPosition==0)"review_unknown" else "review_confirmed",detail)
                }.setNeutralButton("SHRANI INDIVIDUALNI ZNAK"){_,_->
                    if(source.selectedItemPosition==0 || meaning.text.isBlank())toast("Najprej izrecno potrdite pomen in vir.")
                    else LivePatientNotes.add(this,meaning.text.toString(),source.selectedItem.toString(),"Seja ${LiveTestSession.sessionId}, ${event.elapsedMs} ms ${event.detail}")
                }.setNegativeButton("NAZAJ",null).show()
        }.show()
    }
    private fun suggestions(){
        val rows=LiveSuggestions.review(this)
        if(rows.isEmpty()){show("Predlogi","Ni dovolj povezanih dogodkov za predlog. Za pregled zabeležite pogovor, iskanje, izbiro in potrditev.");return}
        AlertDialog.Builder(this).setTitle("Največ tri prednostne izboljšave").setItems(rows.map{it.text}.toTypedArray()){_,i->
            val p=rows[i]
            AlertDialog.Builder(this).setTitle("Predlog — negotov, potrebuje pregled").setMessage("${p.text}\n${p.existingPath}\n${p.evidence}\nPrva stran ostane 25 ikon. Uredite drugo stran ali podikono.")
                .setPositiveButton("POTRDI PREGLED V UREJEVALNIKU"){_,_->LiveTestSession.annotate("review_proposal",p.text+" "+p.evidence);LiveTestSession.stop();startActivity(Intent(this,AacEditorActivity::class.java))}
                .setNegativeButton("NE / NE VEM",null).show()
        }.show()
    }
    private fun toggle(label:String,changed:(Boolean)->Unit)=Switch(this).apply{text=label;textSize=18f;minHeight=72;root.addView(this);setOnCheckedChangeListener{_,checked->if(!binding)changed(checked)}}
    private fun text(value:String)=TextView(this).apply{text=value;textSize=18f;setPadding(0,16,0,12);root.addView(this)}
    private fun edit(hintText:String,value:String)=EditText(this).apply{hint=hintText;setText(value);root.addView(this)}
    private fun button(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=18f;minHeight=80;root.addView(this);setOnClickListener{action()}}
    private fun show(title:String,body:String){AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("ZAPRI",null).show()}
    private fun toast(value:String){Toast.makeText(this,value,Toast.LENGTH_LONG).show()}
    override fun onResume(){super.onResume();handler.post(refresh)}
    override fun onPause(){handler.removeCallbacks(refresh);super.onPause()}
    override fun onSaveInstanceState(outState:Bundle){outState.putString("permission",pendingPermission);outState.putString("patient",pendingPatient);outState.putString("exportText",exportText);super.onSaveInstanceState(outState)}
}

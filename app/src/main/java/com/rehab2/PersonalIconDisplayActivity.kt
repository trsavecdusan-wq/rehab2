package com.rehab2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*
import java.io.File

class PersonalIconDisplayActivity : AppCompatActivity() {
    private lateinit var patient: String
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var busy=false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        patient=state?.getString("patient") ?: AacPatientImages.store(this).currentPatientId()
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,24) }
        setContentView(ScrollView(this).apply { addView(content) })
        text("PRIKAZ IN OBNOVA OSEBNIH SLIK",24f)
        val data=org.json.JSONObject(String(AacPatientImages.profiles(this).profileFor(patient),Charsets.UTF_8))
        text("${data.optString("firstName")} ${data.optString("lastName")}\nUUID: $patient")
        status=text("Nastavitvi veljata samo za ta profil. Položaji, govor in podatki se ne spreminjajo.")
        val hidden=CheckBox(this).apply { text="BREZ NAPISOV (POSKUSNO)";isChecked=AacIconPresentation.hidden(this@PersonalIconDisplayActivity);minHeight=64 }
        val light=CheckBox(this).apply { text="SVETLO OZADJE PRI PRIKAZU BREZ NAPISOV";isChecked=AacIconPresentation.light(this@PersonalIconDisplayActivity);minHeight=64 }
        content.addView(hidden);content.addView(light)
        val save={ runCatching { AacIconPresentation.save(this,patient,hidden.isChecked,light.isChecked) }.fold({status.text="Shranjeno za ta profil. Napisi ostanejo v nastavitvah in opisih za dostopnost."},{status.text=it.message}) }
        hidden.setOnCheckedChangeListener { _,_->save() };light.setOnCheckedChangeListener { _,_->save() }
        button("VRNI PREJŠNJE SLIKE — PREDOGLED") { if(!busy) backups() }
        button("VRNI PREJŠNJI RAZPORED") { if(!busy) gridBackups() }
        button("ODPRI KOMUNIKATOR") { openMain() }
        button("NAZAJ") { if(!busy)finish() }
    }
    private fun gridBackups() {
        runCatching {
            val store=AacPatientImages.store(this);val ids=store.gridBackups(patient)
            if(ids.isEmpty()){status.text="Ni kopije prejšnjega razporeda.";return}
            val labels=ids.map{java.text.DateFormat.getDateTimeInstance().format(java.util.Date(store.imageBackupTime(patient,it)))+" · "+it.take(8)}
            AlertDialog.Builder(this).setTitle("Izberi kopijo razporeda").setItems(labels.toTypedArray()){_,which->
                AlertDialog.Builder(this).setTitle("VRNI PREJŠNJI RAZPORED")
                    .setMessage("Profil: $patient\nVrne se razpored pred izbrano spremembo. Slike in govor ostanejo shranjeni; slike lahko obnoviš ločeno.")
                    .setNegativeButton("OHRANI TRENUTNEGA",null).setPositiveButton("POTRJUJEM VRNITEV"){_,_->
                        runCatching{store.restoreGrid(patient,ids[which])}.fold({status.text="Razpored obnovljen. Odpri komunikator."},{status.text="Obnova ni izvedena: ${it.message}"})
                    }.show()
            }.setNegativeButton("PREKLIČI",null).show()
        }.onFailure{status.text="Obnova ni izvedena: ${it.message}"}
    }
    private fun backups() {
        runCatching {
            check(patient==AacPatientImages.store(this).currentPatientId()) { "Pacient se je spremenil." }
            val store=AacPatientImages.store(this);val ids=store.imageBackups(patient)
            if(ids.isEmpty()){status.text="Za ta profil še ni varnostne kopije uvoza.";return}
            val labels=ids.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(store.imageBackupTime(patient,it)))+" · "+it.take(8) }
            AlertDialog.Builder(this).setTitle("Zbirka pred uvozom / obnovo").setItems(labels.toTypedArray()){_,which->preview(ids[which])}.setNegativeButton("PREKLIČI",null).show()
        }.onFailure { status.text="Obnova ni izvedena: ${it.message}" }
    }
    private fun preview(backup: String) {
        runCatching {
            val store=AacPatientImages.store(this);val before=store.imageBackupBindings(patient,backup);val current=store.imageBindings(patient)
            val catalog=AacHomeLayout.catalog(this,AacEditorStorage.loadItems(this)).associateBy { it.id }
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,10,20,10) }
            body.addView(TextView(this).apply { text="Profil: $patient\nLevo: trenutno. Desno: pred izbranim uvozom. Če prejšnje osebne slike ni, se vrne splošna oziroma ohranjena prejšnja osnovna slika. Druge nastavitve ostanejo enake." })
            (before.keys+current.keys).sorted().filter { before[it]!=current[it] }.forEach { id ->
                body.addView(TextView(this).apply { text="${catalog[id]?.labelSl ?: id} · $id";textSize=18f })
                val row=LinearLayout(this)
                listOf(current[id],before[id]).forEach { file ->
                    if(file!=null) row.addView(ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(0xff35404b.toInt());setImageBitmap(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=8})) },LinearLayout.LayoutParams(160,160))
                    else row.addView(TextView(this).apply { text="Brez osebne zamenjave" },LinearLayout.LayoutParams(160,160))
                }
                body.addView(row)
            }
            AlertDialog.Builder(this).setTitle("Potrdi vrnitev prejšnje zbirke").setView(ScrollView(this).apply{addView(body)})
                .setNegativeButton("OHRANI TRENUTNE",null).setPositiveButton("VRNI PREJŠNJE SLIKE"){_,_->restore(backup)}.show()
        }.onFailure { status.text="Predogled ni dosegljiv: ${it.message}" }
    }
    private fun restore(backup:String) {
        if(busy)return;busy=true;status.text="Obnavljam povezave slik …"
        val app=applicationContext
        Thread {
            val result=runCatching { AacPatientImages.store(app).restoreImages(patient,backup){file->
                val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=8});val ok=bitmap!=null;bitmap?.recycle();ok
            } }
            runOnUiThread { busy=false;if(!isDestroyed)result.fold({status.text="Prejšnje slike so obnovljene. Tudi zbirka pred obnovo je varnostno shranjena. Odpri komunikator."},{status.text="Obnova ni izvedena: ${it.message}"}) }
        }.start()
    }
    private fun openMain(){if(!busy){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));finish()}}
    private fun text(value:String,size:Float=17f)=TextView(this).apply{text=value;textSize=size;setPadding(0,12,0,12);content.addView(this)}
    private fun button(label:String,action:()->Unit){content.addView(Button(this).apply{text=label;minHeight=64;setOnClickListener{action()}})}
    override fun onSaveInstanceState(out:Bundle){out.putString("patient",patient);super.onSaveInstanceState(out)}
}

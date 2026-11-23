package com.rehab2

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.*
import com.rehab2.patient.*
import java.io.File

/** Both bounded archives are validated before review. Only the final confirmation publishes state. */
class LanaGridImportActivity:AppCompatActivity() {
    private lateinit var patient:String
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private var packs=listOf<PersonalIconZip>()
    private val choices=linkedMapOf<CheckBox,PersonalIconZip.Entry>()
    private var busy=false
    @Volatile private var closed=false
    @Volatile private var cancelled=false
    private val local=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.size==2&&!busy) prepare { readAll(uris.map { uri-> { contentResolver.openInputStream(uri)!! } }) }
        else if(uris.isNotEmpty())status.text="Izberi oba produkcijska ZIP-a v4 (25 in 47)."
    }
    private var exportIndex=0
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null&&!busy) {
            busy=true
            Thread { val result=runCatching { val file=saved(exportIndex);check(PersonalIconZip.sha256(file)==HASHES[exportIndex]);contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}} }
                runOnUiThread{busy=false;status.text=if(result.isSuccess)"Zasebna kopija izvožena. Izvozi tudi drugi paket." else "Izvoz ni uspel: ${result.exceptionOrNull()?.message}"}
            }.start()
        }
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);patient=state?.getString("patient")?:AacPatientImages.store(this).currentPatientId()
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,24)}
        setContentView(ScrollView(this).apply{addView(body)});header()
        status=text("Mreža v4: 25 glavnih mest in 47 podikon. Slike so predlogi; pomen potrdi skrbnik. Prenos ne spremeni profila.")
        button("PRENESI LANINE OSEBNE IKONE — V4 (OBA PAKETA)"){download()}
        button("PREKLIČI PRENOS"){cancelled=true}
        button("ODPRI SHRANJENA PAKETA BREZ INTERNETA"){if(!busy)prepare { readAll(HASHES.indices.map { i-> { saved(i).inputStream() } }) }}
        button("IZBERI OBA ZIP-A V4"){if(!busy)local.launch(arrayOf("application/zip","application/octet-stream"))}
        for(i in HASHES.indices)button("IZVOZI ZASEBNO KOPIJO — ${if(i==0)"25 GLAVNIH" else "47 PODIKON"}"){if(!busy){exportIndex=i;export.launch(NAMES[i])}}
        if(state==null&&intent.getBooleanExtra("offerLana",false))body.post{download()}
    }
    private fun owner():String? { val s=AacPatientImages.store(this);return s.confirmedSeedPatient(HASHES[0])?:s.confirmedSeedPatient(VerifiedPackageDownload.LANA_SHA256)?:s.confirmedSeedPatient(VerifiedPackageDownload.PREVIOUS_LANA_SHA256) }
    private fun identity(){check(patient==AacPatientImages.store(this).currentPatientId()){ "Pacient se je spremenil." };check(owner()==null||owner()==patient){"Paket pripada drugemu potrjenemu Laninemu profilu."}}
    private fun saved(i:Int)=File(noBackupFilesDir,"aac-patients/$patient/v4-packages/${HASHES[i]}.zip")
    private fun read(input:java.io.InputStream)=PersonalIconZip.read(input,File(noBackupFilesDir,"aac-patients/$patient/import-staging"))
    private fun download(){
        if(busy)return
        runCatching{identity()}.onFailure{status.text=it.message;return}
        AlertDialog.Builder(this).setTitle("Potrdi Lanin profil")
            .setMessage("${profileName()}\nUUID: $patient\nPrenesem oba javna paketa, skupaj približno 95 MB. Šele po preverjanju vseh 72 slik sledita predogled in ločena potrditev mreže ter zamenjav.")
            .setNegativeButton("PREKLIČI",null).setPositiveButton("POTRJUJEM PROFIL IN PRENOS"){_,_->
                prepare {
                    identity();AacPatientImages.store(this).confirmSeedPatient(HASHES[0],patient)
                    val result=mutableListOf<PersonalIconZip>()
                    try { for(i in HASHES.indices){
                        val target=saved(i);target.parentFile!!.mkdirs()
                        if(!target.isFile||target.length()!=SIZES[i]||PersonalIconZip.sha256(target)!=HASHES[i]) {
                            val received=VerifiedPackageDownload().download(URLS[i],HASHES[i],SIZES[i],target.parentFile!!,{closed||cancelled},{bytes,total->runOnUiThread{if(!closed)status.text="Paket ${i+1}/2: ${bytes*100/total} %"}})
                            if(target.exists())check(target.delete())
                            check(received.renameTo(target)){"Zasebne kopije ni mogoče shraniti."}
                        }
                        result+=target.inputStream().use{read(it)}
                    };result } catch(e:Exception){result.forEach{it.close()};throw e}
                }
            }.show()
    }
    private fun readAll(sources:List<()->java.io.InputStream>):List<PersonalIconZip> {
        val result=mutableListOf<PersonalIconZip>()
        try { for(source in sources)result+=source().use{read(it)};return result }
        catch(e:Exception){result.forEach{it.close()};throw e}
    }
    private fun prepare(load:()->List<PersonalIconZip>){
        if(busy)return;busy=true;cancelled=false;status.text="Preverjam oba paketa …"
        Thread {
            var prepared=listOf<PersonalIconZip>()
            val result=runCatching {
                identity();prepared=load();check(prepared.map{it.archiveSha256}.toSet()==HASHES.toSet()){"Potrebna sta oba preverjena produkcijska paketa v4."}
                val expected=AacGridV4.nodes(this).associateBy{it.getString("id")};val entries=prepared.flatMap{it.entries}
                check(entries.size==72&&entries.map{it.candidates.singleOrNull()}.toSet()==expected.keys){"Nepopolna ali podvojena zbirka."}
                for(e in entries){
                    check(!closed&&!cancelled){"Preklicano."};check(e.sha256==expected.getValue(e.candidates.single()).getString("sha256"))
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(e.file.path,bounds)
                    check(bounds.outMimeType=="image/png"&&bounds.outWidth==e.width&&bounds.outHeight==e.height)
                    val b=BitmapFactory.decodeFile(e.file.path,BitmapFactory.Options().apply{inSampleSize=8})?:error("Neveljavna slika")
                    try {check(b.hasAlpha());val px=IntArray(b.width*b.height);b.getPixels(px,0,b.width,0,0,b.width,b.height);check(px.any{it ushr 24==0})}finally{b.recycle()}
                };identity()
                AacGridV4.previewCatalog(this,AacHomeLayout.catalog(this,AacEditorStorage.loadItems(this)))
            }
            runOnUiThread{busy=false;if(closed||result.isFailure){prepared.forEach{it.close()};if(!closed)status.text="Brez sprememb: ${result.exceptionOrNull()?.message}. Prenos lahko ponoviš."}else{packs.forEach{it.close()};packs=prepared;runCatching{review()}.onFailure{status.text="Predogled ni mogoč; brez sprememb: ${it.message}"}}}
        }.start()
    }
    private fun review(){
        body.removeAllViews();choices.clear();header()
        status=text("Preverjenih vseh 72 slik. Mreža in slike še niso spremenjene. Izberi zamenjave; POPRAVI potrebuje posebno potrditev. Neizbrane slike ostanejo prejšnje oziroma splošne.")
        val catalog=AacGridV4.previewCatalog(this,AacHomeLayout.catalog(this,AacEditorStorage.loadItems(this))).associateBy{it.id}
        text("PREDLAGANA GLAVNA MREŽA\n"+AacGridV4.home(this).chunked(5).joinToString("\n"){row->row.joinToString(" | "){catalog.getValue(it).labelSl}},18f)
        button("OZNAČI PREDLOGE RAZEN POPRAVI"){choices.forEach{(c,e)->c.isChecked=e.candidates.single()!="correct_message"&&c.isEnabled}}
        for(e in packs.flatMap{it.entries}){
            val id=e.candidates.single();val item=catalog.getValue(id)
            text("${item.labelSl} · $id",20f)
            val images=LinearLayout(this);val old=AacPatientImages.resolve(this,item)
            for(file in listOf(old,e.file))images.addView(ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(0xffeeeeee.toInt());file?.let{setImageBitmap(BitmapFactory.decodeFile(it.path,BitmapFactory.Options().apply{inSampleSize=8}))}},LinearLayout.LayoutParams(160,160))
            images.setOnClickListener{val color=if(images.tag==true)0xffeeeeee.toInt() else 0xff35404b.toInt();images.tag=images.tag!=true;for(i in 0 until images.childCount)images.getChildAt(i).setBackgroundColor(color)}
            body.addView(images);text("Levo: trenutno; desno: predlog. ${e.reviewNote}")
            val mode=AacGridV4.nodes(this).first{it.getString("id")==id}.getString("speech_mode")
            text(if(mode=="navigate_only")"Samo odpre izbire; brez govora." else "SL: ${AacLocalizedTextResolver.resolveSpeakText(item,"sl")}\nUK: ${AacLocalizedTextResolver.resolveSpeakText(item,"uk")}")
            val choice=CheckBox(this).apply{text="POTRJUJEM SLIKO: ${item.labelSl} · $id";minHeight=64;isEnabled=!item.locked}
            if(id=="correct_message")choice.setOnClickListener{if(choice.isChecked){choice.isChecked=false;AlertDialog.Builder(this).setTitle("POPRAVI SPOROČILO")
                .setMessage("Potrdi pomen komunikacijskega popravka, ne telesnega položaja. ${e.reviewNote}")
                .setNegativeButton("OHRANI PREJŠNJO",null).setPositiveButton("POTRJUJEM TA POMEN"){_,_->choice.isChecked=true}.show()}}
            choices[choice]=e;body.addView(choice)
        }
        button("POTRDI NOVO MREŽO IN IZBRANE SLIKE"){confirm()}
    }
    private fun confirm(){
        if(busy)return
        val selected=choices.filterKeys{it.isChecked&&it.isEnabled}.values.toList()
        if(selected.isEmpty()){status.text="Najprej izberi potrjene slike.";return}
        runCatching{identity()}.onFailure{status.text=it.message;return}
        AlertDialog.Builder(this).setTitle("Potrdi mrežo v4 in zamenjave")
            .setMessage("${profileName()}\nUUID: $patient\nNova mreža: vseh 25 mest iz predogleda. Izbrane slike: ${selected.size}/72.\n${selected.joinToString{it.candidates.single()}}\nPrejšnji razpored in povezave se varnostno shranijo. Drugi pacienti ostanejo nespremenjeni.")
            .setNegativeButton("PREKLIČI",null).setPositiveButton("POTRJUJEM MREŽO IN ZAMENJAVE"){_,_->
                busy=true;status.text="Shranjujem celotno potrjeno spremembo …"
                Thread {val result=runCatching{
                    identity();val store=AacPatientImages.store(this);store.confirmSeedPatient(HASHES[0],patient)
                    val now=AacGridV4.previewCatalog(this,AacHomeLayout.catalog(this,AacEditorStorage.loadItems(this))).associateBy{it.id}
                    check(selected.all{now[it.candidates.single()]?.locked==false}){"Spremenjen ali zaščiten katalog."}
                    store.importGridV4(patient,selected.associate{it.candidates.single() to it.file},selected.associate{it.candidates.single() to it.sha256}){file->val b=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=8});val ok=b!=null;b?.recycle();ok}
                };runOnUiThread{busy=false;if(!closed)result.fold({packs.forEach{it.close()};packs=emptyList();body.removeAllViews();header();status=text("Mreža in ${selected.size} slik shranjene zasebno. Kopija: ${it.name}. Deluje brez interneta.");button("ODPRI KOMUNIKATOR"){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));finish()}},{status.text="Brez potrjene spremembe: ${it.message}"})else{packs.forEach{it.close()};packs=emptyList()}}}.start()
            }.show()
    }
    private fun profileName():String {val p=org.json.JSONObject(String(AacPatientImages.profiles(this).profileFor(patient),Charsets.UTF_8));return "${p.optString("firstName")} ${p.optString("lastName")}"}
    private fun header(){text("LANINA MREŽA V4",24f);text("${profileName()}\nUUID: $patient");button("NAZAJ / BREZ SPREMEMB"){if(!busy)finish()}}
    private fun text(s:String,size:Float=17f)=TextView(this).apply{text=s;textSize=size;setPadding(0,10,0,10);body.addView(this)}
    private fun button(s:String,f:()->Unit){body.addView(Button(this).apply{text=s;minHeight=64;setOnClickListener{f()}})}
    override fun onSaveInstanceState(out:Bundle){out.putString("patient",patient);super.onSaveInstanceState(out)}
    override fun onDestroy(){closed=true;cancelled=true;if(!busy)packs.forEach{it.close()};super.onDestroy()}
    companion object {
        val NAMES=listOf("Lana_v4_glavne_25.zip","Lana_v4_podikone_47.zip")
        val HASHES=listOf("0f2997864748e772489317c814cdecdd2d1b6f1fedc4d80076f9646bfad5a5d3","722a02d39cc7c1a83b08a73900b1329a974a1e2c0b2b17648ec02819eab4661d")
        val SIZES=listOf(31993998L,63291886L)
        val URLS=NAMES.map{"https://github.com/trsavecdusan-wq/rehab2/releases/download/v1.2.838/$it"}
    }
}

"""Real cache/coordinator with fake Android metadata and service; no live audio claims."""
from pathlib import Path
import os,subprocess,tempfile
r=Path(__file__).resolve().parents[2];a=r/'app/src/main/java/com/rehab2/aac'
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
jars=[]
for name in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
 jars+=list((cache/'org.jetbrains.kotlin'/name/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
out=Path(tempfile.mkdtemp(prefix='nr842-cache-'));files=[]
def source(name,s):
 p=out/name;p.write_text(s,encoding='utf8');files.append(str(p))
source('Context.kt','package android.content\nclass Context(val filesDir:java.io.File)')
source('Log.kt','package android.util\nobject Log {fun d(a:String,b:String)=0}')
source('Json.kt','package org.json\nclass JSONArray(val values:List<Any>) {override fun toString()=values.joinToString("|"){ "${it.toString().length}:$it" }}')
source('Metadata.kt','''package android.media
class MediaMetadataRetriever {
 companion object {const val METADATA_KEY_DURATION=9}
 var valid=false
 fun setDataSource(path:String){valid=java.io.File(path).readBytes().firstOrNull()?.toInt()==42}
 fun extractMetadata(key:Int)=if(valid) "1000" else "0"
 fun release(){}
}''')
source('Config.kt','''package com.rehab2.aac
import android.content.Context
object AacSpeechApiConfig {
 var identity="model1:instructions1:mp3"
 fun read(c:Context)=this
 fun generationIdentity(language:String)="$identity:$language"
}
object AacLocalStorage {fun getAudioSlDir(c:Context)=java.io.File(c.filesDir,"legacy")}
object AacLanguageResolver {const val DEFAULT_LANGUAGE_CODE="sl";fun normalize(s:String)=s.lowercase()}
data class AacItem(val id:String, val locked:Boolean=false, val userEdited:Boolean=false, val translationManualOverride:Boolean=false)
object AudioDuckingSettings {
 const val DEFAULT_DUCKING_PERCENT=80
 val enabled=true;val duckingPercent=80
 fun load(c:Context)=this
}
''')
radio=(r/'app/src/main/java/com/rehab2/radio/RadioPlayerController.kt').read_text(encoding='utf-8-sig')
def body(name):
 start=radio.index('    fun '+name+'(');op=radio.index('{',start);end=op+1;depth=1
 while depth:
  depth+=(radio[end]=='{')-(radio[end]=='}');end+=1
 return radio[start:end]
source('Radio.kt','''package com.rehab2.radio
class Player {var volume=1f}
class RadioPlayerController {
 val player=Player();var volumeBeforeDucking:Float?=null
 fun trace(s:String){}
'''+body('applyDucking')+body('restoreDucking')+'\n}')
source('Test.kt','''import com.rehab2.aac.*
import android.content.Context
import java.io.File
import java.security.MessageDigest
var checks=0
fun verify(ok:Boolean){checks++;check(ok){"check $checks"}}
fun main(args:Array<String>){
 val root=File(args[0]);root.mkdirs();val cache=AacSpeechCache(Context(root))
 val text="Boli me.";val identity="sl|coral|0.88|$text"
 val hash=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString(""){"%02x".format(it)}
 val old=File(root,"NovaRehab2/aac/audio/generated/sl/coral/$hash.mp3");old.parentFile.mkdirs();old.writeBytes(byteArrayOf(42,1))
 verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==null)
 verify(cache.getLegacyGeneratedSpeechFile(text,"sl","coral",0.88)==old)
 verify(cache.saveGeneratedSpeech(text,"sl","coral",0.88,byteArrayOf(0,1))==null)
 verify(old.readBytes().contentEquals(byteArrayOf(42,1)))
 val fresh=cache.saveGeneratedSpeech(text,"sl","coral",0.88,byteArrayOf(42,2))!!
 verify(fresh!=old && old.exists());verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==fresh)
 verify(cache.getGeneratedSpeechFile(text,"uk","coral",0.88)==null)
 verify(cache.getGeneratedSpeechFile(text,"sl","onyx",0.88)==null)
 verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.881)==null)
 verify(cache.getGeneratedSpeechFile("Boli me?","sl","coral",0.88)==null)
 AacSpeechApiConfig.identity="model2:instructions1:mp3";verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==null)
 AacSpeechApiConfig.identity="model1:instructions2:mp3";verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==null)
 AacSpeechApiConfig.identity="model1:instructions1:wav";verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==null)
 val offline=object:AacSpeechApiClient {override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray?=null}
 verify(AacSpeechCoordinator(cache,offline).getOrGenerateSpeechFile(text,"sl","coral",0.88)==old)
 val changing=object:AacSpeechApiClient {override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray? {AacSpeechApiConfig.identity="changed-during-http";return byteArrayOf(42,9)}}
 verify(AacSpeechCoordinator(cache,changing).getOrGenerateSpeechFile(text,"sl","coral",0.88)==null)
 verify(cache.getGeneratedSpeechFile(text,"sl","coral",0.88)==null && old.exists() && fresh.exists())
 verify(root.walk().none{it.name.startsWith("speech-")})
 val neg=AacItem("cannot_eat")
 verify(AacTextCorrections842.resolve(neg,"uk","speech","ЇСТИ.")=="Я не можу їсти.")
 verify(AacTextCorrections842.resolve(neg.copy(userEdited=true),"uk","speech","ЇСТИ.")=="ЇСТИ.")
 verify(AacTextCorrections842.resolve(neg,"uk","speech","особистий текст")=="особистий текст")
 verify(AacTextCorrections842.previousSpeech("Я не можу їсти.","uk")==listOf("ЇСТИ."))
 val fixed=listOf("yes","no","thank_you","understand","dont_understand").map{AacItem(it)}
 val care=(AacCareLayout.toilet.keys+"personal-extra").map{AacItem(it)}
 val slots=AacCareLayout.place(care,fixed,AacCareLayout.toilet)
 for((id,slot) in mapOf("nurse_help" to 8,"wc_pee" to 17,"wc_poop" to 19,"wc_wet" to 21,"wc_diaper_change" to 23,"wc_dirty" to 25)) verify(slots[slot-1]?.id==id)
 verify(slots.filterNotNull().map{it.id}.toSet()==(fixed+care).map{it.id}.toSet())
 val nurses=AacCareLayout.nurse.keys.map{AacItem(it)}
 val ns=AacCareLayout.place(nurses,fixed,AacCareLayout.nurse)
 verify(ns[21]?.id=="help_dressing" && ns[23]?.id=="noticed_blood")
 verify(ns.filterNotNull().map{it.id}.toSet()==(fixed+nurses).map{it.id}.toSet())
 val radio=com.rehab2.radio.RadioPlayerController()
 val duck=com.rehab2.radio.RadioDuckingCoordinator
 val owner=Any();val second=Any();duck.register(radio)
 duck.duckForSpeech(Context(root),owner);verify(kotlin.math.abs(radio.player.volume-0.2f)<0.001)
 duck.duckForSpeech(Context(root),owner);verify(kotlin.math.abs(radio.player.volume-0.2f)<0.001)
 duck.duckForSpeech(Context(root),second);duck.restoreAfterSpeech(owner)
 verify(kotlin.math.abs(radio.player.volume-0.2f)<0.001)
 val buffering=com.rehab2.radio.RadioPlayerController();duck.register(buffering)
 verify(kotlin.math.abs(buffering.player.volume-0.2f)<0.001)
 duck.restoreAfterSpeech(second);verify(radio.player.volume==1f && buffering.player.volume==1f)
 duck.restoreAfterSpeech(owner);verify(radio.player.volume==1f)
 println("PASS: $checks cache, care layout and radio ownership checks (Android metadata/players are fakes)")
}''')
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),str(a/'AacSpeechPronunciationPolicy.java')],check=True)
files+=[str(a/n) for n in ['AacSpeechCache.kt','AacSpeechCoordinator.kt','AacSpeechApiClient.kt','AacCareLayout.kt','AacTextCorrections842.kt']]
files.append(str(r/'app/src/main/java/com/rehab2/radio/RadioDuckingCoordinator.kt'))
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',os.pathsep.join([str(out),str(stdlib)]),'-d',str(out),*files],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(out),str(stdlib)]),'TestKt',str(out/'data')],check=True)

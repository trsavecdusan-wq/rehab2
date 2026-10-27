"""JVM tests of real speech cache/coordinator and UI queue body; no live API/audio."""
from pathlib import Path
import os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
jars=[]
for artifact in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
 jars+=list((cache/'org.jetbrains.kotlin'/artifact/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=list((cache/'org.jetbrains.kotlin/kotlin-stdlib/1.9.24').rglob('*.jar'))[0]
out=Path(tempfile.mkdtemp(prefix='NovaRehab-812-runtime-'))
sources=[]
def source(name,code):
 p=out/name;p.write_text(code,encoding='utf-8');sources.append(str(p))
source('Context.kt','''package android.content
import java.io.File
class Context(val filesDir:File)
''')
source('Log.kt','''package android.util
object Log {fun d(tag:String,message:String)=0}
''')
source('Storage.kt','''package com.rehab2.aac
import android.content.Context
import java.io.File
object AacLocalStorage {fun getAudioSlDir(context:Context):File?=File(context.filesDir,"legacy")}
object AacLanguageResolver {const val DEFAULT_LANGUAGE_CODE="sl";fun normalize(s:String)=s.lowercase()}
''')
for name in ['AacSpeechCache.kt','AacSpeechCoordinator.kt','AacSpeechApiClient.kt']:
 sources.append(str(ROOT/'app/src/main/java/com/rehab2/aac'/name))
ui=(ROOT/'app/src/main/java/com/rehab2/PatientPersonalizationActivity.kt').read_text(encoding='utf-8')
start=ui.index('    private fun next()');opening=ui.index('{',start);depth=1;end=opening+1
while depth:
 depth+=(ui[end]=='{')-(ui[end]=='}');end+=1
next_body=ui[start:end].replace('private fun','fun',1)
source('Uri.kt','''package android.net
import java.io.File
object Uri {fun fromFile(file:File)=file}
''')
source('Runtime.kt','''import java.io.File
import com.rehab2.aac.*
import com.rehab2.patient.PatientSpeechGrammar
object Work {val tasks=ArrayDeque<()->Unit>();fun run(){while(tasks.isNotEmpty())tasks.removeFirst()()}}
class Thread(val block:()->Unit){fun start(){Work.tasks.add(block)}}
class Label {var text=""}
class Preview {fun setImageURI(file:File){}}
class Service {
 var calls=0;var result:File?=null
 fun generate(patient:String,item:String,cancelled:()->Boolean):File {
  calls++;check(!cancelled());return File.createTempFile("candidate-", ".png").also{result=it}
 }
}
class Screen {
 var current="A";var batchPatient:String?="A";var queue=ArrayDeque(listOf("tired","walk"))
 var closed=false;var generationSerial=0;var busy=false
 val status=Label();val preview=Preview();val service=Service()
 var candidate:File?=null;var candidateItem:String?=null;var candidatePatient:String?=null;var reviews=0
 fun patientId()=current
 fun clearCandidate(){candidate?.delete();candidate=null}
 fun runOnUiThread(action:()->Unit){Work.tasks.add(action)}
 fun review(){reviews++}
'''+next_body+'''
}
var checks=0
fun verify(value:Boolean){checks++;check(value){"Check $checks failed"}}
fun main(args:Array<String>){
 val cache=AacSpeechCache(android.content.Context(File(args[0])))
 var calls=0
 val api=object:AacSpeechApiClient{
  override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray {
   calls++;return "$text|$languageCode|$voiceId|$speed".toByteArray()
  }
 }
 val coordinator=AacSpeechCoordinator(cache,api)
 for(male in listOf(false,true)) for(language in listOf("sl","uk")) {
  val text=PatientSpeechGrammar.resolve(if(language=="sl")"Rada bi šla." else "Я втомилася.",language,male)
  val patient=PatientSpeechGrammar.voice(male,false);val guide=PatientSpeechGrammar.voice(male,true)
  val before=calls
  val first=coordinator.getOrGenerateSpeechFile(text,language,patient)!!
  verify(calls==before+1)
  verify(coordinator.getOrGenerateSpeechFile(text,language,patient)==first && calls==before+1)
  val second=coordinator.getOrGenerateSpeechFile(text,language,guide)!!
  verify(second!=first && calls==before+2)
  verify(coordinator.getOrGenerateSpeechFile(text,language,guide)==second && calls==before+2)
  verify(first.readText().contains("|$patient|")&&second.readText().contains("|$guide|"))
 }
 val changed=Screen();changed.current="B";changed.next();Work.run();verify(changed.service.calls==0&&changed.queue.isEmpty())
 val pending=Screen();pending.next();pending.current="B";Work.run();verify(pending.reviews==0&&pending.queue.isEmpty()&&pending.service.result?.exists()==false)
 val cancel=Screen();cancel.next();cancel.generationSerial++;Work.run();verify(cancel.reviews==0)
 val rotated=Screen();rotated.next();rotated.closed=true;Work.run();verify(rotated.reviews==0)
 val batch=Screen();batch.next();Work.run();verify(batch.reviews==1&&batch.candidatePatient=="A")
 batch.clearCandidate();batch.current="B";batch.next();Work.run();verify(batch.service.calls==1&&batch.queue.isEmpty())
 println("PASS: $checks runtime checks (SL/UK cold/warm voice caches and captured patient queue). Live API, Android UI and listening NOT tested.")
}
''')
classes=out/'classes';classes.mkdir()
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),str(ROOT/'app/src/main/java/com/rehab2/patient/PatientSpeechGrammar.java')],check=True)
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(stdlib)+os.pathsep+str(classes),'-d',str(classes),*sources],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(stdlib),'RuntimeKt',str(out/'cache')],check=True)

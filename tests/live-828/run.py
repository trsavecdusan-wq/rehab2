"""Compile actual launch/settings code against deterministic Android storage substitutes.
This is not a physical Android permission/camera test.
"""
from pathlib import Path
import os, subprocess, tempfile
R=Path(__file__).resolve().parents[2];out=Path(tempfile.mkdtemp(prefix='novarehab-828-'));files=[]
def source(name,text):
 p=out/name;p.write_text(text,encoding='utf-8');files.append(str(p))
source('Context.kt','''package android.content
import java.io.File
import org.json.JSONObject
class Context(val root:File){
 companion object {const val MODE_PRIVATE=0}
 fun getExternalFilesDir(x:String?)=root
 fun getSharedPreferences(name:String,mode:Int)=Prefs(File(root,name+".json"))
}
class Prefs(val file:File){
 val json=if(file.exists())JSONObject(file.readText())else JSONObject()
 fun getBoolean(k:String,default:Boolean)=json.optBoolean(k,default)
 fun edit()=this
 fun putBoolean(k:String,v:Boolean):Prefs{json.put(k,v);return this}
 fun commit():Boolean{file.parentFile.mkdirs();file.writeText(json.toString());return true}
}
''')
source('Patient.kt','''package com.rehab2.aac
object AacPatientImages{var patient="one";fun store(c:android.content.Context)=this;fun currentPatientId()=patient}
''')
source('Test.kt','''import com.rehab2.aac.*
import android.content.Context
import java.io.File
fun main(args:Array<String>){
 var n=0;fun verify(v:Boolean){n++;check(v){"Check $n"}}
 val ownership=CaptureResourceGate();val oldEpoch=ownership.generation
 val acquired=java.util.concurrent.CountDownLatch(1);val proceed=java.util.concurrent.CountDownLatch(1);val stopped=java.util.concurrent.CountDownLatch(1)
 var resource=false
 val opener=Thread{ownership.open(oldEpoch){acquired.countDown();proceed.await();resource=true}}
 opener.start();acquired.await()
 val stopper=Thread{ownership.stop{resource=false};stopped.countDown()};stopper.start()
 verify(!stopped.await(30,java.util.concurrent.TimeUnit.MILLISECONDS));proceed.countDown();opener.join(2000);stopper.join(2000)
 verify(!opener.isAlive && !stopper.isAlive && !resource)
 verify(!ownership.open(oldEpoch){resource=true});verify(!resource)
 verify(ownership.open(ownership.generation){resource=true});verify(resource)
 ownership.stop{resource=false};verify(!resource)
 var clock=1000L;val gate=CaptureAudioGate{clock}
 val clean=gate.begin();verify(gate.accepts(clean))
 gate.event("speech_will_start");verify(!gate.accepts(clean));verify(gate.begin()==null)
 gate.event("sound_start");gate.event("playback_end");verify(gate.begin()==null)
 clock+=1001;verify(gate.begin()!=null);verify(!gate.accepts(clean))
 val next=gate.begin();gate.event("speech_will_start");gate.event("speech_cancelled");clock+=1001;verify(!gate.accepts(next))
 val safe=gate.begin();verify(gate.accepts(safe));gate.event("tap");verify(gate.accepts(safe))
 var p=CaptureLaunchPolicy()
 verify(p.autoStart(false,false,false,false)==Pair(false,false))
 verify(p.autoStart(true,true,true,true)==null) // returning from settings is not a new launch
 p=CaptureLaunchPolicy();verify(p.autoStart(true,true,false,true)==Pair(false,true))
 p=CaptureLaunchPolicy();verify(p.autoStart(true,true,true,false)==Pair(true,false))
 p=CaptureLaunchPolicy();verify(p.autoStart(true,false,true,true)==Pair(true,false))
 p.stop();verify(p.autoStart(true,true,true,true)==null)
 p.newLaunch();verify(p.autoStart(true,true,true,true)==Pair(true,true));p.stop()
 verify(!p.mayCapture(true,true,true));p.manualStart();verify(p.mayCapture(true,true,true))
 verify(!p.mayCapture(false,true,true));verify(p.mayCapture(true,true,true)) // foreground return
 verify(!p.mayCapture(true,false,true));verify(!p.mayCapture(true,true,false))
 p=CaptureLaunchPolicy();verify(p.autoStart(true,true,true,true)==Pair(true,true)) // new process resets stop latch
 val root=File(args[0]).apply{mkdirs()};var c=Context(root)
 verify(LiveCaptureSettings.load(c)==LiveCaptureSettings.Settings())
 val chosen=LiveCaptureSettings.Settings(true,false,true,false)
 LiveCaptureSettings.save(c,chosen);c=Context(root);verify(LiveCaptureSettings.load(c)==chosen)
 AacPatientImages.patient="two";verify(LiveCaptureSettings.load(c)==LiveCaptureSettings.Settings())
 AacPatientImages.patient="one";verify(LiveCaptureSettings.load(c)==chosen)
 val observation=AiObservationSettings.Settings(true,true,true,true,true,true,123)
 verify(AiObservationSettings.save(c,observation));verify(AiObservationSettings.load(Context(root))==observation)
 val keyword=AacKeywordListenerSettings.Settings(true,"SESSION",true,true,true,listOf("voda"),listOf("voda"),8,123)
 verify(AacKeywordListenerSettings.save(c,keyword));val read=AacKeywordListenerSettings.load(Context(root))
 verify(read.allowMicrophone && read.allowNetwork && read.allowBackgroundListening)
 verify(read.maxSuggestions==3 && read.wakeWords==listOf("voda"))
 println("PASS: $n launch, foreground, stop latch, independent permission and persistent settings checks")
}
''')
for name in ['CaptureResourceGate','CaptureAudioGate','CaptureLaunchPolicy','LiveCaptureSettings','AiObservationSettings','AacKeywordListenerSettings']:
 files.append(str(R/f'app/src/main/java/com/rehab2/aac/{name}.kt'))
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1';jars=[]
for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
 jars+=list((cache/'org.jetbrains.kotlin'/a/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
jsonjar=Path(os.environ['TEMP'])/'novarehab-json-20240303.jar';cp=str(stdlib)+os.pathsep+str(jsonjar)
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(out),*files],check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+cp,'TestKt',str(out/'storage')],check=True)

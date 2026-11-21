from pathlib import Path
import argparse, subprocess, os, tempfile
parser=argparse.ArgumentParser(description='Preverjanje predpomnilnika in ponovnega ustvarjanja slovenskega govora z Android nadomestki.')
parser.add_argument('--repo', type=Path, required=True)
parser.add_argument('--kotlin-libs', type=Path, required=True)
args=parser.parse_args()
repo=args.repo.resolve()
source=repo/'app/src/main/java/com/rehab2/aac'
libs=args.kotlin_libs.resolve()
for name in ('kotlin-compiler-embeddable-1.9.24.jar', 'kotlin-stdlib-1.9.24.jar', 'kotlin-script-runtime-1.9.24.jar', 'kotlin-daemon-embeddable-1.9.24.jar', 'trove4j-1.0.20200330.jar', 'annotations-13.0.jar'):
 if not (libs/name).is_file(): parser.error(f'Manjka knjiznica {libs/name}')
if not (source/'AacSpeechPronunciationPolicy.java').is_file(): parser.error('Najprej uporabi in preveri prilozene spremembe kode.')
out=Path(tempfile.mkdtemp(prefix='novarehab-speech-test-'))
classes=out/'classes';classes.mkdir()
def write(name,text):
 p=out/name;p.write_text(text);return str(p)
context=write('Context.kt','''package android.content
import java.io.File
class Context(val filesDir:File)
''')
log=write('Log.kt','''package android.util
object Log {fun d(tag:String,message:String)=0}
''')
stubs=write('Storage.kt','''package com.rehab2.aac
import android.content.Context
import java.io.File
object AacLanguageResolver {
 const val DEFAULT_LANGUAGE_CODE="sl"
 fun normalize(s:String)=s.trim().lowercase().let {if(it=="ua") "uk" else it}
}
object AacLocalStorage {fun getAudioSlDir(c:Context):File?=File(c.filesDir,"legacy-audio")}
''')
test=write('SpeechTest.kt','''import android.content.Context
import com.rehab2.aac.*
import java.io.File
import java.security.MessageDigest
fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
fun legacy(root:File,language:String,voice:String,text:String):File {
 val key="$language|$voice|0.88|$text"
 return File(root,"NovaRehab2/aac/audio/generated/$language/$voice/${hash(key)}.mp3")
}
fun main(args:Array<String>) {
 val root=File(args[0]);root.mkdirs()
 val cache=AacSpeechCache(Context(root))
 val old=legacy(root,"sl","onyx","Kaj te boli?")
 old.parentFile.mkdirs();old.writeBytes(byteArrayOf(1,2,3))
 check(cache.getGeneratedSpeechFile("Kaj te boli?","sl","onyx",0.88)==null)
 check(cache.getLegacyGeneratedSpeechFile("Kaj te boli?","sl","onyx",0.88)==old)
 val ukrainian=legacy(root,"uk","coral","У мене болить.")
 ukrainian.parentFile.mkdirs();ukrainian.writeBytes(byteArrayOf(8))
 check(cache.getGeneratedSpeechFile("У мене болить.","uk","coral",0.88)==ukrainian)
 var calls=0
 val client=object:AacSpeechApiClient {
  override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray? {
   calls++;return byteArrayOf(4,5,6)
  }
 }
 val coordinator=AacSpeechCoordinator(cache,client)
 val fresh=coordinator.getOrGenerateSpeechFile("Kaj te boli?","sl","onyx",0.88)!!
 check(fresh!=old && fresh.readBytes().contentEquals(byteArrayOf(4,5,6)))
 check(old.readBytes().contentEquals(byteArrayOf(1,2,3)))
 check(coordinator.getOrGenerateSpeechFile("Kaj te boli?","sl","onyx",0.88)==fresh && calls==1)
 val missingClient=object:AacSpeechApiClient {
  override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray?=null
 }
 val secondOld=legacy(root,"sl","coral","Boli me.")
 secondOld.parentFile.mkdirs();secondOld.writeBytes(byteArrayOf(7))
 check(AacSpeechCoordinator(cache,missingClient).getOrGenerateSpeechFile("Boli me.","sl","coral",0.88)==secondOld)
 check(cache.getGeneratedSpeechFile("Kaj te boli?","sl","coral",0.88)==null)
 check(cache.getGeneratedSpeechFile("Kaj te boli?","sl","onyx",1.0)==null)
 check(AacSpeechCoordinator(cache,missingClient).getOrGenerateSpeechFile(" ","sl","onyx",0.88)==null)
 val start=java.util.concurrent.CountDownLatch(1)
 val finish=java.util.concurrent.CountDownLatch(1)
 val count=java.util.concurrent.atomic.AtomicInteger()
 val slowClient=object:AacSpeechApiClient {
  override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray? {
   count.incrementAndGet();start.countDown();finish.await();return byteArrayOf(9,9)
  }
 }
 val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
 val one=pool.submit<File?> { AacSpeechCoordinator(cache,slowClient).getOrGenerateSpeechFile("shared","sl","onyx",0.88) }
 start.await()
 val two=pool.submit<File?> { AacSpeechCoordinator(cache,slowClient).getOrGenerateSpeechFile("shared","sl","onyx",0.88) }
 finish.countDown()
 check(one.get()==two.get());check(count.get()==1);pool.shutdown()
 val throwing=object:AacSpeechApiClient {
  override fun generateSpeech(text:String,languageCode:String,voiceId:String,speed:Double):ByteArray?=throw IllegalStateException("offline")
 }
 check(AacSpeechCoordinator(cache,throwing).getOrGenerateSpeechFile("Boli me.","sl","coral",0.88)==secondOld)
 println("PASS: 13 checks of real cache/coordinator — Slovenian regeneration, legacy fallback, Ukrainian cache, voice/speed separation and blank input")
}
''')
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(classes),str(source/'AacSpeechPronunciationPolicy.java')],check=True)
stdlib=libs/'kotlin-stdlib-1.9.24.jar'
cp=os.pathsep.join(map(str,libs.glob('*.jar')))
files=[context,log,stubs,str(source/'AacSpeechCache.kt'),str(source/'AacSpeechCoordinator.kt'),str(source/'AacSpeechApiClient.kt'),test]
subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',os.pathsep.join([str(stdlib),str(classes)]),'-d',str(classes),*files],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(classes),str(stdlib)]),'SpeechTestKt',str(out/'data')],check=True)

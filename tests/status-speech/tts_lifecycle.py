"""Execute actual Kotlin TTS lifecycle bodies with a deterministic fake Android engine.
Set KOTLIN_TEST_LIB_DIR to Kotlin 1.9.24 compiler/dependency jars, or reuse Gradle's cache.
No real device, audio engine, network or radio behavior is simulated as verified.
"""
from pathlib import Path
import os,re,subprocess,tempfile
R=Path(__file__).resolve().parents[2]
s=(R/'app/src/main/java/com/rehab2/aac/AacAudioPlayer.kt').read_text(encoding='utf-8-sig')
def body(name):
 m=re.search(r'    (?:private |override )?fun '+name+r'\(',s);assert m,name
 start=m.start();op=s.index('{',start);depth=1;end=op+1
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end].replace('private fun','fun',1).replace('override fun','fun',1)
methods=['onInit','speakAndroidFallback','configureTtsLanguage','isLanguageUsable','speak','beginSpeechRequest','postCurrentTtsCallback','postSpeechCallback','stopCurrentSpeech','interruptCurrentSpeechForNewInput','nextSpeechRequestSerial','release','playAudioFileIfAvailable']
code=r'''
import java.util.Locale
import java.io.File
import com.rehab2.aac.AacSpeechRequestPolicy
class Handler {
 val tasks=ArrayDeque<()->Unit>()
 fun post(action:()->Unit){tasks.add(action)}
 fun drain(){while(tasks.isNotEmpty()) tasks.removeFirst()()}
}
open class UtteranceProgressListener {
 open fun onStart(utteranceId:String?){}
 open fun onDone(utteranceId:String?){}
 open fun onError(utteranceId:String?){}
 open fun onError(utteranceId:String?,errorCode:Int){}
}
class Bundle {fun putString(k:String,v:String){}}
object AudioManager {const val STREAM_MUSIC=3}
object Toast {const val LENGTH_SHORT=0;fun makeText(c:Any,s:String,n:Int)=this;fun show(){}}
object Log {fun d(t:String,s:String){};fun w(t:String,s:String){};fun e(t:String,s:String){}}
object AacLanguageResolver {
 const val DEFAULT_LANGUAGE_CODE="sl"
 fun normalize(s:String)=s.lowercase()
 fun ttsLanguageTag(s:String)=s
}
class MediaPlayer {
 var duration=1500;var completion:((MediaPlayer)->Unit)?=null
 var error:((MediaPlayer,Int,Int)->Boolean)?=null
 var releases=0
 fun setDataSource(path:String){}
 fun setOnCompletionListener(action:(MediaPlayer)->Unit){completion=action}
 fun setOnErrorListener(action:(MediaPlayer,Int,Int)->Boolean){error=action}
 fun prepare(){}
 fun start(){}
 fun release(){releases++}
}
class TextToSpeech {
 companion object {const val SUCCESS=0;const val ERROR=-1;const val LANG_MISSING_DATA=-2;const val LANG_NOT_SUPPORTED=-3;const val QUEUE_FLUSH=0}
 object Engine {const val KEY_PARAM_STREAM="stream"}
 data class Voice(val name:String,val locale:Locale)
 data class Spoken(val text:String,val language:String,val id:String)
 var voice:Voice?=null
 val languages=mutableMapOf<String,Int>()
 val spoken=mutableListOf<Spoken>()
 var listener:UtteranceProgressListener?=null
 var stops=0;var shutdowns=0
 fun setLanguage(locale:Locale):Int {voice=Voice("fake",locale);return languages[locale.language]?:LANG_NOT_SUPPORTED}
 fun setOnUtteranceProgressListener(value:UtteranceProgressListener?){listener=value}
 fun speak(text:String,mode:Int,params:Bundle,id:String):Int {spoken.add(Spoken(text,voice!!.locale.language,id));return SUCCESS}
 fun stop(){stops++}
 fun shutdown(){shutdowns++}
}
interface SpeechListener {fun event()}
class Audio {
 var released=false;var isTtsReady=false;var isTtsFailed=false
 var pendingTextToSpeak:String?=null;var pendingLanguageCode="sl"
 val engine=TextToSpeech();var textToSpeech:TextToSpeech?=engine
 val context=Any();val TAG="test";val mainHandler=Handler()
 var speechRequestSerial=1;var starts=0;var completions=0;var errors=0
 var speechListener:SpeechListener?=null
 var mediaPlayer:MediaPlayer?=null
 val MIN_AUDIO_DURATION_MS=700
 fun applySpeechLoudness(player:MediaPlayer){}
 fun releaseMediaPlayer(){mediaPlayer?.release();mediaPlayer=null}
 fun traceAudio(s:String){}
 fun notifySpeechStarted(){starts++}
 fun notifySpeechCompleted(){completions++}
 fun notifySpeechError(){errors++}
 fun stopPlayback(notifyCancellation:Boolean=true,restoreDuckingWhenSilent:Boolean=false){}
 fun requestMusicAudioFocus(){}
'''+ '\n'.join(body(n) for n in methods)+r'''
}
var checks=0
fun verify(ok:Boolean){checks++;check(ok){"Lifecycle check $checks failed"}}
fun main(){
 Locale.setDefault(Locale.ENGLISH)
 // Ukrainian request queued before init succeeds even when Slovenian is absent.
 val uk=Audio();uk.engine.languages["uk"]=0
 uk.speakAndroidFallback("Дата", "uk");verify(uk.pendingTextToSpeak=="Дата")
 uk.onInit(0);verify(uk.engine.spoken.single().language=="uk");verify(!uk.isTtsFailed)
 // Missing Ukrainian never plays through a wrong-language/default voice.
 val missing=Audio();missing.engine.languages["sl"]=0
 missing.speakAndroidFallback("Дата","uk");missing.onInit(0)
 verify(missing.engine.spoken.isEmpty());verify(missing.errors==1);verify(missing.pendingTextToSpeak==null)
 // No supported fallback language returns an error, rather than claiming success.
 val none=Audio();none.onInit(0);none.speakAndroidFallback("Besedilo","sl")
 verify(none.engine.spoken.isEmpty());verify(none.errors==1)
 // An engine init failure clears and reports the pending request.
 val failed=Audio();failed.speakAndroidFallback("test","sl");failed.onInit(-1)
 verify(failed.pendingTextToSpeak==null);verify(failed.errors==1)
 // A newer generated request discards text queued while TTS was still starting.
 val replaced=Audio();replaced.engine.languages["sl"]=0
 replaced.speakAndroidFallback("staro","sl");val serial=replaced.beginSpeechRequest()
 verify(serial==2 && replaced.engine.stops==1 && replaced.pendingTextToSpeak==null)
 replaced.onInit(0);verify(replaced.engine.spoken.isEmpty())
 replaced.speakAndroidFallback("novo","sl");verify(replaced.engine.spoken.single().text=="novo")
 // Every TTS callback is checked when dispatched, including both error overloads.
 val events=Audio();events.onInit(0);val listener=events.engine.listener!!
 listener.onStart("aac_1");events.mainHandler.drain();verify(events.starts==1)
 listener.onDone("aac_1");listener.onError("aac_1");listener.onError("aac_1",-1)
 events.beginSpeechRequest();events.mainHandler.drain()
 verify(events.completions==0 && events.errors==0)
 listener.onStart("aac_2");listener.onDone("aac_2");events.mainHandler.drain()
 verify(events.starts==2 && events.completions==1)
 listener.onError("aac_2",-1);events.mainHandler.drain();verify(events.errors==1)
 listener.onDone(null);events.mainHandler.drain();verify(events.completions==1)
 // Already queued UI notifications are also invalidated by new input.
 var callbacks=0;events.speechListener=object:SpeechListener{override fun event(){callbacks++}}
 events.postSpeechCallback{it.event()};events.interruptCurrentSpeechForNewInput();events.mainHandler.drain()
 verify(callbacks==0)
 // Release invalidates both pending init and all old engine callbacks.
 val dead=Audio();dead.speakAndroidFallback("old","sl");dead.release();dead.onInit(0)
 verify(dead.engine.spoken.isEmpty() && dead.pendingTextToSpeak==null && dead.engine.shutdowns==1)
 listener.onStart("aac_3");events.release();events.mainHandler.drain();verify(events.starts==2)
 // Late events from a replaced MediaPlayer must not release or fail the new one.
 val media=Audio();val file=File.createTempFile("lifecycle-", ".wav");file.writeText("fake")
 verify(media.playAudioFileIfAvailable(file));val old=media.mediaPlayer!!
 verify(media.playAudioFileIfAvailable(file));val current=media.mediaPlayer!!
 old.completion!!(old);verify(media.mediaPlayer===current && current.releases==0 && media.completions==0)
 verify(old.error!!(old,1,1));verify(media.mediaPlayer===current && media.errors==0)
 current.completion!!(current);verify(media.mediaPlayer==null && current.releases==1 && media.completions==1)
 file.delete()
 println("PASS: $checks Kotlin lifecycle checks executing real application bodies; Android playback not tested")
}
'''
custom=os.environ.get('KOTLIN_TEST_LIB_DIR')
if custom:jars=list(Path(custom).glob('*.jar'))
else:
 cache=Path(os.environ.get('USERPROFILE',str(Path.home())))/'.gradle/caches/modules-2/files-2.1'
 jars=[]
 for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
  jars+=list((cache/'org.jetbrains.kotlin'/a/'1.9.24').rglob('*.jar'))
 jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
assert any('compiler-embeddable' in p.name for p in jars),'Kotlin compiler missing'
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
out=Path(tempfile.mkdtemp(prefix='novarehab-tts-lifecycle-'))
source=out/'Lifecycle.kt';source.write_text(code,encoding='utf-8')
policy=R/'app/src/main/java/com/rehab2/aac/AacSpeechRequestPolicy.java'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),str(policy)],check=True)
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',os.pathsep.join([str(out),str(stdlib)]),'-d',str(out),str(source)],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(out),str(stdlib)]),'LifecycleKt'],check=True)

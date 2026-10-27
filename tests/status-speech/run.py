"""Compile actual callback bodies in a deterministic JVM scheduler.
No Android audio/device claims: transport, clock, UI and playback are fakes.
Requires the Kotlin compiler already cached by the Android build.
"""
from pathlib import Path
import os,re,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
audio=(ROOT/'app/src/main/java/com/rehab2/aac/AacAudioPlayer.kt').read_text(encoding='utf-8-sig')
main=(ROOT/'app/src/main/java/com/rehab2/MainActivity.kt').read_text(encoding='utf-8-sig')
def function(source,name):
 start=source.index('    private fun '+name+'('); opening=source.index('{',start); depth=1; end=opening+1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end].replace('private fun','fun',1)
assert 'speakTextWithGenerationTimeout(text, languageCode, SPEECH_GENERATION_TIMEOUT_MS)' in audio
assert 'speakTextWithGenerationTimeout(text, languageCode, STATUS_SPEECH_GENERATION_TIMEOUT_MS, guide = true)' in audio
for view in ('txtStatusDate','txtStatusWeather'):
 assert re.search(view+r'\.setOnLongClickListener\s*\{\s*speakStatusOrientation\(\)\s*true\s*}',main)
 assert re.search(view+r'\.setOnClickListener\s*\{\s*speakStatusOrientation\(\)\s*}',main)
assert 'statusOrientationRequestSerial += 1' in main.split('override fun onPause()',1)[1].split('override fun onDestroy()',1)[0]
assert 'statusOrientationRequestSerial += 1\n                mainAacTapId' in main
constants='\n'.join(re.findall(r'        const val (?:STATUS_)?SPEECH_GENERATION_TIMEOUT_MS = [\d_L]+',audio)).replace('const val','val')
code='''
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
object Clock {
 var now=0L; var delay=0L; var order=0
 data class Event(val time:Long,val order:Int,val action:()->Unit)
 val queue=mutableListOf<Event>()
 fun post(delay:Long, action:()->Unit){queue.add(Event(now+delay,order++,action))}
 fun run(){while(queue.isNotEmpty()){val e=queue.minWith(compareBy<Event>{it.time}.thenBy{it.order});queue.remove(e);now=e.time;e.action()}}
 fun reset(){now=0;delay=0;order=0;queue.clear()}
}
class Handler {
 fun postDelayed(action:()->Unit, delay:Long)=Clock.post(delay,action)
 fun post(action:()->Unit)=Clock.post(0,action)
}
class Thread(val action:()->Unit){fun start()=Clock.post(Clock.delay,action)}
object Toast {val LENGTH_SHORT=0;fun makeText(c:Any,s:String,n:Int)=this;fun show(){}}
object Log {fun d(tag:String,message:String){}}
class Coordinator {
 var available=true
 fun getOrGenerateSpeechFile(text:String,languageCode:String,voiceId:String):File?=if(available) File("fake.wav") else null
}
object AacPatientVoiceSettings {fun voice(context:Any, guide:Boolean)="test"}
class Audio {
 val context=Any()
 val TAG="test";val mainHandler=Handler();var speechRequestSerial=1
 val speechCoordinator=Coordinator();var plays=0;var fallbacks=0
 fun showSpeechApiConfigIssueIfNeeded(){}
 fun traceAudio(s:String){}
 fun playAudioFileIfAvailable(file:File?):Boolean {if(file!=null){plays++;return true};return false}
'''+constants+'\n'+function(audio,'playGeneratedSpeechOrFallback')+'''
 fun start(status:Boolean){playGeneratedSpeechOrFallback("date","sl",speechRequestSerial,"generated",timeoutMs=if(status) STATUS_SPEECH_GENERATION_TIMEOUT_MS else SPEECH_GENERATION_TIMEOUT_MS){fallbacks++}}
}
object StatusOrientationSettings {
 var speakWeather=true;var weatherSourceUrl="test"
 fun load(context:Any)=this
}
object WeatherClient {var sentence:String?="weather";fun fetchOrientationSentence(url:String,language:String)=sentence}
object StatusOrientationSpeaker {
 var base="date";var baseLanguage=""
 fun buildBaseSpeechText(context:Any,languageCode:String):String {baseLanguage=languageCode;return base}
 fun buildSpeechText(context:Any,weatherSentence:String?,languageCode:String)=base+if(weatherSentence!=null) " "+weatherSentence else ""
}
class Speaker {val spoken=mutableListOf<String>();var language="";fun speakStatusText(text:String,code:String){spoken.add(text);language=code}}
class Status {
 var statusOrientationRequestSerial=0;val mainHandler=Handler();val TAG="test";val aacAudioPlayer=Speaker()
 fun getActiveSpeechLanguage()="uk"
 fun runOnUiThread(action:()->Unit)=Clock.post(0,action)
'''+function(main,'speakStatusOrientation')+'''
}
var checks=0
fun verify(value:Boolean){checks++;check(value){"Check $checks failed"}}
fun main(){
 for(delay in listOf(0L,2500L,29999L)){
  Clock.reset();Clock.delay=delay;val a=Audio();a.start(true);Clock.run();verify(a.plays==1&&a.fallbacks==0)
 }
 Clock.reset();Clock.delay=31000;val late=Audio();late.start(true);Clock.run();verify(late.plays==0&&late.fallbacks==1)
 Clock.reset();Clock.delay=2500;val ordinary=Audio();ordinary.start(false);Clock.run();verify(ordinary.plays==0&&ordinary.fallbacks==1)
 Clock.reset();val unavailable=Audio();unavailable.speechCoordinator.available=false;unavailable.start(true);Clock.run();verify(unavailable.plays==0&&unavailable.fallbacks==1)
 Clock.reset();Clock.delay=2500;val cancelled=Audio();cancelled.start(true);Clock.post(500){cancelled.speechRequestSerial++};Clock.run();verify(cancelled.plays==0&&cancelled.fallbacks==0)
 Clock.reset();Clock.delay=2500;val weather=Status();weather.speakStatusOrientation();Clock.run();verify(weather.aacAudioPlayer.spoken==listOf("date weather"));verify(weather.aacAudioPlayer.language=="uk")
 Clock.reset();Clock.delay=5000;val slow=Status();slow.speakStatusOrientation();Clock.run();verify(slow.aacAudioPlayer.spoken==listOf("date"))
 Clock.reset();WeatherClient.sentence=null;val offline=Status();offline.speakStatusOrientation();Clock.run();verify(offline.aacAudioPlayer.spoken==listOf("date"));WeatherClient.sentence="weather"
 Clock.reset();Clock.delay=2500;val repeat=Status();repeat.speakStatusOrientation();Clock.post(200){repeat.speakStatusOrientation()};Clock.run();verify(repeat.aacAudioPlayer.spoken==listOf("date weather"))
 for(reason in listOf("AAC","pause")){
  Clock.reset();Clock.delay=2500;val stale=Status();stale.speakStatusOrientation();Clock.post(100){stale.statusOrientationRequestSerial++};Clock.run();verify(stale.aacAudioPlayer.spoken.isEmpty())
 }
 Clock.reset();StatusOrientationSettings.speakWeather=false;val date=Status();date.speakStatusOrientation();Clock.run();verify(date.aacAudioPlayer.spoken==listOf("date"));StatusOrientationSettings.speakWeather=true
 println("PASS: $checks deterministic callback checks using actual source bodies; listener/invalidation source checks passed. Android playback NOT tested.")
}
'''
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
compiler=[]
for artifact in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
 compiler+=list((cache/'org.jetbrains.kotlin'/artifact/'1.9.24').rglob('*.jar'))
compiler+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=list((cache/'org.jetbrains.kotlin/kotlin-stdlib/1.9.24').rglob('*.jar'))[0]
out=Path(tempfile.mkdtemp(prefix='NovaRehab-status-810-'));source=out/'StatusRegression.kt';source.write_text(code,encoding='utf-8')
cp=os.pathsep.join(map(str,compiler))
subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-nowarn','-classpath',str(stdlib),'-d',str(out/'classes'),str(source)],check=True)
subprocess.run(['java','-cp',str(out/'classes')+os.pathsep+str(stdlib),'StatusRegressionKt'],check=True)

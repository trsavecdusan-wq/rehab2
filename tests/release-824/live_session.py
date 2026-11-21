"""Execute real session lifecycle with a deterministic clock and minimal Android UI stubs."""
from pathlib import Path
import os, subprocess, tempfile
R=Path(__file__).resolve().parents[2]
out=Path(tempfile.mkdtemp(prefix='novarehab-live-'))
files=[]
def source(name,text):
    p=out/name;p.write_text(text,encoding='utf-8');files.append(str(p))
source('Context.kt','package android.content\nopen class Context')
source('Activity.kt','package android.app\nopen class Activity:android.content.Context(){fun <T> findViewById(id:Int):T?=null}')
source('Clock.kt','package android.os\nobject SystemClock {var now=100L;fun elapsedRealtime()=now}')
source('View.kt','package android.view\nopen class View {companion object {const val VISIBLE=0;const val GONE=8};var visibility=0;fun setOnClickListener(action:(View)->Unit){}}')
source('Button.kt','package android.widget\nclass Button:android.view.View(){var text=""}')
source('R.kt','package com.rehab2\nobject R {object id {const val btnLiveTestStop=1}}')
source('Patient.kt','package com.rehab2.aac\nobject AacPatientImages {var patient="one";fun store(c:android.content.Context)=this;fun currentPatientId()=patient}')
source('Test.kt','''import com.rehab2.aac.*
import android.os.SystemClock
fun main(){
 var n=0;fun verify(v:Boolean){n++;check(v){"Live check $n"}}
 val c=android.content.Context()
 LiveTestSession.record("tap","ignored");verify(LiveTestSession.snapshot().isEmpty())
 LiveTestSession.start(c);verify(LiveTestSession.active)
 LiveTestSession.record("tap","pain");SystemClock.now+=10;LiveTestSession.record("page_shown")
 SystemClock.now+=90;LiveTestSession.record("sound_start")
 SystemClock.now+=800;LiveTestSession.record("playback_end");LiveTestSession.record("input_ready")
 verify(LiveTestSession.report().contains("začetek 100 ms"));verify(LiveTestSession.report().contains("odziv 900 ms"))
 LiveTestSession.record("note_observed","obrne glavo");LiveTestSession.record("note_confirmed","želi odmor")
 verify(LiveTestSession.report().contains("note_observed"));verify(LiveTestSession.report().contains("note_confirmed"))
 LiveTestSession.stop();val size=LiveTestSession.snapshot().size;LiveTestSession.record("tap","ignored")
 verify(!LiveTestSession.active && LiveTestSession.snapshot().size==size)
 LiveTestSession.clear();verify(LiveTestSession.snapshot().isEmpty())
 LiveTestSession.start(c);repeat(2200){LiveTestSession.record("tap","yes")};verify(LiveTestSession.snapshot().size==2000)
 AacPatientImages.patient="two";LiveTestSession.validate(c);verify(!LiveTestSession.active && LiveTestSession.snapshot().isEmpty())
 LiveTestSession.start(c);SystemClock.now+=86400001;verify(LiveTestSession.snapshot().isEmpty() && !LiveTestSession.active)
 println("PASS: $n live-session checks; explicit start/stop, independent notes, timing, bounds, patient isolation and 24h deletion")
}''')
files.append(str(R/'app/src/main/java/com/rehab2/aac/LiveTestSession.kt'))
cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
jars=[]
for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-daemon-embeddable']:
    jars+=list((cache/'org.jetbrains.kotlin'/a/'1.9.24').rglob('*.jar'))
jars+=list((cache/'org.jetbrains.intellij.deps/trove4j').rglob('*.jar'))+list((cache/'org.jetbrains/annotations').rglob('*.jar'))
stdlib=next(p for p in jars if p.name.startswith('kotlin-stdlib-'))
subprocess.run(['java','-cp',os.pathsep.join(map(str,jars)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(out),*files],check=True)
subprocess.run(['java','-cp',str(out)+os.pathsep+str(stdlib),'TestKt'],check=True)
